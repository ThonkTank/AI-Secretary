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
`ComboProgress`, nicht mit frei vergleichbaren Strings. Schema 6 hat den früheren
`RoutineProgress` sowie dessen Legacy-Spalten aus der aktiven Domäne entfernt.

Schema 7 speichert Rewardänderungen als unveränderliche, vorzeichenbehaftete Buchungen.
Bestehende Reward-Snapshots wurden ohne erneute XP- oder Komboerhöhung in Legacy-Buchungen
überführt; Undo ergänzt eine eindeutig referenzierte Gegenbuchung. Gesamt-XP und Kombos bleiben
atomar aktualisierte Leseprojektionen.

## Explizite Schreib- und Lesevorgänge

Das Laden des Dashboards ist rein lesend. Fällige Occurrences entstehen ausschließlich über
den expliziten Use Case `MaterializeDueOccurrences`, den die App vor einem fachlichen
Refresh ausführt. Dashboard-Schritte und fällige Schrittvorlagen werden gebündelt geladen;
die Anzahl der Leseabfragen wächst daher nicht mit der Zahl der Aufgaben.

Erstellen, Bearbeiten, Verschieben, Zurückstellen, Abschließen und Löschen sind getrennte
Use Cases. Die bisherige Klasse `TaskService` bleibt vorübergehend als delegierende Fassade
für ältere Characterization-Tests bestehen, enthält aber keine Transaktions- oder
Schedulinglogik mehr. Produktionscode verwendet sie nicht mehr.

Der Transaktionsport liefert generische Rückgabewerte; Completion-Use-Cases benötigen deshalb
keine veränderlichen Holder mehr. Abschluss, Ernte, Undo und Condition-Close delegieren an den
transaktionalen `CompletionService`. Rewardberechnung, Zustandsübergänge und Terminprojektion
sind getrennte reine Komponenten. Für Folgetermine werden nur das früheste offene und das letzte
abgeschlossene Vorkommen gezielt abgefragt, nicht die vollständige Task-Historie.

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

Die Migration 1→2 konnte mangels historischer Wocheninformation lediglich den letzten
Abschlusstag übernehmen. Der Entity-Mapper normalisiert diesen defensiv auf den Montag der
betroffenen Kalenderwoche. Damit werden keine fehlenden Ringwochen erfunden, die bekannte
Woche wird aber unter Schema 3 korrekt fortgeführt.

## Einheitlicher Präsentationszustand

`TaskViewModel` veröffentlicht einen unveränderlichen `DashboardUiState`. Darin liegen das
typisierte Navigationsziel, das bereits mit Kalenderterminen sortierte `TodayUiModel`,
der Kalender- und Berechtigungszustand, die aktuelle Palette, Ladeinformationen, laufende
`UiCommand`s, der offene Vollbildeditor und der `SetProgressEditorState`. Die Activity führt Aufgaben und Termine daher nicht
mehr selbst zusammen und sortiert keine fachlichen Einträge.

Navigation und Editorziel werden mit `SavedStateHandle` wiederhergestellt. Laufende
Kommandos werden synchron über typisierte Identitäten gesperrt, sodass Doppelklicks nicht
mehrere identische Schreibvorgänge anstellen. Fehler, Bestätigungsdialoge,
Berechtigungsanfragen und externe Navigation sind konsumierbare `UiEvent`s statt
wiederholender `LiveData<String>`-Werte.

Rewardanimationen sind keine konsumierbaren `UiEvent`s mehr. Das ViewModel führt eine
ID-deduplizierte FIFO-Queue aus `RewardEffect`s; der eigenständige `RewardAnimator` bestätigt
jeden Effekt nach Abschluss. Ein explizites `RewardAnchorRegistry` ersetzt View-Tags und
Hierarchiesuche.

## Komponentenbasierte Views

`MainActivity` ist nur noch Lifecycle-Host, verdrahtet System-Events und leitet
Benutzeraktionen an das ViewModel weiter. `DashboardRenderer` hält einen langlebigen
View-Baum und aktualisiert ihn differenziell; ein Minutenwechsel ersetzt weder Karten noch
den gesamten Inhalt. Nur ein tatsächlicher Navigationswechsel montiert eine andere
Oberfläche. Scroll-, Fokus- und Accessibility-Zustände der Dashboard-Komponenten bleiben
bei normalen Updates erhalten.

Header, Fokusaufgabe, Aufgabenblatt, Kalenderblatt, Leerzustand, Footer-Navigation,
Kombo-Maserung und Optionen sind eigenständige Views. Wiederverwendbare Typografie-, Maß- und
Form-Helfer liegen in `UiStyle`, Produkttexte und zentrale Maße in Android-Ressourcen. Die
Activity nutzt die AndroidX-Edge-to-edge- und WindowInsets-APIs.

