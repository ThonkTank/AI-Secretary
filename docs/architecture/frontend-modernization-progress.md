# Fortschritt und Selbstaudit der Frontend-Modernisierung

Dieses Dokument protokolliert Vorprüfung, Zuschnitt, Nachweise und ehrliche Nachkritik jeder
Phase. Die autoritative Zieldefinition bleibt die
[Frontend-Modernisierungsroadmap](frontend-modernization-roadmap.md); Abweichungen werden hier
dokumentiert und nicht durch rückwirkendes Umschreiben der Roadmap verborgen.

## Phase 0 – Architekturvertrag und Freeze

### Vorprüfung

- Ausgangspunkt: sauberer `main` auf `e56ce330`, identisch mit `origin/main`, Release-Tag
  `forest-android-1011101`.
- Tatsächliches Room-Schema: 16. Die ältere Architekturkarte nennt noch Schema 14 und ist damit
  für diese Baseline nicht autoritativ.
- Präsentationsbaseline: 192 Java-Dateien, keine Kotlin-Datei und 28 eigene View-/ViewGroup-
  Implementierungen im App-Hauptquellensatz.
- Relevante Größen: `MainActivity` 389 Zeilen, `TaskViewModel` 779 Zeilen,
  `TaskEditorView` 958 Zeilen und `AllTasksViewModel` 249 Zeilen.
- Bestehende Homescreen- und Editorreferenzen sowie deren menschliche Freigaben bleiben
  unverändert.

### Implementationsplan

Phase 0 ist bewusst dokumentarisch und in einem Sprint abschließbar. Sie legt die Original-
Roadmap als unveränderliche Referenz ab, trifft die neue Präsentationsentscheidung in einer ADR,
verlinkt beides aus dem Architekturindex und führt dieses separate Fortschrittsprotokoll ein.
Produktcode, Buildsystem und Golden-Baselines werden nicht berührt.

### Ergebnis und Nachaudit

- Angelegt wurden die autoritative Roadmap, ADR-022 und dieses getrennte Phasenprotokoll; der
  Architekturindex verlinkt alle drei Artefakte.
- Die Roadmap fixiert Zielzustand, Schnittstellen, Ausnahmen, Phasenschnitt, Split-Regel,
  Selbstkritik und Merge-/Gerätegates. Produktcode und visuelle Baselines blieben unberührt.
- Lokale Nachweise: `git diff --check`, interne Markdown-Linkprüfung sowie die vollständigen
  Python-Suites unter `scripts/ci` und `scripts/release` sind grün.

Negativ fiel auf, dass die bisher als aktuell verlinkte Architekturkarte noch behauptete,
Schema 14 bleibe unverändert. Das war nach den Editor-Migrationen zu Schema 15 und 16 falsch und
wurde in dieser Phase korrigiert. Die Roadmap übernimmt bewusst aktuelle Toolchain-Versionen;
dies ist eine zeitabhängige Festlegung, die im Vorcheck von Phase 2 gegen die dann offiziellen
Kompatibilitätsmatrizen erneut bestätigt werden muss. Darüber hinaus wurden keine Shortcuts,
zweiten Wahrheiten oder Scope-Vereinfachungen identifiziert. Eine Nacharbeitsphase ist nach dem
lokalen Audit nicht nötig; der entfernte PR-Gate bleibt noch ausstehend.

Der Pull Request #254 bestand anschließend den stabilen Remote-Gate und wurde als
`e0dcaab7` per Squash nach `main` übernommen. Phase 0 ist damit implementiert; als reine
Dokumentationsphase benötigt sie keine Geräteabnahme.

## Phase 1 – Laufzeit- und Animationstestnetz

### Vorprüfung und Aufteilung

- Ausgangspunkt: sauberer `main` auf `e0dcaab7`, identisch mit `origin/main`.
- Der Standard-Instrumentierungsjob prüft API 26 und 35 mit vollständig deaktivierten
  Animationen. API 37 und ein expliziter 1×-Nachweis fehlen.
- Kritische Interaktionstests existieren bereits für Editor, Alles-Tab und Today. Sie können als
  begrenzte Animation-on-Suite wiederverwendet werden; Datenbank- und Upgrade-Tests benötigen
  diesen zweiten Lauf nicht.
- Editor und Today enthalten noch Polling mit 16-ms-Wartezeiten; Recreation während Editor-
  Motion, Refresh und Eingabe sowie ein Presentation Trace fehlen.

Die Phase ist für einen Sprint zu groß und wird vor Produktänderungen geteilt:

- **Phase 1a** ergänzt den deterministischen 1×-Animationspfad auf API 26, 35 und 37 samt
  Einstellungsnachweis, Diagnostik und verpflichtendem Sammelgate.
- **Phase 1b** ersetzt zeitbasiertes UI-Polling durch Synchronisationssignale, ergänzt die
  fehlenden Lifecycle-/Race-Szenarien und führt den debug-only Presentation Trace ein.

### Phase 1a – Implementationsplan

Der bestehende animationsfreie Volltest bleibt als schnelle, breite Regression erhalten. Ein
zweiter Matrixjob führt ausschließlich die drei kritischen Interaktionssuites je einmal und ohne
Retry aus. Der gemeinsame Instrumentierungsrunner setzt alle drei Android-Animationsskalen auf
1,0, liest sie vor dem Gradle-Start zurück und bricht bei einer Abweichung ab. Das vorhandene
Sammelgate verlangt bei instrumentierungspflichtigen Änderungen beide Jobs. Fehlerartefakte
enthalten zusätzlich die tatsächlich gelesenen Animationsskalen.

### Phase 1a – Ergebnis und Nachaudit

- Der neue Job `animation-instrumentation` führt Editor, Alles-Tab und Today je einmal ohne
  Retry auf API 26, 35 und 37 aus. Der bestehende animationsfreie Volltest bleibt unverändert.
- Der Runner setzt und verifiziert Window-, Transition- und Animator-Skala vor dem Teststart.
  Eine Abweichung beendet den Lauf vor Gradle mit eigenem Fehlercode; Fehlerartefakte enthalten
  die gelesenen Werte.
- Das bestehende `instrumentation-gate` verlangt für instrumentierungspflichtige Änderungen nun
  sowohl den breiten animationsfreien als auch den gezielten Animation-on-Lauf.
- Lokale Nachweise: Shell-Syntax, Workflow-YAML, Scope-Klassifizierung, `git diff --check`, alle
  12 CI-Tooltests und alle 16 Release-/Workflow-Vertragstests sind grün.

