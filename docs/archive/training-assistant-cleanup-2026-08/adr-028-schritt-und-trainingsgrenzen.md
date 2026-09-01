# ADR-028: Eindeutige Schritt-, Satz- und Trainingsentscheidungsgrenzen

- Status: angenommen
- Datum: 2026-08-29

## Kontext

ADR-027 führte einen fachlich reinen, deterministischen Trainingsassistenten ein. Seine erste
Integration musste Trainingsfelder jedoch durch mehrere Vorlagen-, Snapshot-, Editor- und
Persistenzmodelle führen. Wiederholungen und vollständige Trainingssatzdaten entstanden als zwei
gekoppelte In-Memory-Wahrheiten. Die vorhandene Auditspur war nicht bis in die UI projiziert.

Zusätzlich setzte der Editor beim Aktivieren ohne Wissen über die konkrete Maschine 20 kg sowie
2,5 kg beziehungsweise 5 lb als Lastschritt. Ein solcher Geräteschritt kann weder zuverlässig
geraten noch immer innerhalb der automatischen Zehn-Prozent-Grenze liegen.

## Entscheidung

Die Zielarchitektur besitzt drei fachliche Wahrheiten:

1. `StepPrescription` ist der gemeinsame, unveränderliche Ausführungsplan für Vorlage,
   Occurrence und Flow-Snapshot.
2. `SetResult` ist das vollständige atomische Ist-Ergebnis eines Satzes; Trainingsdetails sind
   Bestandteil desselben Resultats und keine parallele Liste.
3. `TrainingDecision` ist die versionierte, erklärbare Ausgabe der Regel-Engine und kann eine
   Änderung anwenden, halten, pausieren oder eine konkrete nächste Last anfragen.

Ein Assistentenprofil existiert nur bei aktiviertem Assistenten. Es enthält keine angenommene
Geräteschrittweite. Für veränderbare Lasten muss der Nutzer die reale Ausgangslast angeben. Wenn
eine Lastprogression ansteht, fragt die App nach der konkret nächsten verfügbaren Last. Antworten
werden nur für diese Entscheidung verwendet und nicht als allgemeines Geräteprofil interpretiert.
Ein automatischer Sprung über zehn Prozent bleibt ausgeschlossen; eine bewusste manuelle Änderung
bleibt möglich und startet die Kalibrierung neu.

Anpassungen und offene Lastfragen werden lokal persistiert, stabil geordnet und bis in die
Präsentation projiziert. Der Inline-Verlauf zeigt die letzten zehn Entscheidungen. Nur die neueste
angewendete Anpassung kann rückgängig gemacht werden, solange ihr Nachzustand noch aktuell ist.

Composition und Transaktionen werden über fokussierte Ports und einen eigenen
`TransactionRunner` verdrahtet. Ein breites Repository darf als konkrete Implementierungsdetails
existieren, ist aber kein Domainvertrag und keine Pflicht für Feature-Testdoubles.

## Konsequenzen

Die Migration erfolgt ausschließlich in den Phasen der
[Trainingsassistenten-Roadmap](training-assistant-cleanup-roadmap.md). Bis zum jeweiligen
Phasenabschluss bleiben bestehende APIs kompatibel; danach werden die ersetzten Konstruktoren,
Typen und Portmethoden entfernt. Bereits materialisierte Planungssnapshots und historische
Resultate werden nicht umgedeutet. ADR-027 bleibt für Forschung, Kalibrierung, Sicherheitsmarker,
Progressionsreihenfolge und Volumengrenze maßgeblich; seine Annahme einer festen Lastschrittweite
und seine konkrete damalige Schemanummer werden durch diese Entscheidung ersetzt.
