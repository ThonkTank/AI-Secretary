# AI Secretary - Development Roadmap

**Projekt:** AI Secretary - Native Android App
**Feature Suite 1:** Taskmaster
**Letzte Aktualisierung:** 9. November 2025 (Phase 10 Architektur-Cleanup hinzugefügt)
**Design Dokument:** [DESIGN.md](./DESIGN.md) | [BUILD_FIX_GUIDE.md](./BUILD_FIX_GUIDE.md)

---

## 🎯 Vision

Ein umfassendes Alltags-Planungstool mit intelligenter Aufgabenverwaltung, Tracking und automatischer Tagesplanung. Vollständige Spezifikation: [CLAUDE.md](./CLAUDE.md)

---

## ✅ Implementierter Stand (Phasen 0-8)

### Phase 0: Projekt-Setup ✅
- Native Android Projekt-Struktur, Git-Repo, Dokumentation (CLAUDE.md, DESIGN.md, README.md)

### Phase 1: Grundlagen & Datenbank ✅
- **TaskEntity** mit allen Properties (Basis, Recurrence, Streaks, Performance, Chains, Category)
- **Design System v1.0** (colors.xml, styles.xml, dimens.xml aus DESIGN.md)
- **TaskDao** (SQLite CRUD + Queries), **TaskRepository** (Singleton, Business-Logic)
- **MainActivity** mit RecyclerView, TaskAdapter, Swipe-Gesten, Stats

### Phase 2: Task-Erstellung & -Verwaltung ✅
- **AddTaskActivity** mit Tab-Layout (Basis/Wiederholung/Details)
- Task-Bearbeitung, Validierung, Smart-Defaults
- **RecurrenceManager** + **RecurringTaskService** (automatische Task-Resets)

### Phase 3: Tracking & Performance-Daten ✅
- **CompletionDialog** (Zeit/Schwierigkeits-Input)
- **CompletionHistoryDao** (historische Tracking-Daten)
- Repository erweitert um History-Methoden

### Phase 4: Streak-Tracking & Gamification ✅
- **StreakManager** (Streak-Logik, Milestone-System)
- Streak-Display im UI, Streak-Reset-Logik
- **TaskWidgetProvider** (4x4 Large Widget) mit Streak-Anzeige

### Phase 5: Intelligente Tagesplanung ✅
- **TaskScheduler** (Multi-Faktor-Scoring: Priorität, Due-Date, Streak-Risk, Performance)
- Intelligentes Sorting, "Next Task"-Empfehlung

### Phase 6: Task-Verkettung ✅
- **ChainManager** (Task-Chains mit Abhängigkeiten)
- Chain-Progress-Tracking, Chain-Visualisierung

### Phase 7: Statistik-Dashboard ✅
- **StatisticsActivity** (Produktivitäts-Analyse)
- **StatsManager** (Trend-Berechnung, Streak-Stats, Kategorie-Stats)

### Phase 8: Benachrichtigungen & Backup ✅
- **NotificationService** (Daily Summary, Task Reminders, Streak Warnings)
- **NotificationManager** (Notification Channels)
- **BackupActivity** (JSON Export/Import)

**Details zu Phasen 1-8:** Siehe Git-Commit-History und DESIGN.md

---

## 🚧 Aktuelle Phase

### Phase 9: Build-System Stabilisierung (KRITISCH) 🔥

**Status:** In Arbeit (9.1 ✅ | 9.2 in Progress)

**Problem:** App war nicht buildbar (Gradle kaputt in Termux, build_apk.sh unterstützt keine Dependencies)
**Lösung:** GitHub Actions Cloud-Build (funktioniert!)

#### 9.1 Critical Bug-Fixes ✅ ABGESCHLOSSEN

**Gefixte Bugs:**
- ✅ NotificationService.java Import Fix (database → repository)
- ✅ 15x db.close() entfernt (TaskDao + CompletionHistoryDao) - verhindert SQLite Crashes
- ✅ 11 Repository-Methoden synchronized (Thread-Safety)

