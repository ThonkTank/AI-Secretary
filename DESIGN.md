# AI Secretary - UX/UI Design Document

**Projekt:** AI Secretary - Taskmaster Feature Suite
**Erstellt:** 8. November 2025
**Design-Philosophie:** Widget-First, Streak-Focused, Frictionless Task Management

---

## 🎨 Design-Philosophie

### Core Principles

1. **Widget-First Approach**
   - Das Widget ist die primäre Schnittstelle
   - App nur für komplexe Operationen öffnen
   - 90% der täglichen Interaktionen passieren im Widget

2. **Streak Visibility**
   - Streaks sind das Hauptmotivations-Element
   - Immer sichtbar, prominent platziert
   - Visuelles Feedback bei Streak-Gefahr

3. **Frictionless Interaction**
   - Tasks mit einem Tap erledigen
   - Bearbeiten/Löschen mit minimalem Aufwand
   - Keine unnötigen Dialoge oder Bestätigungen (außer beim Löschen)

4. **Information Density**
   - Kompakt aber übersichtlich
   - Nur relevante Informationen zeigen
   - Progressive Disclosure für Details

5. **Visual Hierarchy**
   - Wichtigstes zuerst: Nächste Aufgabe → Heutige Tasks → Statistiken
   - Farben zur Priorisierung nutzen
   - Klare visuelle Trennung zwischen Bereichen

---

## 📱 Screen Design

### 1. Home Screen Widget (Primäre Schnittstelle)

**Größen:**
- **Small (2x2):** Nächste Aufgabe + Streak
- **Medium (4x2):** Nächste Aufgabe + Top 3 Tasks + Streak
- **Large (4x4):** Vollständige Tagesansicht (bevorzugt)

#### Large Widget Layout (4x4)

```
┌─────────────────────────────────────┐
│ 🔥 Streak: 12 Tage    📊 Heute: 3/8 │
├─────────────────────────────────────┤
│                                     │
│ NÄCHSTE AUFGABE                     │
│ ┌─────────────────────────────────┐ │
│ │ ⭐⭐⭐ Morning Routine       [✓]│ │
│ │ 📅 Jeden Tag · 🕐 08:00          │ │
│ │ 🔥 Streak: 12                    │ │
│ └─────────────────────────────────┘ │
│                                     │
│ HEUTE                               │
│ ┌─────────────────────────────────┐ │
│ │ ⭐⭐ Team Meeting          [✓]│ │
│ │ 📅 15:00                         │ │
│ ├─────────────────────────────────┤ │
│ │ ⭐ Email beantworten       [✓]│ │
│ │ 📅 Heute                         │ │
│ ├─────────────────────────────────┤ │
│ │ ⚠️ Rechnung bezahlen       [✓]│ │
│ │ 📅 ÜBERFÄLLIG (2 Tage)           │ │
│ └─────────────────────────────────┘ │
│                                     │
│ [➕ Neue Aufgabe]    [📱 App öffnen]│
└─────────────────────────────────────┘
```

**Widget-Features:**
- **Direktes Abhaken:** Checkbox rechts zum sofortigen Erledigen
- **Task-Details bei Tap:** Öffnet Completion-Dialog (Zeit/Schwierigkeit)
- **Lange-Tap:** Quick-Menü (Bearbeiten, Löschen, Verschieben)
- **Streak-Anzeige:** Große Zahl mit Feuer-Emoji
- **Heute-Counter:** Erledigte/Gesamt für den Tag
- **Neue Aufgabe:** Direkter Button zum Erstellen
- **Farbcodierung:**
  - Überfällig: Rot (⚠️)
  - Heute fällig: Orange
  - Zukünftig: Grau
  - Hohe Priorität: Sterne (⭐⭐⭐)

#### Medium Widget Layout (4x2)

```
┌─────────────────────────────────────┐
│ 🔥 12 Tage    📊 3/8    [➕]         │
├─────────────────────────────────────┤
│ ⭐⭐⭐ Morning Routine       [✓]  │
│ ⭐⭐ Team Meeting (15:00)    [✓]  │
│ ⚠️ Rechnung bezahlen         [✓]  │
└─────────────────────────────────────┘
```

#### Small Widget Layout (2x2)

```
┌─────────────────┐
│ 🔥 12  📊 3/8   │
├─────────────────┤
│ ⭐⭐⭐          │
│ Morning Routine │
│      [✓]        │
└─────────────────┘
```

---

