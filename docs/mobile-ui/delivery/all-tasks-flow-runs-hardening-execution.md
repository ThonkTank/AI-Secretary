# Ausführung: mobile Darstellungs- und Verifikationshärtung

- Status: Phase 1 in Arbeit
- Owner: AutoSecretary
- Last Reviewed: 2026-09-09
- Source of Truth: Append-only Ausführungsnachweise zu dieser Roadmap

Kanonische Grundlage:
[Roadmap: mobile Darstellungs- und Verifikationshärtung](all-tasks-flow-runs-hardening-roadmap.md)

Dieses Protokoll ist append-only. Phasenplan, lokale Validierung, Audit, Korrekturrunden,
Pull-Request-Gate, Squash-Merge und exakter Main-Nachweis werden getrennt ausgewiesen.

## Phase 1 – Gemeinsame Darstellung und lebende Restzeit

Status: in Arbeit

Ausgangsstand: `493e7b0d9bac` (`origin/main`)

### Plan – 2026-09-09

Ergebnis: Alles-Vorschau und Ablaufmonitor verwenden dieselben kleinen mobilen
Darstellungsprimitiven und dieselbe reine Restzeitformatierung. Ein vorhandener Minutentakt
liefert über die bestehenden State-Owner einen expliziten Epoch-Zeitpunkt, sodass beide
Oberflächen während der sichtbaren Laufzeit aktualisieren, ohne Ablaufdaten erneut zu laden.

Betroffene Komponenten und Schnittstellen:

- `presentation.mobile` als neue zustandslose Grenze für Typografie, Farben, Blattflächen,
  Aktionsschaltflächen und Restzeitformatierung;
- `ClockInvalidationSource` und `ClockSnapshot` für einen zur vorhandenen lokalen Zeit
  kohärenten Epoch-Darstellungswert;
- `AppShellScreenState`/`AppShellViewModel`, `DashboardRenderer` und der Alles-Compose-Host für
  die vorhandene Shell-/Alles-Zeitprojektion;
- `FlowRunsScreenState`/`FlowRunsViewModel`, Activity und Compose-Host für die eigenständige
  Ablaufmonitor-Zeitprojektion;
- Alles- und FlowRuns-Präsentationstests sowie vorhandene Clock-/State-Owner-Tests.

Umsetzungsreihenfolge:

1. Clock-, State-, Host- und Screen-Flüsse mit Tests als aktuelle Basis erfassen.
2. Reine gemeinsame Restzeitformatierung samt Grenzwerttests einführen.
3. Kleine visuelle Primitive extrahieren und beide Screens ohne beabsichtigte visuelle Änderung
   migrieren.
4. Den Epoch-Zeitpunkt atomar aus derselben Clock-Lesung erzeugen, in die beiden State-Owner
   aufnehmen und bis zu den Screens reichen.
5. Direkte Wanduhraufrufe aus den beiden Darstellungswegen entfernen und Minutentick-/Foreground-
   Verhalten mit festen Uhren prüfen.
6. Fokussierte Tests, Goldens, Architekturgrenzen, vollständigen lokalen Android-Gate und
   `git diff --check` ausführen.
7. Implementierung getrennt gegen diesen Plan und die vollständige Roadmap auditieren. Vor jeder
   notwendigen Korrektur zuerst einen Fixplan in diesem Protokoll ergänzen.

Abnahme: Sichtbare Restzeiten aktualisieren minutengenau ohne fachliches Reload; gemeinsame
Primitiven und Formatter sind frei von fachlichen Besitzern; bestehende Layouts und Aktionen
bleiben erhalten; Editor-, Domain-, Use-Case-, Repository- und Room-Pfade bleiben unverändert;
alle lokalen und Remote-Gates sind grün.

### Quellenbasis

- Lokaler Code: `app/src/main/kotlin/de/thonktank/autosecretary/data/observable/ClockInvalidationSource.kt`
- Lokaler Code: `app/src/main/java/de/thonktank/autosecretary/presentation/shell/AppShellViewModel.java`
- Lokaler Code: `app/src/main/kotlin/de/thonktank/autosecretary/presentation/alltasks/AllTasksRunningFlows.kt`
- Lokaler Code: `app/src/main/kotlin/de/thonktank/autosecretary/presentation/flowruns/FlowRunsComposeScreen.kt`
- Architekturvertrag: `docs/architecture/adr-034-mobile-aufgabenverwaltung-und-ablaufmonitor.md`

## Ereignis – 2026-09-09 – Phase 2 Korrekturplan 3

Remote-Befund:

- PR `#353`, Workflow `34344809500`: Quality ist in 6:04 Minuten grün; der normale
  API-26-Lauf führt alle fünf FlowRuns-Tests aus. Navigation, angebotener Run,
  zeitwartender Run und Zustände sind grün.
- Nur der kapazitätswartende Run scheitert vor dem ersten Klick: Sein unterhalb des sichtbaren
  Bereichs liegender LazyColumn-Eintrag ist noch nicht komponiert, daher kann die direkte
  Knotensuche `performScrollTo()` nicht auf ihm ansetzen. Der Fehlernachweis meldet explizit
  keinen passenden Knoten für `flow_runs_action_move_up__capacity`.
- Produktzustand, Callback und Semantikkennung sind nicht falsch; der Test muss die
  virtualisierte Liste über ihren bereits vorhandenen Screen-Knoten zum Ziel scrollen.

Korrektur:

1. Im Aktionshelfer zuerst auf dem FlowRuns-Screen mit `performScrollToNode` zur stabilen
   Zielkennung scrollen und den danach komponierten Aktionsknoten klicken.