Negativ ist die zusätzliche Emulator-Matrix bewusst teuer und dupliziert einen Teil des
Workflow-Setups. Ein gemeinsamer Lauf würde die Aussagekraft verwässern, weil die vollständige
Suite weiterhin einen animationsfreien Zustand benötigt; deshalb wurde diese Duplizierung nicht
wegabstrahiert. Die Testklassenauswahl ist eine kommaseparierte Runner-Schnittstelle und wird
durch den Workflow-Vertrag gegen versehentliche Lücken gesichert. Zeitbasiertes Polling in den
bestehenden Editor-/Today-Tests und fehlende Lifecycle-Races wurden nicht als Shortcut ignoriert,
sondern bleiben der bereits festgelegte Kern von Phase 1b.

Die erste Remote-Runde belegte unmittelbar den Nutzen des neuen Gates: API 26 und 35 fanden
denselben realen Fehler. Während der Löschdialog nach „Behalten“ noch ausblendete, fing sein altes
Dialogfenster Hardware-Back ab; der aktuelle Editorzustand erhielt die Aktion nicht. Der
ausblendende Dialog leitet Back nun an den aktuellen State Owner weiter, und ein fokussierter
Robolectric-Test sichert den Übergang von DELETE über die Ausblendung zu DISCARD. API 37 schlug
vor dem Emulatorstart fehl, weil das verfügbare Preview-Paket `android-37.0` nur im Canary-Kanal
liegt; die Matrix verwendet deshalb explizit `37.0`/`canary`, ohne das Preview als stabil
auszugeben. Dieser Preview-Emulator installierte anschließend korrekt, blieb beim Kaltstart aber
offline. Ein expliziter ADB-Start vor dem Emulator schloss im Folgelauf den zunächst sichtbaren
Daemon-Startfehler aus, der Gast blieb dennoch volle 600 Sekunden offline. Die Upstream-Diagnose
für API 37 benennt die eigentliche Ursache: Das auf dem GitHub-Runner vorinstallierte alte
`avdmanager` schreibt für minor-versionierte Pakete fälschlich `target=android-0`. Nur der
Preview-Zweig aktualisiert die SDK-Command-line-Tools deshalb vor der AVD-Erzeugung auf Version 22
oder neuer; der Workflowvertrag sichert sowohl diese Voraussetzung als auch den vorgezogenen
ADB-Start. Mit den tatsächlich installierten Tools 23 bootete API 37 anschließend und führte
dieselben zehn Interaktionstests wie API 26 und 35 aus.

Der erste breite API-35-Lauf zeigte außerdem bereits bei der Fensterfokus-Synchronisation mehrere
Fehler, obwohl dieselbe Suite auf API 26 grün war. Das ist ein unabhängiges, schon vorher mögliches
Testharness-Race und bleibt bis zur Wiederholung des aktualisierten Commits unter Beobachtung; es
wird nicht mit einem Retry kaschiert. Ein späterer Lauf reproduzierte das Muster ausschließlich
im Animation-on-Job: Editor und Today erhielten durchgehend keinen Window-Fokus, während
AllTasks ohne echte Activity-Interaktion weiterlief. Der gemeinsame Runner weckt und entsperrt
das Gerät deshalb nun vor jeder UI-Instrumentierung explizit, statt implizit auf den Zustand des
Emulator-Images zu vertrauen. Nachdem dieser Startfokus stabil war, isolierten API 35 und 37 noch
eine zweite Synchronisationslücke: Der Test sendete Back nach dem fachlichen Schließen des
Löschdialogs, aber vor der abgeschlossenen Fokusübergabe vom Dialogfenster an die Activity. Der
Dialogpfad verarbeitet Back während seines Fade-outs bereits selbst; für den nachfolgenden
Activity-Pfad wartet der Gerätetest nun zusätzlich auf den tatsächlichen Window-Fokus.

Für den lokalen Android-Nachweis war zunächst nur eine Java-21-JRE ohne Compiler verfügbar. Mit
einem temporären vollständigen Temurin-21-JDK wurden anschließend der neue fokussierte
Editor-Regressionslauf sowie `testDebugUnitTest`, `lintDebug`, `assembleDebug`,
`assembleDebugAndroidTest` und `assembleRelease` erfolgreich ausgeführt. Der erste Gesamtlauf
scheiterte nach grünen Unit-Tests einmalig an der DNS-Auflösung von Google Maven; nach bestätigter
Erreichbarkeit bestand die unveränderte Wiederholung vollständig. Nach der letzten
Instrumentierungskorrektur kompilierten zusätzlich `compileDebugAndroidTestJavaWithJavac` und die
Unit-Suite erneut erfolgreich.

Der abschließende Remote-Gate von Pull Request #255 ist grün: Quality, die breite
animationsfreie Instrumentierung auf API 26 und 35 sowie die gezielte Animation-on-Suite auf API
26, 35 und 37 bestanden; API 37 führte dabei tatsächlich alle zehn Tests aus. Es wurden weder
Retry noch neue Wartezeiten eingeführt. Der anfangs vermutete reine ADB-Startfehler erwies sich als
unvollständige Diagnose und wurde nicht als dauerhafte Erklärung stehen gelassen; der
minor-versionierte AVD-Fehler ist mit der eng auf Preview-Pakete begrenzten und explizit
verifizierten Toolaktualisierung behoben. Die verbliebenen bestehenden 16-ms-Pollingschleifen,
Lifecycle-Races und der fehlende Presentation Trace sind weiterhin sichtbar und gehören gemäß
Originalroadmap zu Phase 1b. Weitere verdeckte Scope-Kürzungen oder fachliche Seiteneffekte fand
der Nachaudit nicht; eine zusätzliche Nacharbeitsphase ist vor dem Squash-Merge nicht nötig.

Pull Request #255 bestand auch auf seinem Audit-Head erneut die vollständige Matrix und wurde als
`12bc2f98` per Squash nach `main` übernommen. Phase 1a ist damit implementiert. Sie verändert
keine sichtbare Produktfläche und benötigt kein UI-Cutover-Gate auf einem physischen Gerät.

### Phase 1b – Vorprüfung und erneute Aufteilung

- Ausgangspunkt: sauberer `main` auf `12bc2f98`, identisch mit `origin/main`.
- Die kritischen Editor- und Today-Gerätetests warten noch in zwei Schleifen alle 16 ms auf
  Zustände. Andere Instrumentierungstests verwenden bereits ausschließlich Androids
  Idle-Synchronisation oder synchrone Zustandsübergänge.
