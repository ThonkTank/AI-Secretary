# Roadmap: belastbare Bedienwege, gezielte Prüfungen und sichere Diagnose

Status: Entwurf zur Umsetzung; keine Produkt- oder Freigabeänderung ausgeführt.

Erstellt: 2026-09-12

Referenzstand: `6ad6b6c6019a69eb75529479644df793074c4618` (`origin/main`),
veröffentlichte Version 0.2.172, Room-Schema 27.

## Ziel und Herkunft

Diese Roadmap setzt das Remediation Assessment zur Absturzserie aus PR 358–362 um.
Sie ersetzt weder die bereits erfolgten Reparaturen noch startet sie einen allgemeinen
Architekturumbau. Das getrennte [Ausführungsprotokoll](recovery-remediation-execution.md)
enthält später Phase, Commit, Prüfbelege, Abweichungen und Geräteabnahme.

| Befund | Ergebnis dieser Roadmap |
|---|---|
| R1: getrennte UI- und Speicherungstests lassen den Bedienweg offen | Titel und „Später“ werden über die produktive Verdrahtung bis zur dauerhaften Wirkung geprüft |
| R2: pauschale Android- und Upgrade-Pflichtmatrix | Ein eng begrenztes Today-Profil prüft passende Risiken; unbekannte Änderungen behalten den vollständigen Gate |
| R3: Diagnose unterdrückt App-Initialisierung, Hintergrundkomponenten erwarten sie trotzdem | Definierter Diagnosezustand mit überprüfbarer Schreibruhe und sicherem anschließenden Normalbetrieb |
| R4: Status- und Nachweisumfang teilweise unklar | Jede Phase trennt Implementierung, automatische Prüfung, Veröffentlichung und sichtbare Geräteabnahme |
| U1: ursprünglicher Erzeuger verwaister Daten und alter Zähler unbekannt | Begrenzte Ursachenuntersuchung mit belegtem Ergebnis; keine erfundene Ursachenreparatur |

Bereits erledigt: BRING_FIRST kehrt korrekt zurück; die beobachteten Waisen werden atomar
archiviert; die Sequenzvergabe berücksichtigt Historie und belegte Schlüssel; Fremdschlüsselfehler
werden vollständig berichtet. Diese Korrekturen bleiben erhalten.

## Unveränderliche Grenzen

- Kein Löschen produktiver App-Daten, keine Neuinstallation und kein Downgrade auf dem Pixel.
- Keine erfundenen Abläufe, Fortschritte oder Belohnungen. Das Wiederherstellungsarchiv bleibt erhalten.
- Keine Änderung von Room, UI-Framework oder Programmiersprache als Selbstzweck.
- Keine pauschale Entfernung von Plattform- oder historischen Upgrade-Tests.
- Produktionsartefakte behalten Paketidentität, reguläre Signatur, höhere Versionscodes und
  die Prüfung der veröffentlichten Bytes gegen den getesteten Kandidaten.
- Unterstützte Android-Versionen und historische Upgradequellen bleiben unterstützt. Eine
  gezielte Auswahl pro Änderung ist keine stillschweigende Einstellung ihrer Unterstützung.
- Der ursprüngliche Frontend-Checkout bleibt unberührt. Jede Implementierungsphase beginnt
  auf einem eigenen `codex/`-Themenbranch vom dann aktuellen `origin/main`.
- Repository-Regel: Commit, PR gegen `main`, alle anwendbaren Checks grün, Squash-Merge.
  Anschließend wird der exakte Main-Lauf geprüft. Keine direkten Main-Pushes.

## Freigabevertrag und Phasenabschluss

Bis zum Abschluss von P2 gelten die bestehenden Pflichtprüfungen vollständig. P2 ändert
ausdrücklich den Prüfvertrag aus ADR-005 und die verpflichtende Auswahl des in ADR-035
beschriebenen Fixture-Korpus. Die Umsetzung dieser Vertragsänderung bedarf der Annahme dieses
Roadmap-Entwurfs durch den Repository-Verantwortlichen; der vorliegende Plan ändert keine Regel.

Die PR-Prüfung von P2 selbst bleibt vollständig. Änderungen an Prüfklassifikation,
Freigabelogik, Build oder Signierung dürfen sich durch ihre eigene neue Regel nicht auf ein
kleines Profil herunterstufen. AGENTS.md mit PR-/Squash-Pflicht bleibt unverändert.

