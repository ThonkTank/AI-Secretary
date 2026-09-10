# Ausführungsprotokoll: Kachel-Editor und parallele Abläufe

Dieses Protokoll wird append-only ergänzt. Verbindlich ist die
[Roadmap](flow-tiles-roadmap.md), fachlich [ADR-036](adr-036-kachel-editor-und-parallele-ablaeufe.md).

## 2026-09-10 — D-Freigabe und C-Beginn

- Nutzerfreigabe: Kacheln bestimmen Reihenfolge; Dialog enthält Name, Wartezeit danach
  und direkt „Beim Start nachfragen“. Kein Zeitoptionen-Dropdown, keine Ablaufbestätigung.
  Kapazitäten folgen auf dem zweiten Blatt. Kein erneutes Design-Freigabegate erforderlich.
- Ausgangspunkt `80476639`, Schema 24, isolierter Worktree
  `/tmp/autosecretary-flow-tiles-contract`, Branch `codex/flow-tiles-contract`.
- Vor Vertragsarbeit Fast-forward auf geprüftes Remote-main
  `de6fc4037a974dc6482e91e841befca01c964c64`: PR #353/#354 bleiben erhalten.
- Vertragsphase enthält keine Android-Produktänderung und veröffentlicht kein Paket.
- Mockup-Prüfungen werden separat von Android-Nachweisen geführt. Android-Referenzzustände,
  komplette Laufzeit-/Migrationsprüfung und Geräteabnahme sind noch offen.

### C — lokale Vertragsprüfung

- Mockup: Name/Zeit/Einheiten, direkt sichtbare Checkbox, kein Dropdown/Bestätigungsfeld,
  Cancel/Undo, Anlegen und Kapazitätszuordnung bestanden. Kachelgeometrie, Drag/Undo,
  320/360/736 px und große Schrift separat bestanden.
- CI-Vertragstests: 27 bestanden; Release-Vertragstests: 31 bestanden.
- `git diff --check` bestanden. Android-Gesamtsuite ist nicht als ausgeführt behauptet:
  diese Phase ändert ausschließlich Dokumentation und Referenz, keinen Produktcode.
- Mockup-SHA-256 steht in der Referenz. PR-/main-Nachweise folgen nach Remoteprüfung.