- `TouchGestureDriver` enthält ebenfalls zeitliche Abstände. Diese erzeugen jedoch eine echte
  Long-Press-/Drag-Eingabe mit systemkonformer Dauer und sind keine Wartebedingung auf einen
  Präsentationszustand; sie werden deshalb nicht als Polling kaschiert oder entfernt.
- Einen Presentation Trace gibt es noch nicht. Editor-Motion, Today-Actions, Window-Fokus und
  Randscrollen besitzen daher kein gemeinsames beobachtbares Diagnosesignal.
- Der bestehende Today-Test prüft Recreation während eines aktiven Reorders. Recreation während
  Editor-Motion, Refresh und Texteingabe sowie der explizite Vergleich fachlicher Seiteneffekte
  mit und ohne Animation fehlen weiterhin.

Der nach Phase 1a verbleibende Umfang ist erneut nicht in einem kohärenten Sprint abschließbar und
wird vor Produktcode weiter geteilt:

- **Phase 1b** führt einen im Release wirkungslosen, im Debug-Build aufzeichnenden Presentation
  Trace ein. Editor-, Today- und Lifecycle-Übergänge liefern monotone Signale. Die Editor- und
  Today-Gerätetests warten über diese Signale und Layout-/Fokus-Callbacks mit hartem Timeout statt
  über Polling und protokollieren bei Fehlern den Trace.
- **Phase 1c** ergänzt auf dieser Grundlage Recreation während Animation, Refresh und Eingabe
  sowie den Nachweis, dass Animationen keine Fachaktion, Navigation oder Persistenz auslösen.

### Phase 1b – Implementationsplan

Die Varianten `debug` und `release` erhalten dieselbe kleine Trace-Schnittstelle: Debug speichert
eine begrenzte, sequenzierte Ereignisfolge und erlaubt kurzlebige Listener; Release verwirft die
Ereignisse und hält keine Historie. Hauptcode emittiert nur beschreibende Präsentationsereignisse,
keine Fachaktionen. Instrumentiert werden Editor-State/Motion, Today-State/Randscrollen sowie die
Window-Fokusübergänge der Debug-Hosts. Ein AndroidTest-Helfer registriert zuerst Trace-, Layout-
und Fokus-Callbacks, prüft die Bedingung ausschließlich auf dem Main Thread und blockiert den
Testthread höchstens bis zum festen Timeout. Die bisherigen `SystemClock.sleep(16)`-Schleifen
entfallen; Workflow-Vertragstests verhindern ihre Rückkehr.

### Phase 1b – Ergebnis und lokaler Nachaudit

- Der Debug-Build besitzt nun einen auf 256 Einträge begrenzten, monoton sequenzierten
  Presentation Trace. Er beobachtet echte Activity-Lifecycle-/Fokuswechsel, Dashboard- und
  Editorzustände, Editor-/Dashboard-Motion sowie Today-Reorder und Randscrollen, ohne selbst
  Actions auszulösen.
- Die Release-Variante hält weder Historie noch Listener. Eine explizite Aktivierungsabfrage
  verhindert zusätzlich, dass der Hauptcode dort Diagnosedetails und insbesondere Strings pro
  Animationsframe erzeugt.
- `PresentationAwaiter` registriert Trace-, Layout- und Attach-Signale vor der ersten
  Zustandsprüfung, wertet Bedingungen nur auf dem Main Thread aus und verwendet lediglich einen
  festen Fünf-Sekunden-Abbruch für echte Signalausfälle. Editor- und Today-Interaktionstests
  enthalten keine Zustands-Pollingschleife mehr.
- Die verbleibenden Sleeps in `TouchGestureDriver` bilden weiterhin ausschließlich Long-Press-
  Dauer und zeitlich getrennte reale Move-Events ab; sie fragen keinen UI-Zustand ab.
- Lokale Nachweise mit Java 21: vollständiges `testDebugUnitTest`, `lintDebug`, `assembleDebug`,
  `assembleDebugAndroidTest` und `assembleRelease`; nach der Nachtarbeit erneut Unit-Suite,
  AndroidTest-Kompilierung und Release-Artefakt. Zusätzlich sind alle 12 CI-Tooltests, alle 18
  Release-/Workflow-Vertragstests und `git diff --check` grün.

Der negative Nachaudit fand zunächst einen nichtfunktionalen, aber realen Shortcut: Obwohl die
Release-Trace-Implementierung Ereignisse verwarf, hätte Java bereits vor dem No-op Detailstrings
erzeugt; beim Randscrollen sogar pro Frame. Diese Last wurde in einer Nachtarbeitskorrektur durch
eine vorgezogene Variantenabfrage entfernt und ihr Debug-/Release-Vertrag zusätzlich statisch
gesichert. Die doppelte kleine Variantenoberfläche bleibt ein Drift-Risiko, wird aber durch die
Kompilierung beider Varianten und den Vertragstest begrenzt. Der Awaiter kann durch mehrere
Signalklassen häufiger als nötig geweckt werden, prüft dabei jedoch nur die explizite Bedingung
auf dem Main Thread und erzeugt weder Fachzustand noch Navigation.

Recreation während Editor-Motion, Refresh und Eingabe sowie die fachliche Gleichheit mit und ohne
Animation wurden nicht stillschweigend vereinfacht; sie bleiben der vorab dokumentierte Umfang
von Phase 1c. Der erste vollständige Remote-Gate von Pull Request #256 ist grün: Quality, die
breite animationsfreie Instrumentierung auf API 26 und 35 sowie die kritische Animation-on-Suite
auf API 26, 35 und 37 bestanden ohne Retry; auch Instrumentierungs- und PR-Sammelgate sind grün.
Damit lief die neue Signalsynchronisation auf allen in Phase 1a verbindlich gemachten Runtimes,
ohne einen Fokus- oder Timeoutfehler zu verdecken. Nach der ausgeführten Nachtarbeit ist keine
weitere Nacharbeitsphase erforderlich; vor dem Squash-Merge muss der um diesen Audit ergänzte Head
denselben erforderlichen Gate erneut bestehen.

Der Audit-Head bestand anschließend denselben vollständigen Gate erneut. Pull Request #256 wurde
als `9cc7a92f` per Squash nach `main` übernommen; Phase 1b ist damit implementiert und benötigt als
nicht sichtbarer Diagnose-/Testumbau keine physische UI-Abnahme.

### Phase 1c – Vorprüfung

