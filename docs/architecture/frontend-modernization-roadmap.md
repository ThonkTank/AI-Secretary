# Roadmap: robuste Compose-Präsentationsarchitektur

Status: verbindlich

Beschlossen: 2026-08-24

Ausgangsstand: `e56ce330` (`forest-android-1011101`)

Ausgangsschema: Room 16

## Ziel und unveränderliche Grenzen

Das Android-Frontend wird schrittweise von imperativ verdrahteten Java-Views auf Kotlin,
Jetpack Compose und einen eindeutigen unidirektionalen Datenfluss umgestellt. Die Migration
ändert zunächst weder Produktverhalten noch visuelle Gestaltung. Domainmodelle, Use Cases,
Room-Daten, App-ID, Signatur, unterstützter Upgradepfad, RemoteViews-Widgets und spezialisierte
Grafikberechnungen bleiben erhalten.

Während der Migration gilt ein UI-Feature-Freeze. Zulässig sind Fehlerkorrekturen,
Barrierefreiheitskorrekturen, Testhärtung und Änderungen, die für die Migration zwingend nötig
sind. Neue Produktfunktionen, ein Material-Redesign, Hilt, eine allgemeine Design-System-
Einführung und eine nebenläufige Umschreibung der Fachlogik sind ausgeschlossen. Abweichungen
benötigen eine eigene ADR und Owner-Entscheidung.

Die visuellen Verträge unter `docs/reference/homescreen` und `docs/reference/task-editor` sowie
die vorhandenen Golden-Baselines sind autoritativ. Ein Pixelunterschied wird nicht stillschweigend
als Compose-Nebeneffekt akzeptiert.

## Ausgangslage

Der fachliche und persistente Kern besitzt bereits sinnvolle Grenzen in `core-domain`,
`today-core`, Capability-Ports, Room und den vorhandenen Use Cases. Die Präsentation bleibt
jedoch fragil:

- Das App-Modul enthält 192 Java- und keine Kotlin-Quelldateien; 28 Klassen implementieren
  eigene `View`-/`ViewGroup`-Flächen.
- `MainActivity` verdrahtet mehrere ViewModels und Präsentationskoordinatoren. Sie ist dadurch
  weiterhin ein impliziter Nachrichtenbus.
- `TaskViewModel` bündelt Editor, Today, Kalender, Refresh und Widgetinvalidierung.
- Editor und Alles-Tab halten Teile ihres Zustands parallel in View und ViewModel.
- synchrone Room-Reads, manuelle Reload-Signale und verbrauchbare LiveData-Ereignisse machen
  Aktualisierungsreihenfolgen lebenszyklusabhängig.
- die Standard-Instrumentierung deaktiviert Systemanimationen und deckt deshalb einen wichtigen
  Teil der realen Laufzeit nicht ab.

Diese Roadmap ersetzt keine fachliche ADR. Sie setzt für die Präsentationsmigration die früher
formulierte Einschränkung außer Kraft, Compose sei grundsätzlich nicht vorgesehen.

## Verbindlicher Phasenablauf

Jede Phase wird von aktuellem `origin/main` auf einem Branch
`codex/frontend-p<Nummer>-<Thema>` begonnen. Vor Implementierungsbeginn werden diese Roadmap,
der aktuelle Projektstand, die Ergebnisse vorheriger Phasen und die betroffenen Tests erneut
gelesen. Der konkrete Zuschnitt, Zustandsbesitz, Datenfluss, Risiken und die erforderlichen
Nachweise werden im Fortschrittsprotokoll festgehalten.

Eine Phase wird vor dem ersten Produktcode geteilt, wenn sie nicht mit einem kohärenten,
reviewbaren Ergebnis, vollständigen Tests und ohne parallele Alt-/Neu-Wahrheiten in einem Sprint
abschließbar ist. Unterphasen heißen beispielsweise `5a`, `5b` und `5c`; jede Unterphase erhält
einen eigenen Branch, Pull Request und Abschlussaudit.

Nach der Implementation folgt ein expliziter Abgleich mit dieser Roadmap. Dabei werden
insbesondere beobachtete Schwächen, Shortcuts, ungünstige Scope-Vereinfachungen, verbliebene
Doppelzustände und fehlende Nachweise dokumentiert. Blockierende Diskrepanzen werden auf demselben
Branch behoben. Größere kohärente Restarbeiten werden als unmittelbar folgende Nacharbeitsphase
geplant und umgesetzt; sie dürfen nicht stillschweigend in eine spätere Fachphase verschoben
werden.

