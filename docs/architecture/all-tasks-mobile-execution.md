# Ausführung: mobile Aufgabenverwaltung und Ablaufübersicht

Kanonische Grundlage: [Roadmap](all-tasks-mobile-roadmap.md) und
[ADR-034](adr-034-mobile-aufgabenverwaltung-und-ablaufmonitor.md)

Das Protokoll ist append-only. Lokale Implementierung, PR-Gate, Squash-Merge und Main-Nachweis
werden getrennt ausgewiesen.

## Phase 1 – Mobiler Alles-Katalog

Status: in Arbeit

Ausgangsstand: `1bab65fb` (`origin/main`)

Plan:

1. Präsentationszustand um den gespeicherten Filter-Offen-Zustand bereinigen und die
   Aufgabenprojektion um stabile Tageszeitüberschriften ergänzen.
2. Mobile Screen-Hierarchie, modales Sofortfilter-Blatt, kompakte Aufgabenblätter und direkten
   Editor-Einstieg umsetzen, ohne den Editor selbst zu verändern.
3. Sortierdarstellung auf eine aktive Einfügemarke reduzieren und bestehende Drag-/Randscroll-
   sowie Accessibility-Aktionen erhalten.
4. Laufende Runs auf eine höchstens zweizeilige Vorschau reduzieren; nur die bereits vom
   Today-State gelieferten, tatsächlich gestarteten Runs verwenden.
5. Zustands-, Projektions-, Golden-, Instrumentierungs- und Architekturtests aktualisieren;
   danach Roadmap-Audit, vollständige lokale Prüfung, PR und Remote-Gates.

Risiken und Gegenmaßnahmen:

- Filterblatt und Drag bleiben flüchtiger Compose-Zustand; der ViewModel-State bleibt einzige
  Quelle für Filter und Reihenfolge.
- Stabile Listen-/Platzierungs-IDs werden nicht aus sichtbaren Positionen abgeleitet.
- Eine kleine gemeinsame visuelle Primitive darf nur Präsentationswerte bündeln und keine
  fachliche Aktion besitzen.

Lokaler Implementierungs- und Anforderungsaudit am 2026-09-07:

- Suche, Ablaufvorschau, gleichgewichtete Filter-/Reihenfolge-Aktionen, Trefferzeile und
  Tageszeitgruppen bilden die vereinbarte mobile Hierarchie. Die geprüften Goldens umfassen
  320/412/600 dp, erhöhte Schrift, Nachtmodus, Filterblatt, Sortiermodus, Leerzustände und einen
  eigenen Zwei-Run-Fall.
- Das Filterblatt sendet jede Status-, Slot-, Rhythmus- und Wochentagsauswahl unmittelbar über
  die bestehende Action-Grenze. `filtersExpanded`, seine Action, sein Callback und der gespeicherte
  Bundle-Wert wurden entfernt; ein alter `filters_expanded`-Wert wird im Test bewusst ignoriert.
- Der Stift sendet die bestehende `EditTask`-Action. Editorpfade und Editorverhalten wurden nicht
  geändert; Löschen bleibt allein im Überlaufmenü.
- Listenzeilen erhalten stabile Slot-Überschriften. Schritt-/Zeitplatzierungs-Keys,
  Long-Press-Drag, Randscrollen, Drop-Mapping und Accessibility-Verschiebeaktionen bleiben
  erhalten. Permanente Zieltexte/-höhen entfallen; nur die dem Zeiger nächste Zeile zeichnet
  während des aktiven Drags eine Einfügemarke.
- Der Ablaufstreifen wird nur im Listenmodus gerendert, zeigt höchstens zwei kurze Run-Zeilen und
  konsumiert unverändert `todayState.today().flowRuns`. Kandidaten-, Run-, Domain-, Room- und
  `FlowRunsActivity`-Code wurden in dieser Phase nicht verändert.
- Header, Footer und Vollbild-Editor liegen außerhalb der Änderungsmenge. Ein Namensscan der
  geänderten Dateien bestätigt außerdem, dass weder Domainmodule noch Datenbankpfade betroffen
  sind.
- Fokussierte AllTasks-Zustands-, Dispatcher-, Architektur- und Golden-Tests: grün. Vollständiger
  lokaler Gate mit `testInstrumentationUnitTest lintDebug assembleDebug
  assembleInstrumentationAndroidTest assembleRelease`: auf dem finalen Quellstand grün in
  27 min 33 s. Artefakte:
  Debug-APK 5,5 MiB, Instrumentierungs-APK 1,6 MiB, unsigned Release-APK 2,8 MiB.
