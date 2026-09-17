# Ablaufblatt: Remediation-Ausführung

Referenz: [Roadmap](flow-remediation-roadmap.md). Beginn 2026-09-17;
Remote-main `9b0d3dc7`. Isolierter Worktree; alter Frontend-Checkout unverändert.
Die Abschnitte halten den Prüfverlauf mit Zwischenständen und Phasenabschlüssen fest.

| Phase | Status |
|---|---|
| P0 Prüfablauf | Erfüllt; wird in den Folgephasen weiter angewendet |
| P1 Wartezeitbedienung | Abgeschlossen: PR #375, Remote-main 83750722 |
| P2 Ablaufende | Abgeschlossen: PR #377, Remote-main cd7c51df |
| P3 Animation/Modell | Abgeschlossen: PR #378, Remote-main 5e03b00a |
| P4 Handy | Offen: kein physisches Gerät erreichbar |

## P1 – Umfang und Prüfweg

Vorliegender Chatplan: bestehende FlowChainStripView-Prüfung um Bestätigung,
Mehrfachauswahl, identische Titel, Abbruch und Zugriff bei Einsammelbereitschaft
ergänzen. SQLite über die produktive ApplicationUseCaseComposition verwenden;
native Langdruck- und Accessibility-Prüfung in TodayInteractionInstrumentationTest.
Kein erwarteter Produktverhaltenswechsel. Laufende Pulsanimation bleibt im nativen
Test eingeschaltet; harte Ergebnis-Zeitgrenzen ersetzen globale Ruheprüfungen.

Prüfung: gezielt FlowChainStripViewTest; anschließend check-all.sh und die vom
Repository verlangte PR-Matrix. Einzelbefehle und SQLite-Wirkung werden gemeinsam
geprüft, native Bedienung separat. Testdaten bleiben ausschließlich im isolierten
Testpaket bzw. einer In-Memory-Datenbank. Abschluss gemäß Roadmap und AGENTS.md.

P1-Korrekturrunde: Der erste gezielte Lauf hatte 3/6 Fehler: Robolectric hatte den
asynchronen Dialog-onShow-Callback noch nicht ausgeführt, deshalb war der produktive
Bestätigungslistener noch nicht installiert. Der Test verarbeitet nun die bereits
fälligen Main-Looper-Ereignisse am unveränderten virtuellen Zeitpunkt vor Bestätigung.
Kein Warten auf zukünftige Animationsruhe; keine Produktänderung oder schwächere
Datenbankassertion. Abbruch prüft zusätzlich den geschlossenen Dialog.

Lokale Geräteinfrastruktur: API-35-Emulator beim Boot mit SIGSEGV beendet, bevor ein
App-Test lief. Lokale native Prüfung deshalb auf separatem API-26-Emulator;
API 35/37 bleiben über die erforderliche PR-Matrix abzunehmen.

P1 gezielter Nachweis: `:app:testInstrumentationUnitTest --tests '*FlowChainStripViewTest'`
mit sechs Tests, null Fehlern. Datenbank- und Identitätsassertionen sowie benannte
Accessibility-Aktion bestehen. Zu diesem Zwischenstand waren native APKs, vollständiger lokaler Lauf und
PR-Matrix noch ausstehend. Roadmap-Abgleich des Patches: alle P1-Szenarien
abgedeckt; keine produktive Buchungs-/Schemaänderung.

P1 zweite Korrekturrunde: Der Workflow-Vertrag lehnt Polling in kritischen nativen
Interaktionen ab. Der neue Dialogzugriff wartete zunächst durch begrenztes Polling
auf den Accessibility-Baum. Er verwendet nun UiAutomation.executeAndWaitForEvent
mit harter Zeitgrenze; Aktion und beobachtbares Accessibility-Ereignis sind gekoppelt.
Der Vertrag bleibt unverändert. Auch der lokale API-26-Emulator endete beim Boot
mit SIGSEGV, ohne App-Test. Native Abnahme erfolgt deshalb über die CI-Emulatoren;
kein weiterer lokaler Emulator-Neustart und kein behaupteter lokaler Geräteerfolg.


## P1 – Abschluss

