# Ausführungsprotokoll: saubere Schritt- und Trainingsarchitektur

Die autoritative Zieldefinition steht in der
[Trainingsassistenten-Roadmap](training-assistant-cleanup-roadmap.md). Dieses Dokument bewahrt
Vorprüfungen, konkrete Phasenpläne, Nachweise, Audits und Korrekturrunden, ohne die ursprüngliche
Roadmap nachträglich umzuschreiben.

## Phasenstatus

| Phase | Status | Abschlussnachweis |
|---|---|---|
| 0 – Vertrag und Grundlage | lokal bestanden, PR ausstehend | ausstehend |
| 1 – Schrittverordnung | ausstehend | – |
| 2 – Satzresultat | ausstehend | – |
| 3 – Lastentscheidung | ausstehend | – |
| 4 – Bedienung | ausstehend | – |
| 5 – Ports und Abschlussaudit | ausstehend | – |

## Phase 0 – Vertrag und reproduzierbare Grundlage

### Vorprüfung

- Sauberer Ausgangspunkt ist `origin/main` auf `521fafcb`, Room-Schema 21. Der aktive
  Frontend-Checkout bleibt unberührt; gearbeitet wird im separaten Main-Worktree.
- CI verwendet JDK 21, lokal wurde ohne Vorgabe JDK 25 gewählt. Robolectric ist damit nachweislich
  nicht zuverlässig ausführbar.
- `DatabaseContract.VERSION` ist 21, während der unabhängige Upgrade-Probe Quellversion 8 und
  Zielversion 21 zusätzlich als Literale hält. Der Workflowvertrag fixiert dieselben Literale.
- Der Upgrade-Probe darf bewusst keine Produktklasse laden: Seine Test-APK muss sowohl gegen die
  alte als auch die neue Produkt-APK funktionieren.

### Implementationsplan

- Die freigegebene Roadmap unverändert als kanonisches Dokument ablegen, ADR-028 ergänzen und
  dieses getrennte Protokoll verlinken.
- JDK 21 über `.java-version` und Gradles Daemon-JVM-Kriterien festlegen. Der offizielle
  Foojay-Resolver erzeugt plattformbezogene Download-URLs; lokale Installationspfade werden nicht
  eingecheckt.
- Upgrade-Probe aus dem geprüften Fixture lesen lassen: Die Seed-Phase speichert die tatsächlich
  verwendete Quellversion, die Verify-Phase liest die erwartete Zielversion aus demselben Asset.
  Produkt- und Fixture-Vertrag bleiben über den bestehenden Hosttest gekoppelt.
- Workflowverträge so ändern, dass sie fehlende Literale und den Fixture-basierten Pfad verlangen.
- Veraltete Testnamen und die direkte Schema-21-Assertion auf den zentralen Vertrag umstellen.
- Release-Verträge, fokussierte Migrationstests und anschließend das vollständige lokale Gate mit
  JDK 21 ausführen.

### Planabweichung aus der Vorprüfung

Die Roadmap formulierte, der Probe solle den Produktvertrag symbolisch referenzieren. Das würde
seine nachgewiesene Unabhängigkeit von der installierten Produkt-APK verletzen. Stattdessen enthält
der Probe keine Schema-Konstante mehr und liest das durch `ProductionUpgradeFixtureContractTest`
gegen `DatabaseContract` geprüfte Fixture. Damit verschwindet die ungesicherte zweite Wahrheit,
ohne eine neue Laufzeitabhängigkeit auf Produktcode einzuführen.

### Validierung und Audit

- `git diff --check`: grün.
- `python3 -m unittest discover -s scripts/ci -p 'test_*.py'`: 17 Tests grün.
- `python3 -m unittest discover -s scripts/release -p 'test_*.py'`: 23 Tests grün.
- Erster fokussierter Gradle-Lauf: Domain und Produktcode kompilierten; AndroidTest-Kompilierung
  schlug fehl, weil `UpgradePersistenceTest` nach der neuen Fixture-basierten Signatur noch keinen
  Test-Asset-Kontext an `UpgradePersistenceProbe.verify` übergab.

### Korrekturrunde 1 – AndroidX-Probeaufruf

Plan: Den normalen Instrumentierungstest wie den unabhängigen Runner um den Context der Test-APK
ergänzen. Danach werden AndroidTest-Kompilierung und beide fokussierten Hosttests unverändert
wiederholt. Es werden weder Probe-Verhalten noch Produktcode erweitert.

Ergebnis: AndroidTest-Kompilierung und beide fokussierten Hosttests sind grün.

### Korrekturrunde 2 – ausführbarer JDK-Vertrag

Der negative Audit fand, dass die neue JDK-21-Auswahl zwar funktioniert, aber noch kein Test ihre
drei repositoryweiten Bestandteile gegen späteren Drift schützt. Der bestehende Buildgrundlagen-
Vertrag wird deshalb um `.java-version`, Daemon-JVM-Version und offiziellen Foojay-Resolver
ergänzt. Danach werden Release-/Workflowverträge erneut ausgeführt.

Ergebnis: Alle 23 Release-/Workflowverträge sind erneut grün und sichern die drei Bestandteile.

### Vollständige Validierung

- `git diff --check` und interne Markdown-Linkprüfung: grün.
- CI-Harness: 17 Tests grün; Release-/Workflowverträge: 23 Tests grün.
- Fokussierter Migrations-/Fixture-Lauf und AndroidTest-Kompilierung: grün.
- Vollständiges Quality-Gate ohne gesetztes `JAVA_HOME`:
  `./gradlew testInstrumentationUnitTest lintDebug assembleDebug
  assembleInstrumentationAndroidTest assembleRelease --console=plain`; 157 Tasks in 21:29
  Minuten, 492 Tests, null Fehler, ein bewusster Benchmark-Skip, Lint grün und alle drei
  Paketierungen erfolgreich.
- Gradle verwendete den mit den neuen Kriterien kompatiblen JDK-21-Daemon. Die generierte Datei
  enthält nur offizielle plattformspezifische Resolver-URLs und keinen lokalen Pfad.
- Artefakte: Debug 5.571.045 Byte, Instrumentierung 1.599.043 Byte, unsigned Release 2.775.076
  Byte, Fonts 1.478.008 Byte; alle bestehenden Budgets werden eingehalten.

### Roadmap- und Phasenaudit

Der Abgleich findet keine Produkt-, Schema-, Signatur-, UI- oder Upgradepfadänderung. Roadmap,
ADR und getrenntes Protokoll sind verlinkt. JDK 21 ist ausführbar festgelegt und gegen Drift
gesichert. Der unabhängige Upgrade-Probe besitzt keine Schema-Literale oder Produktabhängigkeit;
Quelle und Ziel stammen aus dem Fixture, dessen Hostvertrag beide Werte gegen
`DatabaseContract` prüft. Der Migrationsgraph und der konkrete 20→21-Test verwenden die zentrale
Version. Die beiden dokumentierten Korrekturrunden sind geschlossen; lokal bleibt keine
Diskrepanz. Abschlussnachweis sind nun der eigene grüne Pull Request und Squash-Merge nach
`main`. Als nicht sichtbare Vertragsphase benötigt Phase 0 keine physische Geräteabnahme.
