# ADR-039: Schrittnotizen ohne Trainingsassistent

Status: umgesetzt und auf Remote-`main` verifiziert (17.09.2026); ursprüngliche Umsetzung
in [PR #374](https://github.com/ThonkTank/AI-Secretary/pull/374), native Absicherung und
Tastaturkorrektur in [PR #376](https://github.com/ThonkTank/AI-Secretary/pull/376).

Der Trainingsassistent wird auf Nutzerwunsch vollständig entfernt. Gewicht wird als Freitext
in der Schrittnotiz geführt. Dies ersetzt die Trainingsanteile von ADR-030 und ADR-032;
allgemeine Schrittidentitäten, Ergebnisfortschritt, Pausen und Transaktionen bleiben bestehen.

## Verhalten

Die Today-Schrittzeile verwendet wieder den direkten Plus/Minus-Zähler mit horizontal scrollbar angeordneten
Satzbalken. Das Drei-Punkte-Menü enthält „Notiz bearbeiten“ für jeden echten dargestellten
Schritt. Sammelaktionen sind keine Schritte. Der Dialog enthält mehrzeiligen Freitext,
Abbrechen und Speichern. Ein leerer Text löscht die Notiz. SavedStateHandle bewahrt den
Entwurf bei Hostwechsel oder Prozesswiederherstellung; Fehler lassen den Entwurf offen.
Fokus und initialer Tastaturmodus werden vor dem Anzeigen des Dialogfensters gesetzt,
damit die Bildschirmtastatur auch auf API 26 beim Öffnen zuverlässig erscheint.

`EditStepNote` liest und schreibt über `StepRepository` in einer Transaktion. Es ändert nur
Notizfelder an der gewählten Ausführung und deren Vorlage; bei Flow-Ausführungen auch am
zugeordneten Lauf-Schritt. Startkandidaten bearbeiten ihre Vorlage. Bereits materialisierte
andere Ausführungen bleiben unverändert. Fehlende Vorlagen werden nicht rekonstruiert.
Room-Invalidierungen aktualisieren die bestehenden App- und Widget-Projektionen.

## Migration 27 → 28

Aktuelle Gewichtsvorgaben werden mit Einheit und voller Genauigkeit als neue Notizzeile
übernommen; Körpergewicht, Zusatzgewicht und Unterstützung bleiben eindeutig. Bestehende
Notizen bleiben erhalten. Eine identische Zeile wird nicht erneut angehängt. RIR, Lastdetails
abgeschlossener Sätze, Assistenteneinstellungen, Entscheidungen und offene Fragen entfallen.
Wiederholungen und Erledigungszustände bleiben bestehen.

Der Tabellenumbau sichert die vollständige betroffene Fremdschlüssel-Abhängigkeit vor dem
Löschen der Tabellen. Alle Spaltenzuordnungen sind explizit; Belohnungen, Zuordnungen, Timer,
Flow-Kanten und Ressourcen werden wiederhergestellt. Historische SQL-Migrationen und ihr
Datenleser bleiben für ältere Upgradepfade erhalten. Die App verwendet keine Trainings-Domain
und keinen Trainings-Repository-Port mehr.

## Verifikation und Abschluss

Migrationstests auf API 26 und 35 prüfen die Datenübernahme und Fremdschlüssel; weitere Tests
prüfen atomare Notizänderungen, Vorlagenbezug, verwaiste und erledigte Schritte, Dialogentwürfe
und die allgemeine Schrittbedienung. Screenshots prüfen Satzbalken auf kleinen Displays.

`StepNoteDialogInstrumentationTest` bedient das echte Drei-Punkte-Menü und den Dialog über
MainActivity, produktive Zustandsverarbeitung und Room mit sichtbarer Bildschirmtastatur.
Die fünf Fälle prüfen mehrzeiliges Speichern, Abbrechen, Leeren, den ungespeicherten Entwurf
nach Activity-Wiederherstellung und die Vorlagenbearbeitung eines Ablauf-Startkandidaten ohne
Laufstart. Ausführung, Vorlage und unveränderter Satzfortschritt werden aus Room zurückgelesen.
Die Activity-Wiederherstellung ist kein nativer Nachweis eines Prozessabbruchs.

Die Nachbesserung bestand den vollständigen lokalen Gate einschließlich 800 bestandener
JVM-/Robolectric-Tests (ein optionaler Benchmark übersprungen), 100 Python-Tests, Lint,
APK-Builds, Instrumentation-Identität und Größenbudgets. Die
[PR-Verifikation](https://github.com/ThonkTank/AI-Secretary/actions/runs/35210683750)
bestand auf API 26, 35 und 37 einschließlich der separaten Animationsprüfungen.
PR #376 wurde als `726e72996231d968b6240a1727d2d4f8377b0b9d` nach `main` übernommen;
der Inhaltsbaum stimmt mit dem geprüften PR überein. Die
[Main-Verifikation](https://github.com/ThonkTank/AI-Secretary/actions/runs/35212621051)
bestätigte die Qualitäts- und Instrumentation-Gates anhand dieser identischen PR-Nachweise.

Die ursprüngliche Entfernung des Assistenten aus PR #374 wurde als
[0.2.177](https://github.com/ThonkTank/AI-Secretary/releases/tag/forest-android-1017701)
veröffentlicht; die zugehörige
[Main-/Release-Pipeline](https://github.com/ThonkTank/AI-Secretary/actions/runs/34837471315)
einschließlich der signierten Upgradeprüfungen war grün. Dieser Release-Nachweis bezieht
sich auf die ursprüngliche Umsetzung und enthält die spätere Tastaturkorrektur aus PR #376
noch nicht. Veröffentlichung und signiertes Geräteupgrade bleiben getrennte Nachweise.
Eine manuelle Abnahme auf einem physischen Gerät ist für diese Nachbesserung weiterhin offen;
Emulatortests und CI-Upgradeprüfungen ersetzen sie nicht.