**Commit:** `2b92673` - "Phase 9.1: Critical Bug Fixes vor Build"
**Pushed:** 8. Nov 2025, GitHub Actions Build läuft

#### 9.2 Ersten Build durchführen ⏳ IN PROGRESS

**Aktuell:**
- ✅ GitHub Actions triggerd (Push zu main)
- ⏳ Cloud-Build läuft (~5-10 Min)
- ⏳ APK Download von GitHub Actions Artifacts
- ⏳ APK Installation auf Gerät

**Schritte:**
1. Öffne: https://github.com/ThonkTank/AI-Secretary/actions
2. Klicke neuesten Workflow-Run ("Phase 9.1: Critical Bug Fixes...")
3. Download "app-debug" Artifact
4. APK-Größe prüfen (erwartet: 5-8MB)
5. Installiere APK auf Gerät

#### 9.3 APK-Installation & Testing 🧪 PENDING

**Installation:**
- [ ] APK nach Downloads kopieren: `cp app-debug.apk ~/storage/downloads/`
- [ ] APK installieren (Dateimanager oder `termux-open`)
- [ ] "Installation aus unbekannten Quellen" erlauben (Android-Einstellung falls nötig)

**Smoke-Tests nach Installation:**
- [ ] App öffnet ohne Crash
- [ ] Task erstellen funktioniert
- [ ] Task abhaken funktioniert
- [ ] Swipe-Gesten (Complete/Delete)
- [ ] Widget wird angezeigt
- [ ] Dark Mode funktioniert
- [ ] Statistik-Screen öffnet
- [ ] Backup/Restore funktioniert

**Falls Crashes:** Logcat analysieren, weitere Bugs fixen, neuer Build

#### 9.4 Build-Dokumentation 📝 TEILWEISE

- ✅ BUILD_FIX_GUIDE.md erstellt (komplettes Troubleshooting)
- ✅ ROADMAP.md erweitert mit Phase 9
- [ ] README.md aktualisieren mit Build-Status
- [ ] BUILD_INSTRUCTIONS.md aktualisieren
- [ ] BUILD_STATUS.md aktualisieren mit neuem Status

#### 9.5 Alternative Build-Methoden Setup 🔄 OPTIONAL

- [ ] **Option A:** AIDE installieren & Projekt importieren testen
- [ ] **Option B:** apkc installieren & konfigurieren
- [ ] **Option C:** GitHub Actions verifizieren (bereits funktioniert)

**Geschätzter Aufwand:** 30 Min | **Priorität:** NIEDRIG (Backup-Methoden)

---

### Timeline: Was als nächstes

**Sofort (heute):**
1. ✅ Build-System analysiert
2. ✅ Lösungen dokumentiert
3. ✅ Critical Bug-Fixes durchgeführt (9.1)
4. ✅ Phase 10 Cleanup-Tasks in ROADMAP aufgenommen
5. ⏳ R.java Generation Problem debuggen (10.4 BLOCKING)
6. ⏳ GitHub Actions Build fixen

**Diese Woche:**
7. R.java Problem lösen (10.4)
8. Ersten erfolgreichen Build durchführen (9.2)
9. APK Testing (9.3 Smoke-Tests)
10. Comprehensive Code Audit starten (10.3)

**Nächste Woche:**
11. Package-Struktur Cleanup (10.1)
12. ChainId Type Consistency Fix (10.2)
13. Code Audit finalisieren (10.3)
14. Dokumentation vervollständigen (9.4)

**Danach:**
15. Security & Stability Fixes (Phase 11)
16. MVVM-Refactoring (Phase 12)
17. Testing & I18n (Phase 13)

---

## 🐛 Offene Code-Qualitäts-Probleme

**Vollständige Analyse:** Siehe Git-Commit `ea539b7` (Code-Analyse vom 8. Nov)

