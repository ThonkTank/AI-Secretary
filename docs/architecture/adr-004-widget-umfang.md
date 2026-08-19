# ADR-004: Widget-Funktionsumfang

- Status: angenommen
- Datum: 2026-08-15

## Kontext

Das Widget ist der schnelle Arbeitsbereich. RemoteViews und die verfügbare Fläche begrenzen
jedoch Inhalt und Interaktion.

## Entscheidung

Gemeinsam für alle Größen:

- Tippen auf Fläche oder Titel öffnet das Dashboard.
- Tau-Aktionen ändern einen Schritt unmittelbar.
- Wo der Größenausschnitt eine Primäraktion zeigt, schließt sie den aktuellen Block
  unmittelbar ab.
- Das Plus öffnet den Editor in der App.
- Eine endgültige Bedingungs- oder Verlustaktion wird in der App bestätigt.
- Ein Widget-Refresh ist lesend; fachliche Materialisierung wird getrennt ausgelöst.

Größenbezogener Ausschnitt:

| Größe | Inhalt |
| --- | --- |
| Small / 2×2 | Marker, Fokustitel, kompakter Fortschritt, Primäraktion |
| Wide / 4×2 | Fokus und bis zu drei Schritte mit kompakter Mengen-/Notizzeile und Restzahl |
| Tall / 3×4 | Fokus, bis zu drei Schritte mit kompakter Mengen-/Notizzeile, Primäraktion, nächster Kalendertermin |
| Large / 4×4 | Fokus, bis zu drei Schritte mit kompakter Mengen-/Notizzeile, danach-Blatt, nächster Kalendertermin, Plus |

Alle bedienbaren Flächen sollen mindestens 48 dp erreichen. Wo ein Launcher die effektive
Widgetfläche kleiner liefert, wird Inhalt reduziert statt das Touchziel weiter zu
verkleinern.

## Konsequenzen

Ein gemeinsames Widget-UI-Modell wird größenabhängig projiziert. Daten werden pro
Updatezyklus einmal geladen und anschließend für alle Widget-IDs gerendert.
Schritttitel und Metadaten stammen aus demselben präsentationsfertigen Modell wie im
Dashboard; das Widget formatiert keine fachlichen Mengen. Die vollständige Kombination aus
Titel und Metadaten bleibt auch bei visueller Kürzung als Inhaltsbeschreibung verfügbar.
