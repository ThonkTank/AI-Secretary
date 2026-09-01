# Architekturkritik von XP, Gefäß, Kombo und Today-Screen

Stand: 2026-08-19

## Umsetzungsstand

- Phase 1 behoben: `OccurrenceStep.done` ist explizit; Bearbeiten, Fertigstellen und
  Wiederöffnen sind getrennte Befehle, und vollständiger Satzfortschritt bleibt beim Undo
  erhalten.
- Phase 2 behoben: Schema 6 entfernt Legacy-Streakspalten und `RoutineProgress` aus der aktiven
  Domäne; neue Vorkommensschritte speichern ihre Template-Herkunft, historische nur bei einem
  eindeutigen Match.
- Phase 3 behoben: Schema 7 ersetzt verteilte Rewardfelder durch ein unveränderliches Ledger,
  exakte Gegenbuchungen und vorzeichenbehaftete Receipts; Anzeigeprojektionen stammen aus dem
  Ledger.
- Phase 4 behoben: Ein generischer Transaktionsport liefert typisierte Ergebnisse;
  `RewardCalculator`, `CompletionStateMachine` und `ScheduleProjector` sind reine Komponenten,
  der `CompletionService` orchestriert sie. Schedule-Reconciliation nutzt zwei gezielte Queries,
  und `OccurrenceKind` ersetzt Stringpräfixe in der Fachlogik.
- Phase 5 behoben: `TodayUiModel` ist das einzige kanonische Today-Read-Model; der Mapper
  berechnet XP-Fortschritt, Fokus, Erntereife und Undo-Verfügbarkeit. Commands, Rewardeffekte und
  Anchors sind typisiert, Rewardeffekte werden per ID bestätigt, und View-Tags beziehungsweise
  Action-Strings wurden entfernt. Rewardanimation, Satzeditor und Editor-Mounting besitzen eigene
  Komponenten; Inline-Entwurf, Expansion und Fehler überleben Re-Rendering im Presentation-State.
- Phase 6 behoben: SDF und Marching Squares laufen in einer deduplizierten Worker-Pipeline;
  `onDraw` zeichnet nur immutable Renderdaten. Der View erhält lokale Rechtecke, der Cache ist
  auf geschätzte 4 MiB begrenzt, und Lifecycle-/Cache-/Build-Invarianten sind getestet. Median
  und p95 wurden gegenüber der reproduzierbaren Baseline schneller; Software-Halo und Blur
  bleiben wegen der Null-Pixel-Golden-Toleranz bewusst bestehen.
- Phase 7 behoben: Reine Reward-/Completion-/Undo-/Schedule-Fälle verwenden ein schnelles
  `InMemoryTaskRepository`; historische Robolectric-Schemata stammen aus Room-Exports. Golden-
  Fehler erzeugen Expected/Actual/Diff und Updates sind in CI gesperrt. Today und Inline-Editor
  laufen durch die 3×3-Matrix aus 320/412/600 dp und Font Scale 1,0/1,3/2,0; Rollen, Zustände,
  TalkBack-Reihenfolge, Tastatur, Touchziele, Reduced Motion, Kontrast und Dauerlast sind geprüft.

## Umfang und Gesamturteil

Diese Kritik bezieht sich auf die in den sieben Refactoring-Phasen bearbeiteten Bereiche: XP- und
Komboregeln, Abschluss und Undo, Room-Schema 5, Dashboard-Read-Models, Widget-Verhalten,
Today-Screen, Gefäß und Wert-Tau, Canvas-Maserung, Animationen, Satzfortschritt sowie die
zugehörigen Regel-, Use-Case-, Migrations-, UI- und Golden-Tests.

Das Ergebnis ist funktional deutlich geschlossener als die Ausgangslage. Reine Modelle wie
`ComboProgress`, `XpProgress` und `RewardPolicy` machen zentrale Regeln lesbar. Tatsächlich
angewandte XP und Kombodeltas werden gespeichert, Use Cases begrenzen Transaktionen, die
Maserungsgeometrie ist vom Android-Canvas getrennt, Bewegungswerte sind zentralisiert und die
Testabdeckung reicht von Regeln über Room bis zu Phone- und Widget-Goldens.

Architektonisch bleibt es dennoch eine große Erweiterung in einer historisch gewachsenen,
einmoduligen Android-App. Der neue Pfad funktioniert, aber ein Teil seiner Sicherheit entsteht
durch Disziplin, Tests und Namenskonventionen statt durch starke Typen, Aggregate oder harte
Modulgrenzen. Besonders Domänenzustand, Persistenzbelege, UI-Effekte und Geometrie sind enger
miteinander gekoppelt, als es für zukünftige Änderungen wünschenswert wäre.

## Was Planung und Implementierung erschwert hat

### Der Auftrag war ein Big-Bang über alle Schichten

Die Änderung verband in einem Zug:

