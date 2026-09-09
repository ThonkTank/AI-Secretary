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
