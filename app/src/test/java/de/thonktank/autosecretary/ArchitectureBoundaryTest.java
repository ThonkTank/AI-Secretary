package de.thonktank.autosecretary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

/** Executable package rules for the management and execution slices. */
public final class ArchitectureBoundaryTest {
    @Test public void domainHasNoAndroidOrPresentationDependencies() throws Exception {
        forEachJava(main("domain"), source -> {
            String text = read(source);
            assertFalse(source + " imports Android", text.contains("import android."));
            assertFalse(source + " imports AndroidX", text.contains("import androidx."));
            assertFalse(source + " imports presentation", text.contains(
                    "de.thonktank.autosecretary.presentation."));
            assertFalse(source + " reads UI resources", text.matches("(?s).*\\bR\\.[a-z]+\\..*"));
        });
    }

    @Test public void todayDoesNotDependOnManagementState() throws Exception {
        forEachJava(main("presentation/today"), source -> {
            String text = read(source);
            assertFalse(source + " imports management", text.contains("presentation.alltasks"));
            assertFalse(source + " names management state", text.contains("AllTasks"));
        });
    }

    @Test public void slicesAndFocusedPortsAreConcretePackageBoundaries() {
        assertTrue(Files.isDirectory(main("presentation/alltasks")));
        assertTrue(Files.isDirectory(main("presentation/today")));
        assertTrue(Files.isDirectory(main("domain/schedule")));
        assertTrue(Files.isDirectory(main("domain/steps")));
        assertTrue(Files.isDirectory(main("domain/today")));
        assertTrue(Files.isDirectory(main("data/local")));
        assertTrue(Files.exists(main("domain/schedule/TaskScheduleRepository.java")));
        assertTrue(Files.exists(main("domain/steps/StepOrganizationRepository.java")));
        assertTrue(Files.exists(main("domain/repository/TodayStepOrderRepository.java")));
    }

    @Test public void todayOrderIsPureAndStepExecutionHasItsOwnService() throws Exception {
        String order = read(main("domain/today/TodayStepOrder.java"));
        assertFalse(order.contains("domain.repository"));
        assertFalse(order.contains("Repository repository"));
        String completion = read(main("domain/usecase/CompletionService.java"));
        assertFalse(completion.contains("toggleStep("));
        assertFalse(completion.contains("recordRepetitionResult("));
        assertFalse(completion.contains("advanceStepWithPlannedResult("));
        assertTrue(Files.exists(main("domain/usecase/StepExecutionService.java")));
    }

    @Test public void managementCommandsDoNotDependOnExecutionOrCompositionPorts()
            throws Exception {
        String executionPort = "domain.repository.TaskRepository";
        String compositionPort = "domain.repository.ApplicationTaskRepository";
        for (String relative : new String[]{
                "domain/usecase/CreateTask.java",
                "domain/usecase/UpdateTask.java",
                "domain/schedule/MoveScheduleEntry.java",
                "domain/schedule/MoveTaskPlacement.java",
                "domain/steps/MoveTaskStep.java",
                "domain/steps/SwapTaskSteps.java"}) {
            String source = read(main(relative));
            assertFalse(relative + " imports execution repository", source.contains(executionPort));
            assertFalse(relative + " imports composition repository", source.contains(compositionPort));
        }
        String execution = read(main("domain/repository/TaskRepository.java"));
        assertFalse(execution.contains("extends TaskScheduleRepository"));
        assertFalse(execution.contains("StepOrganizationRepository"));
    }

    @Test public void managementPortTestsUseFocusedDoubles() throws Exception {
        Path tests = Path.of("app/src/test/java/de/thonktank/autosecretary/domain");
        if (!Files.exists(tests)) tests = Path.of("src/test/java/de/thonktank/autosecretary/domain");
        forEachJava(tests.resolve("schedule"), source -> assertFalse(source
                + " uses execution acceptance store", read(source).contains(
                "InMemoryExecutionRepository")));
        forEachJava(tests.resolve("steps"), source -> assertFalse(source
                + " uses execution acceptance store", read(source).contains(
                "InMemoryExecutionRepository")));
    }

    @Test public void productionMigrationGraphStartsAtSupportedSchemaEight() throws Exception {
        String source = read(main("data/local/DatabaseFactory.java"));
        assertTrue(source.contains("MIGRATION_8_9"));
        for (int version = 1; version < 8; version++)
            assertFalse("unsupported migration " + version + " registered",
                    source.contains("MIGRATION_" + version + "_" + (version + 1) + ","));
    }

    @Test public void removedCompatibilityMutationFacadesStayRemoved() throws Exception {
        assertFalse(Files.exists(main("TaskService.java")));
        assertFalse(Files.exists(main("domain/usecase/MoveTask.java")));
        String update = read(main("domain/usecase/UpdateTask.java"));
        assertFalse(update.contains("legacyUpdate"));
        assertFalse(update.contains("execute(TaskId id, String title"));
        String create = read(main("domain/usecase/CreateTask.java"));
        assertFalse(create.contains("execute(String title"));
        String task = read(main("domain/model/Task.java"));
        assertFalse(task.contains("ignoredSlot"));
        assertFalse(task.contains("ignoredTimeOfDayMask"));
        String editor = read(main("EditorUiState.java"));
        assertFalse(editor.contains("legacySteps"));
        assertFalse(editor.contains("public boolean ongoing"));
    }

    private static Path main(String relative) {
        Path module = Path.of("src/main/java/de/thonktank/autosecretary", relative);
        return Files.exists(module) ? module
                : Path.of("app/src/main/java/de/thonktank/autosecretary", relative);
    }

    private static void forEachJava(Path directory, CheckedConsumer consumer) throws Exception {
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path source : (Iterable<Path>) files.filter(value -> value.toString()
                    .endsWith(".java"))::iterator) consumer.accept(source);
        }
    }

    private static String read(Path source) throws IOException {
        return new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
    }

    private interface CheckedConsumer { void accept(Path source) throws IOException; }
}