- Auf dem lokalen Host war kein Android-Gerät verbunden. Die API-/Animationsmatrizen bleiben
  deshalb dem verpflichtenden Pull-Request-Gate vorbehalten.

Remote-Status: wartet auf Commit, Pull Request, grüne Matrix, Squash-Merge und exakten
Main-Nachweis. Diese lokalen Ergebnisse allein schließen Phase 1 nicht ab.

Korrekturrunde PR #340:

- Der erste PR-Lauf bestand Quality sowie API 26 mit/ohne Animationen und API 35 ohne
  Animationen. Beide API-37-Jobs scheiterten ausschließlich im API-37-UIAutomator-Canary, weil
  dieser noch nach den ersetzten Texten „Sortieren“ und „Aufgaben“ suchte und das nicht gefundene
  Objekt ohne Prüfung anklickte.
- Der Canary sucht nun die freigegebenen sichtbaren Texte „Reihenfolge“ und „Fertig“ und prüft
  das gefundene Objekt vor dem Klick ausdrücklich. Produktcode und fachlicher Vertrag wurden in
  dieser Korrekturrunde nicht verändert. Der lokale Neubau der Instrumentierungs-APK war in
  17 s grün; anschließend muss die vollständige PR-Matrix erneut grün laufen.

Rebase- und Gate-Runde:

- Während des grünen PR-Laufs wurde `origin/main` durch PR #339 auf `94fdcbbc` weitergeführt.
  Der vorgeschriebene Merge blockierte deshalb korrekt; es wurde weder ein Admin- noch ein
  Auto-Merge verwendet.
- Der Themenbranch wurde konfliktfrei auf `94fdcbbc` rebasiert. Die neue Basis betrifft nur
  Schema-24-/Migrationspfade und überlappt nicht mit den Alles-Dateien.
- Der vollständige lokale Gate wurde auf dem kombinierten Baum erneut ausgeführt:
  `testInstrumentationUnitTest lintDebug assembleDebug assembleInstrumentationAndroidTest
  assembleRelease` war grün in 18 min 38 s. Vor dem Merge ist trotzdem eine vollständig neue
  PR-Matrix für den rebasierten Head erforderlich.

Remote-Abschluss Phase 1 am 2026-09-07:

- Die vollständig neue PR-Matrix für Head `f3f5aacd` war in Lauf `34162086456` grün: Quality,
  API 26/35/37 jeweils mit und ohne Animationen, Instrumentierungs-Gate und PR-Gate.
- PR #340 wurde ohne Admin-Ausnahme per Squash nach `main` gemergt. Der bestätigte Remote-Commit
  ist `c4c78a9e`.
- Der exakte Push-Workflow `34163431599` für `c4c78a9e` war grün. Paketbau, Upgrade von API
  26/35/37, Instrumentierungs-Gate und Veröffentlichung waren erfolgreich. Phase 1 ist damit
  vollständig abgeschlossen.

## Phase 2 – Mobile Ablaufdetailseite

Status: in Arbeit

Ausgangsstand: `c4c78a9e` (`origin/main`)

Plan:

1. `FlowRunsActivity` auf Host-Aufgaben begrenzen und ihren imperativen Seiten-/Kartenbau durch
   einen zustandslosen Foundation-Compose-Screen ersetzen. ViewModel, Screen State, Actions,
   Use Cases, Navigation, Toasts sowie Zeit- und Abbruchdialoge bleiben unverändert.
2. Eine mobile Einspalten-Hierarchie aus Zurück-Header, kurzer Einleitung und kompakten
   Ablaufkarten umsetzen. Jede Karte ordnet Seed, Aufgabe/Schritt, Fortschritt, Status,
   Ressourcen und die vorhandenen kontextabhängigen Aktionen.
3. Laden, Leerzustand und laufende Änderung sichtbar und zugänglich darstellen. Während einer
   Änderung werden alle konkurrierenden Kartenaktionen gesperrt.
4. Das erneut geprüfte, außerhalb von `FlowRunsActivity` ungenutzte `FlowRunningStripView`
   entfernen; benötigte reine Statusformatierung lokal in die neue Präsentationsschicht
   übernehmen.