- neue XP- und Komboregeln;
- eine persistente, exakt umkehrbare Buchungslogik;
- eine Room-Migration über vier mögliche Ausgangsschemata;
- neue App- und Widget-Semantik;
- eine weitgehende native Neugestaltung des Today-Screens;
- einen eigenen SDF-/Marching-Squares-Renderer;
- Animation, Reduced Motion und Accessibility;
- neue Goldens und ein vollständiges Release-Gate.

Das Ergebnis umfasste 66 Dateien, rund 1.500 neue beziehungsweise geänderte Quelltextzeilen und
zwölf Phone-Goldens. Eine solche horizontale Änderung erschwert die Ursachenanalyse: Ein Fehler
im Dashboard kann aus einer Regel, einer Migration, einem Mapper, einer View-Hierarchie oder
einem Animationseffekt stammen. Gewünscht wären mehrere vertikale, jeweils auslieferbare
Schnitte gewesen: zuerst Schema und Fachregeln, dann Abschluss/Undo, danach Read-Model und
Widgetvertrag, anschließend Today-UI und zuletzt Renderer/Bewegung. Jeder Schnitt hätte einen
kleineren Diff, ein engeres Gate und eine klarere Rückfallmöglichkeit gehabt.

### Das Handoff war für Web präzise, für native Android-Ausführung aber indirekt

Das Archiv enthielt ein HTML-Zielbild, CSS-/JavaScript-Designsystem und eine ausführliche
Beschreibung. Farben, Maße und Bewegungszeiten waren dadurch gut nachvollziehbar. Es fehlten
jedoch native Layoutreferenzen, exportierte Vektorpfade, ein semantischer Komponentenbaum und
Referenzen für unterschiedliche Dichten, Schriftgrößen oder Bildschirmbreiten. DOM-Geometrie,
CSS-Schatten, Masken und Pseudoelemente mussten in programmgesteuerte Android-Views, Canvas-
Operationen und Software-Layer übersetzt werden.

Das verlangsamte die Umsetzung und erhöhte das Risiko, dass eine optisch richtige 412×892-dp-
Aufnahme auf anderen Geräten unnötig teuer oder fragil ist. Besser wären zusätzlich gewesen:

- native Maße und Zustände als maschinenlesbare Tokens;
- Referenzen für kleine und große Schrift, schmale Geräte und mindestens zwei Dichten;
- klare Angaben, welche Effekte exakt und welche nur wahrnehmungsäquivalent sein müssen;
- ein Performancebudget für Renderzeit, Speicher und Animationen;
- eine Entscheidung, ob SDF/Marching Squares fachlich vorgeschrieben oder nur eine mögliche
  technische Umsetzung ist.

Die konkrete Vorgabe des Renderingalgorithmus half bei der visuellen Zieltreue, nahm der
Implementierung aber Architekturspielraum. Für eine dekorative Oberfläche ist ein eigener
Konturextraktor ein erheblicher Wartungsblock; ein vorgerenderter Pfad, ein kleiner Shader oder
ein parametrisches VectorDrawable hätte abhängig von den tatsächlich benötigten Zuständen
einfacher sein können.

### Ausgangslage, in Phase 2 behoben: Die alte Wochenring-Architektur war nicht entfernt

Nach dem ersten visuellen Umbau waren zwar `ringWeeks` und der Jahresring verschwunden, das
Kernmodell trug aber weiter `RoutineProgress`; Entity, Mapper und Tests kannten alte Level- und
Streakfelder. Die Mechanik war damit weiterhin Teil jedes Task-Roundtrips.

Das störte auf zwei Ebenen. Erstens musste bei jeder Konstruktor- oder Copy-Operation entschieden
werden, wie ein fachlich totes Objekt weitergereicht wird. Zweitens täuscht die Domäne vor, dass
zwei Belohnungssysteme gültig sind, obwohl nur `ComboProgress` Verhalten und UI steuert. Die ADR
erlaubt kompatible Altdaten im Schema, aber daraus folgt nicht, dass sie dauerhaft Bestandteil
des aktiven Domänenmodells bleiben müssen.

Phase 2 setzte diese gewünschte Trennung um: Schema 6 baut `tasks` kontrolliert neu auf und
`RoutineProgress` existiert weder in aktiver Domäne noch Mappern oder aktuellen Tests. Die
veröffentlichten Schema-1-bis-5-JSONs enthalten die alten Spalten absichtlich weiterhin als
historischen Migrationsvertrag.

### Ausgangslage, in Phase 3 behoben: Es gab keinen Buchungsbegriff

Die Zwischenarchitektur speicherte `awardedXp`/`comboPointDelta` am Vorkommen und
`earnedXp`/`comboPointDelta` am Schritt. Das ermöglichte exaktes Undo, war aber kein
eigenständiges Buchungsmodell.

In dieser Zwischenarchitektur war die Bedeutung verteilt:

