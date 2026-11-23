# Lextronic Rádió – Android app

Ingyenes, reklám nélküli netrádió- és zenelejátszó, valamint a saját építésű **Lextronic** ESP32-S3 internetrádió távirányítója.

## Mit tud
- **Netrádió-kereső** a nyílt [radio-browser.info](https://www.radio-browser.info) adatbázisból (~50 000 állomás): név, ország, műfaj szerint.
- **Kedvencek** a telefonon – úton, mobilneten is.
- **Helyi zenék** a telefon tárhelyéről: kereshető lista, keverés, ismétlés, előző/következő, pozíciócsúszka.
- **„Most szól” képernyő**: borító/logó, spektrum (saját FFT, mikrofon-engedély nélkül), SSD1322-kijelző stílusú
  információs panel (ICY-számcím), vezérlők, alul a legutóbb játszottak kis keresővel.
- **5 sávos hangszínszabályzó** előbeállításokkal.
- **Háttérlejátszás**: értesítés, lezárási képernyő, fülhallgató-gombok.
- **Rádió fül** (otthon, WiFi-n): magától megtalálja a `lextronic.local`-t; mi szól, hangerő, léptetés, állomáslista,
  és bármelyik adó átküldhető a rádióra (meghallgatás vagy mentés).
- Energiatakarékos: mobilneten nem keresi a rádiót, háttérben nem kérdez le semmit, AMOLED-fekete felület.

## Letöltés
A legfrissebb APK mindig a **Releases → latest** alatt található (GitHub Actions fordítja minden módosítás után).

## Fordítás
Android Studio → *File → Open* → ez a mappa → várd meg a Gradle-szinkront → ▶ Run.
(Kotlin, Jetpack Compose, Media3/ExoPlayer, Coil; minSdk 26, targetSdk 35.)
