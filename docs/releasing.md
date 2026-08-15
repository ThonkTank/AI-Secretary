# Auto Secretary – Builds und Updates veröffentlichen

Jeder Push auf `main` durchläuft Unit-Tests, Lint, Instrumentierungstests und einen echten
Produktions-Upgrade-Test auf API 26 und API 35. Nur wenn die unabhängigen Qualitätsprüfungen
erfolgreich sind, baut GitHub genau einmal einen signierten Produktionskandidaten. Derselbe
Kandidat wird auf beiden API-Stufen getestet und anschließend bytegleich als stabiles GitHub
Release veröffentlicht.

## Dauerhafter Signaturschlüssel

Das Repository verwendet diese Actions-Secrets:

| Secret | Inhalt |
| --- | --- |
| `KEYSTORE_BASE64` | Base64-kodierter Produktions-Keystore |
| `KEYSTORE_PASSWORD` | Keystore-Passwort |
| `KEYSTORE_ALIAS` | Schlüsselalias; Übergangsfallback `release` |
| `KEY_PASSWORD` | separates Schlüsselpasswort; Übergangsfallback `KEYSTORE_PASSWORD` |

Alias und Passwörter sind unabhängig; die Fallbacks erhalten lediglich die bestehende
Secretkonfiguration. Die Pipeline akzeptiert nur den in `release/release.properties`
festgehaltenen Zertifikat-Fingerprint. App-ID und Signaturkontinuität dürfen nach der ersten
Installation nicht unkontrolliert geändert werden, sonst kann Android spätere APKs nicht als
Update installieren. Backup, Restore, Kompromittierung und die noch nicht aktivierte
Signing-Lineage-Strategie beschreibt das
[Signing- und Recovery-Runbook](signing-and-recovery.md).

## Automatischer Ablauf

1. Eine abgeschlossene Änderung wird auf `main` gepusht.
2. `.github/workflows/verify.yml` führt das vollständige Quality-Gate aus.
3. `scripts/release/release_tool.py` vergibt einen eindeutigen, gegenüber allen vorhandenen
   Kanal-Tags höheren `versionCode` und einen sichtbaren Namen wie `0.2.20`.
4. Die Produktions-APK wird einmal signiert und auf Paketname, Version, Größe, Hash und
   Zertifikat geprüft. APK, Metadaten, Releaseplan und signiertes Test-APK werden als kurzlebiges
   internes Workflow-Artefakt weitergereicht.
5. Die vorige Produktions-APK wird installiert und mit Aufgaben-, Schritt-, Statistik- und
   Einstellungsdaten befüllt. `adb install -r` aktualisiert sie auf den Kandidaten; anschließend
   müssen App-Start, höherer Versionscode, Room-Schema und alle Testdaten erhalten sein.
6. Ein neuer oder nach einem Fehler wiederaufgenommener Draft erhält genau
   `AutoSecretary.apk` und `release-metadata.json`; vorhandene Assets werden kontrolliert ersetzt.
7. GitHub lädt beide Dateien zur Gegenprüfung erneut herunter, vergleicht die APK byteweise mit
   dem getesteten Kandidaten und veröffentlicht erst danach. Der höchste Build wird als „Latest“
   markiert.

Ein Fehler vor der abschließenden Prüfung veröffentlicht nichts. Ein Wiederanlauf verwendet einen
vorhandenen Draft, Tag und Assets desselben Commits weiter. Ein bereits veröffentlichter Commit
erzeugt kein Duplikat. Das interne Artefakt trägt zusätzlich die Workflow-Versuchsnummer, damit
auch ein GitHub-Rerun nicht mit einem unveränderlichen Artefakt des vorigen Versuchs kollidiert.

Der Upgrade-Test wählt dynamisch den höchsten bereits veröffentlichten `forest-android-`-Build
unterhalb des Kandidaten. Das Test-APK wird mit demselben Produktionsschlüssel signiert, damit es
den nicht-debugbaren Releaseprozess vor und nach dem Android-Upgrade prüfen kann. API 26, API 35
und der Veröffentlichungsschritt laden dasselbe interne Produktionsartefakt herunter; der
Veröffentlichungsschritt beweist die Bytegleichheit zusätzlich mit `cmp`.

## Releasewerkzeug lokal prüfen

Die versionierte Python-Implementierung ist die gemeinsame Vertragslogik für lokale Prüfungen und
GitHub Actions. Sie benötigt nur Python aus der Standardbibliothek:

```bash
python3 -m unittest discover -s scripts/release -p 'test_*.py' -v
python3 scripts/release/release_tool.py --help
```

Das Werkzeug validiert `release/release.properties`, plant neue oder wiederaufzunehmende Releases,
erzeugt und prüft Metadaten, wählt den vorherigen stabilen Build und validiert bereits vorhandene
Git-Tags. GitHub-spezifische Zugriffe bleiben bewusst im Workflow; die fachlichen Entscheidungen
liegen im lokal testbaren Werkzeug.

## Update in der App

Die App prüft höchstens einmal täglich nach dem Start und jederzeit manuell unter
**Optionen → Updates**. Ein Download wird vor der Installation auf Größe, SHA-256, Paketname,
Versionscode und dieselbe Android-Signatur wie die installierte App geprüft. Anschließend öffnet
die App den Android-Systeminstaller; dessen Bestätigung kann und soll nicht umgangen werden.

Releasefeed, Metadaten, APK und jeder Redirect-Hop müssen HTTPS und einer exakten GitHub-
Host-Allowlist entsprechen. Antworten sind größenbegrenzt; Timeouts, Rate-Limits und temporäre
Serverfehler werden höchstens dreimal mit kurzem exponentiellem Backoff versucht. Ein Abbruch
beendet den Transfer typisiert. Die APK bleibt bis zum erfolgreichen Hash-, Paket-, Versions-,
Commit- und Signaturnachweis eine temporäre Datei und wird erst dann atomisch als installierbar
finalisiert. Eine unbekannte Quelle oder eine nicht vollständig verifizierte APK erreicht den
Android-Systeminstaller nicht.

Diese Remote-Prüfungen sind ausschließlich in Produktions-/Release-Builds aktiv. Debug-Builds
verwenden eine explizite netzwerkfreie Development-Konfiguration; die manuelle Schaltfläche kann
dort deshalb weder GitHub noch einen anderen echten Updateendpunkt kontaktieren.

Beim ersten Wechsel von einem lokal oder durch CI debug-signierten Build muss dieser Build
einmalig deinstalliert werden. Ab der Installation des ersten Produktions-Releases bleiben lokale
Aufgaben bei allen folgenden Updates erhalten.

## Release lokal prüfen

Der lokale Quality-Gate bleibt:

```bash
python3 -m unittest discover -s scripts/release -p 'test_*.py' -v
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
```

Ein lokaler `assembleRelease` bleibt absichtlich unsigned. Ein signierter Build erfordert den
Produktionsschlüssel und `-PrequireReleaseSigning=true`; reguläre signierte Builds entstehen nur
in GitHub Actions.
