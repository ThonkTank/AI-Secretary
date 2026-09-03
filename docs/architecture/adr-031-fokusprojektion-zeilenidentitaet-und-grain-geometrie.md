# ADR-031: Fokusprojektion, Zeilenidentität und Grain-Geometrie

- Status: angenommen
- Datum: 2026-09-03

## Kontext

Das Heute-Aufgabenblatt leitete den aktiven Schritt aus Listenposition und `FocusStepStatus` ab.
Eine zusätzliche lokale Auswahl hätte dieselbe Entscheidung in State, Eingabereducer und View
erneut berechnen müssen. Gleichzeitig verwendete die Grain-Maskierung ganze View-Rechtecke und
die positionsbasierte Wiederverwendung komplexer Schrittzeilen konnte veraltete Unterzustände
zwischen unterschiedlichen Schrittidentitäten sichtbar machen.

## Entscheidung

`:today-core` besitzt die einzige Fokusprojektion. Kanonische Reihenfolge, Reorder-Preview und
lokale Auswahl bleiben getrennt. Eine Android-freie Projektion liefert fertig geordnete Zeilen,
deren Modus und einzige ausführbare Aktion bereits feststehen. Android-Views rendern diese
Projektion und bestimmen weder Auswahl noch Aktionsart erneut.

Schrittzeilen werden ausschließlich innerhalb derselben Occurrence und stabilen Schritt-ID
wiederverwendet. Jede Bindung beschreibt alle sichtbaren und leeren Zustände ihrer Teilbereiche.
Materialisierte Schritte übernehmen ihre Inhalte atomar aus einer Vorlage, einem getragenen
Occurrence-Schritt oder einem Flow-Snapshot; Persistenz-Rehydration bleibt davon getrennt.

Textverdeckung für Jahresringe ist ein Geometrievertrag. `GrainSpec` erhält Occlusion-Quellen,
die nach dem finalen Layout sichtbare lokale Rechtecke liefern. Die Blattoberfläche kennt keine
Text-Widget-Sonderfälle. Das XP-Gefäß leitet Wasserfläche und Wasserlinie aus derselben
Kreisgeometrie und demselben Clip ab.

## Konsequenzen

`FocusStepStatus.ACTIVE/AVAILABLE`, doppelte aktive/kompakte Aktionsfelder, lokale
Auswahlalgorithmen in Views und unvollständige Reset-Listen entfallen. Temporäres Vorziehen
verändert weder Domainzustand noch gespeicherte Reihenfolge. Offene Snapshots werden weder anhand
von Titel oder Position korrigiert noch aus später geänderten Vorlagen neu aufgebaut.

Die vollständige Umsetzung und die phasenweisen Gates stehen in der
[verbindlichen Roadmap](today-focus-clean-roadmap.md). Bis alle Produktphasen gemergt sind,
beschreibt diese ADR den Zielvertrag, nicht den bereits vollständig erreichten Istzustand.

