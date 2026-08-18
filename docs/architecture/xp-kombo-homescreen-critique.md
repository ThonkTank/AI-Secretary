# Architekturkritik von XP, Gefäß, Kombo und Today-Screen

Stand: 2026-08-18

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
- Phasen 4–7 bleiben offen.

## Umfang und Gesamturteil

Diese Kritik bezieht sich auf die mit Commit `1afb7fb0` bearbeiteten Bereiche: XP- und
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

### Die alte Wochenring-Architektur war nicht wirklich entfernt

Die sichtbaren `ringWeeks`-/Jahresring-Verträge konnten entfernt werden. Im Kernmodell trägt
`Task` aber weiterhin `RoutineProgress`; `TaskEntity`, `TaskEntityMapper` und alte Domänentests
kennen `routineLevel`, Vorkommens- und Wochenstreak weiterhin. Damit ist die alte Mechanik nicht
nur historisches Schema, sondern noch Teil jedes Task-Roundtrips und fast jeder Task-Kopie.

Das störte auf zwei Ebenen. Erstens musste bei jeder Konstruktor- oder Copy-Operation entschieden
werden, wie ein fachlich totes Objekt weitergereicht wird. Zweitens täuscht die Domäne vor, dass
zwei Belohnungssysteme gültig sind, obwohl nur `ComboProgress` Verhalten und UI steuert. Die ADR
erlaubt kompatible Altdaten im Schema, aber daraus folgt nicht, dass sie dauerhaft Bestandteil
des aktiven Domänenmodells bleiben müssen.

Gewünscht wäre eine klare Trennung gewesen: Legacy-Spalten bleiben ausschließlich in einer
Persistence-Entity oder werden in einer späteren Tabellenneuanlage entfernt; `Task` selbst kennt
nur noch das aktuelle Modell. Solange das nicht geschieht, besteht das Risiko, dass zukünftiger
Code versehentlich wieder auf den alten Fortschritt zugreift.

### Es gab keinen Buchungsbegriff, nur verteilte Zustandsfelder

Exaktes Undo verlangte einen Beleg darüber, was tatsächlich angewandt wurde. Dafür wurden
`awardedXp` und `comboPointDelta` auf Vorkommen sowie `earnedXp` und `comboPointDelta` auf
Vorkommensschritten gespeichert. `RewardReceipt` macht das Ergebnis eines Use Cases sichtbar.
Das erfüllt den aktuellen Bedarf, ist aber kein eigenständiges Buchungsmodell.

Die Bedeutung ist verteilt:

- Schritt-XP liegen am Schritt und sind zunächst nur Gefäßinhalt;
- Ernte-XP liegen am Vorkommen und verändern Gesamt-XP;
- beide heißen in `RewardReceipt` lediglich `xp` und werden über `Target` unterschieden;
- eine atomare Widget-Aktion führt Schrittbuchungen und Ernte aus, kann aber nur einen
  einzelnen Receipt zurückgeben;
- Reversal wird durch einen positiven XP-Wert plus `reversed` statt durch eine explizite
  Gegenbuchung beschrieben.

Das ist für UI-Animationen ausreichend, für Audit, spätere Statistiken, Synchronisation oder
mehrstufiges Undo aber zu implizit. Gewünscht wäre ein unveränderliches Reward-Ledger mit
Buchungs-ID, Aktion, Owner, Vorkommen, vorzeichenbehaftetem XP-/Punktedelta, Zeitpunkt und
optionaler Referenz auf die stornierte Buchung. Der aktuelle Vorkommenszustand könnte daraus
projiziert werden; Undo wäre eine Gegenbuchung statt einer spezialisierten Rückwärtsmutation.

### Der Repository-Vertrag ist breit und transaktionale Rückgaben sind umständlich

