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

## Phase 2 – Mobile Ablaufdetailseite

Status: wartet auf den vollständigen Abschluss von Phase 1

Der konkrete Ausgangscommit und das erneut geprüfte Verwendungsbild werden vor dem ersten
Produktcode ergänzt.
