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

### P1 – Integration und Abschluss

- PR 364, geprüfter Head `29ef4c5ba77da95b57cbae7906f330cec71db304`, Lauf 34698017126:
  Contracts, Goldens, Build und alle sechs normalen/animierten API-26/35/37-Lanes grün.
- Vollständiger lokaler `check-all.sh`: Exit 0, 788 Tests, 0 Fehler, 1 optionaler Benchmark
  übersprungen; Gradle-Gesamtdauer 7m51s einschließlich Tests, Lint und Builds. Identität und
  Größenlimits bestanden. Keine Gegenprobe im Produktcode verblieben.
- Squash-Main `5425bb51aeb1f5b0c2b397a95c13227b04f583d7`; exakter Main-Lauf 34699171018
  ebenfalls vollständig grün. Kein Produktrelease. Main und PR besitzen identische Git-Inhalte.
- PR-Jobzeiten: Contracts 2m34s, Goldens 1m56s, Build 6m14s; native normale Lanes
  4m18s/5m24s/4m54s, Animations-Lanes 4m42s/15m05s/8m02s (API 26/35/37).
  Der API-35-Animationslauf führte 36 Tests erfolgreich aus; kein Retry. Parallelzeiten sind
  nicht addierbar. Die bestehende Wiederverwendung greift nur bei Produktänderungen, weshalb
  Main für die Teständerung trotz identischer Inhalte nochmals dieselbe Matrix ausführte.
- Abschließender Abgleich gegen P1-Plan und kanonische Roadmap: alle P1-Nachweise erfüllt.
  Die weiterhin fehlende Pixel-Verbindung gehört zur separaten P0/P5-Geräteabnahme.

## P2 – Phasenplan vor Implementierung

Ausgangsstand: `5425bb51aeb1f5b0c2b397a95c13227b04f583d7`.
Branch: `codex/risk-based-today-verification`. P1 und dessen exakter Main-Lauf sind abgeschlossen.
Die Nutzerbeauftragung nimmt die in der Roadmap ausdrücklich beschriebene Vertragsänderung an.

1. `change_scope.py` bekommt vier versionierte Profile mit sichtbaren Gründen. Git-Status,
   neue Dateien, Umbenennungen/Löschungen, gemischte/unklare Diffs und fehlende Basis werden
   konservativ behandelt. Die Today-Positivliste umfasst nur die zwei benannten Dispatcher-
   Dateien plus unmittelbar zugehörige Hosttests; keine allgemeine Modulliste.
2. Vor Aufnahme zusätzlich die Handler-/Payload-Zuordnung des TodayCommandDispatcher durch
   gültige Befehle prüfen. Der bisherige Architekturtest sucht dort nur Quelltext. P1 belegt
   bereits den produktiven Titel-/Später-Weg und sämtliche Coordinator-Befehlsarten.
3. Lokalen Einstieg und `verify.yml` auf denselben Profilvertrag ausrichten. Docs bekommen
   schnelle Dokument-/Vertragsprüfungen; Hosts die Contracts; Today zusätzlich Build und einen
   API-35-Animationslauf der Today-Suite; Full behält die vollständigen bestehenden Lanes.
   Stabile Aggregat-Gates lehnen unpassende, fehlende, rote und fälschlich übersprungene Jobs ab.
4. PR-/Main-Wiederverwendung benötigt identischen Inhalt, identisches Profil samt Policy-Version
   und jeden ausgewählten Einzeljob. Ein profil-/policyabhängiger Jobname macht diesen Nachweis
   über die Jobs-API prüfbar. Reine Teständerungen dürfen künftig denselben strikten Nachweis nutzen.
5. Aktuelle signierte Quelle direkt bei Releaseplanung durch Tag, Commit, Version, Paket,
   Signatur, APK-/Metadatenhash und Quellschema binden. Die Metadatenformatversion ist nicht die
   Datenbankschemaversion; diese wird am unveränderlichen Quellcommit gelesen. Quelle und
   aktuelles Smoke-Fixture werden mit dem einmal gebauten Kandidaten archiviert, ohne spätere
   Latest-Auflösung. Fehlende oder widersprüchliche Quelle verhindert Veröffentlichung.
