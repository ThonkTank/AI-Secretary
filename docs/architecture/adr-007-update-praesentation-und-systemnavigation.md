# ADR-007: Update-Präsentation und Android-Systemnavigation

- Status: angenommen
- Datum: 2026-08-16

## Kontext

Nach der fachlichen Schichtentrennung beobachtete die `MainActivity` zwar bereits
`UpdateUiState` und `UpdateEvent`, interpretierte die Updateeffekte aber weiterhin selbst. Sie
baute Dialoge, formatierte die APK-Größe, prüfte die Berechtigung für unbekannte
Installationsquellen und erzeugte Installer-, Einstellungs- sowie GitHub-Navigation. Damit war
die Activity zugleich Lifecycle-Host, Update-Präsentationslogik und Android-Adapter. Besonders
der Berechtigungs-Rückweg war nur zusammen mit der Activity testbar.

## Entscheidung

Der Updatebildschirm verwendet ausdrücklich die Kombination aus einem langlebigen Zustand und
einmaligen Effekten:

- `UpdateUiState` ist der vollständig renderbare Zustand für den Optionsbereich.
- `UpdateEvent` ist ein konsumierbarer, einmaliger Effekt für Angebot, Installation oder Fehler.
- `UpdateUiController` koordiniert diese Effekte und alle zugehörigen Benutzerentscheidungen.
- `UpdateFlow` beschreibt die vom Controller benötigten Aktionen und Beobachtungen; das
  `UpdateViewModel` implementiert diesen Port.
- `UpdateDialogs` kapselt Update-Dialoge einschließlich Textformatierung und Callbacks.
- `UpdatePlatform` kapselt Installationsberechtigung, Android-Paketinstallation,
  Einstellungsnavigation und den externen Release-Link.

Die konkreten Adapter `AndroidUpdateDialogs` und `AndroidUpdatePlatform` liegen im
Activity-nahen UI-Rand des Root-Pakets. Das ist bewusst der Composition Root: Er darf Android,
Presentation und Infrastructure verbinden, ohne eine Rückabhängigkeit aus einer inneren
Update-Schicht zu erzeugen. Der Controller selbst kennt keine Android-UI-API und wird mit
In-Memory-Ports getestet.

`MainActivity` erzeugt die Adapter, beobachtet Zustand und Effekte, rendert den Zustand und
delegiert Lifecycle-, Menü- und Activity-Result-Signale. Sie kennt weder `UpdateEvent`-Typen noch
`UpdateInfo`, `VerifiedUpdate`, Installer-Intents, Berechtigungsregeln, Update-Dialoge oder
Größenformatierung. Ein Architekturtest schützt diese Grenze.

## Konsequenzen

Das sichtbare Verhalten bleibt unverändert: Updateangebote können angenommen oder verschoben
werden; ein verifiziertes APK öffnet den Android-Installer; fehlende Berechtigung führt in die
passende Systemeinstellung; Fehler bieten weiterhin den GitHub-Releasekanal an. Die
Plattformentscheidungen sind unabhängig von einer Activity testbar, während die vorhandenen
Robolectric-Tests weiterhin die konkreten Installer-Intents prüfen.

Die zunächst nur boolesch injizierte Debug-/Produktionsentscheidung sowie Executor und Uhr wurden
mit [ADR-008](adr-008-deterministische-update-abhaengigkeiten.md) durch benannte Konfigurations-
und Laufzeitports ersetzt. `UpdateEvent` verwendet weiterhin ein Consume-Flag; ein späterer
Wechsel auf einen dedizierten Effect-Stream ist nicht erforderlich, solange Recreates und
Mehrfachbeobachtung durch Tests abgesichert bleiben.
