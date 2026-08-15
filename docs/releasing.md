# Auto Secretary – Release-APK veröffentlichen

Die App ist für direkte Installationen aus GitHub Releases eingerichtet. Damit Android jede neue APK als **Update** erkennt und die Aufgaben behalten kann, muss GitHub immer denselben Signaturschlüssel verwenden.

## Einmalig: Repository-Secrets setzen

Erzeuge den Schlüssel an einem sicheren Ort (nicht im Repository):

```bash
keytool -genkeypair -v -keystore jetzt-release.keystore -alias jetzt -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 jetzt-release.keystore
```

In GitHub unter **Settings → Secrets and variables → Actions** vier Repository-Secrets anlegen:

| Secret | Wert |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Ausgabe des zweiten Befehls |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore-Passwort |
| `ANDROID_KEY_ALIAS` | `jetzt` (oder der gewählte Alias) |
| `ANDROID_KEY_PASSWORD` | Schlüsselpasswort |

Den Keystore sicher aufbewahren. Ohne exakt diesen Schlüssel kann eine spätere APK nicht über eine vorhandene Installation aktualisiert werden.

## Release erstellen

Die Versionsnummer in `app/build.gradle.kts` erhöhen, committen und einen Tag wie `v0.1.1` pushen. Die GitHub Action baut die signierte APK und hängt sie an den GitHub Release an. Auf dem Handy die APK aus dem Release herunterladen und installieren; bei Updates bleiben die lokalen Daten erhalten.