6. Eigener expliziter Current-Smoke-Vertrag mit Aufgaben, Today-Platzierung und Historie erlaubt
   Schema 27→27 bei höherer App-Version. Historische Fixtures bleiben beim strengeren
   Migrationsvertrag. Generiertes Smoke-Asset separat vom strikt manifestierten historischen
   Corpus einbinden. Aktueller Smoke für jeden Produktrelease, fünf historische Lanes zusätzlich
   für Full; alle auf denselben signierten Kandidaten, Publish abhängig von anwendbaren Erfolgen.
7. ADR-005/035, README und aktuelle Teststrategie gemeinsam aktualisieren. Historische Berichte
   und die kanonische Roadmap bleiben historische Belege; keine rückwirkende Umdeutung.
8. Gezielte Python-/Hostverträge, historische Diffs PR359/360/361/362, negative Freigabe- und
   Source-Pin-Fälle prüfen; P1-Gegenproben müssen im Today-Contracts-Scope enthalten sein.
   Danach vollständiger lokaler Gate und vollständiger P2-PR-Gate. P2 stuft seine eigenen
   Policy-/CI-/Build-/Probe-Änderungen nicht herab. Erwarteter Releasebedarf: ja; aktueller
   Smoke und alle historischen signierten Lanes sowie exakter Main-/Publish-Nachweis erforderlich.

Vor Produktänderungen in P3 bleiben Bootstrap-/Ursachenbefunde vorbereitend. Die offene
Pixel-Abnahme ist kein Ersatz für die automatischen Freigaben und wird nicht als erledigt geführt.

### P2 – Zwischenvalidierung und Korrekturrunde 1

- Statusbewusste Klassifikation: 14 gezielte Tests grün. Dispatcher-Verhaltensvertrag für alle
  29 Befehlsarten inklusive Payload, Einzelaufruf und regulärer Rückkehr grün (Hostlauf 12s).
- Wiederverwendung prüft jetzt ausgewählte Einzeljobs, eindeutigen Profil-/Policy-Zeugen und
  identischen Git-Inhalt; auch Teständerungen sind zulässig. Ein neuerer roter/unvollständiger
  Lauf wird nicht durch einen älteren grünen verdeckt. Elf Tests einschließlich negativer Fälle
  für alle vier Profile grün. Fünf weitere Tests prüfen die tatsächlich ausgeführte Aggregatlogik.
- Lokale Profile und CI-Auswahl sind verdrahtet. Gesamte Python-CI-Suite: 41 Tests grün.
  Hostprofil führt auch Goldens aus, damit reine Änderungen an Golden-Tests nicht ausgespart werden.
- Aktueller Smoke: expliziter Vertrag Version 2 erlaubt gleiche Datenbankschemata; historischer
  Vertrag Version 1 bleibt strikt auf höhere Zielschema-Versionen begrenzt. Sechs Python-Tests
  prüfen Quellenbindung, negative Identitäten und vollständige synthetische Zeilen in SQLite.
- Quellenauflösung tatsächlich gegen GitHub ausgeführt: veröffentlichte 0.2.172, Tag
  `forest-android-1017201`, Commit `6ad6b6c6019a69eb75529479644df793074c4618`, Schema 27 aus
  genau dessen `DatabaseContract.java`. APK-Hash
  `84ce792395f217bc53ba984503bca40344f522389b159f6e545255cc90f1ea2b`; Metadatenhash
  `fe9ac31eeb0dce076332ac38c4932bf27a47df74ffde8f67c556137b9c88ee16`.
  Lokale Pin-Dateien: `/tmp/p2-current-source/`. Kein produktiver Installationsvorgang.
