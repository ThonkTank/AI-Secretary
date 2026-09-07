# Roadmap: mobile Aufgabenverwaltung und Ablaufübersicht

Status: verbindlich

Beschlossen: 2026-09-07

Ausgangsstand: `1bab65fb` (`origin/main`)

Ausgangsschema: Room 23

## Ziel und Grenzen

Der Alles-Tab wird als übersichtlicher mobiler Aufgabenkatalog gestaltet; anschließend erhält
die bestehende Ablaufdetailseite dieselbe mobile visuelle Sprache. Der App-Header, Footer,
Aufgabeneditor, Domainmodelle, Use Cases, Room-Schema und Ablaufsemantik bleiben unverändert.
ADR-021, ADR-033 und ADR-034 bilden den verbindlichen Produktvertrag.

## Phase 1 – Mobiler Alles-Katalog

Branch: `codex/alltasks-mobile-catalog`

- Suche, kompakter Ablaufmonitor, Filter-/Reihenfolge-Aktionen, Trefferzahl und gruppierte
  Aufgabenblätter bilden eine klare einspaltige Hierarchie.
- Das Filterblatt wirkt ohne Anwenden-Schritt. Der gespeicherte Darstellungszustand
  `filtersExpanded` und seine Action-/Callback-Grenzen entfallen.
- Jede Aufgabenzeile erhält eine direkte Bearbeiten-Aktion zum unveränderten Editor; Löschen
  bleibt im Überlauf.
- Im Sortiermodus bleiben Suche und Filter verfügbar. Nur die aktive Einfügeposition erscheint
  während eines Drags; bestehende Gesten-, Randscroll- und Accessibility-Verträge bleiben.
- Die Aufgabenliste wird nach Tageszeit gruppiert. Mehrfachplatzierungen und stabile Identitäten
  bleiben gemäß ADR-021 erhalten.
- Der Ablaufmonitor zeigt ausschließlich bis zu zwei nutzergestartete Runs und öffnet die
  bestehende Detailseite.

Abnahme: Zustands-/Saved-State-, Projektions-, Robolectric-Golden-, Instrumentierungs-,
Accessibility- und Architekturtests werden angepasst. Die vollständige lokale Quality-Prüfung,
PR-Matrix, der Squash-Merge sowie der anwendbare exakte Main-Nachweis müssen grün sein.

## Phase 2 – Mobile Ablaufdetailseite

Branch: `codex/flow-runs-mobile-screen`

- `FlowRunsActivity` bleibt Host für Navigation, Insets, Dialoge und Toasts; die imperative
  Renderhierarchie wird durch einen zustandslosen Foundation-Compose-Screen ersetzt.
- Ein mobiler Zurück-Header, kurze Einleitung und kompakte Ablaufkarten zeigen Titel/Seed,
  aktuellen Schritt, Fortschritt, Status und Ressourcen in einer klaren Reihenfolge.
- Aufschieben, Nicht bereit, Jetzt bereit, Zeit anpassen, Hoch/Runter und Abbrechen bleiben
  vollständig verfügbar. Während einer Änderung sind konkurrierende Aktionen gesperrt.
- Laden, Leerzustand, Änderung und Fehler werden ausdrücklich dargestellt. ViewModel,
  Screen-State, Actions, Use Cases und bestehende Bestätigungs-/Dauerdialoge bleiben
  unverändert.
- `FlowRunningStripView` wird nur entfernt, wenn ein erneuter Verwendungsnachweis bestätigt,
  dass sie ungenutzt ist.

Abnahme: Screen-State-/Action-Verträge, Robolectric-/Golden-, Interaktions-, Accessibility- und
Architekturtests decken alle Zustände und Aktionen ab. Danach gelten dieselben PR-, Squash- und
Main-Gates wie in Phase 1.

## Ausführungsregel

Jede Phase beginnt vom frisch gemergten `origin/main` in einem isolierten Worktree. Vor
Produktcode wird ihr Plan im getrennten
[Ausführungsprotokoll](all-tasks-mobile-execution.md) festgehalten. Nach den Tests folgen ein
Anforderungsaudit und die Prüfung, dass Editor, Domain und Persistenz nicht unbemerkt verändert
wurden. Phase 2 beginnt erst nach dem vollständigen Abschluss von Phase 1.