`TaskRepository` vereinigt Task-, Template-, Vorkommens-, Schritt-, Statistik- und Kombozugriffe.
Use Cases sehen dadurch mehr Persistenzoperationen, als sie jeweils benötigen. Die
Transaktionsgrenze lautet `void inTransaction(Runnable)`. Weil ein `Runnable` keinen Wert
zurückgeben kann, verwenden mehrere Use Cases veränderliche Ein-Element-Arrays, um
`RewardReceipt` aus einer Transaktion herauszutragen.

Das ist ein kleines, aber klares Zeichen, dass der Port nicht mehr zur Anwendungsschicht passt.
Ein generischer Vertrag wie `<T> T inTransaction(Supplier<T>)` oder dedizierte Aggregate-
Repositories würde Boilerplate entfernen und Transaktionsresultate typisieren. Noch besser wäre
ein `CompletionService`, der Vorkommen, Schritte, Kombo und Buchung als eine fachliche Einheit
lädt und speichert, statt die Orchestrierung über viele einzelne CRUD-Aufrufe zu verteilen.

### Termin-Neuberechnung und Belohnungsbuchung sind zu eng gekoppelt

`RewardEngine` berechnet XP und Kombo, verändert Persistenz, öffnet beziehungsweise schließt
Vorkommen indirekt und rekonstruiert anschließend durch `reconcile` Archivstatus,
`nextDueOn`, letzten Termin und letzten Abschluss. Für heutiges Undo ist diese Bündelung
praktisch, sie macht den Engine-Namen aber irreführend: Er ist zugleich Reward-Service,
Completion-Service und Schedule-Reconciler.

`reconcile` lädt dafür alle Vorkommen einer Aufgabe und leitet den Zustand aus ihnen neu ab. Das
ist robust für die kleine lokale Datenmenge, skaliert aber linear mit der Historie und erschwert
gezielte Tests der Terminlogik. Belohnung, Vorkommenszustandsautomat und Terminprojektion sollten
separate fachliche Komponenten mit einem gemeinsamen transaktionalen Orchestrator sein.

### Fortlaufende Vorhaben wurden über ein synthetisches Vorkommen angepasst

Das Schließen eines fortlaufenden Vorhabens ohne offenes Vorkommen erzeugt ein Vorkommen mit einer
stringkodierten ID wie `condition:<task>:<datum>` und maximalem Sortierwert. Damit kann derselbe
Ernte-/Undo-Pfad wiederverwendet werden, und der Ansatz vermeidet eine zweite Belegform. Er zeigt
aber, dass das vorhandene Vorkommensmodell die Aktion „Bedingung erfüllt“ nicht natürlich
ausdrückt.

Stringpräfix und Sonder-Sortierwert sind verborgene Typinformation. Ein expliziter
`OccurrenceKind` oder ein allgemeiner `CompletionRecord` würde diese Semantik sichtbar machen.
Ohne ihn müssen zukünftige Exporte, Synchronisation oder Auswertungen die Konvention kennen.

## Verbleibende Schwächen in Domäne und Persistenz

### `done` ist beim Satzschritt gleichzeitig Zustand und Ableitung

`OccurrenceStep` speichert `done`, setzt es im Konstruktor aber automatisch auf wahr, sobald die
Anzahl tatsächlicher Sätze der Planung entspricht. `EditStepProgress` besitzt gleichzeitig einen
expliziten gewünschten Erledigtzustand, und die UI bietet „wieder öffnen“ an.

Diese beiden Modelle kollidieren. Ein vollständig erfasster Schritt kann nicht unabhängig von
seiner Satzanzahl offen sein; „wieder öffnen“ funktioniert fachlich nur, wenn zugleich Fortschritt
entfernt wird. Umgekehrt kann Speichern allein durch die Anzahl eine Buchung auslösen. Die
Implementierung deckt frühes Fertigstellen und Fortschrittsänderung ab, aber die Invariante ist
nicht klar genug, um alle Kombinationen intuitiv zu erklären.

Gewünscht wäre eine Entscheidung für genau eines der Modelle:

1. `done` ist vollständig abgeleitet; dann gibt es kein unabhängiges „wieder öffnen“, sondern nur
   das Entfernen eines Satzes.