- Schritt-XP lagen am Schritt und waren zunächst nur Gefäßinhalt;
- Ernte-XP lagen am Vorkommen und veränderten Gesamt-XP;
- beide hießen in `RewardReceipt` lediglich `xp` und wurden über `Target` unterschieden;
- eine atomare Widget-Aktion führte Schrittbuchungen und Ernte aus, konnte aber nur einen
  einzelnen Receipt zurückgeben;
- Reversal wurde durch einen positiven XP-Wert plus `reversed` statt durch eine explizite
  Gegenbuchung beschrieben.

Phase 3 führte genau das gewünschte Ledger ein. Buchungs-ID, Transaktion, Owner, Ziel,
vorzeichenbehaftete Deltas und eindeutige Gegenbuchung sind nun explizit; die alten Rewardfelder
sind aus den aktiven Tabellen entfernt. Bewusst verbleibt, dass `stats.xp`, Kombos und
Vorkommenszustand materialisierte Projektionen sind, ohne allgemeines Rebuild-/Repair-Werkzeug.

### Phase 4 teilweise behoben: Der Repository-Vertrag bleibt breit

`TaskRepository` vereinigt weiterhin Task-, Template-, Vorkommens-, Schritt-, Statistik-, Kombo-
und Ledgerzugriffe. Use Cases sehen dadurch mehr Operationen, als sie jeweils benötigen; der
vollständige In-Memory-Fake in Phase 7 machte diese Breite besonders sichtbar.

Phase 4 ersetzte die umständliche `Runnable`-Grenze durch `<T> T inTransaction(Transaction<T>)`
und bündelte Completion im `CompletionService`. Mutable Ein-Element-Rückgabecontainer sind
verschwunden. Kleinere Aggregate-Ports würden Testdoubles und Abhängigkeitsflächen weiter
reduzieren, wären aber ein weiterer struktureller Schnitt durch Room und alle Use Cases.

### Ausgangslage, in Phase 4 behoben: Termin und Reward waren zu eng gekoppelt

Der frühere `RewardEngine` war zugleich Reward-Service, Completion-State-Machine und
Schedule-Reconciler und lud für `reconcile` die vollständige Historie. Phase 4 ersetzte ihn durch
reinen `RewardCalculator`, `CompletionStateMachine`, `ScheduleProjector` und den transaktionalen
`CompletionService`. Die Terminprojektion liest nur frühestes offenes und jüngstes abgeschlossenes
Vorkommen. Bewusst bleibt die Orchestrierung synchron und repositorygebunden; ein späterer
Server-Sync bräuchte Konflikt- und Idempotenzregeln über die lokale Transaktion hinaus.

### Phase 4 verbessert: Fortlaufende Vorhaben verwenden einen expliziten Typ

Das Condition-Vorkommen behält aus Kompatibilitätsgründen eine synthetische ID und einen
Sondersortierwert, trägt seit Phase 4 aber `OccurrenceKind.CONDITION`. Fachlogik erkennt den Typ
nicht mehr über Stringpräfixe. ID und Sortierwert bleiben Persistence-Konventionen, die bei Export
oder Sync sauberer durch einen allgemeinen `CompletionRecord` ersetzt werden könnten.

## Verbleibende Schwächen in Domäne und Persistenz

### Phase 1 behoben: `done` war gleichzeitig Zustand und Ableitung

Die Ausgangslage speicherte `done`, überschieb es aber im Konstruktor, sobald die Zahl erfasster
Sätze der Planung entsprach. Das kollidierte mit explizitem Wiederöffnen.

Diese beiden Modelle kollidierten: Ein vollständig erfasster Schritt konnte nicht unabhängig von
seiner Satzanzahl offen sein; „wieder öffnen“ funktionierte nur, wenn zugleich Fortschritt
entfernt wurde. Umgekehrt konnte reines Speichern durch die Anzahl eine Buchung auslösen.

Phase 1 entschied sich für das zweite Modell: Bearbeiten bewahrt `done`; Finish und Reopen sind
explizite Commands, Reopen bewahrt Wiederholungen. Nur `ConfirmSet` darf beim neu hinzugefügten
letzten Satz einen Abschluss auslösen. Damit ist die Invariante heute eindeutig und idempotent.

### Phase 2 verbessert: Historische Schrittidentität bleibt teilweise unwiederbringlich

Neue Vorkommensschritte speichern seit Schema 6 immer `sourceTemplateId`. Für historische Daten
wird sie nur gesetzt, wenn `comboOwnerId` genau einem vorhandenen Template entspricht; andernfalls
bleibt sie `null`, während der Owner unverändert bleibt. Das ist ehrlicher als eine positionsbasierte
Erfindung, kann verlorene Vergangenheit aber naturgemäß nicht rekonstruieren. Tests halten diesen
Unknown-Owner-Fall fest.

### Phase 2 behoben: Legacy-Spalten vergrößerten jede Migration

Schema 6 nutzt den kontrollierten Tabellenneuaufbau und kopiert ausschließlich aktive Daten. Es
gibt keinen destruktiven Fallback. Die Altspalten verbleiben nur in historischen Schemaexports;
Downgrades werden weiterhin nicht unterstützt.