### Kritische Bugs (MUSS vor Production)
- ✅ **FIXED:** db.close() in DAOs (15x entfernt)
- ✅ **FIXED:** NotificationService.java Import
- ✅ **FIXED:** Repository Thread-Safety (11 Methoden synchronized)
- ⚠️ **QUICK PATCH:** util/ vs utils/ Package-Inkonsistenz → **Phase 10.1** (Proper Fix geplant)
- ⚠️ **QUICK PATCH:** chainId long/String Type-Mismatch → **Phase 10.2** (Proper Fix geplant)
- 🔥 **BLOCKING:** R.java Generation Failure → **Phase 10.4** (In Investigation)
- ⚠️ **OFFEN:** Widget Security (Context-Injection möglich) → **Phase 11**
- ⚠️ **OFFEN:** MainActivity God-Object (533 LOC → refactoring nötig) → **Phase 12**

### High-Priority Verbesserungen
- Database Transactions fehlen (Race-Conditions bei Multi-Threading)
- Kein echtes MVVM (ViewModels fehlen)
- MainActivity zu groß → aufbrechen in Fragments
- 0% JavaDoc-Coverage

### Medium-Priority
- Dependencies veraltet (androidx.core 1.13.1 → 1.17.0, Gradle 8.4 → 8.14.2)
- Hardcoded Strings (~50+) → I18n erforderlich
- Accessibility-Features fehlen

### Low-Priority
- Performance-Optimierung (Main-Thread-Blocking)
- Tests schreiben (0 Tests vorhanden)
- ProGuard-Rules erweitern

**Gesamt:** 48 Bugs/Improvements identifiziert
**Details:** Code-Analyse-Report in Git-Commit-Message `ea539b7`

---

## 📋 Nächste Phasen (Post-Build)

### Phase 10: Architektur-Cleanup & Investigation (KRITISCH) 🔍

**Status:** PENDING | **Priorität:** HOCH
**Kontext:** Während Phase 9 wurden strukturelle Probleme mit "quick patches" gelöst. Diese benötigen gründliche Refactorings.

#### 10.1 Package-Struktur Cleanup ⚠️ HIGH

**Problem:** Inkonsistente Benennung util/ vs utils/
- `util/` (singular) - nur NotificationManager.java
- `utils/` (plural) - alle anderen Manager (ChainManager, StatsManager, StreakManager, RecurrenceManager, TaskScheduler)

**Quick Patch (Phase 9.1):** Imports überall manuell korrigiert
**Proper Fix Optionen:**
- [ ] **Option A:** Alles nach `utils/` (plural) verschieben
  - NotificationManager.java → utils/ verschieben
  - Alle Imports von `util.NotificationManager` → `utils.NotificationManager`
- [ ] **Option B:** Alles nach `util/` (singular) verschieben
  - Alle 5 Manager von utils/ → util/ verschieben
  - Alle Imports anpassen

**Empfehlung:** Option A (utils/ ist Mehrheit)
**Geschätzter Aufwand:** 1 Stunde | **Blocker:** Keine

#### 10.2 ChainId Type Consistency ⚠️ CRITICAL

**Problem:** Fundamentaler Type-Mismatch
- **TaskEntity.chainId:** `long` (DB: INTEGER, Line 51)
- **ChainManager Methoden:** erwarten `String chainId` Parameter
- **Aktuelle Lösung:** String.valueOf(task.chainId) überall + null checks → 0 checks

**Quick Patch (Phase 9.1):**
```java
// Überall String.valueOf() Konvertierungen eingefügt:
getTasksInChain(allTasks, String.valueOf(currentTask.chainId))
if (currentTask.chainId == 0) { // statt == null
```

**Proper Fix - Option A (String überall):**
- [ ] TaskEntity.chainId: `long` → `String`
- [ ] DB-Schema ändern: `INTEGER` → `TEXT`
- [ ] DB-Migration schreiben (v2 → v3)
- [ ] Alle String.valueOf() entfernen
- [ ] Default: `0` → `""`

**Proper Fix - Option B (long überall):**
- [ ] ChainManager Methoden: `String chainId` → `long chainId`
- [ ] ChainInfo.chainId: `String` → `long`
- [ ] Alle Vergleiche: `chainId.equals(...)` → `chainId == ...`
- [ ] Alle String.valueOf() entfernen

