# ADR-039: Schrittnotizen ohne Trainingsassistent

Status: angenommen; Implementierung und Verifikation im zugehörigen Themenbranch.

Der Trainingsassistent wird auf Nutzerwunsch vollständig entfernt. Gewicht wird als Freitext
in der Schrittnotiz geführt. Dies ersetzt die Trainingsanteile von ADR-030 und ADR-032;
allgemeine Schrittidentitäten, Ergebnisfortschritt, Pausen und Transaktionen bleiben bestehen.

## Verhalten

Die Today-Schrittzeile verwendet wieder den direkten Plus/Minus-Zähler mit horizontal scrollbar angeordneten
Satzbalken. Das Drei-Punkte-Menü enthält „Notiz bearbeiten“ für jeden echten dargestellten
Schritt. Sammelaktionen sind keine Schritte. Der Dialog enthält mehrzeiligen Freitext,
Abbrechen und Speichern. Ein leerer Text löscht die Notiz. SavedStateHandle bewahrt den
Entwurf bei Hostwechsel oder Prozesswiederherstellung; Fehler lassen den Entwurf offen.

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
Der Abschluss erfordert den vollständigen lokalen Gate, grüne PR-Pflichtchecks und den
verifizierten Squash-Merge auf Remote-main. Veröffentlichung und signiertes Geräteupgrade
sind getrennte Nachweise und werden nicht durch lokale Tests behauptet.