2. `done` ist ein expliziter Zustandsautomat; dann darf der Konstruktor ihn nicht aus der Anzahl
   überschreiben, und „Plan erfüllt“ ist nur ein UI-Vorschlag oder ein expliziter Command.

Das zweite Modell passt besser zu „früher fertig“ und zu exakt reversierbaren Buchungen.

### Stabile Schrittidentität ist in der Migration nur heuristisch rekonstruierbar

Schema 4 kannte am Vorkommensschritt keine Template-ID. Migration 4→5 sucht deshalb über
Task-ID und Position den damaligen Template-Schritt und fällt sonst auf die konkrete
Vorkommensschritt-ID zurück. Das ist eine vernünftige Best-Effort-Migration, aber nicht in allen
historischen Fällen stabil: gelöschte, eingefügte oder umsortierte Templates können Positionen
mehrdeutig machen.

Ein dauerhaftes `sourceTemplateId` hätte beim Erzeugen des Vorkommens von Anfang an persistiert
werden müssen. Für vorhandene Daten lässt sich die verlorene Information nicht vollständig
zurückholen. Die Einschränkung sollte als Migrationsannahme dokumentiert und mit Fixtures für
gelöschte beziehungsweise verschobene Templates sichtbar gemacht werden.

### Legacy-Spalten vergrößern jede zukünftige Migration

Schema 5 trägt aktuelle Rewarddaten und weiterhin alte Routine-Level-/Streak-Spalten. Room
vergleicht die gesamte Tabellenform, weshalb jede weitere Migration diese Altlast mitführen oder
die `tasks`-Tabelle kontrolliert neu aufbauen muss. Ein Schema 6 sollte die Gelegenheit nutzen,
die Tabelle mit nur aktiven Spalten neu anzulegen und Daten explizit zu kopieren. Vorher ist zu
prüfen, ob irgendein Release noch einen Downgrade- oder Exportvertrag für die alten Werte besitzt.

### Persistenztypen sind teilweise stringbasiert und verlieren Semantik

Datum, Enum, Satzliste und Owner-Art werden als Strings gespeichert. Das ist einfach und für Room
portabel, verlagert Validierung aber in Mapper und Konventionen. Besonders `actualRepetitions`
und `ownerId` sind strukturelle Daten in Textform. Eine normalisierte `occurrence_set`-Tabelle und
eine explizite Owner-Art würden gezielte Änderungen, Abfragen und Datenintegrität verbessern.

Für die aktuelle rein lokale App wäre eine vollständige Normalisierung möglicherweise zu viel.
Mindestens sollten Serialisierung und Owner-ID aber eigene Value Objects beziehungsweise Room-
Converter besitzen, damit Stringformate nicht durch UI, Migration und Domäne durchsickern.

## Verbleibende Schwächen in Presentation und UI

### Zu viele ähnliche Dashboard-Modelle können auseinanderlaufen

Der Zustand läuft über `Dashboard`, `DashboardState`, `DashboardUiModel`, `TaskSnapshot`,
`TaskStepSnapshot` und schließlich `WidgetUiModel`. `DashboardState` und `DashboardUiModel`
halten beide `xp` und leiten beide `XpProgress` neu ab. Fokuswahl und Aufgabenlisten werden
ebenfalls an mehreren Stellen erneut interpretiert.

Diese Vervielfachung war beim Entfernen von `ringWeeks` konkret störend: derselbe alte Vertrag
musste aus mehreren Modellen, Mappern, Widgetklassen, Activity-Ereignissen, Strings und Fixtures
entfernt werden. Das ist genau die Art technischer Schuld, bei der eine kleine Fachänderung einen
breiten mechanischen Diff erzeugt.

Gewünscht wäre ein kanonisches, unveränderliches `TodayUiModel`, aus dem App und Widget jeweils
bewusst kleine Projektionen erzeugen. Abgeleitete Werte wie `XpProgress`, `undoAvailable` und
Fokus sollten genau einmal berechnet werden. Konstruktoren mit vielen primitiven Parametern
sollten durch benannte Builder oder kleine Value Objects ersetzt werden.