Vor jedem Implementierungsschritt wird im Ausführungsprotokoll ein konkreter Phasenplan
festgehalten. Nach Tests folgt ein gesonderter Abgleich gegen diesen Plan und die Roadmap.
Bei einer Abweichung werden Ursache und begrenzte Korrektur vor weiteren Änderungen notiert.
Abgeschlossene Belege werden nicht nachträglich zu einem anderen Prüfstand umgeschrieben.

Der pro Phase erwartete Releasebedarf wird vorab anhand der geltenden Klassifikation notiert.
Insbesondere können CI- und Upgrade-Probe-Änderungen heute einen Release auslösen. Ein solcher
Lauf wird nicht überraschend als „nur Tests“ behandelt. Reine Dokumentation erzeugt keinen
Produktrelease. Ein erforderlicher Release ist erst nach Packaging, anwendbaren signierten
Upgrades und Veröffentlichung abgeschlossen; eine offene Pixel-Prüfung bleibt separat offen.

## Reihenfolge und Aufwand

| Phase | Abhängigkeit | Umfang | Abschlussartefakt |
|---|---|---|---|
| P0: Belege und Abnahmebestand | keine | S | überprüfter Ausgangsstand und Nachweisliste |
| P1: vollständiger Today-Bedienweg | P0 | M | verknüpfte Regressionstests und natives Testszenario |
| P2: gezielte Freigabeprofile | P1 | M | getestete Klassifikation und aktualisierter Freigabevertrag |
| P3: robuster Diagnosezustand | P2 | M, ältere APK-Kompatibilität offen | Bootstrap-/Diagnosevertrag und Ereignistests |
| P4: begrenzte Ursachenuntersuchung | P0; P3 vor neuer Diagnose am Produktionsbestand | Untersuchung S–M, Reparatur unbekannt | reproduzierte Ursache oder ausdrücklich begrenzter Negativbefund |
| P5: Abschluss und Nachweisabgleich | P1–P4 | S zusätzlich | konsistenter Abschlussbericht und Geräteabnahme |

R4 wird in jeder Phase mitbearbeitet; P5 zählt diese Dokumentationsarbeit nicht ein zweites Mal.
U1 darf anhand vorhandener Artefakte bereits während P1/P2 untersucht werden. Es blockiert
deren Umsetzung nicht. Die Implementierungsphasen werden regulär nacheinander integriert.

## P0 – Ausgangsstand und Nachweise festhalten

Branch bei Dokumentationsänderungen: `codex/remediation-baseline`.

1. Remote-Main, aktuelle installierte Version und vorhandene PR-/Releasebelege abgleichen.
2. Die vier korrigierten Fehler getrennt von R1–R4 und U1 dokumentieren.
3. Vorhandene Geräteprotokolle, ursprüngliche Absturzreproduktion, Archivzählungen und
   Prüfberichte zuordnen. Keine private Gerätedatenbank als Repositoryfixture übernehmen.
4. Ausgangswerte der Prüfzeit festhalten: letzter lokaler Check 22m44s, PR-Lauf 20m22s,
   Main-/Release-Lauf 7m11s. Parallele Laufzeiten nicht addieren. Zusätzlich Jobdauer,
   ausgeführte Tests und konkrete abgedeckte Risiken erfassen.
5. Sichtbare Bestandsprüfung von Titel-Klick und „Später“ auf 0.2.172 durchführen, sobald das
   Pixel entsperrt verfügbar ist. Bereits vorhandene Aufgaben verwenden, keine Abschlüsse
   oder Ablaufstarts für diesen Test auslösen. Geänderte Auswahl nach Möglichkeit zurückstellen.

Abnahme: belegter Ausgangsstand; offene Nachweise einzeln benannt. Ein gesperrtes Pixel
blockiert nur die sichtbare Prüfung, nicht P1–P4. Ein neuer Fehler erhält eine eigene Diagnose
und wird nicht durch „Kaltstart erfolgreich“ überdeckt.

## P1 – Titel und „Später“ bis zur gespeicherten Wirkung prüfen

Branch: `codex/today-action-integration`.

