# AI Secretary - Build-Anleitung

## Status

**Code:** ✅ Produktionsreif
**Build-System:** ✅ Vollständig konfiguriert
**Termux-Build:** ⚠️ Gradle funktioniert nicht (JVM libiconv-Fehler)

## Build-Optionen

### Option 1: GitHub Actions (EMPFOHLEN)

Dies ist die einfachste Methode, da die Cloud-Umgebung alle notwendigen Tools hat.

#### Setup:

1. **Repository auf GitHub pushen:**
   ```bash
   cd /data/data/com.termux/files/home/ai-secretary-dev/ai-secretary-native
   git init
   git add .
   git commit -m "Initial commit: AI Secretary Native App"
   git remote add origin https://github.com/YOUR_USERNAME/ai-secretary-native.git
   git push -u origin main
   ```

2. **GitHub Actions wird automatisch ausgeführt:**
   - Bei jedem Push auf `main` oder `develop` Branch
   - Workflow-Datei: `.github/workflows/android-build.yml`

3. **APK herunterladen:**
   - Gehe zu GitHub → Actions → neuester Workflow-Run
   - Lade das `app-debug` Artifact herunter
   - Entpacke die ZIP-Datei → `app-debug.apk`

4. **Manuelle Release-Builds:**
   - Gehe zu Actions → "Android CI Build" → "Run workflow"
   - Wähle `release` als Build-Typ
   - Konfiguriere Release-Signing in GitHub Secrets (siehe unten)

#### GitHub Secrets für Release-Builds:

Gehe zu Repository Settings → Secrets and variables → Actions → New repository secret:

```
RELEASE_KEYSTORE_PASSWORD=dein-keystore-passwort
RELEASE_KEY_ALIAS=dein-key-alias
RELEASE_KEY_PASSWORD=dein-key-passwort
```

Dann den Base64-kodierten Keystore als Secret hinzufügen:
```bash
base64 -w 0 release.keystore > release.keystore.base64
# Inhalt als RELEASE_KEYSTORE_BASE64 Secret speichern
```

---

### Option 2: Android Studio (Desktop/Laptop)

1. **Android Studio installieren**
2. **Projekt klonen:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/ai-secretary-native.git
   ```
3. **In Android Studio öffnen:**
   - File → Open → Projektverzeichnis auswählen
4. **Gradle Sync:**
   - Android Studio führt automatisch Gradle Sync durch
5. **Build:**
   - Build → Build Bundle(s) / APK(s) → Build APK(s)
6. **APK-Pfad:**
   - `app/build/outputs/apk/debug/app-debug.apk`

---

### Option 3: Lokaler Gradle Build (funktioniert NICHT in Termux)

⚠️ **Hinweis:** Dies funktioniert auf normalen Linux/macOS/Windows-Systemen, aber NICHT in Termux aufgrund des libiconv-Fehlers.

```bash
# Gradle Wrapper initialisieren (nur einmal nötig)
gradle wrapper --gradle-version=8.4

# Debug-Build
./gradlew assembleDebug

# Release-Build
./gradlew assembleRelease

# APK-Pfad
ls -la app/build/outputs/apk/debug/app-debug.apk
```

---

## Installation auf Android-Gerät

### Methode 1: ADB (empfohlen)

```bash
# APK auf Gerät installieren
adb install app-debug.apk

# Oder vorherige Version ersetzen
adb install -r app-debug.apk

# Bei Installation-Fehler: Deinstallieren und neu installieren
adb uninstall com.aisecretary.taskmaster
adb install app-debug.apk
```

### Methode 2: Datei-Manager

1. APK auf Gerät kopieren (z.B. via `Downloads/`)
2. Mit Datei-Manager öffnen
3. "Aus unbekannten Quellen installieren" erlauben (falls nötig)
4. Installation bestätigen

### Methode 3: Termux (wenn APK auf GitHub Actions gebaut wurde)

```bash
# APK herunterladen (ersetze URL mit tatsächlicher GitHub Actions Artifact URL)
curl -L -o app-debug.apk "https://github.com/.../app-debug.apk"

# In Downloads verschieben
mv app-debug.apk ~/storage/downloads/

# Mit Termux API installieren (requires termux-api package)
termux-open ~/storage/downloads/app-debug.apk
```

---

## Debugging & Logs

### Logcat anzeigen

```bash
# Alle Logs der App
adb logcat | grep "com.aisecretary.taskmaster"

# Nur Error-Logs
adb logcat *:E | grep "Taskmaster"

# Log in Datei speichern
adb logcat > app.log
```

### App starten via ADB

```bash
# App starten
adb shell am start -n com.aisecretary.taskmaster/.MainActivity

# App beenden
adb shell am force-stop com.aisecretary.taskmaster
```

---

## Troubleshooting

### Problem: "App wurde nicht installiert"

**Lösung:**
```bash
# Alte Version deinstallieren
adb uninstall com.aisecretary.taskmaster

# Oder über Settings → Apps → AI Secretary → Deinstallieren
```

### Problem: Gradle Wrapper fehlt

**Lösung:**
- Bei GitHub Actions Build: Wrapper wird automatisch erstellt
- Bei lokalem Build: `gradle wrapper --gradle-version=8.4`

### Problem: Signing-Fehler bei Release-Build

**Lösung:**
```bash
# Debug-Keystore verwenden (nur für Testing!)
./gradlew assembleRelease
# Oder Release-Keystore konfigurieren (siehe app/build.gradle)
```

### Problem: "Theme not found" Fehler

**Status:** ✅ BEHOBEN
Die `styles.xml` Dateien wurden aktualisiert auf `Theme.Material` (vorher: veraltetes `Theme.Holo`)

---

## Testing

### Manuelle Tests

Siehe `TEST.md` für vollständige Test-Checkliste.

**Quick-Tests:**
1. App öffnet ohne Crash
2. Task erstellen funktioniert
3. Task abschließen funktioniert
4. Widget wird angezeigt
5. Dark Mode funktioniert

### Automatisierte Tests (optional)

```bash
# Unit-Tests
./gradlew test

# Instrumented Tests (benötigt angeschlossenes Gerät/Emulator)
./gradlew connectedAndroidTest
```

---

## Nächste Schritte

1. ✅ GitHub Repository erstellen
2. ✅ Code pushen
3. ✅ GitHub Actions laufen lassen
4. ✅ APK herunterladen
5. ✅ Auf Gerät installieren
6. 📋 Manuelle Tests durchführen (siehe TEST.md)

---

## Kontakt & Support

Bei Problemen:
1. GitHub Issues erstellen
2. Logs via `adb logcat` sammeln
3. Screenshots anhängen

**Viel Erfolg! 🚀**