2. Aktionsmenge, frische Activity je Kontext, exakt-einmalige Callback-Prüfung und alle
   Produktdateien unverändert lassen.
3. Android-Test-Kompilierung, fokussierte Verträge, vollständigen lokalen Android-Gate und
   `git diff --check` erneut ausführen; danach den vollständigen PR-Workflow auf einem neuen
   Korrekturcommit frisch starten.

## Ereignis – 2026-09-09 – Phase 2 Korrektur 3 lokal validiert

- Der Aktionshelfer scrollt den vorhandenen FlowRuns-Screen per `performScrollToNode` zur
  stabilen Aktionskennung; erst danach wird der nun komponierte Zielknoten geklickt.
- Die fünf kontextbezogenen Tests, alle zwölf Aktionsprüfungen und ihre exakt-einmaligen
  Callback-Assertions bleiben erhalten. Produktcode wurde nicht geändert.
- Android-Test-Kompilierung und fokussierte FlowRuns-Verträge: grün in 49 Sekunden.
- Vollständiger lokaler Android-Gate einschließlich neu gebauter Instrumentierungs-Test-APK:
  grün in 6:27 Minuten.
- CI-Helfertests: 25 grün; Release-Helfertests: 30 grün; `git diff --check`: grün.
- Der dritte Remote-Lauf bestätigt Quality, API 37 und alle Animationspfade; seine normalen
  API-26/35-Fehler sind derselbe durch diese Korrektur behobene LazyColumn-Zielzugriff.
  Ein neuer vollständiger PR-Workflow bleibt erforderlich.

## Ereignis – 2026-09-09 – Phase 2 lokal validiert

Umgesetzt:

- `FlowRunsComposeScreen` enthält nur noch Zustandsverzweigung, Listenanordnung, Header und die
  Weitergabe an getrennte Status- beziehungsweise Kartenkomponenten.
- Ablaufkarte, sichtbare Ablaufaktionen sowie Status-/Ressourcenpräsentation liegen in eigenen
  paketlokalen Dateien und verwenden weiterhin die unveränderten fachlichen Modelle und
  Callbacks.
- `FlowRunsSemantics` definiert stabile Kennungen für Screen, Zustände, Karten und jede Aktion;
  der Screen exportiert Compose-Testtags als Android-Ressourcen-IDs.
- Compose-Instrumentierung und API-37-UIAutomator verwenden ausschließlich diese Kennungen für
  Interaktionen. Der API-37-Test ist nicht mehr an deutsche Texte oder Content-Descriptions
  gekoppelt.
- Der frühere Toast und `handledErrorId` wurden entfernt. Die zugängliche Inline-Meldung besitzt
  eine Schließen-Aktion, die ihre konkrete `errorId` genau einmal über den typisierten Callback
  an `FlowRunsAction.acknowledgeError` weitergibt.
- Die bisherigen FlowRuns-/Mobile-Quelltext-Suchtests wurden durch ausführbare State-,
  Semantik- und Host-Weiterleitungsverträge ersetzt.
- ADR-034 beschreibt die nun umgesetzte Komponenten-, Semantik- und Fehlergrenze.

Lokale Nachweise:

- alle betroffenen Produkt-, Unit-Test- und Android-Test-Varianten: kompilieren grün;
- Fehlerpersistenz, identitätsgebundene Quittierung und eindeutige Semantikkennungen: grün;
- genau-einmalige Weiterleitung sämtlicher sichtbarer Ablaufaktionen: als Android-Test
  kompiliert; mangels lokal verbundenem Gerät folgt die Ausführung im PR-Matrixgate;
- alle acht Ablaufmonitor-Goldens: grün; das visuell geprüfte Fehler-Golden enthält als einzige
  beabsichtigte Layoutänderung den Inline-Schließen-Button;
- vollständiger Android-Gate
  (`testInstrumentationUnitTest`, `lintDebug`, `assembleDebug`,
  `assembleInstrumentationAndroidTest`, `assembleRelease`): grün in 16:32 Minuten;
- CI-Helfertests: 25 grün; Release-Helfertests: 30 grün;
- APK-Größen: Debug 5.700.141 Bytes, Instrumentierung 1.636.722 Bytes,
  Release unsigned 2.837.812 Bytes;
- `git diff --check`: grün.

Getrennter Plan-/Roadmap-Audit:

- Phasenplan vollständig erfüllt: Orchestrierung, Karte, Aktionen und Status/Ressourcen sind
  getrennt; alle Interaktionskennungen sind zentral und ausführbar verwendet.
- Im Ablaufmonitor existiert genau ein Fehlerkanal. Der State bewahrt Fehler über reine
  Darstellungsänderungen und falsche Quittierungs-IDs hinweg; nur die passende explizite Aktion
  entfernt ihn.
- Die beiden FlowRuns-Android-Tests enthalten keine sichtbaren Text- oder
  Content-Description-Selektoren. Der API-37-Pfad verwendet `By.res` auf den exportierten
  Kennungen.
- `FlowRunsComposeScreen.kt` enthält keine Karten-, Aktions-, Status-, Ressourcen- oder
  Restzeitimplementierung; diese Grenzen werden durch typisierte Aufrufe und ausführbare
  Verträge statt Quelltextfragmenten getragen.
- Bestehende Dialoge, Aktionen, Sperrzustände, ViewModel- und Use-Case-Grenzen bleiben erhalten.
  Editor, Domain, Use Cases, Repository und Room sind unverändert.
- Keine Abweichung und damit kein Korrekturplan erforderlich. Remote-Ausführung der Android-
  Interaktionsmatrix und Remote-Abschluss stehen noch aus.

## Ereignis – 2026-09-09 – Phase 2 Korrekturplan 1

