# ADR-038: Früher Diagnose-Bootstrap und sichere Android-Einstiege

- Status: angenommen mit P3 der Recovery-Remediation-Roadmap; Freigabenachweise im Ausführungsprotokoll
- Datum: 2026-09-12
- Bezug: [Roadmap P3](recovery-remediation-roadmap.md), [Ausführung](recovery-remediation-execution.md)

## Entscheidung

`DiagnosticProbeInstrumentation` ist der unterstützte lesende Diagnosezugang. Er aktiviert
`DiagnosticBootstrap.enterForDiagnosis(1)` in `newApplication`, bevor die Application erzeugt
und Provider initialisiert werden. Der Vertrag bleibt durch `@Keep` im Release-APK erhalten.
Die Auswahl hängt von der ausdrücklich gewählten Instrumentierungskomponente ab, nicht von
den erst später verfügbaren Argumenten. `upgradePhase=diagnose` am historischen Runner ist
kein unterstützter Diagnosezugang mehr.

Der frühere `callApplicationOnCreate`-Check kam nach Providern. Die Reihenfolge ist in den
Android-Quellen für [API 26](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r36/core/java/android/app/ActivityThread.java)
und [Android 16](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/core/java/android/app/ActivityThread.java)
nachvollziehbar und wird zusätzlich in frischen nativen Prozessen geprüft.

Fehlt der Vertrag oder kann er nicht früh aktiviert werden, sendet der Runner ausdrücklich
`diagnosticUnsupported` und verhindert die anschließende App-Erzeugung. Eine solche Ablehnung
ist kein erfolgreicher Diagnosebericht. Insbesondere erhalten bereits veröffentlichte APKs
bis einschließlich 0.2.173 durch einen neuen Helper keinen nachträglichen Schutzvertrag.
Die historische `UpgradeProbeInstrumentation` bleibt für `seed`/`verify` mit alten APKs geeignet.

## Geschützte Arbeit und verbleibende Framework-Initialisierung

Der Diagnosemodus gilt nur für diesen Prozess. Die Application baut keinen AppContainer auf,
und `DatabaseFactory` verweigert den produktiven Room-Einstieg. Boot-/Paketwechsel-, Timer- und
Task-Receiver kehren vor asynchroner Facharbeit zurück. Der WidgetProvider fängt die Ereignisse
vor dem Framework-Callbackdispatch ab. Activities beenden ihre Erzeugung vor fachlichen
ViewModels sicher. Der echte FlowWakeWorker gibt `retry` zurück, bevor er den Container verwendet.
Benutzeraktionen werden weder gespeichert noch zur späteren Wiederholung vorgemerkt.

FileProvider sowie AndroidX Startup mit WorkManager, EmojiCompat, ProcessLifecycle und
ProfileInstaller bleiben initialisiert. WorkManager einfach abzuschalten wäre falsch:
Der aufgelöste `SystemJobService` aus WorkManager 2.11.2 setzt die Initialisierung bei dieser
Application voraus und würde andernfalls in `onCreate` werfen. WorkManagers eigene Verwaltungs-
daten und Framework-Caches können geschrieben werden. Die Zusicherung betrifft Schema und
Nutzdaten in `auto_secretary.db`, einschließlich Timer, Historie, Platzierung, Belohnungen und
Wiederherstellungsarchiv; sie ist keine Behauptung vollständiger Dateisystemruhe.

Es gibt keine gespeicherte Moduspräferenz, dauerhaft deaktivierte Komponente oder erforderliche
Geräteeinstellung. Das unterstützte Kommando benutzt kein `--no-restart`. Das reguläre Ende der
Instrumentierung beendet den Prozess; der CLI prüft dessen Ende. Der nächste normale Start baut
die App regulär auf, gleicht Timer ab und lässt zurückgestellte Systemarbeit weiterlaufen.

## Kompatibilität und Bedienung

