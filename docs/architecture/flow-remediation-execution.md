# Ablaufblatt: Remediation-Ausführung

Referenz: [Roadmap](flow-remediation-roadmap.md). Beginn 2026-09-17;
Remote-main `9b0d3dc7`. Isolierter Worktree; alter Frontend-Checkout unverändert.

| Phase | Status |
|---|---|
| P0 Prüfablauf | Erfüllt; wird in den Folgephasen weiter angewendet |
| P1 Wartezeitbedienung | Abgeschlossen: PR #375, Remote-main 83750722 |
| P2 Ablaufende | Abgeschlossen: PR #377, Remote-main cd7c51df |
| P3 Animation/Modell | Regression und Implementierung |
| P4 Handy | Offen: adb meldet kein Gerät |

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
Accessibility-Aktion bestehen. Native APKs, vollständiger lokaler Lauf und PR-Matrix
sind vor dem Merge noch ausstehend. Roadmap-Abgleich des Patches: alle P1-Szenarien
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
Vollständiger lokaler Lauf und PR-Matrix stehen vor dem Phasenabschluss noch aus.

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
Die korrigierte gesamte Regression wird nochmals am unveränderten Produktstand
verglichen, bevor die endgültige Korrektur abgenommen wird.

P3 eindeutiger negativer Nachweis auf dem ursprünglichen Produktcode: je API 26/35
scheitern Einhängezustand, ausgeblendete Eltern/Fenster, übergeordnetes Scrollen,
horizontales Clipping und fortlaufende Zeichenanforderungen unsichtbarer bereiter
Container (zehn Puls-Regressionsfälle). Für die Frame-Prüfung besteht zuerst die
positive Kontrolle des sichtbaren Containers. Der sichtbare Countdown benötigt
außerdem die Layoutnachführung beim ersten Einhängen. Die Füllanimation wird in
16-ms-Schritten simuliert: ein einzelner großer Sprung liefert bei pausierter
Vsync-Simulation nur einen Frame und ist kein Nachweis einer abgeschlossenen
endlichen Animation. Die Produktkorrektur wird danach unverändert wieder angewandt.
