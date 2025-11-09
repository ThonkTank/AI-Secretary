# Auto-Update System - Technische Dokumentation

## Komponenten

### UpdateChecker.java
**Zweck:** Prüft GitHub Releases API nach neuen Versionen

**Funktionsweise:**
- Verbindet zu: `https://api.github.com/repos/ThonkTank/AI-Secretary/releases/latest`
- Verwendet GitHub Personal Access Token für API-Zugriff
- Vergleicht aktuelle App-Version mit neuester Release-Version
- Sucht nach `.apk` Assets im Release

**Verwendung:**
```java
UpdateChecker.checkForUpdates(context, new UpdateChecker.UpdateListener() {
    @Override
    public void onUpdateAvailable(String version, String downloadUrl, String changelog) {
        // Zeige Update-Dialog
    }

    @Override
    public void onNoUpdateAvailable() {
        // Informiere User
    }

    @Override
    public void onError(String error) {
        // Fehlerbehandlung
    }
});
```

### UpdateInstaller.java
**Zweck:** Lädt APK herunter und startet Installation

**Funktionsweise:**
- Nutzt Android DownloadManager für APK-Download
- Speichert APK in öffentlichem Downloads-Ordner
- BroadcastReceiver wartet auf Download-Abschluss
- Startet Package Installer automatisch

**Permissions erforderlich:**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

## User Flow (Version 1.2+)

1. User öffnet App
2. Klickt auf Settings-Button (⚙ oben rechts)
3. Klickt auf "Check for Updates"
4. Bei verfügbarem Update: Dialog mit Changelog
5. "Download & Install" startet Download
6. System-Notification zeigt Fortschritt
7. Nach Download: Automatische Installation

## GitHub Actions Workflow

**Datei:** `.github/workflows/build-and-release.yml`

**Trigger:** Jeder Push auf main-Branch

**Build-Schritte:**
1. Checkout Code
2. Setup JDK 17
3. Android SDK Installation
4. AAPT2 Ressourcen-Kompilierung
5. Java-Kompilierung (Source 8, Target 8)
6. DEX-Erstellung mit d8
7. APK Alignment
8. APK Signierung (Debug-Keystore)
9. GitHub Release Erstellung
10. APK Upload als Release Asset

**Release-Format:**
- Tag: `v{version}` (z.B. v1.2)
- Asset: `AISecretary-signed.apk`
- Body: Automatisch generiert mit Commit-Message und Build-Info

## Versionierung

**Schema:** Semantic Versioning (Major.Minor)
- **Major:** Breaking Changes oder größere Feature-Suites
- **Minor:** Neue Features, UI-Änderungen, Bugfixes

**Tracking:**
- `android:versionCode` - Integer, inkrementell (für Android)
- `android:versionName` - String, für User sichtbar

**Beispiel:**
```xml
<manifest
    android:versionCode="3"
    android:versionName="1.2">
```

## Update-Flow Debugging

**Typische Log-Sequenz:**
```
[INFO] [MainActivity] === Application started ===
[INFO] [MainActivity] App version: 1.1 (code: 2)
[INFO] [MainActivity] Settings button clicked
[INFO] [MainActivity] User initiated update check from settings
[INFO] [UpdateChecker] Starting update check. Current version: 1.1 (code: 2)
[DEBUG] [UpdateChecker] GitHub API response code: 200
[INFO] [UpdateChecker] Latest version from GitHub: 1.2
[DEBUG] [UpdateChecker] Found 1 assets in release
[INFO] [UpdateChecker] Found APK asset: AISecretary-signed.apk
[INFO] [UpdateChecker] Update available! 1.1 -> 1.2
[INFO] [MainActivity] Update dialog shown to user for version 1.2
[INFO] [MainActivity] User accepted update download
[INFO] [UpdateInstaller] Starting download for version 1.2
[INFO] [UpdateInstaller] Download enqueued with ID: 12345
[INFO] [UpdateInstaller] Download completed for ID: 12345
[INFO] [UpdateInstaller] Starting installation for: file:///...
[INFO] [UpdateInstaller] APK installer intent launched successfully
```

## API Rate Limits

**GitHub API:**
- Mit Token: 5000 Requests/Stunde
- Ohne Token: 60 Requests/Stunde

## Bekannte Limitierungen

1. **Automatischer Download kann fehlschlagen:**
   - Permissions-Probleme
   - Netzwerk-Issues
   - Storage-Probleme
   - **Workaround:** Manuelle APK-Installation aus Downloads

2. **Keine Delta-Updates:**
   - Jedes Update lädt vollständige APK
   - Für kleine App (20KB) akzeptabel

3. **Keine Rollback-Funktion:**
   - Kein automatisches Rollback bei Problemen
   - User muss manuell alte Version installieren

## Zukünftige Verbesserungen

- [ ] In-App Update API (Google Play Core Library)
- [ ] Changelog-Formatierung (Markdown-Rendering)
- [ ] Update-Häufigkeit begrenzen (max. 1x pro Tag prüfen)
- [ ] Background-Updates (WorkManager)
- [ ] Delta-Updates für größere APKs
