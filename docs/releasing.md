# Auto Secretary – Builds und Updates veröffentlichen

Jeder Push auf `main` durchläuft Unit-Tests, Lint, Instrumentierungstests und einen echten
Produktions-Upgrade-Test auf API 26 und API 35. Nur wenn alle Prüfungen erfolgreich sind, baut
GitHub eine signierte APK und veröffentlicht sie als stabiles GitHub Release.

## Dauerhafter Signaturschlüssel

Das Repository verwendet die bestehenden Actions-Secrets:

| Secret | Inhalt |
| --- | --- |
| `KEYSTORE_BASE64` | Base64-kodierter Produktions-Keystore |
| `KEYSTORE_PASSWORD` | Store- und Schlüsselpasswort |

Der Schlüsselalias ist `release`. Die Pipeline akzeptiert nur den in
`release/release.properties` festgehaltenen Zertifikat-Fingerprint. App-ID, Keystore und Alias
dürfen nach der ersten Installation nicht mehr geändert werden, sonst kann Android spätere APKs
nicht als Update installieren.

## Automatischer Ablauf

1. Eine abgeschlossene Änderung wird auf `main` gepusht.
2. `.github/workflows/verify.yml` führt das vollständige Quality-Gate aus.
3. Die Pipeline vergibt einen eindeutigen `versionCode` und einen sichtbaren Namen wie `0.2.17`.
4. Die APK wird signiert und auf Paketname, Version, Größe und Zertifikat geprüft.
5. Die vorige Produktions-APK wird installiert und mit Aufgaben-, Schritt-, Statistik- und
   Einstellungsdaten befüllt. `adb install -r` aktualisiert sie auf den Kandidaten; anschließend
   müssen App-Start, höherer Versionscode, Room-Schema und alle Testdaten erhalten sein.
6. Ein Draft-Release erhält genau `AutoSecretary.apk` und `release-metadata.json`.
7. GitHub lädt beide Dateien zur Gegenprüfung erneut herunter und veröffentlicht erst danach das
   Release. Der höchste Build wird als „Latest“ markiert.

Ein fehlgeschlagener Build veröffentlicht nichts. Eine Wiederholung für einen bereits
veröffentlichten Commit erzeugt kein Duplikat.

Der Upgrade-Test wählt dynamisch den höchsten bereits veröffentlichten `forest-android-`-Build
unterhalb des Kandidaten. Das Test-APK wird mit demselben Produktionsschlüssel signiert, damit es
den nicht-debugbaren Releaseprozess vor und nach dem Android-Upgrade prüfen kann. Bis zur
Extraktion eines einmalig gebauten Releaseartefakts wird der signierte Kandidat für diesen Test je
API-Stufe separat gebaut; Signatur, Paket und Versionscode entsprechen dem späteren Release, die
Dateiidentität wird in der nächsten Releasewerkzeug-Phase vereinheitlicht.

## Update in der App

Die App prüft höchstens einmal täglich nach dem Start und jederzeit manuell unter
**Optionen → Updates**. Ein Download wird vor der Installation auf Größe, SHA-256, Paketname,
Versionscode und dieselbe Android-Signatur wie die installierte App geprüft. Anschließend öffnet
die App den Android-Systeminstaller; dessen Bestätigung kann und soll nicht umgangen werden.

Beim ersten Wechsel von einem lokal oder durch CI debug-signierten Build muss dieser Build
einmalig deinstalliert werden. Ab der Installation des ersten Produktions-Releases bleiben lokale
Aufgaben bei allen folgenden Updates erhalten.

## Release lokal prüfen

Der lokale Quality-Gate bleibt:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
```

Ein lokaler `assembleRelease` bleibt absichtlich unsigned. Ein signierter Build erfordert den
Produktionsschlüssel und `-PrequireReleaseSigning=true`; reguläre signierte Builds entstehen nur
in GitHub Actions.
