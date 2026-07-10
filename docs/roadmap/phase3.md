# Phase 3 — Querschnitt-Typen nach `shared/` verschieben

Status: **abgeschlossen**.

## Design

### Verschiebeziele

1. `MealType`
   - Datei von `src/main/java/com/autosecretary/features/meal/domain/MealType.java`
     nach `src/main/java/com/autosecretary/shared/MealType.java` verschieben.
   - Package auf `com.autosecretary.shared` ändern.
   - Alle Importe aktualisieren:
     - `database/Converters`
     - `features/task/domain/model/TaskCore`
     - Meal-Domain, Meal-Data, Meal-Application, Meal-UI und Tests.
   - Keine Enum-Werte, Labels, Icons oder `toString()` ändern; Room-Konvertierung bleibt
     String/Enum-name-basiert und damit schemaidentisch.
   - Doku-Referenzen aktualisieren:
     - `src/main/java/com/autosecretary/features/meal/README.md`
     - `src/main/java/com/autosecretary/features/meal/domain/README.md`
     - `src/main/java/com/autosecretary/features/meal/ui/README.md`
     - `CLAUDE.md` shared-Paketbeschreibung.

2. `WidgetRefreshNotifier`
   - Datei von `src/main/java/com/autosecretary/app/WidgetRefreshNotifier.java`
     nach `src/main/java/com/autosecretary/shared/WidgetRefreshNotifier.java` verschieben.
   - Package auf `com.autosecretary.shared` ändern.
   - Importe in `AppCompositionRoot`, `TaskCompletionEffects`, `DailyPlanningReceiver`,
     `TaskViewModel`, `TaskViewModelFactory` und Tests aktualisieren.
   - Interface-Methoden bleiben unverändert.

3. Budget-Default-Icon/-Farbe
   - Konstanten `DEFAULT_ICON` und `DEFAULT_COLOR_HEX` vom Entity-Owner
     `BudgetCategoryEntity` in den Domain-Record `BudgetCategory` verschieben.
   - `BudgetCategory.create(...)` verwendet die Domain-Konstanten direkt.
   - `BudgetCategoryEntity` referenziert die Domain-Konstanten fuer Felddefaults.
   - Keine DB-Spalten, Default-Werte oder UI-Anzeige ändern.

### Bekannte Nicht-Ziele / Drift

- `AppDatabase.version` bleibt in Phase 3 unveraendert. Schemakonstanz wird gegen
  `src/main/java/com/autosecretary/database/AppDatabase.java` geprueft.
- Die bereits bekannte `CLAUDE.md`-DB-Versionsabweichung (CLAUDE nennt 24,
  `AppDatabase` ist 27) wird nicht in Phase 3 geloest; finaler Doku-Sync bleibt
  Phase 8. Phase 3 aktualisiert `CLAUDE.md` nur dort, wo die `shared/`-Beschreibung
  durch `MealType`/`WidgetRefreshNotifier` sonst falsch waere.

### Mechanik

- Reine Paket-/Importverschiebung plus Konstanten-Eigentümerwechsel.
- Keine neuen Tests; Phase 2-Suite muss unverändert grün bleiben.
- Keine Room-Migration, keine DB-Versionserhöhung.
- Keine neuen Gradle-Module, kein DI-Framework, kein Event-Bus.

## Vollständigkeits-Review (Subagent) — Ergebnis

Erster Review-Zeitpunkt: 2026-07-10.

Ergebnis: fail; Umsetzung blockiert, bis Designluecken geschlossen sind.

Blockierende Punkte aus dem Review und Design-Reaktion:
- Doku-Referenzen auf die alte `MealType`-Lage waren nicht entschieden; Design nennt
  jetzt Meal-READMEs und `CLAUDE.md`-Shared-Beschreibung als Arbeitsflaeche.
- Done-When pruefte alte Imports nur in `src/main/java`; DW4 umfasst jetzt `src/main/java`
  und `src/test/java`.
