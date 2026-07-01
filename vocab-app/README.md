# Deutsch B1 · Wortschatz

Ein einfacher Android-Vokabeltrainer für die **Goethe-Zertifikat B1 Wortliste** (2869 Wörter).

## Funktionen
- 📋 Alle **2869 Wörter** der offiziellen B1-Liste (aus dem PDF übernommen, nichts ausgelassen)
- ☑️ Jedes Wort mit einer **Checkbox** als „gelernt“ markieren
- 📊 **Fortschritt** oben: in Prozent und als Zähler (z. B. `320 / 2869 gelernt · 11%`)
- 💾 Fortschritt wird **auf dem Gerät gespeichert** und bleibt nach dem Neustart erhalten
- 🔎 **Suche/Filter** über alle Wörter
- 🎨 Helles, farbiges und ruhiges Design (Material 3)

## Installation der APK
1. Die Datei `DeutschB1-Wortschatz.apk` (im Repo-Wurzelverzeichnis) auf das Android-Telefon herunterladen.
2. In den Einstellungen **„Installation aus unbekannten Quellen / diese Quelle zulassen“** aktivieren.
3. Die APK öffnen und installieren.

> Hinweis: Dies ist ein **Debug-Build** zum direkten Ausprobieren (nicht signiert für den Play Store).

## Selbst bauen
```bash
cd vocab-app
./gradlew assembleDebug
# Ergebnis: app/build/outputs/apk/debug/app-debug.apk
```
Voraussetzungen: JDK 17, Android SDK (Platform 34, Build-Tools 34). `local.properties` mit `sdk.dir=<Pfad zum Android SDK>`.

## Projektstruktur
- `app/src/main/assets/words.txt` — die 2869 Wörter (eine Zeile pro Eintrag)
- `app/src/main/java/com/arslan/b1vokabeln/` — `MainActivity`, `WordAdapter`, `Word`
- `app/src/main/res/` — Layouts, Farben, Theme, Launcher-Icons