- Upgrade-Probe mit separat generiertem Current-Smoke-Asset gebaut: Exit 0, 55s. Dieser Build
  belegt Kompilierung und Asset-Einbindung, noch keinen signierten Android-Update-Lauf.
- Die Release-Python-Suite hat vier erwartete Vertragsabweichungen gefunden: bestehende
  Quelltextprüfungen verlangen noch Inline-Matrizen, pauschale Qualitätsflags und die bisherige
  eingebettete Shell-Gatelogik. Korrekturplan vor Änderung: auf die neue tatsächliche Verdrahtung
  zur versionierten Policy und getesteten Aggregatfunktion umstellen; vollständige API-Auswahl,
  stabile PR-Gates, Signierung und Kandidatenbindung weiterhin prüfen. Die neuen negativen
  Verhaltensprüfungen bleiben maßgeblich; keine rote Auswahl durch gelockerte Assertion akzeptieren.

Noch offen: aktuelles Fixture im produktiven Room-/Today-Ablauf, signierte Smoke-Ausführung,
Dokumentangleichung, historischer Diffvergleich, vollständiger lokaler Gate, P2-PR und Main/Release.

### P2 – Korrekturrunde 2: Room-Fixture-Testaufbau

Der erste native Build und die Quellenbindung sind grün; der zusätzliche Room-Hosttest scheitert
bereits beim Kompilieren, weil `AppDatabase` kein `AutoCloseable` implementiert. Plan: dieselbe
explizite `try/finally`-Schließung wie im bestehenden Produktions-Fixture-Test verwenden, danach
API-26/35-Lauf erneut ausführen. Das ist kein fehlgeschlagener Persistenznachweis und kein Produktfehler.
ADR-005 fordert für Vertragsänderungen eine ergänzende ADR. Der bereits angenommene P2-Vertrag
wird deshalb zusätzlich als neue Entscheidung dokumentiert und von ADR-005/035 referenziert.

### P2 – Korrekturrunde 3: fachlich vollständiges Smoke-Fixture

Nach korrigiertem Schließen startet der Room-Test auf API 26/35 und scheitert im echten
`MaterializeDueOccurrences`: `Active task has no schedule: current-smoke-task`. Das Fixture
war SQL-/FK-gültig, aber fachlich unvollständig. Plan: den erforderlichen regulären Zeitplan
mit sämtlichen Spalten und unverändertem fernen Fälligkeitsdatum ergänzen; alle Seedwerte nach
Materialisierung, Today-Projektion und Neuöffnung erneut prüfen. Weder den Startpfad umgehen
noch den Test auf bloßes Lesen reduzieren. Der historische Korpus bleibt unverändert.
Historische Git-Diffs frisch klassifiziert: PR 359/360/361 jeweils `full`, PR 362 `today`;
Rohbeleg `/tmp/p2-historical-diff-profiles.json`.

### P2 – Abgleich vor vollständiger Validierung

- Korrigiertes Current-Smoke-Fixture: produktive Materialisierung und Today-Projektion ergeben
  genau die offene Aufgabe im gespeicherten Mittagsabschnitt. Alle Seedspalten inklusive
  Zeitplan und abgeschlossenem Verlauf bleiben über Neuöffnung erhalten; API 26/35 grün (47s).
  Das Fixture-Verzeichnis ist als Gradle-Testeingabe registriert, damit Änderungen Tests neu auslösen.
- Release-Verträge auf tatsächliche neue Verdrahtung umgestellt: 38 Python-Release-Tests grün.
  42 Python-CI-Tests grün; die neue ausführbare Publish-Prüfung lehnt fehlenden/roten aktuellen
  Smoke und fehlende/rote/fälschlich übersprungene historische Vollprüfungen ab. Anschließend
  zusätzlicher Negativtest gegen abgeschnittene GitHub-Jobantworten ergänzt.