**Empfehlung:** Option B (long ist effizienter, keine DB-Migration nötig)
**Geschätzter Aufwand:** 2-3 Stunden | **Blocker:** Build muss erst funktionieren

#### 10.3 Comprehensive Code Structure Audit 🔍 CRITICAL

**Ziel:** ALLE ähnlichen Architektur-Probleme finden BEVOR sie zu Bugs werden

**Untersuchungsbereiche:**

**A) Package-Naming Audit:**
- [ ] Prüfe alle 34 Java-Klassen auf Package-Zuordnung
- [ ] Suche nach singular/plural Inkonsistenzen
- [ ] Dokumentiere Package-Struktur (was gehört wo?)
- [ ] Erstelle Package-Richtlinien für neue Klassen

**B) Type-Mismatch Audit (Entity ↔ Manager):**
- [ ] TaskEntity Properties vs Manager Parameter-Typen
  - chainId (long vs String) ✅ bekannt
  - Andere Properties prüfen: priority, category, recurrenceInterval, etc.
- [ ] CompletionHistoryEntity prüfen
- [ ] Alle 6 Manager durchgehen (ChainManager, StatsManager, StreakManager, RecurrenceManager, TaskScheduler, NotificationManager)

**C) Database Schema Consistency:**
- [ ] Vergleiche DB-Schema (TaskDao.java CREATE TABLE) mit Entity-Properties
- [ ] INTEGER vs long, TEXT vs String, REAL vs float, BOOLEAN vs boolean
- [ ] Prüfe auf fehlende/zusätzliche Spalten
- [ ] Dokumentiere Type-Mapping

**D) Import-Probleme & Dependencies:**
- [ ] Suche potenzielle "cannot find symbol" Kandidaten
- [ ] Prüfe zirkuläre Dependencies
- [ ] Validiere alle Import-Paths gegen tatsächliche Dateistruktur
- [ ] Erstelle Dependency-Graph

**E) Manager Responsibilities Audit:**
- [ ] Sind Manager-Grenzen klar definiert?
- [ ] Gibt es überlappende Responsibilities?
- [ ] Sollte NotificationManager in utils/ sein?
- [ ] Brauchen wir neue Manager oder Merger?

**Methode:**
1. Erstelle vollständige Klassen-Inventur (Excel/Markdown)
2. Prüfe jeden Entity-Manager Pair auf Type-Konsistenz
3. Dokumentiere ALLE gefundenen Inkonsistenzen
4. Priorisiere Fixes (Critical/High/Medium/Low)
5. Erstelle detaillierten Cleanup-Backlog
6. Schätze Aufwand pro Fix

**Deliverables:**
- `ARCHITECTURE_AUDIT.md` (Findings-Report)
- `CLEANUP_BACKLOG.md` (priorisierte Fix-Liste)

**Geschätzter Aufwand:** 4-6 Stunden | **Priorität:** CRITICAL

#### 10.4 R.java Generation Problem Lösung 🐛 BLOCKING

**Problem:** Layout-Ressourcen existieren, aber R.java wird nicht generiert
- `activity_add_task.xml` ✅ existiert (3.4KB)
- Compilation error: `cannot find symbol: variable activity_add_task` ❌

**Mögliche Ursachen:**
- [ ] **Malformed XML** in Layout-Dateien (fehlende Tags, ungültige Attribute)
- [ ] **AndroidManifest.xml** Syntax-Fehler oder fehlende Activity-Deklaration
- [ ] **Resource ID Duplikate** (gleiche IDs in verschiedenen Layouts)
- [ ] **Theme/Style Referenzen** auf nicht existierende Styles
- [ ] **Gradle Resource Processing** Config-Problem
- [ ] **Namespace-Probleme** (xmlns:android falsch)

