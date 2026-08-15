package de.thonktank.autosecretary.calendar;

public interface CalendarDataSource {
    CalendarResult loadToday();

    Subscription observeChanges(Runnable observer);

    interface Subscription {
        void close();
    }
}
