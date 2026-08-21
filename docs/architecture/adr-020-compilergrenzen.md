# ADR-020: Compilergrenzen für Domain und Today-Kern

- Status: angenommen
- Datum: 2026-08-21

## Kontext

Paketregeln und stringbasierte Architekturtests konnten unerlaubte Android- oder
Managementabhängigkeiten nur nachträglich erkennen. Domainmodelle, Ports, Use Cases und der
Today-Zustandsautomat lagen trotz stabiler Pakete weiterhin im Android-App-Modul. Dadurch war
eine versehentliche Rückkopplung technisch möglich und Refactors benötigten zusätzliche
Quelltextprüfungen.

## Entscheidung

- `:core-domain` ist ein `java-library`-Modul und besitzt Domainmodelle, Capability-Ports,
  Use Cases sowie die reinen Zeit- und Schedulingtypen.
- `:today-core` ist ein `java-library`-Modul und besitzt Today-Projektionen, Actions, Commands,
  Reducer und Coordinator. Seine einzige Modulabhängigkeit ist `:core-domain`.
- `:app` hängt von beiden Modulen ab und behält Android-Ressourcen, Room, Lifecycle,
  lokale Textauflösung und Views.
- Android-nahe Renderinputs wie `FocusCardUiModel`, die Palette, Preferences oder Draftzustand
  verbinden, verbleiben bewusst unter `app/ui.today` und sind kein Kernmodell.
- Der lokale Build kompiliert die Android-Test-APK. Echte Gesten- und Recreationtests laufen in
  der bestehenden CI-Instrumentierungsmatrix auf API 26 und API 35.

## Konsequenzen

Domain-zu-Android-, Today-zu-Management- und Today-zu-App-Abhängigkeiten können nicht mehr
kompilieren. Die App bleibt Composition Root; Room implementiert das gebündelte
`ApplicationTaskRepository`, ohne dass Use Cases dessen breite Sicht benötigen. Modulgrenzen
ersetzen die früheren Importscans für die extrahierten Bereiche. Paket- und Ownershiptests
bleiben nur dort bestehen, wo Android-Komponenten weiterhin gemeinsam im App-Modul liegen.