### 2. Main App - Today View (Hauptbildschirm)

**Layout:**

```
┌─────────────────────────────────────┐
│ ☰  AI Secretary            [⚙️]     │
├─────────────────────────────────────┤
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ 🔥 DEINE STREAKS                 │ │
│ │                                  │ │
│ │ ┌──────────┐  ┌──────────┐      │ │
│ │ │    12    │  │    7     │      │ │
│ │ │   Tage   │  │   Tage   │      │ │
│ │ │ Morning  │  │ Training │      │ │
│ │ └──────────┘  └──────────┘      │ │
│ └─────────────────────────────────┘ │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ 📊 HEUTE                         │ │
│ │ 3 von 8 Tasks erledigt           │ │
│ │ ████████░░░░░░░░ 37%             │ │
│ └─────────────────────────────────┘ │
│                                     │
│ DEINE AUFGABEN                      │
│ [Alle] [Heute] [Überfällig] [+]     │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ ⭐⭐⭐ Morning Routine   🔥12 │ │
│ │ Jeden Tag · 08:00               │ │
│ │ [✓] [✎] [🗑️]                   │ │
│ ├─────────────────────────────────┤ │
│ │ ⭐⭐ Team Meeting               │ │
│ │ Heute · 15:00                   │ │
│ │ [✓] [✎] [🗑️]                   │ │
│ ├─────────────────────────────────┤ │
│ │ ⚠️ Rechnung bezahlen ÜBERFÄLLIG │ │
│ │ Fällig vor 2 Tagen              │ │
│ │ [✓] [✎] [🗑️]                   │ │
│ └─────────────────────────────────┘ │
│                                     │
│ [➕ Neue Aufgabe]                   │
└─────────────────────────────────────┘
```

**Interaktionen:**
- **Swipe Right auf Task:** Schnell als erledigt markieren
- **Swipe Left auf Task:** Löschen (mit Bestätigung)
- **Tap auf Task:** Details & Edit-Ansicht öffnen
- **Tap auf ✓:** Task erledigen → Completion-Dialog
- **Tap auf ✎:** Direkt bearbeiten
- **Tap auf 🗑️:** Löschen (mit Bestätigung)
- **Tap auf Streak-Karte:** Streak-Details anzeigen

**Visual Elements:**
- Streak-Karten horizontal scrollbar
- Progress-Bar für Tagesfortschritt
- Farbcodierung wie im Widget
- Tabs für Filter (Alle/Heute/Überfällig)

---

### 3. Task Completion Dialog

Erscheint beim Abhaken eines Tasks:

```
┌─────────────────────────────────────┐
│ ✅ Morning Routine erledigt!         │
├─────────────────────────────────────┤
│                                     │
│ Wie lange hat es gedauert?          │
│                                     │
│ [5 Min] [15 Min] [30 Min] [1 Std]  │
│                                     │
│ Oder: [__:__] Stunden : Minuten     │
│                                     │
│ Wie einfach war es?                 │
│                                     │
│ Sehr schwer ★☆☆☆☆ Sehr einfach      │
│                                     │
│ 🔥 Streak auf 13 Tage erhöht!       │
│                                     │
│      [Überspringen]  [Speichern]    │
└─────────────────────────────────────┘
```

**Features:**
- Schnellauswahl-Buttons für häufige Zeiten
- Star-Rating für Schwierigkeit (1-5)
- Streak-Feedback sofort sichtbar
- "Überspringen" für schnelles Abhaken ohne Tracking

---

### 4. Add/Edit Task Screen

**Kompakter Tab-Ansatz:**

```
┌─────────────────────────────────────┐
│ ← Neue Aufgabe                 [✓]  │
├─────────────────────────────────────┤
│                                     │
│ [Basis] [Wiederholung] [Details]    │
│                                     │
│ ─── BASIS ───                       │
│                                     │
│ Titel                               │
│ ┌─────────────────────────────────┐ │
│ │ Morning Routine                  │ │
│ └─────────────────────────────────┘ │
│                                     │
│ Beschreibung (optional)             │
│ ┌─────────────────────────────────┐ │
│ │ Meditation, Stretching, Dusche   │ │
│ │                                  │ │
│ └─────────────────────────────────┘ │
│                                     │
│ Priorität                           │
│ [⭐] [⭐⭐] [⭐⭐⭐] [⭐⭐⭐⭐]     │
│                                     │
│ Fälligkeit                          │
│ [📅 Heute] [📅 Morgen] [📅 Datum...] │
│                                     │
└─────────────────────────────────────┘
```

