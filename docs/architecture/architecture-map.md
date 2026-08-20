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

- `MaterializeDueOccurrences` bleibt der transaktionale Orchestrator, delegiert Planung, Rollover
  und Assembly aber inzwischen an getrennte Komponenten.
- `Task.nextDueOn` ist fachlich als Planungscursor geklärt, trägt im Storage aber weiterhin den
  historischen Feldnamen.
- Das Open-Occurrence-Limit wird durch SQLite-Trigger geschützt; Room exportiert diese Trigger
  nicht vollständig, deshalb müssen sie in jeder betroffenen Migration manuell rekonstruiert
  und getestet werden.
- Carry-forward-Provenienz ist seit Phase 3 fachlich modelliert und seit Schema 9 persistent.
- `Occurrence.completedOn` ist seit Schema 10 nullable statt über einen leeren String codiert.
- Optionale Task-Daten (`lastScheduledOn`, `lastCompletedOn`, `boundUntilOn`, `deadlineOn`) sind
  seit Schema 11 ebenfalls nullable; `nextDueOn` bleibt als fachlich erforderlicher Cursor
  nicht-null.
- Teilernte schließt eine Occurrence trotz offener Schritte; die UI filtert diese Sonderlage.
- Room nutzt in älteren historischen Schemas weiterhin leere Strings; Schema 11 migriert diese
  Werte beim Öffnen des aktuellen Datenbestands zu SQL-`NULL`.
- `TaskRepository`, `TaskViewModel` und `MainActivity` bleiben breite Orchestratoren.
- Das Android-Modul besitzt keine compiler-erzwungenen Architekturgrenzen.
- Upgrade-, Zeitzonen-, Prozessneustart- und parallele Materialisierungstests sind nicht vollständig
  durch lokale Instrumentation abgesichert.

Phase 3 hat die fachliche Carry-forward-Provenienz im Domainmodell eingeführt und Teilernte in
`HARVESTED_WITH_MISSED_STEPS` gegenüber vollständiger `COMPLETED`-Ernte unterschieden. Die
Room-Spalten, Trigger und die entsprechende Migrationsprüfung sind in Phase 4 ergänzt.

Diese Liste wird in späteren Phasen aktualisiert und nicht stillschweigend als erledigt betrachtet.

Phase 5 führt mit `TaskDefinitionRepository` und `TransactionalRepository` einen ersten
verbindlichen Fachport ein. Task-Erstellung, Bearbeitung, Löschung, Verschiebung und Detail-Lesen
hängen nicht mehr vom gesamten Reward-/Occurrence-Vertrag ab. `TaskRepository` bleibt als
Composition-Root-Kompatibilitätsaggregat erhalten, damit die Migration schrittweise bleibt.

Phase 6 führt `DashboardRefreshReason` und `DashboardRefreshPolicy` ein. Initialer Start,
Foreground-Rückkehr, externe Datenänderung, persistierende Commands und ein echter Datumswechsel
sind dadurch getrennte Refresh-Ursachen; ein Minuten-Tick bei unverändertem Datum lädt nicht
erneut aus der Datenbank.
