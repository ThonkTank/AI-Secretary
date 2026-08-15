# Architekturkritik der Release- und Updatebereiche

Stand: 2026-08-16

## Gesamturteil

Der bearbeitete Pfad ist heute deutlich belastbarer: Ein Kandidat wird genau einmal gebaut,
signiert, auf zwei Android-Versionen als echtes Upgrade geprüft, bytegleich veröffentlicht und
vom In-App-Updater nur nach Paket-, Versions-, Hash-, Größen-, Commit- und Signaturprüfung an den
Systeminstaller übergeben. Die Updateimplementierung hat erkennbare Domain-, Application-,
Infrastructure- und Presentation-Grenzen.

Trotzdem ist das keine „fertige“ Zielarchitektur. Es ist eine pragmatische Härtung einer kleinen,
historisch gewachsenen Ein-Modul-App. Mehrere Grenzen werden durch Konvention und Quelltexttests,
nicht durch Module oder den Compiler geschützt. Der Releasepfad ist sicherer, bleibt aber komplex,
langsam und stark an GitHub Actions gebunden.

## Was Planung und Implementierung unnötig erschwert hat

### Releasewissen war über Workflow, Gradle, App und Dokumentation verteilt

Versionierung, Tagformat, APK-Name, Signer, GitHub-Releaseformat und Updateauswahl hatten mehrere
Quellen. Vor der Extraktion des Releasewerkzeugs lag ein großer Teil als Inline-Shell im Workflow.
Das machte Idempotenz, Fehlerwiederaufnahme und lokale Tests unnötig schwer. Auch jetzt bleiben
Orchestrierung, `jq`, GitHub-API-Aufrufe und Secretbehandlung in einer rund 360 Zeilen langen
Workflowdatei. Das Python-Werkzeug besitzt die fachlichen Regeln, aber nicht den gesamten Ablauf;
eine gewisse Vertragsduplizierung bleibt.

Die Kopplung des Versionscodes an GitHub-Laufnummer und -Versuch ist robust monoton, aber fachlich
merkwürdig. Ein fehlgeschlagener Lauf ließ 0.2.24 aus und der nächste erfolgreiche Commit wurde
0.2.25. Das ist korrekt, zeigt aber, dass eine Infrastrukturkennung die sichtbare Produktversion
bestimmt. Eine repositoryeigene, transaktionale Versionsquelle wäre langfristig verständlicher.

### Der Upgrade-Test vermischte Migration und allgemeine Persistenz

Der in Phase 5 eingeführte Test seedete absichtlich alte `forest_ui`-Werte, um die Migration nach
`forest_updates` zu beweisen. Sobald 0.2.23 selbst die Vorgängerversion war, hatte deren normaler
Start die einmalige Migration bereits markiert und den Prüfzeitpunkt aktualisiert. Beide
Phase-6-Upgrades scheiterten deshalb nicht an Produktcode, sondern an einer Fixture-Annahme, die
nur für genau einen Übergang galt. Der Probe ist nun versionsbewusst, aber die Episode zeigt die
fehlende Trennung zwischen „rollendes Upgrade von N-1“ und „historische Migrationsmatrix“.

Gewünscht wäre ein deklaratives Fixture-Register pro Persistenzänderung: Ausgangsversion,
vorhandene Dateien/Schema, erwarteter Zielzustand und Gültigkeitsbereich. Der rollende Test könnte
dann immer N-1 prüfen, während unveränderliche historische Fixtures jede einmal eingeführte
Migration dauerhaft absichern.

### Abhängigkeiten waren versteckt und Produktionsverhalten leckte in Tests

Das frühere ViewModel erzeugte seinen Executor selbst, Zeit war ein unbenannter Supplier, der
GitHub-Adapter erzeugte seinen HTTP-Transport intern und Updatewerte lagen in allgemeinen
UI-Preferences. Tests mussten pollen und schlafen; Debug-Builds konnten bei manueller Bedienung
echte Remoteanfragen auslösen. Diese technische Schuld verlangsamte jede Änderung, weil
Nebenläufigkeit, Zeit, Netzwerk und Persistenz nicht unabhängig kontrollierbar waren.

