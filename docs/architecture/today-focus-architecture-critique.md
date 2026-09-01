# Architekturkritik der Today-/Fokus-Bereiche

Stand: 2026-08-21, nach Abschluss der Bereinigungsroadmap

## Gesamturteil

Die acht UX-Fehler waren keine voneinander unabhängigen Viewdefekte. Sie entstanden aus vier
strukturellen Mehrfachwahrheiten: Geometrie wurde in mehreren Ebenen rekonstruiert, Rewards
wurden an mehreren Stellen berechnet, Today-Inhalte überlappten in generischen Snapshots und
Reorder besaß gleichzeitig View-, ViewModel- und Repositoryzustand. Das machte kleine sichtbare
Korrekturen riskant und erklärte, warum lokale Patches zuvor neue Randfälle erzeugten.

Nach dem Refactor sind die wichtigsten Grenzen compiler- oder typgesichert. Das Ergebnis ist
wesentlich besser, aber die Android-App bleibt ein großer Composition Root. Vor allem
`TaskViewModel`, programmatische Views, Room-Integration und manuelle Widgetinvalidierung
begrenzen weiterhin, wie lokal zukünftige Änderungen bleiben.

## Was Planung und Implementierung erschwert hat

### Geometrie hatte keinen eindeutigen Owner

Header, Fokuskarte, Timelineblatt und Grain-Layer kannten jeweils eigene Radien, Offsets,
Pivots oder `post()`-Korrekturen. Ein Ring konnte mathematisch richtig für eine Ebene und
sichtbar falsch für die transformierte Vorderseite sein. Tests mussten zunächst klären, welche
Pixel tatsächlich Vertragsbestandteil waren. Gewünscht gewesen wäre von Anfang an ein
Leaf-Primitive, das Form, Clip, lokale Koordinaten und Transformation gemeinsam besitzt.

`LeafShape`, `LeafSurface` und `GrainSpec` lösen das. Negativ bleibt, dass die imperative
Viewhierarchie Layoutbounds erst spät kennt; asynchrones Grain-Rendering benötigt deshalb
weiterhin spezielle Synchronisationspunkte und einen Cachevertrag.

### Rewarddaten waren scheinbar redundant, aber semantisch verschieden

Grundwert, Kombostufe, Faktor, gerundeter Endwert und bereits eingesammelte XP wurden als
unabhängige primitive Zahlen weitergereicht. Dadurch war nicht erkennbar, ob ein Tau gerade den
Grund- oder Endwert zeigte. Die Einführung von `RewardBreakdown` war klein, verlangte aber eine
breite Call-Site-Prüfung, weil bestehende Tests teils visuelle Grain-Stufen mit fachlichen
Kombostufen vermischten.

Gewünscht wäre ein fachlicher Rewardwert seit dem ersten Ledgerentwurf gewesen. Die heutige
Trennung ist belastbar; historische Debugfixtures behalten bewusst einen separaten visuellen
Grain-Level und bleiben damit eine kleine Ausnahme.

### Today-Modelle waren überladen und überlappend

`TaskSnapshot`, allgemeine Tasklisten, Fokus, Timeline und History konnten dieselbe Aufgabe
mehrfach repräsentieren. Referenzidentität wurde stellenweise wie fachliche Identität behandelt.
Das erschwerte insbesondere den Filter „erledigt gehört nur in die Tageshistorie“. Lange
Konstruktoren machten Fixtureänderungen mechanisch groß und vertauschte Werte schwer sichtbar.

Verbraucherspezifische Modelle und Builder beseitigen diese Mehrdeutigkeit. Einige Mapper im
App-Modul bleiben dennoch breit, weil sie Domain, Texte, Kalender und mehrere Consumer in einem
Refresh zusammensetzen. Ein eigener App-Presentation-Adapter pro Consumer wäre noch lokaler,
würde aktuell aber zusätzliche Verdrahtung ohne neuen Nutzervertrag erzeugen.

### Reorder war ein verteilter, impliziter Zustandsautomat

Die View hielt kanonische und temporäre Reihenfolge, entschied Dropsemantik und konnte direkt
Persistenz auslösen. Accessibility und Drag verwendeten ähnliche, aber getrennte Wege. Fehler,
Duplicate Drop und Refresh während einer Vorschau waren dadurch schwer beweisbar.

Der explizite `TodayCoordinator` mit `IDLE`, `DRAGGING` und `PERSISTING` war die wichtigste
strukturelle Korrektur. Seine Einführung wurde durch alte Dashboardevent-Wrapper und
ViewModel-Switches verlangsamt. Gewünscht wäre ein geschlossener Action-/Commandvertrag bereits
vor der ersten Drag-Implementierung gewesen.

### Der breite Repositoryvertrag verteuerte jede Use-Case-Änderung

