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
- Tatsächlich angewandte XP- und Punktedeltas werden mit stabilem Owner im unveränderlichen
  Reward-Ledger gespeichert. Vorkommens- und Schrittzustand enthalten keine Rewardfelder mehr;
  heutiges Undo verwendet ausschließlich die exakte Originalbuchung.
- „Rest erledigen“ und Ernte sind in der App getrennte Transaktionen. Die unveränderte
  Widget-Oberfläche führt beide Schritte atomar aus.
- Die Maserringe werden außerhalb des UI-Threads als immutable SDF-Konturen vorgebaut und in
  einem auf 4 MiB gewichteten Cache gehalten. `onDraw` zeichnet nur fertige Renderdaten. Der
  Renderer begrenzt nur die sichtbare Ringzahl; der fachliche Kombofaktor bleibt offen.
- Satzfortschritt ist ein editierbarer Zustand. Erst ein Wechsel zwischen offen und erledigt
  erzeugt beziehungsweise reversiert eine persistierte XP-/Kombo-Buchung.
- Der Erledigtzustand eines Satzschritts ist explizit und wird nicht aus der Zahl erfasster
  Sätze abgeleitet. Das Bestätigen des letzten geplanten Satzes ist ein eigener Abschlussbefehl;
  reines Bearbeiten bewahrt den Zustand, und Wiederöffnen bewahrt den erfassten Fortschritt.
- Die Migration erhält Gesamt-XP, startet alle Kombos bewusst bei null und übernimmt keine
  nicht belegbare Wochenhistorie.
- Seit Schema 6 enthält jeder neue Vorkommensschritt seine `sourceTemplateId`. Historische
  Schritte erhalten sie nur bei einem eindeutigen Owner-/Template-Match; ein unbekannter Owner
  bleibt erhalten, ohne eine Template-Identität zu erfinden.
- Seit Schema 7 sind tatsächlich angewandte Rewards unveränderliche, vorzeichenbehaftete
  Buchungen. Undo erzeugt eine eindeutige Gegenbuchung; Details und Migrationsvertrag stehen in
  [ADR-010](adr-010-reward-ledger-und-gegenbuchungen.md).

## Konsequenzen

Der Dashboard-Refresh rechnet fälligen Komboverfall explizit ab. Das Kopfblatt leitet seine
Stufe ausschließlich aus Gesamt-XP ab. Schema 6 entfernt die alten Routine-Level- und
Wochenringfelder durch einen verlustfreien Tabellenneuaufbau; sie gehören nicht mehr zum aktiven
Task-Modell.
Today-Aktionen werden seriell animiert und respektieren die systemweite Einstellung für
reduzierte Animationen; die Zustandsänderung selbst bleibt davon unabhängig.