### Persistenztypen sind teilweise stringbasiert und verlieren Semantik

Datum, Enum, Satzliste und Owner-Art werden als Strings gespeichert. Das ist einfach und für Room
portabel, verlagert Validierung aber in Mapper und Konventionen. Besonders `actualRepetitions`
und `ownerId` sind strukturelle Daten in Textform. Eine normalisierte `occurrence_set`-Tabelle und
eine explizite Owner-Art würden gezielte Änderungen, Abfragen und Datenintegrität verbessern.

Für die aktuelle rein lokale App wäre eine vollständige Normalisierung möglicherweise zu viel.
Mindestens sollten Serialisierung und Owner-ID aber eigene Value Objects beziehungsweise Room-
Converter besitzen, damit Stringformate nicht durch UI, Migration und Domäne durchsickern.

## Verbleibende Schwächen in Presentation und UI

### Phase 5 behoben: ähnliche Dashboard-Modelle

Die Ausgangslage führte den Zustand über `Dashboard`, `DashboardState`, `DashboardUiModel`,
`TaskSnapshot`, `TaskStepSnapshot` und schließlich `WidgetUiModel`. `DashboardState` und
`DashboardUiModel` hielten beide `xp` und leiteten beide `XpProgress` neu ab. Fokuswahl und
Aufgabenlisten wurden ebenfalls an mehreren Stellen erneut interpretiert.

Diese Vervielfachung war beim Entfernen von `ringWeeks` konkret störend: derselbe alte Vertrag
musste aus mehreren Modellen, Mappern, Widgetklassen, Activity-Ereignissen, Strings und Fixtures
entfernt werden. Das ist genau die Art technischer Schuld, bei der eine kleine Fachänderung einen
breiten mechanischen Diff erzeugt.

`DashboardState` und `DashboardUiModel` wurden durch genau ein unveränderliches `TodayUiModel`
ersetzt. `DashboardUiMapper` berechnet `XpProgress`, Fokus, `harvestReady` und `undoAvailable`
einmal; Kalender und Widget bilden daraus kleine, explizite Projektionen. Bewusst verbleibt die
primitive, lange `TaskSnapshot`-Konstruktorsignatur. Benannte Builder oder kleinere Value Objects
wären der nächste sinnvolle Schritt, sind aber keine Voraussetzung mehr für korrekte Ableitungen.

### Phase 5 behoben: stringbasierte UI-Aktionen und Animationsziele

`TaskViewModel` baute Schlüssel wie `action:id`. `UiEvent` transportierte denselben String, und
`MainActivity.rewardSource` zerlegt ihn wieder, mappt Aktionsnamen auf View-Tags wie
`step:<id>`, `vessel:<id>` oder `reward-head` und sucht anschließend rekursiv im View-Baum.

Diese Kopplung ist weder vom Compiler noch durch eine gemeinsame Typdefinition geschützt.
Umbenennen einer Aktion kann dazu führen, dass die Fachaktion korrekt ausgeführt wird, aber die
Animation lautlos auf eine Fallbackkoordinate fliegt. IDs, Doppelpunkte und Sonderfälle für
fortlaufende Vorhaben werden zu einem inoffiziellen Protokoll.

`UiCommand`, `RewardEffect` und `RewardAnchorKey` bilden jetzt den gemeinsamen Vertrag.
`RewardAnchorRegistry` hält explizite, schwache View-Referenzen und verwirft dynamische Anchors
vor jedem Today-Bind. `RewardAnimator` benötigt weder Tags noch rekursive Hierarchiesuche.
Fallbackkoordinaten bleiben absichtlich erhalten, falls ein Effekt nach Navigation oder
Lifecycle-Wechsel keinen sichtbaren Anchor mehr besitzt.

### Phase 5 teilweise behoben: große UI-Klassen

In der Ausgangslage waren ihre Verantwortungen breit:

- `MainActivity` besitzt Lifecycle, Insets, Berechtigungen, Navigation, Dialoge, Editor-Mounting,
  Reward-Queue, Koordinatenauflösung und Animation;
- `TaskViewModel` besitzt Dashboardladen, Editorworkflow, Validierung, alle Taskcommands,
  Nebenläufigkeit, Running-Action-Deduplizierung, Fehler und Effekte;
- `FocusTaskView` baut Layout, bindet Domänenprojektionen, verwaltet expandierten Zustand,
  parst Satzlisten, validiert Eingaben, registriert Actions, synchronisiert Ebenen und animiert.

