# Architekturkritik der bearbeiteten Today-/Fokus-Bereiche

Stand: 2026-08-20, nach den Phasen 0 bis 8

## Gesamturteil

Der Refactor hat die riskantesten Mehrfachwahrheiten entfernt: Wiederholungsdrafts haben nur
noch einen Zustandsweg, Theme und Fokuslimit sind Bestandteil eines atomaren Renderzustands,
Fokus/Timeline/Widget besitzen eigene Projektionen, die Fokuskarte ist zerlegt und
Wiederholungsergebnisse sind adressierbare Room-Zeilen. Das macht den aktuellen Pfad wesentlich
verständlicher und testbarer.

Die Grenzen werden jedoch überwiegend durch Konvention, package-private Klassen, ADRs und Tests
geschützt. Es gibt weiterhin nur ein Android-Modul und ein sehr großes Root-Paket. Der Compiler
verhindert weder Domain-zu-UI-Abhängigkeiten noch das Umgehen der vorgesehenen Mapper. Das
Ergebnis ist eine gute lokale Architektur innerhalb einer weiterhin schwachen globalen
Struktur.

## Was Planung und Implementierung negativ beeinflusst hat

### Mehrere Zustandswege waren gleichzeitig plausibel

Vor dem Umbau änderte `FocusTaskView` den Wiederholungsdraft lokal und löste parallel den
ViewModel-Renderpfad aus. Präferenzen wurden zusätzlich während des Renderns imperativ gelesen.
Damit konnte man beim Lesen einer einzelnen Klasse nicht feststellen, welcher Zustand im
nächsten Frame autoritativ sein würde. Charakterisierungstests mussten zunächst Verhalten
einfrieren, bevor eine sichere Änderung möglich war.

Gewünscht wäre von Anfang an ein dokumentierter Owner je Zustand gewesen: Domainzustand im
Repository, flüchtiger Eingabedraft im ViewModel, vollständiger Renderzustand in genau einem
Snapshot und reine Views als Ausgabe. Diese Festlegung hätte mehrere Refactor-Schritte und
Fehlersuchen verkürzt.

### Präsentationsmodelle waren zugleich universell und unpräzise

Fokuskarte, Timeline und Widget teilten Modelle, obwohl sie andere Texte, Aktionen und
Fortschrittsdetails benötigen. Gleichzeitig ist `TaskSnapshot` ein langer, primitiver
Datencontainer mit vielen booleschen und numerischen Konstruktorparametern. Änderungen erzeugten
breite mechanische Diffs, während vertauschte Argumente kaum lesbar waren.

Die verbraucherspezifischen Modelle lösen die erste Hälfte. Offen bleibt die zweite: benannte
Value Objects oder Builder für Fokusidentität, Completion, Reward und Zeitdarstellung würden
Fixtures und Mapping robuster machen. Noch besser wäre eine klare Trennung zwischen
persistenznaher Domainprojektion und vollständig renderfertigem Fokusmodell.

### Das Root-Paket verschleiert Verantwortungsgrenzen

Domain und Teile der Datenhaltung besitzen Pakete; viele Controller, Zustände, Mapper,
Android-Views und Renderhilfen liegen dagegen nebeneinander im Root-Paket. Während der Arbeit
musste die tatsächliche Richtung über Imports, Konstruktoren und Call Sites rekonstruiert
werden. Ähnliche Namen wie Dashboard, Today, Snapshot, State und UiModel verstärkten das.

Gewünscht wären mindestens Pakete für `presentation.today`, `presentation.focus`,
`presentation.dashboard` und Android-spezifische `ui`-Komponenten sowie automatisierte
Abhängigkeitsregeln. Separate Gradle-Module wären erst der nächste Schritt; schon Paketregeln
mit Architekturtests würden versehentliche Rückkopplungen sichtbar machen.

### Imperative Android-Views machten Layoutlogik unnötig teuer

Die alte Fokuskarte entschied Sichtbarkeit in `onMeasure`, maß mehrfach und synchronisierte
Dekoration über `post()`-Ketten. Fachlich einfache Fragen wie „Wie viele Folgezeilen passen?“
benötigten deshalb Robolectric. Das vollständige Breiten-/Schrift-/Palettenkreuzprodukt machte
eine einzelne Testklasse zur mit Abstand langsamsten der Suite.

Die reine `FocusStepLayoutPolicy` und sechs repräsentative Androidfälle sind deutlich besser.
Dennoch hängt die Eingabe der Policy weiterhin von real gemessenen programmgesteuerten Views ab.
Declarative XML-/Compose-Strukturen oder zumindest klarere Layoutparameterobjekte könnten
Messung und Rendering weiter entkoppeln. Eine native Designreferenz mit Zustandsmatrix,
Schriftgrößen und kleinen Viewports hätte weniger Trial-and-Error erfordert.

### Testseams waren in Produktionsklassen eingebaut

Methoden wie `visibleFollowingStepsForTest`, `cardExtentForTest`,
`animationDurationForTest` und `selectedIndexForTest` spiegelten interne Implementierung statt
Benutzerverträge. Schon kleine interne Umbauten brachen dadurch Tests, obwohl die sichtbare
Semantik unverändert blieb. Zusätzlich erzeugte `FocusTestActions` mit vielen überschreibbaren
No-op-Callbacks eine zweite, nur in Tests existierende Event-API.