5. Screen-State-/Action-Verträge, Host-Weiterleitung, Semantik, Interaktionen sowie mobile
   Goldens für Zustände, 320/412 dp, große Schrift und Nachtmodus ergänzen. Danach folgen
   Anforderungsaudit, vollständiger lokaler Gate, PR-Matrix, Squash-Merge und exakter
   Main-Nachweis.

Risiken und Gegenmaßnahmen:

- Compose besitzt nur flüchtige Darstellungsdetails; `FlowRunsViewModel` bleibt alleiniger
  Besitzer fachlicher Zustände und Nebenwirkungen.
- Dialoge benötigen die vollständige `FlowRunSummary`; der Host erhält deshalb typisierte
  Callbacks statt neue fachliche Action-Duplikate.
- Reihenfolgeaktionen bleiben exakt an die aktuell veröffentlichte Run-Liste gebunden. Stabile
  Run-IDs und bestehende `moveBefore`-Semantik werden nicht aus sichtbarem Text rekonstruiert.
- Die Phase ändert weder Aufgabeneditor noch Domain-, Use-Case-, Datenbank- oder
  Ablaufmaterialisierungslogik.

Verwendungsnachweis vor Produktcode: `FlowRunningStripView` wird produktiv nur noch von der
imperativen `FlowRunsActivity` für die Restzeitformatierung referenziert. Die Alles-Vorschau ist
bereits Compose-basiert; ein Charakterisierungstest stellt ausdrücklich sicher, dass dort kein
`FlowRunningStripView` mehr eingebaut ist. Nach dem Host-Cutover kann die Legacy-View daher ohne
zweiten Produktkonsumenten entfernt werden.

Lokaler Implementierungs- und Anforderungsaudit am 2026-09-08:

- `FlowRunsActivity` erstellt nur noch den Compose-Host, bindet den unveränderten Screen State,
  zeigt bestehende Toasts/Dialoge und leitet die vorhandenen Actions an das unveränderte
  `FlowRunsViewModel` weiter. Navigation, Zeitänderung und Abbruchbestätigung bleiben im Host.
- Der Foundation-Compose-Screen zeigt mobilen Zurück-Header, die gekürzte Einleitung sowie
  einspaltige Karten mit Seed, Aufgabenbezug, aktuellem Schritt, Fortschritt, Status,
  Ressourcen und allen kontextabhängigen Aktionen. Während `changing` sind sämtliche Aktionen
  sichtbar, semantisch deaktiviert und nicht klickbar.
- Laden, Leerstand, Änderung und Fehler besitzen eigene sichtbare Zustände. Ein Fehler ohne Runs
  wird nicht zusätzlich als leer bezeichnet; der bestehende Toast- und Acknowledge-Vertrag
  bleibt bestehen.
- Der erneute Verwendungsnachweis war eindeutig; `FlowRunningStripView` wurde entfernt. Die
  kompakte Restzeitformatierung liegt als reine Präsentationsfunktion am neuen Screen und ist
  mit Grenzwerttests abgedeckt.
- Die geprüften Goldens umfassen Standard, Änderung, Laden, Leerstand, Fehler, 320 dp, doppelte
  Schriftgröße und Nachtmodus. Alle wurden vor Aufnahme visuell geprüft und laufen anschließend
  im normalen Nur-Lese-Modus grün.
- Ein Diff-Audit bestätigt unveränderte `FlowRunsViewModel`-, `FlowRunsScreenState`-,
  `FlowRunsAction`-, Domain-, Use-Case- und Datenbankpfade. Auch der Aufgabeneditor liegt
  vollständig außerhalb der Änderungsmenge.
- Vollständiger lokaler Gate mit `testInstrumentationUnitTest lintDebug assembleDebug
  assembleInstrumentationAndroidTest assembleRelease`: grün in 20 min 42 s. Artefakte:
  Debug-APK 5,7 MiB, Instrumentierungs-APK 1,7 MiB, unsigned Release-APK 2,8 MiB.
- Ein Pixel 8 mit API 36 war verbunden, trägt aber die produktive App-ID. Der lokale
  Instrumentierungs-Build nutzt dieselbe App-ID und wurde deshalb nicht über die vorhandene
  Installation geschrieben. Die isolierten API-/Animationsläufe bleiben dem verpflichtenden
  PR-Gate vorbehalten.

Remote-Status Phase 2: wartet auf Commit, Pull Request, grüne Matrix, Squash-Merge und exakten
Main-Nachweis. Diese lokalen Ergebnisse allein schließen Phase 2 nicht ab.
