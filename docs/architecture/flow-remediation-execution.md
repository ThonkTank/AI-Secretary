# Ablaufblatt: Remediation-Ausführung

Referenz: [Roadmap](flow-remediation-roadmap.md). Beginn 2026-09-17;
Remote-main `9b0d3dc7`. Isolierter Worktree; alter Frontend-Checkout unverändert.

| Phase | Status |
|---|---|
| P0 Prüfablauf | Erfüllt; wird in den Folgephasen weiter angewendet |
| P1 Wartezeitbedienung | Abgeschlossen: PR #375, Remote-main 83750722 |
| P2 Ablaufende | Implementierung und gezielte Prüfung |
| P3 Animation/Modell | Ausstehend |
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
