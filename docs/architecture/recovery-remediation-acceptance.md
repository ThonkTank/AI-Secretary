# Recovery-Remediation: Abschlussabgleich und offene Geräteabnahme

Stand: 2026-09-13. Referenz ist die unveränderte
[Roadmap](recovery-remediation-roadmap.md), Durchführung und Korrekturrunden stehen im
[Ausführungsprotokoll](recovery-remediation-execution.md).

**Software geliefert; Gesamtabnahme offen.** P0-Belegbestand und P1–P4b sind geprüft auf
Remote-Main integriert. Der letzte Produktstand ist 0.2.175 / 1017501, Schema 27,
Commit `c87d3240e0394681d2a57724ee82b64d58fc9572`. Der geprüfte Dokumentationsabschluss
`1e3b8a40a5e38429455c3c3b9c41539fa6263f71` verändert gegenüber diesem Produktstand nur das
Ausführungsprotokoll. Dieser P5-Bericht ändert ebenfalls keine Software oder Freigaberegel.
Die aktuelle USB-Abfrage findet kein Gerät. Installierter Pixel-Stand und sichtbare Abnahme
werden deshalb nicht aus CI, Release oder historischen Protokollen abgeleitet.

## Ergebnis je Befund

| Befund | Tatsächliches Ergebnis | Grenze |
|---|---|---|
| R1 – getrennte UI-/Speicherungstests | Echte Today-Verdrahtung bis zur file-backed Room-Datenbank geprüft; drei gezielte Fehlergegenproben erkannt | Native Geste, Activity-Neuerzeugung und Datenbank-Neuöffnung sind unterschiedliche Nachweise; Pixel-Bestand noch offen |
| R2 – pauschale Prüfpflichten | Vier konservative Profile, gemeinsamer lokaler/CI-Vertrag, aktuelle signierte Upgradequelle und geprüfte Main-Wiederverwendung | Keine pauschale Behauptung, alle sechs Android-Läufe oder historische Upgrades seien unnötig; keine gemessene Today-Profil-Zeitersparnis behauptet |
| R3 – zu später Diagnosezustand | Früher Bootstrap, Room-Sperre, native Ereignisse und anschließender Normalbetrieb geprüft | Schutz gilt für die Fach-Datenbank; Framework-/WorkManager-Verwaltungsdaten können geschrieben werden. APKs bis 0.2.173 unterstützen den neuen Diagnosevertrag nicht |
| R4 – unklarer Nachweisstatus | Lokaler Check, PR, exakter Main, signierter Kandidat, veröffentlichte Bytes und Gerät werden getrennt zugeordnet | Der Geräteanteil ist noch nicht geschlossen |
| U1 – Datenursprung | Migration 22→23 als Erzeuger aller drei Waisenkategorien reproduziert und in P4b repariert | Keine Rekonstruktion persönlicher Gerätehistorie; ursprünglicher Erzeuger des rückständigen Zählers weiterhin unbekannt |

Bereits vor dieser Roadmap gelieferte Schutzmechanismen bleiben erhalten: reguläre Rückkehr
von BRING_FIRST, atomare typisierte Archivierung, vollständige FK-Fehlermeldung und
Kennungsvergabe unter Berücksichtigung vorhandener Historie und belegter Schlüssel.
P4b entfernt ausschließlich als unbenutzt erkannte Snapshots bei der Kandidatenumwandlung;
Ausführungsergebnisse, Timer, Fortschritt und beide Reward-Verknüpfungen schließen den
betroffenen Ablauf von dieser Bereinigung aus. Bestehende Archivzeilen werden nicht entfernt.

## Zuordnung zu Integration und Veröffentlichung

Die Links bezeichnen abgeschlossene GitHub-Läufe, keine lokalen Absichtserklärungen.
Die vollständigen Heads, Korrekturrunden und lokalen Ergebnisse stehen im Ausführungsprotokoll.

