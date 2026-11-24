package hu.lextronic.radio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import hu.lextronic.radio.ui.LextronicApp
import hu.lextronic.radio.ui.LextronicTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Android 13+: az értesítéshez (háttérlejátszás vezérlője) engedély kell
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent { LextronicTheme { LextronicApp(vm) } }
    }

    override fun onStart() { super.onStart(); vm.foreground = true; vm.kick(); vm.checkUpdate() }
    override fun onStop() { vm.foreground = false; super.onStop() }
}