- Workflow mit `actionlint` v1.7.12 geprüft, Exit 0; Werkzeugarchiv gegen veröffentlichten
  Prüfsummenbeleg geprüft. YAML-Struktur und `git diff --check` ebenfalls fehlerfrei.
- Abgleich gegen P2-Phasenplan: vier Profile und lokale/CI-Übereinstimmung umgesetzt; Host-Goldens
  enthalten; Dispatcher-Verhalten geprüft; Einzeljob-/Profil-/Policy-/Inhaltsnachweis für Main;
  aktuelle Quelle bei Planung gebunden; eigener Same-Schema-Smoke und separate Assets;
  vollständige historische Matrix bei `full`; aktueller und anwendbarer historischer Gate vor
  Publish; ADR-037 ergänzt die aktualisierten ADR-005/035, README, Releaseguide und Teststrategie.
- Abgleich gegen kanonische Roadmap: keine Pauschalabschaffung historischer oder Android-Lanes,
  keine erweiterten Today-Produktdateien, P1-Tests im Contracts-Scope, historische Diffs passend.
  P2-Arbeitsstand klassifiziert sich selbst als `full` mit Releasebedarf. Originalroadmap und
  Frontend-Checkout bleiben unverändert. Kein P3-Produktfix in dieser Phase.
- Noch unbewiesen und vor Phasenabschluss erforderlich: kompletter lokaler Check, vollständige
  P2-PR-Läufe, Squash und exaktes Main, aktueller plus fünf historische signierte Upgrades und
  bytegeprüfte Veröffentlichung. Die Pixel-Abnahme bleibt unabhängig davon offen.

### P2 – Korrekturrunde 4: native Wartegrenze und tatsächlicher PR-Testinhalt

Der vollständige lokale Gate bestand auf `e4e66733` (Exit 0, 13m58s; 791 Tests, keine Fehler,
1 optionaler Benchmark übersprungen; Lint/Build/Identität/Größe grün). PR 365, Lauf 34702118064,
bestand Quality und fünf Android-Lanes. API-35-Animation scheiterte am 20-Minuten-Schrittlimit.
32/36 Tests waren fertig. Das fortlaufende geräteseitige Protokoll identifiziert den neuen
`titleTouchAndAccessibleLaterChangeActualFocusAndPersistOnRecreation`: Start 15:38:43Z,
MainActivity resumed 15:38:43Z, erste Neuerstellung erst 15:44:53Z, laufende Frames bis zum
Abbruch 15:49:06Z. Keine beobachtete App-Exception in diesem Bedienweg. Rohbelege:
`/tmp/p2-api35-failure/` und `/tmp/p2-pr-failed.log`.

Der tatsächlich eingebundene AndroidX-Test-Core 1.7.0 ruft in `ActivityScenario.launch`,
`onActivity`, `recreate` und seinen Zustandsabfragen jeweils `Instrumentation.waitForIdleSync`
auf (Bytecodebeleg `/tmp/p2-activity-scenario.txt`). Diese unbeschränkten Leerqueue-Wartepunkte
liegen außerhalb des signalgebundenen `PresentationAwaiter`-Timeouts. Die Logs stützen eine
Blockade an diesen globalen Wartepunkten; ein lokaler nativer Reproduktions-/Stacknachweis wird
vor der endgültigen Ursachenbehauptung ergänzt.

Plan: isolierten API-35-Emulator bereitstellen und den unveränderten fehlgeschlagenen Stand
gezielt untersuchen. Für den animierenden Produktbildschirm die globale Queue-Ruhe durch
beobachtete echte Lifecycle-Ereignisse mit harten Zeitgrenzen ersetzen, sofern die Reproduktion
diese Ursache bestätigt. Titel-Touch, Accessibility-Später, Fokus, Speicherung und echte
Neuerstellung beibehalten. Keine Animation abschalten, Assertion abschwächen oder identischen
roten Lauf automatisch wiederholen. Der Produktcode und TouchGestureDriver bleiben außerhalb
solcher Harness-Korrekturen, solange kein eigener Defekt nachgewiesen ist.

