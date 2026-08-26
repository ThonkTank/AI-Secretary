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

### Phase 2c – zweite Nachtarbeitsphase: Release-Scope

Pull Request #261 und der Squash-Commit `283dd13a` bestanden Quality sowie breite und
animationsaktive Instrumentierung auf API 26/35/37. Der anschließende `main`-Lauf übersprang
jedoch Paketierung, Produktionsupgrade und Veröffentlichung: `run-upgrade-test.sh` lag nur unter
dem allgemeinen Instrumentierungspräfix und war nicht als Release-Input klassifiziert. Dadurch
konnte ausgerechnet eine Änderung am Produktionsfreigabe-Runner ihre eigene Ausführung vermeiden.
Der grüne Lauf ist deshalb kein Abschlussbeleg.

Die zweite Nachtarbeitsphase korrigiert ausschließlich diese Scope-Lücke. Upgrade-Runner,
Scope-Klassifikator, der Release-/Publishworkflow und die auch im API-37-Produktionsupgrade
verwendete Preview-SDK-Vorbereitung werden explizite Build-/Release-Inputs; damit erzwingen
Änderungen an Produktionsprüfung, Gate-Entscheidung oder Produktionsinfrastruktur immer Quality,
Instrumentierung, signierten Kandidaten, Produktionsupgrade und Veröffentlichung. Der
Klassifikatortest verschiebt diese Pfade aus „nur Instrumentierung“ in „alle Gates“. Da
`change_scope.py` selbst Teil dieses Commits ist, muss bereits der Merge dieser Korrektur die
Produktionskette automatisch starten. Produktcode und die fachlichen Phase-2-Verträge bleiben
unverändert; lokal werden erneut alle CI- und Release-Vertragstests sowie der resultierende Scope
geprüft. Der negative Abgleich findet danach keinen weiteren ausführenden Pfad der
Produktionskette außerhalb des Release-Scope. Als Abschluss gelten nur ein eigener grüner Pull
Request, Squash-Merge und ein veröffentlichender `main`-Lauf mit API-26/35/37-Upgrades.

## Phase 3 – beobachtbare Daten und Invalidierung

### Vorprüfung und Aufteilung

- Ausgangspunkt ist der saubere, mit `origin/main` identische Squash-Commit `1e23c7a7`. Sein
  Produktionslauf bestand Quality, breite und animationsaktive Instrumentierung sowie die
  signierte Neuinstallation und das echte Upgrade auf API 26/35/37 und veröffentlichte exakt
  diesen Commit als Release 0.2.116 (`forest-android-1011601`).
- Room-Schema 16 und sämtliche Java-DAO-Reads bleiben synchron. `TaskViewModel` lädt Dashboard
  nach eigenen Writes erneut und veröffentlicht `catalogChanges`; `AllTasksViewModel` lädt nach
  eigenen Writes erneut und veröffentlicht `contentChanges`. `MainActivity` übersetzt beide
  Signale wechselseitig in `reload()`/`load()` und stößt Widgetupdates zusätzlich direkt an.
- Kalender und Anzeigepräferenzen besitzen bereits lifecycle-gebundene Callback-Beobachter, aber
  noch keine Kotlin-Flows. Kalenderberechtigung, Vordergrund und ein Activity-eigener Minutentimer
  erzeugen weitere explizite Refreshs. Widgetinvalidierung ist auf ViewModel, Activity,
  Action-Receiver und Kalender-/Präferenzcallbacks verteilt.
- Die bestehende Präsentation verhindert parallele Kommandos je `UiCommand`, aber ein laufender
  Read kann durch eine neuere Invalidierung weder abgebrochen noch als veraltet verworfen werden.
  Schreibende Use Cases und Room-Transaktionen dürfen bei der Umstellung nicht abgebrochen werden.

Phase 3 ist in einem Sprint nicht kohärent reviewbar und wird vor Produktcode geteilt:

- **Phase 3a** führt eine einzige Room-Invalidierungsquelle als kalten Kotlin-Flow über alle zehn
  Schema-16-Tabellen ein. Reale In-Memory-Room-Tests sichern initiale Emission, Tabellennamen und
  die Emission erst nach erfolgreichem Transaktionscommit. DAOs, Queries, Schema, ViewModels und
  sichtbares Verhalten bleiben unverändert; die Quelle ist noch kein zweiter Screen-State.
- **Phase 3b** adaptiert Kalenderänderungen, relevante UI-Präferenzen und Tages-/Zeitwechsel zu
  lifecycle-unabhängigen Flows mit expliziter Start-/Stop-Verantwortung und deterministischen
  Tests für Policywechsel, Tagesgrenze und Vordergrundmaterialisierung.
- **Phase 3c** verbindet die Quellen mit cancellable latest-Read-Pipelines für Dashboard,
  Alles-Katalog und Widgets. Schreibkommandos bleiben seriell und nicht abbrechbar. Erst wenn
  Race-Tests neuere Reads gewinnen lassen, werden `catalogChanges`, `contentChanges`, Activity-
  Broker-Reloads und verteilte Widgetinvalidierungen entfernt.

### Phase 3a – Implementationsplan

`RoomInvalidationSource` kapselt ausschließlich `AppDatabase.getInvalidationTracker()` und
veröffentlicht `Flow<Set<String>>`. Die vollständige Tabelleliste wird an einer Stelle fixiert;
`emitInitialState=true` garantiert neuen Sammlern einen ersten Ladeimpuls, `conflate()` begrenzt
Invalidierungsbursts, ohne Schreibtransaktionen anzutasten. Ein Robolectric-Test verwendet eine
reale In-Memory-`AppDatabase`: Er prüft die exakte Initialmenge, eine gezielte Tabellenänderung
und dass mehrere Writes innerhalb einer erfolgreichen Room-Transaktion erst nach dem Commit als
Invalidierung sichtbar werden. Ein Rollback darf keinen erfolgreichen Änderungsimpuls erzeugen.
Die Klasse wird in 3a noch nicht im `AppContainer` gemountet; dadurch existieren weder ein
ungenutzter Application-Lifetime-Collector noch parallele Reloadpfade. Nachweise sind der neue
Room-Test, die vollständige App-Suite, Lint/APKs, unverändertes Schema 16 und die bestehende
Remote-Matrix.

### Phase 3a – Implementation und Roadmap-Abgleich

- `RoomInvalidationSource` stellt den Invalidation Tracker als kalten, conflated Flow bereit. Der
  erste Impuls umfasst exakt alle zehn Tabellen von Schema 16; spätere Impulse benennen die von
  Room invalidierten Tabellen. Die synchronen Java-DAOs und `RoomTaskRepository` bleiben die
  einzigen Leser und Schreiber.
- Die Quelle ist absichtlich noch nicht im `AppContainer` verdrahtet. Damit entstehen in dieser
  Grundlagenphase weder ein Application-langer Sammler noch ein zweiter Reloadpfad neben den
  bestehenden ViewModels. UI, Navigation, Widgetaktualisierung, Queries, Transaktionen und
  Persistenzschema sind unverändert.
- Drei Tests gegen eine echte In-Memory-`AppDatabase` sichern den vollständigen initialen
  Tabellenvertrag, genau eine `stats`-Invalidierung für zwei Writes in derselben erfolgreich
  abgeschlossenen Transaktion und keinerlei Invalidierung beziehungsweise Persistenz nach einem
  Rollback. Die erwartete Tabellenmenge ist im Test unabhängig vom Produktionsarray festgehalten.
- Lokal bestanden 14 CI-Harnesstests, 22 Release-/Workflow-Vertragstests und die vollständige
  Java-21-App-Suite mit 429 Tests ohne Fehler (ein bewusst übersprungener Test), Lint sowie Debug-,
  Android-Test- und unsigned Release-APK. Die Größen betragen 8.581.179 Byte Debug, 653.541 Byte
  Android-Test, 6.340.450 Byte Release und 1.478.008 Byte Fonts und liegen unter den verbindlichen
  Grenzen.

Der negative Gegencheck fand im ersten Testentwurf zwei Scheinsicherheiten: Die erwartete
Tabellenmenge wurde zunächst aus derselben Produktionskonstante gelesen, und die Abwesenheit einer
Invalidierung wurde über ein 250-ms-Zeitfenster angenähert. Die Nachtarbeit ersetzt beides durch
einen unabhängigen Schema-16-Vertrag und den synchron ausgewerteten Room-Observer nach expliziter
Tracker-Aktualisierung. Nur das erwartete positive Flow-Ereignis besitzt noch eine großzügige
Fehlergrenze gegen einen tatsächlich hängenden Test. Weitere Scope-Vereinfachungen wurden nicht
gefunden. Phase 3a ist lokal vollständig; ihr Abschluss bleibt der eigene grüne Pull Request, der
Squash-Merge auf `main` und der daraus resultierende grüne Produktionslauf.

### Phase 3a – Nachtarbeitsphase: deterministischer Room-Vertrag

Pull Request #263 bestand Quality und beide Instrumentierungsmatrizen auf API 26/35/37 und wurde
als `8ddb92ff` per Squash nach `main` übernommen. Der anschließende Produktionslauf scheiterte
jedoch in Quality: `committedTransactionEmitsOnceAfterBothWrites` war auf dem frischen Runner
nicht reproduzierbar grün; alle Geräte-, Paket-, Upgrade- und Publishjobs wurden korrekt gesperrt.
Damit ist Phase 3a trotz des grünen PRs noch nicht abgeschlossen.

Die Ursache liegt im Testaufbau. Er sammelte den neuen `createFlow` und registrierte parallel einen
zweiten, alten `InvalidationTracker.Observer`, um eine exakte Callbackanzahl unmittelbar nach
`refreshVersionsSync()` zu prüfen. Room zieht Datenbankänderungen jedoch auf einem anderen Thread;
ein Observer darf deshalb in Race-Situationen auch Änderungen vor seiner Registrierung sehen.
Zudem verspricht der bewusst mit `conflate()` begrenzte öffentliche Adapter keine Callbackanzahl,
sondern einen neuen Ladeimpuls nach Invalidierung. Der Test prüfte somit eine stärkere und
zeitabhängige Eigenschaft als der Produktionsvertrag.

Die Nachtarbeit entfernt den konkurrierenden Legacy-Observer vollständig. Die echte
In-Memory-Datenbank erhält direkte Test-Executors, sodass Invalidierungsarbeit und der
`runBlocking`-Testplan deterministisch geordnet sind. Der Commit-Test belegt nur den relevanten
Vertrag: Nach zwei Writes in derselben erfolgreichen Transaktion liefert der Flow `stats` und der
zweite Wert ist persistiert. Der Rollback-Test lässt den Coroutine-Ereignisloop ohne Wanduhrzeit
abarbeiten und belegt anschließend sowohl einen leeren Flow-Kanal als auch unveränderte Daten.
Danach müssen isolierter Wiederholungstest, vollständige lokale Suite, eigener Pull Request,
Squash-Merge und der komplette veröffentlichende `main`-Lauf erneut grün sein.

### Phase 3a – Nachtarbeitsphase: Implementation und Gegencheck

Der Room-Test verwendet nun für Query- und Transaktionsarbeit einen direkten Executor und sammelt
ausschließlich `RoomInvalidationSource.changes`. Die Commit-Prüfung erwartet nach dem Ende der
gemeinsamen Transaktion den `stats`-Impuls und liest den zweiten geschriebenen Wert zurück; sie
behauptet keine durch `conflate()` ausdrücklich nicht garantierte Callbackanzahl mehr. Nach dem
Rollback wird der `runBlocking`-Ereignisloop mit `yield()` ohne Wanduhrzeit abgearbeitet. Danach
müssen der Flow-Kanal leer und die Stats-Zeile nicht vorhanden sein. Der parallele Legacy-Observer
und `refreshVersionsSync()` sind vollständig entfernt.

Der gezielte Room-Test bestand einmal nach vollständiger Neukompilierung und danach fünf weitere
frische Wiederholungsläufe. Anschließend bestanden erneut 14 CI-Harnesstests, 22
Release-/Workflow-Vertragstests und die vollständige App-Suite mit 429 Tests ohne Fehler (ein
bewusst übersprungener Test), Lint sowie alle drei APK-Builds. Größen und Produktionscode sind
gegenüber dem ersten 3a-Stand unverändert. Der negative Gegencheck findet weder zeitbasierte
Abwesenheitsbehauptungen noch einen zweiten Beobachtungsmechanismus oder eine Abschwächung von
Commit-/Rollbackgrenzen. Der endgültige Abschluss erfordert weiterhin einen eigenen grünen PR,
Squash-Merge und den vollständig veröffentlichenden `main`-Lauf.

### Phase 3b – Vorprüfung und Implementationsplan

Phase 3a wurde nach der deterministischen Nachtarbeitsreparatur auf `main` als `33925947`
vollständig im manuellen Vollmodus geprüft: Quality, breite und animationsaktive
Instrumentierung sowie signierte Neuinstallation und echtes Upgrade bestanden auf API 26/35/37;
Release 0.2.117 (`forest-android-1011701`) veröffentlicht exakt diesen Commit. Der neue
Room-Adapter ist weiterhin kalt und besitzt noch keinen Screen-Collector.

Im aktuellen Stand liefert `CalendarRepository` Provideränderungen über eine explizit schließbare
Callback-Subscription. `UiPreferences` besitzt dasselbe Muster nur für Theme und Fokuslimit;
die Kalenderpolicy ist les- und schreibbar, aber nicht beobachtbar. `MainActivity` hält dagegen
einen unpräzise ab Erzeugungszeit laufenden 60-Sekunden-Handler und übersetzt `onResume()` sowie
den Permission-Callback selbst in Dashboard-Refreshs. Diese Broker werden erst in 3c entfernt;
3b führt ausschließlich die gemeinsam beobachtbaren Quellen ein.

- `CalendarInvalidationSource` registriert vor seinem initialen Ladeimpuls genau eine bestehende
  Calendar-Subscription und schließt sie bei Collector-Abbruch.
- `PreferenceInvalidationSource` adaptiert Anzeigepräferenzen und Kalenderpolicy als getrennte,
  conflated Flows. `UiPreferences` erhält dafür den zum bestehenden Displayvertrag symmetrischen
  Policy-Observer; irrelevante Preference-Schlüssel lösen keinen Impuls aus.
- `ClockInvalidationSource` veröffentlicht Datum, Uhrzeit und Grund (`INITIAL`, `MINUTE_TICK`,
  `FOREGROUND`). Ein Android-Treiber richtet den nächsten Tick an der realen Minutengrenze aus;
  er wird erst mit einem Collector gestartet und bei dessen Abbruch gestoppt. Vordergrund wird
  bewusst über eine explizite Materialisierung eingespeist und ist kein Activity-Lifecycle im
  Datenobjekt.