- Ausgangspunkt: sauberer `main` auf `9cc7a92f`, identisch mit `origin/main`.
- Der Editor-Debug-Host stellt bei Activity-Recreation noch keinen Draft wieder her; die drei
  Gerätetests starten Activities außerdem über eine rohe Instrumentation-Referenz, die nach
  Recreation auf die zerstörte Instanz zeigen würde.
- `TaskViewModel` führt Refreshes in seinem Executor aus und bleibt bei normaler Activity-
  Recreation erhalten. Ein Test mit kontrolliertem Executor und tatsächlich wechselndem
  Lifecycle-Owner fehlt jedoch.
- `MainActivity.openEditorWithFlight` veröffentlicht den neuen Editorzustand erst im End-Callback
  der Dashboard-Animation. Damit löst Motion Navigation aus und eine Recreation während des
  Flights kann den fachlichen Übergang verlieren.
- `RewardAnimator` quittiert ViewModel-Effekte am Animationsende. Die Bestandsprüfung klassifiziert
  dies als presentation-only FIFO-Fortschritt, nicht als Fachaktion, Navigation oder Persistenz;
  eine vorgezogene Quittierung würde bei Recreation noch nicht abgespielte Rewards verlieren und
  bleibt deshalb bis zum stabilen Reward-Cutover in Phase 8 unverändert.

### Phase 1c – Implementationsplan

Der `TaskViewModel` bleibt alleiniger Owner von Editor-, Refresh- und Reward-State. Für den
Editorflight veröffentlicht `MainActivity` die Navigation sofort; `TaskEditorCoordinator` darf
nur das visuelle Mounting bis zum Ende verzögern und verwirft bei Activity-Zerstörung jeden alten
Presentation-Callback. Editorgerätetests verwenden `ActivityScenario`; der Debug-Host speichert
und bindet ausschließlich den bereits vorhandenen `EditorUiState`, sodass Recreation während
Texteingabe und laufender Page-Motion ohne zweite Wahrheit geprüft werden kann.

Ein kontrollierter `TaskViewModel`-Test startet den echten asynchronen Refresh, zerstört den alten
Lifecycle-Owner, bindet einen neuen an dasselbe ViewModel und schließt erst dann den Worker ab.
Unit- und Architekturverträge sichern, dass kein Editor-Motion-Callback ViewModel-Navigation,
Fachcommand oder Persistenz enthält; vorhandene andere End-Callbacks wurden einzeln als reine
View-Bereinigung, Dekoration oder presentation-only Effektquittierung klassifiziert. Der
bestehende Remote-Gate führt dieselben Editor-, Today- und AllTasks-Fachassertionen mit Animationen
aus und bildet den abschließenden Runtimevergleich.

### Phase 1c – Ergebnis und lokaler Nachaudit

- Der Editorgerätetest verwendet nun `ActivityScenario`. Der Debug-Host serialisiert dabei keinen
  zweiten Draft, sondern denselben versionierten `EditorUiState`, den auch der produktive
  `SavedStateHandle` verwendet. Eigene Fälle recreaten während fokussierter Texteingabe und nach
  veröffentlichtem Seitenwechsel bei noch aktiver Motion; Save, Delete und Dismiss bleiben null.
- Ein kontrollierter Test führt den echten `TaskViewModel`-Initialrefresh mit nur einem
  Workerauftrag aus, zerstört den ersten Lifecycle-Owner, bindet einen neuen an dasselbe retained
  ViewModel und belegt genau einen Datenload sowie den finalen `loading=false`-Zustand nur beim
  neuen Owner.
- `MainActivity` veröffentlicht `openEditor` jetzt unmittelbar nach dem Tap und außerhalb jedes
  Motion-Callbacks. `TaskEditorCoordinator` hält ausschließlich das visuelle Mounting zurück;
  Recreation entsorgt den alten Callback, und ein Doppeltap kann nach dem veröffentlichten
  Open-State keinen zweiten Übergang starten.
- Abbruch und reguläres Ende jedes Dashboard-Flights laufen über dieselbe idempotente
  Presentation-Vervollständigung. Damit kann eine abgebrochene View-Animation keinen bereits
  geöffneten Editor dauerhaft unsichtbar lassen.
- Lokale Nachweise mit Java 21: fokussierte State-, Motion- und Architekturtests,
  AndroidTest-Kompilierung, anschließend die vollständigen 427 Unit-Tests, `lintDebug`,
  `assembleDebug`, `assembleDebugAndroidTest` und `assembleRelease`. Alle 12 CI-Tooltests, alle 18
  Release-/Workflow-Vertragstests und `git diff --check` sind grün.

Der negative Audit verwarf zunächst einen geplanten Reward-Umbau: Sofortige Quittierung hätte
eine lokale zweite FIFO benötigt und bei Recreation noch nicht dargestellte Rewards verlieren
können. Da die bestehende Quittierung ausschließlich den Presentation-Effekt fortschaltet und
weder Fachcommand noch Navigation oder Persistenz ist, blieb sie bewusst unverändert; der stabile
Reward-Cutover gehört weiterhin Phase 8. Als blockierende Schwäche blieb dagegen der alte
`withEndAction`-Pfad des Dashboard-Flights, weil Android ihn bei Cancel nicht zuverlässig ausführt.
Die Nachtarbeitskorrektur behandelt End und Cancel einmalig und setzt nur Views zurück
beziehungsweise schließt das visuelle Mounting ab.

Der erste lokale Gesamtlauf meldete einmalig den unveränderten
`AllTasksVirtualizationTest.contentRebindReusesTheExistingHolderChildHierarchy`. Der fokussierte
Lauf auf unverändertem Code und die anschließende vollständige 427-Test-Wiederholung waren grün;
der fachfremde Test wurde nicht abgeschwächt und der Remote-Workflow erhielt keinen Retry. Im
ersten Remote-Lauf waren Quality, die breite Instrumentierung auf API 26/35 und die
Animation-on-Suite auf API 35/37 grün. Nur der neue Page-Motion-Test auf API 26 scheiterte, weil
der Testthread erst nach dem vollständigen 240-ms-Animationslauf wieder eingeplant wurde. Das war
kein verlorener Produktzustand, sondern ein zu schwacher Testaufbau. Die Korrektur veröffentlicht
den Seitenwechsel und fordert die Activity-Recreation nun im selben UI-Turn an; solange Motion
aktiv ist, kann zwischen beiden Operationen kein Frame und damit kein Animationsende laufen.