Zusätzlicher P2-Auditbefund bei Artefaktzuordnung: GitHub testet im PR den synthetischen Merge
(`GITHUB_SHA`), während die Runs-API `head_sha` des Themenbranches meldet. Nur dessen Baum gegen
Main zu vergleichen ist als allgemeiner Inhaltsnachweis zu schwach, etwa wenn Basisänderungen
zwischen PR-Test und Merge hinzukommen und wieder zurückgenommen werden. Plan: zusätzlich den
wirklich ausgecheckten Git-Baum als erfolgreichen, eindeutigen Jobzeugen binden und bei Main-
Wiederverwendung exakt prüfen. Gegenproben für fremden/fehlenden/doppelten Baumzeugen ergänzen.
Danach passende lokale Prüfungen und vollständiger PR-Gate auf dem korrigierten Head; kein Merge
mit dem gegenwärtig roten Nachweis.

Die erste Prüfung des Baumzeugen besteht 44 CI-Vertragstests und Actionlint. Zwei bestehende
Release-Verdrahtungsassertionen erwarten noch die alten Abhängigkeitslisten; sie werden vor
der nächsten Prüfung um den jetzt verbindlichen Inhaltszeugen ergänzt. Negative ausführbare
Gatetests verlangen dessen Erfolg bereits in Quality, Android und PR.

Lokale native Vorbereitung: API-35-Systemabbild installiert, eigenes AVD unter
`/tmp/p2-avd-home` mit 2-GB-Testdatenpartition angelegt. Der vorhandene Emulator 36.4.9
segfaultet vor abgeschlossenem Boot sowohl mit swiftshader_indirect als auch swiftshader
ohne Vulkan. Damit ist ein Host-/Emulatorfehler belegt, seine genaue interne Ursache nicht.
Kein App-Test wurde dabei ausgeführt. Für die Reproduktion wird das offizielle stabile
37.1.11-Binary aus Googles SDK-Metadaten getrennt vom bestehenden SDK entpackt und dessen
veröffentlichte Prüfsumme geprüft; CI und Produkt bleiben dadurch unverändert.

Der Host-Coredump grenzt den ersten Emulatorabsturz auf einen SwiftShader-Worker
(`libGLESv2.so`) ein; Rohbeleg `/tmp/p2-emulator-host-backtrace.txt`. Auch das aktuelle
37.1.11-Binary scheitert mit SwiftShader vor Boot. Der nächste lokale Versuch verwendet
daher die vorhandene Intel-GPU ohne Vulkan; Animationseinstellungen bleiben 1.0. Dies ist
keine Wiederholung eines unveränderten App-/CI-Tests.

API35 mit Intel-GPU bootet. Der erste Instrumentierungsstart wurde vom Android-System
nach 30 Sekunden noch beim DEX-Klassenladen abgebrochen; kein JUnit-Test gestartet. Gleichzeitig
ANRs mehrerer Google-Systemapps, CPU-Load 29.52 bei zwei Kernen. Belege:
`/tmp/p2-native-before-all-logcat.txt`, `/tmp/p2-emulator-anr/`. Dies reproduziert nicht den
CI-Wartefehler. Vor erneutem diagnostischem Teststart wird der Erstboot-/Paketladezustand
beendet und dessen Last geprüft; keine Änderung von Animationen oder Testassertionen.

Die tatsächliche App-Testreproduktion bleibt getrennt von den Host-/Erstbootfehlern offen.
Der Bytecode belegt die unbeschränkte Synchronisierung unabhängig davon. Die begrenzte
Lifecycle-Hilfe wird daher jetzt vorbereitet, während das eigene AVD mit vier Kernen/4 GB
und vorhandenem Bootbestand startet. Vor Integration müssen der native Bedienweg und echte
Neuerstellungen bestehen. Eine nicht erfasste CI-Stackposition wird nicht nachträglich als
bewiesen ausgegeben; der unveränderte Test-APK bleibt für die Ursachenprobe erhalten.

