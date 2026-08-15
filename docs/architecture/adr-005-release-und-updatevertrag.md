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
- GitHub Actions vergibt pro Lauf einen Versionscode oberhalb von `1000000` nach
  `versionCodeFloor + GITHUB_RUN_NUMBER * 100 + GITHUB_RUN_ATTEMPT`.
- Ein neuer Versionscode muss größer als jeder bereits vorhandene Tag des aktuellen Updatekanals
  sein; andernfalls bricht die Planung ab.
- Ein wiederholter Workflowlauf erhält einen eigenen Versionscode und den Suffix `-rN` im
  sichtbaren Versionsnamen. Bereits veröffentlichte Commits werden nicht erneut veröffentlicht.
- Lokale Builds verwenden nur Entwicklungswerte. Sie sind keine Quelle für die nächste
  Produktionsversion.

Die Kopplung der Versionsvergabe an den GitHub-Workflow ist der aktuelle Vertrag, aber keine
gewünschte dauerhafte Architektur. Eine spätere, workflowunabhängige Vergabe muss monotone
Versionscodes für alle bereits veröffentlichten Builds garantieren.

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

Die Pipeline baut und signiert einen Produktionskandidaten genau einmal. API 26, API 35 und der
Veröffentlichungsschritt verwenden dieses interne Workflow-Artefakt. Die Pipeline erstellt oder
übernimmt anschließend einen Draft, lädt beide öffentlichen Assets hoch, lädt sie als Beweis
erneut herunter und veröffentlicht erst nach erfolgreicher Hash-, Commit- und Byteprüfung. Der
Release mit dem höchsten Versionscode im gültigen Tagformat wird als `Latest` markiert.

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

Jeder Push auf `main` muss vor einer Veröffentlichung folgende Prüfungen bestehen:

- Unit- und Robolectric-Tests
- Android Lint
- Debug-, Android-Test- und Release-Build
- Instrumentierungstests auf API 26 und API 35
- Größenlimit für Installationsartefakt und eingebettete Schriftarten
- Paket-, Versions- und Signaturprüfung der Produktions-APK
- Upgrade der vorherigen signierten Produktions-APK auf exakt den später veröffentlichten
  Kandidaten auf API 26 und API 35

Ein Pull Request führt dieselben Qualitäts- und Instrumentierungsgates aus, veröffentlicht aber
kein Release. Ein lokaler Release-Build bleibt ohne ausdrücklich bereitgestellte
Produktionszugangsdaten unsigniert.

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
allein genügt nicht als Nachweis für Updatekompatibilität. Ein reproduzierbarer Test von einer
vorherigen signierten Version auf das aktuelle Release ist deshalb Bestandteil jedes
Produktionslaufs. Die interne Schichtung des In-App-Updaters ist in
[ADR-006](adr-006-update-schichten-und-fehler.md) festgelegt.
