# ADR-019: Dauer-Schritte und Satzpausen

## Entscheidung

Timer gehören zu einem konkreten Occurrence-Schritt. Ein Dauer-Schritt verwendet weiterhin
`StepAmount.Duration`; der Benutzer startet seinen Countdown in der Today-Fokuskarte und schließt
den Schritt nach Ablauf weiterhin manuell ab. Ein `SETS_REPS`-Schritt startet nach jedem
gespeicherten, noch nicht letzten Satz eine Pause. Korrekturen vorhandener Satzwerte starten keine
Pause. Während einer laufenden oder pausierten Satzpause ist die Eingabe des nächsten Satzes
gesperrt; die Pause kann jederzeit übersprungen werden.

Die Pausenlänge ist pro Schritt `INHERIT`, `CUSTOM` oder `OFF`. Neue und migrierte
`SETS_REPS`-Schritte erben die globale Options-Vorgabe von 60 Sekunden. Andere Mengenarten sind
immer `OFF`. Dauerwerte werden im Editor als Minuten und Sekunden eingegeben.

## Laufzeitvertrag

Jeder Countdown wird als unabhängige `timer_sessions`-Zeile in Room gespeichert. Mehrere Schritte
können parallel laufen; ein zweiter Start desselben Timer-Typs auf demselben Schritt ist
idempotent. Laufende Timer verwenden `elapsedRealtime` im aktuellen Boot und zusätzlich eine
Epoch-Deadline zur Rekonstruktion nach Prozessende, Paketaktualisierung oder Neustart. Pause,
Fortsetzen, Zurücksetzen und Überspringen werden ebenfalls persistiert.

Android `AlarmManager` übernimmt das Ende außerhalb des Vordergrunds. Exakte Alarme werden genutzt,
wenn die Sonderfreigabe vorhanden ist; ansonsten wird ein weniger genauer Alarm geplant und im
Vordergrund weitergezählt. Das Ende erzeugt eine High-Importance-Benachrichtigung mit Ton und
Vibration sowie einmalig eine Animation an der Schrittzeile. Es gibt bewusst keine Sprachausgabe.

Fehlende Benachrichtigungs- oder Exaktalarm-Freigaben verhindern weder App-Start noch Timer-Start.
Die App weist auf die eingeschränkte Zuverlässigkeit hin und bietet die Android-Freigaben an.
Ein abgelaufener Timer schließt fachlich niemals selbst einen Schritt ab.

## Persistenz

Schema 17 ergänzt `restTimerMode` und `restTimerSeconds` an Definitionen und Occurrence-Snapshots
sowie die Tabelle `timer_sessions`. Die Migration setzt bestehende `SETS_REPS`-Schritte auf
`INHERIT` und alle anderen auf `OFF`.