### UI-Aktionen und Animationsziele sind stringly typed

`TaskViewModel` baut Schlüssel wie `action:id`. `UiEvent` transportiert denselben String, und
`MainActivity.rewardSource` zerlegt ihn wieder, mappt Aktionsnamen auf View-Tags wie
`step:<id>`, `vessel:<id>` oder `reward-head` und sucht anschließend rekursiv im View-Baum.

Diese Kopplung ist weder vom Compiler noch durch eine gemeinsame Typdefinition geschützt.
Umbenennen einer Aktion kann dazu führen, dass die Fachaktion korrekt ausgeführt wird, aber die
Animation lautlos auf eine Fallbackkoordinate fliegt. IDs, Doppelpunkte und Sonderfälle für
fortlaufende Vorhaben werden zu einem inoffiziellen Protokoll.

Ein typisiertes `RewardEffect` mit `SourceKind`, `sourceId`, `TargetKind` und Buchungswert wäre
deutlich sicherer. Die gerenderte Oberfläche sollte ein kleines `RewardAnchorRegistry` führen,
statt den allgemeinen View-Baum über String-Tags zu durchsuchen.

### `MainActivity`, `TaskViewModel` und `FocusTaskView` sind überladen

Nach der Änderung haben die drei Klassen ungefähr 469, 451 und 462 Zeilen. Ihre Verantwortungen
sind breit:

- `MainActivity` besitzt Lifecycle, Insets, Berechtigungen, Navigation, Dialoge, Editor-Mounting,
  Reward-Queue, Koordinatenauflösung und Animation;
- `TaskViewModel` besitzt Dashboardladen, Editorworkflow, Validierung, alle Taskcommands,
  Nebenläufigkeit, Running-Action-Deduplizierung, Fehler und Effekte;
- `FocusTaskView` baut Layout, bindet Domänenprojektionen, verwaltet expandierten Zustand,
  parst Satzlisten, validiert Eingaben, registriert Actions, synchronisiert Ebenen und animiert.

Diese Konzentration verlangsamte die Arbeit, weil eine Änderung am Rewardfluss gleichzeitig
Activity, ViewModel, View-Tags und konkrete Views berührte. Sinnvolle Extraktionen wären:

- ein `TodayController` beziehungsweise kleiner Store für Zustände und Commands;
- ein eigener `RewardAnimator` mit typisierten Anchors;
- ein `TaskEditorCoordinator` außerhalb der Activity;
- eine eigenständige `SetProgressEditorView` mit Eingabemodell und Validator;
- ein `FocusCardRenderer`, der keine Fachaktionen parst.

### Programmatisch gebaute Views erschweren Strukturtests und Wartung

Die rein native, programmatische Oberfläche passt zum bestehenden Stil und ermöglicht die
ungewöhnlichen Blattformen. Es fehlen aber stabile IDs und deklarative Hierarchie. Tests griffen
deshalb teilweise über `getChildAt(n)` auf Komponenten zu. Beim Entfernen der versteckten XP-
Zeile brach ein alter Charakterisierungstest lediglich deshalb, weil sich der Index der Timeline
verschoben hatte.

Stabile View-IDs, kleine Komponentenverträge und Test-Selektoren würden solche Fehler vermeiden.
XML oder Compose ist nicht zwingend erforderlich; auch programmgesteuerte Views können ihre
wichtigen Knoten benennen und über Methoden zugänglich machen. Die aktuelle Mischung aus
Child-Indizes, rekursiver Textsuche und Tags macht Tests unnötig abhängig von der Implementierung.

### Expandierter Satzeditorzustand lebt nur in der View