Diese Konzentration verlangsamte die Arbeit, weil eine Änderung am Rewardfluss gleichzeitig
Activity, ViewModel, View-Tags und konkrete Views berührte. Phase 5 hat `RewardAnimator`,
`TaskEditorCoordinator` und `SetProgressEditorView` extrahiert. `MainActivity` besitzt keine
Rewardkoordinaten, Effect-Serialisierung oder Editor-View mehr; `FocusTaskView` parst und
validiert keine Satzlisten mehr. Bewusst verbleibt der breite `TaskViewModel`, der
Dashboardladen, Commands, Vollbildeditor, Fehler und Nebenläufigkeit bündelt. Ein späterer
`TodayController` oder mehrere Stores wären sinnvoll, sobald weitere Screens oder parallele
Workflows hinzukommen. Nicht umgesetzt wurde deshalb insbesondere:

- ein `TodayController` beziehungsweise kleiner Store für Zustände und Commands;
- ein `FocusCardRenderer`, der keine Fachaktionen parst.

### Phase 5 verbessert: programmatisch gebaute Views und Testseams

Die rein native, programmatische Oberfläche passt zum bestehenden Stil und ermöglicht die
ungewöhnlichen Blattformen. Es fehlen aber stabile IDs und deklarative Hierarchie. Tests griffen
deshalb teilweise über `getChildAt(n)` auf Komponenten zu. Beim Entfernen der versteckten XP-
Zeile brach ein alter Charakterisierungstest lediglich deshalb, weil sich der Index der Timeline
verschoben hatte.

Zentrale Knoten von Shell, Header, Navigation, Fokus, Timeline und Satzeditor besitzen nun stabile
Ressourcen-IDs. Komponententests finden diese Knoten nicht mehr über feste Child-Indizes oder
Textinhalte; Rewards verwenden keine View-Tags. Traversierende Assertions bleiben dort bestehen,
wo bewusst alle Accessibility-Ziele oder Texte einer Komponente geprüft werden. Die Oberfläche
bleibt programmatisch und damit für große strukturelle Umbauten aufwendiger als eine deklarative
Hierarchie, aber ihre zentralen Testseams sind nicht mehr indexabhängig.

### Phase 5 behoben: Inline-Satzeditorzustand

`expandedStepId` lag in `FocusTaskView`. Bei Re-Rendering, Fokuswechsel oder
Konfigurationsänderung kann die Expansion verschwinden. Eingabewerte werden direkt aus einem
`EditText` gelesen und sind nicht Teil des ViewModel-Zustands. Das ist für eine kurze lokale
Interaktion akzeptabel, aber anfällig bei Rotation, Prozesswiederherstellung oder einem parallel
eintreffenden Dashboardrefresh.

`SetProgressEditorState` liegt jetzt immutable in `DashboardUiState`; Expansion, Entwurf und
Validierungsfehler werden beim Re-Render erneut gebunden. Die eigenständige
`SetProgressEditorView` rendert und meldet Zustand, besitzt ihn aber nicht. Der State überlebt den
View-Neuaufbau und einen Activity-Konfigurationswechsel über das ViewModel, jedoch noch keinen
vom System beendeten Prozess, weil er nicht im `SavedStateHandle` serialisiert wird.

### Phase 5 für Rewards behoben: konsumierbare mutable Events

Allgemeine Dialog- und Plattformereignisse besitzen weiterhin ein atomisches `consume()`-Flag.
Das verhindert normale Doppelzustellung,
mischt aber Ereignisinhalt und Zustellungszustand. Rewardanimationen werden erst nach dem neuen
Dashboardzustand veröffentlicht; ihre Quelle kann zu diesem Zeitpunkt schon aus dem View-Baum
verschwunden sein, weshalb Fallbackkoordinaten nötig sind.

Rewardanimationen sind davon getrennt: `RewardEffectQueue` dedupliziert über Transaktions-IDs,
hält FIFO-Reihenfolge und entfernt einen Effekt erst nach Acknowledgement des `RewardAnimator`.
Die Queue lebt im ViewModel und übersteht Activity-Neuerstellung. Bewusst verbleiben zwei Risiken:
Sie ist nicht prozesspersistent, und Dashboardzustand plus Effekt sind kein atomarer
zweiphasiger Presentation-Übergang. Dafür bleiben robuste Fallbackanchors nötig.

## Canvas-Renderer: gute Trennung, aber hoher technischer Preis

### Phase 6 behoben: Geometrie im UI-Thread

`WoodGrainGeometry` war bereits rein und separat testbar. In der Ausgangslage erzeugte
`WoodGrainView` beim ersten Cache-Miss jedoch SDF-Raster und Marching-Squares-Pfade innerhalb von
`onDraw` auf dem UI-Thread.

`WoodGrainRenderPipeline` baut nach Größen- oder Anchor-Änderungen auf zwei benannten
Worker-Threads. Gleiche Schlüssel teilen einen Build; publiziert werden immutable Pfade und ein
Generationstoken verhindert veraltete Lifecycle-Rückgaben. Ein Architekturtest hält SDF,
Marching Squares und Hierarchierekonstruktion aus `WoodGrainView`; wiederholtes `draw()` verändert
den Build-Zähler nicht. Der reproduzierbare Benchmark ist in
`docs/architecture/wood-grain-benchmark.md` dokumentiert.

