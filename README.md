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

Die APK baut GitHub Actions bei jedem Push (Workflow „Build APK“, Artefakt `spheres-apk`).
Ein Tag `v*` (z. B. `v1.0`) veröffentlicht die APK zusätzlich als GitHub-Release.

Auf dem Handy die `spheres.apk` öffnen und „Installation aus unbekannten Quellen“ erlauben. Benötigt Android 8.0 oder neuer.

## Selbst bauen

```sh
./gradlew testDebugUnitTest   # Unit-Tests der Spiellogik
./gradlew assembleRelease     # -> app/build/outputs/apk/release/app-release.apk
```

Benötigt JDK 17+ und das Android SDK (Platform 35).

Die APK wird mit `app/spheres.keystore` (Passwort `spheres`) signiert, damit alle Builds denselben
Schlüssel haben und Updates ohne Deinstallation funktionieren. Für eine Veröffentlichung im Play Store
einen eigenen, geheimen Schlüssel verwenden.

## Aufbau

- `game/Board.kt` – reine Spiellogik (Pfad, Quadrate, Nachrutschen, Mischen falls kein Zug mehr möglich), getestet in `BoardTest.kt`
- `game/GameView.kt` – Zeichnen, Touch-Eingabe, Fall- und Pulsanimationen
- `game/Sound.kt` – kleiner Synthesizer für die Töne
- `MainActivity` (Menü) und `GameActivity` (HUD, Timer, Spielende)
