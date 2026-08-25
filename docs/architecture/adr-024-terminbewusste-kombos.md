# ADR-024: Terminbewusste Kombos und echte Verpflichtungen

- Status: angenommen
- Datum: 2026-08-25
- Owner: Combo- und Today-Domäne
- Source of Truth: `ComboPolicy`, `ComboObligation`, `ApplyComboDecay` und Room-Schema 18

## Kontext

Die bisherige Komboabrechnung zog für jeden vollständig vergangenen Kalendertag zwei Punkte ab.
Sie kannte weder Aufgabenintervalle noch Wochentage, Schrittfrequenzen oder geplante freie Tage.
Eine täglich fällige Aufgabe konnte dadurch wachsen, während korrekt ausgeführte Intervall- oder
Mo/Mi/Fr-Aufgaben zwischen ihren echten Terminen stärker verfielen als sie Punkte verdienten.

Carry-forward ist außerdem keine neue Planung. Es hält unerledigte Arbeit sichtbar, darf aber
weder einen zusätzlichen Termin noch eine zusätzliche Strafe erfinden.

## Entscheidung

Schema 18 führt `combo_obligations` ein. Eine Verpflichtung bezeichnet genau einen laut
Aufgaben-, Schritt- und Slot-Zeitplan echten Termin. Sie bleibt offen, bis positive Aktivität oder
ein expliziter Nullabschluss sie löst. Carry-forward übernimmt dieselbe Verpflichtung.

`combo_decay_events` hält für Owner und logisches Kalenderdatum fest, dass eine mögliche
Verfallsbuchung ausgewertet wurde. Dadurch ist der Tageswechsel auch dann idempotent, wenn ein
Kombostand bereits null war und deshalb keine Ledgerbuchung entstehen konnte. Wenn Punkte
abgezogen werden, enthält `reward_bookings` eine unveränderliche `COMBO_DECAY`-Buchung mit dem
tatsächlich angewandten Delta.

Die Richtlinie besteht aus drei unabhängigen Werten:

- ganzzahliger Gewinn, Standard `2`;
- ganzzahliger Verfall, Standard `1`;
- Trigger `MISSED_OCCURRENCE`, `DAILY_OVERDUE` oder `NEXT_SCHEDULED_OCCURRENCE`, wobei
  `DAILY_OVERDUE` der Standard ist.

Gewinn und Verfall dürfen null sein. Einstellungen wirken nur auf neue Buchungen; vorhandene
Ledgerzeilen und Kombopunkte werden nicht neu berechnet. Pünktliche und nachgeholte positive
Aktivität erhalten denselben Gewinn. Der Verfall ist die einzige Komboauswirkung des
Versäumnisses.

Mehrere offene Verpflichtungen desselben Owners können am selben Datum höchstens eine
Verfallsauswertung erzeugen. Aufgaben verwenden standardmäßig `COLLAPSE`; `ACCUMULATE` bewahrt
jeden echten Termin als Queue. Deshalb entfernt Schema 18 die frühere Triggerregel, die mehrere
offene Vorkommen eines Task-/Slot-Paars grundsätzlich verbot.

Gewinn, Verfall und Trigger werden in den App-Optionen gespeichert und über `ComboPolicySource`
in alle Reward- und Verfallspfade injiziert. Der Task-Editor bietet die Rückstandsmodi
„Zusammenfassen“ (`COLLAPSE`) und „Einzeln nachholen“ (`ACCUMULATE`) an. Im zweiten Modus wird
jeder fällige Termin mit seinem damaligen Schrittplan materialisiert. Today zeigt je Task und
Slot nur den ältesten offenen Termin sowie `noch N offen`; nach dessen Abschluss rückt der
nächste Termin nach. Mehrere tägliche Slots bleiben damit getrennte Queues.

Der Today-Vorbereitungspfad materialisiert zunächst echte Termine und Verpflichtungen und wertet
anschließend Verfall aus. Damit werden weder ein gerade erst fälliger Termin noch ein neutraler
Carry-Tag vorzeitig bestraft.

## Migration und Kompatibilität

Vorhandene `combo_progress.points` werden bytegetreu übernommen. Offene Vorkommen werden beim
Upgrade als aktuelle Verpflichtungen übernommen; erledigte Schritte werden dabei als gelöst
markiert. Bereits abgeschlossene historische Tage werden nicht rückwirkend in Verpflichtungen
oder Verfallsereignisse umgewandelt. `settledThroughOn` bleibt vorerst ein gelesenes Legacy-Feld,
steuert aber keine Kalenderabrechnung mehr.

## Konsequenzen

Die Fachlogik kann geplante freie Tage sicher von überfälliger Arbeit unterscheiden. Exakter
Retry, spätere frei wählbare Richtlinien und eine sequenzielle Rückstandsqueue sind möglich, ohne
historische Rewards umzuschreiben. Dafür kommen zwei persistente Projektionen und explizite
Auflösung/Gegenöffnung von Verpflichtungen bei Completion und Undo hinzu.

Ein späteres Lernsystem darf tatsächliche Wiederholungen, Sets, Dauer oder Gewicht auswerten,
aber weder Planung noch Aufgaben automatisch verändern. Analyse, Empfehlungen und automatische
Anpassung sind ein eigener Folgeschnitt und nicht Teil dieser Entscheidung.

## Lokale Nachweise

- Fachmodell: `core-domain/src/main/java/de/thonktank/autosecretary/domain/model/ComboPolicy.java`
- Terminbewertung: `core-domain/src/main/java/de/thonktank/autosecretary/domain/usecase/ApplyComboDecay.java`
- Persistenzmigration: `app/src/main/java/de/thonktank/autosecretary/data/local/DatabaseMigrations.java`
- Akzeptanzfälle: `app/src/test/java/de/thonktank/autosecretary/ScheduleAwareComboDecayTest.java`
- Benutzerrichtlinie: `app/src/main/java/de/thonktank/autosecretary/data/preferences/UiPreferences.java`