### Phase 6 behoben: eintragsbasierter globaler Cache

Der alte statische `LruCache` hielt 16 `RenderData`-Objekte. Ein Eintrag konnte je nach Blattgröße und
Ringzahl sehr unterschiedlich viele Path-Segmente enthalten; 16 Einträge sind daher keine
brauchbare Speicherobergrenze. Der Cache ist außerdem pro Prozess global und kennt weder Theme-
noch Lifecycle-Grenzen. Die Geometrie ist zwar farbunabhängig, aber ein Größen-/Dichtewechsel kann
viele teure Einträge zurücklassen.

`WoodGrainRenderCache` gewichtet Pfade anhand ihrer Segmentzahl und verwirft im LRU-Verfahren,
bis die feste 4-MiB-Grenze eingehalten ist. Übergewichtige Einzelobjekte werden nicht gecacht.
Tests beweisen die Grenze; Benchmarkausgaben enthalten Cacheeinträge, geschätzte Bytes,
Buildzahl und beobachtetes Heapwachstum.

### Bewusst verbleibend: Textmasken und Halos erzwingen Software-Rendering

Für Fade-Masken und `BlurMaskFilter` setzt der Renderer einen Software-Layer; Text-Halos setzen
auch einzelne TextViews auf Software. Das löst die visuelle Anforderung verlässlich, kann aber
Speicher, Rasterkosten und Animationen verschlechtern.

Phase 6 hat eine hardwarefreundliche Variante ohne Software-Halo und Blur geprüft; sie veränderte
101.821 Pixel und verletzte damit die bestehende Null-Pixel-Toleranz. Deshalb bleiben diese
Effekte bewusst erhalten. Trotz dieser Restschuld wurden Draw-Median und p95 schneller. Ein
späterer Austausch erfordert einen explizit freigegebenen visuellen Vertrag und zusätzlich ein
reproduzierbares automatisiertes Performance-Budget in der vorgeschriebenen CI-Matrix.

### Phase 6 verbessert: Layoutkoordinaten am Renderer-Rand

Der Renderer summierte `left`/`top` entlang der Parent-Kette und korrigierte Scrollpositionen. Das
war nötig, weil Tau, Gefäß und Text in anderen Teilbäumen liegen als die Canvas-Ebene. Die Methode
ignoriert bewusst Rotations- und Skalierungsmatrizen, obwohl Karten und Effekte Rotation nutzen.
Für das aktuelle Zielbild ist die Näherung ausreichend; weitere Transformationen können Ringe
und Masken aber verschieben.

`WoodGrainView` und die Worker-Pipeline erhalten jetzt ausschließlich lokale `RectF`-Werte für
Anchors und Textmasken. Die View kennt keine Parent-Kette und keine fremden Views mehr. Bewusst
verbleibt `WoodGrainCoordinates` als Adapter, der die bisherige Layoutsemantik vor dem
Renderer-Rand einfriert; Rotations- und Skalierungsmatrizen werden weiter nicht neu
interpretiert, weil eine matrixbasierte Variante die Null-Pixel-Goldens änderte. Ein gemeinsamer
lokaler Layoutcontainer wäre langfristig sauberer.

## Testarchitektur nach Phase 7: deutlich schneller, mit bewussten Grenzen

### Phase 7 behoben: reine Fachabläufe benötigen weder Android noch Room

`InMemoryTaskRepository` führt Reward-, Completion-, Undo- und Schedule-Fälle als normale
JUnit-Tests aus. Die Kernfälle beweisen atomare Multi-Booking-Abschlüsse, exakte Gegenbuchung,
Wiederabschluss, Condition-Reopen, Transaktionsrollback und deterministisches Dashboardladen
unter Dauerlast. Room bleibt für Mapping, SQL-Querybudgets, echte Transaktions-/Constraint-
Semantik, Prozessneustart und Migrationen im Testbaum.

Negativ fiel auf, wie teuer der Fake wegen des sehr breiten `TaskRepository`-Ports wurde: Eine
einzige Testimplementierung muss Tasks, Templates, Vorkommen, Schritte, XP, Kombos, Ledger,
Sortierung, Cascades und Transaktionsrollback nachbilden. Das ist selbst neue technische Schuld.
Sie ist vertretbar, weil der Fake bewusst keine SQL-Eigenschaften vortäuscht und Room-
Integrationstests erhalten bleiben. Langfristig wären kleinere Ports oder Aggregate besser;
sonst können Sortier- oder Constraintsemantik zwischen Fake und Room auseinanderlaufen.

Nicht alle älteren Robolectric-Use-Case-Tests wurden mechanisch gelöscht. Einige verbinden
bewusst Service, Mapper und Repository oder prüfen konstante Queryzahlen. Ihre Existenz ist kein
Ersatz für die neuen reinen Tests, aber notwendige Integrationsabdeckung. Die Pyramide ist damit
verbessert, nicht dogmatisch auf nur eine Testart reduziert.

