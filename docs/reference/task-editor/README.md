# Visuelle Abnahme des Aufgaben-Wizards

## Referenzvertrag

Kanonisch ist Variante **2a** aus dem Design-Handoff „Aufgabendialog überarbeiten“: vier
Wizard-Blätter plus das nicht gezählte Schrittdetail. Auf der Übersicht bleibt der Fortschritt
ausgeblendet. Der gültige Referenzrahmen zeigt dort stattdessen die destruktive Aktion
„Löschen“; ein Fortschrittsbalken auf dieser Seite wäre eine Abweichung.

Die zehn unabhängigen HTML-Renderings liegen unter
`app/src/test/resources/reference/task-editor/variant-2a`. Sie stammen aus:

- Archiv SHA-256:
  `0987792299f95f36baa03ee309a7917de5025df98e65cd42cadae39801ac27cb`
- `design/Aufgaben-Editor Alternativen.dc.html` SHA-256:
  `ad4fe3a99e82b748324dbd35fc7c928bf7f94bdf1231df57750d771839ee4d9b`
- Chromium 140 / Playwright 1.55, Viewport 1280 px, Variante 2a bei 412×892 px

Die HTML-Bilder sind keine Golden-Baselines und werden nicht pixelweise mit Android verglichen.
Sie bleiben eine unabhängige Designreferenz. Die Android-Bilder unter
`app/src/test/resources/golden/task-editor/wizard` sind dagegen technische
Regression-Baselines.

## Kontaktblatt

[`contact-sheet-variant-2a.png`](contact-sheet-variant-2a.png) stellt für alle zehn Zustände links
die HTML-Referenz und rechts das aktuelle Android-Rendering gegenüber. Der Golden-Test erzeugt
dasselbe Blatt bei jedem Lauf zusätzlich unter
`app/build/reports/goldens/task-editor/contact-sheet-variant-2a.png`; dadurch wird es im
Quality-Artefakt jedes Pull Requests veröffentlicht.

## Verhaltensabdeckung

Die Acceptance-Suite prüft:

- Anlegen über Blatt 1 → 4 → Speichern;
- alle Seiten-, Detail-, Rücksprung-, Prompt- und Schließen-Pfade von Hardware-Back;
- jede „ändern“-Zeile einschließlich Rückkehr zur Übersicht;
- Abbrechen ohne Änderung sowie Behalten und Verwerfen bei geändertem Entwurf;
- Löschen und Behalten;
- Schritte hinzufügen, bearbeiten, verschieben und entfernen;
- Routing von der Übersicht zuerst zum fehlerhaften Aufgabenfeld und anschließend zum
  fehlerhaften Schritt.

CI darf die Baselines weiterhin nicht aktualisieren. `GoldenAssertionsTest` und der bestehende
Updatevertrag bleiben dafür das Gate. `TaskEditorGoldenCatalogTest` verhindert neue verwaiste
Editor-Baselines und erzwingt die Eins-zu-eins-Zuordnung von Szenario, Golden und HTML-Referenz.

## Menschliche Freigabe

Status: **VISUELL FREIGEGEBEN**

- Prüfende Person: Auftraggeber / Repository-Owner `ThonkTank`
- Datum: 2026-08-23
- Geprüfter PR-Commit: `5a6047eb82e64d8bf4e984855eb0358da2c0df62`
- Ergebnis: „visuell freigegeben“
- Prüfgrundlage: das oben verlinkte Side-by-Side-Kontaktblatt für alle zehn kanonischen Zustände

Automatisierte Goldens und eine Prüfung durch Codex ersetzen diese Freigabe ausdrücklich nicht.
