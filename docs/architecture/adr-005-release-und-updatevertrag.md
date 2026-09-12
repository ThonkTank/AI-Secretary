# ADR-005: Release- und Updatevertrag

- Status: angenommen
- Datum: 2026-08-15

## Kontext

Die Android-Anwendung wird außerhalb eines App-Stores als signierte APK über GitHub Releases
verteilt. Android akzeptiert eine APK nur als Aktualisierung, wenn Paketname und Signatur zur
installierten Anwendung passen und der neue Versionscode größer ist. Frühere Repositorystände
verwendeten andere Paketnamen, Versionsreihen und Releaseformate; deren Tags sind deshalb kein
verlässlicher Updatekanal für die heutige Anwendung.

Diese ADR hält den derzeit produktiven Vertrag fest. Änderungen daran benötigen eine neue ADR,
eine Migrationsstrategie und passende Upgrade-Tests. Die maschinenlesbare Quelle für die
konkreten Konstanten bleibt `release/release.properties`.

## Entscheidung

### Paketidentität und Versionierung

- Der produktive Paketname und Android-Namespace sind `de.thonktank.autosecretary`.
- Der Updatekanal berücksichtigt ausschließlich Tags mit dem Präfix `forest-android-`.
- Ein Release-Tag enthält nach dem Präfix den dezimalen Android-`versionCode`.
- Die aktuelle sichtbare Versionsreihe ist `0.2`.
- Die nächste Produktversion folgt der höchsten stabil veröffentlichten Produktversion, unabhängig
  von der GitHub-Workflownummer. Der Code bleibt im bestehenden Format
  `versionCodeFloor + Produktnummer * 100 + Versuch` monoton.
- Ein belegter Draft kann für denselben Commit wiederaufgenommen werden. Andernfalls wird ein
  freier Code im nächsten Produktfenster gewählt. Bereits veröffentlichte Commits werden nicht
  erneut veröffentlicht. Lokale Entwicklungswerte sind keine Quelle für Produktionsversionen.

### Signatur und Vertrauensmodell

- Produktions-Releases werden mit dem permanenten Android-Schlüssel signiert. Der Alias ist eine
  austauschbare Keystore-Eigenschaft und keine Android-Paketidentität.
