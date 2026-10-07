# spheres – Hinweise für Claude

Android-Klon des Puzzlespiels **Dots** (Kotlin, ohne AndroidX, minSdk 26, targetSdk 35).
Mit dem Nutzer wird **Deutsch** gesprochen; Code, Kommentare und Commit-Messages sind **Englisch**.

## Arbeitsweise

- **Jede Aufgabe gehört zu einem GitHub-Issue.** Anforderungen und Entscheidungen werden am Issue
  festgehalten (Kommentar mit Checkliste), nach der Umsetzung Punkt für Punkt mit Nachweis abgehakt.
- Offene Design-Fragen nicht still entscheiden: Startwerte wählen, als Annahme kennzeichnen und
  im Issue zur Entscheidung stellen.

## Branches, Sessions und Pull Requests

Es arbeiten mehrere Claude-Sessions parallel, **eine Session pro Issue**.

- **`main`** ist der Standard-Branch und enthält nur gemergte, auf dem Gerät geprüfte Stände.
  Nie direkt auf `main` committen.
- Jede Session arbeitet auf **ihrem eigenen Branch** (der Branch, den die Session vorgibt, sonst
  `issue-<nr>-<kurzname>`, z. B. `issue-5-endless-survival`), abgezweigt vom aktuellen `main`.
  Nie auf fremden Branches committen.
- Commits referenzieren das Issue (`refs #5`).
- **Jede Änderung kommt per Pull Request nach `main`**, auch kleine Korrekturen und Änderungen an
  Regeln oder Workflows. Den Pull Request gegen `main` gleich nach dem ersten Push öffnen (als Entwurf,
  solange nicht fertig): Die Workflows laufen nur für Pull Requests und `main`, nicht für einzelne Branches.
  Beschreibung mit Bezug aufs Issue (`refs #5`, nicht `fixes`) und der Checkliste.
- Ist die Umsetzung fertig, die Checkliste abgehakt und sind beide Workflows im Pull Request grün:
  Pull Request als bereit markieren.
- Für Tests auf dem Handy kann von einem Branch eine Vorab-APK veröffentlicht werden (siehe Release).
  Versionsnummer dann nur auf dem eigenen Branch erhöhen; bei Konflikten in `app/build.gradle.kts`
  beim Mergen die höhere Version nehmen.
- **Der Nutzer** testet auf dem Gerät, mergt den Pull Request und schließt das Issue. Issues werden
  nie automatisch geschlossen.
- Hat sich `main` inzwischen geändert: `main` in den eigenen Branch mergen (kein Rebase/Force-Push
  auf geteilten Branches), Konflikte lösen, Workflows erneut abwarten.
- Mehrere Branches ändern oft dieselben Dateien (`GameActivity.kt`, `play.py`, `strings.xml`):
  Änderungen klein und auf das Issue begrenzt halten, nichts nebenbei umbauen.

## Bauen und Testen

Das Android-SDK ist in Claude-Cloud-Sessions **nicht verfügbar** (dl.google.com / Google Maven gesperrt).
Gebaut und getestet wird über GitHub Actions:

| Workflow | Datei | Inhalt |
|---|---|---|
| Build APK | `.github/workflows/build.yml` | Unit-Tests, signierte Release-APK (R8), optional Release |
| Emulator test | `.github/workflows/emulator.yml` | `.github/e2e/play.py` auf API 26 und 35 |

Beide laufen bei Pull Requests gegen `main` (auf dem Merge-Ergebnis mit `main`), bei Pushes auf `main`
und manuell (`workflow_dispatch`). Ein neuer Push in einen Pull Request bricht dessen alten Lauf ab.
Der Emulator-Test nutzt den Build-Typ `e2e`: die Release-APK (R8), aber debuggable, damit `play.py`
per `run-as` Daten setzen kann (z. B. das Punktekonto auffüllen).

- **Unit-Tests lokal** ohne SDK: Spiel-Logik liegt bewusst in Android-freien Klassen
  (`Board`, `PathTracker`, `Tones`, `SavedGame`, `PowerUp`, `Difficulty`). Neue Logik ebenso trennen
  und in `app/src/test` testen.
- **Emulator-Test (`play.py`)** spielt die App per adb: liest Punktfarben aus Screenshots, prüft HUD
  per `uiautomator`. Neue Features bekommen dort eine Prüfung. Der Emulator ist langsam – nicht feste
  Wartezeiten annehmen, sondern auf den erwarteten Zustand warten.
- **Ergebnisse lesen** (Artefakt-Downloads und Job-Logs sind aus der Session gesperrt):
  - Prüfergebnisse: `gh api repos/psIQos/spheres/check-runs/<job-id>/annotations`
    (Notice „API xx: n/m checks passed“ + Fehler als Annotation)
  - Screenshots: Annotation „screenshots api-xx“ listet Git-Blob-SHAs →
    `gh api repos/psIQos/spheres/git/blobs/<sha> --jq .content | base64 -d > shot.png`
- Vor dem Pushen: Unit-Tests grün; nach dem Pushen: beide Workflows im Pull Request abwarten.
  Ein rotes Ergebnis erst analysieren (Test- oder App-Fehler?), nie Tests abschwächen, um grün zu werden.

## Release

1. `versionName` (Schema `1.0.0-beta.N`) und `versionCode` in `app/build.gradle.kts` erhöhen.
2. Beide Workflows im Pull Request grün abwarten.
3. *Build APK* manuell mit `release: true` starten (`workflow_dispatch`), auf dem Branch des Pull Requests
   (Vorab-APK zum Testen) oder auf `main`: legt Tag `v<versionName>` an und
   veröffentlicht `spheres-<version>.apk`; Versionen mit Bindestrich werden Pre-release.
   (Tags direkt pushen ist aus Cloud-Sessions nicht erlaubt.)

Die APK ist mit `app/spheres.keystore` signiert (bewusst im Repo), damit Updates ohne Deinstallation gehen.

## Code-Überblick

- `game/Board.kt` – Spielfeld, Pfade, Quadrate, Nachrutschen, Power-up-Effekte
- `game/PathTracker.kt` – Touch → Pfad (Trefferzonen, Zurückwischen), unit-getestet mit simulierten Gesten
- `game/GameView.kt` – Zeichnen, Animationen, Touch, Ziel-Modus für Power-ups
- `game/Sound.kt`, `game/Tones.kt`, `game/Haptics.kt` – synthetisierte Töne (SoundPool), Vibration
- `GameActivity.kt` – HUD, Timer, Pause/Fortsetzen, Speichern, Power-ups
- `GameMode.kt` – Modi, `Difficulty`, `Prefs` (Rekorde/Konto je Schwierigkeit, Einstellungen)
- `SavedGame.kt`, `PowerUp.kt` – gespeicherter Spielstand, Power-ups und Punktekonto
- `MainActivity.kt`, `SettingsActivity.kt` – Menü und Einstellungen

Gespeicherte Daten bleiben über Updates erhalten: Schlüssel in `Prefs` nicht umbenennen, Formate
(`SavedGame.encode`) nur abwärtskompatibel erweitern.
