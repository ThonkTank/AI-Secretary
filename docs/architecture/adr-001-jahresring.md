# ADR-001: Jahresring und Routinefortschritt

- Status: angenommen
- Datum: 2026-08-15

## Kontext

Das Design ersetzt einen occurrence-basierten Streak durch einen Jahresring, dessen Zahl
Wochen ausdrückt. Das alte Schema enthält keine vollständige Wochenhistorie. Historische
Werte können deshalb nicht zuverlässig nachträglich rekonstruiert werden.

## Entscheidung

- Eine Routine erhält pro Kalenderwoche höchstens einen Ringabschnitt.
- Eine Woche zählt, sobald mindestens eine in dieser Woche fällige Ausführung am geplanten
  Tag abgeschlossen wurde.
- Weitere pünktliche Abschlüsse derselben Woche erhöhen den Ring nicht erneut.
- Eine verspätet abgeschlossene fällige Ausführung beendet die aktuelle Wochenserie.
- XP und das dauerhaft erreichte Routine-Level werden durch einen Serienabbruch nicht
  reduziert.
- Kalenderwochen beginnen montags in der lokalen Zeitzone der Anwendung.
- Unbekannte historische Wochen werden nicht erfunden. Ein vorhandener positiver alter
  Streak darf bei einer Migration höchstens als bereits begonnene Woche übernommen werden.

## Konsequenzen

Die Fortschrittsberechnung benötigt den geplanten und den tatsächlichen Abschlusstag sowie
die zuletzt gezählte Kalenderwoche. Mehrere Abschlüsse innerhalb einer Woche müssen
idempotent sein. Datums- und Zeitzonengrenzen werden explizit getestet.