**Tab: Wiederholung**

```
┌─────────────────────────────────────┐
│ Wiederholungstyp                    │
│                                     │
│ ○ Einmalig                          │
│ ● x mal pro Zeitraum                │
│ ○ Alle x Zeiteinheiten              │
│ ○ Geplante Zeiten                   │
│                                     │
│ ─── EINSTELLUNGEN ───               │
│                                     │
│ [3] mal pro [Woche ▼]               │
│                                     │
│ Start-Datum                         │
│ [📅 Heute]                          │
│                                     │
│ End-Datum (optional)                │
│ [📅 Kein Ende] [📅 Datum...]        │
│                                     │
└─────────────────────────────────────┘
```

**Tab: Details**

```
┌─────────────────────────────────────┐
│ ⏱️ Geschätzte Dauer                 │
│ [15 Min] [30 Min] [1 Std] [___]     │
│                                     │
│ 🕐 Bevorzugte Zeit                  │
│ [Morgen] [Mittag] [Abend] [___]     │
│                                     │
│ 🔗 Verkettung (optional)            │
│ Nach: [Kein Task ▼]                 │
│ Vor:  [Kein Task ▼]                 │
│                                     │
│ 🏷️ Kategorie (optional)             │
│ [Keine ▼]                           │
│                                     │
└─────────────────────────────────────┘
```

**Interaktionen:**
- **✓ oben rechts:** Speichern
- **← oben links:** Abbrechen
- Tabs für Organisation (nicht alles auf einmal)
- Smart-Defaults: Heute, Priorität 2, Einmalig

---

### 5. Statistics / Streaks View

```
┌─────────────────────────────────────┐
│ ← Statistiken                       │
├─────────────────────────────────────┤
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ DEINE LEISTUNG                   │ │
│ │                                  │ │
│ │ Heute        Woche    Durchschn. │ │
│ │   3/8         21        3.0      │ │
│ │              ████████             │ │
│ └─────────────────────────────────┘ │
│                                     │
│ 🔥 AKTIVE STREAKS                   │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ Morning Routine                  │ │
│ │ 🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥           │ │
│ │ 12 Tage in Folge                 │ │
│ │ Längste Streak: 18 Tage          │ │
│ └─────────────────────────────────┘ │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ Training                         │ │
│ │ 🔥🔥🔥🔥🔥🔥🔥                  │ │
│ │ 7 Tage in Folge                  │ │
│ │ Längste Streak: 21 Tage          │ │
│ └─────────────────────────────────┘ │
│                                     │
│ ⚠️ STREAKS IN GEFAHR                │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ Lesen                            │ │
│ │ Noch nicht heute erledigt!       │ │
│ │ Aktuelle Streak: 5 Tage          │ │
│ └─────────────────────────────────┘ │
│                                     │
└─────────────────────────────────────┘
```

**Features:**
- Feuer-Emojis visualisieren Streak-Länge
- Warnung bei gefährdeten Streaks
- Historische Best-Streak anzeigen
- Mini-Wochengraph für Aktivität

---

## 🎨 Visual Design System

### Farbpalette

**Primary Colors:**
```
Primary:       #6200EE (Lila) - Buttons, Highlights
Primary Dark:  #3700B3 (Dunkel-Lila) - Status Bar
Accent:        #FF6B00 (Orange) - Streaks, Wichtige Elemente
```

**Status Colors:**
```
Success/Completed:  #4CAF50 (Grün)
Warning/Due Today:  #FFC107 (Gelb/Orange)
Error/Overdue:      #F44336 (Rot)
Info:               #2196F3 (Blau)
```

**Priority Colors:**
```
Priority 1 (⭐):       #2196F3 (Blau)
Priority 2 (⭐⭐):     #FFC107 (Gelb)
Priority 3 (⭐⭐⭐):   #FF9800 (Orange)
Priority 4 (⭐⭐⭐⭐): #F44336 (Rot)
```

**Streak/Fire:**
```
Streak Color: #FF6B00 (Orange-Rot)
Fire Emoji: 🔥 (für visuelle Konsistenz)
```

### Typografie

**Schriftarten:**
- System Default: Roboto (Android Standard)
- Titel: Roboto Bold, 20sp
- Subtitel: Roboto Medium, 16sp
- Body: Roboto Regular, 14sp
- Caption: Roboto Regular, 12sp

