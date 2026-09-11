# Abnahme: Kachel-Editor und parallele Abläufe

## Umfang und Referenz

Freigegeben ist der Kachelentwurf gemäß ADR-036: Schrittdialog nur mit Name,
Wartewert/Einheit und direkter Zeitabfrage-Checkbox; Kapazitäten auf Blatt 2.
Die vorhandenen mobilen Farben und Blattflächen bleiben erhalten. Die Referenz
`docs/reference/flow-editor/approved-tiles.html` ist kein Migrationsnachweis.

## Anforderungsabgleich

| Vertrag | Konkrete Nachweise |
|---|---|
| T01 – minimaler Dialog | `FlowTileEditorRenderTest`, `FlowTileEditorInstrumentationTest.stepDialogHasOnlyNameWaitAndTheDirectCheckboxAtLargeFont`; drei Editor-Goldens |
| T02 – Struktur und Identitäten | `FlowTileGraphTest`, `FlowTileLayoutTest`, `FlowTileInteractionTest`; native Touch-/Maus-/Abbruch-/Join-/Undo-Reisen einschließlich erneutem Drag nach Undo |
| T03 – Geräte und Zugänglichkeit | Native `FlowTileEditorInstrumentationTest` auf API 26/35/37; semantische Alternative zusätzlich auf 26/35; Kantenscrollen, große Schrift und 320-dp-Goldens |
| T04 – alternative Starts und Parallelität | `FlowGraphExecutionTest`: nur erreichbarer Snapshot, ungleich lange Zweige, ausdrücklicher Join, unabhängiges Kapazitätswarten; echte Room-Projektion in `GraphFlowRuntimePersistenceTest` |
| T05 – Ressourcen | `FlowGraphCommandsTest`: konkurrierende Starts, eine Maschine, drei Trockenplätze, transaktionale Reservierungen und Reduktion; echter Datenbankfall `laundryReservesThreeDryingPlacesButOnlyOneMachine` |
| T06 – Zeiten und Wiederherstellung | Zeitabfrage auch am letzten Schritt, ausgewählte Wartephase verlängern, Snapshot unverändert, erneute Befehle; `FlowGraphExecutionTest`, `GraphFlowRuntimePersistenceTest`, `FlowEditorPersistenceViewModelTest` |
| T07 – gemeinsames Blatt | `StepFlowRuntimeRobolectricTest.deferMovesOnlySheetBehindNormalWork`; keine unbegonnenen Runs/Geisterblätter, ausdrückliche Folgeaktionen und zweiter Kreis; `GraphFlowSheetGoldenRobolectricTest`, `FlowRunsComposeGoldenRobolectricTest` |
| T08 – Tau | Gleichzeitige letzte Aktionen und Doppelklick, einmalige Abschlussbuchung, Einsammeln nach letzter Wartephase, bereits ausgezahlte Beträge und Rollback: `FlowGraphCommandsTest`, `FlowGraphExecutionTest`, `GraphFlowRuntimePersistenceTest` |
| T09 – Datenübernahme | Schema 25; `FlowGraphMigrationTest`, `FlowGraphSqlMigrationTest`, `DatabaseMigrationRobolectricTest`, vollständiger Quellversions-/Upgrade-Korpus; signierte Produktions-Upgrades als separates Release-Gate |
| T10 – ein Editor und atomarer Entwurf | `FlowTaskKindTest`, `FlowGraphEditorPersistenceTest`, `FlowEditorPersistenceViewModelTest`; echte Erstellen-/Speichern-/Wiederöffnen-/Recreation-/Verwerfen-Reise in `FlowProductEditorInstrumentationTest` |

API 37 kann Espressos ältere InputManager-Brücke nicht ausführen. Derselbe
Join-/Bestätigungs-/Undo-Vertrag wird dort über native Accessibility geprüft,
ebenso auf 26/35. Kein fachlicher Prüffall entfällt. Die Gestenprüfungen warten
auf Fensterfokus und stabile Darstellung; fehlgeschlagene Gesten werden nicht wiederholt.

