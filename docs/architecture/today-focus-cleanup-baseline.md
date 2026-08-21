# Baseline der Today-/Fokus-Architekturbereinigung

Stand: 2026-08-21, Ausgangspunkt `91467be1`, Datenbankschema 14

## Unveränderliche UX-Verträge

1. Kopfblatt-Ringe sind konzentrisch zum Mittelpunkt der oberen rechten Blattrundung.
2. Kopfblatt-, Fokus- und Timeline-Ringe werden exakt durch ihre asymmetrische Blattform begrenzt.
3. Gefäß- und Tau-Ringe verwenden die finalen zentrierten Bounds ihres sichtbaren Anchors.
4. Notiz, Menge und Wiederholungseditor ändern weder Ring-Owner noch Ring-Anker.
5. Jedes sichtbare offene Tau ist ausführbar; ein späterer Wiederholungsschritt übernimmt den
   nächsten Planwert und wird, falls weiter offen, zum ersten offenen Schritt.
6. Offene Schritte lassen sich per Long-press-Drag und gleichwertigen Accessibility-Aktionen
   innerhalb des aktuellen Vorkommens umsortieren; erledigte Slots bleiben stehen.
7. Ein Tau zeigt den bereits multiplizierten Endwert. Das Gefäß zeigt Endwert und darunter
   `Grundwert × Faktor`.
8. Erledigte Aufgaben erscheinen nicht in Fokus oder Timeline, sondern ausschließlich in der
   eingeklappten Tageshistorie mit exaktem Undo.

## Reproduzierbare technische Baseline

Ausgeführt mit JDK 21, Gradle 8.10.2, Robolectric 4.14.1 und `--max-workers=1`:

| Gate | Ergebnis |
|---|---:|
| Hosttests | 307, davon 306 erfolgreich und 1 bewusst übersprungen |
| Fehler | 0 |
| Testlauf mit `--rerun-tasks` | 1:24,00 min |
| maximale RSS des Testlaufs | 1.133.556 KiB |
| Lint-Issues | 0 |
| Debug- und Android-Test-Kompilierung | erfolgreich |

Der separate xxhdpi-Grain-Benchmark meldete:

```json
{"missMedianMs":11.652,"cacheHitMedianMs":0.000,"frameMedianMs":0.004,
 "frameP95Ms":0.009,"cacheEntries":16,"cacheBytes":762128,
 "heapDeltaBytes":7066048,"buildCount":16}
```

Hostzeiten sind nur auf derselben Maschine vergleichbar. Für die Abschlussphase gelten Median
und p95 von Grain-Build beziehungsweise Draw als regressionsfrei, wenn sie höchstens zehn Prozent
über dieser Baseline liegen. Der Cache darf 4 MiB nicht überschreiten.

## Golden- und Schema-Vertrag

- Keine Refactor-Phase darf PNG-Baselines ohne ausdrückliche Produktänderung aktualisieren.
- Schema 14 bleibt während der Roadmap unverändert. Gezielte DAO-Operationen sind erlaubt, neue
  Tabellen, Spalten, Indizes oder Migrationen nicht.
- Der produktiv unterstützte Upgradepfad beginnt bei Schema 8; historische Migrationen 1 bis 7
  bleiben Testfixtures und Archivvertrag.
