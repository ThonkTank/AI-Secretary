# Changelog

## Unreleased

- Dauer-Schritte können in Today als eigenständige Countdown-Timer gestartet, pausiert und
  zurückgesetzt werden, ohne den Schritt automatisch abzuhaken.
- Gym-Schritte mit Sätzen und Wiederholungen unterstützen automatische, pro Schritt konfigurierbare
  Satzpausen mit einer globalen 60-Sekunden-Vorgabe.
- Timer laufen parallel, überstehen Prozessende und Geräteneustart und melden ihr Ende per
  Benachrichtigung, Ton/Vibration und Schrittanimation.

## Nächster automatischer 0.2-Build

- Neuer „Alles“-Tab zum Suchen, Filtern und Bearbeiten aktiver sowie archivierter Aufgaben.
- Native Drag-and-drop-Sortierung pro Tageszeit, einschließlich mehrmals täglich geplanter
  Aufgaben und direkter Synchronisierung offener Heute-Vorkommen.
- Schritte können innerhalb einer Aufgabe und zwischen Aufgaben verschoben oder getauscht
  werden; passende offene Zustände und Reward-Buchungen wandern konsistent mit.
- Room-Schema v12 normalisiert die unabhängigen Tageszeit-Platzierungen einer Aufgabe mit
  verlustfreier Migration aus den bisherigen Zeitmasken.
- Neuer Vollbild-Aufgabeneditor für Dauer, Rhythmus, mehrere Tageszeiten, Fristen und Grenzen.
- Stabile Schrittdefinitionen mit Wochentagen, Mengen, Notizen und unveränderlichen Vorkommens-Snapshots.
- Satzweise Erfassung tatsächlicher Wiederholungen direkt aus der Tagesansicht.
- Mehrere idempotente Vorkommen pro Aufgabe und Tag sowie verlustfreie Room-Migration auf Schema v4.
- 17 automatisierte Editor-Golden-Zustände einschließlich Inline-Fehlern und eigenen Rückfragen.
- Jeder grüne `main`-Push veröffentlicht eine dauerhaft signierte APK mit monotoner Version.
- Automatische tägliche und manuelle Updateprüfung direkt in den Optionen.
- Größen-, Hash-, Paket-, Versions- und Signaturprüfung vor Übergabe an den Android-Installer.
- Verständlicher Berechtigungspfad für seitlich geladene Updates und GitHub-Fallback bei Fehlern.

## 0.2.0 – 2026-08-15

- Room-Schema v3 mit verlustfreien Migrationen 1→2→3 und typisierten Domain-Modellen.
- Kleine, einzeln getestete Task-Use-Cases statt fachlicher Logik in UI und Transaktionen.
- Manueller Application-Container, injizierbare Zeit/Zeitzone und strukturierte Fehlergrenzen.
- Unveränderlicher Dashboard-State und komponentenbasierte, differenziell aktualisierte Views.
- Vollständiger Aufgaben-Editor für Wiederholung, Schritte und ongoing-Bedingungen.
- Strukturierte, gecachte und permission-sichere Kalenderintegration für alle sichtbaren Kalender.
- Benanntes Designsystem mit acht getesteten Tagesankern und gemeinsamem Wald-Renderer.
- Responsive Widget-Pipeline für vier Größen mit API-26-Fallback und sicheren Aktionen.
- Erweiterte Unit-, Migration-, Golden-, Accessibility- und Widgettests sowie API-Matrix-CI.
- Adaptives Launcher-Icon und aktualisierte AndroidX-Abhängigkeiten.

## 0.1.0

- Erste lokale Stabilitätsversion.