### Phase 7 behoben: historische Tabellenformen kommen aus den Room-Exports

`ExportedRoomSchemaFixture` liest Tabellen, Indizes, Views und Setup-Queries direkt aus den
eingecheckten Room-JSONs. Das lange, handgeschriebene Schema-1-SQL wurde entfernt; spezielle
Seed-Daten bleiben als lesbare Szenarien im Test. Die Kette 1→7 bis 6→7 läuft unter Robolectric
auf API 26 und 35, während `MigrationTestHelper` dieselben Exporte instrumentiert verwendet.

Gestört hat, dass Room für lokale JVM- und Instrumentationstests zwei verschiedene Hilfswege
anbietet. `MigrationTestHelper` steht nur im Android-Test-Setup zur Verfügung; Robolectric
brauchte daher einen kleinen JSON-Adapter und eine robuste Auflösung des Gradle-Arbeitsordners.
Ein einziges von Room bereitgestelltes, hostfähiges Fixture-API wäre weniger fehleranfällig.
Der neue Adapter vertraut außerdem dem Room-JSON-Format; bei einem Formatwechsel muss er bewusst
angepasst werden.

### Phase 7 verbessert: Golden-Fehler sind untersuchbar und CI-sicher

`GoldenAssertions` schreibt bei einem Fehler ein Tripel aus Expected, Actual und magentafarbenem
Diff; Actual wird für jeden Lauf abgelegt. Baseline-Updates erfordern eine explizite lokale
Umgebungsvariable und brechen in CI ab. Phone- und Widget-Toleranzen bleiben unverändert, und in
dieser Phase wurde keine Baseline aktualisiert.

Die Arbeit zeigte eine weitere Restschuld: Die statische, asynchrone Maserungspipeline und ihr
prozessweiter Cache können Testreihenfolgen beeinflussen. Ein kombinierter Golden-Lauf zeigte
einmal einen ausschließlich auf Maserungsringen liegenden Drift, der isoliert und im erneuten
Gesamtlauf nicht reproduzierbar war. Das Gate blieb anschließend mehrfach grün, trotzdem wäre
ein injizierbarer Pipeline-/Cache-Scope pro Test stabiler als globale Test-Hooks. Außerdem sind
große PNGs weiterhin schlecht im Textreview; die Artefakte verbessern Diagnose, ersetzen aber
kein menschliches visuelles Review.

### Phase 7 verbessert: feste Skalierungs- und Accessibility-Matrix

Today und der expandierte Inline-Editor werden bei 320, 412 und 600 dp jeweils mit Font Scale
1,0, 1,3 und 2,0 gemessen. Die Matrix prüft horizontale Grenzen, sichtbaren Text, 48-dp-Ziele,
Standardreihenfolge, Button-/Toggle-Rollen, Checked-State und Enter-Taste. Der Satzeditor stapelt
seine Aktionen bei schmaler Breite oder großer Schrift. Gezeichnete Tau- und Gefäßcontrols
besitzen explizite Accessibility-Rollen; doppelte Fokusziele auf Schrittlabel und Tau wurden
entfernt. Bestehende Tests decken Reduced Motion und die WCAG-Kontraste der Tagespaletten ab.

Negativ war, dass die programmgesteuerte View-Hierarchie keine semantische Struktur mitliefert.
Rollen, Reihenfolge, Fokus und Zustände mussten einzeln nachgerüstet werden; ein visuell
anklickbares Textlabel war zugleich ein zu kleines und doppeltes TalkBack-Ziel. Robolectric kann
Node-Metadaten und Tastaturverhalten beweisen, aber weder tatsächliche TalkBack-Ansagen noch
Switch-Access- oder OEM-Schriftverhalten vollständig simulieren. Instrumentierung und der
Accessibility-Scanner ergänzen deshalb die automatisierte Matrix; nicht automatisierbare
Beobachtungen sind dokumentierte Produktgrenzen und kein zusätzlicher Release-Gate.

### Phase 7 ergänzt Dauerlast, aber kein vollständiges Hardware-Performancebild

Ein deterministischer Dauerlastfall lädt 240 Tasks, 2.880 offene Schritte, 240 Kombos und 4.800
historische Vorkommen über den In-Memory-Port. Er besitzt ein großzügiges Zehn-Sekunden-Limit,
damit er algorithmische Ausreißer erkennt, ohne ein CI-Mikrobenchmark zu spielen. Querybudget-
Tests sichern die gebündelten Room-Abfragen; Phase 6 misst separat Renderer-Median, p95, Cache
und Heap.

Der Fake-Stresstest misst keine SQLite-I/O, Cursorallokation, Low-End-GPU oder reale
Frameverteilung. Ein reproduzierbarer Macrobenchmark auf einem fest definierten API-26-Gerät
wäre aussagekräftiger. Gewünscht wären außerdem Produktions-Telemetrie oder wenigstens ein
versioniertes, gerätebasiertes Budget statt ausschließlich hostabhängiger Tests.

