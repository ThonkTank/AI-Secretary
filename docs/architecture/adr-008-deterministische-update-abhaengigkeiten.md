# ADR-008: Deterministische Update-Abhängigkeiten und Konfiguration

- Status: angenommen
- Datum: 2026-08-16

## Kontext

Der frühere Update-State-Owner erzeugte seinen Threadpool selbst und erhielt Zeit als unbenannten
`LongSupplier`. Seine Tests mussten deshalb nebenläufige Ergebnisse pollen und verwendeten
`Thread.sleep`. Der Produktionsadapter erzeugte seinen HTTP-Transport intern. Debug-Builds
unterdrückten zwar automatische Prüfungen in der `MainActivity`, konnten bei manueller Bedienung
aber unbeabsichtigt das echte GitHub-Repository anfragen. Außerdem lagen Update-Zeitstempel in
derselben Shared-Preferences-Datei wie Theme und Kalender.

## Entscheidung

Alle veränderlichen Laufzeitabhängigkeiten besitzen explizite Ports und werden am Composition
Root verbunden:

- `UpdateClock` liefert die Zeit für tägliche Prüfung und Verschiebung.
- `UpdateExecutorFactory` erzeugt pro `OptionsViewModel` genau einen lifecycle-eigenen
  `UpdateExecutor`; Produktion verwendet `SerialUpdateExecutor`, Tests einen direkten Executor.
- `GitHubUpdateRepository` erhält seinen `HttpTransport` im Konstruktor.
- `UpdateRepository`, `UpdatePreferences` und `UpdateInstaller` bleiben injizierte
  Abhängigkeiten ihrer jeweiligen Verbraucher.

`UpdateConfiguration` benennt drei Umgebungen. Produktion aktiviert Remote- und automatische
Prüfungen. Development – und damit der Debug-Build – verwendet unabhängig von der UI einen
`DisabledUpdateRepository` und kann keine echte Netzwerkanfrage auslösen. Testkonfigurationen
sind ebenfalls netzwerkfrei; einzelne Repositorytests injizieren ausschließlich einen
In-Memory-HTTP-Transport. Die Releasekonfiguration verwendet weiterhin exakt die Koordinaten aus
`release/release.properties`.

Updatezustand wird in `forest_updates` gespeichert. `SharedUpdatePreferences` übernimmt beim
ersten Start vorhandene Werte für letzte Prüfung und verschobene Version verlustfrei aus
`forest_ui`, schreibt zuerst das neue Ziel und entfernt die alten Schlüssel erst nach
erfolgreichem Commit. Theme- und Kalenderwerte bleiben unangetastet. Der echte CI-Upgrade-Test
prüft diese Migration von der vorherigen Produktionsversion.

## Konsequenzen

Updateabläufe laufen in Unit-Tests vollständig synchron und benötigen weder Polling noch
Wartezeiten. Eine ViewModel-Recreation erzeugt keinen ungenutzten Threadpool, weil der Factory-Port
den Executor erst in `ViewModelProvider.Factory.create` anlegt; `onCleared` schließt genau diese
Instanz geordnet, ohne bereits gestartete Update-I/O hart abzubrechen. Veraltete Resultate werden
durch die Workflowgeneration des Owners verworfen. Debug- und Robolectric-Anwendungsstarts bleiben garantiert offline, während der
Release-Build unverändert den produktiven GitHub-Kanal verwendet.

Die Sicherheitsgrenze des injizierten Netzwerkadapters, einschließlich Host-Trust-Policy,
Redirects, Retry/Backoff, Abbruch und atomischer Download-Finalisierung, legt ADR-009 fest.