PR #375, geprüfter Head `744b248e`, Squash-Merge auf Remote-main `83750722`.
CI-Lauf `35203753184`: alle erforderlichen Checks einschließlich API 26/35/37 jeweils
normal und mit Animationen grün. Vollständiges `scripts/ci/check-all.sh`: erfolgreich,
793 App-Tests, null Fehler, ein optionaler Benchmark ausgelassen; Lint, APKs,
Identität und Größenbudgets bestanden. Der lokale Kaltlauf benötigte 32m21s im
Gradle-Teil; kein zusätzlicher vollständiger Wiederholungslauf nach Erfolg.

## P2 – Plan

Basis ist P1 auf Remote-main. TodayViewModel erhält seine Flows und den vorhandenen
Scheduler im gemeinsamen Konstruktor vor Beginn der ersten Projektion. Der
Produktionskonstruktor verwendet denselben Weg; die Tests können dort eine
kontrollierte Uhr und die echte ClockInvalidationSource verbinden.

Integration mit realer Room-Datenbank, produktiven UseCases, DashboardPresenter,
TodayViewModel und Renderer: verborgenes Blatt bei Fälligkeit, Verlängern/Verkürzen,
Hintergrund/Wiederaufnahme, abschließende Wartephase und idempotentes Einsammeln.
Zeitablauf darf keinen Testaufruf von activateReady oder manuellen Refresh benötigen.
Native MainActivity-Prüfung ergänzt den echten Lifecycle und die sichtbare Darstellung.
Zuerst schnelle Workflow-Verträge und betroffene Tests, dann vollständiger lokaler
Abschlusslauf und erforderliche PR-Matrix. Keine neue Auszahlungsregel oder Migration.

P2 erste gezielte Prüfung: Domain-/ViewModel-Übergänge und Einsammeln funktionieren,
aber sechs Rendererassertionen finden die Schrittzeile nicht. Der Testhost hatte
noch keinen Layoutdurchlauf für das wieder eingeblendete Blatt. Korrekturplan:
den Renderer in einem gemessenen Scroll-Viewport prüfen und bei Fehlern den
Textbaum ausgeben; keine Abschwächung auf reine Callback-/Modellassertionen.

P1 Main-Nachweis nach Merge: Workflow `35206735960` für `83750722` erfolgreich.
P2 korrigierter gezielter Lauf: acht Integrationstests auf API 26/35 grün;
der gemessene Viewport zeigt die erwartete native Schrittzeile. Die native
Testklasse ist erfolgreich kompiliert. 16 schnelle Workflow-Verträge grün.
Native MainActivity-Szenarien prüfen Vordergrund-Fälligkeit, tatsächliches
Hintergrund/Resume und Activity-Recreation; Fixtures sind auf das Testpaket begrenzt.
Zu diesem Zwischenstand standen vollständiger lokaler Lauf und PR-Matrix noch aus.

## P2 – Abschluss

PR #377, geprüfter Head `98d2d071`, Squash-Merge auf Remote-main `cd7c51df`.
CI-Lauf `35207597564`: alle erforderlichen Checks einschließlich der sechs
normalen/animierten API-26/35/37-Lanes erfolgreich. Vollständiges check-all.sh
lokal erfolgreich (Gradle 30m06s): 801 App-Tests, null Fehler, ein optionaler
Benchmark ausgelassen; Lint, APKs, Identität und Größenbudgets bestanden.
Der langsame Lauf wurde anhand zweier Thread-Stichproben als fortschreitend
bestätigt: unterschiedliche Tests, anschließend aktive Lint-/R8-Arbeit.
Kein Abbruch oder Wiederholung ohne Diagnose.

## P3 – Plan

Basis `cd7c51df`, Branch `codex/flow-vessel-lifecycle`. Zuerst Regression auf dem
unveränderten Produktcode: angehängte, aber vollständig herausgescrollte Ketten,
gewöhnliche Tau-Container in einem übergeordneten ScrollView, ausgeblendete
Eltern und Fenster, Entfernen/Wiedereinfügen sowie echte wiederholte
Zeichenanforderungen im unsichtbaren Zustand. Zusätzlich Countdown,
reduzierte Bewegung und endliche Füllanimation. Kontrollierte Framework-
Layout-/Scrollsignale ersetzen im Robolectric-Viewport den nativen Traversal;
der Test ruft keine Sichtbarkeitsmethode des Containers selbst auf.

