# Ablaufeditor — freigegebener Kachelentwurf

Owner-Freigabe: 2026-09-10, nach den ausdrücklich verlangten Vereinfachungen.
Vertrag: [ADR-036](../../architecture/adr-036-kachel-editor-und-parallele-ablaeufe.md).
Diese Referenz ersetzt keine Referenz des normalen Aufgabeneditors.

`approved-tiles.html` ist der freigegebene interaktive Entwurf, kein Produktcode.
SHA-256: `07fbf886c255ecac02c8d78556496a5e6b2a7f0f131666480ab35707b5cdda2d`.
Die Wäsche- und Abendessen-Beispiele sind Prüffälle, keine Vorlagenfunktion.
Das Mockup bildet Erscheinungsbild und grundlegende Interaktionen ab; es ist ausdrücklich
kein Nachweis einer Android-Datenmigration, Prozesswiederherstellung oder Transaktionssicherheit.

## Verbindliche sichtbare Zustände

- Wäsche: Bunt, Weiß, Handtuch, Bett nebeneinander; Aufhängen, Abhängen, Wegräumen
  jeweils darunter über die gemeinsame Breite. Nur Kacheln, keine Graph-Verzierungen.
- Abendessen: Vorbereiten; Teig kneten und Dip rühren parallel; Backen nur im linken
  Zweig; Servieren als gemeinsame Folgekachel über beide Zweige.
- Schrittdialog: Name; Wartezeit danach als Wert/Einheit; direkt sichtbare Checkbox
  „Beim Start nachfragen“; Abbrechen/Speichern. Keine weiteren aufklappbaren Zeitoptionen.
- Kapazitäten auf Blatt 2: Name/Gesamtmenge und explizite Zuordnungen mit Menge,
  Reservierungsbeginn und Freigabepunkt. Fertig speichert ohne dritte Seite.
- Ablaufblatt: nur verfügbare Aktionen, eindeutige Herkunft, kleine Warteübersicht,
  Später und Tau an der abschließenden Aktion statt oben rechts.

Browserprüfungen: Name/Zeit/Einheiten/Checkbox, Erstellen, Cancel, Undo,
Kapazität anlegen/zuweisen; Tile-Spans und Drag/Undo; 320/360/736 px, 160 % Schrift,
helle/dunkle Darstellung. Tests liegen neben dem ursprünglichen Gesprächsentwurf.
Produktabnahme ergänzt daraus Compose-Referenzzustände und Tests gemäß Roadmap T01–T10.
Die generische Join-/Zweigauswahl und alle zugänglichen Alternativen sind nach ADR-036
in Android vollständig zu prüfen; Browser-Smoke-Tests sind keine vollständige Abnahme.
