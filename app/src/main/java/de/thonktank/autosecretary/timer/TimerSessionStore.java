package de.thonktank.autosecretary.timer;

import java.util.List;

public interface TimerSessionStore {
    void put(TimerSession session);
    List<TimerSession> all();
    TimerSession find(String id);
    void delete(String id);
}