**Hierarchie:**
- Task-Titel: 16sp, Medium
- Task-Details: 12sp, Regular, 70% Opacity
- Streak-Zahlen: 32sp, Bold
- Widget-Text: 14sp, Medium

### Iconography

**Konsistente Icons:**
- ✓ Checkmark - Task erledigen
- ✎ Pencil - Bearbeiten
- 🗑️ Trash - Löschen
- ➕ Plus - Neu erstellen
- 🔥 Fire - Streak
- ⭐ Star - Priorität
- 📅 Calendar - Datum/Fälligkeit
- 📊 Chart - Statistiken
- ⚙️ Gear - Einstellungen
- ⚠️ Warning - Überfällig/Gefahr
- 🕐 Clock - Zeit

### Spacing & Layout

**Abstände:**
- Padding (Cards): 16dp
- Margin (zwischen Elementen): 8dp
- Margin (zwischen Sections): 16dp
- Widget Padding: 12dp

**Card Elevation:**
- Normal: 2dp
- Hover/Active: 4dp
- Modal/Dialog: 8dp

**Corner Radius:**
- Cards: 12dp
- Buttons: 8dp
- Chips/Tags: 16dp

---

## 🔄 Interaktionsmuster

### Task-Interaktionen

**Schnellaktionen (im Widget & in der App):**

1. **Tap auf Checkbox:**
   - Zeigt Completion-Dialog (Zeit/Schwierigkeit)
   - Option zum Überspringen
   - Sofortiges Streak-Feedback

2. **Swipe Right (nur App):**
   - Schnelles Erledigen ohne Dialog
   - Für routinierte Tasks

3. **Swipe Left (nur App):**
   - Löschen-Aktion
   - Bestätigungs-Dialog erscheint
   - Undo-Option für 5 Sekunden

4. **Long-Press:**
   - Quick-Menu öffnet sich
   - Optionen: Bearbeiten, Löschen, Verschieben, Details

5. **Tap auf Task-Body:**
   - Öffnet Detail/Edit-View

### Feedback & Animationen

**Mikro-Animationen:**
- Checkbox-Check: Kurze Bounce-Animation
- Streak-Erhöhung: Confetti-Animation (nur bei Meilensteinen)
- Task-Completion: Fade-Out mit Slide
- Task-Add: Fade-In von oben
- Swipe-Actions: Follow-Finger mit Icon-Reveal

**Haptisches Feedback:**
- Task erledigt: Kurze Vibration
- Streak-Meilenstein: Doppel-Vibration
- Löschen: Warnung-Vibration
- Error: Fehler-Vibration

**Loading States:**
- Skeleton Screens für Task-Liste
- Shimmer-Effect beim Laden
- Pull-to-Refresh Support

---

## 📐 Widget Deep Dive

### Widget als Hauptschnittstelle

**Design-Ziele:**
1. 90% der Interaktionen im Widget
2. Kein App-Öffnen für tägliche Tasks
3. Maximale Information-Density ohne Überladung
4. One-Tap Task Completion

### Widget-Interaktionen (detailliert)

**Tap auf Task-Checkbox:**
```
User tappt auf Checkbox
    ↓
Completion-Dialog öffnet sich ÜBER dem Widget
    ↓
User gibt Zeit/Schwierigkeit ein (oder Skip)
    ↓
Task wird als erledigt markiert
    ↓
Widget aktualisiert sich sofort
    ↓
Nächster Task rückt nach oben
```

**Long-Press auf Task:**
```
User hält Task gedrückt (0.5s)
    ↓
Quick-Menu erscheint (Popup)
    ↓
Optionen: Bearbeiten | Verschieben | Löschen
    ↓
Tap außerhalb schließt Menu
```

**Tap auf "Neue Aufgabe":**
```
User tappt auf ➕ Button
    ↓
Add-Task-Dialog öffnet sich (Overlay)
    ↓
Minimal-Form: Titel + Priorität + Fälligkeit
    ↓
"Erweitert"-Button für volle Form
    ↓
Task wird erstellt
    ↓
Widget aktualisiert sich
```

### Widget-Updates

**Automatische Updates:**
- Alle 15 Minuten
- Bei Task-Änderungen
- Bei Mitternacht (neue Tasks für heute)
- Bei Streak-Änderungen

**Manuelle Updates:**
- Pull-Down Geste (wenn Android unterstützt)
- Tap auf Widget-Header (Refresh-Icon)