| Kombination | Vertrag |
| --- | --- |
| Neuer Diagnose-Runner, App mit Bootstrap 1 | lesender Bericht ohne Room-Migration oder Facharbeit |
| Neuer Diagnose-Runner, App ohne Bootstrap 1 | erkennbare Ablehnung vor App-/Provider-Erzeugung |
| Historischer Runner mit `seed`/`verify` | bestehender signierter Upgradevertrag |
| Historischer Runner mit `diagnose` | nicht unterstützt; neuen Diagnosezugang verwenden |
| Isolierte Fixture-/Normal-Runner | nur Testpaket im Emulator; nicht im Produktionshelper deklariert |

Nach Installation des zur App passenden regulär signierten Helpers:

```sh
python3 scripts/release/diagnostic_probe.py --serial SERIAL --report-file diagnosis.json
```

Der CLI sät keine Daten, installiert nichts und ändert keine Komponenten. Exit 0 bedeutet
unterstützte Diagnose mit Bericht und beendetem Prozess; Exit 4 bezeichnet eine explizit nicht
unterstützte Kombination. Ein unbekannter Absturz oder fehlendes Ergebnis ist ein Fehler.
Der Bericht enthält nur Version/Identität und aggregierte Datenbankdiagnose, keine Nutzdatenpayloads.
Nach der Geräteprüfung wird ausschließlich der Helper entfernt und die App normal gestartet.
Ein beschädigter Altbestand wird dadurch nicht als repariert behauptet; Reparaturen bleiben
Vorwärtsupdates über die vorhandene Installation.

## Nachweise und Grenzen

`run-diagnostic-lifecycle.py` prüft vor Rootzugriff, Installation oder Fixture-Schreiben das
isolierte Emulatorziel. Die Java-Fixtures prüfen zusätzlich Testpaket und Emulatorhardware.
Schema 24 belegt fehlende Migration; Schema 27 enthält auch Archiv und explizite Today-Platzierung.
Vollständige typisierte Daten- und Schemasnapshots werden vor/nach Diagnose verglichen.

Echte Boot-, Paketwechsel-, Timer-, Widget- und Activity-Ereignisse werden während einer gehaltenen
Diagnose zugestellt. Das Optionsereignis verwendet die vom Framework akzeptierte null-Options-
Eingabe; die Präsenz beider Extra-Schlüssel erreicht den tatsächlichen Optionscallback. Der Treiber
prüft die unveränderte PID vor und nach jeder Zustellung. Ein ausschließlich debugseitiger Provider
belegt den frühen Modus nach AndroidX Startup, bindet über Android den tatsächlich registrierten
SystemJobService und lässt WorkManager einen echten FlowWakeWorker ausführen. Die Servicebindung
beweist dessen Erzeugung; sie wird nicht als ausgelöster JobScheduler-Job ausgegeben.

Reguläres Ende und absichtlicher Prozessabbruch werden für beide Schemata getrennt geprüft.
Der folgende normale Prozess muss Timerabgleich und Worker-Erfolg sowie erhaltene Fachdatensätze
belegen. Eine vorherige normale Kontrollausführung prüft die Erwartungen der synthetischen Daten.
Die normalen vollständigen CI-Lanes führen diese Folge auf API 26/35/37 aus; die Animations-Lanes
behalten ihren Bedienkorpus. Im aktuellen signierten Upgrade-Lane wird der vorherige Stand klar
als unterstützt/nicht unterstützt eingeordnet; der neue Kandidat muss den Diagnosevertrag erfüllen.
Historische Seed-/Verify-Lanes bleiben unverändert erforderlich.

Ein generischer Prozessabsturz gilt niemals als erlaubte Kompatibilitätsablehnung. Identitätsprüfungen
kontrollieren jedes deklarierte Runner-/Zielpaar: vier Komponenten beim isolierten Helper, zwei
beim Produktionshelper. Tatsächlich bestandene lokale, native, PR-, Main-, Release- und Pixel-
Nachweise werden getrennt im Ausführungsprotokoll geführt. Ohne verfügbares Pixel bleibt dessen
lesende Diagnose und sichtbare Abnahme ausdrücklich offen.
