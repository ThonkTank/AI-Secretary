# ADR-016: Normalisierte Persistenz von Wiederholungsergebnissen

- Status: angenommen
- Datum: 2026-08-20

## Kontext

Bis Datenbankversion 7 lagen alle Ist-Wiederholungen eines Occurrence-Schritts als
kommagetrennter Text in `occurrence_steps.actualRepetitions`. Lesen, Anhängen und Korrigieren
erforderten vollständiges Parsen und Serialisieren der Liste. Slotidentität, Wertebereich und
Referenzintegrität waren nur Konventionen im Mapper. Eine Korrektur eines Satzes schrieb stets
die vollständige Liste neu.

Historische Daten können nichtnegative Werte oberhalb des heutigen Eingabelimits 999 enthalten.
Diese Werte sind gültige Historie und dürfen bei einer Normalisierung nicht verloren gehen.

## Entscheidung

Datenbankversion 8 führt `repetition_results` ein:

| Spalte | Bedeutung |
|---|---|
| `stepId` | Fremdschlüssel auf `occurrence_steps.id`, Löschen kaskadiert |
| `slotIndex` | nullbasierte, stabile Position des Ergebnisses |
| `actualRepetitions` | historischer oder neu erfasster nichtnegativer Ist-Wert |

Der zusammengesetzte Primärschlüssel `(stepId, slotIndex)` verhindert doppelte Ergebnisse pro
Slot. Ein zusätzlicher FK-Index unterstützt schrittweise Reads und Cascades.

`MIGRATION_7_8` liest den bisherigen Text in Reihenfolge und schreibt jeden Wert in eine eigene
Zeile. `0` und Werte über 999 werden unverändert übernommen. Die Migration verwendet idempotente
Upserts, sodass ein erneuter kontrollierter Lauf keine Duplikate erzeugt. Ist ein historischer
Text syntaktisch fehlerhaft oder enthält negative beziehungsweise nicht als Integer lesbare
Werte, wird für diesen Schritt keine partielle Projektion geschrieben. Stattdessen wird eine
Fehlermeldung mit Step-ID geloggt und der unveränderte Rohtext bleibt zur Diagnose erhalten.

Die Spalte `occurrence_steps.actualRepetitions` bleibt in Version 8 ausschließlich als explizites
Übergangsfeld bestehen. Das Entity nennt sie deshalb `legacyActualRepetitions`. Produktive Reads
ignorieren sie; produktive Inserts und Updates schreiben dort nur den leeren String. Eine spätere
Migration kann die Spalte entfernen, sobald ausreichend Releasegenerationen Version 8 passiert
haben.

`RoomTaskRepository` lädt Ergebniszeilen geordnet und baut daraus die Domainliste. Beim Schreiben
vergleicht es vorhandene und gewünschte Slots: neue oder veränderte Slots werden einzeln
upserted, nur entfernte Endslots werden gelöscht. Eine Korrektur schreibt somit genau die
betroffene Zeile. Domain- und Repository-API enthalten keine Komma-Repräsentation mehr; der alte
Codec wurde entfernt.

## Konsequenzen

Bestehende Datenbanken können über die vollständige Migrationskette bis Version 8 geöffnet
werden. Ein Instrumentationstest migriert eine reale exportierte v7-Datenbank und prüft `0`,
`999` und `1200`. Der Robolectric-Migrationstest prüft zusätzlich fehlerhafte Daten,
Wiederholbarkeit, Domain-Readback und per Schreib-Trigger die Ein-Zeilen-Korrektur.

Das weiterhin persistierte `OccurrenceStep.done` bleibt eine bewusste Projektion des fachlichen
Abschlusszustands. Die Legacy-Textspalte ist ebenfalls noch technische Schuld, wird aber nicht
mehr als zweite Wahrheit verwendet.
