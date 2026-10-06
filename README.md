# spheres

Ein Klon des klassischen Puzzlespiels **Dots** für Android.

Verbinde benachbarte Punkte gleicher Farbe (waagerecht/senkrecht) mit dem Finger.
Beim Loslassen verschwinden sie, die Punkte darüber rutschen nach und neue fallen von oben herein.
Schließt du ein **Quadrat** (eine geschlossene Schleife), werden *alle* Punkte dieser Farbe abgeräumt.

## Spielmodi

| Modus  | Regel                          |
|--------|--------------------------------|
| Zeit   | 60 Sekunden (Uhr startet mit der ersten Berührung) |
| Züge   | 30 Züge                        |
| Endlos | ohne Limit                     |

Für jeden Modus wird ein Rekord gespeichert. Mit Ton (synthetisierte, ansteigende Noten) und haptischem Feedback; Ton lässt sich im Menü abschalten.

## Installieren

Fertige APKs liegen unter **Releases** (`spheres-<version>.apk`). Auf dem Handy die Release-Seite öffnen
(das Repo ist privat, also vorher im Browser bei GitHub anmelden), die APK unter *Assets* antippen, öffnen
und „Apps aus dieser Quelle zulassen“ bestätigen. Benötigt Android 8.0 oder neuer.
Neue Versionen lassen sich darüber installieren, Rekorde bleiben erhalten.

Zusätzlich baut GitHub Actions bei jedem Push die APK (Workflow „Build APK“, Artefakt `spheres-apk`).

## Release erstellen

1. `versionName` in `app/build.gradle.kts` setzen (bei Bedarf `versionCode` erhöhen), pushen und warten,
   bis „Build APK“ und „Emulator test“ grün sind.
2. Unter *Actions → Build APK → Run workflow* den Branch wählen und „Create tag … and publish a release“
   anhaken. Der Workflow legt den Tag `v<versionName>` auf dem gebauten Commit an und veröffentlicht die APK.
   Alternativ den Tag selbst pushen: `git tag v1.0.0-beta.1 && git push origin v1.0.0-beta.1`.

Bei einem gepushten Tag bricht der Workflow ab, wenn Tag und `versionName` nicht übereinstimmen. Versionen mit Bindestrich
(`-beta.1`, `-rc.1`) werden als Vorabversion (Pre-release) veröffentlicht.

## Selbst bauen

```sh
./gradlew testDebugUnitTest   # Unit-Tests der Spiellogik
./gradlew assembleRelease     # -> app/build/outputs/apk/release/app-release.apk
```

Benötigt JDK 17+ und das Android SDK (Platform 35).

## Emulator-Test

Der Workflow „Emulator test“ installiert bei jedem Push die Release-APK auf einem Emulator mit
Android 8.0 (API 26, minSdk) und Android 15 (API 35, targetSdk) und führt `.github/e2e/play.py` aus:

- liest die Punktfarben aus Screenshots und spielt ein komplettes 30-Züge-Spiel (lange Pfade, Quadrate),
- prüft nach jedem Zug Punktestand und verbleibende Züge, danach Spielende, Rekord, „Nochmal“ und den Timer,
- startet anschließend `monkey` mit 5000 Zufallseingaben und schlägt bei Absturz oder ANR fehl.

Die Screenshots liegen als Artefakt `screenshots-api-*` am Lauf.

Die APK wird mit `app/spheres.keystore` (Passwort `spheres`) signiert, damit alle Builds denselben
Schlüssel haben und Updates ohne Deinstallation funktionieren. Für eine Veröffentlichung im Play Store
einen eigenen, geheimen Schlüssel verwenden.

## Aufbau

- `game/Board.kt` – reine Spiellogik (Pfad, Quadrate, Nachrutschen, Mischen falls kein Zug mehr möglich), getestet in `BoardTest.kt`
- `game/GameView.kt` – Zeichnen, Touch-Eingabe, Fall- und Pulsanimationen
- `game/Sound.kt` – kleiner Synthesizer für die Töne
- `MainActivity` (Menü) und `GameActivity` (HUD, Timer, Spielende)
