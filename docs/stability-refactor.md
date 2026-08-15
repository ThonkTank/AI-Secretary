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
für ältere Characterization-Tests bestehen, enthält aber keine Transaktions- oder
Schedulinglogik mehr. Produktionscode verwendet sie nicht mehr.

## Abhängigkeiten und Systemgrenzen

`AutoSecretaryApplication` besitzt genau einen manuellen `AppContainer`. Er verdrahtet
Datenbank, Repository, Use Cases, Uhr, Zeitzone, ID-Erzeugung, Kalenderzugriff,
UI-Präferenzen und Presenter. Activity, Widget und Broadcast-Aktionen verwenden dieselben
Instanzen; Tests können die fachlichen Bausteine weiterhin direkt mit Fakes und einer
In-Memory-Datenbank erstellen und müssen keinen globalen Datenbank-Singleton zurücksetzen.

Datum, Uhrzeit und Zeitzone werden über `Clock` und `ZoneIdProvider` injiziert. Direkte
SharedPreferences-Zugriffe sind auf `UiPreferences` und `LegacyStateCleaner` begrenzt.
Die einmalige Bereinigung des alten Prototyp-Speichers läuft vor der normalen
Datenbankerzeugung und ist von `DatabaseFactory` getrennt. Fehler an Android-Systemgrenzen
werden über `AppLogger` strukturiert protokolliert.

## Einheitlicher Präsentationszustand

`TaskViewModel` veröffentlicht einen unveränderlichen `DashboardUiState`. Darin liegen das
typisierte Navigationsziel, das bereits mit Kalenderterminen sortierte `DashboardUiModel`,
der Kalender- und Berechtigungszustand, die aktuelle Palette, Ladeinformationen, laufende
Aktionsschlüssel und der offene Editor. Die Activity führt Aufgaben und Termine daher nicht
mehr selbst zusammen und sortiert keine fachlichen Einträge.

Navigation und Editorziel werden mit `SavedStateHandle` wiederhergestellt. Laufende
Kommandos werden synchron über stabile Aktionsschlüssel gesperrt, sodass Doppelklicks nicht
mehrere identische Schreibvorgänge anstellen. Fehler, Bestätigungsdialoge,
Berechtigungsanfragen und externe Navigation sind konsumierbare `UiEvent`s statt
wiederholender `LiveData<String>`-Werte.
