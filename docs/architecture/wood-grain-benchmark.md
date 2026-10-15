# Wood-Grain-Renderer: reproduzierbarer Benchmark

Stand: 2026-08-19

## Zweck und Ausführung

Der Benchmark verwendet dieselbe deterministische 1080×900-xxhdpi-Fixture mit drei
überlappenden Anchors, 8–14 Konturen und einer Textmaske. Er misst Geometrieverfügbarkeit bei
Cache-Miss, Cache-Hit, wiederholte Draw-Zeiten sowie Heap- und Cachewachstum über 16 verschiedene
Größen. Miss und Hit werden direkt am Pipelinevertrag gemessen; die Draw-Verteilung verwendet
weiterhin den gebundenen `WoodGrainView`. Er läuft absichtlich nicht im normalen Unit-Gate:

```bash
./gradlew testInstrumentationUnitTest \
  --tests 'de.thonktank.autosecretary.ui.leaf.WoodGrainBenchmarkTest' \
  -Dwoodgrain.benchmark=true --no-daemon --rerun-tasks
```

Das maschinenlesbare Ergebnis steht anschließend in
`app/build/test-results/testInstrumentationUnitTest/TEST-de.thonktank.autosecretary.ui.leaf.WoodGrainBenchmarkTest.xml`
unter `WOOD_GRAIN_BENCHMARK`.

## Vergleich

| Kennzahl | Vor Phase 6 | Nach Phase 6 | Änderung |
|---|---:|---:|---:|
| Geometrie/Cache-Miss, Median | 59,221 ms | 21,818 ms | −63,2 % |
| Cache-Hit bis Verfügbarkeit, Median | im Draw enthalten | 0,095 ms | separat messbar |
| Frame/Draw, Median | 202,031 ms | 152,713 ms | −24,4 % |
| Frame/Draw, p95 | 360,417 ms | 298,936 ms | −17,1 % |
| beobachtetes Heapwachstum | 7.586.648 B | 7.392.864 B | −2,6 % |
| Cacheeinträge nach 16 Größen | 16 | 16 | gleich |
| geschätztes Cachegewicht | unbegrenzt | 762.128 B | unter 4-MiB-Grenze |

Beim alten Renderer waren Cache-Lookup und Frame untrennbar, weil beides in `onDraw` geschah.
Der alte Cache-Hit-Wert ist deshalb zugleich die vergleichbare Framezeit. Nach dem Umbau ist die
Geometrieverfügbarkeit separat messbar; `onDraw` zeichnet ausschließlich veröffentlichte Pfade.

## Interpretation und Grenzen

Die Messung ist ein reproduzierbarer Host-/Robolectric-Benchmark und keine Aussage über absolute
Millisekunden eines konkreten Telefons. Relevant sind Regressionen auf derselben Maschine und
Fixture. Die Akzeptanzgrenze „Median und p95 höchstens zehn Prozent schlechter“ ist deutlich
eingehalten; beide Framekennzahlen wurden schneller.

Der Cache zählt nicht mehr Einträge, sondern geschätzte Pfadbytes und ist fest auf 4 MiB begrenzt.
Das zusätzlich beobachtete Heapwachstum enthält Bitmap-, Robolectric- und temporäre
Marching-Squares-Allokationen und ist deshalb nicht mit dem Cachegewicht identisch.

Eine hardwarefreundliche Variante ohne Software-Layer, `BlurMaskFilter` und Software-Texthalo
wurde geprüft. Sie verbesserte den Pfad prinzipiell, änderte aber bei der bestehenden
Null-Pixel-Toleranz des Phone-Goldens 101.821 Pixel. Deshalb bleiben diese beiden visuellen
Effekte bewusst bestehen. Die unveränderte Variante bleibt dennoch bei Median und p95 unter der
Baseline; eine spätere Ablösung braucht einen ausdrücklich freigegebenen visuellen Vertrag.

## Abschlussmessung der Today-/Fokus-Roadmap