Phase 8 entfernt diese Seams. Tests lesen sichtbaren Viewzustand oder zeichnen echte
`DashboardEvent`s auf. Verbleibende `*ForTest`-Hooks des Wood-Grain-Renderers sind eine bewusste
Ausnahme für asynchrone Pipeline-, Cache- und Benchmarkinvarianten; sie sollten nicht als
Vorbild für normale UI-Tests dienen. Das Abschlussaudit entfernte auch dort die vermeidbaren
Durchreichmethoden und die direkte `renderData`-Abfrage aus `WoodGrainView`; übrig bleiben nur
package-private Synchronisations- und Messpunkte an der tatsächlich asynchronen globalen
Pipeline. Der View-Lifecycle-Test beobachtet stattdessen den öffentlichen Invalidierungsvertrag.

Beim Abschlussaudit fiel außerdem auf, dass der komponentenbezogene Golden-Schalter die
Baseline unmittelbar schreiben konnte. Die Dokumentation verlangte zwar eine vorherige
Diffprüfung, der Helper erzwang sie aber nicht. Der Updatepfad akzeptiert deshalb jetzt nur noch
ein bereits in einem fehlgeschlagenen normalen Lauf erzeugtes, pixelgenau zum aktuellen Render
passendes Expected-/Actual-/Diff-Triplet. So ist die Prüfung weiterhin eine bewusste menschliche
Entscheidung, ein versehentlich übersprungener Erzeugungsschritt aber technisch ausgeschlossen.

### Die alte Satzliste war technische Schuld im Schema

Kommagetrennter Text machte Slotidentität implizit, erforderte bei jeder Korrektur einen
Vollrewrite und verlagerte Datenvalidierung in Mapper. Migration, Domain und UI mussten dasselbe
Format verstehen. Das verlangsamte jede Änderung an Wiederholungen und machte historische
Sonderwerte riskant.

Schema 8 normalisiert die Ergebnisse und schützt Identität per Primär-/Fremdschlüssel. Die
Legacyspalte bleibt allerdings vorhanden, und `OccurrenceStep.done` bleibt eine redundante
Projektion des fachlichen Fortschritts. Beides erhöht weiterhin den Migrations- und
Invariantenaufwand.

### Widgetinvalidierung ist korrekt, aber manuell

Das Entfernen von `updateAll()` aus jedem Render war wichtig: ein lokaler Stepperdraft darf
keine Datenbank-/RemoteViews-Arbeit verursachen. Der neue Invalidierungsport ist testbar, beruht
aber darauf, dass jeder zukünftige persistierende Pfad ihn korrekt aufruft. Es gibt keine
transaktionale Outbox oder beobachtbare Datenquelle, die Auslassungen strukturell verhindert.

Für die lokale App ist der Port angemessen. Mit Background-Sync oder weiteren Schreibern wäre
ein datengetriebener Invalidierungsmechanismus beziehungsweise eine Outbox verlässlicher.

## Verbleibende technische Schuld, priorisiert

1. `TaskViewModel` und `MainActivity` sind weiterhin breite Orchestratoren. Ein eigener
   Today-Controller/Store würde Commands, Editor, Kalender, Updates und Systemdialoge klarer
   trennen.
2. `DashboardEvent` ist eine manuell per `instanceof` ausgewertete Summe. Exhaustive Dispatch
   ist in Java nicht erzwungen; ein neuer Eventtyp kann in einem Consumer still übersehen werden.
3. `TaskSnapshot` hat eine fehleranfällige Konstruktorfläche und enthält Fokus-, Domain- und
   Aktionsdaten zugleich.
4. `TaskRepository` bündelt Tasks, Templates, Occurrences, Schritte, Stats, Combo und Ledger.
   Kleine Port-Interfaces würden Use-Case-Abhängigkeiten und Testdoubles reduzieren.
5. `legacyActualRepetitions` sollte nach einem definierten Kompatibilitätsfenster per Migration
   entfernt werden. Dazu fehlen Releasekriterium und Reparaturwerkzeug für geloggte Altfehler.
6. Persistiertes `done` und `RepetitionProgress` können nur durch Mapper-/Write-Disziplin
   konsistent bleiben; stärkere Datenbankconstraints oder eine einzige kanonische Speicherung
   wären robuster.
7. Das einmodulige Root-Paket besitzt keine automatisierten Architekturgrenzen.
8. Robolectric deckt Accessibilitysemantik gut ab, ersetzt aber weder Accessibility Scanner
   noch eine manuelle TalkBack-Prüfung. Instrumentation und echter Upgrade-Probe liefen für den
   Phase-7-Commit im CI auf API 26 und 35; lokal wurde mangels Zielsystem nur das Test-APK gebaut.
9. Native-Graphics-Goldens sind wertvoll, aber werkzeug- und Renderingversionssensitiv. Der
   komponentenbezogene Updatevertrag begrenzt Schäden, beseitigt diese Empfindlichkeit nicht.
10. Widgetinvalidierung ist explizite Aufrufdisziplin; neue persistierende Commands benötigen
    weiterhin einen passenden Test.

## Was ich mir für weitere Arbeiten wünschen würde

- eine kleine, aktuelle Architekturkarte mit erlaubten Abhängigkeitsrichtungen;
- einen Fixture-Builder statt langer Snapshot-Konstruktoren;
- ein festes Geräte-/Emulator-Gate für Room-Migration und Accessibility;
- messbare Budgets für Hosttest-Laufzeit, Testworker-Speicher und UI-Rendering;
- einen dokumentierten Zeitraum zum Entfernen von Legacyspalten;
- komponentenbezogene visuelle Spezifikationen für 320/412/600 dp und große Schrift;
- Architekturtests, die Consumer-Modelle und Widgetgrenzen automatisch schützen.

Diese Punkte sind keine Voraussetzung für den jetzigen Funktionsumfang. Sie bestimmen aber,
ob der nächste ähnlich breite Umbau lokal bleibt oder wieder quer durch Domain, Persistenz,
Activity, Views, Widget und Tests schneiden muss.