Ein Repository bündelte Definitionen, Zeitplan, Occurrences, Schritte, Statistik, Combo,
Ledger und Materialisierung. Kleine Use Cases konnten dadurch unbeabsichtigt alles lesen oder
schreiben; Testdoubles mussten fachfremde Methoden implementieren. Erst die Zerlegung zeigte
klar, dass Reorder nur Snapshot und Positionswrites, Completion dagegen Execution und Ledger
benötigt.

Die Trennung von `StepExecutionService` und `OccurrenceCompletionService` verbessert Testbarkeit
und Transaktionssicht. Die spätere Storage-Konsolidierung hat die damalige breite Room-Klasse
durch fünf fachliche Ports, fünf DAOs und fünf einportige Adapter ersetzt; portübergreifende
Atomizität besitzt nur noch der separate `RoomTransactionRunner`.

### Paketregeln waren vor der Modultrennung nur nachträgliche Warnungen

Importscans konnten unerlaubte Abhängigkeiten melden, aber nicht verhindern. Das Root-Paket
verschleierte zusätzlich, ob eine Klasse Android-UI, Presentation oder Fachlogik war. Die
physische Extraktion nach `:core-domain` und `:today-core` war deshalb wertvoll, deckte aber auch
auf, dass harmlose AndroidX-Nullability-Annotationen und UI-nahe Formatter echte
Compilergrenzen blockierten.

Gewünscht wäre eine Modulstruktur vor dem Wachstum des Features gewesen. Positiv ist, dass die
Extraktion nach stabilisierten Paketgrenzen ohne Produkt- oder Schemaänderung möglich war.

### Tests waren stark, aber teilweise an Implementierungsdetails gekoppelt

Reflection, Rendergetter und lokale Eventfassaden beschleunigten frühe Tests, machten spätere
Refactors jedoch teurer. Goldens waren unverzichtbar, um Geometrieänderungen zu erkennen, aber
bei reinen Paketmoves lieferten sie wenig zusätzliche Diagnose. Der neue öffentliche
Action-/Gesture-Pfad und komponentenbezogene Goldens sind besser austariert.

Echte Instrumentierung bleibt lokal von einem Emulator oder Gerät abhängig. Die Test-APK
kompiliert, und CI besitzt API-26-/API-35-Jobs; ohne verbundenes Ziel darf der lokale Status
nicht als ausgeführter Gerätetest bezeichnet werden.

## Verbleibende technische Schuld, priorisiert

1. `TaskViewModel` ist weiterhin ein breiter Android-Orchestrator für Today, Editor, Kalender,
   Updates und Widgeteffekte. Die fachliche Dispatchkette ist entfernt, die Lebenszyklus- und
   Fehlerbehandlung aber noch zentralisiert.
2. `DashboardPresenter`/`DashboardUiMapper` bilden weiterhin einen großen Projektionsrand. Mehr
   unabhängige Read Models könnten Refreshes weiter verkleinern.
3. Widgetinvalidierung bleibt explizite Aufrufdisziplin. Weitere Schreiber würden eine
   datengetriebene Invalidierung oder Outbox rechtfertigen.
4. `OccurrenceStep.done` und normalisierte Wiederholungsergebnisse sind redundante
   Zustandsanteile, deren Konsistenz durch Use Cases und Mapper statt durch eine vollständige
   Datenbankinvariante geschützt wird.
5. Die historische Legacyspalte für Wiederholungstext bleibt bis zu einem separat definierten
   Kompatibilitätsfenster bestehen.
6. Programmatische Views erzeugen viel manuelle Mess-, Fokus- und Accessibilitylogik. Eine
   deklarative UI wäre langfristig günstiger, ist aber bewusst außerhalb dieser Roadmap.
7. Native-Graphics-Goldens und Mikrobenchmarks reagieren auf Host-, JDK- und Renderervarianz.
   Absolute Budgets und Pixelverträge sind aussagekräftiger als winzige relative Zeitdifferenzen.
8. Instrumentation kann lokal ohne stabiles ADB-Ziel nicht ausgeführt werden; CI bleibt für die
   beiden API-Stufen die verbindliche Geräteinstanz.

## Was für zukünftige Arbeit besser vorbereitet sein sollte

- ein kleiner eigener Today-Application-Adapter statt weiterer Handler im `TaskViewModel`;
- getrennte, inkrementell beobachtbare Read Models für Today, Kalender und Widget;
- ein dokumentiertes Removal-Gate für Legacy-Wiederholungsdaten;
- ein fest verfügbarer Emulator- oder Gerätepool für lokale Accessibility-/Recreationtests;
- visuelle Spezifikationen mit semantischen Anchors statt ausschließlich PNG-Vergleich;
- Performancebudgets mit absoluter Untergrenze, damit Mikrosekundenrauschen nicht als
  prozentuale Regression fehlinterpretiert wird.

Der entscheidende Gewinn ist nicht weniger Code, sondern weniger plausible Orte für dieselbe
Wahrheit. Neue Today-Funktionalität muss nun durch Action, Reducer, Command, fokussierten Use
Case und Capability-Port laufen; Geometrie und Rewardwerte haben jeweils genau einen Owner.
