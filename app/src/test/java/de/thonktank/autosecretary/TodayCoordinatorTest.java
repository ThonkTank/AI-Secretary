package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayCommand;
import de.thonktank.autosecretary.presentation.today.TodayCoordinator;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;

public final class TodayCoordinatorTest {
    @Test public void immediateMoveProducesExactlyOnePersistenceCommand() {
        List<TodayCommand> commands = new ArrayList<>();
        TodayCoordinator coordinator = new TodayCoordinator(today(), commands::add, state -> { });

        coordinator.emit(TodayAction.moveStep("a", null));
        coordinator.emit(TodayAction.moveStep("a", null));

        assertEquals(1, commands.size());
        assertEquals(TodayCommand.Kind.PERSIST_REORDER, commands.get(0).kind);
        assertEquals("a", commands.get(0).id);
    }

    @Test public void centralDispatcherContainsEveryActionKind() throws Exception {
        String source = new String(Files.readAllBytes(Path.of(
                "../today-core/src/main/java/de/thonktank/autosecretary/presentation/today/TodayCoordinator.java")),
                StandardCharsets.UTF_8);
        EnumSet<TodayAction.Kind> handled = EnumSet.noneOf(TodayAction.Kind.class);
        for (TodayAction.Kind kind : TodayAction.Kind.values())
            if (source.contains("case " + kind.name() + ":")) handled.add(kind);

        assertEquals(EnumSet.allOf(TodayAction.Kind.class), handled);
        assertTrue(source.contains("Unhandled Today action"));
    }

    private static TodayUiModel today() {
        FocusTaskUiModel focus = FocusTaskFixtures.task("task", "Task")
                .occurrence("occurrence").steps(Arrays.asList(
                        FocusTaskFixtures.simpleStep("a", "A", false),
                        FocusTaskFixtures.simpleStep("b", "B", false),
                        FocusTaskFixtures.simpleStep("c", "C", false))).build();
        return new TodayUiModel(new XpProgress(0), focus, Collections.emptyList(),
                Collections.emptyList());
    }
}
