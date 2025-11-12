# Hello World - AI Secretary Test App

Eine einfache Android Hello World App, gebaut mit AAPT2 in Termux.

## Zweck

Diese App dient als Proof of Concept für die manuelle Android App-Entwicklung in Termux ohne Gradle. Sie demonstriert den vollständigen Build-Prozess mit AAPT2, dx, zipalign und apksigner.

## Projektstruktur

```
hello-world/
├── AndroidManifest.xml          # App-Manifest
├── res/
│   ├── values/
│   │   └── strings.xml          # String-Ressourcen
│   └── layout/
│       └── activity_main.xml    # Main Activity Layout
├── src/
│   └── com/secretary/
│       └── MainActivity.java    # Haupt-Activity
├── build.sh                     # Automatisches Build-Skript
└── README.md                    # Diese Datei
```

## Build-Prozess

### Automatisch mit Build-Skript

```bash
cd ~/AI-Secretary/hello-world
./build.sh
```

Das Skript führt automatisch alle notwendigen Schritte aus:
1. Aufräumen vorheriger Builds
2. Ressourcen kompilieren (aapt2 compile)
3. Ressourcen verlinken (aapt2 link)
4. Java-Code kompilieren (javac)
5. DEX-Datei erstellen (dx)
6. DEX zur APK hinzufügen (zip)
7. APK alignieren (zipalign)
8. APK signieren (apksigner)

### Manuell

Falls du den Prozess verstehen möchtest, siehe die Befehle in `build.sh`.

## Installation

Nach dem erfolgreichen Build:

```bash
# Kopiere APK zu Downloads
cp app_signed.apk ~/storage/downloads/HelloWorld.apk

# Öffne APK zur Installation
termux-open ~/storage/downloads/HelloWorld.apk
```

Oder öffne die APK direkt mit dem Dateimanager deines Geräts.

## App-Informationen

- **Package Name:** com.secretary.helloworld
- **App Name:** AI Secretary
- **Version:** 1.0
- **Min SDK:** 28 (Android 9)
- **Target SDK:** 35 (Android 15)
- **Compiled SDK:** 33 (Android 13)

## Technologie-Stack

- **Build-Tools:** AAPT2 2.19, dx, zipalign, apksigner
- **Java Version:** OpenJDK 21 (kompiliert mit -source 8 -target 8)
- **Android SDK:** API 33
- **Signing:** Debug Keystore

## Limitierungen

Diese manuelle Build-Methode hat folgende Einschränkungen:

- ❌ Keine Support-Libraries (AppCompat, Material Components)
- ❌ Keine automatische Dependency-Verwaltung
- ❌ Keine AAR-Auflösung
- ✅ Funktioniert nur für einfache Apps
- ✅ Gut zum Lernen des Android Build-Prozesses

## Nächste Schritte

Für die echte AI Secretary App wird **GitHub Actions mit Gradle** verwendet, da:
- Gradle nicht lokal in Termux funktioniert (libiconv-Fehler)
- GitHub Actions volle Gradle-Unterstützung bietet
- Support-Libraries einfach integriert werden können
- Der Workflow professioneller und wartbarer ist

Siehe `../CLAUDE.md` für Details zum Produktions-Workflow.

## Entwickelt mit

- **Claude Code Agent** am 09.11.2025
- **Termux** googleplay.2025.10.05
- **Google Pixel 8** mit Android 16

---

**Status:** ✅ Erfolgreich gebaut und getestet
# Trigger GitHub Actions build