- Der erwartete SHA-256-Zertifikat-Fingerprint ist in `release/release.properties` hinterlegt.
- Der Keystore wird GitHub Actions über `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
  `KEYSTORE_ALIAS` und `KEY_PASSWORD` bereitgestellt. Zur Abwärtskompatibilität fällt ein nicht
  gesetzter Alias auf `release` und ein nicht gesetztes Key-Passwort auf das Store-Passwort
  zurück; Workflow, Gradle und `apksigner` behandeln die Werte unabhängig.
- Die Pipeline bricht ab, wenn Keystore oder fertige APK nicht den festgelegten Fingerprint
  besitzen.
- Die App vertraut nicht allein auf GitHub-Metadaten. Vor einer Installation müssen Paketname,
  höherer Versionscode, Dateigröße, SHA-256 und APK-Signatur stimmen. Die APK-Signatur muss
  außerdem mit der Signatur der installierten Anwendung übereinstimmen.
- Der Android-Systeminstaller bleibt die einzige Installationsinstanz. Die App umgeht dessen
  Bestätigung und Berechtigungsprüfung nicht.

Backup, Wiederherstellung, Incident Response und die Voraussetzungen einer späteren Rotation
sind in [Produktionssignatur, Recovery und Repositorybetrieb](../signing-and-recovery.md)
dokumentiert. Der aktuelle Kanal besitzt noch keine Signing Lineage; eine Rotation erfordert
deshalb vor jeder Schlüsseländerung eine eigene getestete Migrationsphase.

### Release-Artefakte und Metadaten

Ein stabiles Release gehört zum Repository `ThonkTank/AI-Secretary`, zeigt auf den gebauten
Commit und enthält genau diese Updateartefakte:

| Asset | Bedeutung |
| --- | --- |
| `AutoSecretary.apk` | signierte, installierbare Produktions-APK |
| `release-metadata.json` | maschinenlesbarer Integritäts- und Versionsvertrag |

Das Metadatenformat hat `schemaVersion` 1 und enthält:

- `versionCode` und `versionName`
- `packageName` und `apkAsset`
- `apkSizeBytes` und den kleingeschriebenen SHA-256-Wert `sha256`
- den kleingeschriebenen Zertifikat-Fingerprint `signerSha256`
- den vollständigen Git-Commit `commitSha`

Die Pipeline baut und signiert einen Produktionskandidaten genau einmal. Der aktuelle signierte
Update-Smoke, die nach [ADR-037](adr-037-risikobasierte-verifikation-und-aktuelles-upgrade.md) anwendbaren historischen Upgrade-Lanes
und der Veröffentlichungsschritt verwenden dieses
interne Workflow-Artefakt. Die Pipeline erstellt oder übernimmt anschließend einen Draft, lädt
beide öffentlichen Assets hoch, lädt sie als Beweis erneut herunter und veröffentlicht erst nach
erfolgreicher Hash-, Commit- und Byteprüfung. Der Release mit dem höchsten Versionscode im
gültigen Tagformat wird als `Latest` markiert.

Die fachliche Releaseplanung und Metadatenprüfung liegt in
`scripts/release/release_tool.py`. Das Werkzeug ist lokal ausführbar, verwendet dieselbe
`release/release.properties` wie GitHub Actions und unterscheidet neue Drafts, wiederaufzunehmende
Drafts und bereits veröffentlichte Commits. Vorhandene Tags werden nur übernommen, wenn sie auf
den geplanten Commit zeigen. Die Workflow-Actions sind auf vollständige Commit-SHAs gepinnt.

### Updateverhalten

- Die App prüft nach dem Start höchstens einmal innerhalb von 24 Stunden automatisch und kann
  jederzeit manuell unter **Optionen → Updates** prüfen.
- Es wird der gültige stabile Release mit dem höchsten Versionscode gewählt; Drafts,
  Vorabversionen, unbekannte Tagformate und unvollständige Assets werden ignoriert.
- Ein Update wird nur angeboten, wenn sein Versionscode größer als der installierte ist.
- Ein aufgeschobenes Update wird für 24 Stunden nicht automatisch angeboten, bleibt aber über
  die manuelle Prüfung erreichbar.
- Download und Prüfung finden vor dem Öffnen des Android-Systeminstallers statt. Eine fehlende
  Berechtigung für unbekannte Installationsquellen führt zuerst in die zugehörige
  Systemeinstellung.
- Störungen beim Prüfen oder Herunterladen verändern keine Aufgaben oder Kalenderdaten und
  verhindern nicht die normale Nutzung der App.

### CI-Gates

Die Prüfauswahl richtet sich nach [ADR-037](adr-037-risikobasierte-verifikation-und-aktuelles-upgrade.md). `docs`, `host`, `today` und `full`
verwenden denselben Classifier lokal, im PR und auf Main. `full` enthält weiterhin Unit-/Room-/
Golden-Tests, Lint, Builds, Größen-/Identitätsprüfungen und sechs normale/animierte Android-Lanes
auf API 26, 35 und 37. `today` ist auf zwei geprüfte plattformfreie Weiterleitungsdateien begrenzt
und verlangt Contracts inklusive produktivem Today-Bedienweg, Build/Lint/Identität/Größe und die
native API-35-Today-Suite mit Animationen. Fehlende oder unklare Einordnung führt zu `full`.

Jeder Produktrelease benötigt den signierten aktuellen In-place-Update-Smoke. Im vollständigen
Profil bleiben zusätzlich diese sechs historischen Lanes verpflichtend:

| Fixture | Quelle und Ausgangsschema | API-Lanes |
| --- | --- | --- |
| `schema-8-floor` | 0.2.80 / `forest-android-1008001`, Schema 8 | 26, 35 und 37 |
| `schema-20-organic-flow` | 0.2.137 / `forest-android-1013701`, Schema 20 | 26 |
| `schema-22-clean-candidate` | 0.2.157 / `forest-android-1015701`, Schema 22 | 26 |
| `schema-23-repair-boundary` | 0.2.158 / `forest-android-1015801`, Schema 23 | 26 |

Jede Quelle ist durch Release- und Tag-Ziel, Commit, Paketname, Versionscode, Versionsname,
Metadaten- und APK-SHA-256 sowie Produktionssignatur festgelegt. Die Schema-20-Fixture bildet die
organische SQLite-Spaltenhistorie ab. Die Schema-22-Fixture prüft die vollständige Entfernung
unbenutzter Angebote ohne neue Recovery-Zeilen sowie den Erhalt aktiver Ressourcen und Historie.
Die Schema-23-Fixture beweist sowohl die gezielte Reparatur
einer vertauschten 0.2.158-Zeile als auch den unveränderten Erhalt einer korrekten Zeile. Der
Migrationsvertrag und die Fixture-Arten sind in
[ADR-035](adr-035-physische-migrationshistorie-und-upgrade-fixtures.md) festgelegt.

Der stabile Sammelcheck `pull-request-gate` verlangt alle für das Profil anwendbaren Jobs.
Dokumentation und Hosttests erzeugen keinen Release. Änderungen an Produkt, Build, Signierung,
Upgrade-Probe und Freigabelogik bleiben releasewirksam. Gewöhnliche Test-APK-/Debug-Harness-
Änderungen werden vollständig geprüft, ohne ein Produktupdate zu veröffentlichen.

Das Ruleset von `main` verlangt einen aktuellen Pull Request, den grünen `pull-request-gate` und
einen Squash-Merge. Main kann ausschließlich inhaltsidentische PR-Nachweise mit identischer
Policy, identischem Profil und allen eindeutigen grünen Einzeljobs übernehmen. Andernfalls läuft
das ausgewählte Profil auf Main erneut. Packaging, aktuelle und anwendbare historische Upgrades
sowie Veröffentlichung bleiben am exakten Main-Kandidaten. Ein lokaler Release-Build ist ohne
Produktionszugangsdaten unsigniert.

Auch Fehlerbehebungen mit unmittelbarer Nutzerwirkung folgen dem Pull-Request-Gate. Eine Änderung
der Versionsstrategie bleibt ein separates Vorhaben; sie wird nicht mit gewöhnlichen
Refactorings gekoppelt.

### Legacy-Versionen

- Historische Tags ohne Präfix `forest-android-` gehören nicht zum aktuellen Updatekanal.
- Builds mit einer anderen Application-ID sind aus Android-Sicht andere Anwendungen und können
  nicht in-place auf `de.thonktank.autosecretary` aktualisiert werden.
- Debug-signierte oder mit einem anderen Schlüssel signierte Installationen müssen vor dem
  ersten Produktions-Release entfernt werden. Android erhält deren lokale Daten dabei nicht.
- Historische Tags und Artefakte werden nicht automatisch gelöscht. Sie dienen nur als
  Repositoryhistorie, solange keine eigene Legacy-Migrationsentscheidung getroffen wurde.
- Ab der ersten Installation eines gültigen Produktions-Releases müssen Paketname,
  Signaturkontinuität und Datenmigrationen erhalten bleiben.

## Konsequenzen

Paketname, Produktionsschlüssel, Versionscode und Metadatenschema sind öffentliche
Kompatibilitätsgrenzen. Änderungen sind keine gewöhnlichen Refactorings. Der Releaseworkflow und
der In-App-Updater müssen denselben Vertrag implementieren, und ein erfolgreicher Clean Install
allein genügt nicht als Nachweis für Updatekompatibilität. Reproduzierbare Tests von den
festgelegten signierten Schemaepochen sind deshalb Bestandteil vollständiger Produktionsläufe;
der aktuelle signierte In-place-Smoke ist Bestandteil jedes Produktreleases. Die interne Schichtung des In-App-Updaters ist in
[ADR-006](adr-006-update-schichten-und-fehler.md) festgelegt.