Die neuen Ports und Konfigurationen beheben den akuten Schmerz. Sie machen aber auch sichtbar,
dass das handgeschriebene Dependency-Injection-Modell kein Scope- oder Ownership-Konzept hat.
`AppContainer` veröffentlicht viele Komponenten als öffentliche Felder und übernimmt zugleich
Konstruktion, Umgebungsauswahl und Lebensdauer. Für die aktuelle Appgröße ist das tragbar; bei
weiterem Wachstum wird es zu einem Service Locator mit impliziten Abhängigkeiten.

### UI- und Plattformverantwortung war in `MainActivity` konzentriert

Updatevalidierung, Dialogformatierung, Installationsberechtigung und Navigation lagen anfangs im
Lifecycle-Host. Das erschwerte Unit-Tests und machte jede Plattformänderung zu einer
Activity-Änderung. Der Updatebereich ist inzwischen delegiert, aber `MainActivity` umfasst noch
mehr als 300 Zeilen und koordiniert weitere Dashboard-, Task- und Lifecycle-Belange. Die konkrete
Phase hat nur den Updateanteil bereinigt; die allgemeine Activity-Konzentration bleibt technische
Schuld außerhalb dieses Scopes.

### Netzwerkcode hatte keine explizite Vertrauensgrenze

Automatische Redirects, nur implizite HTTPS-Prüfung, fehlende Host-Allowlist, keine Retrystrategie
und unpräzise Fehler machten den Downloadpfad schwer sicher zu beurteilen. Erst die injizierte
Connection-Factory schuf einen brauchbaren Testseam. Ein solcher Seam und eine explizite
Trust-Policy wären von Anfang an wünschenswert gewesen; ihre nachträgliche Einführung erforderte
Änderungen durch Composition Root, Repository, Transport, Domänenfehler und Tests.

## Verbleibende strukturelle Schwächen

### Pakete sind keine harte Modulgrenze

Die vier Update-Schichten liegen im selben Android-Modul. Ein Quelltexttest sucht verbotene
Importstrings und schützt damit die derzeitige Struktur, ist aber empfindlich gegenüber
vollqualifizierten Namen, Reflection, Umbenennungen und Kommentaren. Separate Gradle-/Java-Module
oder ein semantisches Architekturwerkzeug würden Verstöße zur Compilezeit beziehungsweise über
den Syntaxbaum erkennen. Für den kleinen Bereich war die jetzige Lösung schneller, aber sie ist
eine bewusste Kompromissarchitektur.

### Presentation verwendet konsumierbare LiveData-Ereignisse

`UpdateEvent` besitzt ein atomisches `consume()`-Flag. Das verhindert doppelte Dialoge im
Normalfall, koppelt Zustellung und Konsum aber an ein mutierbares Objekt. Bei Prozessneustart,
mehreren Observern oder späterer Navigation ist die Semantik schwerer nachvollziehbar als ein
expliziter, gespeicherter Zustandsautomat mit UI-Acknowledgement. Außerdem werden die typisierten
Fehler zwar geloggt und im State erhalten, die Benutzeroberfläche zeigt heute überwiegend nur
generische Prüf-/Downloadtexte. Die Typisierung verbessert Technik und Diagnose stärker als die
aktuelle UX.

### Abbruch ist kooperativ, nicht sofortig

Der HTTP-Transport prüft Interrupts vor Requests und zwischen Kopierblöcken. Ein gerade
blockierender Socket-Read wird dadurch nicht aktiv geschlossen; im schlechtesten Fall begrenzt
erst der Read-Timeout von 30 Sekunden die Reaktion. Es gibt weder resumierbare Downloads noch
`WorkManager`-Fortsetzung über Prozessende hinweg. Für ein kleines APK und einen expliziten
Foreground-Ablauf ist das akzeptabel, aber kein allgemeiner Downloadmanager.

### Der GitHub-Updatekanal bleibt ein Single Point of Operation

