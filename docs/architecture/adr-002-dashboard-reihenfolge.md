# ADR-002: Dashboard-Reihenfolge und „später“

- Status: angenommen
- Datum: 2026-08-15

## Kontext

Der Heute-Screen bildet eine ruhige Folge aus Aufgaben und Kalenderterminen. Aufgaben
besitzen derzeit grobe Tageszeit-Slots, Kalendertermine dagegen konkrete Uhrzeiten.

## Entscheidung

- Die persistierte Aufgabenreihenfolge ist stabil und wird nie aus Nutzungsverhalten
  automatisch überschrieben.
- Slots bilden geordnete Tagesbereiche: Morgen, Mittag, Abend, Später.
- Innerhalb eines Slots entscheidet die explizite Aufgabenreihenfolge.
- Der erste offene Eintrag ist das Fokusblatt.
- „später“ tauscht das Fokusblatt mit der unmittelbar folgenden offenen Aufgabe. Erledigte
  Blätter und Kalendertermine sind keine Tauschpartner.
- Kalendertermine werden in der Präsentationsschicht anhand ihrer Startzeit zwischen die
  Aufgabenblöcke einsortiert. Sie ändern niemals die persistierte Aufgabenreihenfolge.
- Heute abgeschlossene Aufgaben bleiben an ihrer persistierten Position sichtbar und
  verschwinden erst am Tageswechsel.
- Der Screen rendert nach dem Fokus höchstens drei weitere Blöcke und fasst den Rest als
  „N weitere“ zusammen.

## Konsequenzen

Die Domain speichert nur die Aufgabenreihenfolge. Die gemischte Timeline ist ein
Presentation-Modell. Ein reiner Dashboard-Lesezugriff verändert weder Reihenfolge noch
Occurrences.
