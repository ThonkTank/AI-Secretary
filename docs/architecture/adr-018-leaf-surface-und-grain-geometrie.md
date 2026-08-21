# ADR-018: Gemeinsame Blattoberfläche und lokale Grain-Geometrie

- Status: angenommen
- Datum: 2026-08-21

## Kontext

Header, Fokuskarte und Timeline-Blätter erzeugen Hintergrund, asymmetrische Blattform,
Transformation und Maserung bislang in getrennten Views. Eckradien werden für Drawable und
Grain-Clip doppelt angegeben. Grain-Anker werden nach dem Layout über eine beliebige
Elternhierarchie rekonstruiert, obwohl Rotation, Translation und Pivot nicht Bestandteil dieser
Rekonstruktion sind. Dadurch konnten Inhalt, Tau beziehungsweise Gefäß und Maserringe trotz
richtiger Einzelmaße gegeneinander verschoben oder unterschiedlich begrenzt sein.

## Entscheidung

Eine sichtbare Vorderseite wird künftig durch genau eine `LeafSurface` dargestellt.

- `LeafShape` ist die einzige Quelle der vier Eckradien. Dieselbe Instanz erzeugt Hintergrund,
  Clip und den Mittelpunkt einer ausgewählten Blattecke.
- `LeafSurface` besitzt Hintergrund, Grain, Schatten, Transformation und Frontinhalt. Rotation
  und Pivot werden auf den gemeinsamen Wrapper angewandt, nicht auf synchron zu haltende
  Geschwisterebenen.
- `GrainSpec` beschreibt entweder einen Corner-Fortschritt oder semantische Anchors mit
  Kombostufe. Anker- und Textrechtecke werden nach dem Layout ausschließlich in lokalen
  `LeafSurface`-Koordinaten erfasst.
- Externe `post()`-Ketten und das Hochlaufen durch beliebige View-Eltern gehören nicht zum
  Geometrievertrag. Pro gültigem Layout wird höchstens eine immutable Renderanfrage erzeugt.
- Die bestehende asynchrone SDF-/Marching-Squares-Pipeline und der gewichtete 4-MiB-Cache bleiben
  erhalten. Die Umstellung ist ein pixelidentischer Refactor und keine Designänderung.

## Konsequenzen

Blattform und Grain können nicht mehr unabhängig konfiguriert werden. Header-, Fokus- und
Timeline-Komponenten müssen ihren Frontinhalt unter derselben `LeafSurface` montieren. Reine
Geometrietests prüfen Eckmittelpunkte und lokale Bounds; Komponenten-Goldens bleiben das Gate
für den endgültigen Android-Renderstand.