Eine Phase ist **implementiert**, wenn ihr Pull Request grün geprüft und per Squash nach `main`
übernommen wurde. Bei UI-relevanten Phasen ist sie erst **vollständig abgeschlossen**, wenn die
aus dem exakten Merge-Commit veröffentlichte APK über den In-App-Updater auf einem physischen
Gerät installiert und dort abgenommen wurde. Ohne verfügbares Gerät lautet der Status ausdrücklich
„Geräteabnahme ausstehend“; die nächste UI-Cutover-Phase wartet.

## Zielarchitektur und Schnittstellen

Jeder Bildschirm besitzt genau einen Screen-State-Owner:

- `TodayViewModel` veröffentlicht `StateFlow<TodayScreenState>`;
- `TaskEditorViewModel` veröffentlicht `StateFlow<TaskEditorScreenState>`;
- `AllTasksViewModel` veröffentlicht `StateFlow<AllTasksScreenState>`;
- `OptionsViewModel` veröffentlicht `StateFlow<OptionsScreenState>`.

UI-Eingaben werden als abgeschlossene `ScreenAction`-Typen an den jeweiligen Owner gesendet.
Einmalige Navigation, Dialoge und Systemanfragen werden nicht als verbrauchbare Event-Wrapper
modelliert, sondern als Bestandteil des Screen State mit stabiler ID und expliziter Bestätigung.
ViewModels kommunizieren nicht direkt miteinander. Navigation und screenübergreifende
Invalidierungen entstehen aus gemeinsam beobachteten Datenquellen beziehungsweise expliziten
App-Schnittstellen, nicht aus Activity-Callbacks.

Room-Invalidierungen, Kalenderänderungen, Präferenzen und relevante Zeitwechsel werden als
Kotlin-Flows adaptiert. Reads dürfen bei einer neueren Anfrage abgebrochen werden; bereits
gestartete schreibende Transaktionen werden nie abgebrochen. Kurzlebiger Darstellungszustand
bleibt lokal in Compose, wiederherstellungsrelevante Entwürfe und Filter liegen im
`SavedStateHandle`.

`AppContainer` bleibt der Composition Root; ein DI-Framework wird nicht eingeführt. Normale UI
wird in Compose umgesetzt. Vorhandene Spezialgrafiken dürfen über kleine zustandslose
`AndroidView`-Adapter weiterverwendet werden. RemoteViews-Widgets bleiben außerhalb von Compose.
Nach vollständigem Screen-Cutover übernimmt Navigation 3 typisierte `AppDestination`-Schlüssel.

## Roadmap

### Phase 0 – Architekturvertrag und Freeze

Diese Roadmap, die zugehörige ADR und ein fortlaufendes Auditprotokoll werden angelegt. Baseline,
Legacy-Ausnahmen, visuelle Verträge, Phasengates und Feature-Freeze werden verbindlich. Diese
Phase ändert weder Produktcode noch Golden-Baselines.

### Phase 1 – Laufzeit- und Animationstestnetz

CI prüft die kritischen Interaktionen auf API 26, 35 und 37 zusätzlich mit Animationen bei 1×.
Tests verwenden Synchronisationssignale statt Wartezeiten und decken Recreation während
Animation, Refresh und Eingabe ab. Ein debug-only Presentation Trace macht Zustandsübergänge
diagnostizierbar. Animationen dürfen keine Fachaktion, Navigation oder Persistenz auslösen.

### Phase 2 – moderne, gepinnte Buildgrundlage

AGP 9.2.0, Gradle 9.4.1, `compileSdk 37`, Built-in Kotlin und Compose-Compiler 2.3.21,
Compose BOM 2026.08.00, Activity Compose 1.13.0, Lifecycle 2.11.0 und Room KTX 2.8.4 werden
als getesteter Satz eingeführt. Compose wird zunächst ohne Material in einem unsichtbaren
Smoke-Host aktiviert. `minSdk 26`, `targetSdk 35` und JVM 17 bleiben unverändert. Der Debug-Build
bleibt unter 10 MiB, der unsigned Release-Build unter 8 MiB und die Fonts zusammen unter
1,6 MiB. Neuinstallation und Upgrade werden zusätzlich auf API 37 geprüft. R8-Änderungen werden
nicht in dieselbe Phase aufgenommen.

### Phase 3 – beobachtbare Daten und Invalidierung

