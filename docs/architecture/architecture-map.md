# Architekturkarte und Refactor-Baseline

Stand: 2026-08-20, Ausgangspunkt `f6b9cc4a`

Dieses Dokument ist die Baseline für den phasenweisen Architektur-Refactor. Es beschreibt
die tatsächlich belastbaren Abhängigkeiten und nicht ein gewünschtes Zielbild.

## Abhängigkeiten

```text
Android Views / Widget / Broadcasts
            |
            v
DashboardEvent, Widget-Aktion, Lifecycle-Callback
            |
            v
MainActivity / TaskViewModel / WidgetUpdateCoordinator
            |
            v
DashboardPresenter, CompletionService, MaterializeDueOccurrences,
Task- und Editor-Use-Cases
            |
            v
TaskRepository
            |
            v
RoomTaskRepository -> TaskDao -> Room Entities / DatabaseMigrations
```

Die Domain kennt `TaskRepository`, `Clock`, Occurrence-, Reward- und Completiontypen. Die
Room-Entities werden über `TaskEntityMapper` in Domainmodelle übersetzt. Das Root-Paket enthält
weiterhin zahlreiche Android-Views, Lifecycle-Klassen und Adapter; harte Modulgrenzen existieren
nicht.

## Zustandsbesitzer

| Zustand | Aktueller Besitzer | Persistenz | Autoritativer Schreibpfad |
|---|---|---:|---|
| Task-Definition | `Task`/Repository | ja | Task-Use-Cases |
| Planungscursor | `Task.nextDueOn` | ja | Materialisierung, derzeit auch Scheduling-Projektion |
| Occurrence-Status | `Occurrence`/Repository | ja | Materialisierung und `CompletionService` |
| Schrittfortschritt | `OccurrenceStep`/Repository | ja | `CompletionService`/Repetition-Use-Cases |
| Reward-Historie | Reward-Ledger | ja | `CompletionService` |
| Eingabeentwurf | `TaskViewModel`/Editor-State | nein | Reducer und ViewModel |
| Renderzustand | `DashboardUiState` | nein | `TaskViewModel` |
| Widget-Projektion | Widget-Mapper | nein | `WidgetUpdateCoordinator` |

Bekannte Mehrfachbedeutungen sind der Planungscursor `nextDueOn`, die Kombination aus
`OccurrenceState.COMPLETED` und offenen Schritten sowie die manuelle Kopplung zwischen
Persistenz-Commands und Widgetinvalidierung.

## Baseline-Gates

Vor und nach jeder Phase müssen mindestens folgende Eigenschaften geprüft werden:

1. `./gradlew --no-daemon --max-workers=1 testDebugUnitTest` bleibt erfolgreich.
2. `./gradlew --no-daemon --max-workers=1 compileDebugAndroidTestJavaWithJavac` bleibt erfolgreich.
3. Materialisierung ist innerhalb einer Transaktion idempotent.
4. Pro Task und Slot existiert höchstens eine offene Occurrence.
5. Tägliche, Intervall- und Wochentagsplanung bleibt am geplanten Datum verankert.
6. Teilernte, Undo, Carry-forward, Widget-Projektion und Reward-Ledger behalten ihren Vertrag.
7. Room-Migrationen bleiben vorwärtskompatibel; keine Migration löscht Nutzdaten ohne expliziten
   Kompatibilitätsvertrag.

Connected-Tests werden ausgeführt, wenn ein Gerät verfügbar ist. Ohne Gerät wird ausschließlich
der APK-/Quellkompilierungsstatus berichtet; ein lokaler Build gilt dann nicht als Beweis für
Instrumentation-Verhalten.

## Bekannte technische Schuld

- `MaterializeDueOccurrences` vereinigt Planung, Rollover, Carry-forward und Reconciliation.
- `Task.nextDueOn` vermischt Planungscursor und historische Schedulingsemantik.
- Das Open-Occurrence-Limit ist logisch, aber nicht als partieller Datenbank-Constraint geschützt.
- Carry-forward besitzt keine explizite Occurrence-Abstammung oder Carry-Reason.
- Teilernte schließt eine Occurrence trotz offener Schritte; die UI filtert diese Sonderlage.
- Room nutzt bei optionalen Datumswerten leere Strings als `null`-Sentinel.
- `TaskRepository`, `TaskViewModel` und `MainActivity` bleiben breite Orchestratoren.
- Das Android-Modul besitzt keine compiler-erzwungenen Architekturgrenzen.
- Upgrade-, Zeitzonen-, Prozessneustart- und parallele Materialisierungstests sind nicht vollständig
  durch lokale Instrumentation abgesichert.

Diese Liste wird in späteren Phasen aktualisiert und nicht stillschweigend als erledigt betrachtet.