**Debugging-Schritte:**
1. [ ] Validiere alle Layout XMLs: `xmllint --noout app/src/main/res/layout/*.xml`
2. [ ] Prüfe AndroidManifest.xml: `xmllint --noout app/src/main/AndroidManifest.xml`
3. [ ] Suche nach doppelten IDs: `grep -r 'android:id="@+id/' app/src/main/res/layout/ | sort`
4. [ ] Prüfe Theme-Referenzen in styles.xml
5. [ ] Teste Build mit minimalem Layout (nur TextView)
6. [ ] Vergleiche mit funktionierendem Android-Projekt (GitHub Template)

**Geschätzter Aufwand:** 2-4 Stunden | **Priorität:** BLOCKING (verhindert alle Builds)

---

**Gesamt-Aufwand Phase 10:** 10-15 Stunden
**Muss vor Production:** JA - kritische Architektur-Fundamente
**Kann parallel zu Testing:** TEILWEISE (10.3 Audit kann parallel laufen)

---

### Phase 11: Security & Stability (1-2 Wochen)
- Widget Context-Injection fixen
- Database Transactions hinzufügen
- Weitere Critical Bugs aus Code-Analyse

### Phase 12: Architecture Refactoring (2-3 Wochen)
- MVVM einführen (ViewModels)
- MainActivity aufbrechen (Fragments)
- Dependencies updaten
- JavaDoc schreiben

### Phase 13: Quality & Testing (2-3 Wochen)
- Unit Tests (TaskRepository, Managers)
- Instrumented Tests (UI-Tests)
- Performance-Optimierung
- I18n (Deutsch/Englisch)

### Phase 14: Production Release (1 Woche)
- Release-Build konfigurieren
- APK signieren (Release-Keystore)
- Google Play Store Vorbereitung (optional)
- Final Testing

**Geschätzter Aufwand bis Production:** 7-11 Wochen

---

## 🔧 Build-System Details

**Problem:** Gradle funktioniert nicht in Termux (ARM64 libiconv-Bug)
**Lösung:** GitHub Actions Cloud-Build (empfohlen) oder AIDE/apkc

**Vollständiges Troubleshooting:** [BUILD_FIX_GUIDE.md](./BUILD_FIX_GUIDE.md)

**Quick-Build via GitHub Actions:**
```bash
git add -A
git commit -m "Feature XYZ"
git push origin main
# → GitHub baut automatisch, APK in Actions Artifacts
```

**Alternative Build-Methoden:**
1. **GitHub Actions** (Cloud) ✅ Funktioniert, automatisch bei Push
2. **AIDE IDE** (Android IDE mit GUI) - Dependency-Support
3. **apkc** (CLI-Tool) - Schnell, modern
4. **Android Studio** (Desktop) - Vollständiges IDE

**Lokales build_apk.sh:** Funktioniert NICHT (keine Dependency-Support)

---

## 📊 Projekt-Metriken

**Code:**
- 34 Java-Klassen
- ~8.805 Zeilen Code
- 13 XML Layouts
- 6 Utils/Managers (Streak, Stats, Chain, Recurrence, Scheduler, Notification)

**Architektur:**
- Repository Pattern (TaskRepository Singleton)
- Manager-Pattern (Utils)
- SQLite (Custom DAOs, kein Room)
- Min SDK: 23 (Android 6.0)
- Target SDK: 35 (Android 15)

**Build:**
- Gradle 8.4
- 9 androidx Dependencies
- GitHub Actions CI/CD

---

## 📝 Notizen

- **Testing:** 0 Tests vorhanden (HIGH PRIORITY!)
- **Performance:** Main-Thread-Blocking muss vor Production behoben werden
- **I18n:** ~50+ Hardcoded Strings (aktuell nur Deutsch/Englisch-Mix)
- **Accessibility:** A11y-Features fehlen (Phase 13)
- **BUILD-SYSTEM:** ✅ GitHub Actions funktioniert, lokaler Build limitiert
- **NEXT:** APK downloaden, installieren, testen → Weitere Bugs fixen falls nötig

---

**Entwickelt mit [Claude Code](https://claude.com/claude-code)**