---

## 🎯 Accessibility (Minimal)

Da nur für persönlichen Gebrauch:

**Basis-Anforderungen:**
- Touch-Targets mindestens 48dp
- Kontrast-Ratio mindestens 4.5:1
- Keine Information nur durch Farbe
- Textgrößen skalierbar

**KEINE Anforderungen:**
- Screen-Reader Optimierung
- Vollständige TalkBack-Unterstützung
- Voice-Control
- Komplexe Accessibility-Nodes

---

## 🚀 Progressive Enhancement

### MVP Design (Phase 1-3)
- Basis-Widget (Medium Size)
- Einfache Task-Liste
- Basic Completion-Dialog
- Keine Animationen

### Enhanced Design (Phase 4-5)
- Large Widget mit vollem Feature-Set
- Streak-Visualisierungen
- Swipe-Gesten
- Mikro-Animationen

### Final Design (Phase 6-8)
- Small Widget für kompakte Ansicht
- Verkettungs-Visualisierungen
- Vollständige Animation-Suite
- Haptisches Feedback

---

## 📝 Design-Entscheidungen & Rationale

### Warum Widget-First?

**Begründung:**
- Reduziert Friction auf Minimum
- Nutzer muss App nicht öffnen
- Immer sichtbar auf Home-Screen
- Schneller als jede App-Interaktion

### Warum Streak-Focus?

**Begründung:**
- Streaks sind stärkster Motivator
- Gamification ohne Overengineering
- Visuell einfach darstellbar (🔥)
- Klares Ziel: "Streak nicht brechen"

### Warum keine komplexen Animationen im MVP?

**Begründung:**
- Entwicklungszeit reduzieren
- Performance-Probleme vermeiden
- Core-Funktionalität wichtiger
- Später nachrüstbar

### Warum Tabs in Add-Task statt eine große Form?

**Begründung:**
- Reduziert kognitive Last
- Schneller für einfache Tasks
- Power-Features versteckt aber verfügbar
- Mobile-First Prinzip

---

## 🔮 Zukünftige Design-Überlegungen

**Potenzielle Erweiterungen (außerhalb aktueller Scope):**

1. **Themes:** Light/Dark/Auto (Low Priority)
2. **Kategorien-Farben:** Benutzerdefinierte Farben pro Kategorie
3. **Timeline-View:** Chronologische Tagesansicht
4. **Kalender-Integration:** Sync mit Google Calendar
5. **Widgets in verschiedenen Stilen:** Minimal, Detailed, Compact
6. **Shortcuts:** Quick-Add per Icon-Long-Press
7. **Wear OS Widget:** Für Smartwatch

**Aktuell NICHT geplant:**
- Multiple Themes
- Customizable UI
- Komplexe Grafiken/Charts
- Social Features
- Cloud Sync
- Multiple Accounts

---

## ✅ Design-Checkliste für Implementierung

### Widget
- [ ] Large Widget (4x4) Layout
- [ ] Medium Widget (4x2) Layout
- [ ] Small Widget (2x2) Layout
- [ ] Tap-Interaktionen (Checkbox, Task, Buttons)
- [ ] Long-Press Menu
- [ ] Auto-Update Mechanismus
- [ ] Streak-Anzeige prominent
- [ ] Heute-Counter

### Main App
- [ ] Today View mit Streak-Karten
- [ ] Task-Liste mit Farbcodierung
- [ ] Swipe-Gesten (Right: Complete, Left: Delete)
- [ ] Filter-Tabs (Alle/Heute/Überfällig)
- [ ] Progress-Bar für Tagesfortschritt

### Dialogs/Forms
- [ ] Completion-Dialog (Zeit/Schwierigkeit)
- [ ] Add-Task Form (3 Tabs)
- [ ] Edit-Task Form
- [ ] Delete-Confirmation
- [ ] Quick-Menu (Long-Press)

### Visual Polish
- [ ] Farbschema implementiert
- [ ] Icons konsistent
- [ ] Spacing/Padding korrekt
- [ ] Card-Elevation
- [ ] Corner-Radius

### Animations (später)
- [ ] Checkbox-Animation
- [ ] Task-Completion Fade
- [ ] Streak-Confetti
- [ ] Swipe-Reveals
- [ ] Loading-Skeletons

---

**Status:** Design Document v1.0 - Bereit für Implementierung
**Nächster Schritt:** Roadmap-Update mit Design-Phasen