Die erste Fassung dieser Korrektur rief `Activity.recreate()` direkt innerhalb des UI-Callbacks
auf. API 35/37 und alle anderen Jobs waren grün, auf API 26 blieb der Lifecycle dadurch jedoch
reentrant im Instrumentierungsschritt stehen. Der festgefahrene und durch die Korrektur veraltete
Workflow wurde nicht wiederholt, sondern abgebrochen. Die Recreation liegt jetzt als vorderste
Main-Queue-Nachricht hinter demselben UI-Turn: Sie läuft weiterhin garantiert vor dem nächsten
Animationsframe, aber erst nachdem der aktuelle Callback sauber zurückgekehrt ist. Der Test
wartet explizit auf das Ende der zerstörten Activity, bevor er die neue Instanz bindet.

Der folgende Remote-Lauf endete wieder in normaler Zeit; Quality, beide breiten Gerätejobs und
Animation-on auf API 35/37 waren grün. API 26 meldete jedoch ein `page-end` schon innerhalb
desselben ununterbrochenen UI-Callbacks. Da dort kein Animationsframe laufen kann, war dies ein
verspätetes End-Event eines vorherigen Test-Hosts, das erst nach dem globalen `@Before`-Clear
eintraf. Die finale Isolation leert die Spur deshalb unmittelbar vor dem Klick im selben
UI-Turn. Für den Lifecycle wartet sie instanzbezogen auf `previous.isDestroyed()` statt auf ein
globales Destroy-Event. Die AndroidTest-Kompilierung sowie die fokussierten State-, Motion- und
Architekturtests sind auf diesem Korrekturstand mit Java 21 grün. Der vollständige Remote-Gate
für `4f80103f` ist ohne Retry grün: Quality, die breite animationsfreie Instrumentierung auf API
26/35, die zwölf kritischen Animation-on-Tests auf API 26/35/37 sowie Instrumentierungs- und
PR-Sammelgate bestanden. Der API-26-Animationsjob kehrte mit 2:28 Minuten in seine normale
Laufzeit zurück. Das Instrumentierungs-Sammelgate wartete anschließend rund 21 Minuten ohne
Runner in der GitHub-Queue, lief nach Zuweisung aber in drei Sekunden erfolgreich durch; alle
zugrunde liegenden Ergebnisse waren bereits grün. Weitere lokale Scope-Kürzungen, Doppelzustände
oder notwendige Nacharbeitsphasen wurden im abschließenden Vergleich des gesamten Phase-1-Diffs
mit der Original-Roadmap nicht identifiziert.

## Phase 2 – moderne, gepinnte Buildgrundlage

### Vorprüfung und Aufteilung

- Ausgangspunkt: sauberer `main` auf `8d97a173`, identisch mit `origin/main`.
- Das Projekt verwendet noch AGP 8.7.3, Gradle 8.10.2 und `compileSdk 35`; `minSdk 26`,
  `targetSdk 35` und Java-Quell-/Zielkompatibilität 17 entsprechen bereits dem Zielvertrag.
- Das App-Modul enthält weiterhin keine Kotlin-Datei. Room Runtime/Compiler 2.8.4 und Lifecycle
  2.11.0 sind bereits gepinnt; Activity steht wegen der alten Buildgrenze noch auf 1.10.1.
- Die lokale Ausgangsgröße beträgt 4.959.767 Byte für Debug, 3.755.943 Byte für den unsigned
  Release-Build und 1.478.008 Byte für alle Fonts. Der bisherige CI-Vertrag begrenzt Debug sogar
  auf 5 MiB, prüft den unsigned Release-Build aber noch nicht.
- Die offiziellen Kompatibilitätsangaben wurden am 24. August 2026 erneut geprüft: AGP 9.2
  verlangt Gradle 9.4.1, unterstützt API 37 und JDK 17; Built-in Kotlin ist ab AGP 9 ohne
  `org.jetbrains.kotlin.android` standardmäßig aktiv. Die festgelegten stabilen Artefakte AGP
  9.2.0, Compose-Compiler/Kotlin 2.3.21, Compose BOM 2026.08.00, Activity Compose 1.13.0,
  Lifecycle 2.11.0 und Room KTX 2.8.4 sind in ihren offiziellen Repositories vorhanden.

Die Phase ist als einzelner Sprint nicht kohärent reviewbar und wird vor Produktcode geteilt:

- **Phase 2a** aktualisiert AGP, Gradle und `compileSdk`, aktiviert Built-in Kotlin ohne Opt-out
  und belegt den Kotlin-Testcompile bei weiterhin unverändertem JVM-, Min-/Target-SDK- und
  Produktvertrag.
- **Phase 2b** führt Compose-Compiler, BOM, Activity Compose und Room KTX als einen gepinnten Satz
  ein und mountet ausschließlich einen unsichtbaren, zustandslosen Smoke-Host ohne Material oder
  Produktzustand.
- **Phase 2c** verschärft die Größenverträge und erweitert Neuinstallation sowie den echten
  Produktionsupgradepfad um API 37. R8 bleibt in allen drei Unterphasen unverändert.

### Phase 2a – Implementationsplan

Der Gradle Wrapper wird vollständig auf 9.4.1 und das Android-Plugin auf 9.2.0 aktualisiert;
`compileSdk` steigt isoliert auf 37. `minSdk 26`, `targetSdk 35`, Java 17, Signatur-, Schema- und
Releaseverträge bleiben unverändert. Es wird weder der alte Kotlin-Android-Pluginpfad noch ein
Built-in-Kotlin-Opt-out eingeführt. Ein kleiner Kotlin-Test im Testquellensatz kompiliert und
läuft über AGPs Built-in Kotlin, ohne Produktcode oder APK-Oberfläche zu erweitern. Statische
Buildverträge sichern die exakten Pins und Grenzen gegen späteren Drift. Der Nachweis umfasst
Wrapper-/Plugin-Auflösung, Kotlin-Testcompile, vollständige Unit-/Lint-/APK-Suite sowie die
bestehende Remote-Instrumentierungsmatrix.

### Phase 2a – Implementation und Nachweise

- Gradle Wrapper, Wrapper-JAR und beide Launcher wurden mit Gradle 9.4.1 neu erzeugt; ein
  Vertragstest pinnt zusätzlich den SHA-256 des Wrapper-JARs und den aktuellen `-jar`-Startpfad.
  AGP steht auf 9.2.0 und `compileSdk` auf 37. `minSdk 26`, `targetSdk 35`, Java 17, R8-,
  Signatur-, Schema- und Produktverträge blieben unverändert.
