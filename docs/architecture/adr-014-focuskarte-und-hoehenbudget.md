# ADR-014: Fokuskarte mit explizitem Höhenbudget

- Status: angenommen
- Datum: 2026-08-20

## Kontext

`FocusTaskView` war gleichzeitig Composition Root, Inhaltsrenderer, dynamischer
Zeilenalgorithmus, Reward-Registry, Grain-Geometrieberechnung und Animationscontroller. In
`onMeasure()` änderte sie Sichtbarkeiten, maß die gesamte Karte wiederholt und maß das Kind
zusätzlich mit unbeschränkter Höhe. Oberfläche und Grain wurden anschließend über zwei
verkettete `post()`-Aufrufe an die Inhaltshöhe angeglichen. Dadurch war ein Zwischenframe mit
veralteter Geometrie möglich und die Layoutentscheidung ließ sich nur über die gesamte
Robolectric-View testen.

## Entscheidung

`FocusTaskView` ist nur noch die Android-Kompositionshülle. Sie bestimmt das verfügbare
Höhenbudget, verbindet die Komponenten und erkennt einen Fokuswechsel.

- `FocusCardView` erhält mit `FocusCardUiModel` immer einen vollständigen Render-Snapshot. Sie
  bindet Titel, Gefäß beziehungsweise Dew, Schritteliste und Aktionen. Ihre Messung reserviert
  zuerst den Platz für Kopf und Aktionen und übergibt ausschließlich den verbleibenden Platz an
  die Schritteliste.
- `FocusStepListLayout` garantiert die aktive Zeile und nimmt folgende Zeilen in Quellreihenfolge
  auf. Das `AT_MOST`-Höhenmaß ist ein hartes Budget für aktive Zeile, Folgeschritte und
  Resthinweis. `FocusStepLimit` ist lediglich eine Obergrenze. Die reine
  `FocusStepLayoutPolicy` entscheidet anhand gemessener Teilhöhen über die Zahl sichtbarer
  Folgeschritte.
- `FocusCardDecoration` besitzt Papierstapel, Oberfläche, Grain und Reward-Registrierung. Sie
  liest die endgültigen Bounds erst im Layoutdurchlauf; diese Geometrie beeinflusst weder
  Inhaltsbindung noch die Auswahl sichtbarer Schritte.
- `FocusCardAnimationController` besitzt Glint, Afterglow und Fokuswechselanimation.

Oberfläche und Grain werden im selben `onLayout()`-Durchlauf direkt auf die fertigen
Kartenbounds gelegt. Die früheren Synchronisations-`post()`-Ketten entfallen. Größen, die für
Kartenabstände, Paddings, Mindesthöhen und Schrittabstände den Messvertrag beeinflussen, liegen
als benannte Dimensionen in `dimens.xml`.

## Konsequenzen

Die dynamische Layoutpolitik ist ohne Android-Rendering testbar. Kleine Viewports dürfen unter
einer numerischen Präferenz weniger Zeilen anzeigen; größere Viewports überschreiten die
Präferenz nicht. `FocusTaskView.onMeasure()` verändert keine Kind-Sichtbarkeit und misst keine
Kinder manuell oder wiederholt.

Die Komponenten bleiben vorerst im historischen Root-Paket, weil der Refactor weiterhin ein
einzelnes Android-Modul verwendet. Der bestehende öffentliche `FocusTaskView.bind`-Einstieg
bleibt als Kompatibilitätsgrenze erhalten. Die drei Fokus-Goldens bleiben pixelidentisch; die
Layout-/Accessibility-Matrix umfasst 320, 412 und 600 dp, Font Scale 1.0, 1.3 und 2.0 sowie die
Morgen-, Abend- und Nachtpalette.
