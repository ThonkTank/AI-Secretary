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