- AGPs Built-in Kotlin kompiliert und startet den neuen `BuiltInKotlinSmokeTest` ohne
  `org.jetbrains.kotlin.android` und ohne Opt-out. Es wurde noch kein Kotlin- oder Compose-Code
  in den Produktquellsatz aufgenommen.
- Die von AGP 9 als veraltet gemeldeten Source-Set-Aufrufe wurden auf die neue `directories`-DSL
  umgestellt, ohne die Schema- oder Upgrade-Fixture-Pfade zu ändern.
- API 37 enthält den alten `FingerprintManager`-Stub nicht mehr. Robolectrics breite
  `Shadows.shadowOf`-Überladungstabelle referenzierte diesen Typ bereits beim Java-Testcompile,
  obwohl die betroffenen Tests nur API 26 und 35 ausführen. Die Tests verwenden deshalb nun die
  typspezifischen `ShadowLooper`-/`ShadowApplication`-Zugriffe beziehungsweise `Shadow.extract`;
  Test-SDKs, Assertions und Ablauf blieben unverändert.
- Lokale Nachweise mit Java 21: Gradle-9.4.1-Auflösung, alle 19 Release-/Workflow-Vertragstests,
  Shell-Syntax, vollständige 426 Testfälle der App-Suite ohne Fehler (ein bewusst deaktivierter
  Benchmark), `lintDebug`, `assembleDebug`, AndroidTest-Kompilierung und `assembleRelease` sind
  grün. Der kalte Gesamtaufbau benötigte nach Toolchain- und SDK-Wechsel 14:02 Minuten; die
  abschließende vollständige App-Suite auf dem bereinigten Stand 5:34 Minuten.
- Die erzeugten Artefakte liegen bei 4.833.656 Byte für Debug, 3.754.873 Byte für den unsigned
  Release-Build und 1.478.008 Byte für die Fonts. Damit bleiben sie bereits unter den später in
  Phase 2c verbindlich zu automatisierenden Grenzen.

### Phase 2a – Roadmap-Abgleich und Nacharbeit

Der Abgleich identifizierte zwei zunächst nicht hinreichend saubere Stellen: Der neue Wrapper
war zwischen Unix- und Windows-Launcher nicht vollständig einheitlich regeneriert, und ein
vorschnelles Upgrade der Testplattform hätte den Scope über die eigentliche API-37-
Kompilierhürde hinaus erweitert. Die Nacharbeit regenerierte deshalb alle Wrapper-Bestandteile,
pinnt ihren Vertrag und behielt die bewährte Robolectric-Version bei; nur deren überbreite,
gegen API 37 nicht mehr kompilierbare Hilfs-API wurde durch typspezifische Zugriffe ersetzt.

Es bestehen weder parallele Buildwahrheiten noch Produkt-, Zustands- oder UI-Änderungen. Compose,
Activity Compose, Room KTX und der unsichtbare Smoke-Host bleiben vollständig Phase 2b;
Größengates und API-37-Installations-/Upgrade-Nachweise bleiben Phase 2c. Eine separate
Nachtarbeitsphase ist nach der auf demselben Branch erledigten Nacharbeit nicht erforderlich.
Der lokale Stand erfüllt Phase 2a; der Phasenabschluss bleibt bis zu grüner Remote-Matrix und
Squash-Merge offen.

### Phase 2b – Vorprüfung und Implementationsplan

- Ausgangspunkt ist der saubere, mit `origin/main` identische Squash-Merge `f610298a` aus Pull
  Request #258. Dessen Quality-, API-26-/35-Instrumentierungs-, Animation-API-26-/35-/37.0- und
  Sammelchecks sind grün. Der erste breite API-35-Lauf wurde ausschließlich durch einen
  fokussierten Pixel-Launcher-ANR blockiert; der vollständige Lauf bestand auf einem frischen
  Runner in 4:35 Minuten. App-Crash, Zustands- oder Geometriefehler lagen nicht vor.
- Der gemergte Stand enthält weiterhin keinen Kotlin-Produktcode und keine Compose-Abhängigkeit.
  `MainActivity` bleibt eine `ComponentActivity`; vorhandene Debug-Harnesses sind bereits in einem
  eigenen Debug-Manifest von der Release-App getrennt. Diese Grenze wird für den Smoke-Host
  wiederverwendet.
- Compose-Compiler-Plugin 2.3.21, Compose BOM 2026.08.00, Compose UI ohne Material, Activity und
  Activity Compose 1.13.0 sowie Room KTX 2.8.4 werden exakt gepinnt. Lifecycle 2.11.0 und Room
  Runtime/Compiler 2.8.4 bleiben unverändert. `buildFeatures.compose` aktiviert Compose; ein
  Kotlin-Android-Plugin oder Built-in-Kotlin-Opt-out bleibt ausgeschlossen.
- Der Smoke-Host wird als nicht exportierte Debug-Activity mit transparentem Plattform-Theme
  umgesetzt. Er verwendet `setContent` mit einer leeren, zustandslosen Composition, besitzt
  weder Material-, Produktzustands- noch Navigationsbezug und taucht nicht im Release-Manifest
  auf. Ein Instrumentierungstest startet diesen echten Host und belegt die angehängte Composition
  sowie den unsichtbaren Fenstervertrag.
- Compose bringt unvermeidlich Laufzeitklassen in das unminifizierte Debug-APK. Deshalb wird das
  bisherige Vor-Compose-Limit von 5 MiB bereits mit der Abhängigkeitseinführung auf den
  verbindlichen Roadmapwert von 10 MiB angehoben und statisch gesichert. Phase 2c ergänzt weiterhin
  den fehlenden unsigned-Release-Grenzwert von 8 MiB, bestätigt den Font-Grenzwert explizit und
  erweitert Neuinstallation sowie Produktionsupgrade um API 37. Diese Präzisierung verhindert
  einen künstlich unmergebaren Zwischenstand, ohne den Umfang von 2c vorwegzunehmen.

Der Nachweis für 2b umfasst exakte Abhängigkeits- und Manifestverträge, Kotlin-/Compose-Compile,
den gezielten Smoke-Host-Gerätetest, die vollständige Unit-/Lint-/APK-Suite, Größenmessung und die
bestehende Remote-Instrumentierungs- und Animationsmatrix. R8, `minSdk 26`, `targetSdk 35`, JVM 17,
Schema, App-ID und sichtbares Produktverhalten bleiben unverändert.

### Phase 2b – Implementation und Nachweise