`expandedStepId` liegt in `FocusTaskView`. Bei Re-Rendering, Fokuswechsel oder
Konfigurationsänderung kann die Expansion verschwinden. Eingabewerte werden direkt aus einem
`EditText` gelesen und sind nicht Teil des ViewModel-Zustands. Das ist für eine kurze lokale
Interaktion akzeptabel, aber anfällig bei Rotation, Prozesswiederherstellung oder einem parallel
eintreffenden Dashboardrefresh.

Gewünscht wäre ein kleiner `SetProgressEditorState` im Presentation-State mit Entwurf,
Validierungsfehler und explizitem Speichern/Verwerfen. Damit ließen sich UI und Regeln ohne
View-Hierarchie testen.

### Einmalige UI-Effekte bleiben konsumierbare mutable Events

`UiEvent` besitzt ein atomisches `consume()`-Flag. Das verhindert normale Doppelzustellung,
mischt aber Ereignisinhalt und Zustellungszustand. Rewardanimationen werden erst nach dem neuen
Dashboardzustand veröffentlicht; ihre Quelle kann zu diesem Zeitpunkt schon aus dem View-Baum
verschwunden sein, weshalb Fallbackkoordinaten nötig sind.

Eine explizite Effect-Queue im ViewModel/Store mit IDs und Acknowledgement würde Reihenfolge und
Prozesswiederherstellung klarer machen. Für visuelle Effekte wäre außerdem ein zweiphasiger
Übergang hilfreich: alter Renderzustand plus Effekt, danach bestätigter neuer Zustand. Die
aktuelle `RewardAnimationQueue` serialisiert nur das Abspielen innerhalb der Activity, nicht die
gesamte Zustands-/Effekttransaktion.

## Canvas-Renderer: gute Trennung, aber hoher technischer Preis

### Geometrie wird synchron auf dem UI-Thread erzeugt

`WoodGrainGeometry` ist erfreulich rein und separat testbar. `WoodGrainView` erzeugt beim ersten
Cache-Miss jedoch SDF-Raster und Marching-Squares-Pfade innerhalb von `onDraw`. Das geschieht auf
dem UI-Thread. Die Rasterweite von drei beziehungsweise vier dp begrenzt die Kosten, garantiert
aber kein Framebudget auf langsamen API-26-Geräten oder bei vielen verschiedenen Layoutgrößen.

Es fehlen Mikrobenchmark, Macrobenchmark und Frame-Metriken. Die Goldens beweisen Aussehen, nicht
Latenz. Geometrie sollte nach `onSizeChanged` beziehungsweise Anchor-Änderung vorab berechnet
werden, bei Bedarf auf einem Worker, und anschließend nur noch als unveränderlicher Pfad im Draw-
Schritt vorliegen.

### Der globale Cache zählt Einträge statt Speicher

Der statische `LruCache` hält 16 `RenderData`-Objekte. Ein Eintrag kann je nach Blattgröße und
Ringzahl sehr unterschiedlich viele Path-Segmente enthalten; 16 Einträge sind daher keine
brauchbare Speicherobergrenze. Der Cache ist außerdem pro Prozess global und kennt weder Theme-
noch Lifecycle-Grenzen. Die Geometrie ist zwar farbunabhängig, aber ein Größen-/Dichtewechsel kann
viele teure Einträge zurücklassen.

Gewünscht wäre ein bytegewichteter Cache mit messbarer Obergrenze und Kennzahlen für Hit-Rate,
Buildzeit und Speicher. Alternativ könnte jede Karte nur ihren letzten Pfad halten, wenn reale
Messungen zeigen, dass globale Wiederverwendung gering ist.

### Textmasken und Halos erzwingen Software-Rendering

Für Fade-Masken und `BlurMaskFilter` setzt der Renderer einen Software-Layer; Text-Halos setzen
auch einzelne TextViews auf Software. Das löst die visuelle Anforderung verlässlich, kann aber
Speicher, Rasterkosten und Animationen verschlechtern. Ohne Profiling ist unklar, ob der Effekt
auf Zielgeräten günstig genug ist.