`TaskEditorCoordinator` montiert und bindet den Vollbildeditor außerhalb der Activity.
`SetProgressEditorView` besitzt keine versteckten Entwurfsdaten: Expansion, Eingabe und Fehler
liegen immutable im Presentation-State und überleben normale Dashboard-Re-Renders. Stabile
Ressourcen-IDs bilden Testseams für die zentralen programmgesteuerten Komponenten.

## Lifecycle-sicherer Maserungsrenderer

`WoodGrainView.onDraw` berechnet keine Geometrie. Größen- und Anchoränderungen erzeugen immutable
Requests mit bereits lokalen `RectF`-Werten; `WoodGrainRenderPipeline` dedupliziert gleiche
Schlüssel und baut SDF-/Marching-Squares-Pfade auf Worker-Threads. Generationstokens verhindern,
dass ein nach Detach oder neuer Bindung veraltetes Ergebnis publiziert wird.

Der gemeinsame LRU-Cache ist auf geschätzte 4 MiB Pfaddaten begrenzt. Segmentgewicht,
Buildanzahl, Miss-/Hit-Latenz, Draw-Median, p95 und Heapwachstum sind über den optionalen
`WoodGrainBenchmarkTest` reproduzierbar. Software-Halo und Blur bleiben bewusst erhalten, weil
die geprüfte hardwarefreundliche Variante die unveränderten Null-Pixel-Goldens verletzte.

Der `TaskEditorDialog` besitzt einen vollständigen, über `SavedStateHandle` gesicherten
Entwurf und einen separaten `TaskEditorValidator`. Beim Bearbeiten können Titel,
Tageszeit, Wiederholung, Intervall, Wochentage, Schritte, ongoing-Status und
Erledigungsbedingung geändert werden. `LoadTaskDetails` lädt dafür Task und
Schrittvorlagen, `UpdateTask` ersetzt die Definition atomar; eine bereits offene
Occurrence bleibt als historisch fällige Ausführung unverändert.

## Kalender als strukturierte Systemgrenze

`CalendarDataSource` liefert `Success`, `PermissionMissing`, `ProviderUnavailable` oder
`Error`; eine leere erfolgreiche Terminliste ist damit nicht mehr von einem technischen
Fehler abhängig. `CalendarUiState` übernimmt diesen Status und die Optionen zeigen
Provider- und Ladefehler getrennt vom Berechtigungszustand an.

Standardmäßig werden alle sichtbaren, nicht abgesagten und nicht abgelehnten Kalender
gelesen. Der frühere Google-only-Filter ist die explizite `CalendarPolicy.GOOGLE_ONLY` und
wird typisiert in `UiPreferences` gespeichert. Datum und Zone kommen weiterhin aus den
injizierten Providern. `CalendarDayWindow` berechnet lokale Tagesgrenzen als Instants und
berücksichtigt damit 23- und 25-stündige DST-Tage.

Das Repository cached ein Ergebnis anhand von Datum, Zone, Policy und
Berechtigungszustand. Activity und alle Widgets verwenden die gleiche Instanz aus dem
`AppContainer`. Der `ContentObserver` wird erst nach erteilter Runtime-Berechtigung lazy
registriert, invalidiert den Cache bei Kalenderänderungen und stößt über die
DataSource-Subscription einen Presentation-Refresh an. Der erste App-Start bleibt dadurch
auch ohne Kalenderfreigabe sicher.

## Strukturiertes Designsystem und Wald-Rendering

`DayPalette` besteht aus benannten `SurfaceTokens`, `TypographyTokens`, `ForestTokens`
und `MotionTokens`; der frühere lange positionale Konstruktor ist damit aus den Aufrufern
verschwunden. `DayPaletteInterpolator` interpoliert zwischen acht expliziten Zeitankern.
Token- und Rendering-Goldens fixieren diese Referenzpunkte, während Kontrasttests die
zentralen Text-/Flächen-Kombinationen zusätzlich über den ganzen Tag prüfen.

Die festen Modi frieren Flächen, Typografie, Baumfarbe und Tiefenstaffelung ein. Position,
Breite und Farbe der Sonne bleiben bewusst zeitabhängig, damit der Tagesfortschritt auch
in einem erzwungenen Hell- oder Dunkelmodus erkennbar bleibt. Ein nicht verwendeter
Schattenparameter wurde entfernt.

Der Wald zeichnet die drei Tiefenebenen aus der freigegebenen Silhouetten-Referenz.
Pfade, Astkoordinaten, Shader und Paints werden nur bei Größen- oder Palettenänderungen
neu aufgebaut; `onDraw` allokiert keine dieser Objekte. Die sehr zurückhaltende
Breathing-Animation läuft ausschließlich bei angehängter, sichtbarer View und beachtet
die systemweite Einstellung für reduzierte beziehungsweise deaktivierte Animationen.
App und Widget beziehen ihre Farben über dieselbe `DayPalette`-Definition.

