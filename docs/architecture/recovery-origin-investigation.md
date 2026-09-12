# Herkunft der Recovery-Anomalien: P4-Untersuchung

Untersuchter Produktstand: `3c0a873efa0eb553bcf670b778443d17659422fa`, Version0.2.174,
Schema27. Zugehörige [Roadmap](recovery-remediation-roadmap.md) und
[Ausführungsprotokoll](recovery-remediation-execution.md). Stand: 2026-09-12.

## Ergebnis und Grenze

| Anomalie | Ergebnis | Konsequenz |
|---|---|---|
| Schritte ohne Ablauf (`flow_run_steps`) | Durch aktuelle Migration22→23 nativ reproduziert | Lokaler Ursachenfix in P4b erforderlich |
| Ressourcen ohne Ablauf (`flow_run_resources`) | Derselbe reproduzierte Vorgang | Gemeinsamer Fix; kein zweites Architekturprojekt |
| Schritte ohne Vorkommen (`occurrence_steps`) | Derselbe reproduzierte Vorgang | Dieselbe Löschabhängigkeit berücksichtigen |
| Rückständige Ausführungssequenz | Kein Erzeuger in den abgegrenzten untersuchten Pfaden nachgewiesen | Bestehende robuste Vergabe erhalten; ursprüngliche Ursache bleibt unbekannt |

Der erste Befund ist ein reproduzierbarer Erzeuger, keine Rekonstruktion der persönlichen
Gerätehistorie. Die archivierte Herkunft `sourceSchema=24` bezeichnet den Sicherungspunkt.
Sie beweist weder, dass die Zeilen erst in Version24 beschädigt wurden, noch welche früheren
App-Versionen auf dem Pixel tatsächlich diesen Bestand verarbeitet haben.

## Reproduzierter Vorgang

`DatabaseMigrations.MIGRATION_22_23` wandelt ungestartete Ablaufangebote in Kandidaten um.
Die Auswahl `_clean_flow_candidates` enthält PENDING_START-Abläufe an Position0 ohne aktive
Ressourcen, erledigte angebotene Schritte oder Reward-Buchungen. Zugehörige Snapshots können
dennoch existieren: geplante Ressourcen, Ablauf-Schritte und unerledigte Angebotsschritte.

Nach Anlage des Kandidaten löscht die Migration dessen `occurrences` und `step_flow_runs`.
Sie löscht die Kindtabellen nicht explizit. Der anschließende Tabellenumbau kopiert die
verbliebenen Kindzeilen. Bei ausgeschalteten Fremdschlüsseln erfolgen keine Löschkaskaden.

Der native Test `RecoveryOriginInstrumentationTest` misst genau diesen Zustand innerhalb
des echten Room-Upgrades: `PRAGMA foreign_keys=0`, Upgrade-Transaktion aktiv, vor22→23 keine
FK-Verletzungen, danach genau je eine Verletzung der drei genannten Tabellen. Nach erfolgreichem
Room-Öffnen ist `foreign_keys=1`. Room2.8.4 aktiviert diese Prüfung erst in seinem generierten
`AppDatabase_Impl.onOpen`; die vorhandene `DatabaseFactory` aktiviert sie nicht zuvor.
Eine eingeschaltete Prüfung beim gesunden Fixture-Aufbau wirkt nicht auf diese später neu
geöffnete Verbindung. Der Test verändert den FK-Modus der Upgrade-Verbindung ausdrücklich nicht.

Ein früherer isolierter Host-SQL-Versuch mit denselben Migrationsanweisungen unterschied beide
Modi: FK=0 erzeugte die drei Kategorien, FK=1 entfernte die Kinder per Kaskade. Dieser Versuch
war nur eine Hypothesenprüfung. Der native Room-Test liefert den tatsächlichen Android-Nachweis.

