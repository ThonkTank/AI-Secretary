# Produktionssignatur, Recovery und Repositorybetrieb

Stand: 2026-08-16

Dieses Runbook gilt für die außerhalb eines App-Stores verteilte App
`de.thonktank.autosecretary`. Die Produktionssignatur ist eine dauerhafte
Kompatibilitätsgrenze: Ohne passenden Schlüssel akzeptiert Android kein In-place-Update. Der
aktuell festgelegte SHA-256-Zertifikat-Fingerprint steht in
`release/release.properties`; Alias und Passwörter sind dagegen Betriebsdaten und keine
Android-Identität.

## Aktuelle Signing-Eingaben

Der GitHub-Workflow versteht vier voneinander unabhängige Werte:

| Actions-Secret | Bedeutung | Übergangsfallback |
| --- | --- | --- |
| `KEYSTORE_BASE64` | Base64-kodierter Produktions-Keystore | keiner |
| `KEYSTORE_PASSWORD` | Passwort des Keystores | keiner |
| `KEYSTORE_ALIAS` | Alias des Produktionsschlüssels | `release` |
| `KEY_PASSWORD` | Passwort des Schlüssel-Eintrags | `KEYSTORE_PASSWORD` |

Die beiden Fallbacks erhalten den bestehenden Betrieb, sind aber kein neuer Zwang. Bei einem
Keystore mit anderem Alias oder getrenntem Key-Passwort müssen die beiden optionalen Secrets
explizit gesetzt werden. Der Workflow übergibt Store- und Key-Passwort getrennt an Gradle und
`apksigner` und prüft Zertifikat sowie fertige APK gegen den festgeschriebenen Fingerprint.
Secrets oder Repositoryregeln werden nicht aus dem Repository heraus verändert.

## Normalen Release nachvollziehen

1. Vor dem Pull Request müssen lokales Quality-Gate, sauberer Arbeitsbaum und der beabsichtigte
   Commit feststehen. Nach grünen Pflichtchecks wird der Themenbranch gemäß `AGENTS.md` per
   Squash-Merge nach `main` übernommen; dieser `main`-Commit ist der Release-Trigger.
2. `Verify and publish Android app` muss Quality, Instrumentierung API 26/35, Packaging,
   signierte Upgrades API 26/35 und Publish vollständig bestehen.
3. Der veröffentlichte Tag muss auf denselben vollständigen Commit zeigen. Das Release muss
   stabil, `Latest` und frei von zusätzlichen Assets sein.
4. Internes Workflow-Artefakt und öffentliches `AutoSecretary.apk` werden mit `cmp` und
   `sha256sum` verglichen. `release-metadata.json` wird mit
   `release_tool.py validate` geprüft.
5. `apksigner verify --verbose --print-certs AutoSecretary.apk` und
   `aapt dump badging AutoSecretary.apk` müssen Fingerprint, Paketname, Version und mindestens
   SDK 26 bestätigen.

Ein Workflowfehler vor Publish erzeugt kein stabiles Release. Ein fehlgeschlagener Lauf kann
eine Versionsnummer überspringen; Versionscodes sind absichtlich monoton, nicht lückenlos.

## Backup anlegen und regelmäßig prüfen

Zum Recovery-Satz gehören zwingend:

- der unveränderte Keystore;
- Store-Passwort, Alias und Key-Passwort, getrennt vom Keystore verwahrt;
- das öffentliche Zertifikat, sein SHA-256-Fingerprint und der Fingerprint aus
  `release/release.properties`;
- eine kurze Angabe zu Format, Erstellungszeitpunkt, verantwortlichen Personen und
  Wiederherstellungstest;
- bei einer späteren Rotation zusätzlich jede Signing-Lineage-Datei und alle noch benötigten
  alten Schlüssel.

