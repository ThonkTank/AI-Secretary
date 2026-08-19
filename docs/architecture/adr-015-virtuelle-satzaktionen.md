# ADR-015: Virtuelle Accessibility-Knoten für Satzkorrekturen

- Status: angenommen
- Datum: 2026-08-20

## Kontext

`SetBarsView` zeichnet alle gespeicherten und offenen Sätze in ein einziges Canvas. Für TalkBack
war die gesamte View deshalb nur ein Fokusziel mit mehreren benutzerdefinierten Aktionen.
Gespeicherte Sätze ließen sich zwar über das Aktionsmenü korrigieren, aber weder einzeln in
visueller Reihenfolge fokussieren noch mit einem normalen Enter-Klick bedienen. Eine Umstellung
auf echte Kind-Views hätte den kompakten 30-dp-Rhythmus, die horizontale Scrollbreite und damit
die bestehenden Fokus-Goldens verändert.

Die Stepper-Steuerung besaß außerdem verzögerte Repeat-on-hold-Callbacks, obwohl ihre
Dokumentation sie als zustandslos bezeichnete. Beim Detach wurden diese Callbacks nicht entfernt.

## Entscheidung

`SetBarsView` behält ihre Canvas-Darstellung und erhält über `ExploreByTouchHelper` für jeden
gespeicherten Satz einen virtuellen Button-Knoten. Die Knoten:

- folgen der sichtbaren Satzreihenfolge,
- nennen Satznummer und Ist-Wert sowie die Korrekturaktion,
- besitzen einen 44 × 44 dp großen Accessibility-/Hover-Bereich,
- führen `ACTION_CLICK` genau für ihren eigenen Slotindex aus,
- unterstützen Accessibility-Fokus, Tastaturfokus und Enter.

Touch auf der Canvas wählt weiterhin anhand des nächsten sichtbaren Satzmittelpunkts. Offene
Slots bleiben rein informativ und können keine Korrektur auslösen.

`RepStepperView` beschreibt ihren Repeat-Gesture-State nun ausdrücklich und entfernt beide
verzögerten Callbacks beim Detach. `SetBarsView`, `DewDotView` und der Fokus-Animationscontroller
beenden ihre laufenden Animationen ebenfalls bei Rebind beziehungsweise Detach. Beim Rebinding
einer aktiven als zukünftige Schrittzeile werden Click-Listener, Klickbarkeit, Fokusierbarkeit
und Editor-Controls deaktiviert.

## Konsequenzen

Canvas-Zeichnung, Messbreite und Goldens bleiben unverändert; Accessibility-Semantik ist nicht
mehr an eine wachsende Liste proprietärer Aktionen auf einem Sammelknoten gebunden. Der neue
`androidx.customview`-Baustein ist eine kleine zusätzliche Laufzeitabhängigkeit, kapselt dafür
Accessibility- und Tastaturfokus sowie Hover-Navigation konsistent mit Android.

Robolectric prüft die einzelnen virtuellen Nodes, ihre Reihenfolge, Mindestbounds, Klickziele und
Enter-Auslösung. Ein echter Accessibility-Scanner und eine manuelle TalkBack-Runde auf einem
Gerät bleiben als Releaseprüfung sinnvoll, da Hosttests keine gesprochenen Ansagen bewerten.