- `AppContainer` stellt Room-, Kalender-, Präferenz- und Uhrquellen als Composition Root bereit.
  Da alle Flows kalt sind und der Uhrtreiber keine Subscription ohne Collector besitzt, entstehen
  noch keine parallelen Reloads oder Application-Lifetime-Arbeiten.

Deterministische Tests sichern Initialimpulse, Kalender-Start/Stop, Display- und Policywechsel,
Minutensignal, Tagesgrenze, Vordergrundmaterialisierung und das Stoppen des Uhrtreibers. Sie
verwenden ausschließlich manuelle Signale beziehungsweise den Coroutine-Ereignisloop; Schlafen
und kurze Abwesenheitsfristen bleiben ausgeschlossen. 3b verändert weder bestehende Consumer,
Screen State, Widgetinvalidierung, DAO-/Transaktionsgrenzen noch sichtbares Verhalten.

### Phase 3b – Implementation und Roadmap-Abgleich

- Kalenderänderungen werden als kalter, conflated Flow mit initialem Ladeimpuls angeboten. Die
  bestehende Provider-Subscription wird vor diesem Impuls registriert und bei Collector-Abbruch
  entfernt; der Kalendercache und seine Permissionbehandlung bleiben unverändert.
- Anzeigepräferenzen und Kalenderpolicy besitzen getrennte kalte Flows. Der neue symmetrische
  `UiPreferences`-Policyvertrag liefert den aktuellen Wert sofort, reagiert nur auf seinen eigenen
  Schlüssel und entfernt den SharedPreferences-Listener idempotent. Theme und Fokuslimit bleiben
  ein atomarer `DisplayPreferences`-Wert.
- Die Uhrquelle liefert explizite Snapshots für Initialzustand, Minutentick und Vordergrund. Der
  Android-Treiber startet pro Collector erst nach dem Initialsnapshot, richtet den ersten und alle
  folgenden Ticks an echten Minutengrenzen aus und entfernt den Runnable beim Schließen. Ein
  Vordergrundimpuls ohne Collector startet keine Arbeit; bei späterer Sammlung materialisiert der
  Initialsnapshot ohnehin die aktuelle Uhr.
- `AppContainer` hält nun alle vier beobachtbaren Quellen. Keine davon wird in 3b gesammelt;
  `MainActivity`, ViewModels, der alte Minutentimer, manuelle Reloads und verteilte
  Widgetinvalidierungen bleiben deshalb bewusst bis zum gemeinsamen 3c-Cutover unverändert.

Der Negativ-Audit fand zunächst, dass nur der manuelle Fake-Ticker, nicht aber die Ausrichtung und
Abmeldung des echten Android-Handlers geprüft war. Die Implementationsnachtarbeit machte dessen
Handler und Zeitquelle testbar und belegt mit Robolectrics virtueller Zeit exakt den Tick von
59.999 ms auf die Minutengrenze sowie das Ausbleiben weiterer Signale nach `close()` – ohne
Wanduhrzeit. Weitere Doppelzustände oder aktive Parallelpfade wurden nicht eingeführt. Mehrere
spätere Screen-Collector dürfen den kalten Uhrtreiber nicht unabhängig vervielfachen; 3c bündelt
die Quellen daher in einer einzigen geteilten Read-/Invalidierungspipeline.

Lokal bestanden die neuen Quellenverträge, die angrenzenden Kalender-, Präferenz-, ViewModel- und
Dependency-Tests, 14 CI-Harnesstests, 22 Release-/Workflow-Vertragstests und die vollständige
Java-21-App-Suite mit 435 Tests ohne Fehler (ein bewusst übersprungener Test), Lint sowie Debug-,
Android-Test- und unsigned Release-APK. Die Größen betragen 8.776.812 Byte Debug, 653.541 Byte
Android-Test, 6.356.834 Byte Release und 1.478.008 Byte Fonts. Phase 3b ist lokal vollständig; ihr
Abschluss bleibt der eigene grüne PR, Squash-Merge und veröffentlichende `main`-Lauf.

### Phase 3c – erneute Vorprüfung und weiterer Zuschnitt

Release 0.2.118 (`forest-android-1011801`) veröffentlicht Phase 3b exakt aus dem grünen
`main`-Commit `00a7bae1`. Alle vier Quellen sind weiterhin kalt und werden im Produkt noch nicht
gesammelt. Der aktuelle Datenfluss besitzt deshalb unverändert drei manuelle Wahrheiten:

- `TaskViewModel` und `AllTasksViewModel` laden nach eigenen Writes synchron auf ihren seriellen
  Workern nach und veröffentlichen danach `catalogChanges` beziehungsweise `contentChanges`.
  `MainActivity` übersetzt diese beiden Ereignisse wechselseitig in weitere Reads.
- Vordergrund, Kalenderberechtigung und ein Activity-eigener, nicht an die Minutengrenze
  ausgerichteter Handler erzeugen zusätzliche Dashboard-Reads.
- Widgetupdates werden aus `TaskViewModel`, `MainActivity`, `TaskActionReceiver` und den bestehenden
  Kalender-/Präferenzcallbacks angestoßen. `WidgetUpdateCoordinator` serialisiert zwar Zyklen,
  kann aber einen bereits veralteten Read nicht zugunsten einer neueren Invalidierung abbrechen.

Ein gemeinsamer 3c-PR wäre nicht kohärent reviewbar: Koordinationsprimitiven, zwei ViewModels,
Activity-Lifecycle und RemoteViews würden gleichzeitig geändert. Phase 3c wird deshalb vor dem
ersten Produktcode weiter geteilt:

- **Phase 3c1** führt einen geteilten, zieltypisierten Invalidierungsstrom und eine schließbare
  Latest-Read-Pipeline ein. Der Strom sammelt die vier kalten Quellen höchstens einmal, startet
  nur mit mindestens einem Consumer und gibt jedem Consumer einen eigenen Initialimpuls. Race-
  Tests belegen, dass ein neuer Impuls einen alten Read unterbricht, nur das neueste Ergebnis
  veröffentlicht wird und ein Abbruch nicht als Fehler erscheint. Noch kein Produkt-Consumer
  wird umgestellt.
- **Phase 3c2** stellt Dashboard und Alles-Katalog gemeinsam auf diese Pipeline um. Bereits
  gestartete Writes bleiben auf ihren seriellen Workern und werden nie von Read-Abbrüchen erfasst.
  Erst nach Write-/Read-Race-Tests entfallen `catalogChanges`, `contentChanges`, die Activity-
  Broker und tabgebundene Reloads.
- **Phase 3c3** bindet Widgets an denselben Strom, bündelt überlappende Zyklen und entfernt danach
  die verteilten Widgetinvalidierungen, den Activity-Minutentimer und die manuellen
  Vordergrund-/Permission-Refreshs. Tagesgrenze, Vordergrundmaterialisierung, keine installierten
  Widgets und Provider-Lifecycle werden eigenständig geprüft.

### Phase 3c1 – Implementationsplan

`PresentationInvalidationSource` führt Datenbank, Kalender, Anzeigepräferenzen, Kalenderpolicy
und Uhr in einen einzigen `SharedFlow` zusammen. Ein Ereignis enthält Ursache, Zielmenge und – wo
vorhanden – den bereits beobachteten Präferenz- oder Uhrwert. Zielgefilterte Flows für Dashboard,
Katalog und Widgets erhalten nach dem Filter einen eigenen `INITIAL`-Impuls; so hängt der erste
Read eines spät startenden Consumers nicht vom zuletzt geteilten Ereignis ab. `shareIn` verwendet
`WhileSubscribed`, damit ohne Consumer weder Providerlistener noch Minutenticker laufen.

`LatestReadPipeline<I, O>` besitzt einen eigenen, explizit schließbaren Coroutine-Scope. Sie
verarbeitet Eingaben mit `collectLatest`, führt den synchronen Read unterbrechbar auf einem
injizierten Read-Dispatcher aus und veröffentlicht erst nach erfolgreichem Abschluss. Eine
Cancellation wird weitergereicht und niemals in den Fehlerkanal übersetzt. Deterministische
Tests verwenden Barrieren statt Schlafzeiten und prüfen Initialimpulse je Ziel, genau eine
Upstream-Subscription trotz mehrerer Consumer, Stop/Neustart sowie die Reihenfolge eines
blockierten alten und eines schnellen neuen Reads. `AppContainer` montiert die Quelle noch nicht;
damit bleibt 3c1 ein einzelner Vertrag ohne ungenutzten Application-Scope oder zweiten Reloadpfad.

### Phase 3c1 – Implementation und Roadmap-Abgleich

- `PresentationInvalidationSource` bildet die fünf beobachtbaren Ströme auf typisierte Ursachen
  und die Zielmengen Dashboard, Katalog und Widgets ab. Ein einziger `shareIn`-Scope mit
  `WhileSubscribed` verhindert vervielfachte Room-, Provider-, Preference- und Uhr-Collector.
  Jeder Zielstrom liefert unabhängig vom gemeinsamen Verlauf zuerst seinen eigenen Initialimpuls.
- Die Anmeldung am gemeinsamen Strom erfolgt vor der Freigabe dieses Initialimpulses. Die
  Initialwerte der kalten Upstreams werden bewusst nicht verworfen: Sie bestätigen nach der
  tatsächlichen Observer-Registrierung nochmals den aktuellen Stand und schließen so die Lücke
  zwischen frühem Initial-Read und verspätetem Room-/Provider-Start. Spätere Ereignisse tragen
  Tabellenmenge, Präferenzwert, Policy oder Uhrsnapshot bereits typisiert mit.
- `LatestReadPipeline` verarbeitet Eingaben mit `collectLatest`, verschiebt synchrone Reads per
  `runInterruptible` auf einen separaten Dispatcher und prüft vor der Veröffentlichung erneut die
  aktive Generation. Cancellation wird weitergereicht und nicht als Fehler gemeldet. Quelle und
  Pipeline sind explizit schließbar; Schließen beendet aktive Collector und unterbricht einen
  blockierenden Read.
- `AppContainer`, ViewModels, Activity, Widgets, DAOs, Writes und sichtbares Verhalten bleiben in
  3c1 unverändert. Es existiert daher weiterhin genau der alte Produkt-Reloadpfad und noch kein
  ungenutzter Application-Lifetime-Scope.

Deterministische Tests sichern eine einzige Upstream-Subscription bei mehreren Ziel-Consumern,
Stop und Neustart nach dem letzten Consumer, zielgenaue Ursachen und Payloads, einen unabhängigen
Initialimpuls für spät startende Widgets sowie vollständiges Schließen. Zwei weitere Race-Tests
blockieren den alten synchronen Read an einer Barriere: Eine neuere Eingabe beziehungsweise
`close()` unterbricht ihn; nur das neue Ergebnis wird veröffentlicht und Cancellation bleibt aus
dem Fehlerkanal. Ein echter Lesefehler wird gemeldet, ohne spätere Reads zu beenden. Es werden
weder `sleep` noch kurze Negativzeitfenster verwendet; Zeitgrenzen dienen ausschließlich als
Abbruch für tatsächlich hängende Tests.

Der negative Gegencheck fand im ersten Entwurf zwei relevante Shortcuts. Zunächst wurden die
Initialwerte der kalten Quellen entfernt, um einen Startburst zu vermeiden. Damit hätte eine
Änderung zwischen dem ersten Consumer-Read und der tatsächlichen Upstream-Registrierung übersehen
werden können. Die finale Fassung behält diese Bestätigungssignale und lässt `collectLatest` die
überholten Start-Reads abbrechen. Außerdem stoppte `close()` anfangs nur den internen Sharing-
Scope, nicht zwingend bereits sammelnde Ziel-Flows. Ein explizites Close-Signal beendet nun beide
Seiten und ist getestet. Weitere Doppelzustände, aktive Parallelpfade oder veränderte
Transaktionsgrenzen wurden nicht gefunden; eine gesonderte Nachtarbeitsphase ist für 3c1 deshalb
nicht erforderlich.

Lokal bestanden unter Java 21 die vollständige Suite mit 441 Tests ohne Fehler (ein bewusst
übersprungener Test), Lint sowie Debug-, Android-Test- und unsigned Release-APK. Zusätzlich sind
14 CI-Harnesstests und 22 Release-/Workflow-Vertragstests grün. Die APK-Größen betragen 8.776.812
Byte Debug, 653.541 Byte Android-Test und 6.356.834 Byte Release; Produktabhängigkeiten, Schema 16
und Fontbestand sind unverändert. Der erste lokale Vollversuch unter dem inzwischen systemweiten
JDK 25 scheiterte global in Robolectrics ASM-Instrumentierung und wurde nicht als Produktfehler
gewertet; der verbindliche Java-21-Lauf ist vollständig grün. Der Abschluss von 3c1 erfordert nun
den eigenen grünen Pull Request, Squash-Merge und den veröffentlichenden `main`-Lauf.

### Phase 3c1 – Remote-Abschluss

Pull Request #266 bestand Quality sowie breite und animationsaktive Instrumentierung auf API
26/35/37 und wurde als `d5b9cc55` per Squash nach `main` übernommen. Der erste `main`-Lauf war
bis einschließlich Package, API 26 und API 35 grün. API 37 installierte und startete Vorversion
und Kandidat erfolgreich, sah beim Seed der bestehenden 0.2.80-Fixture jedoch einmal SQLite
`user_version=0` statt 8. Der gezielte GitHub-Rerun konnte das Kandidatenartefakt aus Versuch 1
wegen der versuchsgebundenen Artefaktbenennung nicht beziehen und erreichte deshalb keinen
Emulator.

Der vorgesehene manuelle Vollmodus erzeugte daraufhin alle Artefakte in einem neuen konsistenten
Run auf exakt demselben SHA. Quality, beide Instrumentierungsarten sowie signierte Neuinstallation
und echtes Upgrade bestanden auf API 26/35/37; insbesondere war die API-37-Fixture wieder Schema
8 und der Kandidat migrierte erfolgreich. Release 0.2.119 (`forest-android-1011901`) veröffentlicht
exakt `d5b9cc5517860a07ce2d44e8fa6195ae97b88c18` mit APK und Metadaten. Damit ist 3c1 vollständig
abgeschlossen; der einmalige Fixturezustand begründet keine Code-Nachtarbeit.

### Phase 3c2 – erneute Vorprüfung und weiterer Zuschnitt