Die synchronen Java-DAOs bleiben zunächst bestehen und erhalten Flow-Adapter auf Basis des Room
Invalidation Trackers. Kalender, Präferenzen und Uhr werden ebenfalls beobachtbar. Manuelle
Reload-Signale und verteilte Widgetinvalidierungen werden entfernt, ohne Transaktionsgrenzen zu
verändern. Tageswechsel, Vordergrundmaterialisierung und konkurrierende Reads erhalten Race-Tests.

### Phase 4 – eindeutige Screen-State-Owner

Der bisherige `TaskViewModel` wird nach Bildschirmverantwortung aufgeteilt, der Alles-Tab auf
StateFlow umgestellt. Jeder Screen veröffentlicht einen atomaren Zustand und verarbeitet
typisierte Actions seriell. ViewModel-zu-ViewModel-Signale, Activity-Brokerlogik und verbrauchbare
Events entfallen. Bestehende Views werden bis zu ihrem Cutover nur über dünne Binder gespeist.

### Phase 5 – Aufgabeneditor in Compose

Wizard, Detailseiten, Prompts, Validierung, Fokus, Scrollposition, adaptive Darstellung und
Barrierefreiheit werden mit dem bestehenden Reducer und Formatter in Compose umgesetzt. Save,
Delete und Close sind wiederholungs- und recreation-sicher. Nach Side-by-Side-, Golden- und
Geräteabnahme werden `TaskEditorView` und seine reine View-Orchestrierung entfernt.

### Phase 6 – Alles-Tab in Compose

Die virtuelle Liste wird als `LazyColumn` mit stabilen Schlüsseln umgesetzt. Filter, Expansion,
Dropdown, Drag, Randscrollen und gleichwertige Accessibility-Aktionen behalten ihren bisherigen
Vertrag. Nach Abnahme werden RecyclerView und Legacy-Controls entfernt.

### Phase 7 – Optionen und Updater in Compose

Theme, Kalenderberechtigung, Fokuspräferenzen und Updatefluss werden Compose-basiert. Android-
Systemnavigation bleibt eine Host-Verantwortung; Installationsanfragen besitzen stabile IDs und
Bestätigung. Die Abnahme umfasst den echten In-App-Updatepfad.

### Phase 8 – Today in Compose

Header, Fokus, Timeline, Historie, Footer, Wiederholung und Reorder werden migriert. Today-
Reducer, Projektionen und Fachcommands bleiben erhalten. Reward- und Reorderfolgen müssen über
Recreation exakt einmal und in stabiler Reihenfolge verarbeitet werden. Spezialgrafiken bleiben
zunächst auf der dokumentierten Interop-Allowlist.

### Phase 9 – Compose-Shell und Navigation 3

Die Activity wird auf Lifecycle, Systemergebnisse und Intent-Einstiege reduziert. Typisierte
Navigation bildet Top-Level-Wechsel, Editor-Stack, Zurückverhalten, Widget-Deep-Links und
Prozesswiederherstellung ab. Alte Mount-, Layout- und Navigationsübergänge werden entfernt.

### Phase 10 – Abschlussaudit und Freeze-Aufhebung

Legacy-Views sind nur noch für RemoteViews-Widgets und ausdrücklich freigegebene Spezialgrafiken
zulässig. Architekturtests sichern diese Allowlist. Die vollständige Host-, Instrumentierungs-,
Golden-, Animations-, Gesten- und Upgradematrix läuft gegen die Baseline. Größe, Startzeit,
Eingabelatenz, Frames und Speicher werden verglichen. Der Freeze endet erst nach Merge,
Veröffentlichung und Geräteabnahme.

## Dauerhafte Abnahmematrix

- Editor: Tippen, Fokus, Minutenfeld, Rotation, Prozessverlust, Doppelspeichern, Fehlerpfade,
  Prompts und Hardware-Back.
- Alles-Tab: Datenänderung, Filter, Expansion, Dropdown, Karten, Drag, Persistenzfehler und
  Accessibility-Aktionen.
- Today: Kalenderrefresh, Tageswechsel, Rewardfolge, Recreation, „später“, Ernte, Undo und Drag.
- Optionen/Updater: Berechtigung, Check, Download, Verifikation, Installer und Abbruch.
- App-Shell: Kaltstart, Back, Intents, Widget-Einstieg, Hintergrund, Force-Stop und Upgrade ab dem
  unterstützten Produktionsstand.
- Animationen an und aus müssen dieselben fachlichen Ergebnisse erzeugen.

Schemaänderungen, Ziel-SDK-Erhöhungen, neue Produktfeatures, ein Grafik-Neudesign und allgemeine
Domain-Umschreibungen benötigen eigene Roadmaps und dürfen nicht in diese Phasen hineingezogen
werden.
