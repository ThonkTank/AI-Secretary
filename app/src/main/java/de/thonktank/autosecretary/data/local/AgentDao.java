package de.thonktank.autosecretary.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AgentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertMessage(AgentMessageEntity message);

    @Query("SELECT * FROM agent_messages ORDER BY sequence")
    List<AgentMessageEntity> messages();

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertProposal(AgentProposalEntity proposal);

    @Query("SELECT * FROM agent_proposals ORDER BY createdAtEpochMillis, id")
    List<AgentProposalEntity> proposals();

    @Query("UPDATE agent_proposals SET status=:status, resolvedAtEpochMillis=:resolvedAt "
            + "WHERE id=:id AND status='PENDING'")
    int resolveProposal(String id, String status, long resolvedAt);

    @Query("DELETE FROM agent_proposals") void clearProposals();
    @Query("DELETE FROM agent_messages") void clearMessages();
}
