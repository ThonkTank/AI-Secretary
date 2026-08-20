# ADR-017: Aufgabenverwaltung, Zeitplatzierungen und Schritttransfers

- Status: angenommen
- Datum: 2026-08-20

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
  sichtbaren Aufgaben. Änderungen erfolgen über natives Long-press-Drag-and-drop.

Datenbankversion 12 führt `task_schedule_entries` ein. Jede Zeile verbindet eine stabile ID und
eine Aufgabe mit genau einer Tageszeit und einer innerhalb dieses Slots unabhängigen
`displayOrder`. Die Migration erzeugt für Einmalaufgaben eine Zeile aus ihrem Slot und für
wiederkehrende Aufgaben eine Zeile je gesetztem Bit ihrer Zeitmaske; fehlt eine gültige Maske,
wird der bisherige Slot verwendet.

Beim Verschieben einer Platzierung werden die betroffenen Slotreihenfolgen in 1024er-Schritten
normalisiert. Ein passendes offenes Heute-Vorkommen wechselt Slot und Rang sofort. Abgeschlossene
oder geerntete Vorkommen bleiben historische Snapshots. Der Task-Slot und die bisherige Zeitmaske
bleiben als kompatible Projektion der normalisierten Platzierungen bestehen.

Ein verschobener oder getauschter Schritt behält seine Template-ID, Definition, Kombo und
Position relativ zum Ziel. Für offene Quell- und Zielvorkommen derselben Tageszeit wird der
heutige Schrittzustand einschließlich zugehöriger Reward-Buchungen mitverschoben. Existiert kein
passendes Zielvorkommen, ändert sich nur die Definition für künftige Materialisierungen; der
unzuordenbare heutige Snapshot bleibt bestehen. Geerntete und abgeschlossene Historie wird nie
verändert.

## Konsequenzen

Heute bleibt der Ausführungsbereich, Alles der Verwaltungsbereich. Mehrfach täglich geplante
Aufgaben können unabhängig sortiert werden, ohne Duplikate in der Definitionsansicht. Schema-,
Domain- und Robolectric-Tests sichern Migration, Projektionen, Drag-Ziele sowie Reward- und
Kombo-Erhalt bei Schritttransfers ab.

ADR-002 gilt weiterhin für die Darstellung und die Aktion „später“. Soweit dort eine einzige
globale Aufgabenreihenfolge beschrieben ist, ersetzt diese Entscheidung sie für neu
materialisierte zeitgebundene Platzierungen durch die normalisierte Reihenfolge pro Slot.