| Phase | Geprüfter PR | Squash auf Main | Exakter Main-Nachweis | Veröffentlichung |
|---|---|---|---|---|
| P0 | [363](https://github.com/ThonkTank/AI-Secretary/pull/363), Lauf 34697046556 | `da6e9a41` | [34697081276](https://github.com/ThonkTank/AI-Secretary/actions/runs/34697081276) | kein Produktrelease |
| P1 | [364](https://github.com/ThonkTank/AI-Secretary/pull/364), Lauf 34698017126 | `5425bb51` | [34699171018](https://github.com/ThonkTank/AI-Secretary/actions/runs/34699171018) | kein Produktrelease |
| P2, einschließlich Korrektur | [366](https://github.com/ThonkTank/AI-Secretary/pull/366), Lauf 34707845004 | `d0099a63` | [34708748445](https://github.com/ThonkTank/AI-Secretary/actions/runs/34708748445) | 0.2.173 / 1017301 |
| P3 | [367](https://github.com/ThonkTank/AI-Secretary/pull/367), Lauf 34713738417 | `3c0a873e` | [34714576280](https://github.com/ThonkTank/AI-Secretary/actions/runs/34714576280) | 0.2.174 / 1017401 |
| P4 | [368](https://github.com/ThonkTank/AI-Secretary/pull/368), Lauf 34716431030 | `d779ed38` | [34717366394](https://github.com/ThonkTank/AI-Secretary/actions/runs/34717366394) | kein Produktrelease |
| P4b | [369](https://github.com/ThonkTank/AI-Secretary/pull/369), Lauf 34720018607 | `c87d3240` | [34720901999](https://github.com/ThonkTank/AI-Secretary/actions/runs/34720901999) | [0.2.175 / 1017501](https://github.com/ThonkTank/AI-Secretary/releases/tag/forest-android-1017501) |
| P4b-Ergebnisprotokoll | [370](https://github.com/ThonkTank/AI-Secretary/pull/370), Lauf 34721551677 | `1e3b8a40` | [34721694044](https://github.com/ThonkTank/AI-Secretary/actions/runs/34721694044) | kein Produktrelease |

P4b: vollständiger lokaler `./scripts/ci/check-all.sh` erfolgreich, 793 Hostfälle,
0 Fehler, 1 ausdrücklich optionaler WoodGrain-Benchmark übersprungen; 55 CI- und 45
Release-Vertragstests, Lint, Builds, Identität und Größenprüfung erfolgreich. Lokaler geprüfter
Code `01f1dd6440c98d732bdfacdceca44453c4ebefdc`; zum finalen PR-Head
`bf42fe37e440929cba3361fc1b08660421ff7031` änderten sich nur ADR-005 und Ausführungsprotokoll.
Alle 15 anwendbaren PR-Jobs sind erfolgreich. Der normale native Korpus entdeckt auf API
26/35/37 jeweils 69/69/48 Fälle, mit je zwei absichtlichen UpgradePersistenceTest-Skips;
die signierten Upgradeprüfungen laufen separat. Alle sechs normalen/animierten Android-Lanes
sind grün. Zusätzlich bestätigen die Diagnose-Läufe auf allen drei APIs jeweils Schema
24 und 27 mit regulärem Ende und Abbruch sowie Normalstart.

Main verwendet diese PR-Belege nur für denselben geprüften Baum
`5ed682fbac2fb9a0bd2cfd9a2a38fafeda92e0ad`, Profil `full`, Policy 1.
Die tatsächlichen Policy-Eingaben nennen PR369, Head `bf42fe37`, Lauf 34720018607 und
`identical_tree_and_green_pr`. Packaging, aktueller signierter Smoke und sechs historische
signierte Lanes wurden auf Main zusätzlich ausgeführt; keine zweite normale/animierte Matrix.

| Signierter Fall auf genau diesem Kandidaten | Ergebnis / Job |
|---|---|
| Aktuell: 0.2.174 → 0.2.175, Schema 27→27, API 35 | erfolgreich, 103626864240; Quelle und Ziel unterstützen Diagnose |
| Schema 8, API 26 / 35 / 37 | erfolgreich, 103626864385 / 103626864290 / 103626864302 |
| Schema 20, API 26 | erfolgreich, 103626864353 |
| Schema 22, API 26 | erfolgreich, 103626864338; sauberer Kandidat erzeugt keine Recovery-Zeilen |
| Schema 23, API 26 | erfolgreich, 103626864323 |

Öffentlicher Release vom 2026-09-12T21:53:55Z: heruntergeladene APK und Metadaten sind
vollständig bytegleich zum getesteten Kandidaten. Ihre Hashes stimmen mit den am 2026-09-13
erneut gelesenen Release-Asset-Digests überein:

- APK: `0e5e49bfd7dd26e4ac3a96c9977915aed1ec2f9cceac505119f08fdc5e7e3a7b`.
- Metadaten: `2b773a0525c05c3715b9a908a294a6fb34c64a6f111bd681a127e86c86d14699`.
- Reguläres Signaturzertifikat von App und passendem Helper:
  `de45d94c9724beeaa2e0dff31f69f53bb0f4c9ba79a5aa419d1f29d18f4d91da`.

## Anforderungsabgleich

„Software belegt“ schließt keine Geräteabnahme ein. Zusammengefasste Nummern teilen den
genannten Nachweis; die jeweiligen konkreten Wirkungen werden ausdrücklich aufgeführt.

| Roadmap-Anforderung | Geprüfter Nachweis und Ergebnis | Status |
|---|---|---|
| P0.1 | Ausgangs-Main/PR362/Release 0.2.172 im P0-Protokoll gebunden; historische installierte Version getrennt vom heute unbekannten Pixel-Stand | Belegbestand erfüllt |
| P0.2–3 | Vier schon reparierte Fehler getrennt von R1–R4/U1; ursprüngliche Absturzprotokolle und Archive 8/4/2 lokal zugeordnet, keine persönliche Datenbank als Repositoryfixture | erfüllt |
| P0.4 | Baseline lokal 22m44s, PR 20m22s, Main 7m11s; einzelne Jobs und 35 erfolgreiche API-35-Animationsfälle dokumentiert; Parallelität nicht addiert | erfüllt |
| P0.5 und Geräteanteil der P0-Abnahme | Sichtbarer Titel-/Später-Test auf 0.2.172 damals nicht belegt; wird nicht rückwirkend behauptet oder durch Downgrade nachgeholt | historisch unbelegt; aktuelle Abnahme in P5 offen |
| P1.1 | TodayActionIntegrationRobolectricTest: echter DashboardRenderer → TodayViewModel → ApplicationUseCaseComposition → dateibasierte Room-DB; keine Recorder hinter dem Klick | Software belegt |
| P1.2 | Titel-Test C hinter A/B → C/A/B, relative Reihenfolge und Zustand nach DB-Neuöffnung geprüft | Software belegt |
| P1.3 | Drei Später-Szenarien prüfen Abschnittsende, Wechsel hinter vorhandene Einträge des Folgeabschnitts und Einzelaufgabe über alle Abschnitte bis ausgeblendet, einschließlich Persistenz | Software belegt |
| P1.4 | Mischfall vergleicht vollständigen Fachsnapshot mit Historie, Kandidaten, Ressourcen und Rewards; keine neuen Starts/Abschlüsse | Software belegt |
| P1.5 | TodayProductInteractionScenario: echte MainActivity, nativer Titel-Down/Up, Accessibility-Später, zustandsbasiertes Warten und Activity-Neuerzeugung; nur isoliertes Testpaket | Software belegt |
| P1.6 | TodayCoordinatorTest führt alle Befehlsaktionen aus, prüft genau einen Befehl/Payload und normale Rückkehr; Dispatcher-Test prüft tatsächliche Handler | Software belegt |
| P1-Abnahme | Zehn Hostfälle auf API 26/35 zuletzt grün; die drei isolierten Gegenproben historical-break/wrong-target/no-persistence scheitern je auf beiden APIs am erwarteten Verhalten. Nativer Test in normalen und animierten PR-Lanes; P1 lokaler Vollcheck, PR und Main zugeordnet | Software belegt |
| P2.1 | change_scope.py lässt ausschließlich bestehende TodayCoordinator-/TodayCommandDispatcher-Produktdateien samt direkt zugehörigen Tests zu; neue Dateien, fremde Verträge, Mischdiffs, Rename/Delete werden konservativ behandelt | Software belegt |
| P2.2 | Profil/Gründe im Workflow ausgegeben; test_change_scope.py prüft ungültige Eingaben und fehlende Diffbasis; keine Label-Freischaltung | Software belegt |
| P2.3–4 | current_upgrade_smoke.py bindet veröffentlichte Quelle über Tag/Commit/Version/Metadaten/Bytes; separater current-Vertrag erlaubt höhere App-Version bei Schema 27→27. Fixture erhält Aufgabe, Mittagsplatzierung und Historie | Software und signierter Übergang belegt |
| P2.5 | Fehlende passende aktuelle Quelle scheitert ausdrücklich; full behält historische Quellen auf demselben Kandidaten. P4b ergänzt Schema22: sechs statt ursprünglich fünf historische Lanes | Software und sechs historische Übergänge belegt |
| P2.6 | reuse_pr_verification.py und verification_gate.py prüfen tatsächlichen Testbaum, eindeutige PR-Zuordnung, Profil/Policy, vollständige Jobantworten und Erfolg. Negative Tests für fehlende/rote/skipped/fremde Nachweise; tatsächliche Main-Wiederverwendung oben gebunden | Software belegt |
| P2.7 | ADR-005/035/037, README, Releaseguide und Teststrategie aktualisiert; bewusster full-Lauf bleibt verfügbar | erfüllt |
| P2.8 | check-changes.sh verwendet denselben Classifier: docs/host/today/full → check-docs/host/fast/all. Today lokal Contracts samt P1, verpflichtender Build/native Nachweis in CI; kein nicht ausgeführter lokaler Build als grün bezeichnet | Software belegt |
| P2-Abnahme: Klassifikation/Gegenproben | Vertragstests decken Dokumente, Hosttests, Today, Migration, Plattform, Manifest/SDK, neue/umbenannte/gelöschte Dateien, CI/Policy und fehlende Basis ab. Historische PR359/360/361 als full, PR362 als today klassifiziert; P1 bleibt im Contracts-Scope | belegt |
| P2-Abnahme: Wirkung/Einführung | P2 selbst durch vollständigen PR-/Releasegate integriert. Auswahl und konkrete Risiken unten bewertet; reale Main-Wiederverwendung belegt. Kein schmaler Today-PR-Lauf als Zeitbenchmark vorhanden | Umfang belegt; Zeitgewinn nicht beziffert |
| P3.1–2 | EarlyDiagnosticInstrumentation aktiviert Bootstrap in newApplication vor App/Providern; DiagnosticWitnessProvider, Room-Sperre und native Diagnose beweisen frühzeitigen Schutz, keine bloße onCreate-Zählung | Software belegt |
| P3.3 | Tatsächliche Boot-/Paketwechsel-/Timer-/Widget-/Activity-Ereignisse sowie Worker und gebundener SystemJobService im Diagnoseprozess; typisierte Schema-/Nutzdatensnapshots unverändert. Nach Ende/Abbruch Normalstart, Timerabgleich und Worker-Erfolg geprüft | Software belegt |
| P3.4–5 | Neuer Runner weist fehlenden Bootstrap explizit vor App-Erzeugung ab; historische seed/verify bleiben unterstützt. Modus nur pro Prozess, keine dauerhaften Geräteeinstellungen/Komponentenabschaltung | Software und signierte Kompatibilität belegt |
| P3-Abnahme: native/Upgrade | Auf API 26/35/37 je vier Schema-/Endefälle samt ursprünglicher PID und Vollsnapshot; aktuelle P4b-PR-Protokolle bestätigen alle zwölf Fälle. Signierte aktuelle und historische Upgrades separat grün | belegt |
| P3-Abnahme: Pixel | Unterstützte lesende Diagnose, Helper-Entfernung und Normalstart am tatsächlichen Gerät noch auszuführen | offen |
| P4.1–2 | Herkunftsbericht trennt drei FK-Kategorien von Zähler; direkte historische Umbauten, Snapshot-/Lösch-/Abbruch-/Abschlusspfade und Vergabe untersucht; Herkunft24 nicht als Entstehungsversion ausgegeben | erfüllt |
| P4.3–4 und Abnahme | Native echte Room-Upgradeverbindung misst FK=0 in 22→23 und FK=1 nach onOpen. Drei Waisenkategorien reproduziert; physische Schemahistorie berücksichtigt. Historische Zählerprobe mit 32 Übergängen und direkte Aufrufer ergeben nur begrenzten Negativbefund | Waisenerzeuger belegt; Zählerursprung unbekannt |
| P4b, bedingter Ursachenfix | Eigener vorab protokollierter Plan; explizite Kindbehandlung, konservative Auswahl. Neun native Erhaltungs-/Rollbackfälle; gültige 2-Schritt-Fixture erkennt alten Fehler. Signierter Schema22-Fall plus strikte positive/absent-Verifikationsverträge | Software, Main und Release belegt |
| P5.1 | Dieser Abgleich deckt alle nummerierten Anforderungen, Abnahmen und Befunde ab; Geräteanteile und U1-Grenze bleiben ausdrücklich offen | Softwareaudit erfolgt; Gesamtabschluss offen |
| P5.2 | Testgrenzen unten, aktueller Status im Ausführungsprotokoll und datierter Hinweis im Herkunftsbericht; frühere Fehlversuche bleiben historisch erhalten | Berichtsänderung erstellt; Integrationsbeleg im Ausführungsprotokoll |
| P5.3 | Letzter Produkt-Vollcheck, PR, exakter Main, signierte Upgrades und öffentliche Bytes oben zugeordnet; für diese Dokumente nur docs-Profil | Produktnachweise erfüllt; Dokumentgate separat |
| P5.4 | Aktuelle installierte Version/Herkunft, Kaltstart, frische Prozesslogs, Bestand, Titel/Später und Persistenz auf dem entsperrten Pixel fehlen | offen: Gerät nicht verbunden |
| P5.5 und Gesamtabnahme | Software/Release/Gerät getrennt berichtet. Ohne P5.4 kein vollständiger Abschluss | offen |

## Getrennter Abgleich gegen Phasenpläne und Grenzen

Gegen die vorab gespeicherten Phasenpläne: P0 liefert den Belegbestand; P1 die echte
Verdrahtung, den nativen Fall und isolierte Fehlergegenproben; P2 vier Profile samt lokaler
Auswahl, aktuellem signierten Smoke und Inhaltsbindung; P3 frühen Bootstrap, native Ereignisse
und Kompatibilitätsgrenze; P4 die begrenzte Untersuchung; P4b den belegten lokalen Ursachenfix
samt Erhaltung, Rollback und signiertem Schema22-Nachweis. Die notwendigen Fixture-,
Kalender-, Nachweis- und Dokumentkorrekturen sind vor ihren Änderungen im Ausführungsprotokoll
festgehalten. Die Ergebnisse werden jeweils ihrem tatsächlichen Prüfstand zugeordnet.

Gegen den P5-Phasenplan: Anforderungsmatrix, Befundstatus, Versionen und Nachweisgrenzen
sind erstellt. Die veraltete Freigabereferenz im Herkunftsbericht ist berichtigt. Die
Geräteschritte 3–4 des Phasenplans sind wegen fehlender Verbindung nicht ausgeführt; dessen
Punkt 5 erlaubt ausdrücklich den gesonderten Dokumentationsabschluss. Das macht P5 insgesamt
noch nicht vollständig. Dokumentprüfung und Integration gehören zum separaten docs-Gate.

Gegen die unveränderlichen Roadmap-Grenzen: Der kanonische Text bleibt unverändert; der
ursprüngliche Frontend-Checkout ist unberührt. Umsetzung und Korrekturen erfolgten über
Themenbranches, geprüfte PRs und Squash-Merges; kein direkter Main-Push. Der angenommene
P2-Prüfvertrag und seine Selbstklassifikation als full sind protokolliert. Keine neue
Technologie, kein anderes Domänenmodell und keine erfundenen Fortschritte wurden eingeführt.
Produktidentität, reguläre Signatur, steigende App-Versionen und öffentliche Byteprüfung
bleiben erhalten. Die bisherigen historischen Quellen bleiben im Korpus; Schema22 wurde
gezielt ergänzt. P5 verändert ausschließlich Dokumentation und erfordert keinen Produktrelease.
Für das Pixel gelten unverändert Datenerhalt, ausschließlich Vorwärtsupdate und keine
synthetischen Daten oder Fachaktionen als Test.

## Aussagegrenzen und Nutzen der Prüfungsauswahl

Die Host-Integration prüft die produktive Verdrahtung und das erneute Öffnen der Datenbank,
jedoch keine native Touch-Geometrie. Das native Szenario prüft den tatsächlichen Touch auf den
Titel und Accessibility-Klick auf Später sowie Activity-Neuerzeugung; es ersetzt keinen
Kaltstart des persönlichen Produktionsbestands. Der bestehende Quelltext-Vollständigkeitstest
ist ein Architekturhinweis, kein Verhaltensnachweis. Fehlermutationen wurden isoliert geprüft
und zurückgenommen; sie sind keine committed Produktvarianten.

Das Today-Profil beschränkt sich auf zwei plattformfreie Weiterleitungsdateien. Dafür bleiben
Host-Contracts auf API 26/35 und ein nativer animierter Today-Fall auf API 35 verpflichtend;
fünf weitere Android-Lanes werden für diesen eng abgegrenzten Diff nicht ausgewählt.
Speicherung, Migrationen, Lifecycle und Freigabelogik behalten dagegen das vollständige Profil.
Der neue signierte Schema22-Fall zeigt einen konkreten Nutzen historischer Upgradequellen:
Mit altem Produktcode scheitert die Erwartung fehlender Recovery-Zeilen, obwohl vorherige
positive Erhaltungserwartungen bereits bestehen. Solche Prüfungen sind kein Ersatz für einen
Titel-Klick, aber ein eigenständiger Datenübergangsnachweis.

Belegt ist außerdem die ausbleibende zweite Android-Matrix auf Main bei identischem grün
geprüftem Inhalt. Nicht belegt ist eine gemessene Gesamtdauer eines tatsächlich ausgelieferten
Today-Profil-PRs. Baselinezeiten und spätere full-Läufe werden deshalb nicht als kontrollierter
Vorher/Nachher-Vergleich ausgegeben. P3 ergänzt im finalen P4b-PR je 53.59/57.95/56.45 Sekunden
reine Diagnosefolge auf API 26/35/37; das sind keine vollständigen Joblaufzeiten.

Die ursprüngliche Zählerabweichung bleibt mangels Vorgangsfolge oder Zwischenbestand unbekannt.
Die korrekte heutige Vergabe und erhaltende historische Modellübergänge beweisen nicht, dass
alle früheren Aufrufer, Versionen oder konkurrierenden Datenbankvorgänge fehlerfrei waren.

## Verbleibende P5-Geräteabnahme

Voraussetzung ist das verbundene, entsperrte Pixel 8. Der nächste Schritt ist eine frische
Geräte-/App-Identifikation, keine weitere allgemeine Testsuite.

1. Installierte Version und Herkunft erfassen, sichtbare vorhandene Aufgaben/Abläufe festhalten.
   Die historischen Belege zu 0.2.172 (in-place, Kaltstart, Archiv 8/4/2) sind kein aktueller Bestand
   und keine nachträgliche sichtbare Abnahme dieser alten Version.
2. Falls erforderlich und ausschließlich vorwärts: verifiziertes öffentliches 0.2.175 über die
   vorhandene Installation installieren. Bei bereits neuerem Stand zuerst dessen Herkunft
   prüfen; niemals downgraden oder App-Daten löschen.
3. Auf unterstütztem Stand den passenden regulär signierten Helper für lesende Diagnose nutzen.
   Alte APKs ohne Bootstrap nicht über den früheren unsicheren Diagnoseweg untersuchen.
   Keine seed-/Fixture-Kommandos am Pixel. Nur den Helper anschließend entfernen.
4. Normalen Kaltstart und frisches Prozessprotokoll prüfen; vorhandene Aufgaben/Abläufe sichtbar
   kontrollieren, eine bestehende Aufgabe per Titel vorziehen und Später auslösen. Wirkung nach
   Neustart prüfen; Auswahl nach Möglichkeit zurückstellen. Keine Erledigungen, Rewards oder
   neuen Ablaufstarts als Test auslösen.
5. Tatsächliche Ergebnisse mit Version, Zeitpunkt und beobachtetem Bestand nachtragen. Bei
   Fehlern die betroffene Prüfung offen lassen und gezielt diagnostizieren. P5 erst nach
   erfolgreicher Geräteabnahme vollständig schließen.

Die Geräteverfügbarkeit ist bereits angefragt. Schweigen ist weder ein Testergebnis noch eine
Abnahme. Dieser fehlende Nachweis blockiert den Gesamtabschluss, nicht die Integration dieses
Software-Abschlussberichts.