Die erneute Sichtung des Dashboardpfads fand eine zuvor verdeckte Transaktionsgrenze:
`TaskViewModel.loadContent()` und `loadTodayProjection()` heißen zwar Reads, rufen aber
`DashboardPresenter.refreshWithChanges()` auf. Dieser führt vor der Projektion
`MaterializeDueOccurrences` und optional `ApplyComboDecay` schreibend aus. Würde dieser gesamte
Block direkt in `LatestReadPipeline` verschoben, könnte `collectLatest` eine bereits gestartete
Write-Transaktion unterbrechen und damit den zentralen Roadmapvertrag verletzen.

Zusätzlich dürfen kosmetische Anzeige- oder Minutensignale einen noch ausstehenden fachlichen
Dashboard-Read nicht verdrängen. Der aktuelle Activity-Minutentimer liest nur bei tatsächlichem
Tageswechsel neu; ein naiver gemeinsamer Latest-Strom würde entweder jede Minute unnötig die
Datenbank lesen oder bei getrennten Kurzpfaden eine fachliche Invalidierung verlieren. 3c2 wird
daher vor Produktcode geteilt:

- **Phase 3c2a** erweitert den Read-Vertrag um eine auf einem injizierten seriellen Executor
  laufende, nach Start nicht abbrechbare Vorbereitung. Erst die anschließende reine Projektion ist
  interruptible. Ein typisiertes Dashboard-Routing trennt Content- von Appearance-Ereignissen und
  lässt `MINUTE_TICK` nur bei geändertem Datum in den Contentstrom. Race-Tests beweisen, dass neue
  Ereignisse eine laufende Vorbereitung nicht unterbrechen, danach aber den alten Read verwerfen.
  Noch kein ViewModel verwendet den Vertrag.
- **Phase 3c2b** montiert `PresentationInvalidationSource` im `AppContainer` und stellt
  `TaskViewModel` und `AllTasksViewModel` gemeinsam um. Erfolgreiche Commands schließen nur ihren
  seriellen Writezustand ab; Room invalidiert anschließend beide Projektionen. Die Activity-
  Broker, tabgebundene Reloads, direkten Kalender-/Preference-Observer und manuellen Lifecycle-
  Refreshs entfallen im selben Cutover. Widgetinvalidierungen bleiben bis 3c3 unverändert und
  werden nicht an den neuen Widgetstrom doppelt angeschlossen.

### Phase 3c2a – Implementationsplan

`LatestReadPipeline.prepared(...)` erhält vor dem bestehenden interruptiblen Read eine optionale
`LatestReadPreparation<I>`. Sie wird mit `NonCancellable` auf einem nicht besitzenden Adapter des
übergebenen `Executor` ausgeführt: `close()` oder eine neuere Eingabe dürfen eine gestartete
Vorbereitung nicht interrupten; nach deren Ende wird Cancellation vor dem reinen Read wirksam.
Der Adapter schließt den ViewModel-Executor nicht selbst.

`DashboardInvalidationRouting` veröffentlicht aus dem bestehenden Dashboardstrom zwei abgeleitete
Flows. Content umfasst Initial, Datenbank, Kalender und Policy sowie Clock-Initial/Vordergrund;
ein Minutentick gehört nur bei einem vom zuletzt publizierten Datum abweichenden Snapshot dazu.
Appearance umfasst Displaypräferenzen und alle Uhrsnapshots. Die Zielquelle bleibt weiterhin
ungemountet, sodass 3c2a keine zweite Produktwahrheit erzeugt. Barrieren statt Sleeps sichern
nicht abbrechbare Vorbereitung, Cancellation vor Projektion, neuestes Ergebnis, Close während
Vorbereitung und die Tagesgrenzentscheidung.

### Phase 3c2a – Implementation und Roadmap-Abgleich

- `LatestReadPipeline.prepared(...)` führt eine `LatestReadPreparation` mit `NonCancellable` auf
  einem injizierten `Executor` aus. Eine neuere Invalidierung wartet auf die bereits gestartete
  Vorbereitung, überspringt danach deren veralteten Read und verarbeitet erst dann die neueste
  Eingabe. Der bestehende Read bleibt über `runInterruptible` abbrechbar. Der Executoradapter
  besitzt den Executor nicht und verändert dessen Shutdown-Lifecycle nicht.
- `close()` canceln weiterhin sofort den Pipeline-Scope, unterbrechen aber keine laufende
  Vorbereitung. Nach deren Ende werden weder Read noch Publication ausgeführt. Auch ein erst nach
  `close()` auftretender Vorbereitungsfehler wird aufgrund des erneuten Active-Checks nicht mehr
  in einen verwaisten UI-Fehler übersetzt.
- `DashboardInvalidationRouting` trennt Content und Appearance. Fachliche Quellen sowie
  Clock-Initial/Vordergrund laufen in Content; ein `MINUTE_TICK` nur bei unbekanntem oder
  abweichendem geladenen Datum. Display und alle Uhrsnapshots laufen in Appearance. Fehlende
  Clock-Payloads wählen sicherheitshalber den Content-Read.
- `AppContainer`, Presenter, ViewModels und Activity bleiben unverändert. Der neue Vertrag ist
  ungemountet und erzeugt weder Observer noch Writes oder einen parallelen Screen-State.

Die Race-Tests blockieren Vorbereitungen und Reads über Latches. Sie belegen, dass eine neuere
Eingabe die gestartete Vorbereitung nicht interruptet, den alten Read danach aber vollständig
überspringt; `close()` lässt die Vorbereitung enden und unterdrückt Read, Publication und späten
Fehler. Ein Displayevent wird nachweislich vollständig verarbeitet, während ein Content-Read
blockiert, ohne diesen zu canceln. Routingtests sichern Tagesgrenze, Vordergrund, unbekanntes
Datum, fehlende Payloads und die exakten Ursachemengen. Alle neuen Verträge bestanden fünf
frische Wiederholungsläufe ohne Sleeps.

Der negative Gegencheck ergänzte zwei zunächst fehlende Nachweise: die Nicht-Cancellation eines
laufenden Content-Reads durch kosmetische Ereignisse und die Unterdrückung eines späten Fehlers
aus einer nach `close()` endenden Vorbereitung. Beide Lücken sind im Produktionsvertrag und in
deterministischen Tests geschlossen. Für 3c2b ist nun verbindlich, den seriellen Write-Executor
nur geordnet zu schließen; ein externes `shutdownNow()` könnte naturgemäß auch eine
`NonCancellable`-Coroutine auf Betriebssystemebene interrupten. Weitere Shortcuts oder
Transaktionsverschiebungen wurden nicht gefunden; eine Nachtarbeitsphase ist für 3c2a nicht
erforderlich.

Lokal bestanden unter Java 21 die vollständige Suite mit 448 Tests ohne Fehler (ein bewusst
übersprungener Test), Lint sowie Debug-, Android-Test- und unsigned Release-APK. Die 14
CI-Harnesstests und 22 Release-/Workflow-Vertragstests sind ebenfalls grün; die fünf frischen
Wiederholungen der neuen Race- und Routingtests blieben deterministisch. Die APK-Größen betragen
8.776.812 Byte Debug, 653.541 Byte Android-Test und 6.373.218 Byte unsigned Release; der
Fontbestand bleibt mit 1.478.008 Byte unverändert. Der Abschluss von 3c2a erfordert nun den
eigenen grünen Pull Request, Squash-Merge und den veröffentlichenden `main`-Lauf.

### Phase 3c2a – Remote-Abschluss

Pull Request #267 bestand Quality sowie breite und animationsaktive Instrumentierung auf API
26/35/37 und wurde als `67df90f3` per Squash nach `main` übernommen. Der Produktionslauf
`32783240295` bestand erneut Quality, beide Instrumentierungsmatrizen, signierte Paketierung sowie
Neuinstallation und echtes Upgrade auf allen drei API-Stufen. Release 0.2.120
(`forest-android-1012001`) veröffentlicht APK und Metadaten exakt aus
`67df90f38feba3876e018d42465c4435b6f954a3`. Damit ist der vorbereitete Read-Vertrag vollständig
abgeschlossen.

### Phase 3c2b – erneute Vorprüfung und Implementationsplan

Die erneute Sichtung von Roadmap und Merge-Stand bestätigt den gemeinsamen Screen-Cutover. Eine
weitere Teilung würde entweder Dashboard und Katalog noch über Activity-Signale koppeln oder
neue Invalidierungen parallel zu manuellen Reloads betreiben. Beides widerspräche dem Vertrag
einer einzigen Wahrheit. 3c2b bleibt deshalb ein kohärenter Themenbranch für `AppContainer`,
beide ViewModels und die zugehörige Activity-Verdrahtung; Widgets werden zwar weiterhin aus den
alten Produktstellen angestoßen, sammeln den neuen Widgetstrom aber erst in 3c3.

`AppContainer` montiert genau eine `PresentationInvalidationSource` über die in 3a/3b eingeführten
kalten Quellen. `TaskViewModel` filtert sie über `DashboardInvalidationRouting`: Die Content-
Pipeline materialisiert Fälligkeiten und Combo-Verfall auf ihrem seriellen Executor in der nicht
abbrechbaren Vorbereitung. Danach liest sie Dashboard und Kalender rein und interruptible; nur
das neueste Ergebnis wird atomar publiziert. Eine getrennte Appearance-Pipeline liest aktuelle
Anzeigepräferenzen und Uhrzeit, ändert ausschließlich Präferenz- und Palettenzustand und kann
einen laufenden Content-Read weder abbrechen noch ersetzen. Der geladene Tag wird unter demselben
State-Lock wie der Screen State gelesen und publiziert.

`AllTasksViewModel` sammelt den gemeinsamen Katalogstrom in einer eigenen Latest-Read-Pipeline.
Erfolgreiche Dashboard-, Editor- und Katalogcommands führen nach dem seriellen Write keinen
direkten DAO-Read mehr aus und senden kein ViewModel-Signal; sie schließen nur Running-, Editor-,
Today- oder Rewardzustand ab. Die Room-Invalidierung startet danach beide erforderlichen
Projektionen. Vorbereitung und Commands teilen absichtlich denselben seriellen Executor. Beim
Clearing werden erst beide Pipelines geschlossen und aktive Reads abgebrochen, danach wird der
Executor geordnet per `shutdown()` geschlossen; ein gestarteter Write oder eine gestartete
Vorbereitung wird nie per `shutdownNow()` unterbrochen.

`MainActivity` entfernt `catalogChanges`, `contentChanges`, den Reload beim Eintritt in den
Alles-Tab und ihren eigenen 60-Sekunden-Handler. `onResume()` materialisiert nur noch den
gemeinsamen Clock-Foregroundimpuls. Ein tatsächlich geänderter Kalenderberechtigungsstatus
materialisiert einen expliziten Impuls in `CalendarInvalidationSource`; das ViewModel selbst
aktualisiert dabei ausschließlich seinen Permission-Screen-State. Die bis 3c3 noch nötige
RemoteViews-Aktualisierung bleibt an diesem Übergang sichtbar, erzeugt aber keinen zweiten
Screen-Read.

Die Nachweise umfassen automatische Screen-Aktualisierung nach realen Room-Writes ohne Broker,
Latest-Wins bei konkurrierenden Projektionen, nicht abbrechbare Vorbereitung, getrennte
Appearance-Aktualisierung, Tagesgrenze und Foreground, Permissionmaterialisierung, geordnetes
Clearing sowie die Abwesenheit aller vier alten Activity-/ViewModel-Signale. Bestehende
Editor-, Today-, Alles-, Lifecycle-, Animations- und Upgradeverträge bleiben Teil des vollen
Gates; Schema, DAO-API, visuelle Baselines und Produktverhalten ändern sich nicht.

### Phase 3c2b – Implementation und Roadmap-Abgleich

- `AppContainer` besitzt nun genau eine `PresentationInvalidationSource`. Dashboard und
  Alles-Katalog sammeln zielgefilterte Flows daraus; `catalogChanges`, `contentChanges`, beide
  manuellen Reloadmethoden, die Activity-Broker, der tabgebundene Alles-Reload und der Activity-
  Minutentimer sind entfernt.
- Die Dashboard-Content-Pipeline führt `DashboardPresenter.prepare()` nicht abbrechbar auf dem
  seriellen ViewModel-Executor aus und liest danach die reine Projektion interruptible. Bei einer
  reinen Room-Invalidierung nach bereits geladenem Screen wird nur Today neu gelesen und der
  vorhandene Kalenderzustand atomar beibehalten. Kalender-, Policy-, Tages- und Foregroundimpulse
  lesen beide Projektionen. Appearance besitzt eine unabhängige Latest-Pipeline und verändert nur
  Displaypräferenzen und Palette.
- Dashboard- und Katalogcommands lesen nach einem erfolgreichen Write nicht mehr selbst aus den
  DAOs. Sie schließen Running-, Editor-, Today- und Rewardzustand auf ihrer seriellen Queue ab;
  Room treibt anschließend beide Projektionen. `onCleared()` schließt zuerst alle Pipelines und
  verwendet danach ausschließlich `shutdown()`, sodass aktive Reads abbrechen, gestartete Writes
  und Vorbereitungen aber nicht auf Betriebssystemebene unterbrochen werden.
- `MainActivity.onResume()` materialisiert den gemeinsamen Clock-Foregroundimpuls. Eine tatsächlich
  geänderte Kalenderberechtigung materialisiert `CalendarInvalidationSource` und aktualisiert im
  ViewModel nur den typisierten Permissionzustand. Der Kalenderadapter hält weiterhin genau eine
  Provider-Subscription pro gemeinsamem Upstream.

Der negative Gegencheck fand zwei relevante Übergangsrisiken. Zunächst wurde Collection im
Java-Testadapter versuchsweise auf denselben seriellen Executor wie Vorbereitung und Read gelegt.
Eine dort blockierte Vorbereitung verhinderte dann, dass `collectLatest` die neuere Invalidierung
überhaupt entgegennehmen konnte. Die finale Fassung trennt Collection wieder strikt vom
besitzenden Work-Executor; ein deterministischer Test lässt die alte Vorbereitung enden, verwirft
deren Read und publiziert nur die neue Generation.