Ein besseres Handoff hätte erlaubt, Halo und Fade als toleranzbehaftete Effekte zu behandeln.
Dann könnten vorberechnete Masken, Clipping ohne Blur oder hardwarefreundliche Overlays geprüft
werden. Mindestens sollte ein Performance-Gate auf API 26 und einem schwachen Referenzgerät
existieren.

### Layoutkoordinaten werden manuell rekonstruiert

Der Renderer summiert `left`/`top` entlang der Parent-Kette und korrigiert Scrollpositionen. Das
war nötig, weil Tau, Gefäß und Text in anderen Teilbäumen liegen als die Canvas-Ebene. Die Methode
ignoriert bewusst Rotations- und Skalierungsmatrizen, obwohl Karten und Effekte Rotation nutzen.
Für das aktuelle Zielbild ist die Näherung ausreichend; weitere Transformationen können Ringe
und Masken aber verschieben.

Besser wäre ein gemeinsamer lokaler Koordinatenraum oder `ViewGroup`-Layout, das dem Renderer
fertige Anchor-Rechtecke übergibt. Dann wäre Geometrie unabhängig vom allgemeinen Android-
View-Baum und dessen Transformationsdetails.

## Testarchitektur: breit, aber teilweise langsam und fragil

### Zu viele Fachtests benötigen Android beziehungsweise Room

Die wichtigsten Komboregeln sind rein getestet. Viele End-to-End-Regeln liegen dennoch in
Robolectric-Tests mit echter Room-Datenbank. Das gibt wertvolle Integrationssicherheit, macht den
vollständigen Unit-Lauf aber deutlich langsamer und erschwert eine feine Fehlerlokalisierung.

Ein kleiner In-Memory-`TaskRepository`-Fake würde Reward-, Undo- und Schedule-Szenarien als
schnelle JVM-Tests erlauben. Room-Tests müssten dann nur Mapping, Queries, Transaktionen und
Migration beweisen. Diese Trennung hätte die Implementationsschleife erheblich beschleunigt.

### Migrationsfixtures duplizieren historische Schemata als SQL

Die Robolectric-Migrationstests bauen alte Tabellen von Hand auf; der Instrumentationstest
prüft Teile davon nochmals mit exportierten Schemas. Handgeschriebenes historisches SQL ist
lang, schwer zu vergleichen und kann vom tatsächlich veröffentlichten Schema abweichen.

Gewünscht wäre ein Fixture-Register auf Basis der exportierten Room-Schemas und
`MigrationTestHelper`: Ausgangsversion, Seed-Daten, Zielassertionen. Dieselben Fixtures sollten
für API 26 und 35 wiederverwendet werden. Spezialfälle wie umsortierte oder fehlende
Schritttemplates sollten eigene Seeds besitzen.

### Goldens sind wertvoll, aber als große Binärdateien schwer zu reviewen

Die Phone-Goldens decken leer, teilweise gefüllt, erntereif, geerntet, dreistellige Werte sowie
Tag, Abend und Nacht ab; die unveränderten Widget-Goldens schützen die bewusste visuelle Grenze.
Das war beim Umbau sehr hilfreich. Gleichzeitig sind die PNG-Diffs groß und in normalem Git-
Review kaum aussagekräftig. Ein versehentlich breit aktualisierter Baseline-Satz kann echte
Regressionen verdecken.

Es fehlen automatisch erzeugte Before/After/Diff-Artefakte, ein Änderungsprotokoll pro Golden
und Referenzen für Font Scale/kleine Breite. Der Update-Modus sollte Baselines in CI nie
überschreiben können, und geänderte Goldens sollten als visuelles Review-Artefakt veröffentlicht
werden.

### Accessibility wird punktuell, nicht systematisch geprüft

48-dp-Touchflächen und zentrale Content Descriptions besitzen Tests. Nicht vollständig geprüft
sind TalkBack-Reihenfolge, Rollen/Zustände, große Schrift, Kontrast aller Tagespaletten und die
Bedienbarkeit des Inline-Editors mit Tastatur. Besonders ein gezeichnetes Gefäß braucht eine
semantische Zustandsbeschreibung, die über einen einzelnen String hinaus langfristig konsistent
bleibt.