Remote-Befund:

- PR `#353`, Workflow `34340790267`: Quality grün; der normale Instrumentierungspfad auf
  API 26 und API 35 verwirft `FlowRunsComposeInstrumentationTest` mit
  `InvalidTestClassError`.
- Die Diagnoseartefakte zeigen denselben Initialisierungsfehler: Die private Hilfsmethode
  `click(tag, expectedAction)` trägt versehentlich die vom entfernten Vorgängertest
  übriggebliebene `@Test`-Annotation. JUnit fordert sie deshalb als öffentlichen parameterlosen
  Test an; keine FlowRuns-Interaktion wurde in diesen beiden Jobs ausgeführt.
- Die Produktimplementierung und alle anderen bis dahin gelaufenen Tests sind von diesem
  Klassifizierungsfehler nicht betroffen.

Korrektur:

1. Ausschließlich die irrtümliche `@Test`-Annotation der Hilfsmethode entfernen und die Methode
   zur eindeutigen Lesbarkeit in `clickAction` umbenennen.
2. Android-Test-Kompilierung, die betroffene Host-/State-Suite, vollständigen lokalen
   Android-Gate und `git diff --check` erneut ausführen.
3. Korrektur committen und auf denselben PR-Branch pushen. Keine fehlgeschlagene Matrixspur
   einzeln wiederholen; der neue Commit muss den vollständigen PR-Workflow frisch durchlaufen.

## Ereignis – 2026-09-09 – Phase 2 Korrektur 1 lokal validiert

- Die Hilfsmethode heißt nun `clickAction` und trägt keine Testannotation mehr; die beiden
  eigentlichen Testmethoden bleiben unverändert.
- Android-Test-Kompilierung und fokussierte FlowRuns-State-/Golden-Verträge: grün.
- Vollständiger lokaler Android-Gate einschließlich neu gebauter Instrumentierungs-Test-APK:
  grün in 6:26 Minuten; `git diff --check`: grün.
- Der beendete erste PR-Lauf `34340790267` bestätigt unabhängig bereits den neuen API-37-
  `By.res`-Selektor sowie alle drei Animationspfade. Seine einzigen Produktgate-Fehler sind die
  identischen JUnit-Klasseninitialisierungsfehler auf API 26 und 35, die diese Korrektur behebt.
- Die vollständige Matrix wird auf dem neuen Commit frisch ausgeführt; es erfolgt kein partieller
  Rerun des alten Commits.

## Ereignis – 2026-09-09 – Phase 2 Korrekturplan 2

Remote-Befund:

- PR `#353`, Workflow `34342786676`: Quality erneut grün. API 26 und API 35 führen die
  korrigierte Klasse nun mit beiden echten Tests aus; die Klasseninitialisierung ist behoben.
- `loadingEmptyAndChangingStatesStayExplicit` ist auf beiden APIs grün. Der umfassende
  Aktionstest scheitert auf beiden APIs erst beim elften von zwölf nacheinander ausgeführten
  Klicks, `moveUp("capacity")`, nach wiederholtem Wechsel zwischen virtualisierten Karten.
- Die Fehlstelle ist der pauschale Ein-Sekunden-Poll auf den Harness-Zähler. Sie meldet keinen
  falschen Produktzustand und keine doppelte Weiterleitung, sondern koppelt zwölf unabhängige
  Aktionsverträge an eine einzige lange Scroll-/Kompositionssequenz.

Korrektur:

1. Die vollständige Aktionsmenge in unabhängige Tests für Navigation, angebotenen Run,
   zeitwartenden Run und kapazitätswartenden Run teilen. Jeder Test erhält eine frische Activity
   und prüft weiterhin jede sichtbare Aktion genau einmal.
2. Den synchronen typisierten Callback unmittelbar in `runOnIdle` gegen letzte Aktion und
   Zähler prüfen, statt auf einen zeitbasierten Poll zu warten. Produktcode und Selektoren bleiben
   unverändert.
3. Android-Test-Kompilierung, fokussierte Verträge, vollständigen lokalen Gate und
   `git diff --check` erneut ausführen; anschließend einen neuen vollständigen PR-Workflow auf
   dem Korrekturcommit starten.

## Ereignis – 2026-09-09 – Phase 2 Korrektur 2 lokal validiert

- Die zwölf Aktionsprüfungen sind nun auf vier unabhängige, kontextbezogene Tests verteilt;
  jeder beginnt mit einer frischen Activity und verwendet weiterhin dieselben stabilen Tags.
- Jeder Klick wird nach Compose-Idle unmittelbar gegen `lastAction` und den exakten Zählerwert
  eins geprüft. Es gibt keinen zeitbasierten Poll und keine Abschwächung der Abdeckung.
- Android-Test-Kompilierung und fokussierte FlowRuns-Verträge: grün.
- Vollständiger lokaler Android-Gate einschließlich Instrumentierungs-Test-APK: grün in
  6:24 Minuten; `git diff --check`: grün.
- Der zweite Remote-Lauf bestätigt erneut Quality, API 37 samt `By.res` und alle drei
  Animationspfade. Seine beiden normalen API-26/35-Fehler sind exakt die durch diese
  Korrektur ersetzte lange Scroll-/Poll-Sequenz.
- Ein neuer vollständiger PR-Workflow auf dem Korrekturcommit bleibt erforderlich.

## Phase 2 – Klarer Ablaufmonitor und robuste UI-Verträge

Status: wartet auf Phase 1

## Phase 3 – Schnelle, begrenzte und sichere Verifikation

Status: wartet auf Phase 2

## Cross-Phase-Abschluss

Status: wartet auf Phase 3

