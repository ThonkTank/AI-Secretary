# Ausführungsprotokoll: Recovery-Remediation

Kanonische Roadmap: [recovery-remediation-roadmap.md](recovery-remediation-roadmap.md)

## Planungsstand 2026-09-12

- Status: Roadmap entworfen; Umsetzung nicht begonnen.
- Referenz: Remote-Main `6ad6b6c6019a69eb75529479644df793074c4618`.
- Planungsbranch: `codex/remediation-roadmap`.
- Ausgangsprodukt laut vorhandenen Belegen: 0.2.172, Schema 27. Bei Umsetzung frisch prüfen.
- Keine Produktdatei, CI-Regel oder Gerätedatei wurde für diese Planung geändert.
- Die Roadmap schlägt in P2 eine Änderung des bestehenden Freigabevertrags vor. Dieser ist
  durch die Erstellung des Entwurfs noch nicht geändert.

| Phase | Status | Abschlussbelege |
|---|---|---|
| P0 – Ausgangsstand | Geplant | Keine neuen Abnahmebelege |
| P1 – Today-Bedienweg | Geplant | — |
| P2 – Freigabeprofile | Geplant | — |
| P3 – Diagnosezustand | Geplant | — |
| P4 – Ursachenuntersuchung | Geplant | — |
| P4b – Ursachenfix | Bedingt; kein Erzeuger nachgewiesen | Nicht freigegebener Implementierungsumfang |
| P5 – Abschluss | Geplant | Sichtbare Pixel-Prüfung zuletzt offen |

## Pro Phase zu ergänzen

- Frischer Ausgangsstand und betroffene Verträge.
- Konkreter Plan vor Implementierung, Umfang und erwarteter Releasebedarf.
- Reproduktion, Implementierungs-/Testbelege und gesonderter Roadmap-Abgleich.
- Abweichungen mit vorab notiertem Korrekturplan.
- Branch, PR, geprüfter Head, Squash-Main und exakter Main-Lauf.
- Bei Release: Kandidat, anwendbare Upgradefälle, veröffentlichte Bytes und Gerätebelege.
- Getrennte offene Punkte und Voraussetzungen der nächsten Phase.

Prüflaufzeiten mit Parallelität und abgedecktem Risiko dokumentieren. Zwischenmeldungen
benennen bevorzugt neue Ergebnisse oder die konkrete verbleibende Abhängigkeit.

## P0 – Beauftragte Umsetzung und Phasenplan

Die ausdrückliche Beauftragung „Setze die roadmap per roadmap executor skill um“ nimmt die
Roadmap einschließlich des in P2 vorgeschlagenen Prüfvertrags an. Bis zum geprüften P2-Merge
bleiben die bisherigen Gates gültig. Der ursprüngliche Roadmaptext bleibt als Referenz unverändert.

Plan: beide Planungsdokumente versionieren; Remote-Main und PR-/Releasebelege frisch prüfen;
die vorhandenen Gerätedatenbelege zuordnen und die aktuell fehlende Geräteverbindung als
separaten Abnahmenachweis offen halten. Diese Dokumentationsphase erfordert keinen Produktrelease.

### P0 – Validierung und Abgleich

- Remote-Main unverändert `6ad6b6c6019a69eb75529479644df793074c4618`; PR 362 ist gemergt.
- PR-Lauf 34691047203 und exakter Main-Lauf 34692015363 erneut erfolgreich bestätigt;
  Release `forest-android-1017201` ist öffentlich und kein Prerelease.
- Gesicherte frühere Gerätebelege: 0.2.172 / 1017201 in-place installiert; ursprüngliche
  Installation erhalten; Kaltstart ohne App-/Datenbankfehler; Archiv 8 Ablauf-Schritte,
  4 Ressourcen, 2 Aufgabenschritte. Dies sind historische Belege, keine neue Geräteprüfung.
- Frisches `adb devices -l`: keine Geräte. Anschließen/Entsperren wurde angefragt. Sichtbare
  Aufgaben-/Ablauf-, Titel-/Später- und Persistenzprüfung bleibt offen; P1–P4 sind nicht blockiert.
- Lokaler Baselinecheck: 777 Tests, 0 Fehler, 1 optionaler Benchmark übersprungen, 22m44s
  für den vollständigen Check. Host-Klickregression prüft API 26/35, noch keine gesamte Speicherung.
- PR-Baseline: Contracts 2m37s, Goldens 2m03s, Build 6m03s; normale Instrumentierung auf
  API 26/35/37 4m11s/5m20s/5m38s; Animationen 5m43s/13m52s/5m51s.
  Der lange API-35-Animationslauf absolvierte 35 Tests erfolgreich; kein Stillstand oder Retry.
- PR-Gesamtdauer 20m22s, Main-/Release-Gesamtdauer 7m11s. Lokaler Check und PR liefen parallel;
  die Zeiten sind keine addierbare Wartezeit. Historische Upgrade-Lanes prüfen Datenherkunft
  und signierten In-place-Übergang, nicht den Titel-Bedienweg.
- Reproduktionsbelege liegen lokal in `/tmp/title-repro.xml`, `/tmp/title-tap-crash.txt` und
  `/tmp/recovery-device-proof/`; keine persönlichen Daten werden ins Repository kopiert.
- Dokumentverweise, Phasen P0–P5 und Format wurden geprüft. Abgleich: P0-Belegbestand erfüllt;
  Pixel-Abnahme ausdrücklich offen. Integration per PR steht noch aus.

### P0 – Integration

