# Fortschritt und Selbstaudit der Frontend-Modernisierung

Dieses Dokument protokolliert Vorprüfung, Zuschnitt, Nachweise und ehrliche Nachkritik jeder
Phase. Die autoritative Zieldefinition bleibt die
[Frontend-Modernisierungsroadmap](frontend-modernization-roadmap.md); Abweichungen werden hier
dokumentiert und nicht durch rückwirkendes Umschreiben der Roadmap verborgen.

## Phase 0 – Architekturvertrag und Freeze

### Vorprüfung

- Ausgangspunkt: sauberer `main` auf `e56ce330`, identisch mit `origin/main`, Release-Tag
  `forest-android-1011101`.
- Tatsächliches Room-Schema: 16. Die ältere Architekturkarte nennt noch Schema 14 und ist damit
  für diese Baseline nicht autoritativ.
- Präsentationsbaseline: 192 Java-Dateien, keine Kotlin-Datei und 28 eigene View-/ViewGroup-
  Implementierungen im App-Hauptquellensatz.
- Relevante Größen: `MainActivity` 389 Zeilen, `TaskViewModel` 779 Zeilen,
  `TaskEditorView` 958 Zeilen und `AllTasksViewModel` 249 Zeilen.
- Bestehende Homescreen- und Editorreferenzen sowie deren menschliche Freigaben bleiben
  unverändert.

### Implementationsplan

Phase 0 ist bewusst dokumentarisch und in einem Sprint abschließbar. Sie legt die Original-
Roadmap als unveränderliche Referenz ab, trifft die neue Präsentationsentscheidung in einer ADR,
verlinkt beides aus dem Architekturindex und führt dieses separate Fortschrittsprotokoll ein.
Produktcode, Buildsystem und Golden-Baselines werden nicht berührt.

### Ergebnis und Nachaudit

- Angelegt wurden die autoritative Roadmap, ADR-022 und dieses getrennte Phasenprotokoll; der
  Architekturindex verlinkt alle drei Artefakte.
- Die Roadmap fixiert Zielzustand, Schnittstellen, Ausnahmen, Phasenschnitt, Split-Regel,
  Selbstkritik und Merge-/Gerätegates. Produktcode und visuelle Baselines blieben unberührt.
- Lokale Nachweise: `git diff --check`, interne Markdown-Linkprüfung sowie die vollständigen
  Python-Suites unter `scripts/ci` und `scripts/release` sind grün.

Negativ fiel auf, dass die bisher als aktuell verlinkte Architekturkarte noch behauptete,
Schema 14 bleibe unverändert. Das war nach den Editor-Migrationen zu Schema 15 und 16 falsch und
wurde in dieser Phase korrigiert. Die Roadmap übernimmt bewusst aktuelle Toolchain-Versionen;
dies ist eine zeitabhängige Festlegung, die im Vorcheck von Phase 2 gegen die dann offiziellen
Kompatibilitätsmatrizen erneut bestätigt werden muss. Darüber hinaus wurden keine Shortcuts,
zweiten Wahrheiten oder Scope-Vereinfachungen identifiziert. Eine Nacharbeitsphase ist nach dem
lokalen Audit nicht nötig; der entfernte PR-Gate bleibt noch ausstehend.
