package hu.lextronic.radio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Közös állapot a lejátszó (szolgáltatás) és a felület között — ugyanabban a folyamatban futnak.
 * A hangszínszabályzó erősítései és a spektrum legutóbbi értékei.
 */
object AudioFx {
    /** Az 5 sáv középfrekvenciája (Hz). */
    val FREQS = floatArrayOf(60f, 230f, 910f, 3600f, 12000f)
    val LABELS = listOf("60", "230", "910", "3,6k", "12k")
    const val BANDS = 32

    @Volatile var enabled = false
    @Volatile var gains = FloatArray(5)            // dB, -12..+12
        private set
    @Volatile var version = 0
        private set

    fun setGains(g: FloatArray, on: Boolean) {
        gains = g.copyOf(); enabled = on; version++
    }

    /** A spektrumot csak akkor számoljuk, ha a felület mutatja (energiatakarékosság). */
    @Volatile var wantSpectrum = false
    val spectrum = FloatArray(BANDS)                 // 0..1
    @Volatile var spectrumTime = 0L
}

/**
 * Hangszínszabályzó (5 biquad szűrő) + FFT a spektrumhoz, 16 bites PCM-en.
 * Az ExoPlayer hangútjába fűzzük be (PlaybackService).
 */
@OptIn(UnstableApi::class)
class LxAudioProcessor : BaseAudioProcessor() {
    private var channels = 2
    private var rate = 44100
    private var appliedVersion = -1
    private var active = false
    private var preGain = 1f

    // szűrők: [sáv][b0,b1,b2,a1,a2] és állapot [csatorna][sáv][x1,x2,y1,y2]
    private val coef = Array(5) { FloatArray(5) }
    private var state = Array(2) { Array(5) { FloatArray(4) } }

