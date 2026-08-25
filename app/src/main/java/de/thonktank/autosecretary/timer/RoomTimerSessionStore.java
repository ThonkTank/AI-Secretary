package de.thonktank.autosecretary.timer;

import java.util.ArrayList;
import java.util.List;

import de.thonktank.autosecretary.data.local.TimerSessionDao;
import de.thonktank.autosecretary.data.local.TimerSessionEntity;

public final class RoomTimerSessionStore implements TimerSessionStore {
    private final TimerSessionDao dao;

    public RoomTimerSessionStore(TimerSessionDao dao) {
        this.dao = dao;
    }

    @Override public void put(TimerSession session) {
        dao.put(new TimerSessionEntity(session.id, session.stepId, session.title,
                session.kind.name(), session.state.name(), session.totalSeconds,
                session.remainingMillis, session.targetElapsedRealtime,
                session.targetEpochMillis, session.notificationId,
                session.completionObserved));
    }

    @Override public List<TimerSession> all() {
        List<TimerSession> result = new ArrayList<>();
        for (TimerSessionEntity entity : dao.all()) result.add(map(entity));
        return result;
    }

    @Override public TimerSession find(String id) {
        TimerSessionEntity entity = dao.find(id);
        return entity == null ? null : map(entity);
    }

    @Override public void delete(String id) {
        dao.delete(id);
    }

    private static TimerSession map(TimerSessionEntity entity) {
        return new TimerSession(entity.id, entity.stepId, entity.title,
                TimerSession.Kind.valueOf(entity.kind), TimerSession.State.valueOf(entity.state),
                entity.totalSeconds, entity.remainingMillis, entity.targetElapsedRealtime,
                entity.targetEpochMillis, entity.notificationId, entity.completionObserved);
    }
}
