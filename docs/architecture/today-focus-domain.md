# Today-/Fokus-Refactor: fachlicher Wiederholungsfortschritt

Stand: Phase 1 des schrittweisen Today-/Fokus-Refactors.

## Invarianten

`RepetitionProgress` ist die einzige fachliche Quelle für den Fortschritt einfacher
Wiederholungs- und Satzschritte. Der Typ garantiert:

- eine positive Zahl geplanter Slots;
- höchstens einen Ist-Wert je geplantem Slot;
- nichtnegative gespeicherte Ist-Werte;
- neue und korrigierte Werte ausschließlich im Bereich `0…999`;
- einen aus den Ist-Werten abgeleiteten nächsten offenen Slot;
- einen expliziten Abschlusszustand statt frei kombinierbarer Listen- und Done-Zustände.

Die Abschlusszustände sind:

- `IN_PROGRESS`: Es fehlen Ist-Werte und der Schritt ist offen.
- `RESULTS_COMPLETE`: Jeder geplante Slot besitzt einen Ist-Wert.
- `COMPLETED_WITHOUT_RESULTS`: Ein expliziter Sammelbefehl wie „Rest erledigen“ hat den Schritt
  trotz fehlender Werte abgeschlossen.

`OccurrenceStep.done` bleibt wegen des bestehenden Room-Schemas vorerst als persistierte
Projektion erhalten. Bei Wiederholungsschritten wird der Wert beim Aufbau des Domainobjekts
aus `RepetitionProgress` kanonisiert. Ein historischer Datensatz mit vollständigen Ergebnissen
und `done = false` wird deshalb als `RESULTS_COMPLETE` gelesen. Ein historischer Datensatz mit
`done = true` und fehlenden Ergebnissen wird ausdrücklich als `COMPLETED_WITHOUT_RESULTS`
gelesen.

Beim Wiederöffnen eines mit Ergebnissen abgeschlossenen Schritts wird der letzte Ist-Wert
entfernt. Dadurch kann kein offener Schritt mit bereits vollständig besetzten Slots entstehen.
Ein ohne vollständige Ergebnisse abgeschlossener Schritt behält beim Wiederöffnen seine
vorhandenen Teilwerte.

## Rückwärtskompatibilität

Phase 1 ändert weder Room-Version noch Tabellen oder gespeicherte Spalten. Die bestehende
Komma-Repräsentation wird am Data-Layer weiterhin gelesen und geschrieben. Nichtnegative
Legacy-Werte oberhalb von 999 bleiben vollständig lesbar, darstellbar und erneut
serialisierbar. Sie dürfen lediglich nicht als neuer oder korrigierter Wert durch die
fachliche Schreibgrenze gelangen.

Die Normalisierung der Persistenz ist bewusst erst für Phase 7 vorgesehen.

## Fachliche Befehle

Die produktiven Befehle heißen jetzt nach ihrem tatsächlichen Verhalten:

- `RecordRepetitionResult` erfasst den nächsten Ist-Wert für Satz- und Einzelwiederholungen.
- `CorrectRepetitionResult` korrigiert einen vorhandenen Wert, ohne Rewardbuchungen zu ändern.

Die nicht mehr aus einem Produktions-UI-Pfad erreichbaren Altbefehle `ConfirmSet`,
`EditStepProgress`, `FinishExercise` und `ReopenExercise` wurden entfernt. Vorzeitiges
Abschließen bleibt über den sichtbaren Befehl „Rest erledigen“ erhalten und ist nun als
`COMPLETED_WITHOUT_RESULTS` modelliert.

## Verbleibende Schulden

- `done` ist bis zur späteren Schemamigration eine redundante Persistenzprojektion.
- Ist-Werte werden bis Phase 7 weiterhin als ein String statt als adressierbare Datensätze
  gespeichert.
- Das aktuelle Präsentationsmodell kopiert die Ist-Werte noch in ein eigenes UI-Modell; diese
  Verbrauchertrennung erfolgt in Phase 2.