Betroffen: `DashboardRenderer`, `TaskLeafView`/Fokusaktionen, `TodayViewModel`,
`TodayCoordinator`, `TodayCommandDispatcher` sowie bestehende Presentation-/Room-Testhilfen
und `TodayInteractionInstrumentationTest`. Produktionscode wird nur bei nachgewiesener
Verdrahtungslücke verändert; keine Testbarkeit durch eine zweite Produktverdrahtung erkaufen.

1. Bestehende Room-/ViewModel-Testaufbauten für eine gemeinsame, synthetische Testdatenbank
   verwenden. Der echte Renderer muss die Aktionen an die echte Today-Verarbeitung binden.
   Hinter dem Klick dürfen weder Befehl noch Speicherung durch Recorder ersetzt sein.
2. Titel-Klick: sichtbare Aufgabe B hinter A anklicken; B wird Fokus, übrige relative Reihenfolge
   bleibt erhalten. Datenbank schließen/neu öffnen und Projektion neu erzeugen: B bleibt vorne.
3. „Später“: Fokus ans Abschnittsende, von dort in den nächsten Abschnitt sowie den vorhandenen
   Ein-Aufgaben-Fall über die UI ausführen. Andere fachliche Grenzfälle bleiben in den
   vorhandenen schnellen Room-Tests statt vollständig in Gerätetests dupliziert zu werden.
4. Synthetischen Mischfall aus normaler Aufgabe und Ablauf prüfen. Unbeabsichtigte Abschlüsse,
   neue Ablaufstarts, Kandidaten-/Ressourcenänderungen und zusätzliche Belohnungen ausschließen.
5. Ein repräsentativer nativer Bedienfall prüft echte Touch-/Accessibility-Erreichbarkeit und
   aktualisierten Fokus. Sichtbarkeit wird abgewartet; keine Koordinaten-Wiederholungen oder
   festen langen Wartezeiten als Ersatz für einen verlässlichen Zustand.
6. Den Quelltext-Vollständigkeitstest nicht als Verhaltensnachweis ausgeben. Für die vom
   Coordinator behandelten Befehlsaktionen nachweisen, dass sie mit gültiger Eingabe genau den
   erwarteten Befehl senden und regulär zurückkehren. Keine weiteren String-Suchtests ergänzen.

Abnahme:

- Titel und „Später“ bestehen über die produktive Verdrahtung; Persistenz über Neuöffnung ist belegt.
- Der historische `break`-Fehler lässt den Test scheitern. Auch eine falsche Ziel-ID oder
  unterbundene Speicherung wird erkannt. Diese Gegenproben erfolgen isoliert und werden nicht
  als Produktänderungen committed; kein allgemeines Mutationstest-Framework nötig.
- Der genaue Umfang jedes Tests ist benannt: Touch, Verdrahtung, Speicherung oder Fachlogik.
- Bestehender vollständiger lokaler Check und geltende PR-Prüfungen grün; Main-Abschluss belegt.

## P2 – Kleine Today-Änderungen gezielt freigeben

Branch: `codex/risk-based-today-verification`.

Betroffen: `change_scope.py` und Tests, bestehende lokale Check-Skripte, `verify.yml`, Instrumentierungs-Auswahl,
`reuse_pr_verification.py`, Upgrade-Probe/Fixture-Auswahl sowie ADR-005/ADR-035 und Testdokumentation.

Das erste Ausbaustadium hat bewusst nur vier Profile. Es entsteht kein allgemeiner
Abhängigkeitsgraph und keine neue Orchestrierungsplattform.

| Profil | Zulässiger Umfang | PR-Nachweise | Produktrelease |
|---|---|---|---|
| Dokumentation | ausschließlich Dokumentation | Dokument-/Vertragskonsistenz | nein |
| Hosttests | ausschließlich Hosttests und eindeutig nicht produktive Hilfen | relevante Hostprüfungen | nein |
| Today-Steuerung | ausschließlich geprüfte plattformfreie Aktionsweiterleitung und zugehörige Tests | Contracts einschließlich P1, Lint/Build/Identität/Größe; repräsentativer nativer Today-Fall auf API 35 mit Animationen an | signierter Kandidat plus aktueller In-place-Upgrade-Smoke |
| Vollständig | Datenmodell, Speicherung, Migrationen, Plattform/UI-Lifecycle, neue Abhängigkeiten, Manifest, Build, Signierung, CI/Freigabe; unbekannte oder gemischte Änderungen | vollständige bestehende Prüfung | aktueller Upgrade-Smoke plus fünf historische Upgrade-Lanes |