## Widget-Pipeline

`TaskWidgetProvider` ist nur noch der Android-Lifecycle-Adapter. Ein
`WidgetUpdateCoordinator` lädt pro Updatezyklus genau einmal Dashboard, Kalender und
Palette und projiziert diesen Snapshot anschließend über `WidgetPresenter` und
`WidgetSizeClassifier` in unveränderliche `WidgetUiModel`s. Erst
`WidgetRemoteViewsFactory` bindet diese Modelle an Small, Wide, Tall und Large. Ein
fehlerhaftes einzelnes Widget blockiert die übrigen IDs nicht; der asynchrone
`PendingResult` wird auch bei Lade-, Render- und Schedulingfehlern abgeschlossen.

App und Widget verwenden mit `ForestArtworkRenderer` dieselbe Waldgeometrie. Für
RemoteViews werden größen- und palettenabhängige Bitmaps gecacht. Jede Bitmap ist auf
höchstens 256 × 256 Pixel beziehungsweise 256 KiB begrenzt; ein Parcel-Test hält den
gesamten Renderpfad unter dem Binder-Budget. Unter API 31 bleiben die XML-Hintergründe von
Blättern und Buttons als dokumentierter heller Fallback bestehen, während Wald,
Hintergrund, Sonne, Typografie und Inhaltsfarben dynamisch bleiben. Ab API 31 werden auch
die abgerundeten Chrome-Flächen dynamisch getintet.

Widget-Refreshes verwenden ausschließlich den lesenden Presenter. Schreibaktionen laufen
validiert und geordnet über `TaskActionHandler`; unbekannte oder unvollständige Broadcasts
werden geloggt und ändern keine Daten. Eine endgültige ongoing-Bedingung wird nicht mehr
im Receiver ausgeführt, sondern öffnet den bestehenden Bestätigungsdialog in der App.
Bedienbare Widget-Flächen sind, soweit das RemoteViews-Layout sie kontrollieren kann,
mindestens 48 dp groß.

## Qualitäts- und Release-Gates

Version 0.2.0 verwendet Room 2.8.4, Lifecycle 2.11.0, Activity 1.10.1 und AndroidX Test
1.7.0/1.3.0. Activity bleibt bewusst auf der letzten mit compileSdk 35 und AGP 8.7.3
kompatiblen Linie; neuere Activity-Versionen würden einen separaten Toolchain- und
compileSdk-36-Sprung erzwingen. Java-Kompilierung meldet Deprecations explizit und der
ViewModel-Saved-State- sowie Migrationstest-Code verwendet die aktuellen APIs.

Das Verify-Workflow baut Debug-, Instrumentierungs- und unsigned Release-APK, führt Unit-,
Golden- und Linttests aus, prüft Größenbudgets und startet die Instrumentierungsmatrix auf
API 26 und API 35. Erst nach allen grünen Gates baut derselbe Workflow die dauerhaft signierte
APK, prüft Paket, Version und Zertifikat und veröffentlicht sie mit Updater-Metadaten.
Das Launcher-Icon besitzt ab API 26 eine adaptive Vorder-/Hintergrunddefinition. Die vier
lokalen Fonts inklusive Lizenzen belegen zusammen rund 1,5 MB; CI begrenzt sie auf 1,6 MiB
und die Debug-APK auf 5 MiB. `DebugPreviewFixtures` stellt reproduzierbare volle und leere
Dashboardzustände für Layout Inspector und weitere Preview-Screens bereit.

### Lokale Geräteprüfung

Die installierte Emulatorversion 37.1.11 wurde mit den vorhandenen AVDs
`codex_autosecretary_api26` und `codex_autosecretary_api35` geprüft. API 26 stürzt sowohl
mit KVM/SwiftShader nach etwa zehn Sekunden als auch ohne Beschleunigung nach ungefähr
einer Minute im nativen Prozess `qemu-system-x86_64-headless` mit `SIGSEGV` ab. API 35
erreicht ohne Beschleunigung nach rund 80 Sekunden ADB, stürzt aber vor dem Start des
Package-Managers ebenfalls mit `SIGSEGV` ab. Ein physisches Gerät ist nicht verbunden;
lokale Instrumentierung konnte deshalb nicht ehrlich als ausgeführt markiert werden.

Als sichere Alternativen laufen Robolectric-Tests ausdrücklich auf API 26 und API 35,
beide Instrumentierungs-APKs werden lokal gebaut, und die CI enthält echte Emulatorjobs
für beide API-Stufen. Emulator- und ADB-Prozesse werden nach den Versuchen beendet.
