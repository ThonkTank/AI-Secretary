# Auto Secretary – Builds und Updates veröffentlichen

Der Workflow verwendet die vier Profile aus
[ADR-037](architecture/adr-037-risikobasierte-verifikation-und-aktuelles-upgrade.md).
Dokumentation und Hosttests erzeugen keinen Release. Das enge Today-Profil benötigt Contracts,
Build/Lint/Identität/Größe und einen nativen API-35-Animationslauf. Vollständige Änderungen behalten
alle sechs Android-Lanes. CI-/Freigabeänderungen sind selbst vollständig und releasewirksam.
Main kann ausschließlich identische, profil-/policygleiche und vollständig grüne PR-Nachweise
übernehmen. Jeder Produktrelease benötigt den signierten aktuellen Update-Smoke, vollständige
Änderungen zusätzlich die fünf historischen Upgrades auf demselben Kandidaten.

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

## Abschlusszustände einer Änderung

Die zwei automatisiert belegbaren Zustände werden bewusst getrennt gemeldet:

- **Implementiert:** Der beabsichtigte Code- und Dokumentationsstand ist committed, der Pull
  Request hat alle vorgeschriebenen Prüfungen bestanden, wurde per Squash nach `main` übernommen
  und der anwendbare Main-Workflow für den exakten Squash-Stand ist grün. Dieser Zustand allein
  beweist bei einer nicht produktwirksamen Änderung bewusst keine neue App-Version.
- **Veröffentlicht:** Der Workflow für den exakten `main`-Commit ist vollständig grün. Tag,
  Release-Metadaten und `AutoSecretary.apk` sind stabil veröffentlicht und zeigen nachweislich auf
  diesen Commit. Produktionsupgrade, Packaging, Signatur-, Hash-, Paket-, Versions- und
  Trust-Prüfungen wurden auf genau diesem Artefakt ausgeführt.

Für UI-relevante Änderungen gehören normale und animationsaktive Instrumentierung, Goldens,
Accessibility-, Interaktions-, Upgrade- und Updater-Verträge zur automatisierten Matrix. Es gibt
keinen zusätzlichen manuellen Freigabeschritt nach einer vollständig grünen Veröffentlichung.

## Automatischer Ablauf

1. Eine Änderung wird auf einem Themenbranch committed. Der getestete Scope-Classifier entscheidet
   anhand des tatsächlichen Git-Status über Profil und Releasebedarf. Unbekannte, strukturelle
   oder gemischte Änderungen werden konservativ behandelt.
2. Der stabile Check `pull-request-gate` fasst alle für den Pull Request anwendbaren Prüfungen
   zusammen. Das Ruleset von `main` verlangt diesen aktuellen grünen Check und einen Squash-Merge.
   Instrumentierungsfehler laden je API Screenshot, UI-Hierarchie, Logcat und Input-/Displaydaten
   hoch; der ursprüngliche Test-Exitcode bleibt dabei maßgeblich.
3. Der Merge-Commit auf `main` wird erneut klassifiziert. Nur eine produktionswirksame Änderung
   darf Packaging, Upgrade und Publish starten; reine Hosttests und Dokumentation
   veröffentlichen keine App-Version. Upgrade-Probe, Build und Freigabelogik sind releasewirksam.
4. `scripts/release/release_tool.py` schreibt die letzte veröffentlichte Produktversion um genau
   eins fort. Workflownummern beeinflussen die sichtbare Version nicht; ein fehlgeschlagener Lauf
   kann deshalb keine Versionsnummer überspringen.
5. Die aktuelle veröffentlichte Quelle wird einmalig mit Commit, Tag, Schema, Metadaten-/APK-Hash
   und Produktionsidentität gebunden. Sie wird mit dem Kandidaten archiviert.
   Die Produktions-APK wird einmal signiert und auf Paketname, Version, Größe, Hash und
   Zertifikat geprüft. APK, Metadaten, Releaseplan und signiertes Test-APK werden als kurzlebiges
   internes Workflow-Artefakt weitergereicht.