Mindestens zwei verschlüsselte Offline-Kopien sollen in getrennten Ausfallbereichen liegen. Der
Zugriff braucht ein Vier-Augen- oder vergleichbares Freigabeverfahren; das Entschlüsselungswissen
darf nicht ausschließlich bei einer Person oder im selben Passwortmanager wie der Keystore
liegen. Keystore, Base64-Repräsentation, Passwörter und Lineage gehören niemals ins Repository,
in Build-Artefakte, Tickets oder Chatprotokolle.

Ein Recovery-Satz wird mindestens nach jeder Schlüsseloperation und regelmäßig danach auf einer
isolierten, vertrauenswürdigen Maschine geprüft:

```bash
keytool -list -v -keystore production.jks -alias "$KEY_ALIAS"
keytool -exportcert -rfc -keystore production.jks -alias "$KEY_ALIAS" \
  -file production-public-cert.pem
sha256sum production.jks
```

Der in `keytool` angezeigte Zertifikat-Fingerprint muss nach Entfernen von Doppelpunkten und
Kleinschreibung exakt `signerSha256` entsprechen. Der Keystore-Dateihash dient nur der
Backup-Integrität und ist nicht der Android-Signer-Fingerprint. Für eine Restore-Probe wird eine
lokale Release-APK mit den vier `SIGNING_*`-Umgebungsvariablen gebaut und anschließend mit
`apksigner` geprüft; sie wird weder veröffentlicht noch auf einem produktiven Gerät installiert.

Ein ausführbares Muster, bei dem Passwörter nicht in der Shell-History stehen, ist:

```bash
export SIGNING_STORE_FILE=/absoluter/pfad/production.jks
export SIGNING_KEY_ALIAS=der-tatsaechliche-alias
read -rsp 'Store password: ' SIGNING_STORE_PASSWORD; export SIGNING_STORE_PASSWORD; echo
read -rsp 'Key password: ' SIGNING_KEY_PASSWORD; export SIGNING_KEY_PASSWORD; echo
./gradlew assembleRelease -PversionCode=999001 -PversionName=recovery-probe \
  -PrequireReleaseSigning=true --rerun-tasks
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
unset SIGNING_STORE_PASSWORD SIGNING_KEY_PASSWORD SIGNING_STORE_FILE SIGNING_KEY_ALIAS
```

Die Probeversion ist rein lokal. Sie darf weder in den Updatekanal noch auf ein Gerät mit einer
höherwertigen produktiven Installation gelangen.

## Wiederherstellung

### GitHub-Secrets verloren, Keystore vorhanden

1. Releaseaktivität stoppen und klären, ob nur ein Konfigurationsverlust oder ein möglicher
   Zugriff Dritter vorliegt.
2. Recovery-Kopie auf einer isolierten Maschine entschlüsseln und Zertifikat-Fingerprint sowie
   Keystore-Dateihash prüfen.
3. Mit ausdrücklich autorisiertem Repositoryzugriff die vier Actions-Secrets neu setzen. Dabei
   niemals Secretwerte in Shell-History oder Logs ausgeben.
4. Zuerst lokal einen nicht veröffentlichten Signaturtest durchführen.
5. Einen regulären, reviewten Commit releasen und anschließend Tag, APK, Metadaten,
   Bytegleichheit und Signatur wie oben prüfen.

Für die autorisierte Secretpflege akzeptiert `gh secret set NAME` den Wert interaktiv. Der
Keystore wird vorher in eine geschützte temporäre Datei base64-kodiert und über Standardeingabe
gesetzt; diese Datei wird anschließend sicher entfernt. Passwörter werden nie als
Kommandozeilenargument übergeben. Der genaue Vorgang wird im Recovery-Protokoll festgehalten,
ohne die Werte selbst zu notieren.

### Lokale Kopie verloren, GitHub-Secrets vorhanden

Actions-Secrets lassen sich nicht als Backup zurücklesen. Es muss eine verifizierte Offline-Kopie
wiederhergestellt werden. Bis dahin darf kein Key-Wechsel improvisiert werden.

### Keystore und alle Backups verloren

