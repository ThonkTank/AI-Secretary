# ADR-011: Verbraucherspezifische Today-Präsentationsmodelle

- Status: angenommen
- Datum: 2026-08-20

## Kontext

Fokuskarte, Timeline und Homescreen-Widget verwendeten bisher dasselbe umfangreiche
`TaskStepUiModel`. Es enthielt zugleich den fertig kombinierten Untertitel sowie Mengen- und
Notizbestandteile. Damit konnten diese Werte widersprüchlich werden. Das Widget war außerdem
von der vollständigen Today-Projektion abhängig, obwohl es nur einen kleinen, anders
formatierten Ausschnitt rendert.

## Entscheidung

Jeder Verbraucher besitzt eine eigene unveränderliche Projektion:

- `FocusStepUiModel` enthält die getrennten Fokuswerte für Menge, Notiz,
  Wiederholungsfortschritt und Rewarddarstellung, aber keinen kombinierten Untertitel.
- `TimelineTaskUiModel` und `TimelineStepUiModel` enthalten nur Blatt-, Aktions- und
  Fortschrittsdaten der Timeline. Die Timeline bindet keine Fokus-Schrittmodelle.
- `WidgetDashboardUiModel`, `WidgetTaskUiModel` und `WidgetStepUiModel` werden durch
  `WidgetDashboardMapper` unmittelbar aus dem Domain-`Dashboard` erzeugt. Sie kennen weder
  `TodayUiModel` noch `TaskSnapshot` oder Fokusmodelle.

Lokalisierte Mengen- und Notizregeln bleiben im gemeinsamen `StepTextFormatter`. Der Formatter
teilt Regeln, nicht fertige Modelle. Consumer-Modelle mit alternativen Zuständen verwenden
benannte Factory-Methoden; private Konstruktoren und defensive Listenkopien verhindern
unvollständige oder nachträglich veränderbare Instanzen. `FocusStepUiModel.of` weist zusätzlich
eine Wiederholungsprojektion ohne Mengenlabel und einen offenen Schritt mit bereits vollständig
belegten Ergebnisslots zurück. Ein als „Rest erledigt“ abgeschlossener Schritt darf weiterhin
bewusst nur Teilergebnisse enthalten.

Der `DashboardPresenter` stellt dafür neben der Today-Projektion das Domain-Dashboard für einen
expliziten Stichtag bereit. Activity und Widget erfassen diesen Tag je Ladezyklus genau einmal,
damit ein Tageswechsel nicht Daten und Überfälligkeitsprojektion auseinanderlaufen lässt.

## Konsequenzen

Fokusänderungen können keine Widgetfelder mehr versehentlich verändern. Der kombinierte
Widget-Untertitel besitzt genau eine Quelle, während der Fokus Menge und Notiz separat rendert.
Die bestehende Widgetausgabe und alle Golden-Baselines bleiben unverändert; es gibt keine
Schema- oder Persistenzänderung.

`TaskSnapshot` ist weiterhin das historisch gewachsene Taskmodell der Fokuskarte. Seine spätere
Umbenennung oder weitere Zerlegung ist nicht Teil dieser Phase, weil dies keine zusätzliche
Verbraucherkopplung beseitigen würde und unnötig viele öffentliche UI-Verträge berührte.
