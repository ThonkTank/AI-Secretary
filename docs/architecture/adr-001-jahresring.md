# ADR-001: XP, Gefäß und Kombo-Maserung

- Status: angenommen, ersetzt die Wochenring-Entscheidung vom 2026-08-15
- Datum: 2026-08-18

## Kontext

Der numerische Wochenring wird durch eine Fortschrittsmechanik aus Wert-Tau, Routinegefäß,
Kopfblatt und rein visueller Kombo-Maserung ersetzt. Aufgabe, Routine und stabiler Schritt
haben jeweils ein eigenes Punktekonto; eine Kombostufe wird nie als Zahl ausgegeben.

## Entscheidung

- Ein Schritt ist beim Abschluss `round(10 × (1 + 0,5 × Schrittkombo))` XP wert.
- Eine Routine erntet `round(Summe der Schritt-XP × (1 + 0,5 × Routinekombo))`.
- Einzelaufgaben erhalten bis zu 30 Grund-XP aus ihrer Verspätung und genau einen
  Aufgaben-Kombofaktor.
- Kombostufe `n` beginnt bei `n(n+1)/2` Punkten. Rechtzeitige Schritte geben einen Punkt,
  Routine-Ernten und rechtzeitige Einzelaufgaben drei; verspätete Einzelaufgaben verlieren
  zwei. Jeder vollständig inaktive Kalendertag kostet zwei Punkte.
- XP, angewandte Punktedeltas und stabile Schritt-Owner werden am Vorkommen gespeichert,
  damit ein heutiger Abschluss vollständig und idempotent rückgängig gemacht werden kann.
- „Rest erledigen“ und Ernte sind in der App getrennte Transaktionen. Die unveränderte
  Widget-Oberfläche führt beide Schritte atomar aus.
- Die Maserringe werden von einem gemeinsamen, gecachten Canvas-Renderer aus SDF-Konturen
  erzeugt. Er begrenzt nur die sichtbare Ringzahl; der fachliche Kombofaktor bleibt offen.
- Satzfortschritt ist ein editierbarer Zustand. Erst ein Wechsel zwischen offen und erledigt
  erzeugt beziehungsweise reversiert eine persistierte XP-/Kombo-Buchung.
- Die Migration erhält Gesamt-XP, startet alle Kombos bewusst bei null und übernimmt keine
  nicht belegbare Wochenhistorie.

## Konsequenzen

Der Dashboard-Refresh rechnet fälligen Komboverfall explizit ab. Das Kopfblatt leitet seine
Stufe ausschließlich aus Gesamt-XP ab. Alte Routine-Level- und Wochenringfelder bleiben in
Schema 5 nur als kompatible Altdaten erhalten und steuern kein Verhalten oder sichtbare Kopie.
Today-Aktionen werden seriell animiert und respektieren die systemweite Einstellung für
reduzierte Animationen; die Zustandsänderung selbst bleibt davon unabhängig.