Korrektur an der gemeinsamen XpVesselView: Puls und Countdown an tatsächliche
Sichtbarkeit binden; globale Layout-/Scrollsignale und Fensterlebenszyklus
beobachten, beim Entfernen alle Listener und eigenen Rückrufe lösen.
Native Swipe-/Tap-Prüfung um abgeschnittene Container und tatsächlichen
Hintergrund/Vordergrund-Wechsel erweitern. FocusTaskUiModel.waits und den
ungenutzten Builder-Zugang nach Referenzprüfung entfernen. Keine Aussage
über gemessenen Akkugewinn und keine Datenbankänderung.

P2 Main-/Release-Nachweis: Workflow `35210277442` für `cd7c51df` vollständig
erfolgreich einschließlich signiertem aktuellem Upgrade und historischen
Upgrade-Lanes. Veröffentlichung `forest-android-1017801` / Version 0.2.178.

P3 Prüfaufbaukorrekturen: Der erste Testentwurf konnte den überladenen
Shadows.shadowOf-Aufruf gegen das aktuelle Compile-SDK nicht auflösen;
Shadow.extract vermeidet den Verweis auf eine entfernte Android-Klasse.
Die erste Regression enthielt außerdem Testhostfehler: unvollständig vermessene
Fenster-Vorfahren und einen auf API 26/35 noch nicht öffentlichen Scroll-Dispatcher.
Der Test misst deshalb das Dekorfenster und liefert das Framework-Scrollsignal
über ReflectionHelpers. Diagnose eines weiteren Fehlers: attached=true,
shown=true und positive Geometrie, aber window=GONE. Das einfache Test-Activity
benötigt das App-Sichtbarkeitsereignis des WindowManagers über den tatsächlichen
ViewRoot; die produktive Sichtbarkeit wird nicht über einen Test-Override ersetzt.
Ein kontrollierter 16-ms-Frame-Takt erlaubt den Nachweis tatsächlicher
Zeichenanforderungen; vorheriges Leerlaufen der Simulation wäre hierfür ungeeignet.
Die positive Kontrolle verlangt zunächst Frames im sichtbaren Zustand.

Gezielte Prüfung nach diesen Testhostkorrekturen: Einhängen/Entfernen/Wiedereinfügen
und sichtbare/unsichtbare Zeichenanforderungen auf API 26/35 erfolgreich (vier Fälle).
Die korrigierte gesamte Regression wurde anschließend nochmals am unveränderten
Produktstand verglichen, bevor die endgültige Korrektur abgenommen wurde.

P3 eindeutiger negativer Nachweis auf dem ursprünglichen Produktcode: je API 26/35
scheitern Einhängezustand, ausgeblendete Eltern/Fenster, übergeordnetes Scrollen,
horizontales Clipping und fortlaufende Zeichenanforderungen unsichtbarer bereiter
Container (zehn Puls-Regressionsfälle). Für die Frame-Prüfung besteht zuerst die
positive Kontrolle des sichtbaren Containers. Der sichtbare Countdown benötigt
außerdem die Layoutnachführung beim ersten Einhängen. Die Füllanimation wird in
16-ms-Schritten simuliert: ein einzelner großer Sprung liefert bei pausierter
Vsync-Simulation nur einen Frame und ist kein Nachweis einer abgeschlossenen
endlichen Animation. Die Produktkorrektur wird danach unverändert wieder angewandt.


P3 kombinierter Prüfsatz erfolgreich: 14 Lifecycle-Fälle auf API 26/35,
sechs Wartezeitbedienfälle und acht Fälligkeits-Integrationsfälle, insgesamt
28 Tests ohne Fehler. Native Testklasse kompiliert, 16 Workflow-Verträge grün.

P3 PR #378, erster Head `a216ccf7`: lokale 815 App-Tests ohne Fehler, ein
optionaler Benchmark ausgelassen. Der anschließende Bau-/Lint-Teil wurde
bewusst beendet, als Remote-main durch PR #376 und #379 auf `22640bb5`
fortgeschritten war. Dies ist kein vollständiger lokaler Freigabenachweis.
Dieser freigegebene Stand wurde integriert und der vollständige lokale
Prüflauf für die Kombination neu gestartet.

