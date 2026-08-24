# ADR-022: schrittweise Compose-Präsentationsarchitektur

Status: angenommen

Datum: 2026-08-24

## Kontext

Die Domain-, Today- und Persistenzgrenzen sind inzwischen belastbar. Die Android-Präsentation
rekonstruiert einen deklarativen Zustandsfluss jedoch mit großen, imperativen Java-Views,
mehreren teilweise überlappenden Zustandsbesitzern, manuellen Invalidierungen und
lebenszyklusabhängigen Event-Wrappern. Weitere inkrementelle View-spezifische Reparaturen würden
die Zahl der Synchronisationspfade erhöhen, ohne die strukturelle Ursache zu beseitigen.

## Entscheidung

Die Präsentation wird bildschirmweise auf Kotlin und Jetpack Compose migriert. Der Zielzustand
verwendet unidirektionalen Datenfluss, pro Bildschirm genau einen `StateFlow`-basierten State
Owner, typisierte Actions und bestätigbare Requests. Navigation wird nach dem letzten
Screen-Cutover auf Navigation 3 umgestellt.

Die Migration erfolgt strangler-artig im bestehenden App-Modul. Java-Domaincode, `today-core`,
Room, Use Cases, der manuelle `AppContainer`, RemoteViews-Widgets und spezialisierte
Grafikkomponenten werden nicht allein wegen des UI-Technikwechsels neu geschrieben. Eine
temporäre View-Interop ist nur an diesen Grenzen zulässig und darf keine zweite Zustandswahrheit
besitzen.

Bis zum jeweiligen Cutover bleibt der alte Screen produktiv. Danach wird seine Legacy-
Orchestrierung im selben Phasenabschluss entfernt. Neue Produktfunktionen und visuelle
Neugestaltung bleiben während der Migration eingefroren.

## Konsequenzen

- Kotlin und Compose werden zusätzliche Buildabhängigkeiten; deren Versionen werden gemeinsam
  gepinnt und aktualisiert.
- Die APK darf innerhalb dokumentierter Budgets wachsen. Ein Technikwechsel ohne Größenprüfung
  ist nicht zulässig.
- Bestehende Goldens und physische Abnahmen bleiben der visuelle Vertrag.
- StateFlow ersetzt LiveData nicht mechanisch: zuerst werden Zustandsbesitz und Ereignissemantik
  bereinigt, dann wird der Screen migriert.
- Ein Screen darf während des Übergangs View oder Compose rendern, aber nie zwei unabhängig
  mutierende Präsentationsmodelle besitzen.
- Die frühere Aussage in der Architekturübersicht, Compose sei für das damalige Refactoring
  nicht vorgesehen, bleibt historisch korrekt, gilt aber nicht für diese neue Roadmap.

Die vollständige Sequenz, Gates und Ausnahmen stehen in der
[Frontend-Modernisierungsroadmap](frontend-modernization-roadmap.md).
