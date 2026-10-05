package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.local.AgentMessageEntity;
import de.thonktank.autosecretary.data.local.AgentProposalEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class AgentPersistenceRobolectricTest {
    private AppDatabase database;

    @Before public void setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),
                AppDatabase.class).allowMainThreadQueries().build();
    }

    @After public void close() { database.close(); }

    @Test public void messagesAndProposalResolutionAreDurable() {
        database.agent().insertMessage(new AgentMessageEntity("message", 1, "USER",
                "Sortiere meine Aufgaben", 100));
        database.agent().insertProposal(new AgentProposalEntity("proposal", "message",
                "PENDING", "{}", 101, 0));

        assertEquals(1, database.agent().messages().size());
        assertEquals(1, database.agent().resolveProposal("proposal", "APPLIED", 102));
        assertEquals("APPLIED", database.agent().proposals().get(0).status);
        assertEquals(102, database.agent().proposals().get(0).resolvedAtEpochMillis);

        database.agent().clearProposals();
        database.agent().clearMessages();
        assertEquals(0, database.agent().messages().size());
        assertEquals(0, database.agent().proposals().size());
    }
}
