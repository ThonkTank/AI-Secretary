# ADR-002: Dashboard-Reihenfolge und „später“

- Status: angenommen
- Datum: 2026-08-15

## Kontext

Der Heute-Screen bildet eine ruhige Folge aus Aufgaben und Kalenderterminen. Aufgaben
besitzen derzeit grobe Tageszeit-Slots, Kalendertermine dagegen konkrete Uhrzeiten.

## Entscheidung

- Die persistierte globale Aufgabenreihenfolge ist stabil und wird nie aus
  Nutzungsverhalten automatisch überschrieben.
- Slots bilden die Einfügeposition für neue oder ausdrücklich verschobene Aufgaben:
  Morgen, Mittag, Abend, Später. Danach entscheidet ausschließlich die globale Reihenfolge.
- Positionen werden als fortlaufende Werte mit einem Abstand von 1024 normalisiert. Der
  Abstand ist eine dokumentierte Persistenzstrategie und trägt keine Slot-Semantik.
- Der erste offene Eintrag ist das Fokusblatt.
- „später“ tauscht das Fokusblatt in dieser globalen Reihenfolge mit der unmittelbar
  folgenden offenen Aufgabe – auch über Slotgrenzen hinweg. Erledigte Blätter und
  Kalendertermine sind keine Tauschpartner.
- Kalendertermine werden in der Präsentationsschicht anhand ihrer Startzeit zwischen die
  Aufgabenblöcke einsortiert. Sie ändern niemals die persistierte Aufgabenreihenfolge.
- Heute abgeschlossene Aufgaben werden aus Fokus und gemischter Tagesfolge entfernt. Unterhalb
  des Tagesinhalts bleiben sie in einer standardmäßig eingeklappten, kompakten
  „Heute erledigt“-Historie mit exaktem Undo erreichbar.
- Der Screen rendert nach dem Fokus höchstens drei weitere Blöcke und fasst den Rest als
  „N weitere“ zusammen.

## Konsequenzen

Die Domain speichert nur die Aufgabenreihenfolge. Die gemischte Timeline ist ein
Presentation-Modell. Ein reiner Dashboard-Lesezugriff verändert weder Reihenfolge noch
Occurrences.
