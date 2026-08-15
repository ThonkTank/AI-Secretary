# ADR-006: Update-Schichten und typisierte Fehler

- Status: angenommen
- Datum: 2026-08-16

## Kontext

Der erste produktive Updater lag größtenteils in einem gemeinsamen `update`-Paket. Der
GitHub-Adapter entschied zugleich über Releaseauswahl, Vertragsregeln, HTTP, Dateisystem und
Android-Pakete. `UpdateInfo` besaß außerdem einen öffentlichen Testkonstruktor, der Metadaten und
APK-Adresse leer ließ. Solche Instanzen konnten den Downloadpfad erreichen und erst dort durch
eine generische `IllegalArgumentException` auffallen. Das ViewModel lag im Root-Paket und fing
beliebige `Exception`; Ursache und zulässige Abhängigkeitsrichtung waren dadurch nicht Teil des
Typsystems.

## Entscheidung

Der Updatebereich besteht aus vier expliziten Paketen mit nach innen gerichteten Abhängigkeiten:

| Schicht | Paket | Verantwortung |
| --- | --- | --- |
| Domain | `update.domain` | Releasevertrag, Paketbelege, Prüfvorschriften, Ergebnis- und Fehlertypen |
| Application | `update.application` | Ports für Prüfung, Download, Präferenzen und Fehlerbericht sowie verifiziertes Installationsartefakt |
| Infrastructure | `update.infrastructure` | GitHub-JSON, HTTPS, Cache-Dateien, Android-Paketmanager und Systeminstaller |
| Presentation | `update.presentation` | `UpdateViewModel`, sichtbarer Zustand und einmalige UI-Effekte |

Erlaubt sind `presentation → application → domain` und `infrastructure → application/domain`.
Domain und Application kennen weder Android noch JSON oder konkrete Infrastruktur. Der
Composition Root darf alle Schichten verbinden. Ein automatisierter Quellgrenzentest schützt
diese Richtung vor unbeabsichtigten Rückimporten.

`ReleaseMetadata.create` und `UpdateInfo.from` sind die einzigen Fabriken für öffentliche
Release- beziehungsweise Updateobjekte. Ein `UpdateInfo` enthält immer validierte Metadaten und
eine syntaktisch gültige HTTPS-Adresse. „Kein Update“ ist kein `null` mehr, sondern
`UpdateCheckResult.current()`; ein vorhandenes Angebot ist
`UpdateCheckResult.available(update)`.

Fehler verlassen Application-Ports ausschließlich als `UpdateFailure` mit einem der Fälle:

- Netzwerk oder HTTP
- ungültiger oder inkompatibler Releasevertrag
- Hash-, Paket- oder Signaturabweichung
- lokaler Speicherfehler
- Abbruch

Presentation-Zustand und UI-Effekt tragen den Fehlerfall zusätzlich zur lokalisierten Nachricht.
Das sichtbare Verhalten bleibt zunächst gleich; differenzierte Aktionen pro Fehlerfall können
darauf aufbauen, ohne Fehlermeldungstexte parsen zu müssen.

## Konsequenzen

Die GitHub-Infrastruktur bleibt vorerst ein größerer Adapter, aber ihre Entscheidungen verwenden
reine Domainregeln und typisierte Ergebnisse. Der Android-Systeminstaller bleibt in dieser Phase
Infrastruktur, die `MainActivity` noch direkt aufruft. Seine Kapselung hinter einer kleinen
Plattform- und Effektschnittstelle ist Gegenstand der folgenden Activity-Phase.

Die Updatepräferenzen besitzen bereits einen Application-Port, teilen sich jedoch weiterhin die
Datei mit Theme- und Kalenderwerten. Die physische Trennung sowie kontrollierte Executor- und
Zeitabhängigkeiten folgen in der Determinismus-Phase.