CI-Lauf `35212501367`, Versuch 1: Qualität/Bildvergleiche/Bau und fünf
Android-Lanes grün. API 26 mit Animationen verlor nach 19 erfolgreichen
Tests während eines bestehenden FlowTileEditor-Tests das Gerät. Gesicherte
Artefakte zeigen einen neuen Android-Boot ab 10:58:48 UTC (init/vold/zygote),
`device offline` und einen unvollständigen Lauf: 19 von 39 Tests. Die neuen
Today-Tests waren noch nicht erreicht. Eine Ursache des gesamten Guest-Neustarts
ist aus diesen Artefakten nicht weiter ableitbar. Nach dieser Diagnose wurde
nur die betroffene Lane auf unverändertem Code einmal bewusst wiederholt
(Versuch 2); keine automatische Wiederholungsregel und keine Änderung an Tests
oder Prüfgates. Artefakt:
`animation-instrumentation-failure-api-26-b59f76b4d910cb061dbf4bd0097c9919c94b4e60-1`.


## P3 – Abschluss

PR #378 wurde mit dem geprüften integrierten Head `74b97cdd` als `5e03b00a`
auf Remote-main übernommen. Der vollständige lokale Lauf check-all.sh war
erfolgreich (Gradle 20m03s): 815 App-Tests ohne Fehler, ein optionaler Benchmark
ausgelassen; Lint, alle APKs, Identität und Größenbudgets bestanden.
CI-Lauf `35214000616` für diesen Head war im ersten Versuch vollständig grün,
einschließlich aller sechs normalen/animierten API-26/35/37-Lanes. Der frühere
gezielte Infrastruktur-Wiederholungslauf `35212501367`, Versuch 2, war ebenfalls
erfolgreich; er ersetzt nicht die endgültige Abnahme des integrierten Heads.
Der Git-Baum des geprüften Heads und des Squash-Merges ist identisch.

## Main und veröffentlichter Endstand

Main-Lauf `35215815686` für `5e03b00a` erfolgreich. Die Baumgleichheit zum grünen
PR wurde nachgewiesen; signierte Paketierung, aktuelles In-place-Upgrade und
alle sechs historischen Upgrade-Lanes waren erfolgreich. Stabile Veröffentlichung:
[0.2.180 / forest-android-1018001](https://github.com/ThonkTank/AI-Secretary/releases/tag/forest-android-1018001).

Die öffentlichen Dateien wurden nach Veröffentlichung erneut heruntergeladen
und unabhängig geprüft:

| Merkmal | Verifizierter Wert |
|---|---|
| Version | 0.2.180 / 1018001 |
| Tag und Metadaten-Commit | 5e03b00a87cc64226f3b6c9929de52b1ff58885f |
| APK-Paket | de.thonktank.autosecretary |
| APK-Größe | 2.873.648 Bytes |
| APK-SHA-256 | 46229008feadae034fbb4c98c5bfa8e1ce7ebf0ddecf6be6c2737e239672e252 |
| Signerzertifikat SHA-256 | de45d94c9724beeaa2e0dff31f69f53bb0f4c9ba79a5aa419d1f29d18f4d91da |

`release_tool.py validate`, APK-Paket-/Versionsauswertung und `apksigner verify`
waren erfolgreich; der Signer entspricht dem Vertrag in release.properties.

## P4 – verbleibende Geräteabnahme

Am 2026-09-17 meldet adb auch nach Veröffentlichung kein angeschlossenes Gerät.
Der Nutzer wurde während der Umsetzung um Anschluss und USB-Debugging-Freigabe
gebeten. Installierte Version, echte Wäscheketten und die separate Testkette sind
deshalb noch nicht am physischen Handy geprüft. Es wurde kein Geräteupgrade
und kein Schreibzugriff auf die persönlichen App-Daten durchgeführt.

P0–P3 und die signierte Softwarelieferung sind abgeschlossen. Für P4 bleiben
nach Anschluss: installierte Identität/Version prüfen, erforderlichenfalls
signiert und datenerhaltend aktualisieren und die in der Roadmap aufgeführten
Wäsche-/Testkettenzustände abnehmen. Die Gesamtroadmap bleibt bis dahin bei der
Geräteabnahme offen.
