# Fortschritt der Today-/Fokus-Architekturbereinigung

Stand: 2026-08-21, Datenbankschema 14

## Phase 1 – eindeutige Today- und Rewardmodelle

Status: implementiert und gegen die Roadmap auditiert.

- `RewardBreakdown` erzeugt Grundwert, Kombostufe, Faktor und gerundetes Ergebnis atomar. Alle
  fachlichen XP-Rundungen laufen durch seine Factory.
- `XpVesselView` bindet ausschließlich `XpVesselUiModel`; die deutsche Faktor- und
  Breakdownformatierung liegt im injizierbaren `RewardTextFormatter`.
- `TaskSnapshot` ist aus Main-, Debug-, Unit- und Android-Testquellen entfernt. Fokus,
  Tageshistorie und Menüaktionen verwenden `FocusTaskUiModel`, `CompletedTaskUiModel` und
  `TaskActionTarget`; die offene Timeline behält `TimelineTaskUiModel`.
- `TodayUiModel` besitzt keine allgemeine Taskliste. Fokus, offene Timeline und erledigte
  Historie sind disjunkt; der Mapper partitioniert über stabile Occurrence-/Task-IDs.
- Jeder Fokus-Schritt besitzt `FocusStepStatus`, `StepExecutionUiAction` und einen
  `RewardBreakdown`. Die View interpretiert weder Listenposition noch Wiederholungstyp als
  fachlichen Command.
- Die Debug-Golden-Fixtures behalten ihren historischen visuellen Grain-Level getrennt vom
  fachlichen Reward. Im Produktiv-Mapping sind Grain-Level und Reward-Kombostufe identisch. Die
  temporäre visuelle Eigenschaft geht in Phase 2 in `GrainSpec` auf.

Der Phase-Gate-Lauf enthält 309 Hosttests, davon 308 erfolgreich und einen bewusst
übersprungenen Test. Rewardfälle decken 0, `15 × 1,5 = 23`, ganze Faktoren, dreistellige Werte
und `.5`-Rundungsgrenzen ab. Fokus- und Homescreen-Goldens sind pixelidentisch; Schema und
PNG-Baselines wurden nicht geändert.