    // FFT
    private val n = 1024
    private val fifo = FloatArray(n)
    private var fifoPos = 0
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / (n - 1))).toFloat() }
    private var bandEdges = IntArray(AudioFx.BANDS + 1)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate
        state = Array(channels) { Array(5) { FloatArray(4) } }
        appliedVersion = -1
        buildBandEdges()
        return inputAudioFormat           // ugyanazt adjuk ki, csak módosítva
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (appliedVersion != AudioFx.version) updateCoefficients()
        val out = replaceOutputBuffer(size)
        val inp = inputBuffer.order(ByteOrder.nativeOrder())
        val spec = AudioFx.wantSpectrum
        val frames = size / (2 * channels)
        for (f in 0 until frames) {
            var mono = 0f
            for (c in 0 until channels) {
                var x = inp.short.toFloat()
                if (active) {
                    x *= preGain
                    val st = state[c]
                    for (b in 0 until 5) {
                        val k = coef[b]; val s = st[b]
                        val y = k[0] * x + k[1] * s[0] + k[2] * s[1] - k[3] * s[2] - k[4] * s[3]
                        s[1] = s[0]; s[0] = x; s[3] = s[2]; s[2] = y
                        x = y
                    }
                    if (x > 32767f) x = 32767f else if (x < -32768f) x = -32768f
                }
                out.putShort(x.toInt().toShort())
                mono += x
            }
            if (spec) {
                fifo[fifoPos++] = mono / (channels * 32768f)
                if (fifoPos == n) { fifoPos = 0; computeSpectrum() }
            }
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    override fun onFlush() {
        for (ch in state) for (s in ch) s.fill(0f)
        fifoPos = 0
    }

    override fun onReset() { appliedVersion = -1 }

    // ---------------- hangszín ----------------

    private fun updateCoefficients() {
        appliedVersion = AudioFx.version
        val g = AudioFx.gains
        active = AudioFx.enabled && g.any { it != 0f }
        if (!active) return
        // ha emelünk, előtte halkítunk, hogy ne torzuljon
        val maxBoost = g.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        preGain = 10f.pow(-maxBoost * 0.7f / 20f)
        for (b in 0 until 5) {
            val f = AudioFx.FREQS[b].coerceAtMost(rate * 0.45f)
            when (b) {
                0 -> shelf(coef[b], f, g[b], low = true)
                4 -> shelf(coef[b], f, g[b], low = false)
                else -> peaking(coef[b], f, g[b], 1.0)
            }
        }
    }

    // RBJ „Audio EQ Cookbook” képletek
    private fun peaking(k: FloatArray, f: Float, db: Float, q: Double) {
        val a = 10.0.pow(db / 40.0)
        val w = 2 * PI * f / rate
        val alpha = sin(w) / (2 * q)
        val cw = cos(w)
        val a0 = 1 + alpha / a
        k[0] = ((1 + alpha * a) / a0).toFloat()
        k[1] = ((-2 * cw) / a0).toFloat()
        k[2] = ((1 - alpha * a) / a0).toFloat()
        k[3] = ((-2 * cw) / a0).toFloat()
        k[4] = ((1 - alpha / a) / a0).toFloat()
    }

    private fun shelf(k: FloatArray, f: Float, db: Float, low: Boolean) {
        val a = 10.0.pow(db / 40.0)
        val w = 2 * PI * f / rate
        val cw = cos(w)
        val alpha = sin(w) / 2 * sqrt(2.0)          // S = 1
        val sa = 2 * sqrt(a) * alpha
        val b0: Double; val b1: Double; val b2: Double; val a0: Double; val a1: Double; val a2: Double
        if (low) {
            b0 = a * ((a + 1) - (a - 1) * cw + sa)
            b1 = 2 * a * ((a - 1) - (a + 1) * cw)
            b2 = a * ((a + 1) - (a - 1) * cw - sa)
            a0 = (a + 1) + (a - 1) * cw + sa
            a1 = -2 * ((a - 1) + (a + 1) * cw)
            a2 = (a + 1) + (a - 1) * cw - sa
        } else {
            b0 = a * ((a + 1) + (a - 1) * cw + sa)
            b1 = -2 * a * ((a - 1) + (a + 1) * cw)
            b2 = a * ((a + 1) + (a - 1) * cw - sa)
            a0 = (a + 1) - (a - 1) * cw + sa
            a1 = 2 * ((a - 1) - (a + 1) * cw)
            a2 = (a + 1) - (a - 1) * cw - sa
        }
        k[0] = (b0 / a0).toFloat(); k[1] = (b1 / a0).toFloat(); k[2] = (b2 / a0).toFloat()
        k[3] = (a1 / a0).toFloat(); k[4] = (a2 / a0).toFloat()
    }

    // ---------------- spektrum ----------------

    /** 32 logaritmikus sáv 50 Hz és 16 kHz között. */
    private fun buildBandEdges() {
        val lo = 50.0; val hi = minOf(16000.0, rate / 2.0 - 1)
        val e = IntArray(AudioFx.BANDS + 1)
        for (i in 0..AudioFx.BANDS) {
            val f = lo * (hi / lo).pow(i.toDouble() / AudioFx.BANDS)
            e[i] = (f * n / rate).toInt().coerceIn(1, n / 2 - 1)
        }
        for (i in 1..AudioFx.BANDS) if (e[i] <= e[i - 1]) e[i] = e[i - 1] + 1
        bandEdges = e
    }

    private fun computeSpectrum() {
        for (i in 0 until n) { re[i] = fifo[i] * window[i]; im[i] = 0f }
        fft(re, im)
        val out = AudioFx.spectrum
        for (b in 0 until AudioFx.BANDS) {
            var m = 0f
            val from = bandEdges[b]; val to = max(bandEdges[b + 1], from + 1)
            for (k in from until to.coerceAtMost(n / 2)) m = max(m, hypot(re[k], im[k]))
            val mag = m / (n / 4f)
            val db = 20f * log10(mag + 1e-9f)
            out[b] = ((db + 72f) / 66f).coerceIn(0f, 1f)
        }
        AudioFx.spectrumTime = System.nanoTime()
    }

    /** Helyben számolt radix-2 FFT. */
    private fun fft(x: FloatArray, y: FloatArray) {
        val size = x.size
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = x[i]; x[i] = x[j]; x[j] = tr
                val ti = y[i]; y[i] = y[j]; y[j] = ti
            }
        }
        var len = 2
        while (len <= size) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < size) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val vr = x[b] * cr - y[b] * ci
                    val vi = x[b] * ci + y[b] * cr
                    x[b] = x[a] - vr; y[b] = y[a] - vi
                    x[a] += vr; y[a] += vi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
