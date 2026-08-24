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