API 35 bezeichnet die bestehende CI-Referenz, nicht die Behauptung der neuesten Android-Version.
API 26/35 bleiben im schnellen Today-Regressionsnachweis aus P1 enthalten. API 26/35/37 und der
historische Korpus werden bei vollständigen Änderungen weiter verpflichtend geprüft.

1. Die erste enge Positivliste auf `TodayCoordinator.java`, `TodayCommandDispatcher.java` und
   direkt zugehörige Hosttests beschränken. Vor Aufnahme die plattformfreie Abhängigkeit und
   P1-Abdeckung belegen. Nicht pauschal `today-core/` freigeben. Neue Produktionsdateien, Änderungen an
   weiteren Verträgen oder nicht klassifizierbare Diffs fallen auf das vollständige Profil zurück.
   Git-Umbenennungen, Löschungen und gemischte Änderungen dürfen keine Lücke erzeugen.
2. Das Profil muss im PR sichtbar seine Auswahl und Gründe ausgeben. Ein Fehler des Classifiers
   darf niemals „keine Prüfungen erforderlich“ bedeuten. Kein beliebiges PR-Label schaltet Gates ab.
3. Für jeden Produktkandidaten einen signierten Update-Smoke vom letzten passenden veröffentlichten
   Produktionsstand festlegen. Die Quelle wird bei Releaseplanung über Tag, Commit, Version,
   Metadaten und Hash unveränderlich gebunden, nicht erst später über einen beweglichen Latest-Link.
4. Dieser Smoke muss auch gleiche Datenbankschemata erlauben: 27→27 bei höherer App-Version ist ein
   gültiges Update. Die heutige historische Probe verlangt dagegen ein höheres Zielschema.
   Den neuen Smoke-Vertrag ausdrücklich von der historischen Migrationsprüfung unterscheiden.
   Testdaten enthalten Aufgaben, Today-Platzierung und Historie; nur isolierte Emulatorinstallation säen.
5. Bei Migration-/Speicher-/Toolchain-Risiko oder fehlender sicherer Einordnung zusätzlich den
   vorhandenen historischen Korpus auf demselben signierten Kandidaten ausführen. Fehlende aktuelle
   Smoke-Quelle blockiert Veröffentlichung, statt diesen Nachweis stillschweigend wegzulassen.
6. PR-Gate, Main-Wiederverwendung und Publish müssen dieselbe Profilentscheidung verwenden.
   Wiederverwendung erfordert passenden Commit-Inhalt, Profil-/Policy-Version und vollständige
   Nachweise der ausgewählten Jobs. Fehlende/mehrdeutige Nachweise werden nicht als Erfolg behandelt.
7. Die geltenden Freigabedokumente gemeinsam aktualisieren. Unter dokumentierten Vollprüfungen
   bleibt auch eine bewusst ausgelöste vollständige Verifikation weiterhin möglich.
8. Den lokalen Vorabcheck am selben Profil ausrichten: im Today-Profil schnelle Contracts und
   P1-Regressionsnachweise vor dem PR; der vollständige Build und native Nachweis erfolgen im
   obligatorischen CI-Gate. `check-all.sh` bleibt der vollständige lokale Check für vollständige
   Änderungen oder eine ausdrückliche Anforderung. Eine verkürzte CI-Auswahl darf nicht gleichzeitig
   durch einen unverändert pauschal vorgeschriebenen lokalen Vollbuild wirkungslos werden.
   Lokale und CI-Ergebnisse weiter getrennt ausweisen; keinen nicht ausgeführten lokalen Build
   als bestanden melden. Die vorgeschlagene Änderung dieser lokalen Abschlussregel gehört
   ausdrücklich zur Annahme des neuen Prüfvertrags, nicht zur bereits erteilten früheren
   Anforderung eines vollständigen Checks für die abgeschlossene Absturzreparatur.

Abnahme:

- Automatisierte Klassifikationsfälle: reine Dokumentation, Hosttest, exakter Titel-Fix,
  Today plus Migration, Widget/Receiver, Manifest/SDK, neue Datei, Umbenennung/Löschung,
  CI-/Policy-Änderung und fehlende Diffgrundlage.
