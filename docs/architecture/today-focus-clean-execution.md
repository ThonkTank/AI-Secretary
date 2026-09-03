# Ausführung: saubere Heute-Aufgabenblatt-Architektur

Kanonische Grundlage:
[Roadmap: saubere Heute-Aufgabenblatt-Architektur](today-focus-clean-roadmap.md)

Dieses append-only Protokoll hält Phasenpläne, Validierung, Audits, Korrekturrunden und Blocker
getrennt von der kanonischen Roadmap fest. Frühere Befunde werden durch spätere Runden nicht
umgeschrieben.

## Phase 0 – Zielvertrag und reproduzierbare Ausgangslage

Status: implementiert

### Plan

Ergebnis: Roadmap, Ausführungsprotokoll und ADR-031 bilden den verbindlichen Zielvertrag. Der
aktive alte Frontend-Checkout und der unveröffentlichte Sammelbranch bleiben unverändert.

Reihenfolge:

1. Frischen Worktree von `origin/main` erzeugen und Branch-/Remotezustand verifizieren.
2. Roadmap ohne inhaltliche Umdeutung persistieren, ADR-031 ergänzen und beide Dokumente im
   Architekturindex verlinken.
3. Schema-, Migrations-, Golden- und Ressourcenbaselines erfassen.
4. Dokumentationsdiff, CI-/Release-Vertragstests und anwendbaren Gradle-Gate prüfen.
5. Implementierung getrennt gegen diesen Phasenplan und die vollständige Roadmap auditieren.

Abnahme: Der Diff enthält ausschließlich die drei neuen Architekturtexte und den aktualisierten
Index. Produkt-, Schema-, Migrations-, Ressourcen- und Golden-Dateien bleiben unverändert. Der
Remote-Abschluss erfolgt anschließend über Pull Request, Squash-Merge und Main-Workflow.

### Ausgangsnachweise

- `origin/main`: `39f1e5cdb37ac048c3bd2bfa101be110de2daafa`.
- Referenzbranch: `codex/today-focus-sheet-fixes` auf `be0d7f22d3794c5674734dc43be8380e290199df`,
  ausschließlich lokal.
- Schema 22: `b4f8f0a32b84cfcbc5e70a02018cc1b6bbe120682e98061a8e7773f0f75563e2`.
- Migrationen: `bed6d1c550acbe78ffd724b3057f011366a7447ef3d89854c88841a9aa6b1d9a`.
- kombinierte Focus-/Homescreen-Goldens:
  `21d1fbc4ca705263dbc83ec65efcee89502e20a552cd30421459281fd987f7a8`.
- kombinierte Android-Ressourcen:
  `139cb0cdcc1157b75689a6e90d9c2292f41af6a45539ebb6985123b400d16d0c`.

### Validierung

- `git diff --check`: grün.
- CI-Vertragstests: 16 Tests grün.
- Release-Vertragstests: 23 Tests grün.
- Der tatsächliche Phase-0-Diff wird als reine Dokumentationsänderung erkannt; Quality-,
  Instrumentierungs- und Release-Gate sind nicht erforderlich.
- Schema-22-, Migrations-, Golden- und Ressourcenhashes entsprechen den Ausgangsnachweisen.

### Audit und Abweichungen

- Planabgleich: Roadmap, getrenntes append-only Protokoll, ADR-031, Architekturindex und
  Ausgangsbaselines sind vollständig vorhanden.
- Roadmapabgleich: Der unveröffentlichte Sammelbranch wurde weder verändert noch pauschal
  übernommen. Produktcode, Tests, Schema, Migrationen, Ressourcen und Goldens sind unverändert.
- Der aktive Frontend-Checkout blieb auf `5438e733` unangetastet.
- Es wurde keine inhaltliche Abweichung gefunden; eine Korrekturrunde war nicht erforderlich.
- Der lokale Stand erfüllt Phase 0. Pull Request, Squash-Merge und Main-Workflow stehen als
  Remote-Gate noch aus; bis dahin bleibt der Status `in Arbeit`.

### Korrekturrunde 1 – Status nach dem Remote-Gate

Plan: Nach grünem Pull Request, Squash-Merge und Main-Workflow wird ausschließlich der
Phase-0-Status auf `implementiert` gesetzt. Die früheren lokalen Nachweise und der damalige
Audittext bleiben unverändert. Produkt-, Schema-, Migrations-, Ressourcen- und Golden-Dateien
werden nicht geändert.

Ergebnis: PR 316 wurde als `c81416c802a26458f31355365c7683425da3202b` nach `main`
gesquasht. Der exakte Main-Workflow 33742146087 ist grün; Quality, Instrumentierung, Upgrade,
Packaging und Publish wurden für die reine Dokumentationsphase vertragsgemäß übersprungen.
Phase 0 ist damit implementiert und Phase 1 freigegeben.