- Das Compose-Compiler-Plugin 2.3.21 ist über AGPs Built-in Kotlin aktiviert. Der Compose BOM
  2026.08.00 löst Compose UI auf 1.12.0 auf; Activity und Activity Compose stehen auf 1.13.0,
  Lifecycle auf 2.11.0 und Room Runtime/KTX/Compiler auf 2.8.4. Material ist weder direkt noch
  transitiv im Runtime-Klassenpfad vorhanden.
- `ComposeSmokeActivity` ist ausschließlich im Debug-Quellsatz vorhanden, nicht exportiert und
  verwendet ein transparentes Plattform-Theme. Ihre leere `setContent`-Composition besitzt weder
  Zustand noch Navigation oder Produktbezug. Der Instrumentierungstest prüft am echten Activity-
  Fenster eine angehängte `ComposeView`-Composition, Transparenz und fehlendes Dimmen. Das
  Release-Manifest enthält den Host nicht; `compileReleaseKotlin` bleibt deshalb `NO-SOURCE`.
- Der Abhängigkeitsabgleich zeigte, dass Activity Compose die alte direkte Core-Anforderung
  1.13.1 ohnehin auf 1.18.0 anhob. Die Nacharbeit deklariert jetzt direkt die tatsächlich
  aufgelöste und getestete Version 1.18.0 und entfernt den durch `compileSdk 37` überholten
  Ausnahmekommentar. Der resultierende Runtime-Klassenpfad ändert sich dadurch nicht.
- Lokal sind alle 20 Release-/Workflow-Vertragstests, die Shell-Syntax, 426 App-Tests ohne Fehler
  bei einem bewusst deaktivierten Benchmark, `lintDebug`, Debug-/AndroidTest- und unsigned
  Release-Paketierung grün. Die vollständige Suite lief mit Java 21 in 10:53 Minuten. Ein Lauf mit
  dem systemweiten Java 25 wurde verworfen, weil Robolectric 4.14 bereits beim ASM-Class-Reading
  scheitert; die CI und der gültige Nachweis verwenden unverändert Java 21.
- Das Debug-APK misst 8.530.338 Byte, das AndroidTest-APK 653.541 Byte, das unsigned Release-APK
  6.340.450 Byte und die Fonts zusammen 1.478.008 Byte. Damit unterschreitet der lokale Stand
  bereits alle Roadmapgrenzen; in 2b ist davon wie geplant nur das Debug-Limit von 10 MiB als
  Merge-Gate aktiv.

### Phase 2b – Roadmap-Abgleich und Nacharbeit

Der negative Abgleich fand keine Material-, Zustands-, Navigations-, Schema-, R8- oder sichtbare
Produktkopplung. Er fand jedoch die irreführende alte Core-Direktversion; diese zweite
Versionswahrheit wurde auf demselben Branch bereinigt und statisch abgesichert. Eine zusätzliche
Nachtarbeitsphase ist deshalb nicht erforderlich.

Ein lokaler API-36.1-Play-Store-Emulator stürzte in zwei frischen Startversuchen jeweils im
Emulator-Prozess ab, bevor Android Shell-Kommandos annahm und bevor die App installiert wurde.
Dieser Infrastrukturversuch ist kein Gerätebeleg. Der verbindliche Smoke-Host-Nachweis bleibt
daher die frische Remote-Instrumentierungsmatrix. API-37-Neuinstallation und das echte
Produktupgrade, das unsigned-Release-Gate und das explizite Font-Gate bleiben unverändert Phase
2c. Der lokale Stand erfüllt die Implementationsgrenze von 2b; der Abschluss bleibt bis zur
grünen Remote-Matrix und zum Squash-Merge offen.

### Phase 2c – Vorprüfung und Implementationsplan

- Ausgangspunkt ist der saubere, mit `origin/main` identische Squash-Merge `b962ed8e` aus Pull
  Request #259. Quality, breite Instrumentierung einschließlich Compose-Smoke auf API 26/35,
  Animationen auf API 26/35/37 und beide PR-Sammelgates sind grün. Der anschließende `main`-Lauf
  baute und signierte Release 0.2.115, bestand die Produktionsupgrades auf API 26/35 und
  veröffentlichte exakt den getesteten Commit.
- Im ersten `main`-Versuch blockierte ausschließlich ein sichtbarer Pixel-Launcher-ANR den Fokus
  aller API-35-Animationstests. Screenshot, Window-Dump und acht gleichartige Fokusfehler belegen
  das fremde Systemfenster; der fokussierte Wiederholungslauf auf einem frischen Runner bestand in
  4:07 Minuten. App-, Compose-, Zustands- und Geometriefehler lagen nicht vor.
- Das Debug-Gate von 10 MiB und das Font-Gate von 1,6 MiB existieren bereits; der Roadmapvertrag
  für das unsigned Release-APK von 8 MiB fehlt noch. 2c ergänzt dieses Gate und verschärft auch
  das Metadatenlimit für den signierten Produktionskandidaten auf 8 MiB. Änderungen am R8-Modus
  bleiben ausgeschlossen.
- Die breite Neuinstallationsmatrix erhält neben API 26/35 einen Canary-Lauf auf API 37.0. Die
  Produktionsmatrix prüft den signierten Kandidaten zunächst als saubere Neuinstallation,
  entfernt ihn wieder und führt anschließend das bisherige echte Upgrade mit Datenreadback auf
  API 26, 35 und 37.0 aus. Damit werden Debug-Neuinstallation, Produktions-Neuinstallation und
  Produktionsupgrade nicht miteinander verwechselt.
- Die für minor-versionierte Preview-Systemabbilder nötige SDK-Werkzeugvorbereitung wird aus dem
  bisherigen einzelnen Workflowblock in ein gemeinsames, fail-fast CI-Skript extrahiert und von
  breiter Instrumentierung, Animations- und Upgradematrix wiederverwendet. Vertragstests sichern
  Matrix, Aufrufreihenfolge, Größenwerte und Release-Scope. `release_tool.py` wird als
  release-relevanter Buildinput klassifiziert, damit der Merge die verschärfte Produktionskette
  tatsächlich ausführt statt nur statisch zu beschreiben.

2c bleibt ein zusammenhängender Abschluss der Buildgrundlage und wird nicht weiter geteilt. Der
Nachweis umfasst CI-/Release-Harnesstests, Shell-Syntax, vollständige lokale App-Suite, alle drei
Remote-Instrumentierungsmatrizen, signierten Kandidaten, Neuinstallation und Upgrade auf API 37
sowie die Veröffentlichung desselben `main`-Commits. Produktcode, sichtbare UI, Schema, App-ID,
`minSdk 26`, `targetSdk 35` und JVM 17 bleiben unverändert.