Ohne den bisherigen privaten Schlüssel und ohne bereits vorbereitete kompatible Signing-Lineage
gibt es für direkt verteilte APKs keinen allgemeinen In-place-Recovery-Pfad. Eine neue Signatur
erfordert dann in der Regel eine neue Application-ID und Neuinstallation; lokale Appdaten gehen
ohne eine separat entworfene Export-/Importmigration nicht automatisch mit. Dieser Fall ist ein
Disaster-Recovery-Ereignis und keine normale Releaseänderung.

## Geplante Schlüsselrotation und Signing Lineage

Der aktuelle Kanal ist noch **nicht rotationsfähig**: Die Pipeline erwartet genau einen
Fingerprint, die Metadaten enthalten genau einen Signer und der In-App-Updater vergleicht diesen
mit dem aktuellen Signer der installierten APK. Die derzeitigen APKs besitzen keine vorbereitete
Signing Lineage. Deshalb darf niemand lediglich einen neuen Keystore oder Fingerprint eintragen.

Vor einer Rotation ist eine eigene, separat veröffentlichte Migrationsphase erforderlich:

1. Metadatenschema und Paketnachweis müssen aktuellen Signer und Zertifikatshistorie eindeutig
   abbilden; der Updater muss Lineage-Kompatibilität statt bloßer Fingerprint-Gleichheit prüfen.
2. Der Workflow muss alten und neuen Schlüssel sowie die binäre Lineage getrennt und ohne
   Logausgabe verarbeiten. Das unveränderte Lineage-Artefakt gehört in den Recovery-Satz.
3. Mit `apksigner rotate` wird die Folge alter Schlüssel → neuer Schlüssel erzeugt. Die
   konkreten Befehle werden zuerst mit nichtproduktiven Testschlüsseln geprobt.
4. Weil die App API 26 unterstützt, muss die Übergangssignatur Geräte vor API 28 weiterhin mit
   dem alten v2-Signer bedienen; für API 28+ wird die Rotation mit
   `--rotation-min-sdk-version 28` und `--lineage` vorbereitet. Solange API 26/27 unterstützt
   werden, bleibt der alte private Schlüssel für diese APKs erforderlich.
5. CI muss mindestens alte APK → Übergangs-APK → neue APK auf API 26, 28 und 35 prüfen, jeweils
   einschließlich In-App-Downloadprüfung, Android-Upgrade und Datenerhalt.
6. Erst nach erfolgreichem Übergangsrelease werden neuer erwarteter Signer, Secrets und
   Recovery-Dokumentation in einem weiteren kontrollierten Release umgestellt. Alte Schlüssel
   werden nicht gelöscht, sondern gemäß Lineage- und Mindest-SDK-Strategie offline archiviert.

Das folgende Gerüst zeigt die beabsichtigte Werkzeugfolge, ist aber mit dem heutigen Workflow
noch **kein** freigegebener Produktionsbefehl:

```bash
apksigner rotate --out signing-lineage.bin \
  --old-signer --ks old-production.jks --ks-key-alias "$OLD_ALIAS" \
  --new-signer --ks new-production.jks --ks-key-alias "$NEW_ALIAS"
apksigner sign --ks old-production.jks --ks-key-alias "$OLD_ALIAS" \
  --next-signer --ks new-production.jks --ks-key-alias "$NEW_ALIAS" \
  --lineage signing-lineage.bin --rotation-min-sdk-version 28 transition.apk
```

`apksigner` soll die Passwörter dabei interaktiv abfragen oder aus einem dafür vorgesehenen,
nicht protokollierten Secretkanal lesen. Fingerprints von altem Signer, neuem Signer und Lineage
werden vor und nach jedem Test unabhängig dokumentiert.