- Historische Diffs PR 359/360 erhalten vollständige Migrationstests; PR 362 wird durch das
  Today-Profil einschließlich P1 abgedeckt. PR 361 mit persistenter Kennungsvergabe bleibt vollständig.
- Das Today-Profil erkennt die Gegenproben aus P1. Negative Freigabetests beweisen, dass ein
  fehlender oder roter ausgewählter Job weder durch skipped noch durch fremde PR-Belege ersetzt wird.
- Profilwechsel wird anhand Testumfang, Job-/Gesamtdauer und abgedeckten Risiken bewertet; keine
  feste Zeitgarantie und keine Addition paralleler Laufzeiten. Vorhandene Belege nutzen, keine
  pauschalen Mehrfachläufe bloß zur Erzeugung einer besseren Durchschnittszahl.
- P2 selbst besteht den vollständigen Gate. Erst der geprüfte Merge aktiviert den neuen Vertrag.

## P3 – Diagnosezustand und Android-Einstiegspunkte absichern

Branch: `codex/diagnostic-lifecycle-contract`.

Betroffen: `UpgradeProbeInstrumentation`, `AutoSecretaryApplication`/Bootstrap und unmittelbar
erreichbare Receiver, Widget-/Timer-Einstiege sowie die tatsächlich zusammengeführte Manifest-
und Provider-Initialisierung. Keine bloße Null-Prüfung im TimerBootReceiver als Gesamtlösung.

1. An einer isolierten Installation nachweisen, wann Diagnoseargumente, Application, Provider,
   Receiver und mögliche Hintergrundinitialisierer tatsächlich laufen. Der Schutz muss vor dem
   ersten potenziell schreibenden oder migrierenden Einstieg greifen. Das vorhandene Testmodell,
   das nur `Application.onCreate` zählt, genügt dafür nicht.
2. Einen expliziten, nur für den vorgesehenen Diagnosezugang aktivierbaren Bootstrap-Vertrag
   festlegen. Diagnose startet weder Room-Migrationen noch reguläre Widget-/Timer-Schreibarbeit.
   Falls der Schutz mit dem vorhandenen Bootstrap nicht rechtzeitig erreichbar ist, wird diese
   Phase vor Produktänderungen neu abgegrenzt; keine unterstellte Garantie dokumentieren.
3. Relevante Ereignisse während Diagnose definiert behandeln: keine Abstürze, keine versteckten
   Datenänderungen, keine nachträgliche automatische Wiederholung fachlicher Benutzeraktionen.
   Timer-/Systemzustand wird beim folgenden Normalstart regulär abgeglichen, sodass fällige
   Hintergrundarbeit nicht durch ein dauerhaftes Deaktivieren verloren geht.
4. Unterstützte Kombinationen aus Runner und App-Version ausdrücklich benennen. Ein Vertrag in
   einer neuen App gilt nicht automatisch für alte APKs. Nicht unterstützte Kombinationen werden
   vor der Diagnose erkennbar abgewiesen; keine Neuinstallation oder Downgrade als Ausweg.
5. Keine dauerhaften Geräteeinstellungen oder deaktivierten Komponenten als Diagnosevoraussetzung.

Abnahme:

- Kontrolliert zugestellte Boot-/Paketwechsel-/Timer-/Widget-Ereignisse stören die Diagnose nicht.
- Schema und Nutzdaten bleiben bei Diagnose unverändert; keine Migration oder fachliche
  Schreibarbeit wurde gestartet. Prüfung umfasst synthetische Historie, Platzierung, Belohnungen
  und Archiv, nicht nur den Rückgabewert einer OPEN_READONLY-Verbindung.
- Nach Diagnose einschließlich Abbruch ist ein Normalstart möglich; Timerabgleich funktioniert,
  ohne fachliche Aktionen doppelt auszuführen. Zurückgestellte Systemarbeit ist berücksichtigt.
- Native Nachweise auf unterstützten Plattformen nach vollständigem Profil; historische
  Seed-/Verify-Phasen der signierten Upgrade-Probe bleiben funktionsfähig.
- Auf dem Pixel ausschließlich unterstützte lesende Diagnose, danach Helper entfernen und
  Normalstart prüfen. Geräteprüfung bleibt bei fehlendem Zugang ausdrücklich offen.

## P4 – Ursprung der Datenanomalien begrenzt untersuchen

Branch: `codex/recovery-origin-investigation`.

