# ADR-017: Aufgabenverwaltung, Zeitplatzierungen und Schritttransfers

- Status: angenommen
- Datum: 2026-08-21

## Kontext

Der Heute-Tab ist absichtlich auf das Abarbeiten bereits materialisierter Vorkommen optimiert.
Aufgaben zu finden, Definitionen zu pflegen oder die langfristige Reihenfolge zu ändern würde
diesen Fokus überladen. Außerdem kann eine wiederkehrende Aufgabe für mehrere Tageszeiten gelten;
ein einzelner Slot und eine einzelne globale Sortierzahl bilden diese Platzierungen nicht
unabhängig ab.

Schrittdefinitionen besitzen stabile IDs und Kombos. Beim Verschieben eines Schritts zwischen
Aufgaben dürfen weder diese Identität noch bereits geerntete Historie umgeschrieben werden.

## Entscheidung

Der Alles-Tab besitzt zwei Modi:

- Die Aufgabenansicht zeigt jede Definition einmal. Suche und Filter arbeiten auf Titel,
  Notizen und Schrittinhalten. Aktive Aufgaben können im bestehenden Vollbild-Editor bearbeitet
  und erweitert werden; archivierte Aufgaben sind nur lesbar und löschbar.
- Die Sortieransicht zeigt jede Zeitplatzierung einer aktiven Aufgabe als eigene Zeile in den
  Gruppen Morgen, Mittag, Abend und Später. Ein optionaler Wochentagsfilter beschränkt die
  sichtbaren Aufgaben. Änderungen erfolgen über Long-press-Drag-and-drop oder gleichwertige
  Accessibility-Aktionen.

Datenbankversion 12 führt `task_schedule_entries` ein. Jede Zeile verbindet eine stabile ID und
eine Aufgabe mit genau einer Tageszeit und einer innerhalb dieses Slots unabhängigen
`displayOrder`. Die Migration erzeugt für Einmalaufgaben eine Zeile aus ihrem Slot und für
wiederkehrende Aufgaben eine Zeile je gesetztem Bit ihrer Zeitmaske; fehlt eine gültige Maske,
wird der bisherige Slot verwendet.

Beim Verschieben einer Platzierung werden die betroffenen Slotreihenfolgen in 1024er-Schritten
normalisiert. Schema 13 entfernt Slot, Zeitmaske und die alte Definitionsreihenfolge aus `tasks`;
`catalogOrder` hält nur noch die Reihenfolge der einmaligen Definitionsliste. Damit ist
`task_schedule_entries` die einzige persistente Zeitplanungswahrheit.

Ein Reorder liest und normalisiert ausschließlich Quell- und Zielslot über den schmalen
`TaskScheduleRepository`-Port. Ein passendes offenes Heute-Vorkommen wechselt Slot und Rang
sofort. Abgeschlossene oder geerntete Vorkommen bleiben historische Snapshots. `CreateTask`,
`UpdateTask`, Today-Verschieben und Alles-Sortierung delegieren auf `TaskScheduleService`; ein
zweiter Legacy-Mutationspfad existiert nicht. Create und Update erhalten Definitionen und
Zeitplanung als getrennte Ports; Schedule- und Step-Commands kennen das breitere
Today-/Ausführungsrepository nicht. Erst der Composition Root bündelt die Ports im
`ApplicationTaskRepository` für die konkrete Room-Implementierung.

Ein verschobener oder getauschter Schritt behält seine Template-ID, Definition, Kombo und
Position relativ zum Ziel. `MoveTaskStep` und `SwapTaskSteps` hängen nur vom
`StepOrganizationRepository`-Port ab. Für offene Quell- und Zielvorkommen derselben Tageszeit
wird der heutige Schrittzustand samt aktueller Reward-Zuordnung mitverschoben. Existiert kein
passendes Zielvorkommen, ändert sich nur die Definition für künftige Materialisierungen; der
unzuordenbare heutige Snapshot bleibt bestehen. Geerntete und abgeschlossene Historie wird nie
verändert.

## Konsequenzen

Heute bleibt der Ausführungsbereich unter `presentation.today`, Alles der Verwaltungsbereich
unter `presentation.alltasks`; die Fachoperationen liegen in `domain.schedule` und
`domain.steps`, Room in `data.local`. Architekturtests verbieten Android- und UI-Abhängigkeiten
in der Domain sowie Management-State im Today-Slice. Die Alles-Liste ist als flaches,
virtualisiertes `RecyclerView`-Zeilenmodell mit stabilen IDs umgesetzt.

Der frühere breite In-Memory-Fake ist nur noch ein Ende-zu-Ende-Store für Ausführungs- und
Abnahmeszenarien. Schedule- und Step-Porttests besitzen kleine, ablehnende Testdoubles, die
globale beziehungsweise fachfremde Zugriffe sofort sichtbar machen.

Mehrfach täglich geplante Aufgaben können unabhängig sortiert werden, ohne Duplikate in der
Definitionsansicht. Schema-, Domain-, Robolectric- und Instrumentierungstests sichern Migration,
Projektionen, Drag-/Accessibility-Ziele sowie Reward- und Kombo-Erhalt bei Schritttransfers ab.

ADR-002 gilt weiterhin für die Darstellung und die Aktion „später“. Soweit dort eine einzige
globale Aufgabenreihenfolge beschrieben ist, ersetzt diese Entscheidung sie für neu
materialisierte zeitgebundene Platzierungen durch die normalisierte Reihenfolge pro Slot.