Außerdem hätten mit dem Entfernen der direkten Kalender-/Preference-Observer Provider- und
Policyänderungen bis 3c3 zwar den Screen, nicht aber RemoteViews erreicht. Eine eng begrenzte
Übergangsbrücke sammelt deshalb denselben geteilten Widget-Zielstrom nur für Kalender,
Kalenderpolicy und echte Themewechsel. Sie startet keinen Widget-Read, ignoriert reine Focus-
Preference- und Clock-Ereignisse und überlappt nicht mit den bis 3c3 verbleibenden manuellen
Datenbank-Write-Invalidierungen. Ein Permission-/Kalenderimpuls und ein Themewechsel erzeugen
jeweils genau eine Widgetinvalidierung; ein Focus-Limit-Wechsel keine. Die vollständige
Widget-Read-Pipeline, installierte-Widget-Erkennung und Entfernung der letzten manuellen
Writepfade bleiben unverändert der kohärente Scope von 3c3.

Produktnahe Robolectric-Tests verwenden eine echte In-Memory-Room-Datenbank und denselben
gemeinsamen Quellenverbund wie die App. Ein einzelner Room-Write reprojectet Dashboard und
Alles-Katalog ohne Cross-Signal; Managementwrites lesen ihren Katalog nicht direkt zurück.
Lifecycle-Recreation übernimmt die laufende Initialprojektion ohne doppelte Arbeit, reine
Room-Today-Updates überspringen weiterhin den Kalenderprovider, und Architekturtests sichern die
Abwesenheit der entfernten Broker sowie die Reihenfolge Pipeline-Close vor geordnetem Shutdown.
Nach diesen Korrekturen findet der erneute Gegencheck keine parallele Screen-Wahrheit, keine
veränderte Transaktionsgrenze und keinen fehlenden blockierenden Nachweis. Eine gesonderte
Nachtarbeitsphase ist für 3c2b daher derzeit nicht erforderlich; verbindlich bleibt der volle
lokale und entfernte Gate.

Lokal bestanden unter Java 21 die vollständige Suite mit 449 Tests ohne Fehler (ein bewusst
übersprungener Test), Lint sowie Debug-, Android-Test- und unsigned Release-APK. Die 14
CI-Harnesstests und 22 Release-/Workflow-Vertragstests sind ebenfalls grün. Fünf frische Läufe
der Screen-, Quellen-, Routing-, Pipeline- und Architekturtests bestanden jeweils aus geleertem
Testoutput ohne Race-Ausfall. Die APK-Größen betragen 8.777.100 Byte Debug, 653.541 Byte
Android-Test und 6.373.218 Byte unsigned Release; der Fontbestand bleibt mit 1.478.008 Byte
unverändert. Schema 16, Signatur-, Upgrade- und visuelle Verträge wurden nicht geändert. Der
Abschluss von 3c2b erfordert nun den eigenen grünen Pull Request, Squash-Merge und den
veröffentlichenden `main`-Lauf.

### Phase 3c2b – Remote-Abschluss und Geräte-Gate

Pull Request #268 bestand Quality sowie breite und animationsaktive Instrumentierung auf API
26/35/37 und wurde als `a83584aa` per Squash nach `main` übernommen. Der Produktionslauf
`32788757316` bestand erneut Quality, beide Instrumentierungsmatrizen, signierte Paketierung sowie
Neuinstallation und echtes Upgrade auf allen drei API-Stufen. Release 0.2.121
(`forest-android-1012101`) veröffentlicht `AutoSecretary.apk` und `release-metadata.json` exakt
aus `a83584aa06067b381964bd3082aaea2c791fd34c`.

Die automatisierte Implementation von 3c2b ist damit abgeschlossen. Die laut Roadmap für diesen
UI-relevanten Screen-Datenfluss-Cutover zusätzlich erforderliche Installation über den
In-App-Updater und Abnahme auf einem physischen Gerät ist jedoch **ausstehend**: Der erneute
Gerätecheck vom 25. August 2026 fand über das repositoryunabhängige SDK-ADB kein verbundenes
Gerät. Dieser fehlende externe Nachweis wird nicht durch Emulator-, Upgrade- oder Releaseerfolg
ersetzt. Phase 3c3 wartet deshalb; vor ihrer Vorprüfung muss ein entsperrtes, autorisiertes
Android-Gerät erreichbar sein und Release 0.2.121 über den echten Updatepfad installiert und
abgenommen werden.

### Phase 3c3 – Owner-Fortsetzungsentscheidung, Vorprüfung und Implementationsplan

Der Owner hat am 25. August 2026 ausdrücklich angewiesen, ohne verfügbares Handy fortzufahren.
Die physische In-App-Update- und Sichtabnahme von 3c2b bleibt damit offen und wird nicht als
bestanden umgedeutet; sie blockiert auf Owner-Entscheidung aber die nachfolgende technische
Migration nicht mehr. Der Stand vor 3c3 war der saubere Remote-`main`-Commit `a874617b`, während
der letzte veröffentlichte Produktstand weiterhin Release 0.2.121 aus `a83584aa` war.

Die erneute Sichtung von Roadmap und Produktcode bestätigt 3c3 als kohärenten letzten Teil von
Phase 3. Eine weitere Teilung würde entweder den neuen Widgetstrom ungemountet lassen oder
weiterhin direkte Widgetinvalidierungen parallel betreiben. Der gemeinsame Cutover bleibt deshalb
in einem Themenbranch: `WidgetUpdateCoordinator` wird alleiniger Consumer des Widget-Zielstroms,
besitzt genau eine Latest-Read-Pipeline und sammelt nur bei mindestens einem installierten Widget.
Provider-Update und Größenänderung materialisieren einen typisierten Hostimpuls im selben Strom;
Room, Kalender, Kalenderpolicy, Displaypräferenzen und Uhr bleiben dessen übrige Ursachen.

Die nicht abbrechbare Fachvorbereitung wird vom reinen, interruptiblen Widget-Read getrennt.
Überlappende Ursachen verwerfen dadurch alte Reads, niemals aber gestartete Materialisierung.
Provider-Broadcasts erhalten einen Abschluss erst nach der neuesten Publication oder einem echten
Readfehler. `onEnabled`, `onUpdate`, `onAppWidgetOptionsChanged`, `onDeleted` und `onDisabled`
bilden Start, Refresh und Stop ab; ein Prozessstart gleicht bereits installierte Widgets nach der
vollständigen Container-Erzeugung ab. Erst danach entfallen `WidgetInvalidator`, die temporäre
ViewModel-Umgebungsbrücke und alle direkten Widgetaufrufe nach Screen- oder Receiver-Writes.

### Phase 3c3 – Implementation und Roadmap-Abgleich

- `PresentationInvalidationSource` führt `WIDGET_HOST` als reinen Widgetgrund. Der kalte
  Widget-Zielflow besitzt weiterhin seinen unabhängigen Initialimpuls und teilt Room-, Kalender-,
  Policy-, Präferenz- und Uhrquellen mit den aktiven Screen-Consumern. Die provisorische
  `widgetEnvironmentChanges`-Brücke ist entfernt.
- `WidgetUpdateCoordinator` startet genau eine vorbereitete Latest-Read-Pipeline, wenn der
  Launcher mindestens eine Widget-ID meldet. Initial-, Provider-, Daten-, Kalender-, Präferenz-
  und Uhrimpulse laufen durch denselben Pfad. Eine neue Generation interruptet den alten Read,
  während `WidgetPresenter.prepare()` nicht abbrechbar materialisiert. Ein erfolgreicher Zyklus
  lädt einmal und projiziert daraus alle installierten Größen; einzelne fehlerhafte Launcher-IDs
  blockieren die übrigen nicht.
- Provider-Abschlüsse werden bis zur neuesten Publication oder einem Readfehler gesammelt und
  genau einmal beendet. Beim letzten entfernten Widget wird die Pipeline geschlossen und damit
  der Widget-Collector abgemeldet. Ein App-Prozessstart reaktiviert bereits installierte Widgets
  erst nach vollständigem Aufbau des `AppContainer`.
- `TaskViewModel` und `TaskActionReceiver` führen nach Writes keine Widgetinvalidierung mehr aus.
  Room ist nun auch für RemoteViews die einzige Datenbankwahrheit. Der Übergangsport
  `WidgetInvalidator`, Theme-Sonderzustand und alle verteilten Aufrufe sind gelöscht; Vordergrund
  und Permission materialisieren unverändert nur ihre beobachtbaren Clock-/Kalenderquellen.
- Die ungenutzten kombinierten Presenter-Methoden `refreshDomain`, `refreshWithChanges` und
  `refresh` sind entfernt. Fachvorbereitung und reine Projektion können dadurch nicht versehentlich
  wieder zu einem manuellen Reloadpfad zusammengezogen werden.
- `Clock.now()` erlaubt ein gemeinsames Datum-/Zeit-Sample. `SystemClock`, die Clockquelle und der
  Widget-Read verwenden dieses kohärente Sample; ein Tagesgrenzimpuls reicht seinen bereits
  aufgenommenen `ClockSnapshot` unverändert bis zur Projektion weiter. Auch Dashboard-Content und
  -Appearance verwenden bei Uhrereignissen exakt den Snapshot, auf dessen Datum das Routing seine
  Readentscheidung getroffen hat.

Der negative Gegencheck fand zwei relevante Schwächen im ersten Entwurf. Zunächst startete der
Widgetabgleich noch im Konstruktor von `AppContainer` und konnte damit einen nur teilweise
aufgebauten Composition Root an die asynchrone Pipeline verlieren. Die finale Fassung startet
erst in `AutoSecretaryApplication`, nachdem der Container vollständig zugewiesen wurde. Außerdem
las der Widget-Presenter Datum und Uhrzeit getrennt; ein Mitternachtswechsel zwischen beiden
Abfragen hätte fachlichen Tag und Palette widersprüchlich gemacht. Der neue kohärente Clock-Read
und die unveränderte Weitergabe des Tagesgrenzsnapshots schließen diese Race-Lücke. Diese beiden
Korrekturen bilden die Nachtarbeitsphase von 3c3.

Deterministische Tests sichern Inaktivität ohne installierte Widgets, genau einen Start beim
ersten Widget, Stop beim letzten, Provider-Abschluss nach Publication, Abbruch eines blockierten
alten Reads ohne Fehlerpublication sowie den unveränderten Tagesgrenzsnapshot. Quellen- und
Architekturtests belegen die Zieltrennung und Abwesenheit der alten manuellen Pfade. Die physische
Geräteabnahme bleibt gemäß Owner-Fortsetzungsentscheidung ausdrücklich offen und ist kein Teil
dieses automatisierten Nachweises.

Lokal bestanden unter Java 21 die vollständige Suite mit 456 Tests ohne Fehler (ein bewusst
übersprungener Test), Lint sowie Debug-, Android-Test- und unsigned Release-APK. Die 14
CI-Harnesstests und 22 Release-/Workflow-Vertragstests sind ebenfalls grün. Die APK-Größen
betragen 8.777.753 Byte Debug, 653.541 Byte Android-Test und 6.373.218 Byte unsigned Release; der
Fontbestand bleibt mit 1.478.008 Byte unverändert. Schema 16, `minSdk 26`, `targetSdk 35`,
Signatur-, Upgrade- und visuelle Verträge wurden nicht geändert.

Die neuen Widget-, Quellen-, Clock-, Pipeline-, Screen- und Architekturverträge bestanden danach
fünf vollständig frische Wiederholungsläufe ohne Timeout oder Race-Ausfall.

Ein erster Volltestlauf meldete einmalig im unveränderten Alles-Tab den bestehenden
RecyclerView-Vertrag zur Wiederverwendung derselben Holder-Hierarchie rot. Der Test bestand
anschließend fünf vollständig frische isolierte Läufe sowie den maßgeblichen erneuten 456-Test-
Gesamtlauf; der 3c3-Diff berührt weder Alles-Adapter noch dessen View-Hierarchie. Die Abweichung
wird daher als nicht reproduzierbare Lastinteraktion festgehalten, nicht als übergangener
Produktfehler. Der Abschluss von 3c3 erfordert nun den eigenen grünen Pull Request, Squash-Merge
und den veröffentlichenden `main`-Lauf.

### Phase 3c3 – Remote-Abschluss

Pull Request #270 bestand Quality sowie normale und animationsaktive Instrumentierung auf API
26/35/37 und wurde als `ebe9d62f` per Squash nach `main` übernommen. Der Produktionslauf
`32832276640` bestand dieselbe Matrix, signierte Paketierung sowie Neuinstallation und echtes
Upgrade auf allen drei API-Stufen. Release 0.2.122 (`forest-android-1012201`) veröffentlicht
`AutoSecretary.apk` und `release-metadata.json` exakt aus
`ebe9d62fe9d3b246eeec6defe86d0fa6ada3e2c6`. Die automatisierte Phase 3 ist damit abgeschlossen;
die vom Owner übergangene physische In-App-Update- und Sichtabnahme bleibt ausdrücklich offen.

### Phase 4a – Vorprüfung und Implementationsplan für den Alles-State-Owner

Roadmap, ADR-022, der veröffentlichte Phase-3-Stand und die betroffenen Produkt- und Testpfade
wurden vor Produktänderungen erneut gelesen. Phase 4 ist mit dem 749 Zeilen breiten
`TaskViewModel`, dem Editor-, Today-, Optionen-/Updater- und Alles-Schnitt nicht als ein einzelner
reviewbarer Sprint abschließbar. Sie wird daher vorab geteilt. Phase 4a schließt zunächst den
bereits fachlich unabhängigen Alles-Tab vollständig als einen Screen-State-Owner; Editor, Today
und Optionen folgen auf jeweils eigenen Branches. Phase 4 ist erst nach Entfernung der letzten
ViewModel-zu-ViewModel- und Activity-Brokerpfade insgesamt abgeschlossen.

Der konkrete 4a-Schnitt besitzt genau eine Wahrheit: `AllTasksViewModel` veröffentlicht einen
`StateFlow<AllTasksScreenState>`. Dieser atomare Zustand enthält die bestehende reine
Katalogprojektion und eine geordnete Menge bestätigbarer Hostrequests. Jede Vieweingabe wird im
`AllTasksCoordinator` in einen abgeschlossenen `AllTasksAction`-Typ übersetzt und über genau einen
seriellen `dispatch`-Eingang verarbeitet. Fehler, Hinweise, Editor-Navigation und
Löschbestätigung verlassen damit `UiEvent` und den direkten Coordinator-Hostcallback; Requests
erhalten stabile IDs, bleiben bis zur expliziten Bestätigung im State und werden bei
Prozesswiederherstellung aus dem `SavedStateHandle` rekonstruiert.

