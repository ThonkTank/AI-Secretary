# Roadmap: mobile Darstellungs- und Verifikationshärtung

- Status: verbindlich
- Owner: AutoSecretary
- Last Reviewed: 2026-09-09
- Source of Truth: Dieses Dokument

Beschlossen: 2026-09-08

Ausgangsstand bei Ausführungsbeginn: `493e7b0d9bac` (`origin/main`)

## Zielzustand

Der Alles-Tab bleibt die mobile Aufgabenverwaltung für Suche, Filter, Sortierung und den
Einstieg in den bestehenden Aufgabeneditor. Tatsächlich gestartete Abläufe erscheinen dort nur
als kompakte Vorschau und öffnen den eigenständigen Ablaufmonitor. Beide Oberflächen verwenden
eine kleine gemeinsame mobile Darstellungsschicht, erhalten einen expliziten, minutengenau
aktualisierten Darstellungszeitpunkt und besitzen belastbare semantische Testverträge.

Die Aufgabenbearbeitung, Ablaufdomäne, Ressourcenlogik, Persistenz, fachliche
Reihenfolgesemantik und Navigation bleiben unverändert. Fällige, aber nicht gestartete
Ablaufkandidaten werden weiterhin nicht als laufende Hintergrundabläufe dargestellt.

| Ausgangsproblem | Verbindlicher Zielzustand |
|---|---|
| Doppelte Typografie-, Flächen-, Button- und Restzeitlogik | Eine kleine zustandslose mobile Darstellungsschicht ohne Domain-, ViewModel- oder Navigationsabhängigkeit |
| Restzeit altert bei geöffnetem Screen | Ein vorhandener, lifecycle-gebundener Minutentakt aktualisiert einen expliziten Darstellungszeitpunkt |
| Großer, gemischter Ablaufmonitor-Screen | Bildschirmorchestrierung, Ablaufkarte, Aktionen und reine Darstellung sind getrennt |
| Fehler gleichzeitig inline und als Toast | Eine dauerhafte Inline-Meldung wird ausschließlich durch eine explizite Aktion quittiert |
| Tests koppeln Interaktionen an deutsche Texte und Quelltextfragmente | Stabile Semantikkennungen sowie Verhaltens- und Grenztests bilden den Vertrag |
| Breiter Quality-Block kann lange ohne Diagnose hängen | Getrennte, begrenzte Prüfspuren mit gemeinsamem Gate und verwertbaren Artefakten |
| Lokale Instrumentierung kann die Produktions-App treffen | Eine eindeutig benannte, getrennte Instrumentierungs-App-ID; der signierte Upgrade-Probe-Pfad behält die Produktions-ID |
| Roadmap, Ereignislog und Remote-Abschluss überlappen | Roadmap, append-only Ausführungslog und GitHub-Nachweise besitzen getrennte Verantwortungen |

## Unveränderliche Grenzen

- Aufgabeneditor, Editor-Navigation und Editor-State werden nicht verändert.
- Domainmodelle, Use Cases, Room-Schema, Repositories und Ablaufmaterialisierung werden nicht
  verändert.
- `FlowRunsViewModel` und der Shell-/Alles-State bleiben alleinige Besitzer ihrer fachlichen und
  dauerhaften Darstellungszustände; Compose besitzt nur flüchtige UI-Zustände.
- Die Alles-Vorschau zeigt weiterhin höchstens zwei tatsächlich gestartete Runs und öffnet die
  Detailansicht.
- Suche, unmittelbare Filterwirkung, Sortiermodus, stabile Einfügepositionen und bestehende
  Ablaufaktionen behalten ihre Bedeutung.
- Es entsteht kein allgemeines Design-System, kein neues DI-Framework und kein neues
  Navigationsmodell.

## Verbindlicher Phasenablauf

Vor jeder Phase werden diese Roadmap, das getrennte Ausführungsprotokoll, der aktuelle
Repositoryzustand, `AGENTS.md` und die direkt betroffenen Verträge erneut gelesen. Der konkrete
Phasenplan wird vor Produktcode im Ausführungsprotokoll festgehalten. Nach der Validierung folgt
ein getrennter Abgleich gegen Phasenplan und Gesamtroadmap. Jede Abweichung erhält vor ihrer
Korrektur einen protokollierten Fixplan.

Jede Implementierungsphase beginnt vom dann aktuellen `origin/main` in einem isolierten
Worktree, wird committed und in einem eigenen Pull Request gegen `main` geprüft. Erst ein grüner
Pull-Request-Gate erlaubt den Squash-Merge. Die nächste Phase beginnt erst nach grünem exaktem
Main-Workflow; produktwirksame Phasen benötigen den anwendbaren Paketierungs-, Upgrade- und
Publikationsnachweis.

## Phase 1 – Gemeinsame Darstellung und lebende Restzeit

Branch: `codex/mobile-ui-foundation`