## Ereignis – 2026-09-09 – Phase 1 lokal validiert

Umgesetzt:

- `presentation.mobile` bündelt die gemeinsam genutzten mobilen Schriften, Farben,
  Blattflächen und Aktionsschaltflächen ohne fachliche Abhängigkeiten.
- `remainingDurationText` ist die einzige Restzeitformatierung der beiden Oberflächen und
  erhält sowohl Zielzeit als auch Darstellungszeitpunkt explizit.
- `ClockSnapshot` erzeugt lokalen Zeitpunkt und Epoch-Zeitpunkt aus derselben Clock-Lesung;
  Shell und Ablaufmonitor projizieren diesen Wert über ihre bestehenden State-Owner.
- Der Ablaufmonitor beobachtet Minutenticks lifecycle-gebunden und aktualisiert nur seinen
  Darstellungszeitpunkt. Der initiale sowie fachlich ausgelöste Content-Load bleiben davon
  getrennt.
- Alles und Ablaufmonitor verwenden die gemeinsame Darstellungsschicht ohne beabsichtigte
  Layout-, Navigations- oder Aktionsänderung.

Lokale Nachweise:

- fokussierte Clock-, Invalidierungs-, State-Owner-, Formatter- und Architekturtests: grün;
- Alles- und Ablaufmonitor-Goldens einschließlich kleiner Breite, großer Schrift, Nachtmodus
  und vorhandener Zustände: grün und pixelstabil;
- vollständiger Android-Gate
  (`testInstrumentationUnitTest`, `lintDebug`, `assembleDebug`,
  `assembleInstrumentationAndroidTest`, `assembleRelease`): grün in 22:58 Minuten;
- CI-Helfertests: 25 grün; Release-Helfertests: 30 grün;
- APK-Größen: Debug 5.699.725 Bytes, Instrumentierung 1.636.498 Bytes,
  Release unsigned 2.837.404 Bytes;
- `git diff --check`: grün.

Getrennter Plan-/Roadmap-Audit:

- Phasenplan vollständig erfüllt: gemeinsame reine Primitive und Restzeitformatierung,
  expliziter kohärenter Epoch-Zeitpunkt, beide State-Flüsse sowie Tests und Goldens sind
  vorhanden.
- Der Minutensprungtest belegt die Zeitaktualisierung ohne erneuten Today-Content-Load; der
  Ablaufmonitor-Test belegt dieselbe Trennung von einem fachlichen Reload.
- `presentation.mobile` enthält keine Domain-, Use-Case-, ViewModel-, Action- oder
  Wanduhraufruf-Abhängigkeit; die beiden UI-Wege enthalten keinen direkten Wanduhraufruf.
- Produktänderungen liegen ausschließlich in Container-, Clock-, Shell-, Alles-,
  Ablaufmonitor- und allgemeinen Präsentationspfaden. Editor, Domain, Use Cases, Repository
  und Room bleiben unverändert.
- Keine Abweichung und damit kein Korrekturplan erforderlich. Remote-Abschluss steht noch aus.

## Ereignis – 2026-09-09 – Phase 1 remote abgeschlossen

- Stabiler Phasencommit: `27927a7dff7306edad0be203c7685a389acbb21d`.
- Pull Request: `#352`, vollständiger Pull-Request-Gate grün mit Quality sowie Funktions- und
  Animationsinstrumentierung auf API 26, 35 und 37.
- Squash-Merge auf `main`: `80476639744ebd4f15a07bfc3c33d87b09c1216d`.
- Exakter Main-Workflow: `34336455250`, grün. Der inhaltsgleiche PR-Nachweis wurde
  wiederverwendet; Paketierung, fünf signierte Upgrade-Proben und Veröffentlichung liefen auf
  dem Mergecommit.
- Upgrade-Nachweis: Schema 8, 20 und 23 auf API 26 sowie Schema 8 auf API 35 und 37 grün.
- Veröffentlichter Kandidat: `forest-android-1016401`, Auto Secretary `0.2.164`.
- Phase 1 ist damit abgeschlossen; Phase 2 beginnt ausschließlich auf diesem bestätigten
  `origin/main`.

## Ereignis – 2026-09-09 – Phase 2 geplant

Phase: Klarer Ablaufmonitor und robuste UI-Verträge

Status: in Arbeit

Ausgangsstand: `80476639744ebd4f15a07bfc3c33d87b09c1216d` (`origin/main`)

Ergebnis: Der Ablaufmonitor besteht aus kleinen, eindeutig verantworteten
Darstellungskomponenten. Fehler besitzen genau einen zugänglichen Inline-Kanal mit expliziter
Quittierung. Alle testrelevanten Interaktionen verwenden stabile Semantikkennungen statt
sichtbarer deutscher Texte; Quelltext-Suchtests werden durch ausführbare Verhaltensverträge
ersetzt.

Betroffene Komponenten und Schnittstellen:

- `FlowRunsComposeScreen` nur für Zustandsverzweigung, Listenanordnung und Weitergabe;
- getrennte Ablaufkarte, Aktionsgruppe sowie Status-/Ressourcenpräsentation im bestehenden
  `presentation.flowruns`-Paket;
- `FlowRunsComposeCallbacks` um eine explizite Fehler-Schließen-Aktion;
- `FlowRunsActivity` als unveränderter Besitzer von Navigation und Dialogen, jedoch ohne Toast
  und ohne automatisches Fehlerquittieren;
- eine zentrale `FlowRunsSemantics`-Kennungssammlung mit Android-Ressourcen-ID-Export für
  Compose und UIAutomator;
- Host-/State-/ViewModel-Verhaltenstests, Compose-Instrumentierung, API-37-UIAutomator und
  bestehende Goldens.