Ein Accessibility-Testkatalog pro interaktivem Zustand und mindestens ein instrumentierter
Accessibility-Scanner würden die aktuellen punktuellen Assertions ergänzen.

### Es fehlt ein Performance- und Dauerlast-Gate

Kein Test misst Renderzeit, Cacheverbrauch, wiederholte Rewardaktionen, sehr lange Historien oder
viele Tasks/Schritte. Die Funktionstests beweisen korrekte Einzelabläufe, nicht das Verhalten nach
hunderten Vorkommen oder schnellen Aktionsfolgen. Gerade `reconcile`, vollständige Dashboard-
Loads, globale Comboscans und Canvas-Cache sollten mit realistischen Mengen gemessen werden.

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

## Priorisierte nächste Schritte

1. **Satzschritt-Invariante entscheiden und korrigieren.** Festlegen, ob `done` explizit oder
   abgeleitet ist; UI, Modell, Edit-Use-Case und Undo-Tests danach vereinheitlichen.
2. **Legacy-Fortschritt aus der aktiven Domäne entfernen.** `RoutineProgress` aus `Task`, Mappern
   und aktuellen Tests lösen; Schema 6 als kontrollierten Tabellenneuaufbau planen.
3. **Rewardbuchungen explizit modellieren.** Zunächst einen typisierten, vorzeichenbehafteten
   Buchungsdatensatz und Gegenbuchungen einführen; erst danach Statistiken oder Synchronisation
   auf den aktuellen Feldern aufbauen.
4. **Completion-Orchestrierung schneiden.** Rewardberechnung, Vorkommenszustand und
   Terminprojektion separat testen und über einen generischen Transaktionsport verbinden.
5. **Presentation-Verträge typisieren.** String-Aktionsschlüssel und View-Tag-Protokoll durch
   Commands, Effects und ein Anchor-Registry ersetzen; doppelten Dashboardzustand reduzieren.
6. **Große UI-Klassen zerlegen.** Rewardanimation, Satzeditor und Editor-Koordination aus
   `MainActivity`, `TaskViewModel` und `FocusTaskView` extrahieren; stabile IDs ergänzen.
7. **Renderer messen, bevor er erweitert wird.** API-26-Framezeit, Speicher und Cache-Hit-Rate
   erfassen; Geometrie vorab berechnen und den Cache anhand realer Messwerte begrenzen.
8. **Testpyramide beschleunigen.** In-Memory-Repository für Fachabläufe, gemeinsame
   Room-Migrationsfixtures und visuelle Diff-Artefakte einführen.
9. **Skalierungs- und Accessibility-Matrix ergänzen.** Große Schrift, schmale Breite,
   TalkBack-Reihenfolge, Tastaturbedienung und alle Tagespaletten automatisiert prüfen.

## Schlussfolgerung

Die Implementierung ist für den aktuellen Produktumfang tragfähig und durch ungewöhnlich breite
Tests abgesichert. Ihre größte Schwäche ist nicht eine einzelne Klasse, sondern fehlende
Eindeutigkeit an den Grenzen: alter und neuer Fortschritt koexistieren, Buchungen sind über
Zustandsobjekte verteilt, `done` ist Zustand und Ableitung zugleich, Presentation kommuniziert
über Strings und der Renderer rekonstruiert Layoutwissen aus Views.

Weitere Features sollten deshalb nicht einfach auf die vorhandenen Pfade aufgesetzt werden.
Zuerst sollten Satzinvariante, Legacy-Modell und Buchungsbegriff bereinigt werden. Danach lohnt
sich die Entkopplung von Presentation und Renderer. So wird aus einer gut getesteten
pragmatischen Erweiterung eine Architektur, die auch weitere Belohnungsregeln, Synchronisation
oder komplexere Today-Interaktionen ohne erneuten Big-Bang tragen kann.
