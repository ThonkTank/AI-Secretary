# ADR-025: Mengenbasierte Schritt-Rewards und Tagesabschluss

- Status: angenommen
- Datum: 2026-08-25
- Owner: Reward- und Today-Domäne
- Source of Truth: `StepExecutionService`, `SettlePreviousPartialOccurrences` und Room-Schema 19

## Kontext

Wiederholungs- und Satzschritte vergaben bislang erst beim binären Abschluss den gesamten
Schritt-Reward. Tatsächliche Mengen, Teilfortschritt und Korrekturen hatten dadurch keine
fachliche Auswirkung. Zugleich konnte der Tageswechsel positive Arbeit als ungeerntet behandeln.

## Entscheidung

Für einen Wiederholungsschritt ist die Quote `tatsächliche Wiederholungen / geplante
Wiederholungen`. Für Sätze ist sie `Summe der tatsächlichen Wiederholungen / (Sätze × geplante
Wiederholungen)`. Bei einer positiven Menge wird der Zielwert als
`ceil(eingefrorene volle Schritt-XP × Quote)` berechnet. Er ist nicht auf den Planwert begrenzt;
Übererfüllung wird deshalb vollständig belohnt. Eine Menge null ergibt null XP.

Die erste Mengenbuchung friert die zu diesem Zeitpunkt geltenden vollen Schritt-XP in
`reward_bookings.plannedXp` ein. Jede spätere Eingabe oder Korrektur bucht nur die
vorzeichenbehaftete Differenz zum neuen Zielwert als `STEP_ADJUSTMENT`. Bereits gebuchte Historie
wird nicht umgeschrieben. Positive Teilnahme erhält den konfigurierten Kombogewinn genau einmal,
unabhängig von der Höhe der Menge. Eine Korrektur auf insgesamt null nimmt diesen Gewinn wieder
zurück. Ein explizites Null-Ergebnis löst trotzdem die Terminverpflichtung.

„Für heute abschließen“ beendet einen Mengenschritt mit den tatsächlich gespeicherten Werten;
fehlende Slots werden nicht als Nullergebnisse erfunden. „Rest erledigen“ bewahrt vorhandene
Ist-Werte und ergänzt nur fehlende Slots mit dem jeweiligen Planwert.

Beim Tageswechsel werden positive Teilmengen alter offener Vorkommen automatisch mit ihren
Ist-Werten abgeschlossen. Bereits positive Gefäß-XP werden anschließend automatisch geerntet.
Null- und unberührte Arbeit erzeugt dadurch weder XP noch Kombo. Der Ablauf ist idempotent.

Das Gefäß zeigt die tatsächlich erntbaren XP. Sein Wasserstand verwendet dagegen die Summe der
Ist-Schritt-XP relativ zur Summe der eingefrorenen beziehungsweise noch projizierten Plan-XP und
ist bei 100 Prozent gedeckelt.

## Migration und Kompatibilität

Schema 19 ergänzt ausschließlich die nullable Spalte `plannedXp`. Bestehende Ledgerbuchungen,
Gesamt-XP und Kombostände bleiben unverändert. Für noch nicht eingefrorene Altbuchungen verwendet
die Projektion den aktuellen vollen Schrittwert als Fallback.

## Konsequenzen

Mengenarbeit wird sofort, korrigierbar und auch oberhalb des Plans sichtbar. Routine-Ernten
verwenden weiterhin die Summe der Schritt-XP und wenden anschließend den Aufgabenfaktor an.
Automatisches Lernen aus historischen Mengen bleibt ausdrücklich ein späteres Analysefeature;
es ändert weder Planung noch Ziele ohne eine eigene Entscheidung.

## Lokale Nachweise

- Fachlogik: `core-domain/src/main/java/de/thonktank/autosecretary/domain/usecase/StepExecutionService.java`
- Tageswechsel: `core-domain/src/main/java/de/thonktank/autosecretary/domain/usecase/SettlePreviousPartialOccurrences.java`
- Persistenz: `app/src/main/java/de/thonktank/autosecretary/data/local/DatabaseMigrations.java`
- Akzeptanzfälle: `app/src/test/java/de/thonktank/autosecretary/XpComboRobolectricTest.java`