Die bestehende Java-View bleibt bis Phase 6 produktiv und liest den StateFlow ausschließlich über
eine kleine lifecyclegebundene Legacy-Bindergrenze. Weder View noch Activity halten einen zweiten
mutierbaren Alles-Zustand. Room bleibt alleinige Kataloginvalidierung; Schreibtransaktionen
werden weiterhin nicht abgebrochen. Besonders zu prüfen sind die Reihenfolge schnell
aufeinanderfolgender Filteractions, höchstens eine gleichzeitige Darstellung desselben Requests,
Dialog-Recreation vor Bestätigung, Request-Quittierung ohne fremden Zustandsverlust,
Managementwrites ohne Cross-Signal sowie sauberes Schließen der Read-Pipeline. Produktverhalten,
visuelle Baselines, Schema, SDK-Grenzen und Releasevertrag bleiben unverändert.

### Phase 4a – Implementation und Roadmap-Abgleich

- `AllTasksViewModel` besitzt nun genau einen `MutableStateFlow<AllTasksScreenState>` und gibt ihn
  nur als `StateFlow` heraus. Der atomare State koppelt die unveränderte reine
  `AllTasksUiState`-Projektion mit einer geordneten, unveränderlichen Requestliste; LiveData und
  `UiEvent` sind aus diesem Screen-Owner entfernt.
- `AllTasksAction` bildet Filter, Modus, Expansion, Editorziele, Löschanfrage und -bestätigung,
  Reordercommands sowie Requestquittierung als geschlossene Typen ab. `dispatch` ist der einzige
  öffentliche Eingabepfad und reduziert konkurrierende Aufrufe seriell. Katalogpublication,
  Actions und Requests verändern den gemeinsamen State jeweils unter derselben atomaren
  Zustandsgrenze.
- Fehler, Hinweise, Editorziele und Löschbestätigungen tragen stabile IDs im Screen State. Eine
  explizite Quittierung entfernt nur den adressierten Request. Presentation- und Requestzustand
  werden getrennt und typisiert im `SavedStateHandle` gespeichert; Pending Work überlebt damit
  Activity- und Prozesswiederherstellung.
- `AllTasksCoordinator` ist nur noch ein dünner Übersetzer von Legacy-Viewcallbacks in Actions.
  Sein direkter `Host`-Callback ist entfernt. `MainActivity` bindet den StateFlow über den kleinen
  `LegacyStateFlowBinder`, rendert ausschließlich `state.content` und erfüllt den jeweils ersten
  Hostrequest. Der noch bestehende Aufruf in den breiten Legacy-Editorowner ist eine klar
  begrenzte Übergangsnaht bis zu dessen eigener Phase-4-Unterphase, keine zweite Alles-Wahrheit.
- Roominvalidierung bleibt alleiniger Reprojektionspfad nach Managementwrites. Pipeline-Close,
  Worker-Shutdown, Domainports, Transaktionsgrenzen und visuelle View-Hierarchie wurden nicht
  verändert.

Der negative Roadmap-Abgleich fand zwei relevante Schwächen im ersten Entwurf. Identische schnelle
Doppeltipps konnten denselben Dialog- oder Navigationsauftrag mehrfach einreihen. Außerdem hätte
ein fehlender oder zurückgesetzter Sequenzwert bei vorhandenen restaurierten Requests eine
bereits verwendete ID erneut vergeben können. Die Nachtarbeitskorrektur dedupliziert semantisch
gleiches noch offenes Hostwork und leitet den Sequenzboden zusätzlich aus allen tatsächlich
restaurierten IDs ab. Beschädigte Requestbundles werden nur noch rekonstruiert, wenn die für ihren
Typ nötigen Werte vollständig sind.

Ein echter Robolectric-Activity-Test öffnet einen Management-Löschdialog, recreatet die Activity
vor einer Antwort, sieht denselben stabilen Request erneut und entfernt ihn erst nach der
Negativbestätigung. Weitere Zustandsverträge prüfen Prozesswiederherstellung mit absichtlich
zurückgesetztem Sequenzwert, Doppeltipp-Deduplizierung, Quittierung ohne Änderung derselben
Renderprojektion und Reprojektion eines Managementwrites ausschließlich über Room.

Lokal bestanden unter Java 21 die vollständige Suite mit 460 Tests ohne Fehler (ein bewusst
übersprungener Test), Lint sowie Debug-, Android-Test- und unsigned Release-APK. Die 14
CI-Harnesstests und 22 Release-/Workflow-Vertragstests sind ebenfalls grün. Die APK-Größen
betragen 8.778.889 Byte Debug, 653.541 Byte Android-Test und 6.373.418 Byte unsigned Release; der
Fontbestand bleibt mit 1.478.008 Byte unverändert. Schema 16, `minSdk 26`, `targetSdk 35`,
Signatur-, Upgrade- und visuelle Verträge wurden nicht geändert. Der Phasenabschluss erfordert
nun noch den eigenen grünen Pull Request, Squash-Merge und den veröffentlichenden `main`-Lauf;
die physische Geräteabnahme bleibt gemäß Owner-Entscheidung offen.

Die StateFlow-, Action-, Request-, Activity-Recreation-, Room-Routing- und Architekturverträge
bestanden danach fünf vollständig frische Wiederholungsläufe ohne Race-Ausfall oder verbliebenen
Lifecycle-Collector.

### Phase 4a – Remote-Abschluss

Pull Request #271 bestand Quality sowie normale und animationsaktive Instrumentierung auf API
26/35/37 und wurde als `8f6c42de` per Squash nach `main` übernommen. Der Produktionslauf
`32837858316` bestand dieselbe Matrix, signierte Paketierung sowie Neuinstallation und echtes
Upgrade auf allen drei API-Stufen. Release 0.2.123 (`forest-android-1012301`) veröffentlicht
`AutoSecretary.apk` und `release-metadata.json` exakt aus
`8f6c42dee9672a60df5527392b079978ff063ed5`. Die automatisierte Unterphase 4a ist damit
abgeschlossen; die physische In-App-Update- und Sichtabnahme bleibt gemäß Owner-Entscheidung
offen.

### Phase 4b – Vorprüfung und Implementationsplan für den Editor-State-Owner

Roadmap, ADR-022, der veröffentlichte 4a-Stand, `TaskViewModel`, `DashboardUiState`,
`TaskEditorCoordinator`, `TaskEditorView`, Editor-Reducer und die Editor-Recreation-, Golden- und
Instrumentierungsverträge wurden vor Produktänderungen erneut gelesen. Der Editor lässt sich als
eine kohärente Unterphase aus dem weiterhin 749 Zeilen breiten Dashboardowner schneiden:
`TaskEditorViewModel` übernimmt Laden, Draft, Validierung, Speichern, Löschen und Schließen und
veröffentlicht genau einen `StateFlow<TaskEditorScreenState>`. `DashboardUiState` und
`TaskViewModel` verlieren Editorzustand und Editorcommands vollständig; Roominvalidierung bleibt
nach Save/Delete der einzige Rückfluss in Today und Alles.

Alle Editor-Viewcallbacks werden als geschlossene `TaskEditorAction`-Typen seriell verarbeitet.
Der atomare Screen State enthält `EditorUiState` und geordnete, bestätigbare Fehlerrequests mit
stabilen IDs. Draft und Requests werden im `SavedStateHandle` rekonstruiert. Ein gestarteter Save-
oder Delete-Write wird nicht abgebrochen; doppelte Actions werden weiterhin über stabile
Commandkeys abgewiesen. `MainActivity` liest den StateFlow über denselben dünnen Legacy-Binder und
reicht an `TaskEditorCoordinator` nur den aktuellen Editorinhalt, Palette und Tag weiter.

Die bestehende Activity öffnet den neuen Owner in 4b noch aus Header-, Today-, Intent- und dem in
4a typisierten Alles-Navigationsrequest. Diese klar markierte Routingnaht mutiert keinen zweiten
Editorzustand, erfüllt aber noch nicht das Phase-4-Ziel einer brokerfreien Navigation. Sie wird
deshalb als unmittelbar folgende Phase-4-Nacharbeit über eine explizite App-Navigationsschnittstelle
entfernt und nicht bis Phase 9 aufgeschoben. Besonders zu prüfen sind Laden versus Recreation,
Draftverlust bei paralleler Dashboardpublication, doppelte Save/Delete-Actions, Fehlerrequest-
Recreation, Animation-Cancel beim Öffnen, Rücknavigation sowie identische Golden- und
Accessibility-Verträge. Schema, SDK-Grenzen, Domainports, visuelle Baselines und Releasevertrag
bleiben unverändert.

### Phase 4b – Implementation und Roadmap-Abgleich

- `TaskEditorViewModel` ist nun der einzige Editor-State-Owner. Sein
  `StateFlow<TaskEditorScreenState>` veröffentlicht Editorinhalt und eine geordnete, unveränderliche
  Requestliste atomar; `dispatch(TaskEditorAction)` ist der einzige öffentliche Eingabepfad und
  serialisiert Open, Draft, Save, Delete, Dismiss und Requestquittierung.
- `TaskViewModel` enthält weder `EditorUiState` noch Editorcommands oder den Editorreducer.
  `DashboardUiState` enthält ebenfalls keine Editorprojektion mehr. Today- und Alles-Zustand werden
  nach Editorwrites ausschließlich durch Roominvalidierung neu projiziert; es gibt kein
  ViewModel-zu-ViewModel-Signal und keinen manuellen Reload.
- Draft und geordnete Fehlerrequests werden getrennt im `SavedStateHandle` gespeichert. Requests
  besitzen stabile IDs, werden bis zur expliziten Bestätigung erhalten, semantisch dedupliziert und
  leiten ihren Sequenzboden zusätzlich aus restauriertem Pending Work ab.
- Asynchrone Loads, Saves und Deletes tragen eine Editorgeneration. Dismiss oder ein neuer Open
  verhindern damit, dass ein verspätetes Ergebnis einen neueren Editor wieder öffnet oder schließt.
  Commandkeys weisen schnelle identische Save-/Delete-Doppelausführung weiterhin ab; ein bereits
  gestarteter Write wird beim normalen ViewModel-Clear geordnet beendet.
- `MainActivity` bindet den Editor-StateFlow über `LegacyStateFlowBinder`, rendert nur dessen
  `content` und bestätigt Hostrequests explizit. Ein Activity-Test beweist, dass auch der externe
  Open-Intent im dedizierten Owner landet und nur einmal verbraucht wird. `TaskEditorCoordinator`
  bleibt bis zum Compose-Cutover ein reiner View-Binder.

Der negative Abgleich fand drei Schwächen im ersten Schnitt. Erstens konnte ein vor Dismiss oder
einem neuen Open gestartetes Workerresultat den neueren Zustand überschreiben; die Generationen
und gezielte Race-Tests schließen diese stale Publication. Zweitens schrieben `TaskEditorView` und
`TaskStepsEditorView` ihre lokale Renderkopie vor dem Ownercallback und waren damit für einen kurzen
Moment selbst die Wahrheit. Die Nachtarbeitskorrektur veröffentlicht jetzt immer zuerst in den
Owner; ein Architekturtest hält die Reihenfolge fest. Drittens hätte ein bei Prozessverlust
persistierter `saving=true`-Snapshot dauerhaft einen nicht mehr laufenden Write angezeigt. Ein
unterbrochener Write wird deshalb nicht blind wiederholt, sondern als vollständiger, entsperrter
Entwurf mit Speicherhinweis rekonstruiert. Die echte idempotente Wiederaufnahme bleibt wie in der
Original-Roadmap vorgesehen Phase 5 vorbehalten.

Tests decken zusätzlich Dismiss gegen verspäteten Load, Saveabschluss gegen einen neueren Editor,
Requestwiederherstellung bis zur Quittierung, den unterbrochenen Write, parallele
Dashboardpublication und den realen Activity-Einstieg ab. Lokal bestanden unter Java 21 die
vollständige Suite mit 466 Tests ohne Fehler (ein bewusst übersprungener Test), Lint sowie Debug-,
Android-Test- und unsigned Release-APK. Die 14 CI-Harnesstests und 22 Release-/Workflow-
Vertragstests sind ebenfalls grün. Die APK-Größen betragen 8.783.881 Byte Debug, 653.541 Byte
Android-Test und 6.373.418 Byte unsigned Release; der Fontbestand bleibt bei 1.478.008 Byte.
Schema 16, `minSdk 26`, `targetSdk 35`, Domain- und visuelle Verträge wurden nicht verändert.

Die StateFlow-, Action-, Generation-, Recreation-, Activity-, Legacy-Binder- und
Architekturverträge bestanden anschließend fünf vollständig frische Wiederholungsläufe ohne
Race-Ausfall, Draftverlust oder verbliebenen Lifecycle-Collector.

Die bewusst verbliebene Activity-Routingnaht ist keine zweite Editorwahrheit, erfüllt aber den
vollständigen Phase-4-Satz „Activity-Brokerlogik entfällt“ noch nicht. Sie ist daher keine
stillschweigende Scope-Vereinfachung: Nach dem eigenen PR-/Merge-/Produktionsabschluss von 4b folgt
unmittelbar die bereits geplante Routing-Nacharbeit, bevor weitere Screen-Owner extrahiert werden.
Die physische In-App-Update- und Sichtabnahme bleibt gemäß Owner-Entscheidung offen.

### Phase 4b – Remote-Abschluss

Pull Request #272 bestand Quality sowie normale und animationsaktive Instrumentierung auf API
26/35/37 und wurde als `52b6fbe3` per Squash nach `main` übernommen. Der erste Produktionsversuch
hing im unveränderten `LatestReadPipelineTest` auf dem Runner und wurde erst nach mehr als 30
Minuten ohne Testabschluss abgebrochen. Die identische zweite Ausführung desselben Workflows und
Commits bestand den Quality-Lauf in 3:29 Minuten; auch alle Geräte-, Paket- und Upgradejobs waren
grün. Der Produktionslauf `32844177135` veröffentlichte Release 0.2.124
(`forest-android-1012401`) mit `AutoSecretary.apk` und `release-metadata.json` exakt aus
`52b6fbe337dcf0a14f4bfb006580b82f27bfe973`. Der einmalige Runner-Hänger war damit nicht
reproduzierbar und begründet keine fachfremde Produktänderung. Die automatisierte Unterphase 4b
ist abgeschlossen; die physische In-App-Update- und Sichtabnahme bleibt gemäß Owner-Entscheidung
offen.

### Phase 4c – Vorprüfung und Implementationsplan für die App-Navigationsgrenze

