# Ablaufblatt: Remediation-Ausführung

Referenz: [Roadmap](flow-remediation-roadmap.md). Beginn 2026-09-17;
Remote-main `9b0d3dc7`. Isolierter Worktree; alter Frontend-Checkout unverändert.

| Phase | Status |
|---|---|
| P0 Prüfablauf | Im Einsatz; Abschlussbeleg mit P1 |
| P1 Wartezeitbedienung | Implementierung und gezielte Prüfung |
| P2 Ablaufende | Ausstehend |
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
