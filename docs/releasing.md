# Auto Secretary – Builds und Updates veröffentlichen

Der Workflow klassifiziert jede Änderung getrennt für Host-Qualität, Android-Instrumentierung und
Produktveröffentlichung. Reine Änderungen unter `docs/` sowie an Repository-Markdown bestehen mit
dem Scope- und PR-Sammelcheck, ohne einen Android-Build zu starten. Andere Nicht-Produktänderungen
durchlaufen das passende Quality- beziehungsweise Instrumentierungs-Gate, erzeugen aber keine neue
App-Version. Produkt- und eingebettete Vertragsänderungen werden schon im Pull Request auf API 26
und API 35 instrumentiert. Nach dem Merge wiederholt `main` diese Prüfungen für den exakten
Release-Commit und führt zusätzlich den echten Produktions-Upgrade-Test auf beiden APIs aus.

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

Die drei Zustände werden bewusst getrennt gemeldet:

- **Implementiert:** Der beabsichtigte Code- und Dokumentationsstand ist committed, der Pull
  Request hat alle vorgeschriebenen Prüfungen bestanden und wurde nach `main` übernommen. Dieser
  Zustand allein beweist noch keine veröffentlichte oder installierte App-Version.
- **Veröffentlicht:** Der Workflow für den exakten `main`-Commit ist vollständig grün. Tag,
  Release-Metadaten und `AutoSecretary.apk` sind stabil veröffentlicht und zeigen nachweislich auf
  diesen Commit.
- **Auf Gerät abgenommen:** Bei einer UI-relevanten Änderung wurde die veröffentlichte APK über
  den In-App-Updater auf einem physischen Gerät installiert. Installierte Version, Datenerhalt und
  die betroffenen Interaktionen wurden dort geprüft.

Eine UI-relevante Phase gilt erst mit dem dritten Zustand als vollständig abgeschlossen. Ist kein
autorisiertes Gerät verfügbar, darf der sichere Releaseprozess weiterlaufen; der Status muss dann
ausdrücklich **Geräteabnahme ausstehend** lauten und darf nicht als vollständiger Abschluss
bezeichnet werden.

## Automatischer Ablauf

1. Eine Änderung wird auf einem Themenbranch committed. Der getestete Scope-Classifier entscheidet
   unabhängig, ob Quality, API-26/35-Instrumentierung und später ein Produktrelease erforderlich
   sind.
2. Der stabile Check `pull-request-gate` fasst alle für den Pull Request anwendbaren Prüfungen
   zusammen. Das Ruleset von `main` verlangt diesen aktuellen grünen Check und einen Squash-Merge.
   Instrumentierungsfehler laden je API Screenshot, UI-Hierarchie, Logcat und Input-/Displaydaten
   hoch; der ursprüngliche Test-Exitcode bleibt dabei maßgeblich.
3. Der Merge-Commit auf `main` wird erneut klassifiziert. Nur eine produktionswirksame Änderung
   darf Packaging, Upgrade und Publish starten; Test-, Workflow- und Dokumentationsänderungen
   veröffentlichen keine App-Version.
4. `scripts/release/release_tool.py` schreibt die letzte veröffentlichte Produktversion um genau
   eins fort. Workflownummern beeinflussen die sichtbare Version nicht; ein fehlgeschlagener Lauf
   kann deshalb keine Versionsnummer überspringen.
5. Die Produktions-APK wird einmal signiert und auf Paketname, Version, Größe, Hash und
   Zertifikat geprüft. APK, Metadaten, Releaseplan und signiertes Test-APK werden als kurzlebiges
   internes Workflow-Artefakt weitergereicht.
6. Die explizit unterstützte Produktions-APK 0.2.80 wird installiert und mit dem versionierten
   Fixture `release/upgrade-fixtures/v0.2.80.json` befüllt. `adb install -r` aktualisiert sie auf
   den Kandidaten; anschließend müssen App-Start, höherer Versionscode, Room-Schema und alle
   erwarteten Testdaten erhalten sein.
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

Der Upgrade-Test lädt ausschließlich den in `release/release.properties` festgelegten
0.2.80-Tag. Historische Schemaexporte und ihre Migrationen bleiben durch schnelle
Robolectric-Tests abgedeckt, sind aber kein versprochener Rolling-Installationspfad. Der normale
Unit-Test prüft außerdem alle Fixture-Spalten gegen das exportierte Ausgangs- und Zielschema sowie
die zentrale `DatabaseContract.VERSION`. Das Test-APK wird mit demselben Produktionsschlüssel signiert, damit es
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
python3 -m unittest discover -s scripts/ci -p 'test_*.py' -v
python3 -m unittest discover -s scripts/release -p 'test_*.py' -v
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
```

Ein lokaler `assembleRelease` bleibt absichtlich unsigned. Ein signierter Build erfordert den
Produktionsschlüssel und `-PrequireReleaseSigning=true`; reguläre signierte Builds entstehen nur
in GitHub Actions.