Die Lifecycle-Hilfe kompiliert (1m04s). Die gemeinsame `@After`-Methode enthält für
den Produktfall ohne Harness-Activity noch einen unnötigen unbeschränkten Idle-Wait. Plan:
Diesen Wartepunkt nur zusammen mit der tatsächlich vorhandenen alten Harness-Activity ausführen;
der Produktfall beendet und beobachtet seine Activities bereits in der begrenzten Session.

### P2 – Direktnachweis und Prüfung der Wartekorrektur

Nach beendetem Erstboot und gezielter DEX-Vorkompilierung ausschließlich der beiden isolierten
Testpakete startet der unveränderte Test. Bei aktiven Animationen bleibt er nach Titel-Touch,
Fokus- und Queue-Assertion in der ersten Neuerstellung stehen. SIGQUIT-Stack vom 18:20:50Z:
`Instrumentation$Idler.waitForIdle` → `Instrumentation.waitForIdleSync` →
`ActivityScenario.getCurrentActivityState` → `ActivityScenario.recreate` →
`TodayProductInteractionScenario.execute:68`. Rohbeleg `/tmp/p2-native-before-wait-stack.txt`,
Zeilen 206–222, plus `/tmp/p2-native-before-compiled-result.txt`. Damit ist der zuvor nur aus
CI-Verlauf/Bytecode abgeleitete konkrete Wartepunkt lokal reproduziert. Die exakte Stackposition
des früheren CI-Laufs selbst wurde dort nicht erfasst.

Die Korrektur ersetzt ausschließlich die Lifecycle-Synchronisierung des echten Produktfalls
und seinen unnötigen gemeinsamen Nachlauf durch beobachtete, zeitbegrenzte Ereignisse. Touch,
Accessibility, Fokus-/Reihenfolge-/Erhaltungsassertionen und zwei echte Neuerstellungen bleiben.
Vollständiger lokaler Check auf korrigiertem Code: Exit 0, 34s (vorhandene unveränderte Host-
Ergebnisse wiederverwendet; 44 CI- und 38 Release-Python-Tests frisch, native APKs neu gebaut).
Actionlint und Diffprüfung grün. Native Prüfung des korrigierten APK steht als nächster Schritt an.

Korrigierter nativer Produktfall auf demselben API-35-AVD erfolgreich: 1 Test, 0 Fehler,
10.778s. Alle drei Animationsskalen unmittelbar vorher als 1.0 bestätigt. Lifecycle-Protokoll
zeigt drei unterschiedliche MainActivity-Instanzen mit zwei vollständigen Zerstörungs-/Start-
Übergängen und abschließender Zerstörung. Belege `/tmp/p2-native-fixed-result.txt` und
`/tmp/p2-native-fixed-logcat.txt`. Kein neuer Touch-Treiber, keine Koordinatenwiederholung,
kein Abschalten der Animationen und keine abgeschwächte fachliche Assertion.

Abgleich Korrekturrunde 4 gegen Plan: konkreter Wartepunkt mit unverändertem APK reproduziert,
begrenzte Lifecycle-Synchronisierung implementiert und nativ bestätigt; tatsächlicher PR-Testbaum
als eigener erfolgreicher Zeuge gebunden und Gegenproben bestanden. Abgleich gegen Roadmap:
P1-Bediennachweis erhalten; P2-Inhalts-/Profil-/Policy-Nachweis geschlossen; vollständiger lokaler
Gate erfolgreich. Gegenwärtig offen bleiben vollständiger PR-Gate auf diesem neuen Head,
Squash/exaktes Main, signierte aktuelle/historische Upgrades und Veröffentlichung. P3 beginnt
weiterhin erst nach P2-Abschluss. Pixel-Abnahme bleibt separat offen.