Die Löschlogik wurde mit [PR335](https://github.com/ThonkTank/AI-Secretary/pull/335), Commit
`f6e72e51515c8e3f9024b834616c8ec5cd634c19`, eingeführt. Die erste reguläre veröffentlichte
Version, die diesen Commit enthält, ist [0.2.158](https://github.com/ThonkTank/AI-Secretary/releases/tag/forest-android-1015801),
Tag`forest-android-1015801`, Commit`a25add10fc833f362d01ee16781821c4b4df8d56`, Schema23,
veröffentlicht2026-09-07T15:38:33Z. Auch diese Version verwendet Room2.8.4. Der native Test führt
den heutigen Migrationscode aus; er ist kein erneuter Installationsversuch mit jener historischen APK.

Die bereits reparierte Vertauschung physischer Spalten bei einem früheren Tabellenumbau ist
ein anderer Fehler. Die neuen Tests vergleichen alle ursprünglichen Spaltenwerte einschließlich
Notiz, Verzögerung und Belastungsfeldern vor und nach22→23; die Waisen entstehen auch bei korrekter
Zuordnung dieser Werte.

## Umfang der nativen Beweise

Datei: `app/src/androidTest/java/de/thonktank/autosecretary/RecoveryOriginInstrumentationTest.java`.

- Zwei gesunde Ausgangsbestände: exportiertes Schema22 sowie Schema20 mit echten Zwischen-
  migrationen. Der Test prüft die tatsächlich unterschiedliche physische Spaltenreihenfolge.
- Je ein ungestarteter, gestarteter und wartender Ablauf; nichtleere Kindtabellen; abgeschlossener
  Verlauf, offene Ausführung, Reward-Buchung und Zuordnung. WAITING_TIME hat einen abgeschlossenen
  Vorgänger; alle Ressourcenpositionen liegen innerhalb ihres Ablaufs.
- MigrationTestHelper erstellt ausschließlich die alte Datei. Das Upgrade selbst läuft über
  `Room.databaseBuilder(..., AppDatabase.class, ...)` und den Produktionsmigrationspfad ab8.
  Ein beobachtender Wrapper um22→23 delegiert die unveränderte produktive Migration.
- Prüfung der drei Anomalien unmittelbar nach22→23, bevor die Schutzmigration24→25 sie versteckt.
- Nach Öffnung auf27: genau drei Archivzeilen mit richtigen IDs, Herkunft24 und sämtlichen
  ursprünglichen Spaltenwerten samt SQLite-Typ; keine FK-Verletzungen. Gültige Abläufe, Kandidat,
  Fortschritt, Wartezeit, aktive Ressource, Sequenzen, offene/abgeschlossene Vorkommen und deren
  Schritte bleiben erhalten. Reward-Buchung und Zuordnung sind vollständig unverändert.
- Ausschließlich eigener Emulator und isoliertes Testpaket; eigener Datenbankname. Keine
  persönlichen Gerätezeilen, Produktionsinstallation oder produktive Datenbank beteiligt.

Lokaler nativer API26-Lauf: beide Tests erfolgreich, 4.256s reine Instrumentierungszeit.
Getrennte Rohbelege: `/tmp/p4-api26-native-results.txt` und `/tmp/p4-api26-origin-logcat.txt`.
Der reguläre AndroidJUnit-Korpus wählt die Klasse automatisch aus; weitere Plattformnachweise
werden mit dem tatsächlichen PR-Lauf im Ausführungsprotokoll ergänzt.

## Sequenzzähler: begrenzter Negativbefund

Untersuchter historischer Modell-/Repository-Stand: Schema20-Releasequelle
`e566a078d4edf8bda053c0257c6d9861db647ea8` (0.2.137). Nicht mit dem heutigen Runtime-Modell mischen.

| Betrachteter Pfad | Beobachtung | Aussagegrenze |
|---|---|---|
| Neues Angebot und Carry-forward | FlowRuntimeCoordinator übergibt den gelesenen aktuellen Zähler an `offerOnSheet`, das um1 erhöht | Keine vollständige Untersuchung aller Aufrufer in allen Versionen |
| Zustandswechsel, Abbruch, Rewind, bestehendes Angebot, Clear/Set-Sheet, Reorder | Originaler StepFlowRun übernimmt den bisherigen Zähler | Ein Modelltest deckt keine Datenbank-Interleavings ab |
| Transaktionen | activateReady, Abbruch und Completion/Harvest verwenden Repository-Transaktionen; RoomTaskRepository delegiert an `database.runInTransaction` | Kein Nachweis einer bestimmten früheren Prozessunterbrechung oder Gerätehistorie |
| Snapshot-Anlage/-Update | Parent und Kinder werden transaktional angelegt; Parent-Einfügen IGNORE, Update mit `@Update`, kein Parent-REPLACE | Andere Tabellen verwenden teils REPLACE; daraus folgt kein belegter Zählerreset |
| Aufgabenlöschung | DELETE des Aufgabenelternteils, vorgesehene FK-Kaskaden im Normalbetrieb | Nicht mit der gemessenen Migration bei FK=0 gleichsetzen |
| Mapper, Migration22→23 und24→25 | Zähler wird übernommen beziehungsweise explizit umbenannt | Korrekte Übernahme repariert keinen schon rückständigen Quellwert |
| Heutiger FlowGraphSnapshotWriter.update | Aktualisiert Fortschritt/Ressourcen und paid/collected/time; setzt den Ausführungszähler nicht zurück | Schließt nicht sämtliche anderen früheren Fehler aus |

Zusätzliche ausgeführte Modellprobe: unveränderte historische Quellen StepFlowRun,
StepFlowRunState, TaskId, TaskSlot mit JDK21 kompiliert. Für Zähler0/1/7/100 wurden jeweils die
sieben erhaltenden Übergänge und ein neues Angebot geprüft: 32 Übergänge erfolgreich. Quellen,
kleiner Probe-Aufrufer und Ergebnis liegen unter `/tmp/p4-historical-counter-model/`.
Die öffentliche Methode nimmt eine Sequenz entgegen; ein hypothetischer fehlerhafter Aufrufer
könnte einen älteren Wert übergeben. In den betrachteten produktiven Aufrufen ist das nicht belegt.

Es fehlt eine Vorgangsfolge oder ein Zwischenbestand, der das erste Auseinanderlaufen von
Zähler und vorhandener Historie zeigt. Deshalb lautet das Ergebnis „Erzeuger unbekannt“,
nicht „Zählerfehler ausgeschlossen“. Der bereits integrierte Schutz gegen belegte Schlüssel
und rückständige Zähler bleibt erforderlich und wird hier nicht abgeschwächt.

## Konsequenz für P4b

Die gemeinsame Waisenerzeugung benötigt eine lokale Korrektur der Migration22→23 (D1).
Eine globale Umstellung von Room, SQLite, Datenmodell oder UI ist dafür nicht nachgewiesen nötig.
Ein bloßes Einschalten von FK innerhalb der laufenden SQLite-Transaktion ist kein verlässlicher
Fix. Nur die spätere Archivierung beizubehalten lässt den nachgewiesenen Erzeuger bestehen.

Vor Umsetzung wird ein eigener P4b-Plan mit expliziter Kindbehandlung vor Elternlöschung,
gesunden Mischfällen, Erhaltungs-/Rollbacknachweis und vollständigem Migrations-/Releasegate
festgehalten. Neue Produkt-/Datenentscheidungen sind damit nicht stillschweigend getroffen.
P4 selbst ändert ausschließlich Tests und Dokumentation; Produktreparatur und Pixel-Abnahme
bleiben getrennte Abschlüsse.
