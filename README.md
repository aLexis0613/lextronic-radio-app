# Lextronic Rádió – Android app

Ingyenes, reklám nélküli netrádió- és zenelejátszó, valamint a saját építésű **Lextronic** ESP32-S3 internetrádió távirányítója.

## Telepítés

1. Telefonon nyisd meg: **[Releases → latest](https://github.com/aLexis0613/lextronic-radio-app/releases/tag/latest)**
2. Töltsd le a `LextronicRadio.apk`-t, és nyisd meg.
3. Ha a telefon rákérdez, engedélyezd a telepítést ebből a forrásból (böngésző / fájlkezelő).
   Play Protect figyelmeztetésnél: *További részletek → Telepítés mindenképp*.

Android 8.0 vagy újabb kell hozzá.

### Frissítés
Telepítés után már nem kell letöltögetni: **az app induláskor magától szól, ha van újabb verzió**
(vagy oldalsáv ☰ → *Frissítés keresése*). A *Frissítés* gomb letölti és átadja a telefon telepítőjének;
első alkalommal engedélyezni kell, hogy az app telepíthessen.

> Ha a régebbi, még nem a GitHubról származó verzió van fent, egyszer törölni kell és újratelepíteni
> (más kulccsal van aláírva). Előtte mentsd ki a kedvenceket: ☰ → *Kedvencek → Mentés / megosztás*,
> utána ☰ → *Importálás vágólapról*.

## Mit tud
- **Netrádió-kereső** a nyílt [radio-browser.info](https://www.radio-browser.info) adatbázisból (~50 000 állomás): név, ország, műfaj szerint.
- **Kedvencek** a telefonon – úton, mobilneten is; mentés és visszatöltés (új telefonra, barátnak átküldve).
- **Helyi zenék** a telefon tárhelyéről, fülekkel: *Számok, Albumok, Előadók, Legutóbbi* (max. 50);
  mindig az a lista lesz a lejátszási sor, amelyikből választasz (album, előadó, összes szám…). Keverés, ismétlés, pozíciócsúszka.
- **Külön előzmények**: rádiózás közben a legutóbbi adók, helyi zenénél a legutóbbi zenék látszanak.
- **„Most szól” képernyő**: borító/logó, spektrum (saját FFT, mikrofon-engedély nélkül), SSD1322-kijelző stílusú
  információs panel (ICY-számcím), vezérlők, alul a legutóbb játszottak kis keresővel.
- **5 sávos hangszínszabályzó** előbeállításokkal.
- **Kezdőképernyő-widgetek** két stílusban:
  - *kompakt* (4×1): adó, számcím, lejátszás/szünet, következő kedvenc;
  - *OLED panel* (4×2): kijelző-stílus a hangszínszabályzó sávjaival, előző/következő kedvenc, csillag.
- **Háttérlejátszás**: értesítés, lezárási képernyő, fülhallgató-gombok.
- **Élő adás frissen tartva**: hívás vagy hosszabb szünet után újracsatlakozik (nem a régi pufferből szól),
  WiFi ↔ mobilnet váltáskor azonnal átvált az új hálózatra.
- **Értesítések nem akasztják meg**: értesítési hangnál csak lehalkít, némított módban hozzá sem nyúl; hívásnál megáll, utána folytatja.
- **Rádió fül** (otthon, WiFi-n): magától megtalálja a `lextronic.local`-t; mi szól, hangerő, léptetés, állomáslista,
  és bármelyik adó átküldhető a rádióra (meghallgatás vagy mentés).
- Energiatakarékos: mobilneten nem keresi a rádiót, háttérben nem kérdez le semmit, AMOLED-fekete felület.

## Fejlesztés

**Fordítás:** Android Studio → *File → New → Project from Version Control* → ez a repó → Gradle-szinkron → ▶ Run.
Kotlin, Jetpack Compose, Media3/ExoPlayer, Glance (widgetek), Coil; minSdk 26, targetSdk 36, compileSdk 37, AGP 9.2.

**Kiadás:** minden `main`-re pusholt commit után a GitHub Actions lefordítja az aláírt APK-t, és a
`latest` kiadásba teszi `version.json`-nal együtt – az appok ebből veszik észre a frissítést.
A **commit üzenete jelenik meg a frissítési ablakban**, érdemes úgy írni, hogy a felhasználó is értse.
A verziószám a commitok számából jön (`versionCode = 100 + commitok`).

**Aláírás:** a saját kulcs sosem kerül a repóba.
- Helyben: `keystore.properties` a projekt gyökerében (a `.gitignore` kizárja):
  ```
  storeFile=C:/út/a/kulcshoz/lextronic.jks
  storePassword=…
  keyAlias=lextronic
  keyPassword=…
  ```
- GitHubon: *Settings → Secrets and variables → Actions*: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Kulcs nélkül is lefordul (debug kulccsal), de az így készült APK nem tud a kiadott verzióra frissülni.
