# ADR-019: Eindeutige Today-Projektion, Rewards und Interaktionszustände

- Status: angenommen
- Datum: 2026-08-21

## Kontext

`TaskSnapshot` verbindet Fokus-, Timeline-, Completion- und Rewarddaten in einer langen
primitiven Konstruktorfläche. `TodayUiModel.tasks`, Fokus, Timeline und Tageshistorie bilden
überlappende Wahrheiten. Rewardbasis, Faktor und Endwert werden auf Mapper, Snapshot und View
verteilt abgeleitet. Beim Umsortieren besitzt die View außerdem eine lokale Vorschau neben dem
autoritativen Dashboardzustand; Drag und Accessibility berechnen Zielpositionen getrennt.

## Entscheidung

- `RewardBreakdown` ist der einzige fachliche Wert für Grund-XP, Kombostufe, Faktor und gerundetes
  Ergebnis. Presentation und Views rechnen diese Werte nicht erneut aus.
- Today besitzt getrennte, immutable Modelle für Fokus, Timeline und erledigte Tageshistorie.
  Ein allgemeiner `TaskSnapshot` ist keine Today-Schnittstelle mehr.
- Jeder Fokus-Schritt trägt einen expliziten Status und eine explizite ausführbare Aktion. Eine
  View leitet fachliches Verhalten nicht aus Listenindex oder Wiederholungstyp ab.
- `TodayAction` transportiert nur stabile IDs und konkrete Eingabewerte. Ein
  `TodayCoordinator` und ein reiner Reducer besitzen flüchtigen Eingabe- und Reorderzustand.
- Reorder verwendet die Zustände `IDLE`, `DRAGGING` und `PERSISTING`. Drop erzeugt über eine
  eindeutige Command-ID genau einen Write; Abbruch oder Fehler stellt die kanonische Reihenfolge
  wieder her. Drag und Accessibility verwenden denselben Move-Intent.
- Use Cases liefern typisierte Ergebnisse. Eine tatsächliche Zustandsänderung wird nicht als
  bedeutungsloses `false` oder `RewardReceipt.none()` dargestellt.

## Konsequenzen

Today-Aktionen verlassen schrittweise `DashboardEvent`, `MainActivity` und den breiten
`TaskViewModel`. Erfolgreiches reines Reorder kann den bestätigten Today-State ohne vollständigen
Dashboard-/Kalenderreload übernehmen. Abschluss-, Reward- und Undo-Verträge bleiben unverändert
und werden weiterhin transaktional aus dem Ledger projiziert.
