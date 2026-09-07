# ADR-033: Ablaufkandidaten und gemeinsame Today-Blätter

- Status: angenommen
- Datum: 2026-09-07

## Kontext

Schema 22 bildet einen fälligen, aber noch nicht begonnenen Ablauf als `StepFlowRun` im Zustand
`PENDING_START` ab. Für jeden solchen Run wird eine eigene `FLOW_SHEET`-Occurrence mit einem
materialisierten Schritt erzeugt. `LoadDashboard` fasst diese Einzelblätter anschließend nur für
die Darstellung nach Task und Slot zusammen. Das sichtbare gemeinsame Blatt besitzt weder eine
eigene Identität noch eine eigene Sortierposition.

Diese Zwischenarchitektur repariert das unmittelbar sichtbare Wäscheverhalten, vermischt aber
weiterhin drei verschiedene Begriffe: Fälligkeit, tatsächlich gestartete Ausführung und Today-
Blatt. Die Vermischung zeigt sich in `currentSheetOccurrenceId`, `flowAggregate`,
`flowRunByStepId` und einer `DeferTask`-Sonderbehandlung, die mehrere Run-Occurrences suchen und
gemeinsam verschieben muss. Ein ungestarteter Kandidat bleibt außerdem technisch ein aktiver Run,
obwohl er im Alles-Tab nicht als Hintergrundlauf gezeigt wird.

Die Today-Punkte für Trainingssätze wiederholen ihren vollständig sichtbaren und zugänglichen
Zustand zusätzlich mit „Satz x von y“ und „x/y erledigt“. Diese Texte gehören nicht zum
freigegebenen visuellen Vertrag.

Der vollständige Ziel- und Phasenvertrag steht in der
[Ablaufkandidaten-/Today-Blatt-Roadmap](flow-task-sheet-roadmap.md). Diese ADR trifft die
fachlichen und technischen Entscheidungen, ohne die Roadmap oder ihr getrenntes
[Ausführungsprotokoll](flow-task-sheet-execution.md) zu ersetzen.

## Entscheidung

### Fälligkeit ist ein Kandidat, kein Run

Ein neuer persistenter `FlowCandidate` repräsentiert genau einen fälligen, noch nicht begonnenen
Ablaufstart. Er enthält Kandidaten-ID, Task-ID, Seed-Step-ID, `sourceKey`, Fälligkeitsdatum,
Slot, Queue-Reihenfolge und Erzeugungszeit. Eine Datenbankinvariante erlaubt höchstens einen
offenen Kandidaten pro Task, Seed und Slot; `sourceKey` bleibt zusätzlich global eindeutig.

Kandidaten besitzen keine Run-Step-Snapshots, keine Run-Ressourcen, keinen Wartezustand und keine
Hintergrunddarstellung. Materialisierung erzeugt und dedupliziert Kandidaten. Änderungen an der
Definition vor dem Start gelten beim späteren Start; erst dieser löst den editierfesten Pfad und
seine Ressourcen in einen Snapshot auf.

### Ein Run beginnt nur durch eine Nutzeraktion

`StepFlowRun` wird ausschließlich durch `StartFlowCandidate` erzeugt. Der Use Case lädt den
Kandidaten und die aktuelle Definition innerhalb einer Transaktion erneut, löst den Pfad,
prüft die am Seed erworbenen Ressourcen gegen die aktuelle Belegung, erzeugt Run-, Step- und
Ressourcen-Snapshots, verbucht den Startschritt und entfernt den Kandidaten.

Die Kapazitätsanzeige ist nur eine Projektion; die transaktionale Prüfung beim Klick bleibt
autoritative Zulassung. Bei geänderter Kapazität liefert der Start einen typisierten
`CAPACITY_CHANGED`- beziehungsweise `STALE_CANDIDATE`-Ausgang. Kandidat, Rewards und Ressourcen
bleiben dann unverändert. Ein partieller Run darf nicht sichtbar oder persistent werden.

`PENDING_START` entfällt. Die aktiven Run-Zustände sind `WAITING_RESOURCE`, `WAITING_TIME` und
`OFFERED`; `COMPLETED` und `CANCELLED` bleiben terminal. Alle Runs im Alles-Tab sind damit
tatsächlich nutzergestartete Ausführungen.

Ressourcen, deren Lease am Seed beginnt, werden gemeinsam zugelassen. Beim Wäschepfad umfasst
das Waschmaschine und Trockenplatz. Drei belegte Trockenplätze verhindern daher den Start eines
vierten Waschgangs, auch wenn die Waschmaschine gerade frei wäre.

### Das Today-Blatt besitzt eine eigene Identität

`FlowTaskSheet` ist ein ausdrückliches Read Model für genau eine Ablauf-Aufgabe und einen Slot.
Seine stabile ID wird aus Task und Slot gebildet. Eine eigene persistente
`FlowTaskSheetPlacement` hält Datum und Today-Sortierposition; sie ist weder Run noch
Occurrence-Historie.

Das Blatt enthält typisierte Einträge:

- `FlowCandidateEntry` startet genau einen Kandidaten;
- `FlowRunStepEntry` bearbeitet genau den angebotenen Schritt eines gestarteten Runs.

Angebotene Run-Schritte stehen vor startbaren Kandidaten. Nicht startbare Kandidaten bleiben
persistiert, werden aber nicht angezeigt. Ein Blatt ohne sichtbaren Eintrag wird nicht
projiziert; eine verwaiste Placement-Zeile erzeugt nie ein leeres Blatt.