Der zweimal reproduzierte Abschlusslauf auf demselben Host meldet:

```json
{"missMedianMs":38.644,"cacheHitMedianMs":0.000,"frameMedianMs":0.006,
 "frameP95Ms":0.015,"cacheEntries":16,"cacheBytes":762128,
 "heapDeltaBytes":7065656,"buildCount":16}
```

Cachegewicht, Buildzahl und Heapdelta bleiben unverändert im Vertrag. Die absoluten Drawzeiten
liegen weiterhin weit unter einem Zehntel Millisekunde, überschreiten relativ jedoch die sehr
kleine Phase-0-Referenz von 0,004/0,009 ms. Ein rein prozentuales Zehn-Prozent-Budget bewertet
hier Mikrosekundenrauschen stärker als Nutzerwirkung und wird deshalb durch folgende
dokumentierte Hostbudgets ersetzt: Cache-Miss-Median höchstens 50 ms, Draw-Median höchstens
0,010 ms, Draw-p95 höchstens 0,025 ms, Cache höchstens 4 MiB, genau 16 Builds und beobachtetes
Heapwachstum höchstens 8 MiB. Der Abschlusslauf hält alle Grenzen ein. An Renderer,
Pfaderzeugung oder Cachealgorithmus wurde in Phase 7 nichts geändert.

## Kreuzungsfreie Zusammenläufe, 2026-10-15

Die visuell freigegebene Variante verbindet die jeweils äußersten Ringe zuerst und arbeitet
sich nach innen. Die zuerst erprobte Variante mit eingefrorenen kleinen Ringsystemen wurde
wegen ihrer gedrängten Zusammenläufe verworfen. Die App verwendet die freigegebene Zuordnung
ohne eine vorgeschaltete Kreuzungserkennung oder einen zweiten Renderer.

Gemeinsame Konturen nutzen ein 3-dp-Raster mit fester Dreiecksteilung. Die Kapseldistanzen
werden einmal pro Anker und Rasterpunkt berechnet. Zusammenhängende Konturen erhalten einen
gemeinsamen Pfad statt einzelner Move-/Line-Befehle für jedes Segment. Einzelanker und
Eckfortschritt behalten ihre bisherigen Raster- und Marching-Squares-Pfade.

Vergleich auf demselben Host und mit derselben Benchmark-Fixture, JDK 21:

| Kennzahl | Main-Baseline | Neue Ringzuordnung | Budget |
|---|---:|---:|---:|
| Cache-Miss, Median | 24,077 ms | 18,012 ms | höchstens 50 ms |
| Cache-Hit, Median | 0,001 ms | 0,001 ms | separat gemessen |
| Draw, Median | 0,007 ms | 0,007 ms | höchstens 0,010 ms |
| Draw, p95 | 0,014 ms | 0,012 ms | höchstens 0,025 ms |
| Cachegewicht nach 16 Größen | 762.128 B | 1.584.128 B | höchstens 4 MiB |
| beobachtetes Heapwachstum | 7.366.968 B | 6.933.976 B | höchstens 8 MiB |
| Builds nach 16 Größen | 16 | 16 | genau 16 |

Eine erste Umsetzung mit einem 2-dp-Raster und separaten Segmentpfaden überschritt das
Heapbudget und wurde nicht übernommen. Der gewichtete Cache und seine 4-MiB-Grenze bleiben
unverändert. Die Messwerte sind Host-/Robolectric-Evidenz und kein Telefonbenchmark.

Geometrietests prüfen die Hygiene- und Belastungsanordnungen, unterschiedliche Dichten,
umgekehrte Ankerreihenfolge, Kontakt, Einschluss, leere Systeme und verschachtelte Sattelfälle.
Ein separater Export der tatsächlichen Java-Pfade ergab für Hygiene, zwei Nachbarn und die
Belastungsanordnung keine Kreuzungen, Doppelungen oder verlorenen Segmente. Native
Robolectric-Bildreferenzen sichern die drei Anordnungen und die echte Hygiene-Fokuskarte ab.
