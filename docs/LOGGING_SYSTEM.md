# Logging-System - Technische Dokumentation

## Komponente: AppLogger.java

**Zweck:** Zentrale Logging-Klasse mit automatischer Log-Rotation

**Features:**
- Thread-safe Singleton-Pattern
- Automatische Log-Bereinigung (max. 500 Zeilen)
- Drei Log-Level: INFO, DEBUG, ERROR
- Timestamp für jeden Eintrag
- Parallel-Logging zu Android Logcat

## Log-Format

```
[YYYY-MM-DD HH:mm:ss] [LEVEL] [TAG] Message
```

**Beispiel:**
```
[2025-11-09 14:05:32] [INFO] [MainActivity] === Application started ===
[2025-11-09 14:05:32] [DEBUG] [UpdateChecker] GitHub API response code: 200
[2025-11-09 14:05:33] [ERROR] [UpdateInstaller] Download failed with status: 403
```

## Log-Speicherort

**Pfad:** `/sdcard/AISecretary/app_logs.txt`

**Zugriff:**
- Für App: External Storage (Permissions erforderlich)
- Für Claude Code: Direkt lesbar
- Für User: Sichtbar in Datei-Manager

## Verwendung in Code

**Initialisierung:**
```java
AppLogger logger = AppLogger.getInstance(context);
```

**Logging:**
```java
// Info-Level
logger.info("TAG", "Normal information message");

// Debug-Level (für detaillierte Infos)
logger.debug("TAG", "Detailed debug information");

// Error-Level
logger.error("TAG", "Error message");
logger.error("TAG", "Error with exception", exception);
```

## Log-Kategorien

**MainActivity:**
- App-Starts
- User-Interaktionen (Button-Clicks)
- Dialog-Events
- Versionsinformationen

**UpdateChecker:**
- Update-Check Start
- GitHub API Requests/Responses
- Version-Vergleiche
- Asset-Suche
- Erfolg/Fehler bei Update-Erkennung

**UpdateInstaller:**
- Download-Start
- Download-IDs
- Download-Completion
- Installation-Start
- Fehler bei Download/Installation

## Log-Rotation

**Mechanismus:**
- Maximale Zeilen: 500
- Bereinigung: ~2% Chance bei jedem Log (statistisch)
- Bereinigungsprozess: Nur letzte 500 Zeilen behalten

## Logs auslesen (für Claude Code)

**Befehl:**
```bash
cat /sdcard/AISecretary/app_logs.txt
```

**Log-Analyse:**
1. Chronologische Reihenfolge beachten
2. Nach ERROR-Einträgen suchen
3. Flow zwischen Komponenten nachvollziehen
4. Timestamps für Performance-Analyse

## Debugging-Workflow

**Typischer Ablauf:**
1. User meldet Problem
2. User reproduziert Problem in App
3. Claude liest Logs aus
4. Analyse der Log-Sequenz
5. Identifikation der Fehlerursache
6. Fix implementieren

## Best Practices

**DO:**
- ✅ Jede wichtige User-Aktion loggen
- ✅ API-Responses loggen
- ✅ Fehler mit Context loggen
- ✅ Start/Ende von Operationen markieren

**DON'T:**
- ❌ Passwörter oder sensible Daten loggen
- ❌ Excessive Logging in Loops
- ❌ User-identifizierbare Informationen
- ❌ Vollständige API-Responses (nur Status)

## Performance

**Impact:**
- File I/O: Minimal (gepuffert)
- Memory: ~50KB für 500 Zeilen
- CPU: Vernachlässigbar
- Battery: Keine messbare Auswirkung