1. Gemeinsame mobile Typografie, Farbkonvertierung, Blattflächen und Aktionsschaltflächen aus
   Alles und Ablaufmonitor in eine kleine reine Präsentationsgrenze ziehen.
2. Eine einzige reine Restzeitformatierung einführen.
3. Den vorhandenen `ClockInvalidationSource` um einen kohärenten Epoch-Darstellungszeitpunkt
   ergänzen und diesen über die vorhandenen State-Owner bis in beide Compose-Screens reichen.
4. Direkte Wanduhraufrufe aus Composables und Formatierern entfernen.
5. Beide Oberflächen verhaltens- und layouttreu auf die gemeinsame Grundlage migrieren.

Abnahme:

- Ein Minutensprung aktualisiert eine sichtbare Restzeit ohne Datenbank- oder Ablauf-Neuladen.
- Zeitformatierung ist mit fester Testzeit deterministisch; es gibt genau eine Implementierung.
- Die gemeinsame mobile Schicht importiert keine Actions, ViewModels, Use Cases oder Domainlogik.
- Kleine Breite, große Schrift, Nachtmodus und die vorhandenen Screen-Zustände bleiben visuell
  abgedeckt.
- Editor-, Domain-, Use-Case-, Repository- und Room-Pfade bleiben außerhalb des Produktdiffs.

## Phase 2 – Klarer Ablaufmonitor und robuste UI-Verträge

Branch: `codex/flow-runs-ui-resilience`

1. Ablaufmonitor in Zustandsorchestrierung, Karte, Aktionen sowie reine Status- und
   Ressourcendarstellung zerlegen.
2. Toast und automatisches Fehlerquittieren entfernen; eine zugängliche Inline-Meldung bleibt
   bis zur expliziten Schließen-Aktion sichtbar.
3. Stabile Semantikkennungen bereitstellen, die Compose-Tests und API-37-UIAutomator ohne
   Kopplung an sichtbare deutsche Texte verwenden können.
4. Quelltext-Suchtests durch Verhaltens-, Weiterleitungs- und Paketgrenzentests ersetzen.
5. Sichtbare Texte nur in gezielten Darstellungs- oder Lokalisierungsverträgen prüfen.

Abnahme:

- Jede Benutzeraktion wird genau einmal an das bestehende ViewModel weitergeleitet.
- Fehler erscheinen in genau einem Kanal, bleiben bis zur Quittierung sichtbar und werden genau
  einmal quittiert.
- API-37-Interaktionstests verwenden keine sichtbaren Texte als Selektoren.
- Die zentrale Screen-Datei enthält nur Zustandsverzweigung und Anordnung, keine
  Kartenimplementierung oder Restzeitformatierung.
- Alle vorhandenen Aktionen, Sperrzustände und visuellen Zustände bleiben abgedeckt.

## Phase 3 – Schnelle, begrenzte und sichere Verifikation

Branch: `codex/mobile-verification-contract`

1. Unit-/Architekturtests, Goldens und Lint/APK-Build in getrennte Quality-Jobs mit einem
   gemeinsamen Gate schneiden; Instrumentierung wartet auf dieses Gate.
2. Zeitlimits anhand der gemessenen Basis setzen und bei Fehler oder Timeout verwertbare
   Berichte hochladen. Instabilität wird nicht durch automatische Wiederholung verborgen.
3. Jede Golden-Datei einem einzigartigen Risiko zuordnen; nur nachgewiesene Duplikate entfernen.
4. Der reguläre Instrumentierungs-Build erhält eine getrennte App-ID und sichtbare
   Testkennzeichnung. Nur der isolierte signierte Upgrade-Probe-Build nutzt weiter die
   Produktions-ID.
5. ADR, Roadmap, Ausführungslog und GitHub-Nachweise auf eindeutige Verantwortungen begrenzen.

Abnahme:

- Die schnelle lokale Prüfschleife benötigt auf derselben Maschine höchstens ungefähr ein
  Drittel der erfassten bisherigen Vollprüfzeit; der vollständige Gate-Lauf wird nicht langsamer.
- Hängende Quality-Läufe enden innerhalb des festgelegten Limits mit Diagnoseartefakten.
- Ein lokaler oder physischer Instrumentierungslauf kann die installierte Produktions-App nicht
  überschreiben.
- Der Upgrade-Probe-Pfad prüft weiterhin den echten Paketnamen und die signierte
  Produktionskandidatin auf API 26, 35 und 37.
- Reine Dokumentationsänderungen erzeugen keinen Produktrelease.
- Roadmapabschluss benötigt keinen nachgelagerten reinen Status-PR.

## Gesamtabschluss

Nach Phase 3 folgt ein requirementweiser Cross-Phase-Abgleich gegen dieses Dokument. Offen
gebliebene Abweichungen erhalten vor Codeänderungen einen protokollierten Korrekturplan. Die
Roadmap ist erst abgeschlossen, wenn alle drei Phasen, ihre PR-Gates, Squash-Merges und exakten
Main-Nachweise belegt sind.
