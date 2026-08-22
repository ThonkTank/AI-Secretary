# ADR-021: Platzierungskarten und Archivbearbeitung im Alles-Tab

- Status: angenommen
- Datum: 2026-08-22

## Kontext

ADR-017 trennt die Aufgaben- von der Sortieransicht und zeigt in der Aufgabenansicht jede
Definition einmal. Das kompakte Redesign des Alles-Tabs ordnet die ruhige Leseliste dagegen nach
Tageszeiten. Eine wiederkehrende Aufgabe kann mehrere Zeitplatzierungen besitzen; eine einzelne
Karte müsste diese Einordnung verstecken oder mehrere Zeiten in eine schwer lesbare Metazeile
pressen. Archivierte Definitionen waren außerdem nur lesbar, obwohl ihre Inhalte weiterhin
vollständig und verlustfrei im bestehenden Editor geladen werden können.

## Entscheidung

- Die Aufgabenansicht projiziert eine Karte pro `task_schedule_entries`-Platzierung. Mehrfach
  eingeplante Aufgaben erscheinen daher in jeder Tageszeit, verweisen aber weiterhin auf dieselbe
  Definition und dieselben stabilen Schritt-IDs.
- Karten werden nach Tageszeit und danach nach der manuellen Katalogreihenfolge sortiert. Filter
  und Trefferzahlen arbeiten auf den sichtbaren Platzierungskarten. Der Aufklappzustand gehört zur
  Kombination aus Aufgaben-ID und Tageszeit.
- Bearbeiten oder Löschen einer Karte wirkt auf die gemeinsame Definition und damit auf alle ihre
  Karten. Archivierte Definitionen dürfen im bestehenden Vollbild-Editor bearbeitet werden und
  bleiben beim Speichern archiviert.
- Schritttransfers und die Sortieransicht bleiben auf aktive Aufgaben beschränkt. Im Sortiermodus
  ist der Status deshalb fest `aktiv`; jede Zeitplatzierung bleibt dort eine unabhängig
  verschiebbare Zeile.
- Suche berücksichtigt Aufgaben- und Schritttitel, nicht Notizen. Ein Schritttreffer öffnet die
  betroffene Karte abgeleitet und zeigt nur passende Schritte, ohne den manuellen Aufklappzustand
  zu überschreiben.
- Die Umschaltung zwischen Listen- und Sortiermodus bleibt als Aktion in der Ergebniszeile. Der
  Wochentagsfilter wird ausschließlich im Sortiermodus angeboten, weil er dort die sichtbaren
  Zeitplatzierungen einschränkt.
- Einfügeziele für Schritte und Zeitplatzierungen sind ausschließlich während eines aktiven
  Drag-Vorgangs sichtbar und belegen nur währenddessen Layoutplatz. Außerhalb eines Drags bleibt
  die kompakte Leseliste ohne leere Zielzeilen erhalten.
- Der geöffnete oder geschlossene Filterbereich ist wiederherstellbarer Präsentationszustand und
  überlebt Activity- sowie Prozess-Neuerstellung. Ein geöffnetes Dropdown und ein aktiver Drag
  sind dagegen flüchtig; beide werden bei Abbruch, Detach oder Neuerstellung geschlossen.

## Konsequenzen

ADR-017 bleibt für Ports, Zeitplanungswahrheit, Transfers, Historie und die zwei Modi gültig. Die
dortigen Sätze „jede Definition einmal“ und „archivierte Aufgaben sind nur lesbar“ werden durch
diese Entscheidung ersetzt. Es entsteht keine neue Datenbanktabelle und keine Migration: Die
Mehrfachdarstellung ist ausschließlich eine Präsentationsprojektion. Stable RecyclerView-Keys
müssen die Zeitplatzierung einschließen, während Editier-, Lösch- und Schrittaktionen weiterhin
die gemeinsame Aufgaben-ID verwenden.
