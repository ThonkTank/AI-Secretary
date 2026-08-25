# ADR-010: Unveränderliches Reward-Ledger und Gegenbuchungen

- Status: angenommen
- Datum: 2026-08-21

## Kontext

Bis Schema 6 lagen die tatsächlich angewandten XP- und Kombodeltas als veränderliche Felder
direkt auf `Occurrence` und `OccurrenceStep`. Dadurch waren Zustandsautomat, Anzeigeprojektion
und Abrechnungsbeleg gekoppelt. Ein Undo löschte diese Felder und transportierte seine Richtung
zusätzlich über ein boolesches `reversed`. Atomare Aktionen mit mehreren Schrittbuchungen und
einer Ernte ließen sich nicht als eine Transaktion beschreiben.

## Entscheidung

Schema 7 führt `reward_bookings` als append-only Ledger ein. Jede Buchung besitzt eine eindeutige
Buchungs- und Transaktions-ID, Occurrence, optionalen Schritt, Owner, Art, Ziel (`VESSEL` oder
`HEAD`), vorzeichenbehaftete XP- und Kombodeltas, Buchungsdatum sowie optional die Referenz auf
die stornierte Originalbuchung.

Undo verändert oder löscht keine Originalbuchung. Es fügt genau eine `REVERSAL`-Buchung mit den
exakt negierten Deltas hinzu. Ein eindeutiger Index auf `reversesBookingId` verhindert eine zweite
Gegenbuchung für dasselbe Original auch bei einem Retry. Ein späterer erneuter Abschluss erzeugt
eine neue Originalbuchung und eine neue Transaktions-ID.

`stats.xp` und `combo_progress` bleiben materialisierte Projektionen für schnelle Lesezugriffe.
Sie werden innerhalb derselben Room-Transaktion wie Ledger und Zustandswechsel aktualisiert; das
Ledger ist die Quelle der exakten Undo-Deltas. Nur `HEAD`-XP verändern `stats.xp`. `VESSEL`-XP
bilden den eingesammelten Schrittwert und werden beim Ernten durch die Routine-Regel bewertet.

Die Migration 6→7 überführt vorhandene Schritt- und Occurrence-Rewards in `LEGACY_STEP`- und
`LEGACY_COMPLETION`-Buchungen, ohne Statistiken oder Komboprojektionen erneut zu erhöhen.
Anschließend entfernt sie die Rewardfelder aus den aktiven Tabellen und Domänenmodellen.
Dashboard und UI lesen eingesammelte beziehungsweise vergebene XP aus gebündelten
Ledger-Projektionen.

Schema 14 ergänzt `reward_assignments` als veränderliche Zuordnungsprojektion außerhalb des
Ledgers. Sie ordnet eine bestehende Buchung optional einem aktuell anderen Vorkommen zu. Fehlt
eine Zeile, bleibt `reward_bookings.occurrenceId` wirksam. Alle Reward-Abfragen projizieren die
effektive Occurrence mit `COALESCE(reward_assignments.occurrenceId,
reward_bookings.occurrenceId)`. Ein Schritttransfer ändert ausschließlich diese Zuordnung und
niemals Buchungs-ID, Transaktions-ID, Deltas, Owner oder ursprüngliche Occurrence der Ledgerzeile.

Schema 18 ergänzt `COMBO_DECAY` als unveränderliche Ledgerart. Sie enthält nur das tatsächlich
angewandte negative Kombodelta; die zugehörige Owner-/Datums-Auswertung liegt unabhängig davon
in `combo_decay_events`, damit auch eine Auswertung bei bereits null Punkten idempotent bleibt.
Welche echten Termine eine Auswertung erlauben, definiert
[ADR-024](adr-024-terminbewusste-kombos.md).

Schema 19 ergänzt den optionalen eingefrorenen vollen Planwert `plannedXp` und die Buchungsart
`STEP_ADJUSTMENT`. Mengen- und Korrektureingaben hängen damit vorzeichenbehaftete Differenzen an,
ohne frühere Buchungen oder nachträglich geänderte Komborichtlinien neu zu bewerten.

`RewardReceipt` enthält eine Transaktions-ID und die zugehörigen Buchungen. Seine XP- und
Kombowerte sind vorzeichenbehaftet; Animation und Darstellung leiten die Richtung aus dem
Vorzeichen ab. Ein separates Reverse-Flag existiert nicht mehr.

## Konsequenzen

Historie, Undo, Re-Completion, Schritttransfers und Restart sind nachvollziehbar, ohne fachliche
Belege zu überschreiben. Eine Widget-Komplettaktion kann mehrere Schrittbuchungen und die
Kopf-Buchung mit derselben Transaktions-ID atomar ausführen. Das Dashboard benötigt dafür eine
zusätzliche, aber gebündelte Ledger-Abfrage. Tests prüfen sowohl den effektiven neuen Besitzer als
auch die Byte-für-Byte unveränderten fachlichen Felder der ursprünglichen Ledgerbuchung.

Das Ledger ist noch kein vollständiges Event-Sourcing: Occurrence-Zustand, Gesamt-XP und Kombos
bleiben gespeicherte Projektionen. `CompletionService` koordiniert sie atomar und verwendet
dafür den reinen `RewardCalculator`, `CompletionStateMachine` und `ScheduleProjector`. Ein
späteres Reparaturwerkzeug müsste Projektionen dennoch explizit aus dem Ledger validieren oder
neu aufbauen; diese Reconciliation ist noch nicht implementiert.
