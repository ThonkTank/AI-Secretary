# Wood-Grain-Renderer: reproduzierbarer Benchmark

Stand: 2026-08-19

## Zweck und Ausführung

Der Benchmark verwendet dieselbe deterministische 1080×900-xxhdpi-Fixture mit drei
überlappenden Anchors, 8–14 Konturen und einer Textmaske. Er misst Geometrieverfügbarkeit bei
Cache-Miss, Cache-Hit, wiederholte Draw-Zeiten sowie Heap- und Cachewachstum über 16 verschiedene
Größen. Er läuft absichtlich nicht im normalen Unit-Gate:

```bash
./gradlew testDebugUnitTest \
  --tests 'de.thonktank.autosecretary.WoodGrainBenchmarkTest' \
  -Dwoodgrain.benchmark=true --no-daemon --rerun-tasks
```

Das maschinenlesbare Ergebnis steht anschließend in
`app/build/test-results/testDebugUnitTest/TEST-de.thonktank.autosecretary.WoodGrainBenchmarkTest.xml`
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
