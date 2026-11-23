# Lextronic Rádió – Android app

Ingyenes, reklám nélküli netrádió-kereső és a Lextronic rádió távirányítója.

## Fordítás (Android Studio)
1. Android Studio → **File → Open** → ezt a mappát (LextronicRadio) válaszd ki.
2. Várd meg a Gradle-szinkront (első alkalommal letölti a függőségeket, pár perc).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. Az APK: `app/build/outputs/apk/debug/app-debug.apk` → telefonra másolás, telepítés
   (a telefon rákérdez az „ismeretlen forrás” engedélyre).
   Vagy USB-kábellel, bekapcsolt USB-hibakereséssel: zöld ▶ gomb, és rögtön fel is teszi.

## Mit tud
- **Keresés**: a radio-browser.info nyílt adatbázisában (~50 000 állomás), név, „Csak magyar”, műfaj szerint.
- **Kedvencek**: a telefonon tárolva, úton is működik.
- **Háttérlejátszás**: értesítés, lezárási képernyő, fülhallgató-gombok; kihúzott fülhallgatónál megáll.
- **Rádió fül** (otthon, ugyanazon a WiFi-n): magától megtalálja a lextronic.local-t (mDNS),
  látszik, mi szól, hangerő, némítás, léptetés, a rádió állomáslistája.
- Bármelyik állomás **„Rádióra”** gombbal (hangszóró-ikon): meghallgatás a rádión vagy mentés a listájába.