- DB-Versionsdrift in `CLAUDE.md` war unscharf; Design haelt fest, dass Phase 3 die
  `AppDatabase`-Version nicht aendert und der bestehende DB-Doku-Drift Phase 8 gehoert.

Re-Review-Zeitpunkt: 2026-07-10.

Ergebnis: pass; keine verbleibenden Design-Blocker. Der Re-Review bestaetigt:
- Doku-Referenzen und `CLAUDE.md`-Shared-Beschreibung sind als Arbeitsflaechen abgedeckt.
- DW4 umfasst `src/main/java` und `src/test/java`.
- DB-Version-Drift ist sauber als Phase-8-Thema abgegrenzt.

## Done-When-Kriterien

- DW1: `MealType.java` liegt unter `shared/`; es gibt keine Produktionsimporte mehr von
  `com.autosecretary.features.meal.domain.MealType`.
- DW2: `WidgetRefreshNotifier.java` liegt unter `shared/`; es gibt keine Produktionsimporte
  mehr von `com.autosecretary.app.WidgetRefreshNotifier`.
- DW3: `BudgetCategory` besitzt die Default-Icon/-Farb-Konstanten; `BudgetCategoryEntity`
  referenziert sie und besitzt keine eigenen konkurrierenden Default-Konstanten mehr.
- DW4: `rg` ueber `src/main/java` und `src/test/java` findet die alten Fully-Qualified
  Imports nicht mehr.
- DW5: Meal-READMEs und `CLAUDE.md` enthalten keine falsche Einordnung von `MealType`
  als `features/meal/domain`-Typ mehr; `CLAUDE.md` beschreibt `MealType` und
  `WidgetRefreshNotifier` unter `shared/`.
- DW6: `AppDatabase`-Version bleibt unverändert; keine Migration oder Schemaaenderung.
- DW7: Bestehende Phase-2-Charakterisierungstests bleiben unverändert grün.
- DW8: `./gradlew checkArchitecture --console=plain` Exit 0.
- DW9: `./gradlew assembleDebug --console=plain` Exit 0.
- DW10: `./gradlew testDebugUnitTest --console=plain` Exit 0.

## Verhaltensinvarianten

Keine neuen fachlichen Invarianten. Diese Phase darf nur Typ- und Konstanten-Eigentum
verschieben. Die Phase-2-Tests sichern Budget-Overview, Task↔Meal-Completion,
Task-Completion-Kern, Scheduling-Roundtrip und Meal-Planner-CRUD gegen unbeabsichtigte
Verhaltensaenderungen.

## Erfolgs-Review (Subagent) — Ergebnis

Review-Zeitpunkt: 2026-07-10.

Ergebnis gegen Done-When-Kriterien:
- DW1: pass — `MealType` liegt unter `shared/`; alte Produktionsimporte sind weg.
- DW2: pass — `WidgetRefreshNotifier` liegt unter `shared/`; alte Produktionsimporte sind weg.
- DW3: pass — `BudgetCategory` besitzt `DEFAULT_ICON`/`DEFAULT_COLOR_HEX`;
  `BudgetCategoryEntity` referenziert diese Konstanten und hat keine eigenen Default-
  Konstanten mehr.
- DW4: pass — `rg` ueber `src/main/java` und `src/test/java` auf alte FQNs ergab
  keine Treffer.
- DW5: pass — `CLAUDE.md`, Meal-READMEs und `shared/README.md` ordnen `MealType` und
  `WidgetRefreshNotifier` unter `shared/` ein.
- DW6: pass — kein Diff an `AppDatabase.java`; Version bleibt 27.
- DW7/DW8/DW10: im Review hauptagent-belegt; anschließend im Hauptlauf gruen geprüft.
- DW9: im Review pending; anschließend im Hauptlauf gruen geprüft.

Blockierende Befunde: keine nach Abschluss-Gate.

## Abschluss-Gate

2026-07-10:
- `./gradlew checkArchitecture --console=plain` → Exit 0.
- `./gradlew assembleDebug --console=plain` → Exit 0.
- `./gradlew testDebugUnitTest --console=plain` → Exit 0.
