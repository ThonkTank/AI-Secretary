# Stabilitäts-Refactor

Der Prototyp-Speicher wurde durch eine Room-Datenbank ersetzt. Aufgaben, Schrittvorlagen, offene Aufgabeninstanzen und XP werden in atomaren Datenbanktransaktionen gespeichert.

## Bewusster Neustart

Beim ersten Start dieser Version werden die Testdaten der ersten App-Version gelöscht. Alte Benachrichtigungsalarme werden dabei abbestellt. Neue Aufgaben bleiben bei normalen, gleich signierten APK-Updates erhalten.

## Fälligkeitsregeln

- Pro wiederkehrender Aufgabe kann nur eine offene Instanz existieren; verpasste Termine stapeln sich nicht.
- „Alle N Tage“ rechnet ab der tatsächlichen Erledigung weiter.
- Wochentage bleiben an ihren gewählten Kalendertagen verankert.
- Eine verspätete Erledigung startet die aktuelle Routine-Serie neu, senkt aber nie XP oder Routine-Level.

## Datenbankschema 3

Schema 3 trennt persistierte Fachcodes von deutschen UI-Texten. Tageszeiten werden als
`MORNING`, `MIDDAY`, `EVENING` und `LATER` gespeichert; die Migration 2→3 überführt die
bisherigen Werte `Morgen`, `Mittag`, `Abend` und `Später`. Unbekannte alte Slotwerte werden
defensiv als `LATER` übernommen. Zusätzliche Indizes beschleunigen aktive Dashboard- und
Heute-Abfragen.

Room-Entities werden am Rand des Data-Layers in immutable Domain-Modelle übersetzt. Die
Fachlogik arbeitet mit `TaskSlot`, `Recurrence`, `OccurrenceState`, `TaskId` und
`RoutineProgress`, nicht mit frei vergleichbaren Strings.

## Explizite Schreib- und Lesevorgänge

Das Laden des Dashboards ist rein lesend. Fällige Occurrences entstehen ausschließlich über
den expliziten Use Case `MaterializeDueOccurrences`, den die App vor einem fachlichen
Refresh ausführt. Dashboard-Schritte und fällige Schrittvorlagen werden gebündelt geladen;
die Anzahl der Leseabfragen wächst daher nicht mit der Zahl der Aufgaben.

Erstellen, Bearbeiten, Verschieben, Zurückstellen, Abschließen und Löschen sind getrennte
Use Cases. Die bisherige Klasse `TaskService` bleibt vorübergehend als delegierende Fassade
für Activity und Widget bestehen, enthält aber keine Transaktions- oder Schedulinglogik
mehr.