Roadmap, ADR-022, der veröffentlichte 4b-Stand, `MainActivity`, `AllTasksViewModel`,
`AllTasksCoordinator`, Editorowner, Launch-Intent und die zugehörigen Recreation- und
Architekturtests wurden vor Produktänderungen erneut gelesen. Die unmittelbar angekündigte
Nacharbeit bleibt ein eigener, reviewbarer Schnitt: Sie führt noch nicht Navigation 3 oder eine
neue Shell ein, sondern entfernt ausschließlich die verbliebene Editor-Routingkette
`AllTasksViewModel -> AllTasksRequest -> MainActivity -> TaskEditorViewModel`.

Eine kleine typisierte `AppNavigator`-Schnittstelle mit unveränderlichen `AppDestination`-Werten
bildet neuen Task, Taskbearbeitung, Schrittbearbeitung und Schrittanlage ab. Alle
Alles-Viewcallbacks bleiben `AllTasksAction` und werden weiterhin seriell durch ihren State Owner
verarbeitet; für die Navigation ruft der Owner jedoch die injizierte App-Schnittstelle auf, statt
einen Hostrequest in seinem Screen State abzulegen. Eine Editoradapter-Implementierung übergibt
das Ziel synchron an `TaskEditorViewModel`, dessen bereits persistierter atomarer Zustand den
Routeabschluss bildet. Header, Today-Menü und Widget-Intent verwenden dieselbe Schnittstelle.
`MainActivity` verdrahtet die Abhängigkeiten nur am Composition Root und übersetzt keine
Screenowner-Nachricht mehr in eine Nachricht an einen anderen Owner.

Der Headerflug bleibt ein rein visueller, vor der synchronen Zielübergabe ausgeführter
Host-Hook. Er darf weiterhin weder fachliche Aktion noch Navigation auslösen; Abbruch und Ende
führen nur den bereits vorhandenen visuellen Abschluss aus. Der Navigator verwirft eine zweite
Öffnung, solange bereits ein Editor geöffnet ist, damit Doppeltipps, Recreation oder ein später
Launch-Intent keinen bestehenden Draft ersetzen. Zu prüfen sind direkte Alles-Navigation ohne
pending Request, identische Task-/Schrittparameter, Doppeltipp-Deduplizierung, restaurierter
Editor versus Launch-Intent, Header-Animationsreihenfolge und die vollständige Abwesenheit von
`OPEN_EDITOR` im Alles-State und seiner Saved-State-Codierung. Fehler-, Info- und
Löschbestätigungsrequests bleiben unverändert im jeweiligen Screen State. Produktverhalten,
visuelle Baselines, Schema, SDK-Grenzen und Releasevertrag bleiben unverändert.

### Phase 4c – Implementation und Roadmap-Abgleich

- `AppNavigator` ist nun die explizite screenübergreifende Navigationsgrenze;
  `AppDestination.TaskEditor` trägt Task-, Schritt- und Anlageziel sowie die rein visuelle
  Eintrittsart typisiert und unveränderlich. `TaskEditorNavigator` übernimmt diese Ziele synchron
  in den bereits persistierenden Editorowner.
- `AllTasksViewModel` verarbeitet Edit- und Add-Step-Actions weiterhin seriell, ruft dafür aber
  nur noch den injizierten Navigator auf. `AllTasksRequest`, dessen Saved-State-Adapter und
  `MainActivity` enthalten keinen produktiven `OPEN_EDITOR`-Request und keine Activity-
  Übersetzung zum Editorowner mehr. Renderzustand und übrige bestätigbare Hostrequests bleiben
  davon unberührt.
- Header, Today-Menü und externer Widget-/Launch-Intent verwenden dieselbe Zielgrenze. Eine
  Öffnung wird abgewiesen, wenn der autoritative Editor-State bereits offen ist; dadurch ersetzen
  Doppeltipps und spätere Intents keinen aktuellen oder restaurierten Draft. Der Headerflug wird
  vor der synchronen Zielübergabe vorbereitet, sein Abschlusscallback enthält weiterhin keine
  Navigation.
- `MainActivity` bleibt Composition Root und Host für Views, Dialoge und Animationen, vermittelt
  aber keine Alles-Owner-Nachricht mehr an den Editorowner. Navigation 3 und der Top-Level-Stack
  bleiben unverändert Phase 9; Phase 4c baut dafür nur den typisierten Anwendungsport auf.

Der negative Roadmap-Abgleich fand eine Upgrade-Schwäche im ersten Schnitt: Ein unter 0.2.124
gespeicherter, noch nicht dargestellter Alles-`OPEN_EDITOR`-Request wäre von der neuen Codierung
als unbekannt verworfen worden. Die Nachtarbeitskorrektur liest ausschließlich diesen alten
Snapshottyp, wandelt Task-, Schritt- oder Add-Step-Ziel einmalig in `AppDestination` um und
schreibt unmittelbar den bereinigten Alles-Requestzustand zurück. Mehrere alte Ziele werden
geordnet angeboten; der Navigator akzeptiert wegen des dann offenen Editorzustands nur das erste.
Damit geht der pending Einstieg nicht verloren und kann nach späterem Schließen auch nicht erneut
aufleben. Neue Navigation wird weiterhin nie im Alles-State gespeichert.

Tests sichern alle drei Alles-Ziele ohne Änderung oder Request im Screen State, Headerhook vor
Ownerpublication, Doppeltippschutz, den echten Launch-Intent, Draftschutz über Activity-Recreation,
die einmalige Alt-Snapshotmigration und die Abwesenheit des Activity-Brokers. Lokal bestanden
unter Java 21 die vollständige Suite mit 469 Tests ohne Fehler (ein bewusst übersprungener Test),
Lint sowie Debug-, Android-Test- und unsigned Release-APK. Die 14 CI-Harnesstests und 22
Release-/Workflow-Vertragstests sind ebenfalls grün. Die APK-Größen betragen 8.733.154 Byte
Debug, 653.541 Byte Android-Test und 6.373.418 Byte unsigned Release; der Fontbestand bleibt bei
1.478.008 Byte. Schema 16, `minSdk 26`, `targetSdk 35`, Domain- und visuelle Verträge wurden nicht
verändert.

Die neuen Routing-, Parameter-, Migrations-, Recreation- und Architekturverträge bestanden
anschließend fünf vollständig frische Wiederholungsläufe. Nach der Nachtarbeitskorrektur wurden
keine blockierende Diskrepanz, kein paralleler Navigationszustand und keine weitere notwendige
Nacharbeitsphase für diesen Schnitt gefunden. Phase 4 insgesamt bleibt offen, bis Today sowie
Optionen/Updater eigene StateFlow-Owner besitzen und die verbleibenden `UiEvent`-/Activity-
Brokerpfade entfernt sind. Der Abschluss von 4c erfordert nun den eigenen grünen Pull Request,
Squash-Merge und den veröffentlichenden `main`-Lauf; die physische In-App-Update- und Sichtabnahme
bleibt gemäß Owner-Entscheidung offen.

### Phase 4c – Remote-Abschluss

Pull Request #273 bestand Quality sowie normale und animationsaktive Instrumentierung auf API
26/35/37 und wurde als `81930552` per Squash nach `main` übernommen. Der Produktionslauf
`32853006805` bestand dieselbe Matrix, signierte Paketierung sowie echtes Upgrade vom vorherigen
Produktionsstand auf allen drei API-Stufen. Release 0.2.125 (`forest-android-1012501`)
veröffentlicht `AutoSecretary.apk` und `release-metadata.json` exakt aus
`819305526fb17576fc88cd64555a057b5056bc60`. Die automatisierte Unterphase 4c ist damit
abgeschlossen; die physische In-App-Update- und Sichtabnahme bleibt gemäß Owner-Entscheidung
offen.

### Phase 4d – Vorprüfung und Implementationsplan für den Optionen-State-Owner

Roadmap, ADR-022, der veröffentlichte 4c-Stand, `TaskViewModel`, `DashboardUiState`,
`OptionsView`, `UpdateViewModel`, `UpdateUiController`, Android-Dialog-/Plattformadapter und die
zugehörigen Options-, Update-, Activity- und Architekturtests wurden vor Produktänderungen erneut
gelesen. Der Optionenbildschirm besitzt aktuell drei konkurrierende Zustandswege: Darstellungs-
und Kalenderzustand im Dashboardowner, einen separaten LiveData-Updaterowner und einen
Activity-Controller mit verbrauchbaren `UpdateEvent`s. Eine Unterphase nur für Preferences oder
nur für Updateevents würde diese Mehrfachwahrheit konservieren. Phase 4d schneidet deshalb den
vollständigen Optionen-/Updaterbildschirm als ein kohärentes Review-Ergebnis aus; Today bleibt
danach als letzte große Phase-4-Unterphase im alten Dashboardowner.

`OptionsViewModel` veröffentlicht genau einen `StateFlow<OptionsScreenState>` mit Theme,
Fokusschrittlimit, Kalenderberechtigungs-/Kalenderstatus, Palette, Updatestatus und einer
geordneten Menge bestätigbarer Hostrequests. `OptionsAction` ist der einzige serielle Eingang für
Preferenceauswahl, beobachteten Berechtigungsstand, Berechtigungsaktion, manuellen/automatischen
Updatecheck, Downloadannahme/-aufschub, Installationswiederaufnahme und Requestquittierung.
Preferences und Kalender werden über die bestehenden Invalidierungsquellen beobachtet; der
Todayowner darf dieselben dauerhaften Quellen für seine eigene Projektion weiter lesen, enthält
aber keinen Optionen- oder Updatestatus mehr.

Update-Verfügbarkeit, Fehler, Installationsberechtigung und Installerstart werden nicht mehr als
verbrauchbare Events transportiert. Sie werden zu stabil identifizierten `OptionsRequest`s im
Screen State. Die Activity rendert nur den ersten Request über die bestehenden Androidadapter und
bestätigt ihn explizit; sie entscheidet weder Updatefachlogik noch schreibt sie Optionen in einen
anderen Owner zurück. Ein gestarteter Download wird nicht abgebrochen. Doppelte Checks,
Downloads und Installationsrequests werden dedupliziert; verspätete Ergebnisse dürfen keinen
neueren Workflowzustand überschreiben. Zu prüfen sind Activity-Recreation mit offenem Prompt,
Kalenderpermission samt Rückkehr aus Settings, automatischer Check-Rate-Limit, Downloadfortschritt,
Installationspermission und -retry, Fehlerpfade, Preferenceinvalidierung ohne Contentreload sowie
die Entfernung von `UpdateEvent`, `UpdateUiController` und Optionsfeldern aus `DashboardUiState`.
Visuelle Optionsdarstellung, Android-Systemnavigation, Updatetrust/-transport, Schema, SDK-
Grenzen und Releasevertrag bleiben unverändert.

Während der lokalen Prüfung wurde auf `main` zusätzlich Phase #274 mit dauerhaften Task- und
Pausentimern veröffentlicht. Vor dem Rebase wurde deshalb der neue Produktionsstand erneut
gesichtet und der Implementationsplan erweitert: Die globale Pausentimer-Vorgabe gehört wie die
übrigen editierbaren Preferences in `OptionsScreenState`/`OptionsAction`; laufende Timer,
Timeraktionen und die einmalige Systemberechtigungswarnung bleiben dagegen Today-Verhalten.

### Phase 4d – Implementation und Roadmap-Abgleich

- `OptionsViewModel` ist nun der einzige State-Owner des Optionenbildschirms. Sein
  `StateFlow<OptionsScreenState>` bündelt Theme, Fokusschrittlimit, globale Pausentimer-Vorgabe,
  Kalenderberechtigung, Kalenderprojektion, Palette, Updatezustand und eine unveränderliche
  geordnete Requestmenge. `OptionsView` sendet ausschließlich abgeschlossene `OptionsAction`s
  und bindet ausschließlich diesen Screen State.
- Appearance- und Kalenderreads laufen über getrennte Latest-read-Pipelines mit eigenen
  Invalidierungsrouten. Appearance-, Kalender- und Policyänderungen laden nur ihre Projektion;
  Datenbankinvalidierungen lösen im Optionenowner bewusst keinen Read aus. Der verbleibende
  Dashboardowner liest Fokusschrittlimit und Palette weiterhin aus denselben dauerhaften
  Präferenzen für seine Today-Projektion und die Pausentimer-Vorgabe erst beim tatsächlichen
  Timerstart. Er besitzt aber weder die Vorgabe als Renderzustand noch Kalenderberechtigungs-,
  Kalender- oder Updatestatus des Optionenbildschirms; nur der laufende Timer-Snapshot bleibt
  Today-Zustand.
- Updateangebot, Kalenderpermission, Systemeinstellungen, Installerstart und Fehler sind stabile
  `OptionsRequest`s mit ID und expliziter Bestätigung. Ihr Saved-State-Adapter persistiert nur
  vollständig validierte Releasemetadaten, HTTPS-Quelle, verifizierten Dateipfad und typisierten
  Fehler. Nach Activity-Recreation wird der erste offene Request erneut dargestellt; veraltete
  Dialog-, Installer- und Downloadcallbacks können einen bereits ersetzten Request oder einen
  neueren Workflowzustand nicht verändern.
- `MainActivity` bleibt Android-Host für Permission-, Settings-, Dialog- und Installeradapter,
  enthält aber keine Updateentscheidung und schreibt keinen Optionszustand in den Dashboardowner
  zurück. `UpdateEvent`, `UpdateFlow`, `UpdateUiController` und der separate `UpdateViewModel`
  wurden entfernt. ADR-023 ersetzt den bisherigen Mehrfachownervertrag; ADR-006, ADR-007,
  ADR-008 und ADR-013 verweisen auf die neue Grenze.

Der negative Roadmap-Abgleich fand im ersten Schnitt zwei blockierende Lifecycle-Schwächen.
Ein verspäteter Accept-, Postpone- oder Installercallback hätte noch I/O auslösen können, nachdem
sein Request bereits verschwunden war. Die Nachtarbeitskorrektur konsumiert deshalb atomar exakt
die bestätigte Request-ID und verwirft jede alte Callbackinstanz. Außerdem hatte `onCleared()` den
Updateexecutor mit `shutdownNow()` beendet und damit einen bereits gestarteten Download entgegen
dem Roadmapvertrag unterbrechen können. Der Executor fährt nun geordnet herunter; eine
Ownergeneration verhindert weiterhin jede Publication in den entfernten State. Tests decken
beide Racegrenzen einschließlich eines spät abgeschlossenen Checks nach Owner-Clear ab.

