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

## Phase 2 – Mobile Ablaufdetailseite

Status: wartet auf den vollständigen Abschluss von Phase 1

Der konkrete Ausgangscommit und das erneut geprüfte Verwendungsbild werden vor dem ersten
Produktcode ergänzt.