## Getrennte Auslieferungsnachweise

- Produkt-PR: #356, Kopf `0c9196a9965d562ad170f920bb6426de53141617`.
- Lokales Gesamtgate auf exakt diesem Kopf: grün, 24 s inkrementell;
  vorheriger vollständiger Lauf auf `c97403d8`: 16 min 35 s,
  746 Tests ohne Fehler, ein bestehender Skip. Letzte Änderungen nur an
  Teststeuerung/Dokumentation, Produktcode seit `5780d8e7` unverändert.
- Lokales isoliertes API-26-Gerät: neun Editorfälle bei Animation 1.0,
  letzte Testfassung mit jeweils sieben nativen Fällen bei 1.0 (1 min 38 s),
  0.5 (2 min 34 s) und 0.0 (1 min 32 s) grün. Keine Produktionsinstallation.
- Finale PR-Matrix einschließlich Geräte-/Animationsjobs API 26/35/37 und
  Abschlussgates: [34615684499](https://github.com/ThonkTank/AI-Secretary/actions/runs/34615684499), grün.
- Squash [PR #356](https://github.com/ThonkTank/AI-Secretary/pull/356) am
  2026-09-11 um 15:42:33 UTC: `06cd96b8bd322945a4215d04afb1ad0ee3b059f9`.
  Der Main-Baum und der geprüfte PR-Baum sind identisch:
  `ecdf80794eb6cafc36025b1ca4a00efd0fa67f87`.
- Exakter Main-Lauf [34617732999](https://github.com/ThonkTank/AI-Secretary/actions/runs/34617732999):
  vollständig grün, einschließlich Verträgen, Goldens, Build, allen sechs Geräte-/Animationsprüfungen,
  signiertem Paket, fünf signierten Upgrades und Veröffentlichung. GitHub lieferte nach dem Merge
  keine Commit-/PR-Zuordnung; der vorhandene Fail-closed-Vertrag führt deshalb
  die vollständigen Main-Prüfungen aus, statt die PR-Prüfung wiederzuverwenden.
- Geprüfter Kandidat: 0.2.167 / 1016701, `de.thonktank.autosecretary`,
  2.921.024 Bytes; SHA-256 `dd71f350b4b24effff93751e7292337d2e3f040c5e78e1f86b848d17424bfee2`.
  Produktionszertifikat SHA-256
  `de45d94c9724beeaa2e0dff31f69f53bb0f4c9ba79a5aa419d1f29d18f4d91da`.
  Paketmanifest, Signatur und Release-Metadaten separat lokal bestätigt.
  [0.2.167](https://github.com/ThonkTank/AI-Secretary/releases/tag/forest-android-1016701)
  wurde am 2026-09-11 um 16:09:01 UTC veröffentlicht, nicht als Draft/Prerelease,
  und ist die aktuelle Stable-Version. Der Release-Tag zeigt exakt auf den
  Produkt-Main-Commit `06cd96b8bd322945a4215d04afb1ad0ee3b059f9`.
  Erneut aus der öffentlichen Veröffentlichung heruntergeladen: Release-Validierung
  bestanden, SHA-256 identisch und binär identisch mit dem Upgrade-geprüften Kandidaten.
- Physisches Gerät: bei der Abschlusskontrolle nicht verbunden; weder ADB-Gerät
  noch vorhandener drahtloser Debug-Dienst gefunden. Installierte Version und
  Laufzeitabnahme bleiben offen. Keine App-Daten gelöscht, keine Neuinstallation,
  kein Downgrade und keine Produktionsdaten über einen Testhost verändert.

Die signierten Proben übernehmen Schema 8 auf API 26/35/37 sowie die
Schema-20-Ablauf- und Schema-23-Reparaturgrenze auf API 26 bis Schema 25.
Die lokale Room-Prüfung ergänzt sämtliche deklarierten Quellversionen.

Diese technischen Nachweise ersetzen keine Prüfung der tatsächlich installierten
Produktionsversion. Ohne verbundenes Gerät wird Phase A nicht als abgeschlossen markiert.
