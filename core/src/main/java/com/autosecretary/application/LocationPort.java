package com.autosecretary.application;

import java.util.function.Consumer;

public interface LocationPort {
    record Position(double latitude, double longitude) { }

    Position lastKnown();
    void start(Consumer<Position> listener);
    void stop();
}