6. Der aktuelle In-place-Smoke auf API 35 erhält Aufgaben, Today-Platzierung und Verlauf auch bei
   unverändertem Schema. Im aktuellen Template bindet `${TODAY}` die offene Fälligkeit und
   ihre Platzierung gemeinsam an den UTC-Planungstag; historische Datumswerte bleiben erhalten.
   So wird eine reguläre Tagesfortschreibung nicht fälschlich als Updateverlust geprüft.
   Fehlende Quelle blockiert Publish. Im vollständigen Profil ergänzt der
   Korpus unter `release/upgrade-fixtures/corpus.json` fünf verpflichtende Lanes:
   Schema 8 aus 0.2.80 auf API 26/35/37 sowie Schema 20 aus 0.2.137 und Schema 23 aus 0.2.158
   jeweils auf API 26. Jede exakt festgelegte Produktions-APK wird installiert und mit ihrer
   Fixture befüllt. `adb install -r` aktualisiert sie auf den Kandidaten; anschließend müssen
   App-Start, höherer Versionscode, Room-Schema und sämtliche erwarteten Testdaten erhalten sein.
7. Ein neuer oder nach einem Fehler wiederaufgenommener Draft erhält genau
   `AutoSecretary.apk` und `release-metadata.json`; vorhandene Assets werden kontrolliert ersetzt.
8. GitHub lädt beide Dateien zur Gegenprüfung erneut herunter, vergleicht die APK byteweise mit
   dem getesteten Kandidaten und veröffentlicht erst danach. Der höchste Build wird als „Latest“
   markiert.

Änderungen am testinternen `TouchGestureDriver` erfordern zusätzlich einen manuell gestarteten,
grünen Lauf von `Today gesture instrumentation soak`. Dieser führt die isolierte Gestensuite nach
jeweils sauberer Deinstallation fünfmal auf API 26 und fünfmal auf API 35 aus und verwendet keine
automatischen Retries.

Ein Fehler vor der abschließenden Prüfung veröffentlicht nichts. Ein Wiederanlauf verwendet einen
vorhandenen Draft, Tag und Assets desselben Commits weiter. Ein bereits veröffentlichter Commit
erzeugt kein Duplikat. Das interne Artefakt trägt zusätzlich die Workflow-Versuchsnummer, damit
auch ein GitHub-Rerun nicht mit einem unveränderlichen Artefakt des vorigen Versuchs kollidiert.

Der Upgrade-Test lädt ausschließlich die drei im Korpus festgeschriebenen Tags. Historische
Schemaexporte und ihre Migrationen bleiben zusätzlich durch schnelle Robolectric- und
Lineage-Tests abgedeckt; der signierte Korpus verspricht gezielt die drei dokumentierten
Produktionsquellen und keinen beliebigen Rolling-Installationspfad. Der normale Unit-Test prüft
alle Fixture-Spalten gegen das exportierte Ausgangs- und Zielschema sowie die zentrale
`DatabaseContract.VERSION`. Das Test-APK wird mit demselben Produktionsschlüssel signiert, damit
es den nicht-debugbaren Releaseprozess vor und nach dem Android-Upgrade prüfen kann. Alle fünf
Upgrade-Lanes und der Veröffentlichungsschritt laden dasselbe interne Produktionsartefakt
herunter; der Veröffentlichungsschritt beweist die Bytegleichheit zusätzlich mit `cmp`.

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
python3 -m unittest discover -s scripts/ci -p 'test_*.py' -v
python3 -m unittest discover -s scripts/release -p 'test_*.py' -v
./gradlew testInstrumentationUnitTest lintDebug assembleDebug assembleInstrumentationAndroidTest assembleRelease
```

Ein lokaler `assembleRelease` bleibt absichtlich unsigned. Ein signierter Build erfordert den
Produktionsschlüssel und `-PrequireReleaseSigning=true`; reguläre signierte Builds entstehen nur
in GitHub Actions.