Die maßgebliche Werkzeugreferenz ist die offizielle
[Android-`apksigner`-Dokumentation](https://developer.android.com/tools/apksigner). Eine Rotation
ist erst freigegeben, wenn das konkrete Verfahren mit der dann verwendeten Build-Tools-Version
und echten Upgrade-Fixtures bewiesen wurde.

## Verdacht auf kompromittierten Schlüssel

1. Releases sofort pausieren, Schreibzugriffe und Actions-Ausführungen einschränken und
   Beweise/Auditlogs sichern. Verdächtige Tags oder Releases nicht reflexartig löschen.
2. GitHub-Zugänge, Tokens, Runner und Secretänderungen prüfen; betroffene Zugangsdaten rotieren.
3. Öffentliche APKs unabhängig auf Hash, Commit und Signer prüfen und den frühesten möglichen
   Missbrauchszeitpunkt bestimmen.
4. Ist nur ein CI-Zugang, nicht aber der private Schlüssel betroffen, Secrets aus einer sauberen
   Offline-Kopie neu setzen und einen überprüften Recovery-Release erzeugen.
5. Ist der private Schlüssel betroffen, ist ohne vorher etablierte Lineage kein sicherer
   universeller In-place-Wechsel möglich. Nutzer müssen transparent gewarnt werden; je nach
   Schadensbild sind neuer Paketname, Neuinstallation und eine gesonderte Datenmigration nötig.
6. Bei etablierter Lineage wird ausschließlich der vorher getestete Rotations-/Widerrufsplan
   benutzt. Für ältere Android-Versionen und weiter benötigte v2-Signaturen darf keine
   Sicherheitswirkung versprochen werden, die die Plattform nicht gewährleistet.

## Historische Tags und inkompatible Paketidentitäten

Die folgenden Git-Tags bleiben bewusst erhalten. Nummernbereiche haben Lücken; die Tabelle meint
die tatsächlich vorhandenen Tags in diesen Familien.

| Vorhandene Tagfamilie | Historische Application-ID | Beziehung zum aktuellen Kanal |
| --- | --- | --- |
| `v0.0.22` bis `v0.3.50`, außerdem `v1.0` bis `v3.1` | `com.secretary.helloworld` | andere Android-App |
| `v0.3.51` bis `v0.3.78` | `com.secretary` | andere Android-App |
| `build-2` bis `build-4`, `preview-*`, `test-31388890228-1`, `android-2000061` bis `android-2001201`, `archive-v2-final-2026-08-15` | `com.autosecretary` | andere Android-App |
| `forest-android-*` | `de.thonktank.autosecretary` | einziger aktueller Updatekanal |

Eine identische Produktbezeichnung oder höhere sichtbare Versionsnummer macht diese Pakete nicht
upgrade-kompatibel. Alte Tags werden weder vom Updater ausgewählt noch automatisch gelöscht.

## Empfohlene Branch Protection

Die Empfehlung wird nicht automatisch angewendet, weil die aktuelle `AGENTS.md` direkte Pushes
fertiger Implementierungen auf `main` verlangt. Vor Aktivierung muss diese Governance-Regel
bewusst auf einen Pull-Request-Workflow geändert werden.

Danach sollte ein Ruleset für `main` mindestens Folgendes erzwingen:

- Änderungen nur über Pull Requests, mindestens eine unabhängige Freigabe;
- veraltete Freigaben nach neuen Commits verwerfen und den letzten reviewbaren Push freigeben
  lassen;
- alle Review-Konversationen auflösen;
- den stabilen Check `pull-request-gate` aus GitHub Actions verpflichtend machen und den Branch
  vor Merge aktuell halten;
- Force-Pushes und Branch-Löschung verbieten, Regeln auch für Administratoren anwenden;
- einen benannten Code Owner für `.github/workflows/**`, `scripts/release/**`,
  `release/**` und diese Signing-Dokumentation verlangen;
- Release-Secrets nur für die `main`-Jobs verfügbar halten; Fork-PRs erhalten sie nicht.

`pull-request-gate` fasst die Quality-Prüfung und beide Instrumentierungs-Matrixjobs zusammen.
Packaging, signierte Upgrades und Veröffentlichung laufen bewusst erst nach dem Merge/Push auf
`main` und sind daher keine PR-Pflichtchecks. GitHub beschreibt die verfügbaren Regeln in
[About protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).
