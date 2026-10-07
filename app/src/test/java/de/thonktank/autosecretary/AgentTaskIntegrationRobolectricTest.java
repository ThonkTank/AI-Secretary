package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.domain.model.AgentTaskChangeResult;
import de.thonktank.autosecretary.domain.model.AgentTaskChangeSet;
import de.thonktank.autosecretary.domain.model.AgentTaskOperation;
import de.thonktank.autosecretary.domain.model.AgentTaskSnapshot;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class AgentTaskIntegrationRobolectricTest {
    private AppDatabase database;
    private ApplicationUseCaseComposition app;
    private int ids;

    @Before public void setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),
                AppDatabase.class).allowMainThreadQueries().build();
        Clock clock = new Clock() {
            @Override public LocalDate today() { return LocalDate.of(2026, 10, 5); }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
        app = new ApplicationUseCaseComposition(database, clock,
                () -> "agent-" + ++ids, ComboPolicySource.defaults());
    }

    @After public void close() { database.close(); }

    @Test public void snapshotIsStableAndChangesWhenDefinitionChanges() {
        TaskId id = app.catalog.create.execute(task("Alt"));
        AgentTaskSnapshot before = onlySnapshot();
        assertEquals(id, before.id);
        assertEquals("Alt", before.definition.title);
        assertFalse(before.archived);

        app.catalog.update.execute(id, task("Neu"));
        AgentTaskSnapshot after = onlySnapshot();
        assertEquals("Neu", after.definition.title);
        assertNotEquals(before.fingerprint, after.fingerprint);
    }

    @Test public void confirmedChangeSetAppliesCreateUpdateAndDeleteAtomically() {
        TaskId changed = app.catalog.create.execute(task("Ändern"));
        TaskId deleted = app.catalog.create.execute(task("Löschen"));
        List<AgentTaskSnapshot> snapshots = app.catalog.loadAgentTaskCatalog.execute();
        AgentTaskSnapshot changedSnapshot = find(snapshots, changed);
        AgentTaskSnapshot deletedSnapshot = find(snapshots, deleted);

        AgentTaskChangeSet proposal = new AgentTaskChangeSet("proposal", "Drei Änderungen",
                List.of(AgentTaskOperation.update(changed, changedSnapshot.fingerprint,
                                task("Geändert")),
                        AgentTaskOperation.delete(deleted, deletedSnapshot.fingerprint),
                        AgentTaskOperation.create(task("Erstellt"))));
        AgentTaskChangeResult result = app.catalog.applyAgentTaskChangeSet.execute(proposal);

        assertEquals(AgentTaskChangeResult.Status.APPLIED, result.status);
        assertEquals(1, result.createdTaskIds.size());
        List<AgentTaskSnapshot> current = app.catalog.loadAgentTaskCatalog.execute();
        assertEquals(2, current.size());
        assertTrue(current.stream().anyMatch(value -> value.definition.title.equals("Geändert")));
        assertTrue(current.stream().anyMatch(value -> value.definition.title.equals("Erstellt")));
    }

    @Test public void staleOperationRollsBackEveryOperation() {
        TaskId first = app.catalog.create.execute(task("Erste"));
        TaskId second = app.catalog.create.execute(task("Zweite"));
        AgentTaskSnapshot stale = find(app.catalog.loadAgentTaskCatalog.execute(), second);
        app.catalog.update.execute(second, task("Extern geändert"));

        AgentTaskChangeSet proposal = new AgentTaskChangeSet("stale", "Nicht anwenden",
                List.of(AgentTaskOperation.update(first,
                                find(app.catalog.loadAgentTaskCatalog.execute(), first).fingerprint,
                                task("Darf nicht bleiben")),
                        AgentTaskOperation.delete(second, stale.fingerprint)));
        AgentTaskChangeResult result = app.catalog.applyAgentTaskChangeSet.execute(proposal);

        assertEquals(AgentTaskChangeResult.Status.STALE, result.status);
        assertTrue(app.catalog.loadAgentTaskCatalog.execute().stream()
                .anyMatch(value -> value.definition.title.equals("Erste")));
    }

    private AgentTaskSnapshot onlySnapshot() {
        List<AgentTaskSnapshot> values = app.catalog.loadAgentTaskCatalog.execute();
        assertEquals(1, values.size());
        return values.get(0);
    }

    private static AgentTaskSnapshot find(List<AgentTaskSnapshot> values, TaskId id) {
        return values.stream().filter(value -> value.id.equals(id)).findFirst().orElseThrow();
    }

    private static TaskDefinition task(String title) {
        return TaskDefinition.basic(title, TaskSlot.MORNING, Recurrence.DAILY,
                1, 0, List.of("Schritt"));
    }
}