## Was ich mir als Ausgangslage gewünscht hätte

- ein von Legacy-Fortschritt bereinigtes `Task`-Domänenmodell;
- ein unveränderliches Reward-Ledger statt verteilter XP-/Delta-Felder;
- einen typisierten Transaktionsport mit Rückgabewert;
- getrennte Komponenten für Completion, Reward und Schedule-Reconciliation;
- eine von Anfang an persistierte `sourceTemplateId` pro Vorkommensschritt;
- genau ein kanonisches Today-Read-Model mit kleinen App-/Widget-Projektionen;
- typisierte Commands und Reward-Effects statt zusammengesetzter Strings und View-Tags;
- einen schlankeren Lifecycle-Host sowie eigenständige Reward- und Satzeditor-Komponenten;
- stabile View-IDs und Testseams in der programmgesteuerten Oberfläche;
- native Designreferenzen für mehrere Geräte- und Accessibility-Konfigurationen;
- ein explizites Frame-/Speicherbudget für den Maserungsrenderer;
- schnelle In-Memory-Use-Case-Tests plus wenige gezielte Room-Integrationsfälle;
- wiederverwendbare historische Migrationsfixtures aus den exportierten Schemas;
- visuelle Diff-Artefakte für jede Golden-Änderung;
- eine Umsetzung in kleinen, vertikalen, jeweils grünen und reviewbaren Schritten.

## Priorisierte Maßnahmen und Umsetzungsstatus

1. **Erledigt in Phase 1 – Satzschritt-Invariante entscheiden und korrigieren.** Festlegen, ob `done` explizit oder
   abgeleitet ist; UI, Modell, Edit-Use-Case und Undo-Tests danach vereinheitlichen.
2. **Erledigt in Phase 2 – Legacy-Fortschritt aus der aktiven Domäne entfernen.** `RoutineProgress` aus `Task`, Mappern
   und aktuellen Tests lösen; Schema 6 als kontrollierten Tabellenneuaufbau planen.
3. **Erledigt in Phase 3 – Rewardbuchungen explizit modellieren.** Zunächst einen typisierten, vorzeichenbehafteten
   Buchungsdatensatz und Gegenbuchungen einführen; erst danach Statistiken oder Synchronisation
   auf den aktuellen Feldern aufbauen.
4. **Erledigt in Phase 4 – Completion-Orchestrierung schneiden.** Rewardberechnung, Vorkommenszustand und
   Terminprojektion separat testen und über einen generischen Transaktionsport verbinden.
5. **Erledigt in Phase 5 – Presentation-Verträge typisieren.** String-Aktionsschlüssel und View-Tag-Protokoll durch
   Commands, Effects und ein Anchor-Registry ersetzen; doppelten Dashboardzustand reduzieren.
6. **Teilweise erledigt in Phase 5 – große UI-Klassen zerlegen.** Rewardanimation, Satzeditor und Editor-Koordination aus
   `MainActivity`, `TaskViewModel` und `FocusTaskView` extrahieren; stabile IDs ergänzen.
7. **Erledigt in Phase 6 – Renderer messen, bevor er erweitert wird.** Framezeit, Speicher und Cache-Hit-Rate
   erfassen; Geometrie vorab berechnen und den Cache anhand realer Messwerte begrenzen.
8. **Erledigt in Phase 7 – Testpyramide beschleunigen.** In-Memory-Repository für Fachabläufe, gemeinsame
   Room-Migrationsfixtures und visuelle Diff-Artefakte einführen.
9. **Erledigt in Phase 7 – Skalierungs- und Accessibility-Matrix ergänzen.** Große Schrift, schmale Breite,
   TalkBack-Reihenfolge, Tastaturbedienung und alle Tagespaletten automatisiert prüfen.

## Schlussfolgerung

Die Implementierung ist für den aktuellen Produktumfang tragfähig und durch breite Tests
abgesichert. Phasen 1 bis 5 haben die zuvor fehlende Eindeutigkeit bei Satzabschluss,
Legacyfortschritt, Buchungen, Completion-Orchestrierung und Presentation-Verträgen beseitigt.
Die verbleibende Hauptschuld liegt jetzt klarer abgegrenzt im Software-Halo/Blur und globalen
Lifecycle des Maserungsrenderers, im breiten Repository-/Fake-Vertrag, in programmgesteuerter
Accessibility, in langen Snapshot-Konstruktoren und in fehlenden Geräte-Macrobenchmarks. Die
Room-lastige Kernschleife, dupliziertes Schema-SQL und die fehlende Skalierungsmatrix sind
behoben.

Weitere Reward-, Today- und Maserungsfeatures können auf den typisierten Verträgen und der
asynchronen Renderpipeline aufbauen. Prozesspersistente Presentation-Effects, kleinere Stores,
Builder für die langen Snapshot-Konstruktoren sowie ein explizit freigegebener Ersatz für die
Softwaremasken bleiben sinnvolle, aber bewusst nachrangige Verbesserungen.
