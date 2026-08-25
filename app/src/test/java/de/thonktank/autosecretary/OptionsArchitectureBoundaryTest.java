package de.thonktank.autosecretary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class OptionsArchitectureBoundaryTest {
    @Test public void optionsStateHasOneOwnerActionBoundaryAndNoDashboardBroker() throws Exception {
        Path root = sourceRoot();
        String owner = read(root.resolve("presentation/options/OptionsViewModel.java"));
        String state = read(root.resolve("presentation/options/OptionsScreenState.java"));
        String view = read(root.resolve("OptionsView.java"));
        String dashboard = read(root.resolve("DashboardUiState.java"));
        String dashboardOwner = read(root.resolve("TaskViewModel.java"));
        String activity = read(root.resolve("MainActivity.java"));

        assertTrue(owner.contains("MutableStateFlow<OptionsScreenState>"));
        assertTrue(owner.contains("public void dispatch(OptionsAction action)"));
        assertTrue(state.contains("List<OptionsRequest> requests"));
        assertTrue(view.contains("OptionsActionSink"));
        assertTrue(activity.contains("optionsViewModel::dispatch"));
        for (String forbidden : List.of("CalendarUiState", "CalendarPermissionStatus",
                "UiThemeMode", "UpdateUiState", "restTimerDefaultSeconds"))
            assertFalse("Dashboard state retained options field " + forbidden,
                    dashboard.contains(forbidden));
        for (String forbidden : List.of("updateUpdateState", "updateCalendarPermission",
                "onCalendarPermissionAction"))
            assertFalse("Dashboard owner retained options broker " + forbidden,
                    dashboardOwner.contains(forbidden));
        for (String forbidden : List.of("setThemeMode", "setFocusStepLimit",
                "setRestTimerDefaultSeconds",
                "UpdateUiController", "UpdateViewModel", "UpdateEvent"))
            assertFalse("Activity retained options decision " + forbidden,
                    activity.contains(forbidden));
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Path sourceRoot() {
        Path module = Path.of("src/main/java/de/thonktank/autosecretary");
        return Files.isDirectory(module) ? module : Path.of("app").resolve(module);
    }
}
