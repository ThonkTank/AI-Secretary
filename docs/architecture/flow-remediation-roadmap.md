# Ablaufblatt: Remediation-Roadmap

Beauftragt am 2026-09-17 auf Grundlage des Postmortems und Remediation-Assessments
zu PR #373. Ausgangsstand: Remote-main `9b0d3dc7`.

## P0 – Prüfablauf (PM3)

Gezielte betroffene Tests während der Diagnose; vollständiger lokaler Prüflauf und
alle erforderlichen PR-Checks vor jedem Implementierungsabschluss. Wiederholungen
brauchen einen konkreten Anlass. Auf beobachtbare Ergebnisse mit festen Zeitgrenzen
warten, keine globale Leerlaufprüfung für dauerhaft animierte Oberflächen. Fehler
anhand gesicherter Diagnose erklären; keine automatischen Wiederholungen zum Verdecken
von Fehlern. Statusmeldungen nennen neue Erkenntnisse, Entscheidungen oder Hindernisse.
Prüfzuordnung mit den jeweiligen Änderungen aktualisieren; kein eigener P0-PR.

## P1 – Wartezeitbedienung (PM4)

Branch `codex/flow-wait-interactions`: einzelne Wartezeit bestätigen, bei mehreren
gezielt auswählen, gleichnamige Ketten anhand Lauf-/Warte-IDs auseinanderhalten,
Auswahl und Zeitdialog ohne Schreibvorgang abbrechen. Einsammelbereite Ketten über
Langdruck und benannte Accessibility-Aktion bearbeiten, ohne Tau einzusammeln.
Komponentenprüfung des Befehls, tatsächliche Speicherung und native Bedienwege.
Abschluss: genau die ausgewählte Wartezeit ändert sich; andere Ketten, Wartezeiten
und Tau-Buchungen bleiben unverändert. Abbrechen schreibt nichts.

## P2 – Ablaufende und App-Aktualisierung (PM4)

Nach P1, Branch `codex/flow-deadline-integration`: produktive Verbindung aus
TodayViewModel, Terminplanung, Runtime und Darstellung prüfen. Kontrollierte Zeit
und Testdaten; Aktivierung über den normalen Aktualisierungsweg. Verborgenes Blatt
erscheint bei Fälligkeit, Verlängern ersetzt den alten Termin, Verkürzen zieht ihn
vor, Rückkehr aus dem Hintergrund holt Fälligkeit nach. Abschließende Wartephase
zeigt einsammelbaren Container ohne Schrittzeilen; wiederholte Aktualisierungen
zahlen nicht doppelt aus. Mindestens ein nativer Test mit echtem Activity-Lebenszyklus.
Abschluss: kein manueller activateReady-/Aktualisierungsaufruf zur Herstellung des
Testergebnisses erforderlich.

## P3 – Animation und Modell (PM5a/b)

Nach P2, Branch `codex/flow-vessel-lifecycle`: verbleibende Pulsaufrufe vollständig
herausgescrollter, angehängter Container nachweisen; Pulssteuerung an Sichtbarkeit
und Lebenszyklus binden. Wieder sichtbare bereite Container pulsieren erneut;
verborgene Views, Hintergrund und Entfernen lassen keine Pulsaufrufe zurück.
Systemanimationseinstellung respektieren. Gemeinsame normale Tau-Container,
Countdown, Füllanimation und Touch-Prüfung berücksichtigen. Ungenutztes
FocusTaskUiModel.waits samt Builder-Zugang entfernen; editableWaits bleibt bestehen.
Abschluss: keine unsichtbaren Pulsaufrufe, korrekte sichtbare Animation/Bedienung,
keine Referenzen auf das alte Feld. Akkugewinn nur bei tatsächlicher Messung behaupten.

## P4 – Geräteabnahme (G1)

Nach letztem Merge und erfolgreicher Release-Prüfung: installierte Version und
signierten Endstand prüfen. Tatsächliche Wäscheketten mit Titel, Fortschritt,
Schritt-Kombo, größter Countdown-Einheit, leerendem Kreis, Aus-/Wiedereinblenden,
Scrollen ohne Einsammeln, Wartezeitänderung und einmaliger Auszahlung abnehmen.
Separate Testkette für verkürzte Abläufe. Erforderliches Upgrade signiert und
datenerhaltend. Ohne erreichbares Handy bleibt nur diese Abnahme offen.

## Gemeinsamer Abschlussvertrag

Jede Implementierungsphase: eigener Branch/Commit, PR gegen main, erforderliche
Checks grün, Squash-Merge. Bestehender Frontend-Checkout bleibt erhalten. Lokale
Prüfung, PR, gemergter Stand, Release und Gerät werden getrennt belegt.
Gesamtabschluss erst mit Geräteabnahme. Fortschritt und Nachweise stehen in
[flow-remediation-execution.md](flow-remediation-execution.md).
