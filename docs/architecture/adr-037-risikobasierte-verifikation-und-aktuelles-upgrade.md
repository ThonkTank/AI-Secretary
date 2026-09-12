# ADR-037: Risikobasierte Verifikation und aktuelles signiertes Upgrade

- Status: angenommen mit der Recovery-Remediation-Roadmap; wirksam nach geprüftem P2-Merge
- Datum: 2026-09-12
- Ergänzt: ADR-005 und ADR-035 bezüglich Prüfauswahl und Wiederverwendung

## Entscheidung und Grenze

Die Titel-Absturzregression lag in der plattformfreien Aktionsweiterleitung. Weder historische
Schema-Upgrades noch zusätzliche Android-Versionen ersetzen einen Test dieses Bedienwegs.
P1 prüft deshalb echte Renderer-/ViewModel-/Room-Wirkung auf API 26/35, Persistenz nach Neuöffnung
und einen nativen Touch-/Accessibility-Fall. Gegenproben erkennen den historischen Kontrollfluss,
falsche Ziel-ID und fehlende Speicherung. Auf dieser Grundlage ersetzt eine enge Positivliste
die pauschale Vollprüfung für genau diese Aktionsweiterleitung. Das ist keine Aussage, dass
Plattform- oder historische Upgradeprüfungen allgemein unnötig wären.

`scripts/ci/change_scope.py` ist die gemeinsame, versionierte Quelle für vier Profile:

| Profil | Zulässige Änderungen | Lokaler Vorabcheck | Obligatorischer PR-Gate |
| --- | --- | --- | --- |
| `docs` | ausschließlich Dokumentation | Python-Dokument-/Repositoryverträge | Policy-Zeuge und Dokumentverträge |
| `host` | ausschließlich Hosttests | komplette Hostsuite einschließlich Goldens und Python-Verträge | gleiche Hostsuite |
| `today` | ausschließlich geänderte bestehende `TodayCoordinator.java`, `TodayCommandDispatcher.java` und direkt zugehörige Hosttests | schnelle Contracts einschließlich P1 | Contracts, Lint, Builds, Identität, Größe und native Today-Suite auf API 35 mit Animationen |
| `full` | Speicherung, Migrationen, weitere Produktdateien, Android-Harness, Plattform/Lifecycle, Manifest, Abhängigkeiten, Build, Signierung, CI/Freigabe sowie unbekannte/gemischte Änderungen | vollständiger `check-all.sh` | vollständige Contracts/Goldens/Builds und sechs normale/animierte API-26/35/37-Lanes |

Die genaue Today-Dateiliste liegt im Classifier. Neue Produktionsdateien und strukturelle
Änderungen wie Umbenennung, Löschung oder Typwechsel fallen auf `full` zurück. Reine neue
Hosttests bleiben `host`; gemischte Today- und nicht zugehörige Teständerungen werden `full`.
Git-Status und lokale unversionierte Dateien werden berücksichtigt. Fehlende Diffgrundlage
führt zur Vollprüfung. Fehler bei Klassifikation oder Gate blockieren den Abschluss.

`./scripts/ci/check-changes.sh [BASIS]` klassifiziert den lokalen Arbeitsstand gegen die angegebene
Basis, standardmäßig `origin/main`. `--all` erzwingt die Vollprüfung; `check-all.sh` bleibt direkt
nutzbar. Der lokale Today-Vorabcheck enthält keinen behaupteten lokalen Lint-/APK-/Gerätelauf;
diese zusätzlichen Nachweise erbringt der obligatorische PR-Gate. P2 selbst ist eine vollständige,
releasewirksame Änderung und darf sich nicht durch die eingeführte Regel herunterstufen.

## Wiederverwendung und Veröffentlichung

