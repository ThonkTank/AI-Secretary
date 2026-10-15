# ADR-040: Schritte direkt in Heute bearbeiten

Status: implementiert; Verifikation siehe PR.

## Bedienung und Zustandsbesitz

Das Drei-Punkte-Menü bietet zusätzlich zu „Notiz bearbeiten“ den Dialog „Schritt bearbeiten“.
Er verwendet die Schritt-Eingaben und die gemeinsame Schrittvalidierung des Task Wizards,
öffnet aber keine Task-Seiten. Name, Rhythmus, Menge/Dauer, Satzpause und Notiz lassen sich
mit Speichern dauerhaft übernehmen oder mit Abbrechen/Zurück verwerfen. Bei einmaligen
Aufgaben und Folge-Schritten bleibt der Rhythmus ausgeblendet.

`TodayViewModel` besitzt den Entwurf samt ursprünglichen Feldwerten, Validierungs- und
Speicherfehlern. `SavedStateHandle` bewahrt diese Daten; ein laufender Speichervorgang wird
nach Wiederherstellung nicht automatisch wiederholt. Die Compose-Präsentation schreibt
keine Daten selbst. Initialer und wiederhergestellter Eingabefokus warten auf das aktive
Fenster. Der Dialog setzt den initialen Tastaturmodus und Größenanpassung explizit,
damit auf API 26 weder der Tastaturstart verloren geht noch Touch-Scrollen hinter der
Tastatur landet. Leere Mengen sind zulässige Zwischenstände im Editor; eine ungültige
Vorgabe kann die validierte Domain-Speichergrenze nicht passieren.

## Speichervertrag

`EditStep` arbeitet über `StepEditRepository` in einer Transaktion und liest beim Speichern
Quelle und Fortschritt erneut. Nur bearbeitete Felder werden aus dem Entwurf übernommen;
unberührte Felder behalten zwischenzeitliche Änderungen. Task-Einstellungen, Platzierung,
Schrittidentitäten, Ablaufverbindungen und Ressourcen werden nicht bearbeitet.

Die Vorlage übernimmt alle Änderungen. Die gewählte Ausführung und ihr zugehöriger
Ablauf-Snapshot übernehmen Name und Notiz sofort. Mengen-, Dauer- und Pausenvorgaben werden
nur bei unbegonnenen Schritten sofort geändert. Erfasste Sätze, Erledigung oder eine vorhandene
Timer-Sitzung (auch pausiert oder beendet) schützen die bisherigen Vorgaben. Bereits
betätigte Ablauf-Schritte behalten ebenfalls ihre Laufvorgaben. Der Dialog erklärt diese
Regel; bei einer solchen Änderung bestätigt die Rückmeldung die Gültigkeit ab der nächsten
Ausführung. Rhythmusänderungen gelten nur für künftige Ausführungen.

Andere materialisierte Ausführungen bleiben unverändert. Ein Startkandidat bearbeitet nur
seine Vorlage und startet keinen Lauf. Fehlende Vorlagen oder Ziele werden nicht rekonstruiert;
der bestehende Notizdialog kann weiterhin verwaiste Ausführungen bearbeiten.

Room aktualisiert Ausführungsdetails mit partiellen Updates, die Zustände, Ergebnisse,
Timer, Identitäten, Zeitpunkte und Rewards nicht schreiben. Die vorhandenen Invalidierungen
aktualisieren App und Widget. Das Datenbankschema bleibt unverändert.

## Verifikation

`StepQuickEditRobolectricTest` prüft produktive Use Cases, TodayViewModel und Room auf API 26/35,
einschließlich Transaktionsabbruch, konkurrierendem Fortschritt, unveränderten Ergebnissen und
Timern, Ablaufkandidaten sowie Entwurfswiederherstellung. Bestehende Wizard-Goldens sichern
die extrahierten Eingaben ab. `StepQuickEditorRenderTest` erzeugt kompakte helle und dunkle
Dialogansichten zur visuellen Prüfung.

`StepQuickEditInstrumentationTest` bedient das produktive Drei-Punkte-Menü, den Compose-Dialog,
die Bildschirmtastatur und Room. Es prüft Speichern mit vorhandenem Satzfortschritt,
Abbrechen, Validierung, Activity-Wiederherstellung und Ablaufkandidaten. Activity- und
SavedState-Tests sind kein nativer Nachweis eines Prozessabbruchs oder einer Abnahme auf
einem physischen Gerät.
