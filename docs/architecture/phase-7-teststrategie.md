# Phase 7: Testpyramide, Goldens und Accessibility

Stand: 2026-08-19

## Testschichten

- Reines JUnit mit `InMemoryTaskRepository`: Reward, Completion, Undo, Schedule,
  Transaktionsrollback und Dauerlast. Diese Tests kennen Android und Room nicht.
- Room/Robolectric: Entity-Mapping, Fremdschlüssel und Unique Constraints, echte
  Transaktionsgrenzen, SQL-Querybudgets, Serviceintegration, Prozessneustart und Migrationen.
- Room/Instrumentation: Migrationen mit `MigrationTestHelper` gegen die exportierten Schemas.
- Robolectric/UI: Today- und Inline-Editor-Matrix, Accessibility-Verträge, Reduced Motion,
  Palettenkontrast sowie Phone-/Widget-Goldens.

Room-Integrationsabdeckung bleibt absichtlich bestehen. Der In-Memory-Port soll Fachfehler
schnell lokalisieren, aber weder SQLite noch Room simulieren.

## Migrationsmatrix

`DatabaseMigrationRobolectricTest` läuft mit `@Config(sdk = {26, 35})` und deckt alle
Ausgangsversionen 1 bis 6 bis Schema 7 ab. `ExportedRoomSchemaFixture` erzeugt historische
Tabellen, Indizes, Views und Room-Setup direkt aus
`app/schemas/de.thonktank.autosecretary.AppDatabase/<version>.json`. Szenariospezifische Daten
werden danach separat geseedet. Damit kann das Fixture-SQL nicht unbemerkt vom veröffentlichten
Room-Schema abweichen.

## Golden-Vertrag

`GoldenAssertions` schreibt Actual immer nach `app/build/reports/goldens`. Bei einer Abweichung
entstehen daneben Expected und Diff. Der Diff markiert abweichende Pixel magenta. Baselines
werden nur mit der jeweils expliziten lokalen Update-Variable geschrieben; sobald `CI` oder
`GITHUB_ACTIONS` gesetzt ist, bricht ein Updateversuch ab. Phase 7 ändert keine Golden-Baseline.

## Accessibility- und Layoutmatrix

Today und der expandierte Satzeditor werden für jede Kombination aus 320/412/600 dp und Font
Scale 1,0/1,3/2,0 gemessen. Zusätzlich werden Default-TalkBack-Reihenfolge, Rollen, Checked-
State, Beschriftung, Tastaturaktivierung, 48-dp-Ziele und Text-Clipping geprüft. Die bestehenden
Tests prüfen Reduced Motion sowie Kontrast über alle Tageszeiten und Theme-Modi.

Diese Hosttests ersetzen keinen realen Accessibility-Scanner oder eine manuelle TalkBack-
Prüfung. Ebenso ist der Dauerlastfall ein deterministischer Algorithmuswächter und kein
Gerätebenchmark; Renderer-Messungen stehen separat in `wood-grain-benchmark.md`.