PR 363 (`6f54d0cc`) bestand den Dokumentations-Gate im Lauf 34697046556 und wurde per
Squash als `da6e9a41f844878762870e8a3c2d8cd265faec89` integriert. Exakter Main-Lauf
34697081276 erfolgreich; kein Produktrelease. P0-Belegbestand abgeschlossen, sichtbare
Pixel-Abnahme weiterhin separat offen.

## P1 – Phasenplan vor Implementierung

Ausgangsstand: `da6e9a41f844878762870e8a3c2d8cd265faec89`.
Branch: `codex/today-action-integration`.

- Eine eigene Integrationstestklasse verwendet bestehenden Room-/UseCase-/ViewModel-Aufbau,
  den echten DashboardRenderer und seine produktiven Klickbindungen. Datenbankdatei nach dem
  Titel-Klick schließen/neu öffnen; kein Befehlsrecorder hinter dem Klick.
- Fokus-/Abschnittsübergänge von „Später“ einschließlich Einzelaufgabe und einen normalen/Flow-
  Mischfall prüfen. Historie, XP, Ablaufkandidaten und Ressourcen vor/nach den Aktionen vergleichen.
- Nativer repräsentativer Test in der isolierten Instrumentierungs-App durch den tatsächlichen
  MainActivity-Bedienweg; kein Säen auf dem Pixel-Produktionspaket.
- Coordinator-Befehlsaktionen mit gültigen Factory-Eingaben ausführen und genau einen typisierten
  Befehl sowie normale Rückkehr prüfen. Die vorhandene Quelltextprüfung bleibt höchstens als
  Architekturgrenze, nicht als Ersatz für diese Verhaltensprüfung.
- Gezielte Gegenproben für historischen break, falsches Ziel und unterbundene Speicherung
  lokal/isoliert durchführen; Änderungen danach zurücknehmen und Ergebnisse protokollieren.
- Zuerst gezielte Tests, dann vollständiger bisheriger lokaler Check und PR-Matrix. Test-/Harness-
  Änderungen erfordern Instrumentierung, aber bei unverändertem Produkt-/Upgrade-Probe-Code
  keinen Produktrelease. Ein abweichender Scope wird vor Auslieferung ausdrücklich geprüft.

### P1 – Korrekturrunde 1: Testaufbau

Der erste gezielte Lauf bestand die normalen Titel-/Später-Fälle und alle Coordinator-Fälle.
Der Mischfall scheiterte vor dem Bedienweg: `FlowGraphEdit` prüft `containsValue(null)`, was die
verwendete Java-`Map.of` bereits mit einer NPE abweist. Plan: wie der bestehende Flow-Testaufbau
`LinkedHashMap` verwenden; den zusätzlichen Abschnittsgrenzfall anschließend mitprüfen.
Kein Produktfix oder abgeschwächter Assertion-Umfang ist dafür erforderlich.

Die korrigierten Hosttests sind grün (zehn Integrationsfälle auf API 26/35 und fünf
Coordinator-Tests). Der parallel angeforderte native Build sah beim laufenden Umbenennen der
neuen Hilfsklasse einen inkonsistenten Quellstand und scheiterte an deren Auflösung. Korrekturplan:
Quellstand einfrieren und den nativen Build erneut ausführen. Das neue Szenario wird vom bereits
in allen normalen und Animations-Lanes ausgewählten `TodayInteractionInstrumentationTest`
aufgerufen; die CI-Auswahl muss dafür nicht verändert werden.

### P1 – Gezielte Validierung und Abgleich vor PR

- Hosttestlauf: zehn Integrationsfälle (fünf Szenarien jeweils API 26/35) und fünf Coordinator-
  Tests bestanden. Nativer APK-Build nach eingefrorenem Quellstand ebenfalls erfolgreich.
- Gegenproben, jeweils tatsächlich ausgeführt auf API 26 und 35: historischer `break` erzeugt
  `Unhandled Today action BRING_FIRST` über `DashboardRenderer` → ViewModel → Coordinator;
  falsches Dispatcher-Ziel und unterbundene Room-Speicherung ergeben jeweils unveränderte
  Reihenfolge `[A,B,C]` statt `[C,A,B]`. Alle sechs Negativfälle scheitern am erwarteten Verhalten.
  Die drei XML-Ergebnisse haben getrennte aktuelle Laufzeitstempel; keine Compilerfehler als
  Testversagen gezählt. Lokale Rohbelege: `/tmp/p1-mutation-*/result.xml` und `gradle.log`.
- Alle Gegenproben vollständig zurückgenommen; Produktdateien entsprechen unverändert dem
  Ausgangsstand. Der anschließende vollständige lokale Check prüft den wiederhergestellten Stand.
- Abgleich gegen Phasenplan: produktiver Renderer-/ViewModel-/Room-Bedienweg, dateibasierte
  Neuöffnung, Abschnittsende/-wechsel/Einzelfall, Mischfall mit nichtleerem Verlauf, Ressourcen,
  Kandidaten und Belohnungen abgedeckt. Coordinator prüft alle Befehlsarten; stateful Reorder
  behält seinen eigenen Idempotenztest. Keine zweite Produktverdrahtung eingeführt.
- Abgleich gegen Roadmap: natives Touch-/Accessibility-Szenario ist in allen bestehenden Today-
  Lanes ausgewählt und prüft produktiven Fokus plus Activity-Neuerstellung. Der tatsächliche
  native Lauf, vollständiger lokaler Gate, PR- und Main-Abschluss bleiben bis zu ihren Ergebnissen
  offen. Kein Release erforderlich: ausschließlich Tests und Ausführungsprotokoll geändert.