Die exakte Host-Allowlist verhindert Redirect-Ausbrüche, ist aber bei Änderungen der GitHub-
Assetinfrastruktur absichtlich störanfällig. Es gibt kein Certificate Pinning, keine separat
signierten Metadaten und kein TUF-ähnliches delegiertes Vertrauensmodell. Ein kompromittiertes
GitHub-Konto kann zwar ohne Produktionsschlüssel keine installierbare Fremd-APK erzeugen, aber
Releases blockieren, alte gültig signierte APKs präsentieren oder Verfügbarkeit stören. Der
permanente Android-Schlüssel bleibt der eigentliche Root of Trust und zugleich ein besonders
kritischer Single Point of Failure.

### Schlüsselrotation ist noch nicht implementiert

Der aktuelle Vertrag kennt einen Fingerprint und keine Signing Lineage. API 26/27 verschärfen die
Lage, weil eine Rotation für diese Geräte weiterhin alte v2-Signaturen benötigt. Das neue Runbook
verhindert improvisierte Wechsel, ersetzt aber keine implementierte Lineage-, Metadaten- und
Upgrade-Migration. Vor einer Rotation ist deshalb echte Produktarbeit nötig.

### CI ist gründlich, aber teuer und seriell

Quality, zwei Clean-Install-Instrumentierungen, Packaging und zwei weitere Upgrade-Emulatoren
erzeugen gute Evidenz, dauern aber pro Release mehrere Minuten und wiederholen Setup- und
Buildarbeit. Signierte End-to-End-Tests können lokal ohne Produktionssecret nicht vollständig
reproduziert werden. Kurzlebige Kandidatenartefakte werden nur zwei Tage aufbewahrt. Es fehlen
langfristige Provenance/SBOM, reproduzierbare Builds aus identischem Source-Input und ein
unabhängiger Signierdienst mit stärkerer Schlüsselisolation.

### Der Distributionsweg begrenzt die Architektur

Die App veröffentlicht ein universelles APK direkt über GitHub. Das vereinfacht den Nutzerpfad,
verzichtet aber auf Store-basierte Schlüsselverwaltung, gestufte Rollouts, automatische
Rollback-Steuerung und gerätespezifische Bundles. Der In-App-Updater muss diese Verantwortung
selbst tragen. Das ist eine Produktentscheidung, keine bloße Implementierungsunschönheit, sollte
aber bei jeder weiteren Investition in den Updater neu bewertet werden.

## Was ich mir als Ausgangslage gewünscht hätte

- einen einzigen maschinenlesbaren Releasevertrag von Beginn an;
- einen kleinen lokal testbaren Releaseplaner statt Inline-Shell;
- getrennte, explizite Produktions-/Development-/Test-Kompositionen;
- Ports für Uhr, Executor, HTTP, Paketprüfung und Installer vor dem ersten Test;
- dedizierte Updatepersistenz mit versionierten Migration-Fixtures;
- eine Activity, die von Anfang an nur Lifecycle, Rendering und Delegation übernimmt;
- eine dokumentierte Schlüsselverwahrung mit unabhängigen Alias-/Passwortwerten;
- einen stabilen PR-Sammelcheck und eine zur Repositoryregel passende Governance;
- eine vor dem ersten Produktionsrelease entschiedene Strategie für Signing Lineage und
  Schlüsselverlust.

## Priorisierte nächste Schritte

1. Vor jeder Schlüsseländerung die im Signing-Runbook beschriebene Lineage-Migrationsphase
   implementieren und alte → Übergang → neu auf API 26/28/35 beweisen.
2. Rollende Upgrade-Tests und unveränderliche historische Migrationsfixtures technisch trennen.
3. Release-Orchestrierung weiter aus YAML/Shell in ein typisiertes, lokal ausführbares Werkzeug
   ziehen; Provenance und längere Auditaufbewahrung ergänzen.
4. Bei weiterem Wachstum Update-Domain/Application in ein reines JVM-Modul verschieben und
   Quelltext-Stringtests durch echte Modulgrenzen ersetzen.
5. Updateeffekte und Prozesswiederherstellung als expliziten Zustandsautomaten modellieren; erst
   danach Hintergrund-/Resume-Downloads erwägen.
6. Nach bewusster Änderung von `AGENTS.md` das dokumentierte Branch-Ruleset aktivieren. Bis dahin
   bleibt der direkte Push auf `main` ein organisatorisches Risiko, obwohl jeder Push durch das
   vollständige Releasegate läuft.