Umsetzungsreihenfolge:

1. Den bestehenden Screen ohne Zustands- oder Aktionsänderung in Orchestrierung, Karte,
   Aktionen und reine Status-/Ressourcenpräsentation zerlegen.
2. Stabile Kennungen für Screen, Zustände, Karten und jede Aktion definieren, am Root für
   UIAutomator als Ressourcen-IDs exportieren und die Compose- sowie API-37-Tests darauf
   migrieren.
3. Die Fehlermeldung um eine zugängliche Schließen-Aktion ergänzen, den Callback exakt einmal
   bis `FlowRunsAction.acknowledgeError` weiterleiten und Toast sowie automatische Quittierung
   entfernen.
4. Persistenz und identitätsgebundene Quittierung des Fehlerzustands mit State-/ViewModel-Tests
   sowie genau-einmalige Host-Weiterleitung aller sichtbaren Aktionen mit Interaktionstests
   belegen.
5. Den Quelltext-Suchtest entfernen und die Architektur über typisierte Grenzen,
   paketlokale Zerlegung und ausführbares Verhalten absichern. Sichttexte nur in Golden- und
   gezielten Darstellungsverträgen belassen.
6. Fokussierte Tests, alle Ablaufmonitor-Goldens, vollständigen lokalen Android-Gate,
   CI-/Release-Helfertests und `git diff --check` ausführen.
7. Implementierung getrennt gegen diesen Phasenplan und die Gesamtroadmap auditieren. Vor jeder
   notwendigen Korrektur zuerst einen Fixplan append-only ergänzen.

Abnahme: Jede sichtbare Aktion überquert die typisierte Hostgrenze genau einmal; Fehler bleiben
bis zur passenden expliziten Quittierung in genau einem Kanal sichtbar; API-37 und Compose
verwenden keine sichtbaren Texte als Interaktionsselektoren; die Screen-Datei besitzt keine
Karten-, Aktions-, Status-, Ressourcen- oder Restzeitimplementierung; bestehende Zustände,
Sperren, Dialoge und Ablaufsemantik bleiben erhalten; Editor, Domain, Use Cases, Repository und
Room bleiben unverändert.

### Quellenbasis Phase 2

- Lokaler Code: `app/src/main/java/de/thonktank/autosecretary/FlowRunsActivity.java`
- Lokaler Code: `app/src/main/java/de/thonktank/autosecretary/FlowRunsScreenState.java`
- Lokaler Code: `app/src/main/java/de/thonktank/autosecretary/FlowRunsViewModel.java`
- Lokaler Code: `app/src/main/kotlin/de/thonktank/autosecretary/presentation/flowruns/FlowRunsComposeScreen.kt`
- Lokale Tests: `app/src/androidTest/kotlin/de/thonktank/autosecretary/FlowRunsComposeInstrumentationTest.kt`
- Lokale Tests: `app/src/androidTest/kotlin/de/thonktank/autosecretary/FlowRunsComposeApi37InstrumentationTest.kt`
- Architekturvertrag: `docs/architecture/adr-034-mobile-aufgabenverwaltung-und-ablaufmonitor.md`

## Ereignis – 2026-09-09 – Phase 2 remote abgeschlossen

- Stabiler Phasencommit: `c9203c0d68374b331cf3f84ca84d80f05cfba8fc`.
- Pull Request: `#353`; Workflow `34346692492` ist vollständig grün. Quality lief in 6:04
  Minuten, normale und animationsaktive Instrumentierung auf API 26, 35 und 37 sowie beide
  Sammelgates sind grün. Der langsamste Gerätepfad benötigte 13:17 Minuten.
- Squash-Merge auf `main`: `2dc4c32ed4b88ac47739b6de11491cda28611cc0`.
- Exakter Main-Workflow: `34348505931`, grün. Der inhaltsgleiche PR-Nachweis wurde
  wiederverwendet; Paketierung, fünf signierte Upgrade-Proben und Veröffentlichung liefen auf
  dem Mergecommit.
- Veröffentlichter Kandidat: `forest-android-1016501`, Auto Secretary `0.2.165`.
- Phase 2 ist damit abgeschlossen; Phase 3 beginnt ausschließlich auf diesem bestätigten
  `origin/main`.

## Ereignis – 2026-09-09 – Phase 3 geplant

Phase: Schnelle, begrenzte und sichere Verifikation

Status: in Arbeit

Ausgangsstand: `2dc4c32ed4b88ac47739b6de11491cda28611cc0` (`origin/main`)

Ergebnis: Quality liefert drei parallel sichtbare Signale für Verträge, Goldens und
Lint/Paketierung und wird durch ein einziges stabiles `quality`-Gate zusammengefasst. Jeder
lange Schritt besitzt eine aus realen Laufzeiten abgeleitete Grenze und nachfolgende
Diagnoseartefakte. Reguläre Instrumentierung ist als sichtbare Test-App von der installierten
Produktions-App getrennt; ausschließlich der signierte Upgrade-Probe-Pfad verwendet weiterhin
die Produktionsidentität.

Betroffene Komponenten und Schnittstellen:

- `app/build.gradle.kts` für getrennte Unit-/Golden-Selektion sowie die konditionale
  Instrumentierungs-ID und App-Kennzeichnung;
- `app/src/main/AndroidManifest.xml` für den buildtypabhängigen sichtbaren App-Namen;
- `.github/workflows/verify.yml` für drei Quality-Jobs, gemeinsames Gate, Zeitgrenzen und
  Diagnoseartefakte;
- `scripts/ci/check-fast.sh`, Golden-Register-Vertrag und Soak-Bereinigung für lokal/remote
  identische sichere Schleifen;
