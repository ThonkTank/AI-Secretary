package com.autosecretary.application.update;

public sealed interface UpdateCheckResult
        permits UpdateCheckResult.Current, UpdateCheckResult.Available {
    record Current() implements UpdateCheckResult { }
    record Available(UpdateInfo update) implements UpdateCheckResult {
        public Available {
            if (update == null) throw new IllegalArgumentException("Update fehlt");
        }
    }
}