Run-spezifische Occurrences bleiben nur dort erhalten, wo Ausführung, Reward, Abschluss oder
Undo eine persistente Identität brauchen. Sie heißen fachlich `FLOW_STEP` und sind keine
Today-Blätter. `currentSheetOccurrenceId` wird entsprechend zu
`currentExecutionOccurrenceId`. `FLOW_SHEET`, `DashboardTask.flowAggregate` und
`flowRunByStepId` entfallen aus produktiver Domain und Presentation.

### Today-Aktionen sind typisiert

Today verwendet ein geschlossenes `TodayItemTarget` mit getrennten Varianten für normale
Occurrences und `FlowTaskSheet`. Zeilenaktionen verwenden getrennte Kandidaten- und
Run-Step-Ziele. Eine View erhält die ausführbare Aktion und leitet keine Fachsemantik aus
Stringpräfixen, Listenpositionen oder Aggregate-Flags ab.

`DeferTodayItem` verschiebt bei einer normalen Aufgabe deren Occurrence und bei einem
`FlowTaskSheet` genau dessen Placement. „Später“ verändert weder Kandidaten, Run-Zustände,
Ready-Zeit noch Ressourcen. Die Aktion wird für jedes fokussierbare Blatt angeboten, sobald im
gleichen Today-Kontext ein weiteres ausführbares Blatt existiert.

Sammelabschluss und Widget dürfen einen Flow-Kandidaten oder einen delay-pflichtigen Run-Schritt
nicht ohne die erforderliche Eingabe abschließen. Das Widget projiziert dasselbe Blatt und
öffnet für eine eingabepflichtige Aktion die App, statt einen verkürzten Schreibpfad zu besitzen.

### Sichtbarer Vertrag

Das gemeinsame Ablaufblatt zeigt weder Erklärungshinweis noch „Starten“ oder „Läuft“. Rechts
stehen ausschließlich vorhandene Zeit- oder Mengenangaben. Aktive Schritte nennen ihre Herkunft,
beispielsweise „Buntwäsche: Aufhängen“. Der linke Kreis bleibt die einzige primäre Zeilenaktion;
„Später“ bleibt die Blattaktion.

Die sichtbaren Satztexte „Satz x von y“ und „x/y erledigt“ entfallen ersatzlos. Satzpunkte,
Füllzustand, aktueller Punkt, Auswahlring, Accessibility-Beschreibung und RangeInfo bleiben der
vollständige Fortschrittsvertrag.

### Schema-23-Migration

Schema 23 führt Kandidaten und Blatt-Placements ein und baut Run-/Occurrence-Tabellen so um,
dass der Zielzustand keine alten Blattfelder oder Zustände enthält.

- Unberührte `PENDING_START`-Runs werden zu Kandidaten; ihre ungenutzten Steps, Ressourcen und
  Einzelblätter werden entfernt.
- Bereits gestartete Runs behalten Pfad, gewählte Delays, Queue, aktive Ressourcen, Rewards und
  angebotene Ausführungsschritte.
- Ein inkonsistenter alter `PENDING_START`-Datensatz mit Ausführungs- oder Ressourcenbelegen wird
  als gestarteter Run erhalten und nicht zu einem Kandidaten zurückgestuft.
- Mehrere alte Einzelblattpositionen werden deterministisch zu genau einem Placement pro Task
  und Slot zusammengeführt.
- Nach der Migration gibt es keinen produktiven Legacy-Adapter, der alte Zustände während der
  Laufzeit erneut interpretiert.

Der unterstützte Upgradepfad bleibt ab Produktionsschema 8 erhalten. Ein exportiertes
Schema-22-Upgradefixture deckt mehrere Kandidaten, laufende und wartende Ketten, angebotene
Folgeschritte und belegte Ressourcen ab.

### Verifikation und Veröffentlichung

Die fachliche Abnahme umfasst Domain-, Transaktions-, Room-, Migrations-, Today-, Alles-, Widget-,
Golden- und Accessibility-Verträge sowie normale und animationsaktive Gerätepfade.

Der PR behält die vollständige Geräte-/Animationsmatrix. Ein Main-Lauf darf identische Quality-
und Instrumentierungsnachweise nur dann übernehmen, wenn Git-Baum, Workflowvertrag und grüner
PR-Gate über GitHub-Metadaten eindeutig zum Squash-Inhalt passen. Direkte Pushes, fehlende
Nachweise oder Baumabweichungen erzwingen den vollständigen Main-Lauf. Produktions-APK,
Signatur-, Hash-, Upgrade- und Publish-Prüfungen laufen immer auf dem exakten Main-Commit; ein
PR-APK wird nie veröffentlicht.

Phasenabschluss und Veröffentlichung folgen ADR-030. Die Roadmap ergänzt vor dem Merge visueller
Änderungen eine explizite Prüfung der erzeugten Golden-/Screenshot-Artefakte, aber kein
nachgelagertes physisches Gerätegate.

## Konsequenzen

Fälligkeit, Ausführung und Darstellung besitzen jeweils einen benannten Owner. Kandidaten können
Today angeboten werden, ohne Hintergrundaktivität oder Ressourcenverbrauch vorzutäuschen. Das
gemeinsame Blatt kann wie jedes andere Today-Item verschoben werden, ohne mehrere Run-Occurrences
zu suchen.

Der Cutover benötigt eine reale Room-Migration und eine zusammenhängende Umstellung von Domain,
Today, Alles und Widget. Die Migration ist die einzige Kompatibilitätsgrenze; nach ihrem Erfolg
bleiben weder parallele Modelle noch Laufzeit-Reconciliation für Schema 22 bestehen.

Die vollständige PR-Matrix bleibt Sicherheitsgrenze. Der in der Roadmap vorgesehene
inhaltsgleiche Main-Nachweis reduziert ausschließlich nachweislich identische Wiederholung und
lässt die exakte Produktionskandidaten- und Upgradeprüfung unverändert.