Der stabile `pull-request-gate` und seine Aggregate prüfen die tatsächlich ausgewählten Jobs mit
`verification_gate.py`. Erfolgreich, übersprungen und fehlend sind verschiedene Zustände.
Ein Jobname `verification-policy (VERSION, PROFIL)` belegt die Auswahl zusätzlich in der Jobs-API.
Main darf PR-Nachweise nur übernehmen, wenn genau ein passender gemergter PR, identischer
Git-Baum von PR-Head und Main, aktuelle Policy, identisches Profil und sämtliche ausgewählten erfolgreichen Einzeljobs
vorliegen. Fehlende, doppelte, fremde, rote oder unvollständige Nachweise verhindern die Übernahme.
Zusätzlich muss der eindeutige erfolgreiche Job `verification-content (TREE_SHA)` den wirklich
ausgecheckten und getesteten PR-Merge-Baum mit demselben Main-Baum belegen. Der PR-Head allein
beweist diesen Inhalt bei zwischenzeitlich veränderter Basis nicht.
Ein neuerer gescheiterter Lauf wird nicht durch einen älteren grünen ersetzt. Die Prüfung gilt
auch für reine Teständerungen. Ohne Übernahme führt Main sein ausgewähltes Profil selbst aus.

`docs` und `host` veröffentlichen keine App. Gewöhnliche Android-Test-/Debug-Harness-Änderungen
benötigen volle Verifikation, aber keinen Produktrelease. Produktcode, Upgrade-Probe, Build,
Signierung und Freigabelogik sind releasewirksam; unbekannte Eingaben bleiben konservativ.

Für jeden Produktrelease wird genau ein Kandidat gebaut und regulär signiert:

- Ein aktueller signierter In-place-Smoke auf API 35 ist immer erforderlich.
- Bei `full` kommen die in ADR-035 festgelegten historischen Lanes (aktuell sechs) hinzu.
- Quelle und Kandidat benötigen gleichen Paketnamen und Produktionssignatur; der Kandidat hat
  einen höheren App-Versionscode. Alle anwendbaren Upgrades und Publish verwenden dieselben Bytes.
- Publish verlangt den erfolgreichen aktuellen Smoke und bei `full` erfolgreiche historische
  Upgrades. Bei `today` muss der historische Job tatsächlich übersprungen sein.

Die aktuelle Quelle wird bei Releaseplanung einmalig aus dem höchsten veröffentlichten passenden
Kanal-Tag gewählt. Tag, unveränderlicher Commit, App-Version, Paketname, Signatur, Metadaten- und
APK-Hash werden geprüft und mit Quelle und Kandidat archiviert. Das Datenbankschema wird aus
`DatabaseContract.java` am Quellcommit gelesen; `schemaVersion` in Release-Metadaten bezeichnet
nur deren Format. Eine fehlende/inkompatible Quelle oder fehlendes Schemafixture blockiert Release;
kein stiller Rückgriff auf einen älteren Stand und keine spätere Auflösung von `Latest`.

Der explizite Current-Smoke-Vertrag hat `contractVersion: 2`, `kind: current-smoke` und
`id: current-production`. Er erlaubt gleiche Datenbankschemata bei höherer App-Version und sät
Aufgabe, offene Today-Platzierung und abgeschlossenen Verlauf ausschließlich im isolierten
Emulator. Historische Fixtures behalten Vertrag 1 mit strikt höherem Zielschema. Generierte
Current-Smoke-Assets liegen separat vom vollständig manifestierten historischen Korpus.

## Einführung und Nachweise

Die bisherigen Regeln gelten bis zum geprüften P2-Merge. Die Einführung benötigt vollständige
lokale und PR-Verifikation sowie aktuellen und historischen signierten Upgrade-Gate auf Main.
Unverändert bleiben unterstützte Android-Versionen, Datenhistorien, Signatur, Paketidentität,
PR-/Squash-Pflicht und bytegeprüfte Veröffentlichung. Ein grüner automatischer Gate belegt keine
sichtbare Abnahme des produktiven Pixels. Dort bleiben Neuinstallation, Downgrade und Löschen
der App-Daten für die Recovery ausdrücklich ausgeschlossen.

Zeitgewinn wird anhand ausgewählter Tests, Job-/Gesamtdauer und abgedecktem Risiko beurteilt.
Parallele Jobzeiten werden nicht addiert; keine pauschalen Wiederholungsläufe für Durchschnittswerte.
