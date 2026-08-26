# Charakterisierung des Alles-Tabs

Stand: 22. August 2026

Diese Baseline schützt den mit ADR-021 festgelegten Alles-Tab vor unbeabsichtigten visuellen
und funktionalen Änderungen. Als Designquelle wurde einmalig
`/home/aaron/Schreibtisch/Alles-Tab redesign.zip` geprüft. Verbindliches Ziel darin ist
`design_handoff_alles_tab/Alles-Standard.dc.html`; die beiden Dateien „Ist-Zustand“ und
„Entwurf“ sind nur Vergleichsstufen. Die im Handoff noch offenen Fragen werden durch ADR-021
aufgelöst: Sortieren ist eine Aktion in der Ergebniszeile, der Wochentag erscheint nur im
Sortiermodus, Einfügeziele sind nur während eines Drags sichtbar und archivierte Aufgaben
bleiben bearbeitbar.

## Abgleich mit dem Handoff

| Vertrag | Charakterisierung |
| --- | --- |
| kompakter Kopf, Suche und einklappbare Filter | `standard`, `filters-dropdown` |
| Suche in Titel und Schritten samt Hervorhebung | `search-title`, `search-step` |
| getrennte aktive und archivierte Aufgaben | `standard`, `archived` |
| Sortieransicht und nur temporär sichtbare Einfügeziele | `sort-drag-targets` sowie `hiddenStepTargetsAreNotFocusableUntilDragStarts` |
| unterscheidbare Leergründe | `empty-search`, `empty-filter`, `empty-status` |
| 44-dp-Textflächen, 48-dp-Iconflächen; Schrittgriff ausdrücklich 44 × 44 dp | `interactiveRowsMeetMinimumHeightAndIconTargetsAreSquare` |
| kleine, große und breite Layouts | `width-320-font-1_3`, `width-412-font-2_0`, `width-600` und die bestehende Breiten-/Schriftmatrix |
| Nachtpalette | `night` |
| Mehrfachfilter: ODER innerhalb, UND zwischen Achsen | `filterAxesUseOrInternallyAndAndAcrossStatusSearchAndAxes` |
| Fokus, Accessibility, 120 Elemente und stabile IDs | bestehende `AllTasksVirtualizationTest`- und `AllTasksUiStateTest`-Verträge |

Die visuelle Matrix umfasst damit 13 PNGs unter
`app/src/test/resources/golden/all-tasks`. Das bereits vorhandene `standard.png` blieb beim
Aufbau der Matrix pixelidentisch. Zwölf neue Baselines wurden zuerst als Actual-Artefakte
erzeugt, gemeinsam visuell geprüft und erst danach übernommen.

## Golden-Vertrag

Alle Alles-Tab-Goldens verwenden Nulltoleranz: Kanal-Toleranz `0`, maximal verändertes Verhältnis
`0`. Jeder normale Testlauf schreibt sein Actual nach
`app/build/reports/goldens/all-tasks`. Bei einer Abweichung kommen Expected und ein magentafarbener
Diff hinzu. Die Quality-Workflow-Artefakte machen diese Dateien im Pull Request sichtbar.

Neue und geänderte Baselines benötigen zwei getrennte Läufe:

```bash
./gradlew testInstrumentationUnitTest --tests '*AllTasksRenderRobolectricTest' \
  --no-parallel --max-workers=1
# Actual bzw. Expected/Actual/Diff prüfen
UPDATE_ALL_TASKS_GOLDENS=1 ./gradlew testInstrumentationUnitTest \
  --tests '*AllTasksRenderRobolectricTest' --no-parallel --max-workers=1
# Freigabe wieder ohne Update-Schalter prüfen
./gradlew testInstrumentationUnitTest --tests '*AllTasksRenderRobolectricTest' \
  --rerun-tasks --no-parallel --max-workers=1
```

`GoldenAssertions` übernimmt auch eine neue Baseline nur, wenn ein vorher ohne Update-Schalter
erzeugtes, pixelidentisches Actual vorhanden ist. Unter `CI` oder `GITHUB_ACTIONS` ist jede
Baselineänderung gesperrt. Ein fehlschlagender Golden-Test allein ist niemals eine Erlaubnis,
eine Referenz zu überschreiben.