1. Die drei beobachteten FK-Kategorien und die rückständige Sequenz separat verfolgen.
   `sourceSchema=24` im Archiv bezeichnet den Sicherungspunkt und beweist nicht die Entstehungsversion.
2. Direkt einschlägige historische Tabellenumbauten und Schreib-/Löschpfade prüfen: Ablauf-/Aufgaben-
   Löschung, Snapshot-Ersetzung, Abbruch/Abschluss eines Ablaufs und Sequenzvergabe. Vorhandene
   historische Fixtures und bekannte Zwischenmigrationen verwenden; keine Repository-Gesamtsuche
   nach hypothetischen Defekten als Ersatz für eine kausale Untersuchung.
3. Verdächtige Pfade mit unterscheidbaren synthetischen Daten und realen Fremdschlüsseleinstellungen
   reproduzieren. Bekannte physische Schemahistorie berücksichtigen. Nach jedem relevanten Vorgang
   Beziehungen sowie Zusammenhang von Zähler, Historie und eindeutigen Schlüsseln prüfen.
4. Ergebnis pro Anomalie: reproduzierter Erzeuger mit Version/Vorgang und Test; oder untersuchte
   Pfade ohne Reproduktion plus exakt fehlende Belege. Nicht auflösbare persönliche Historie
   bleibt unbekannt; keine Behauptung „kann nicht wieder auftreten“ daraus ableiten.

Abnahme: abgegrenzter Untersuchungsbericht und reproduzierende Tests, soweit ein Erzeuger
gefunden wurde. Ein begründeter Negativbefund schließt die Untersuchung, nicht die unbekannte
historische Ursache. Er verändert den Status bestehender Schutzmechanismen nicht.

Bedingte Folgephase P4b: Nur bei belegtem, noch nicht beseitigtem Erzeuger wird vor Änderungen
ein eigener Ursachenfix mit Branch, Umfang, Reproduktion, Erhaltungsnachweis und Gate festgelegt.
Ein lokaler Fehler erhält einen lokalen Fix; ein Vertragsfehler die notwendige Vertragskorrektur.
Das ist keine pauschale Freigabe für neue Migrationen oder einen Architekturumbau. Neue
Produkt-/Datenentscheidungen werden vor der entsprechenden Umsetzung vorgelegt.

## P5 – Abschluss, Dokumentation und Geräteabnahme

Branch bei abschließenden Dokumentationsänderungen: `codex/remediation-acceptance`.

1. Gegen diese Roadmap und die getrennten Phasenpläne auditieren. R1–R4 jeweils mit aktuellem
   Commit und passendem Beleg schließen; U1/P4b separat und wahrheitsgemäß einstufen.
2. Testbeschreibungen nennen ihre tatsächlichen Grenzen. Aktuelle Zusammenfassungen enthalten
   keine überholten „läuft noch“-Angaben; historische Fehlversuche bleiben als Historie erkennbar.
3. Die für den letzten Produktstand geltenden Gates nachweisen. Bereits passende grüne Nachweise
   nicht allein für eine weitere Abschlussmeldung wiederholen. Nur neue Änderungen oder offene
   Risiken begründen zusätzliche Läufe.
4. Auf dem entsperrten Pixel: installierte Version und Herkunft, Kaltstart, frisches Prozessprotokoll,
   vorhandene Aufgaben/Abläufe, Titel-Klick, „Später“ und Persistenz nach Neustart prüfen. Keine
   synthetischen Testdaten in die produktive Datenbank säen und keine Erledigungen als Klicktest buchen.
5. Schlussbericht trennt integrierte Änderungen, automatische Nachweise, veröffentlichtes Update,
   Geräteabnahme und verbleibende Unbekannte. Bei gesperrtem/nicht verfügbarem Pixel bleibt P5
   mit genau diesem Abnahmenachweis offen; grünes CI ersetzt ihn nicht.

Gesamtabnahme: Die planbaren Korrekturen sind auf Remote-Main geprüft integriert, der geltende
Freigabevertrag ist konsistent umgesetzt, der Diagnoseumfang ist tatsächlich getestet und die
sichtbare Geräteabnahme ist belegt. Eine ungeklärte ursprüngliche Datenhistorie wird auch dann
ausdrücklich ausgewiesen und nicht als repariert ausgegeben.