Der erste Gesamtsuite-Abgleich machte zusätzlich einen reihenfolgeabhängigen Legacy-Test sichtbar:
Der Alles-Virtualisierungstest für Holder-Wiederverwendung übernahm den globalen Animator- und
AsyncListDiffer-Zustand vorheriger Robolectric-Tests. Fünf isolierte Läufe waren grün, mehrere
Vollsuiten scheiterten jedoch an derselben Identitätsassertion. Die Nachtarbeit bindet Listdiffs
im Test synchron und deaktiviert ausschließlich für diese Identitätsprüfung den ItemAnimator;
Animationsverhalten bleibt separat getestet. Danach bestanden sowohl die vollständige Suite als
auch fünf vollständig frische Wiederholungen der neuen Options-, Recreation-, Routing- und
Architekturverträge.

Der Rebase auf den inzwischen veröffentlichten Timerstand fand eine weitere blockierende
Integrationsschwäche: `PreferenceInvalidationSource` nahm die neue Pausentimer-Vorgabe zwar vom
SharedPreferences-Listener entgegen, ihr `distinctUntilChanged` verglich jedoch weiterhin nur
Theme und Fokuslimit. Eine reine Timeränderung wurde dadurch als identisch verworfen und die
sichtbare Optionsprojektion blieb bei 60 Sekunden. Die Integrations-Nachtarbeit nimmt den Wert in
den typisierten Vergleich auf, führt ihn durch `OptionsAction` und `OptionsScreenState` und sichert
sowohl den Flow als auch Minus-/Plus-Aktionen und die Ownerpublication. Der Todayowner behält
seinen laufenden `TimerManager.Snapshot` und den noch bis Phase 4e zulässigen
`REQUEST_TIMER_PERMISSIONS`-Hostpfad; Optionen- und Updaterbroker kehren dadurch nicht zurück.
Auf dem nach der Timerintegration rebasierten Stand bestanden die Options-, Preference-,
Recreation-, Architektur- und Timerverträge anschließend fünf vollständig frische
Wiederholungsläufe. Vor der Veröffentlichung rückte `main` mit dem terminbewussten Kombo-Schnitt
#275 erneut vor. Der zweite Rebase war konfliktfrei und veränderte keine Datei dieses
Options-/Timer-Schnitts; der exakte Schema-18-Endstand bestand danach erneut die vollständige
Suite. Ein erster zusammenhängender Versuch wurde während der Tests ohne Failure-XML extern mit
Signal 15 beendet und nicht als Ergebnis gewertet. Die vollständige Testtask sowie Lint und alle
APKs bestanden anschließend in zwei ressourcenbegrenzten Einmal-Gradleprozessen mit Exitcode 0.

Unter Java 21 bestanden lokal 479 Tests ohne Fehler (ein bewusst übersprungener Test), Lint sowie
Debug-, Android-Test- und unsigned Release-APK. Die 14 CI-Harnesstests und 22 Release-/Workflow-
Vertragstests sind ebenfalls grün. Die APK-Größen betragen 8.843.276 Byte Debug, 680.309 Byte
Android-Test und 6.436.450 Byte unsigned Release; der Fontbestand bleibt bei 1.478.008 Byte.
Der aus `main` übernommene Termin-Kombo-Stand verwendet Schema 18; `minSdk 26`, `targetSdk 35`,
Update-Trust/-Transport, Timer-/Kombodomainverhalten und visuelle Verträge wurden durch Phase 4d
nicht verändert.

Der abschließende statische Gegencheck findet im Produktionscode keinen `UpdateEvent`, keinen
`UpdateUiController`, keinen separaten `UpdateViewModel`, keinen verbrauchbaren Options-Eventpfad
und keinen zweiten Kalender-/Update-/Pausentimer-Vorgabestatus im Dashboard-State. Die
verbleibenden Palette- und Fokusschrittlimitreads sowie der Pausentimerwert beim tatsächlichen
Timerstart sind die explizit erlaubte Today-Nutzung gemeinsamer dauerhafter Präferenzen. Für
Phase 4d ist nach der Nachtarbeit keine weitere lokale Nacharbeitsphase nötig.
Phase 4 insgesamt bleibt offen, bis Today in der folgenden Unterphase einen eigenen
`TodayScreenState`-Owner erhält und die letzten Dashboard-/`UiEvent`-Brokerpfade entfallen. Der
Abschluss von 4d erfordert nun den eigenen grünen Pull Request, Squash-Merge und den
veröffentlichenden `main`-Lauf; die physische In-App-Update- und Sichtabnahme bleibt gemäß
Owner-Entscheidung ausdrücklich offen.

### Phase 4d – Remote-Abschluss

Pull Request #276 bestand Quality sowie normale und animationsaktive Instrumentierung auf API
26/35/37 und wurde als `3718e38c` per Squash nach `main` übernommen. Der Produktionslauf
`32878950109` bestand dieselbe Matrix, signierte Paketierung und echtes Upgrade vom vorherigen
Produktionsstand auf allen drei API-Stufen. Release 0.2.128 (`forest-android-1012801`)
veröffentlicht `AutoSecretary.apk` und `release-metadata.json` exakt aus
`3718e38cce2ab4147974a4ab5795a165945cfb11`; Tag und Remote-`main` zeigen auf denselben Commit,
und der heruntergeladene APK-Hash `2646578e421d5aa42bf331493227a3e15c60855a586cdf25fa1f5591edc2af52`
stimmt bytegenau mit Asset und Metadaten überein. Die automatisierte Unterphase 4d ist damit
abgeschlossen; die physische In-App-Update- und Sichtabnahme bleibt gemäß Owner-Entscheidung
offen.

### Phase 4e – Vorprüfung und Implementationsplan für den Today-State-Owner

Roadmap, ADR-022, der veröffentlichte 4d-Stand, `TaskViewModel`, `DashboardUiState`,
`DashboardRenderer`, `MainActivity`, Today-Reducer/-Coordinator, Timer- und Rewardpfade sowie die
zugehörigen Recreation-, Architektur- und Interaktionstests wurden vor Produktänderungen erneut
gelesen. Als letzte Phase-4-Mehrfachwahrheit verbleibt ein 681 Zeilen breiter
`TaskViewModel`: Er veröffentlicht Today und Shellnavigation gemeinsam als LiveData, hält
Reorderzustand und Todayprojektion doppelt, transportiert Dialoge und Timerberechtigung über
verbrauchbare `UiEvent`s und veröffentlicht Rewards über einen zweiten LiveData-Kanal. Leerer
Today-Zustand und Timeline-Menü umgehen den Action-Owner zusätzlich über `DashboardEvent` und
lassen die Activity Fachcommands direkt aufrufen.

Phase 4e entfernt diesen Rest in einem kohärenten Schnitt. `TodayViewModel` veröffentlicht genau
einen `StateFlow<TodayScreenState>`. Der atomare Zustand enthält genau eine Todayprojektion im
bestehenden `TodayFeatureState`, Loading und laufende Command-IDs, Wiederholungseingabe,
Fokusschrittlimit, laufende Timer, die bestätigbare Rewardfolge sowie eine geordnete Menge
stabiler `TodayRequest`s. `TodayAction` bleibt der einzige serielle Eingang und wird um
leeren-Zustand-Navigation, Timeline-Menüauswahl, Verschieben, Löschen, Requestquittierung und
Rewardquittierung ergänzt. Fehler, Hinweise, Menü, Lösch-/Schließbestätigung und
Timerberechtigungswarnung bleiben mit stabiler ID bis zur expliziten Antwort im State und werden
über den `SavedStateHandle` rekonstruiert. Fachwrites bleiben seriell und werden nach Start nicht
abgebrochen; neuere Reads dürfen weiterhin ältere Reads verdrängen.

Shellnavigation und globale Palette sind keine Today-Verantwortung. Ein kleiner
`AppShellViewModel` veröffentlicht dafür `StateFlow<AppShellScreenState>`, persistiert die
Tab-Auswahl und beobachtet ausschließlich die gemeinsame Darstellungsquelle. Die Activity bindet
Shell und vier Screen-Owner lifecyclegebunden, aggregiert sie nur für die bestehende
View-Hierarchie und führt keine Today-Fachoperation aus. `DashboardRenderer` nimmt Shell- und
Today-State getrennt entgegen und sendet aus dem Today-Baum ausschließlich `TodayAction`s;
`TaskViewModel`, `DashboardUiState`, `DashboardEvent` und `UiEvent` werden entfernt. Der direkte
Headerflug bleibt der in 4c beschlossene rein visuelle Navigator-Hook und wird nicht zu einem
zweiten Zustandskanal.

Zu prüfen sind Activity- und Prozesswiederherstellung mit offenem Menü, Bestätigungs- und
Timerrequest, semantische Deduplizierung und monoton restaurierte Request-IDs, atomarer Übergang
vom Menü zur Bestätigung, verspätete Dialogcallbacks, Rewardquittierung ohne fremden
Zustandsverlust, Reorder während Recreation, Timerpublication, Tageswechsel sowie die statische
Abwesenheit aller alten LiveData-/Dashboardbroker. Visuelle Baselines, Domainverhalten, Room-
Schema 18, `minSdk 26`, `targetSdk 35`, Updatevertrag und die späteren Compose-Cutover-Phasen
bleiben unverändert. Die vom Owner erlaubte Fortsetzung ohne Handy betrifft nur das Gate: Die
physische Geräteabnahme bleibt offen und wird nicht als bestanden gewertet.

Während der Implementation rückte `origin/main` mit dem konfigurierbaren Kombo-/Backlog-Schnitt
#277 vor. Vor dem Abschluss wurde der Branch deshalb auf diesen Stand aktualisiert und die
Roadmap sowie der neue Präsentations- und Preferenceumfang erneut gegengeprüft. Der Rebase war
konfliktfrei. Kombopolicy, Editor-Backlogwahl, Schema 18 und die zugehörigen Optionsfelder bleiben
unverändert erhalten; Today und Shell lesen daraus keine neue fachfremde Zustandswahrheit.

Unmittelbar vor Commit rückte `origin/main` erneut vor. Der veröffentlichte Mengenreward- und
Tagesabschluss-Schnitt #278 ergänzt `FINISH_STEP`, den zugehörigen Use Case, quantitative
Rewardprojektionen und Schema 19 und verwendet bereits ADR-025. Phase 4e wurde deshalb nochmals
auf den exakten Remote-Stand aktualisiert: Der neue Handler liegt nun im `TodayViewModel`, die
neuen Actions durchlaufen unverändert Coordinator und Dispatcher, und diese ADR wurde
konfliktfrei als ADR-026 nummeriert. Die vollständigen lokalen Nachweise wurden erst nach dieser
Integration erneut ausgeführt.

Der erste Integrationslauf fand genau eine mechanische Reststelle: Der in #278 neu hinzugekommene
Viewtest instanziierte noch den gleichzeitig entfernten `DashboardEventRecorder`. Er verwendet
nun ebenfalls den reinen `TodayActionRecorder`; produktiver Code war davon nicht betroffen.

### Phase 4e – Implementation und Roadmap-Abgleich

- `TodayViewModel` besitzt nun den einzigen `StateFlow<TodayScreenState>` des Today-Bildschirms.
  Der Zustand führt `TodayFeatureState`, Loading und laufende Commands, den
  Wiederholungseingabezustand, Fokusschrittlimit, Timer-Snapshot, Rewardfolge und geordnete
  `TodayRequest`s in einer unveränderlichen Publication zusammen. `TodayAction` ist der einzige
  Eingangsweg für View-, Dialog- und Hostaktionen; Fachwrites laufen seriell auf dem bestehenden
  Worker und werden beim Ownerende nur geordnet heruntergefahren.
- Menü, Verschiebeauswahl, Lösch-/Schließbestätigung, Hinweise, Fehler und
  Timerberechtigungswarnung besitzen stabile IDs und explizite Quittierung. Der
  Saved-State-Adapter rekonstruiert nur validierte Requests, leitet die nächste Sequenz auch aus
  restaurierten IDs ab und verwirft beschädigten oder veralteten Zustand. Menüfolgeschritte
  ersetzen den Ausgangsrequest atomar mit neuer ID; ein verspäteter Callback der alten
  Dialoginstanz kann weder den Nachfolger entfernen noch einen Write starten.
- `AppShellViewModel` besitzt Top-Level-Auswahl und globale Legacy-Palette in einem eigenen
  `StateFlow<AppShellScreenState>`. Die Auswahl wird restauriert, Palette und Uhr werden aus der
  gemeinsamen Invalidierungsquelle projiziert. Today beobachtet bei Darstellungsänderungen nur
  sein Fokusschrittlimit; reine Minutenticks führen dort keinen überflüssigen Preference-Read
  mehr aus.
- `DashboardRenderer` bindet Shell- und Today-State getrennt und emittiert auch aus leerem Today
  und Timeline-Menüs ausschließlich `TodayAction`. `MainActivity` verdrahtet die fünf Owner,
  rendert Views und hostet Android-Dialoge, Permissions und Animationen, führt aber keine
  Today-Use-Cases aus. `TaskViewModel`, `DashboardUiState`, `DashboardEvent`,
  `DashboardEventSink` und `UiEvent` samt zweitem Event-/Reward-LiveData-Kanal wurden entfernt.
  ADR-026 dokumentiert diese Zwischenarchitektur bis zu den Compose- und Navigation-3-Cutovern.

Der negative Roadmap-Abgleich fand drei relevante Schwächen im ersten Schnitt. Erstens hätte der
positive Callback eines bereits ersetzten Timerberechtigungsdialogs noch Android-System-I/O
starten können. Die Nachtarbeitskorrektur prüft und konsumiert deshalb unmittelbar vor dem
Permissionaufruf exakt die noch offene Request-ID. Zweitens hätte eine während des Rewardflugs
zerstörte Activity über den alten Animationscallback den vom neuen Host erneut dargestellten
Reward quittieren können. `RewardAnimator.dispose()` invalidiert nun die Generation, bricht die
alte Viewanimation ab und unterdrückt sowohl Quittierung als auch Glint des zerstörten Hosts;
der Reward verbleibt dadurch für die neue Activity im Ownerzustand. Drittens las Today über die
alte Appearance-Route bei jedem Minutentick die Preferences neu. Eine eigene
`todayPreferenceChanges`-Route beschränkt diesen Read auf echte Displayänderungen, während der
Shellowner die zeitabhängige Palette weiter aktualisiert.