- Golden-Katalog und `HomescreenGoldenRobolectricTest` für die nachgewiesene Entfernung einer
  byteidentischen visuellen Doppelung;
- README, Teststrategie und ADR für eindeutige Verantwortung von Vertrag, Roadmap,
  Ausführungslog und externen GitHub-Nachweisen.

Umsetzungsreihenfolge:

1. Einen ausführbaren Golden-Risikokatalog einführen, der jede Baseline genau einmal einer
   eindeutigen Risikokennung und Beschreibung zuordnet. Die byteidentischen Baselines
   `homescreen/complete.png` und `homescreen/harvested.png` sind die einzige nachgewiesene
   Doppelung; `harvested.png` und sein zweiter Renderdurchlauf werden entfernt, während die
   Zustandssemantik durch bestehende Completion-Verträge erhalten bleibt.
2. Die Host-Suite über eine explizite Gradle-Eigenschaft in Verträge ohne visuelle Goldens und
   eine ausschließliche Golden-Suite teilen. `scripts/ci/check-fast.sh` führt CI-/Release-
   Helferverträge und die nichtvisuelle Host-Suite ohne Wiederholung aus.
3. Den bisherigen Quality-Job in `quality-contracts`, `quality-goldens` und `quality-build`
   schneiden. Das bestehende stabile Check-Ergebnis `quality` wird ein fail-closed Sammelgate;
   Instrumentierung, PR-Gate, Main-Wiederverwendung und Packaging hängen weiter daran.
4. Die gemessenen 6:04 Minuten des kombinierten Remote-Quality-Laufs und maximal 13:17 Minuten
   Instrumentierung als Basis verwenden: Quality-Hauptschritte erhalten 12 Minuten,
   Quality-Jobs 15 Minuten, Geräteschritte 20 Minuten. Fehler- und Timeoutpfade laden die
   jeweiligen Test-, Golden-, Lint-, Build- oder Geräteberichte hoch; automatische Wiederholung
   bleibt ausgeschlossen.
5. Dem regulären Instrumentierungs-Build den Suffix `.test` und den Namen
   `Auto Secretary Test` geben. Bei `-PupgradeProbeRunner=true` bleiben Paketname und Label der
   Produktion unverändert. Der manuelle Soak entfernt nur Testziel und Test-APK, nie mehr die
   Produktions-App.
6. Workflow-, Golden-Register-, Paketidentitäts- und Change-Scope-Verträge ergänzen; schnelle
   Schleife, reine Golden-Suite, vollständigen lokalen Android-Gate, beide Identitätsvarianten,
   Helfertests und `git diff --check` ausführen.
7. Implementierung getrennt gegen Phasenplan und Gesamtroadmap auditieren. Der Ausführungslog
   endet mit dem lokalen und Cross-Phase-Audit; unveränderliche PR-, Merge- und Main-Belege
   bleiben in GitHub und im abschließenden Handoff, damit kein reiner Status-PR erforderlich ist.

Abnahme: Die schnelle lokale Schleife bleibt unter einem Drittel der erfassten 22:58-Minuten-
Vollprüfbasis; der vollständige lokale Gate wird gegen seine 6:27-Minuten-Warmbasis verglichen.
Quality- und Gerätehänger enden begrenzt mit Diagnosepfad. Normale Testinstallationen besitzen
`de.thonktank.autosecretary.test` und ein sichtbares Testlabel, signierte Upgrade-Proben weiter
`de.thonktank.autosecretary`. Dokumentationsänderungen lösen keinen Release aus. Jede verbliebene
Golden-Datei besitzt genau einen eindeutigen Risikoeintrag; kein nicht nachgewiesenes Bild wird
entfernt.

### Quellenbasis Phase 3

- Workflow: `.github/workflows/verify.yml`
- Buildvertrag: `app/build.gradle.kts`
- App-Manifest: `app/src/main/AndroidManifest.xml`
- Workflow-Verträge: `scripts/release/test_workflow_contract.py`
- Change-Scope: `scripts/ci/change_scope.py`
- Geräteläufer: `scripts/ci/run-instrumentation.sh`
- Soak-Läufer: `scripts/ci/run-instrumentation-soak.sh`
- Teststrategie: `docs/architecture/phase-7-teststrategie.md`
- Architekturvertrag: `docs/architecture/adr-034-mobile-aufgabenverwaltung-und-ablaufmonitor.md`
## Ereignis – 2026-09-09 – Phase 3 Schnellprüfung: Umgebungsfehler und Korrekturplan

Phase: Schnelle, begrenzte und sichere Verifikation

Status: Korrektur geplant

Beobachtung: Die Python-Verträge waren vollständig grün, anschließend brach
`scripts/ci/check-fast.sh` vor dem ersten Android-Test ab. Gradle konnte im isolierten Worktree
keinen Android-SDK-Pfad auflösen (`ANDROID_HOME` beziehungsweise `sdk.dir` fehlte). Es liegt kein
Produkt-, Test- oder Workflowfehler vor; der Lauf hat keine Android-Aufgabe ausgeführt.

Korrekturplan:

1. Keine Produkt- oder Testdatei ändern.
2. Für alle lokalen Android-Nachweise die bereits für das Repository bestätigten Pfade
   `JAVA_HOME=/home/aaron/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2` und
   `ANDROID_HOME=ANDROID_SDK_ROOT=/home/aaron/Android/Sdk` setzen.
3. Den unveränderten Schnelllauf erneut messen und erst nach einem grünen Ergebnis mit Golden-
   und Build-Lane fortfahren.
