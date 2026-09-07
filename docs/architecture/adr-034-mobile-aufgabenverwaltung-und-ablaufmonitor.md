# ADR-034: Mobile Aufgabenverwaltung und kompakter Ablaufmonitor

- Status: angenommen
- Datum: 2026-09-07

## Kontext

Der Alles-Tab besitzt bereits die fachlich korrekte Platzierungskarten-Projektion und den
bestehenden Vollbild-Editor. Seine Oberfläche wirkt auf einem Telefon jedoch wie eine dichte
Werkzeugleiste neben einer großen Kartenliste: Filter beanspruchen dauerhaft Raum, Bearbeiten
liegt hinter einem Menü und Sortierziele erscheinen als beschriftete Leerzeilen. Laufende
Abläufe nehmen gleichzeitig unverhältnismäßig viel Platz ein, obwohl an dieser Stelle nur ein
Überblick und der Weg zur Detailansicht benötigt werden.

Die fachlichen Verträge aus ADR-021 und ADR-033 bleiben gültig. Insbesondere ist eine fällige
Ablaufkandidatin kein Hintergrundlauf; nur nutzergestartete Runs dürfen als laufend erscheinen.
Aufgabenbearbeitung, Persistenz, Zeitplatzierungen und Ablaufaktionen werden nicht neu entworfen.

## Entscheidung

### Alles ist ein mobiler Katalog

Der bestehende App-Header und die untere Navigation bleiben unverändert. Dazwischen erhält der
Alles-Tab eine ruhige, einspaltige Fläche mit telefongeeigneten Außenabständen. In Listenansicht
folgen aufeinander: Suche, kompakter Ablaufmonitor, gleichgewichtete Aktionen „Filtern“ und
„Reihenfolge“, Trefferzahl und die nach Morgen, Mittag, Abend und Später gruppierte Aufgabenliste.

Eine kompakte Aufgabenzeile zeigt Titel, Rhythmus beziehungsweise Fälligkeit, Schrittzahl und
Aufklappzustand. Ein direkt sichtbarer Stift öffnet unverändert den bestehenden Vollbild-Editor.
Löschen bleibt im Überlaufmenü. Aufgeklappte Schritte, Transfers, Schrittbearbeitung und
Hinzufügen behalten ihre vorhandenen Aktionen und Zustandsgrenzen.

### Filter wirken unmittelbar

„Filtern“ öffnet ein modales Compose-Blatt. Status, Tageszeit und Rhythmus stehen in der
Listenansicht zur Verfügung; im Sortiermodus kommt der Wochentag hinzu. Jede Auswahl wird sofort
an den bestehenden Screen-State-Owner gesendet. Es gibt keinen zusätzlichen Anwenden-Schritt
und keinen gespeicherten Offen-/Geschlossen-Zustand des Blatts. Die ausgewählten Filter selbst
bleiben wie bisher wiederherstellbar.

### Sortieren bleibt eine Variante desselben Katalogs

Im Sortiermodus bleiben Suche und Filter erreichbar. Die Ansicht kennzeichnet den Modus mit
„Reihenfolge“, einer kurzen Hilfe und „Fertig“. Sie zeigt ausschließlich aktive
Zeitplatzierungen. Long-Press-Drag, stabile Schlüssel, Randscrollen und Accessibility-Aktionen
bleiben erhalten. Permanente beschriftete Ablagezeilen entfallen; während eines aktiven Drags
zeigt genau eine dünne Einfügemarke die aktuelle Zielposition.

### Laufende Abläufe sind Vorschau, nicht zweite Arbeitsfläche

Der Alles-Tab zeigt höchstens zwei kurze, ein- bis zweizeilige Zusammenfassungen tatsächlich
laufender Runs. Ein Tippen öffnet die eigenständige Ablaufansicht. Diese wird in einer getrennten
Phase zu einer mobilen Compose-Ansicht mit kompakten Karten umgebaut, behält aber den bestehenden
`FlowRunsViewModel`, dessen Screen State, Use Cases, Dialoge und sämtliche Ablaufaktionen.

## Konsequenzen

`AllTasksPresentationState` speichert nicht länger, ob Filter sichtbar sind; alte gespeicherte
Bundle-Werte werden still ignoriert und benötigen keine Datenbankmigration. Die bestehende
Aufgaben- und Ablaufdomäne bleibt unverändert. Gemeinsame visuelle Hilfen dürfen lokal und klein
zwischen Alles-Tab und Ablaufansicht geteilt werden; ein allgemeines Design-System ist nicht
Teil dieser Entscheidung.

Die Umsetzung erfolgt in zwei nacheinander gemergten Phasen nach der
[mobilen Aufgaben-/Ablauf-Roadmap](all-tasks-mobile-roadmap.md). Jede Phase benötigt eigenen
Themenbranch, Pull Request, grünen Squash-Merge und den anwendbaren Main-Nachweis.