Der Gesamtsuite-Abgleich fand außerdem einen veralteten Metatest: Er verlangte, dass der
Android-freie `TodayCoordinator` auch die neuen Screen-/Hostactions behandelt. Der Vertrag prüft
nun bewusst die vollständige Vereinigung aus `TodayViewModel` und Kern-Dispatcher, während der
Kern für direkt fehlgeleitete Actionarten weiterhin hart fehlschlägt. Zusätzliche Tests sichern
die Recreation eines offenen Bestätigungsintents, Requestrestauration und monotone
Menütransition, wirkungslose alte IDs, voneinander unabhängige Reward-/Requestquittierung,
restaurierte Shellnavigation, getrennte Preferenceprojektion und die Abwesenheit der alten
Brokerklassen.

Unter Java 21 bestanden lokal 492 Tests ohne Fehler (ein bewusst übersprungener Test), Lint sowie
Debug-, Android-Test- und unsigned Release-APK. Die 14 CI-Harnesstests und 22
Release-/Workflow-Vertragstests sind ebenfalls grün. Die APK-Größen betragen 8.849.272 Byte
Debug, 680.410 Byte Android-Test und 6.458.826 Byte unsigned Release; der Fontbestand bleibt bei
1.478.008 Byte. Der aus #278 übernommene Stand verwendet Schema 19; `minSdk 26`, `targetSdk 35`,
Domain-, Kombo-, Mengenreward-, Updater-, Signatur- und visuelle Verträge wurden durch Phase 4e
nicht verändert.

Die kritischen Request-, Reward-, `FINISH_STEP`-, Recreation-, Shell-, Routing- und Architekturverträge
bestanden anschließend fünf vollständig frische Wiederholungsläufe. Der statische Gegencheck
findet in produktivem Today-/Shell-Code kein LiveData, keinen alten Dashboard-/Eventbroker,
keinen direkten Today-Use-Case-Aufruf der Activity und keinen zweiten Navigations- oder
Palettezustand. Nach der Nachtarbeit ist keine weitere lokale Nacharbeitsphase für 4e nötig.
Phase 4 erfüllt damit lokal ihren Architekturumfang; ihr automatisierter Abschluss erfordert nun
den eigenen grünen Pull Request, Squash-Merge und den veröffentlichenden `main`-Lauf. Die
physische In-App-Update- und Sichtabnahme bleibt gemäß Owner-Entscheidung ausdrücklich offen und
wird nicht als bestanden gewertet.

### Phase 4e – Remote-Abschluss

Pull Request #279 bestand Quality sowie normale und animationsaktive Instrumentierung auf API
26/35/37 und wurde als `10e1777e` per Squash nach `main` übernommen. Der Produktionslauf
`32893632832` bestand zusätzlich Paketbau und Upgradeprüfungen auf API 26/35/37 und veröffentlichte
Release 0.2.131 mit Tag `forest-android-1013101`. Remote-`main`, Tag, Releaseziel und
`release-metadata.json` zeigen auf den vollständigen Merge-Commit
`10e1777e481a1091e5dcd60d6b5905fb40f14a67`. Die separat heruntergeladene APK hat 6.467.018 Byte
und den sowohl vom Release-Asset als auch von den Metadaten deklarierten SHA-256
`75d479bd3d35a5a9bd598e8edf9d103b74b77e9d9a3f9b4306c7114ec2b634c8`. Phase 4e ist damit
automatisiert abgeschlossen. Die physische In-App-Update- und Sichtabnahme bleibt offen.

### Phase 5a – Vorprüfung und Implementationsplan für den Compose-Editor

Roadmap, ADR-022, der veröffentlichte 4e-Stand, der autoritative Editor-Referenzvertrag,
`TaskEditorViewModel`, `EditorUiState`, Reducer, Formatter, `TaskEditorCoordinator`, die 1.003
Zeilen breite `TaskEditorView`, die separate Schrittansicht sowie Golden-, Adaptive-, Acceptance-
und Instrumentierungsverträge wurden vor Produktänderungen erneut gelesen. Phase 5 ist als ein
einziger reviewbarer Schnitt zu groß: Wizard, Schrittdetail, Prompts, Fokus-/Scrollrestauration,
adaptive Geometrie, Barrierefreiheit, produktiver Cutover und Entfernen der Legacy-Orchestrierung
würden sonst gleichzeitig zwei Renderer und zu viele Nachweisarten berühren.

Phase 5 wird deshalb vor dem ersten Produktcode geteilt. 5a implementiert den vollständigen
Compose-Renderer gegen den bestehenden einzigen `EditorUiState` und dessen Reducer. Er führt
keinen eigenen Draft und keinen zweiten State Owner ein; Texteingaben und Navigation publizieren
stets zuerst einen neuen autoritativen Zustand über den vorhandenen Actionpfad. Der Renderer wird
zunächst nur über einen Test-/Vergleichshost montiert, während der produktive Editor unverändert
bleibt. Kanonische und adaptive Szenarien, Semantik, Mindestzielgrößen, Fokus, Scrollposition,
Promptdarstellung und Actiongleichheit werden Side-by-Side gegen die vorhandenen Verträge
geprüft. Compose Foundation wird ohne Material oder allgemeines Design-System ergänzt; Palette,
Typografie, Formatter, Reducer, Motiontoken und visuelle Baselines bleiben autoritativ.

5b übernimmt danach auf einem eigenen Branch das produktive Mounting, die vollständigen
Interaktions-, Recreation-, Save-/Delete-/Close- und Animationsnachweise und entfernt nach dem
automatisierten Cutover `TaskEditorView`, `TaskStepsEditorView` und ihre reine View-Orchestrierung.
Ein Screen besitzt auch während der Migration nur einen mutierenden State Owner; Side-by-Side
bedeutet ausschließlich zwei Renderer desselben unveränderlichen Testzustands. Schema 19,
Domainverhalten, SDK-Grenzen, Updatevertrag und Produktgestaltung bleiben unverändert. Gemäß
Owner-Anweisung wird ohne verfügbares Handy fortgefahren; die physische Geräteabnahme bleibt
ausdrücklich offen und wird nicht als bestanden gewertet.

### Phase 5a – Implementation und lokale Nachweise

Der vollständige Editor liegt nun als fünf getrennte Compose-Bausteine im Debug-Quellsatz:
Hostgrenze, Screen-/Prompt-/Footer-Orchestrierung, Primitive, Wizardseiten und Schritteditor. Alle
vier Seiten, das Schrittdetail, Rhythmus-, Zeit-, Mengen-, Pausen- und Begrenzungsfelder,
Zusammenfassung, Löschen/Verwerfen, Validierungsfokus, seitenbezogene Scrollrestauration und die
kompakte 4+3-/Zweizeilen-Geometrie werden aus dem bestehenden `EditorUiState` gerendert. Jede
Eingabe läuft zuerst über `TaskEditorComposeDispatcher`, vorhandenen Reducer und Listener zum
`TaskEditorViewModel`; der Host hält lediglich die zuletzt vom Owner zurückgebundene Renderkopie.
System-Zurück bleibt Hostverantwortung und verwendet denselben Dispatcherpfad.

Ein nicht exportierter `TaskEditorComposeHarnessActivity` speichert und restauriert exakt den
autoritativen Bundlezustand. Der reale Compose-Instrumentierungsvertrag prüft Texteingabe und
Wizardnavigation, Recreation, einen über Recreation stabilen Prompt mit verborgener
Hintergrundsemantik, Host-Zurück, Mindestzielgrößen und Schrittaktionen. Der native
Robolectric-Vergleich rendert alle zehn kanonischen und fünf adaptiven Szenarien gegen die
unveränderten freigegebenen Legacy-Baselines; die Compose-spezifische Grenze beträgt höchstens
25 Prozent geänderte Pixel bei einer Kanaltoleranz von 64. Dieser Vergleich ist strikt
schreibgeschützt und kann nur Ist-/Diff-Artefakte, nie eine neue Legacy-Baseline erzeugen.
Material, unabhängige Farben, ein zweiter Draft und ein produktives Mounting werden durch
Architekturverträge ausgeschlossen.

Lokal bestanden unter Java 21 alle 499 Tests ohne Fehler bei einem bewusst übersprungenen Test,
darunter alle 15 Compose-Bildvergleiche, außerdem Lint, Debug-, Android-Test- und unsigned
Release-Paketierung. Der neue Instrumentierungstest kompiliert und ist Bestandteil der
Gerätematrix; ohne verbundenes Gerät wurde er lokal nicht als ausgeführt gewertet. Die 14
CI-Harnesstests und nun 23 Release-/Workflow-Vertragstests sind ebenfalls grün. Aus einem sauberen
Paketbuild messen Debug 5.104.140 Byte, Android-Test 1.350.328 Byte und unsigned Release
6.458.826 Byte; die Fonts bleiben bei 1.478.008 Byte. Ein zusätzlich mit dem lokalen Testschlüssel
signierter Release-Kandidat misst 6.467.018 Byte und bleibt damit klar unter 8 MiB.

### Phase 5a – Roadmap-Abgleich und Nachtarbeit

Der negative Abgleich fand vier substanziellere Fehler im ersten Schnitt. Erstens verwendete die
Seitenanimation den `AnimatedContent`-Zielzustand nicht und hätte während des Übergangs zweimal
den neuen Seiteninhalt zeichnen können. Sie rekonstruiert nun für alten und neuen Schlüssel je
den passenden Navigationszustand und dessen eigene Scrollposition. Zweitens leitete die adaptive
Breite sich aus gerundeten Konfigurationswerten statt aus der wirklichen Fensterfläche ab; sie
verwendet nun die Containergröße und bleibt damit bei Insets und Mehrfensterbetrieb stabil.
Drittens waren Renderer und Foundation zunächst bereits Produktionsinputs. Das war trotz
fehlendem Mounting weder ein sauberer Strangler-Schnitt noch nachhaltig für das Releasebudget.
Viertens besaßen die zunächst verwendeten Golden-Helfer trotz Vergleichscharakter einen expliziten
lokalen Schreibschalter auf die freigegebenen Legacy-Dateien. Eine eigene Read-only-Schnittstelle
entfernt diesen Pfad; der Architekturtest verbietet Compose-Updatevariablen.

Der erste entfernte Gerätematrixlauf `32909863594` deckte eine fünfte, gemeinsame Paketgrenze auf:
Alle sechs API-/Animationsjobs installierten beide APKs, starteten aber null Tests, weil
`AndroidJUnitRunner` bereits beim Aufbau seines Testverzeichnisses mit fehlendem
`kotlin.LazyKt` abstürzte. Der nachfolgende Nullfehler im Android-Gradle-Testreport war nur ein
Sekundärfehler beim Einlesen dieses abgebrochenen Laufs. Ziel- und Test-APK werden getrennt
minifiziert; der aus der Test-APK kommende Aufruf ist für R8 beim Schrumpfen der Ziel-APK nicht
sichtbar. Ein erster enger Debug-Anker stellte den Runnerstart wieder her, und alle drei
Animationsjobs des zweiten Laufs `32912071533` waren grün. Die vollständigen Jobs fanden danach
jedoch weitere unsichtbare Kanten: Room benötigte `kotlin.collections.CollectionsKt`, Compose-Test
`CompletableJob.complete()`. Punktuelle Keeps oder weitere künstliche Anker wären damit ein
fragiler, unvollständiger Parallelvertrag für fremde Testbibliotheken geworden.

Die zweite Nachtarbeit ersetzt diesen verworfenen Ansatz durch einen eigenen, ungeschrumpften
`instrumentation`-Buildtyp. Er verwendet exakt denselben Debug-Quellsatz mit Renderer und
Harnesses, während nur das installierbare Debug-Produkt für sein 10-MiB-Budget geschrumpft wird.
`androidTest` kompiliert und läuft ausschließlich gegen diesen vollständigen Zielgraphen;
Instrumentierungsrunner, Quality-Bau, Release-Upgrade-Testpaket und Dokumentation verwenden den
neuen Variantennamen. Da AGP 9.2 den Test-Buildtyp gemeinsam für Geräte- und lokale Tests wählt,
läuft auch dieselbe Unit-/Golden-Suite nun als `testInstrumentationUnitTest`; Produkt-Lint und
Größengate bleiben auf Debug. Der Releasevertrag verbietet die alte Debug-Testpaketierung.
Produkt-Release und Editorzustand bleiben davon unberührt. Lokal kompiliert der neue Android-Testkandidat mit
1.436.280 Byte; sein ungeschrumpftes, nicht auszulieferndes Instrumentierungsziel misst
11.445.777 Byte und unterliegt bewusst nicht dem 10-MiB-Produktgate. Beide enthalten die im
gescheiterten Lauf fehlenden Kotlin-/Coroutine-Klassen.

Das vollständige lokale Quality-Gate lief anschließend in 11 Minuten 35 Sekunden grün: 499
Unit-/Robolectric-/Golden-Tests ohne Fehler bei einem bewussten Skip, Debug-Lint, kompaktes
Debug-APK, neuer Android-Testkandidat und unsigned Release. Die finalen Paketgrößen bleiben bei
5.104.140, 1.436.280 und 6.458.826 Byte; zusätzlich sind alle 14 CI-Harness- und 23
Release-/Workflow-Verträge grün.

Die Nachtarbeit verschiebt deshalb den gesamten 5a-Renderer samt Foundation und Animation in den
Debug-Quellsatz. Release enthält weder diese Bibliotheken noch UI-Testcode und ist bytegleich zum
Produktumfang vor 5a; erst 5b verschiebt den Renderer beim echten Cutover in den
Produktions-Quellsatz und entfernt gleichzeitig die Legacy-Orchestrierung. Für Debug entfernt R8
ausschließlich unbenutzte
Abhängigkeitsklassen; alle eigenen App- und Harnessklassen sowie Quelldatei-/Zeileninformationen
bleiben explizit erhalten. Das hält das 10-MiB-Gate ein, verlängert einen vollständig sauberen
Paket-/Lintlauf jedoch auf rund zwölf Minuten. Normales Kompilieren und die Unit-/Golden-Schleife
bleiben davon getrennt; die Remote-Gerätematrix muss nun den ungeschrumpften, debuggleichen
Instrumentierungsziel- und Testkandidaten vor Abschluss belegen.

Der abschließende statische Gegencheck findet kein Material, keine unabhängige Farbkonstante,
keinen zweiten mutierenden Editorzustand, kein Compose-Mounting im produktiven Coordinator und
keine Foundation-, Animation- oder UI-Test-Abhängigkeit im Release-Laufzeitpfad. Eine weitere
lokale Nachtarbeitsphase ist damit nicht begründet. Phase 5a bleibt bis zum eigenen grünen Pull
Request, Squash-Merge und veröffentlichenden `main`-Lauf offen. Die physische Sicht- und
In-App-Update-Abnahme bleibt gemäß Owner-Anweisung offen und wird nicht als bestanden gewertet.