## Ereignis – 2026-09-09 – Phase 3 Build-Grenze: Messbefund und Korrekturplan

Phase: Schnelle, begrenzte und sichere Verifikation

Status: Korrektur geplant

Beobachtung: Schnellprüfung (`375,69 s`) und Golden-Lane (`141,51 s`) sind grün und passen in
ihre geplanten 12-Minuten-Schrittgrenzen. Der kalte lokale Build-Lane aus Lint sowie Debug-,
Instrumentierungs-, Test- und Release-APK war ebenfalls grün, benötigte mit `1042,17 s`
beziehungsweise 17:21 Minuten aber mehr als die geplanten 12 Minuten. Der Prozess war während
der langen Lint-/R8-Phasen CPU-aktiv; eine 12-Minuten-Grenze würde damit einen gültigen Lauf
abschneiden.

Korrekturplan:

1. Nur `quality-build` auf 30 Minuten Job- und 25 Minuten Schrittgrenze erweitern. Das lässt rund
   44 Prozent Reserve über der gemessenen kalten Lokalzeit und beendet echte Hänger weiterhin
   deutlich früher als die bisher unbegrenzte Ausführung.
2. Die belegten engeren Grenzen für Vertrags-, Golden- und Geräteschritte unverändert lassen.
3. Den Workflow-Vertrag an die lane-spezifische Grenze anpassen und danach Workflow-Verträge,
   Build-Lane und Paketidentitätsprüfung erneut ausführen.

## Ereignis – 2026-09-09 – Phase 3 Identitätsvertrag: Shellfehler und Korrekturplan

Phase: Schnelle, begrenzte und sichere Verifikation

Status: Korrektur geplant

Beobachtung: Der erneute reguläre Instrumentierungs-Build war grün. Der anschließend neue
APK-Identitätsvertrag brach jedoch vor der inhaltlichen Prüfung mit einem Shell-Syntaxfehler ab,
weil zwei `case`-Muster über eine Zeilenfortsetzung verkettet waren. Der gleiche Ausdruck im
Packaging-Workflow besitzt dieselbe unnötige Komplexität.

Korrekturplan:

1. Runner und Zielpaket jeweils in einem eigenen `case` prüfen; keine verketteten Muster nutzen.
2. Dieselbe Vereinfachung im signierten Produktionsprobe-Block anwenden.
3. Shellsyntax, Workflow-YAML, Verträge und beide realen APK-Identitätsmodi erneut prüfen.

## Ereignis – 2026-09-09 – Phase 3 Skriptportabilität: Korrekturplan

Phase: Schnelle, begrenzte und sichere Verifikation

Status: Korrektur geplant

Beobachtung: Shellcheck meldet in den drei neuen CI-Skripten ausschließlich `SC1007`: Die
repository-relative Pfadermittlung setzt `CDPATH` mit einer uneindeutigen Leerstelle statt einer
expliziten leeren Zeichenkette.

Korrekturplan: In allen drei neuen Skripten `CDPATH=''` explizit setzen, danach Shellcheck und
die ausführbaren Verträge erneut ausführen. Keine Produkt- oder Workflowsemantik ändern.

## Ereignis – 2026-09-09 – Phase 3 lokaler Vollgate: Auditbefund und Korrekturplan

Phase: Schnelle, begrenzte und sichere Verifikation

Status: Korrektur geplant

Beobachtung: Der finale Schnellpfad ist in `278,05 s` grün. Ein lokaler Vollgate aus nacheinander
aufgerufener Schnell- und Golden-Lane würde dieselbe Hosttest-Aufgabe wegen unterschiedlicher
Filter zweimal ausführen. Das bildet die parallele CI korrekt ab, ist lokal aber langsamer als
nötig und verletzt das Ziel eines nicht verlangsamten vollständigen Abschlusslaufs.

Korrekturplan:

1. `scripts/ci/check-all.sh` als einzelnen lokalen Abschlussvertrag ergänzen: Helferverträge,
   einmalige vollständige Host-/Golden-Suite, Lint, alle APKs, Identitäts- und Größenbudgets.
2. README und Teststrategie auf diesen Vollgate verweisen; die drei CI-Lanes bleiben unverändert
   parallel und liefern weiterhin getrennte Signale.
3. Den neuen Vollgate messen und gegen die erfasste 6:27-Minuten-Warmbasis prüfen.

## Ereignis – 2026-09-09 – Phase 3 lokal validiert

Phase: Schnelle, begrenzte und sichere Verifikation

Status: lokaler Phasenabschluss; Remote-Gates ausstehend

Ergebnis:

- `scripts/ci/check-fast.sh` ist im endgültigen Stand grün in `278,05 s` (4:38 Minuten) und
  bleibt damit deutlich unter einem Drittel der erfassten 22:58-Minuten-Vollprüfbasis.
- `scripts/ci/check-goldens.sh` ist grün in `141,51 s`; der Risikovertrag erfasst alle 68
  Golden-Artefakte, davon 66 PNGs und zwei numerische beziehungsweise Hash-Anker, genau einmal.
  Nach Entfernung der byteidentischen `homescreen/harvested.png` bleibt keine Byte-Doppelung.
- `scripts/ci/check-all.sh` ist vollständig grün. Sein vergleichbarer Gradle-Gate benötigt
  6:26 Minuten statt der erfassten 6:27-Minuten-Warmbasis; der gesamte Wrapper einschließlich
  58 Python-Verträgen benötigt `388,76 s`.
- Der kalte Build-Lane ist mit Lint sowie Debug-, Instrumentierungs-, Test- und Release-APK grün
  in `1042,17 s`. Seine daraus korrigierte 25-Minuten-Schritt- und 30-Minuten-Jobgrenze besitzt
  Reserve, während Vertrags- und Golden-Schritte bei 12/15 Minuten und Geräteaktionen bei 20
  Minuten begrenzt bleiben.
