# ADR-003: Occurrences und fortlaufende Vorhaben

- Status: angenommen
- Datum: 2026-08-15

## Kontext

Aufgaben können einmalig, wiederkehrend oder fortlaufend sein. Verpasste Termine sollen
nicht zu einem überwältigenden Rückstand anwachsen.

## Entscheidung

- Pro aktiver Aufgabe und Tageszeit darf höchstens eine offene Occurrence existieren.
- Beim Tageswechsel wird eine offene Occurrence als `MISSED` historisiert. Nur unerledigte
  Schritte werden in eine heutige Occurrence übernommen; ungeerntete Gefäß-XP verfallen.
- Verpasste Schritte werden am nächsten Kalendertag nachgeplant. Regulär fällige Schritte
  werden zusätzlich gemäß ihrem festen Rhythmus ergänzt und je Occurrence dedupliziert.
- Tägliche, Intervall- und Wochentagswiederholungen werden vom geplanten Termin aus
  weitergerechnet, unabhängig vom tatsächlichen Abschluss. `Task.nextDueOn` ist dabei
  ausschließlich der persistierte Planungscursor; Completion und Undo dürfen ihn nicht
  zurücksetzen.
- Wochentagswiederholungen bleiben an den ausgewählten Kalendertagen verankert.
- Abgeschlossene Occurrences bleiben als Historie bestehen.
- Das Dashboard zeigt pro Task und Tageszeit höchstens einen offenen Eintrag. Eine offene
  Occurrence hat Vorrang vor einer heute abgeschlossenen Occurrence desselben Tasks.
- Ein einmaliger Task wird nach Abschluss archiviert.
- Ein fortlaufendes Vorhaben bleibt offen, bis seine ausdrücklich benannte Bedingung erfüllt
  wird. Der Abschluss der Bedingung archiviert das Vorhaben und vergibt höchstens einmal XP.
- Das Erzeugen fälliger Occurrences ist ein expliziter Schreibvorgang. Eine reine
  Dashboard-Abfrage darf keine Occurrence materialisieren.
- Die aktive Domäne unterscheidet `SCHEDULED` und `CONDITION` über `OccurrenceKind`.
  Condition-Abschlüsse werden im Reward-Ledger zusätzlich als `CONDITION_COMPLETION`
  klassifiziert. Der Data-Layer erkennt bestehende Schema-7-IDs mit `condition:` einmalig als
  Kompatibilitätsadapter; Fachlogik und Rewardberechnung prüfen kein Stringpräfix mehr.

## Konsequenzen

Die Datenbank muss die Ein-Offene-Occurrence-Regel durch Transaktionen und möglichst durch
eine überprüfbare Invariante schützen. Refresh und Query werden als getrennte Use Cases
modelliert.

Abschluss, Teilernte, Undo und Condition-Close laufen über einen transaktionalen
`CompletionService`. Eine Ernte darf vorhandene Gefäß-XP auch bei offenen Schritten
gutgeschreiben; die offenen Schritte werden danach als verpasst nachgeplant.
Ein reiner Zustandsautomat führt Occurrence-/Schrittübergänge aus, ein reiner
`RewardCalculator` berechnet Rewards, und ein `ScheduleProjector` projiziert Archivstatus und
Folgetermine aus einem typisierten Occurrence-Snapshot. Die Projektion verändert den
Planungscursor nicht. Dafür lädt das Repository gezielt nur das früheste offene und das letzte
abgeschlossene Vorkommen statt der vollständigen Historie.
