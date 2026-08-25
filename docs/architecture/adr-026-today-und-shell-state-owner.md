# ADR-026: Getrennte State-Owner für Today und App-Shell

- Status: angenommen
- Datum: 2026-08-25

## Kontext

Nach der Extraktion von Alles, Editor und Optionen verblieben Today, Top-Level-Navigation,
Darstellung, Timer, Rewards und Hostdialoge in einem gemeinsamen `TaskViewModel`. Der Owner
veröffentlichte einen aggregierten LiveData-Zustand sowie separate konsumierbare Event- und
Rewardkanäle. Leerer Zustand und Timeline-Menü umgingen den Today-Actionpfad über
Activity-Callbacks. Dadurch hing die Vollständigkeit der sichtbaren Oberfläche weiterhin von
Observerreihenfolge und Activity-Lebenszyklus ab.

## Entscheidung

`TodayViewModel` ist der einzige State-Owner des Today-Bildschirms. Es veröffentlicht
`StateFlow<TodayScreenState>` und verarbeitet ausschließlich `TodayAction`. Der atomare Zustand
enthält die einzige Todayprojektion im `TodayFeatureState`, Loading, laufende Commands,
Wiederholungseingabe, Fokusschrittlimit, Timer, die bestätigbare Rewardfolge und eine geordnete
Liste stabiler `TodayRequest`s.

Fehler, Hinweise, Timeline-Menü, Verschiebeauswahl, Lösch-/Schließbestätigung und
Timerberechtigungswarnung bleiben bis zur expliziten Antwort im Zustand. Der Requestadapter
rekonstruiert nur vollständig validierte Werte aus dem `SavedStateHandle`; IDs bleiben auch bei
unvollständigem Sequenzzustand monoton. Menüentscheidungen werden atomar konsumiert oder in den
nächsten Hostrequest überführt. Veraltete Callbacks können weder Fachwrites noch System-I/O
auslösen.

Top-Level-Auswahl und die globale Palette sind keine Today-Verantwortung. Bis zum Navigation-3-
Cutover besitzt deshalb ein kleiner `AppShellViewModel` den
`StateFlow<AppShellScreenState>`. Es beobachtet nur Darstellungs- und Uhränderungen und
persistiert die aktuelle Tab-Auswahl. Today beobachtet aus der gemeinsamen Preferencequelle nur
das für seine Projektion notwendige Fokusschrittlimit; Minutenticks lösen dort keinen
Preference-Read aus.

`MainActivity` bindet die StateFlows lifecyclegebunden und hostet Dialoge, Android-Permissions,
Animationen und die bestehende View-Hierarchie. Sie führt keine Today-Use-Cases aus.
`DashboardRenderer` erhält Shell- und Today-State getrennt und sendet aus dem Today-Baum nur
`TodayAction`. `TaskViewModel`, `DashboardUiState`, `DashboardEvent` und `UiEvent` entfallen.
Der in ADR-022/Phase 4c beschlossene Headerflug bleibt ein rein visueller Hook vor dem
typisierten `AppNavigator`.

## Konsequenzen

Activity-Recreation und Prozesszustandswiederherstellung verlieren offene Today-Hostarbeit nicht.
Screeninhalt, Requests und Rewards werden über einen StateFlow beobachtet; ein zweiter
LiveData-Zustand existiert nicht. Gestartete Writes werden beim Ownerende nicht hart abgebrochen,
während Latest-read-Pipelines weiterhin veraltete Reads verdrängen dürfen.

Die Java-Views und der aggregierende Renderer bleiben nur bis zu den jeweiligen Compose-
Cutovern bestehen. `AppShellViewModel` ist eine bewusste Zwischenarchitektur und wird in Phase 9
durch Navigation 3 ersetzt. Visuelle Gestaltung, Domainverhalten, Room-Schema und
Update-/Signaturvertrag ändern sich durch diese Entscheidung nicht. Die physische Geräteabnahme
bleibt ein separates offenes Gate.
