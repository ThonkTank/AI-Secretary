# ADR-029: Fokussierte Composition, Transaktionen und Room-Adapter

- Status: angenommen
- Datum: 2026-08-31

## Kontext

ADR-028 legte kleine Fachports und einen unabhängigen `TransactionRunner` als Ziel fest. Der
Zwischenstand bündelte weiterhin alle Ports in `ApplicationTaskRepository`, ließ jeden Port
`TransactionalRepository` erben und stellte Katalog, Today, Flows und Training gemeinsam als
`TaskUseCases` bereit. `RoomTaskRepository` enthielt außerdem die Abbildung von Schrittresultaten,
Trainingsanpassungen und Lastfragen.

Dadurch konnte ein kleiner Use Case nur mit einem Objekt verdrahtet werden, das fachfremde
Fähigkeiten und Transaktionsausführung versprach. Slice-Tests griffen aus Bequemlichkeit auf den
breiten Abnahmespeicher zurück. Die Compilergrenze war sauber, die Capability-Grenze jedoch nicht.

## Entscheidung

Die Composition Root veröffentlicht vier fachliche Bündel:

1. `CatalogUseCases` für Definitionen, Platzierungen und Konfiguration,
2. `TodayUseCases` für Materialisierung und Ausführung,
3. `FlowUseCases` für Definition und Laufzeit von Schrittflüssen,
4. `TrainingUseCases` für Lastentscheidung, Verlauf und Undo.

Die Bündel enthalten fertige Use Cases und kennen selbst keine Persistenz. Jeder transaktionale
Use Case erhält `TransactionRunner` explizit und getrennt von den benötigten Repository-Ports.
Fachports erben keine Transaktionsfähigkeit. `ApplicationTaskRepository`,
`TransactionalRepository`, `FlowExecutionRepository` und `TaskUseCases` entfallen. Auch
optionale Fähigkeiten werden nicht per `instanceof` erraten: Materialisierung, Flow-Laufzeit,
Combo-Pflichten und Dashboard-Lesewege erhalten jeden benötigten Port einzeln.

Room besitzt drei fokussierte Adaptergrenzen: `RoomTransactionRunner`, `RoomStepRepository` und
`RoomTrainingRepository`. Der verbleibende `RoomTaskRepository` delegiert Schritt- und
Trainingsabbildung an diese Adapter. Sein infrastruktureller `TaskStore` darf die konkreten Room-
Fähigkeiten für Wiring und umfassende Abnahmetests zusammenfassen, darf aber nicht in Domain oder
Präsentation importiert werden.

Fokussierte Schedule-, Step- und Trainings-Slice-Tests verwenden nur ihre kleinen Ports und
Runner. Der breite In-Memory-Speicher bleibt ausschließlich für bewusst mehrschichtige
Abnahmeabläufe zulässig.

## Konsequenzen

- Screens mit echten Cross-Slice-Operationen erhalten mehrere klar benannte Bündel; ein neuer
  Sammelalias ist nicht zulässig.
- Transaktionsgrenzen sind in Konstruktoren sichtbar und können unabhängig getestet werden.
- Training und Schrittresultate können ohne den restlichen Room-Gateway getestet und weiter
  entwickelt werden.
- Schema 22, Migrationen, Entities, Ressourcen und visuelle Goldens werden durch diese reine
  Architekturentscheidung nicht geändert.
- Ausführbare Architekturtests sichern entfernte Verträge, fokussierte Bundles, getrennte Runner,
  Room-Adapter und kleine Slice-Doubles dauerhaft ab.