- Shellcheck, Workflow-YAML, `git diff --check`, 27 CI-Helfertests und 31 Release-/Workflowtests
  sind grün. Fehlerartefakte sind je Lane erhalten; kein Job enthält automatische Wiederholung.
- Die real gebauten regulären APKs belegen App-ID `de.thonktank.autosecretary.test`, Label
  `Auto Secretary Test`, Test-APK-ID `de.thonktank.autosecretary.test.test`, AndroidJUnitRunner
  und Zielpaket der Test-App. Der neue ausführbare APK-Vertrag erzwingt diese Grenze in CI.
- Der real gebaute Upgrade-Probe-Modus belegt App-ID `de.thonktank.autosecretary`, Label
  `Auto Secretary`, Test-APK-ID `de.thonktank.autosecretary.test`,
  `UpgradeProbeInstrumentation` und Produktionszielpaket. Packaging erzwingt diese Identität vor
  den weiterhin ausstehenden signierten Remote-Upgrades.
- Ein reiner Dokumentationspfad ergibt weiterhin `quality_required=false`,
  `instrumentation_required=false` und `release_required=false`.

## Ereignis – 2026-09-09 – Requirementweiser Cross-Phase-Audit

Status: lokal bestanden; Phase-3-PR, Squash-Merge und exakter Main-Nachweis ausstehend

Abgleich gegen den Zielzustand:

1. **Gemeinsame mobile Darstellung:** Phase 1 lieferte eine kleine zustandslose
   Darstellungsschicht für Typografie, Flächen und Aktionen. Paketgrenzen belegen weiterhin
   keine Domain-, Action-, ViewModel- oder Navigationsabhängigkeit.
2. **Lebende, deterministische Restzeit:** Phase 1 besitzt eine einzige reine Formatierung und
   reicht den lifecycle-gebundenen Darstellungszeitpunkt durch beide State-Owner. Minutenwechsel,
   feste Testzeit und unveränderte Datenneuladung sind abgedeckt.
3. **Klarer Ablaufmonitor:** Phase 2 trennt Orchestrierung, Karte, Aktionen, Status und Ressourcen.
   Die zentrale Screen-Datei enthält keine Kartenimplementierung oder eigene Zeitformatierung.
4. **Ein Fehlerkanal und stabile Interaktion:** Phase 2 entfernt Toast und automatische
   Quittierung, hält Inline-Fehler bis zur expliziten Aktion und belegt jede Weiterleitung genau
   einmal. Compose und API 37 verwenden stabile Semantik statt sichtbarer deutscher Selektoren.
5. **Schnelle, begrenzte Verifikation:** Phase 3 trennt Verträge, Goldens und Build parallel,
   fasst sie fail-closed als `quality` zusammen und lässt Instrumentierung erst danach laufen.
   Gemessene Grenzen, Diagnoseartefakte, fehlende Retries sowie schneller und vollständiger
   lokaler Gate sind belegt.
6. **Sichere Installationsidentität:** Reguläre Instrumentierung und Soak betreffen nur die
   sichtbar getrennte Test-App. Ausschließlich der signierte Upgrade-Probe-Pfad behält die
   Produktions-ID; deren Paket-, Runner- und Zielgrenze wird vor Geräteausführung geprüft.
7. **Golden-Verantwortung:** Jedes verbliebene Artefakt besitzt genau eine eindeutige
   Risikobeschreibung. Nur eine nachweislich byteidentische Baseline wurde entfernt; die
   Abschlusssemantik bleibt anderweitig vertraglich abgedeckt.
8. **Unveränderliche Fachgrenzen:** Keine Phase änderte Aufgabeneditor, Domainmodell, Use Cases,
   Room-Schema, Repositories, Ablaufmaterialisierung, Navigation, Filter-/Sortierbedeutung oder
   die Abgrenzung von Kandidaten zu gestarteten Runs.
9. **Nachweisverantwortung:** Die Roadmap bleibt Sollvertrag, dieses Dokument append-only
   Ereignis- und Auditlog, ADR/Teststrategie dauerhafte Architektur. PR-, Merge-, exakter Main-,
   Signatur-, Upgrade- und Veröffentlichungsnachweis bleiben unveränderlich in GitHub und im
   abschließenden Handoff; dadurch ist kein nachgelagerter Status-PR nötig.

Offene lokale Abweichungen: keine. Die Roadmap ist dennoch erst abgeschlossen, wenn Phase 3 ein
grünes PR-Gate, einen Squash-Merge und einen vollständig grünen exakten Main-Lauf einschließlich
signierter Produktionsupgrades auf API 26, 35 und 37 besitzt.

## Ereignis – 2026-09-09 – Phase 3 Commitstaging: Korrekturplan

Status: Korrektur geplant

Beobachtung: Der explizite Staging-Befehl nannte die bereits durch `git rm` vorgemerkte und im
Arbeitsbaum nicht mehr vorhandene Golden-Datei erneut. `git add` brach deshalb ab; der
nachfolgende Commit erfasste nur diese bereits vorgemerkte Löschung. Alle übrigen validierten
Änderungen blieben unverändert im Arbeitsbaum erhalten.

Korrekturplan: Den vollständig bekannten Phase-3-Arbeitsbaum mit `git add -A` vormerken,
`git diff --cached --check` sowie die Dateiliste prüfen und den vorläufigen Commit amendieren.
Damit bleibt genau ein vollständiger Phasencommit; es wird kein zusätzlicher Korrekturcommit
erzeugt.