### Phase 2c – Implementation und lokale Nachweise

- Quality begrenzt nun neben Debug und Fonts auch das unsigned Release-APK strikt auf weniger als
  8 MiB. Der Releasevertrag akzeptiert auch den signierten, veröffentlichten Kandidaten nur bis
  8 MiB; eine Grenzwertüberschreitung ist durch einen eigenen Negativtest belegt.
- Breite Instrumentierung und Produktionsupgrade führen API 37.0 mit Canary-Systemabbild zusätzlich
  zu API 26/35 aus. Die bereits grüne API-37-Animationsmatrix bleibt bestehen. Alle drei Matrizen
  verwenden dasselbe geprüfte Skript zur Aktualisierung der für minor-versionierte Preview-Pakete
  nötigen SDK-Werkzeuge; die duplizierte Workflow-Implementierung wurde entfernt.
- Der Produktionsrunner beweist auf jedem Upgradegerät zuerst, dass kein App-Paket vorhanden ist,
  installiert und startet den exakt signierten Kandidaten, prüft dessen Version und entfernt ihn
  vollständig. Erst danach installiert er die festgelegte frühere Produktion, erzeugt Altdaten und
  prüft beim Update auf denselben Kandidaten deren Readback. Installationen, beide Starts,
  Paketabwesenheit und Kandidatenversionen sind jeweils fail-fast geprüft.
- Änderungen am ausführenden Releasevertrag werden jetzt als Release-Buildinput klassifiziert.
  Dadurch muss der Merge dieser Phase die Paket-, Neuinstallations-, Upgrade- und
  Veröffentlichungskette tatsächlich durchlaufen; ein nur statisch grüner PR kann sie nicht
  umgehen.
- Lokal bestanden 13 CI-Harnesstests und 22 Release-/Workflow-Vertragstests, Shell-Syntax und
  Diff-Whitespace-Prüfung. Der vollständige Java-21-Build bestand mit 426 App-Tests (ein bewusst
  übersprungener Test), Lint, Debug-, Android-Test- und Release-APK. Die gemessenen Größen sind
  8.530.338 Byte Debug, 653.541 Byte Android-Test, 6.340.450 Byte unsigned Release und 1.478.008
  Byte Fonts.

### Phase 2c – Roadmap-Abgleich und Nachtarbeit

Der negative Abgleich bestätigt unveränderten Produktcode, UI, Navigation, Persistenzschema,
App-ID, SDK-Zielvertrag und R8-Modus. Er fand jedoch eine Beweislücke im ersten Entwurf: Ein
ausgeführtes `am start` hätte allein noch keinen erfolgreichen Start des Produktionskandidaten
bewiesen. Die Nachtarbeit ergänzt deshalb die explizite Prüfung auf `Status: ok` sowie die
Paketabwesenheit vor der Neuinstallation und nach der Deinstallation; der Harnesstest sichert
Reihenfolge und beide Prüfungen. Weitere lokale Abkürzungen oder Scope-Vereinfachungen wurden
nicht gefunden. Der Phasenabschluss bleibt bis zu grünen Remote-Matrizen, signierter
API-37-Neuinstallation und -Upgrade, Veröffentlichung und Squash-Merge auf `main` offen.

### Phase 2c – Nachtarbeitsphase: Vorprüfung und Reparaturplan

Pull Request #260 bestand Quality, die breite Matrix und die Animationsmatrix auf API 26/35/37
vollständig und wurde als `fcebab0b` per Squash nach `main` übernommen. Der Produktionslauf baute
den signierten Kandidaten, stoppte aber auf allen drei Upgradegeräten innerhalb der ersten
Paketabwesenheitsprüfung, noch bevor eine Installation ausgeführt wurde. Ursache ist ein falscher
Shellvertrag: `pm path` meldet ein erwartungsgemäß fehlendes Paket mit Exitcode 1; `set -e`
interpretierte gerade diesen sauberen Ausgangszustand als Fehler. App, APK, Signatur, Start und
Upgradepfad wurden in diesen drei Jobs daher noch nicht erreicht.

Die Reparatur bleibt eine kleine, eigene Nachtarbeitsphase ohne Produktänderung. Die
Abwesenheitsprüfung wechselt auf `pm list packages` und wertet die exakte Paketzeile aus: Ein
Transport- oder Shellfehler bleibt fatal, eine leere erfolgreiche Liste bedeutet abwesend und
eine exakte Paketzeile bedeutet installiert. Der Harnesstest emuliert ausdrücklich, dass der alte
`pm path`-Weg fehlschlagen würde, und sichert zweimal den neuen Aufruf. Danach müssen erneut
CI-/Release-Vertragstests, Shell-Syntax, vollständiger PR, Squash-Merge und die gesamte
Produktionskette einschließlich API 37 grün sein; eine bloße Wiederholung des gescheiterten Laufs
wäre kein zulässiger Abschluss.

### Phase 2c – Nachtarbeitsphase: Implementation und Gegencheck

Die Paketabwesenheitsprüfung verwendet nun `pm list packages` mit dem exakten Paketnamen. Der
Befehl selbst muss erfolgreich sein; nur seine leere Ausgabe gilt als abwesend, während die
exakte `package:`-Zeile vor jeder Installation hart abbricht. Damit wird weder der erwartete
„nicht installiert“-Zustand noch ein ADB-/Package-Manager-Fehler verschluckt. Der ausführbare
Harnesstest belegt sowohl den vollständigen Erfolgsweg mit zwei Abwesenheitsprüfungen als auch den
Negativweg, der ein bereits vorhandenes Paket vor dem ersten Installationsversuch stoppt. Der alte
`pm path`-Aufruf ist zusätzlich statisch ausgeschlossen.

Lokal bestanden 14 CI-Harnesstests, 22 Release-/Workflow-Vertragstests, Shell-Syntax und
Diff-Whitespace-Prüfung. Der negative Abgleich fand keine weitere Statusmaskierung, keine
Abschwächung von Neuinstallation, Start, Versionsprüfung oder Datenreadback und keine Änderung an
Produktcode, Buildabhängigkeiten oder Roadmap-Scope. Eine weitere Nachtarbeitsunterteilung ist
lokal nicht begründet. Abschlussbeleg bleiben ein grüner eigener Pull Request, Squash-Merge und
der anschließend vollständig grüne Produktionslauf auf API 26/35/37 mit Veröffentlichung.
