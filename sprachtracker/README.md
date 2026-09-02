# Sprechzeit

Ein schlanker Android-Tracker für 30 Tage Sprechpraxis auf Deutsch.

Die App gibt **keine Themen vor**. Sie sagt nur, **wie viele** Themen heute dran sind
und in **welchem Format** — die Themen selbst wählt der Nutzer.

* Ziel: **90 TestDaF-Themen + 90 freie KI-Dialoge** in 30 Tagen (2. September – 1. Oktober 2026)
* Grundplan: 3 + 3 pro Tag
* **Flexibel statt starr:** Was offen bleibt, verfällt nie. Beim nächsten Start verteilt die App
  den Rest gleichmäßig auf die verbleibenden Tage — nie alles auf einen Tag.
* Jeder Tag lässt sich nachträglich korrigieren: Häkchen setzen oder entfernen.
* Ein Zitat pro Tag. In der Übersicht sind nur die Zitate sichtbar, die schon dran waren.
* Feiern in drei Stufen: Konfetti pro Thema, Feuerwerk + Overlay pro vollem Tag,
  ein endloses Finale, wenn alle 180 Themen erledigt sind.

## Aufbau

Eine einzige Activity mit einem WebView; die gesamte Oberfläche liegt in `app/src/main/assets/`.
Keine externen Abhängigkeiten, keine Netzwerkzugriffe zur Laufzeit.

```
app/src/main/
  java/de/sprechzeit/app/MainActivity.java   WebView-Hülle, Speicher, Haptik, Zurück-Taste
  assets/index.html                          Aufbau der beiden Ansichten
  assets/app.css                             Helles, minimalistisches Layout
  assets/app.js                              Plan, Ausgleich, Feuerwerk
  assets/data.js                             36 Tageszitate (DE + RU)
  assets/fonts/                              Playfair Display + Manrope (SIL OFL)
```

Der Fortschritt liegt als JSON in `filesDir/sprechzeit-state.json` (atomar geschrieben),
zusätzlich gespiegelt in `localStorage`.

## Bauen

```bash
export ANDROID_HOME=/pfad/zum/android-sdk
./gradlew assembleDebug          # app/build/outputs/apk/debug/
```

Für ein signiertes Release die vier Eigenschaften mitgeben (oder als Umgebungsvariablen setzen):

```bash
./gradlew assembleRelease \
  -PSPZ_STORE_FILE=/pfad/zum/keystore.jks \
  -PSPZ_STORE_PASSWORD=… -PSPZ_KEY_ALIAS=… -PSPZ_KEY_PASSWORD=…
```

Ohne diese Eigenschaften fällt der Release-Build auf den Debug-Schlüssel zurück,
liefert also trotzdem ein installierbares APK.

## Lizenz der Schriften

Playfair Display und Manrope stehen unter der SIL Open Font License 1.1,
siehe `app/src/main/assets/fonts/OFL.txt`.
