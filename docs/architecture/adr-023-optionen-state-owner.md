# ADR-023: Ein State-Owner für Optionen, Berechtigung und Updater

- Status: angenommen
- Datum: 2026-08-25

## Kontext

Der Optionenbildschirm wurde aus drei zeitlich unabhängigen Pfaden gerendert: Theme,
Fokuslimit, globale Pausentimer-Vorgabe, Kalenderstatus und Palette lagen im breiten
Dashboardzustand, der Updateablauf in einem separaten LiveData-ViewModel und Dialoge
beziehungsweise Installationsrückwege in einem Activity-Controller mit konsumierbaren Events.
Recreation zwischen Eventpublication und Konsum konnte Hostwork verlieren oder von der gerade
sichtbaren Renderprojektion trennen.

## Entscheidung

`OptionsViewModel` ist der einzige Presentation-State-Owner des Optionenbildschirms. Es
veröffentlicht ein `StateFlow<OptionsScreenState>` mit Darstellungspräferenzen, Palette,
Pausentimer-Vorgabe, Kalenderberechtigung und -status, `UpdateUiState` sowie einer geordneten
Liste `OptionsRequest`. `dispatch(OptionsAction)` ist der einzige serielle Eingang.

Preferences und Kalender werden aus den gemeinsamen typisierten Invalidierungsquellen
projiziert. Datenbankänderungen lösen im Optionenowner keinen Read aus. Der Todayowner darf
Kalenderereignisse und Fokuslimit weiterhin als eigene, aus denselben dauerhaften Quellen
abgeleitete Projektion besitzen und die globale Pausentimer-Vorgabe beim Start eines Resttimers
direkt aus der dauerhaften Quelle lesen; die globale Legacy-Palette liegt seit ADR-026 im
Shellowner. Today enthält keinen Optionen- oder Updatestatus. Der laufende
`TimerManager.Snapshot` bleibt Today-Laufzeitzustand und gehört nicht zum Optionenowner.

Updateangebot, Installation, Fehler und Android-Systemnavigation sind bestätigbare Requests mit
stabilen IDs. Sie werden unmittelbar im `SavedStateHandle` gespeichert und erst nach einer
expliziten Hostantwort entfernt. Angebotannahme, Aufschub und Installerfehler konsumieren atomar
genau den referenzierten passenden Request; verspätete Dialogcallbacks bleiben wirkungslos.
Metadaten und ein noch vorhandenes verifiziertes APK werden über eine validierende Bundle-Grenze
rekonstruiert. Ein bei Prozessverlust laufender, noch nicht verifizierter Download wird nicht als
scheinbar laufend restauriert und kann sicher neu angestoßen werden.

`MainActivity` bleibt Host für Runtime-Permission, App-Einstellungen, Dialogadapter,
Installationsberechtigung und Systeminstaller. Sie rendert ausschließlich den ersten Request und
bestätigt das erledigte Hostwork; sie schreibt keine Optionsdaten in den Dashboardowner. Der
frühere `UpdateEvent`-/`UpdateFlow`-/`UpdateUiController`-/`UpdateViewModel`-Pfad entfällt.

## Konsequenzen

Activity- und Prozess-Recreation verlieren offene Optionenaufträge nicht. Doppelte Checks,
Downloads und Installationsaufträge werden abgewiesen oder semantisch dedupliziert; asynchrone
Resultate tragen eine Workflowgeneration. Der Updateexecutor wird beim Ownerende geordnet
geschlossen, damit begonnene I/O nicht hart unterbrochen wird, während Resultate eines bereits
beendeten Owners nicht mehr publiziert werden.

Die Java-View und die Androidadapter bleiben bis zum Compose-Cutover in Phase 7 erhalten. Der
Releaseabschluss richtet sich nach
[ADR-030](adr-030-minimale-trainingsarchitektur-und-automatisierter-abschluss.md); diese
Entscheidung führt keinen eigenen nachgelagerten Gate-Zustand.
