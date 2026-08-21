# Abschlussaudit der Today-/Fokus-Roadmap

Stand: 2026-08-21, Schema 14

## UX-Verträge

| Kritikpunkt | Implementierung | Testnachweis | Dauerhafte Grenze |
|---|---|---|---|
| Header-Ringe nicht zentriert | Corner-Mittelpunkte aus `LeafShape` und finalen Bounds | Geometrie- und Homescreen-Goldens | `LeafSurface` besitzt Form und Grain |
| Header-Ringe nicht auf Blatt begrenzt | gemeinsamer asymmetrischer Clip in `LeafSurface` | Leaf-Komponententests und Goldens | keine separaten Clipradien in Views |
| Step-/Task-Ringe hinter falschem Anchor | `GrainSpec` mit lokalen semantischen Anchor-Bounds | verschachtelte Anchor-, Scroll- und Rotationsfälle | Boundsauflösung erst in `LeafSurface.onLayout()` |
| Notiz-/Wiederholungsschritte ohne Ringe | jeder sichtbare Schritt liefert einen expliziten Grain-Anchor | Notiz-, Repetition- und Hidden-Row-Goldens | `FocusStepRowView.appendGrainAnchor` unabhängig vom Inhalt |
| sichtbarer späterer Schritt nicht ausführbar | explizite `StepExecutionUiAction` je offener Zeile | einfache und wiederholte spätere Schritte in View-/Use-Case-Tests | View leitet Aktion nicht aus Listenindex ab |
| aktueller Schritt nicht verschiebbar / anderer nicht vorziehbar | `TodayCoordinator`, reine Permutation und gezielter Positionswrite | Reducer-, Room-, Gesture- und Accessibilitytests | `IDLE/DRAGGING/PERSISTING` plus `TodayStepOrderRepository` |
| Tau/Gefäß zeigen unklaren Reward | `RewardBreakdown`, `resultXp`, `XpVesselUiModel` und Formatter | Rewardgrenzen einschließlich `15 × 1,5 = 23` und UI-Verträge | Rundung nur in `RewardBreakdown` |
| erledigte Aufgaben bleiben in Heute | disjunkte Fokus-, Timeline- und `completedToday`-Projektionen | Mapper-/Charakterisierungs- und Undo-Tests | `TodayUiModel` besitzt keine allgemeine Taskliste |

## Architekturkritik

| Frühere Schuld | Umsetzung | Test/Gate | Grenze |
|---|---|---|---|
| mehrere Geometriewahrheiten | `ui.leaf` mit Shape, Surface und Spec | Architekturtest + pixelidentische Goldens | UI-Paketgrenze |
| überladene Snapshots | getrennte Focus-/Timeline-/Historymodelle | Modellinvarianten und Mappertests | `:today-core` |
| verteilter Reorderzustand | Reducer und Coordinator | Zustandsmatrix, Duplicate Drop, Refresh/Fehler | `:today-core` |
| breite Persistenzabhängigkeit | Capability-Ports | fokussierte Port- und Querybudgettests | `:core-domain` |
| Completion-Sammelservice | Step-/Occurrence-Services | Use-Case-, Ledger- und Undo-Suite | `:core-domain` |
| Today-Dispatch in Activity/ViewModel | `TodayActionSink` und `TodayCommandDispatcher` | Activity-/ViewModel-Architekturtests | App-Rand statt Fachswitch |
| schwache Paketkonventionen | physische Java-Module | `compileJava` beider Module | Gradle-Compilergraph |
| Reflection-/Rendergetter-Tests | öffentliche Gesten, Actions und sichtbare Semantik | Robolectric + Instrumentation-APK | keine Reorder-Reflection |

## Nicht veränderte Verträge

- Datenbankschema und Exporte bleiben auf Version 14.
- Golden-PNGs wurden nicht aktualisiert.
- Reward-Ledger, Undo, Carry-forward und Zeitplanung behalten ihre bisherigen Fachverträge.
- Java 17, programmatische Android-Views und der visuelle Stil bleiben bestehen.

## Bewusst verbleibend

Breites Android-Orchestrieren im `TaskViewModel`, manuelle Widgetinvalidierung,
Legacy-Wiederholungsspalte, programmatische Layoutkomplexität und lokal fehlende Geräteausführung
sind keine stillschweigend offenen Roadmappunkte. Sie sind in der aktualisierten
Architekturkritik priorisiert und benötigen jeweils einen eigenen Produkt- oder
Migrationsauftrag.
