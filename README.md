# spheres

Ein Klon des klassischen Puzzlespiels **Dots** für Android.

Verbinde benachbarte Punkte gleicher Farbe (waagerecht/senkrecht) mit dem Finger.
Beim Loslassen verschwinden sie, die Punkte darüber rutschen nach und neue fallen von oben herein.
Schließt du ein **Quadrat** (eine geschlossene Schleife), werden *alle* Punkte dieser Farbe abgeräumt.

## Spielmodi

| Modus  | Regel                          |
|--------|--------------------------------|
| Zeit   | Zeitlimit (Uhr startet mit der ersten Berührung) |
| Züge   | begrenzte Anzahl Züge          |
| Endlos | ohne Limit                     |

Mit „Pause“ oder der Zurück-Taste wird ein laufendes Spiel pausiert (Uhr steht). Der Spielstand wird je
Modus gespeichert, auch wenn Android die App beendet, und lässt sich im Menü fortsetzen. Wer die App
verlässt und zurückkommt oder ein Spiel aus dem Menü fortsetzt, spielt direkt weiter; die Uhr läuft
erst mit der nächsten Berührung weiter.

## Power-ups

Jeder abgeräumte Punkt landet auf einem Punktekonto, das über alle Spiele erhalten bleibt
(wie im Original-Dots). Damit kauft man im Spiel Power-ups über die Leiste unter dem Spielfeld:

| Power-up       | Kosten | Wirkung                                        | Modi        |
|----------------|--------|------------------------------------------------|-------------|
| Schrumpfer     | 30     | einen gewählten Punkt entfernen                | alle        |
| Zeitstopp      | 60     | Uhr steht 5 Sekunden                           | Zeit        |
| +3 Züge        | 60     | drei zusätzliche Züge                          | Züge        |
| Expander       | 120    | alle Punkte der angetippten Farbe entfernen    | alle        |

Schrumpfer und Expander werden erst bezahlt, wenn ein Punkt angetippt wurde; erneutes Tippen auf den
Button bricht ab. Mit Power-ups entfernte Punkte zählen zum Spielstand, aber nicht als Zug und nicht
für das Punktekonto.

## Einstellungen

Schwierigkeit, Ton, Vibration und „Alle Rekorde zurücksetzen“ (mit Sicherheitsabfrage).

## Schwierigkeit

In den Einstellungen wählbar, gilt für alle Modi:

|                | Leicht | Normal | Schwer |
|----------------|--------|--------|--------|
| Farben         | 4      | 5      | 6      |
| Spielfeld      | 6×6    | 6×6    | 7×7    |
| Zeit           | 75 s   | 60 s   | 45 s   |
| Züge           | 35     | 30     | 25     |

Weniger Farben ergeben mehr lange Pfade und Quadrate. Für jede Kombination aus Modus und Schwierigkeit
wird ein eigener Rekord gespeichert. Rekorde aus Versionen vor der Schwierigkeitseinstellung zählen als „Normal“.

Mit Ton (synthetisierte, ansteigende Noten) und haptischem Feedback, beides in den Einstellungen abschaltbar.

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
- wischt sichtbar ungenau über einen Pfad zurück,
- spielt eine Runde auf „Schwer“ (7×7, 6 Farben), prüft „Leicht“ und dass die Rekorde je Schwierigkeit getrennt bleiben,
- pausiert (Uhr muss stehen), verlässt ein Spiel, beendet die App hart und setzt mit gleichem Brett und Punktestand fort,
- prüft das Einstellungsmenü und das Zurücksetzen der Rekorde,
- setzt jedes Power-up ein und prüft Kosten, Punkte, Züge und den angehaltenen Timer,
- prüft (ab Android 12) im Vibrations-Log des Systems, dass Verbinden vibriert und der Schalter in den Einstellungen das abstellt,
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
