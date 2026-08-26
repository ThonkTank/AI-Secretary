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
die HTML-Referenz und rechts den visuell freigegebenen Android-Referenzstand gegenüber. Seit dem
Compose-Cutover bleibt dieses geprüfte Handoff-Dokument unverändert; aktuelle Renderings und
Diffs werden zustandsweise in den Quality-Artefakten veröffentlicht.

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

CI darf die Baselines nicht aktualisieren. Der produktive Compose-Vergleich ist ausschließlich
lesend und besitzt keinen Updatepfad. `TaskEditorGoldenCatalogTest` verhindert neue verwaiste
Editor-Baselines und erzwingt die Eins-zu-eins-Zuordnung von Szenario, Golden und HTML-Referenz.

## Menschliche Freigabe

Status: **VISUELL FREIGEGEBEN**

- Prüfende Person: Auftraggeber / Repository-Owner `ThonkTank`
- Datum: 2026-08-23
- Geprüfter PR-Commit: `5a6047eb82e64d8bf4e984855eb0358da2c0df62`
- Ergebnis: „visuell freigegeben“
- Prüfgrundlage: das oben verlinkte Side-by-Side-Kontaktblatt für alle zehn kanonischen Zustände

Automatisierte Goldens und eine Prüfung durch Codex ersetzen diese Freigabe ausdrücklich nicht.

## Adaptive Fidelity (Phase 5)

[`contact-sheet-adaptive-fidelity.png`](contact-sheet-adaptive-fidelity.png) dokumentiert die fünf
zusätzlichen Abnahmeszenarien: die Übersicht in der Nachtpalette sowie Titel, Rhythmus, Schritte
und Übersicht bei 320×640 dp und Schriftfaktor 1,5. Die zehn kanonischen 412×892-Ansichten bleiben
pixelgleich zu ihren bestehenden Regression-Baselines.

Der adaptive Vertrag verwendet bei Breiten unter 360 dp oder einem Schriftfaktor ab 1,3 das
Kompaktlayout. Es teilt die Fußzeile in zwei Reihen, ordnet die Wochentage als 4+3-Raster an und
lässt das Fokusblatt nur bei echtem Überlauf scrollen. Interaktive Ziele werden in beiden Layouts
mit mindestens 48×48 dp geprüft. Abhängige Felder und Prompts verwenden den 240-ms-Motion-Token
mit der Kurve `(0.2, 0.7, 0.3, 1)`; bei ausgeschalteten Systemanimationen werden sie unmittelbar
in den Endzustand versetzt. Ein Quelltext-Gate verhindert unabhängige hart codierte Farben in den
Editor-Komponenten.

Die freigegebenen Baselines liegen unter
`app/src/test/resources/golden/task-editor/adaptive`. Nach dem Compose-Cutover sind sie
schreibgeschützt; eine Änderung erfordert einen gesonderten, menschlich geprüften Design-Handoff.

## Produktiver Compose-Renderer (Phase 5b)

Der produktive Compose-Editor rendert dieselben zehn kanonischen und fünf adaptiven Zustände gegen die
unveränderten, bereits freigegebenen Android-Baselines. Der eigene Vergleich ist mit 64
Farbkanalstufen und höchstens 25 Prozent abweichenden Pixeln enger als der allgemeine
Editor-Migrationsvertrag. Jeder Lauf schreibt die tatsächlichen Compose-Bilder in die
Quality-Artefakte. Der Vergleich ist strikt schreibgeschützt: Weder lokal noch in CI existiert
ein Aktualisierungspfad für die autoritativen Referenzbaselines.

Phase 5b montiert diesen Renderer über den produktiven `TaskEditorCoordinator`; der frühere
View-Renderer, sein XML und seine reine View-Orchestrierung sind entfernt. Dieselbe Hostgrenze
trägt die bestehenden Save-/Delete-/Close-Actions zum einzigen `TaskEditorViewModel`. Die
automatisierte Prüfung ist keine neue menschliche oder physische Gerätefreigabe.

### Erneute menschliche Freigabe

Status: **VISUELL FREIGEGEBEN**

- Prüfende Person: Auftraggeber / Repository-Owner `ThonkTank`
- Datum: 2026-08-23
- Geprüfter UI-Commit: `db758d3675dd44253ccc06367b0dc7bde35b45e1`
- Ergebnis: „visuell freigegeben“
- Prüfgrundlage: das adaptive Kontaktblatt mit Nachtübersicht und den vier Kompaktansichten

Automatisierte Goldens und die technische Prüfung durch Codex ersetzen diese Freigabe
ausdrücklich nicht.
