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
    @Test public void domainIsOwnedByThePureJavaCompilerModule() throws Exception {
        assertTrue(Files.isDirectory(Path.of("../core-domain/src/main/java")));
        assertFalse(hasJava(Path.of(
                "src/main/java/de/thonktank/autosecretary/domain")));
        String build = read(Path.of("../core-domain/build.gradle.kts"));
        assertTrue(build.contains("`java-library`"));
        assertFalse(build.contains("com.android"));
        assertFalse(build.contains("dependencies"));
    }

    @Test public void todayCoreHasOnlyTheDomainCompilerDependency() throws Exception {
        assertTrue(Files.isDirectory(Path.of("../today-core/src/main/java")));
        assertFalse(hasJava(Path.of(
                "src/main/java/de/thonktank/autosecretary/presentation/today")));
        String build = read(Path.of("../today-core/build.gradle.kts"));
        assertTrue(build.contains("`java-library`"));
        assertTrue(build.contains("api(project(\":core-domain\"))"));
        assertFalse(build.contains("com.android"));
        assertFalse(build.contains("androidx"));
    }

    @Test public void todayAndLeafViewsLiveBehindFeaturePackageBoundaries() {
        assertTrue(Files.isDirectory(main("ui/today")));
        assertTrue(Files.isDirectory(main("ui/leaf")));
        for (String view : new String[]{
                "HeaderView.java", "TaskLeafView.java", "CalendarLeafView.java",
                "FocusTaskView.java", "FocusCardView.java", "FocusStepRowView.java",
                "CompletedTodayView.java", "WoodGrainView.java"})
            assertFalse("feature view remains in application root: " + view,
                    Files.exists(main(view)));
    }

    @Test public void activityOwnsNavigationButNotTodayUseCaseDispatch() throws Exception {
        String activity = read(main("MainActivity.java"));
        assertFalse(activity.contains("domain.usecase"));
        assertFalse(activity.contains("TodayAction"));
        assertFalse(activity.contains("completeRemainingSteps.execute"));
        assertFalse(activity.contains("moveTodayStep.execute"));
        assertTrue(activity.contains("viewModel::dispatchToday"));
    }

    @Test public void screensShareInvalidationsWithoutActivityBrokerOrManualReloads()
            throws Exception {
        String activity = read(main("MainActivity.java"));
        String dashboard = read(main("TaskViewModel.java"));
        String catalog = read(main("presentation/alltasks/AllTasksViewModel.java"));
        String options = read(main("presentation/options/OptionsViewModel.java"));
        String container = read(main("AppContainer.java"));
        String widgets = read(main("WidgetUpdateCoordinator.java"));
        String widgetProvider = read(main("TaskWidgetProvider.java"));
        String widgetReceiver = read(main("TaskActionReceiver.java"));

        for (String removed : new String[]{"catalogChanges()", "contentChanges()",
                "minuteHandler", "allTasksViewModel.reload()", "viewModel.load()",
                "viewModel.refresh("})
            assertFalse("manual broker remains: " + removed, activity.contains(removed));
        assertFalse(dashboard.contains("observeDisplayPreferences("));
        assertFalse(dashboard.contains("calendar.observeChanges("));
        assertFalse(dashboard.contains("shutdownNow()"));
        assertFalse(catalog.contains("shutdownNow()"));
        assertFalse(dashboard.contains("invalidateWidgets"));
        assertFalse(read(main("presentation/DashboardPresenter.java"))
                .contains("refreshDomain"));
        assertFalse(widgetReceiver.contains("widgetUpdates.updateAll"));
        assertTrue(container.contains("PresentationInvalidationSource presentationInvalidations"));
        assertTrue(widgets.contains("source.getWidgetChanges()"));
        assertTrue(widgets.contains("LatestReadPipeline.prepared"));
        assertTrue(widgetProvider.contains("reconcileInstalledWidgets()"));
        assertTrue(widgetProvider.contains("stopObserving()"));
        assertTrue(activity.contains("clockInvalidations.materializeForeground()"));
        assertFalse(activity.contains("calendarInvalidations.materializeExternalChange()"));
        assertTrue(options.contains("calendarInvalidated.run()"));
        assertFalse(catalog.contains("current.withCatalog(catalog.execute())"));
        assertTrue(dashboard.indexOf("contentReads.close()")
                < dashboard.indexOf("worker.shutdown()"));
        assertTrue(catalog.indexOf("reads.close()") < catalog.indexOf("worker.shutdown()"));
    }

    @Test public void managementScreenHasOneStateFlowOwnerAndOneTypedActionBoundary()
            throws Exception {
        String viewModel = read(main("presentation/alltasks/AllTasksViewModel.java"));
        String request = read(main("presentation/alltasks/AllTasksRequest.java"));
        String requestState = read(main(
                "presentation/alltasks/AllTasksRequestSavedStateAdapter.java"));
        String coordinator = read(main("presentation/alltasks/AllTasksCoordinator.java"));
        String activity = read(main("MainActivity.java"));

        assertTrue(viewModel.contains("StateFlow<AllTasksScreenState> state()"));
        assertTrue(viewModel.contains("void dispatch(AllTasksAction action)"));
        assertFalse(viewModel.contains("LiveData"));
        assertFalse(viewModel.contains("MutableLiveData"));
        assertFalse(viewModel.contains("UiEvent"));
        assertFalse(viewModel.contains("events()"));
        assertFalse(coordinator.contains("interface Host"));
        assertTrue(coordinator.contains("viewModel.dispatch(AllTasksAction"));
        assertFalse(coordinator.contains("viewModel.update"));
        assertTrue(activity.contains("LegacyStateFlowBinder.observe"));
        assertFalse(activity.contains("allTasksViewModel.events()"));
        assertTrue(viewModel.contains("navigator.navigate(AppDestination."));
        assertFalse(request.contains("OPEN_EDITOR"));
        assertFalse(requestState.contains("putString(LEGACY_STEP_ID"));
        assertFalse(requestState.contains("putBoolean(LEGACY_ADD_STEP"));
        assertFalse(activity.contains("AllTasksRequest.Kind.OPEN_EDITOR"));
        assertFalse(activity.contains("editorViewModel.dispatch(TaskEditorAction.open"));
        assertTrue(Files.exists(Path.of(
                "src/main/kotlin/de/thonktank/autosecretary/presentation/legacy/"
                        + "LegacyStateFlowBinder.kt")));
    }

    @Test public void motionCallbacksCannotOwnEditorNavigation() throws Exception {
        String activity = read(main("MainActivity.java"));
        String open = activity.substring(activity.indexOf("private void openEditorWithFlight()"),
                activity.indexOf("private void completeOrConfirm("));
        String navigator = read(main("presentation/navigation/TaskEditorNavigator.java"));
        assertTrue(open.contains("appNavigator.navigate(AppDestination.newTaskFromHeader())"));
        assertTrue(open.contains("editorCoordinator.deferNextOpen()"));
        assertTrue(open.contains("editorCoordinator::completeDeferredOpen"));
        assertFalse(open.contains("editorViewModel.dispatch"));
        assertTrue(navigator.indexOf("prepareHeaderEntrance.run()")
                < navigator.indexOf("editor.dispatch(TaskEditorAction.openNew())"));

        String renderer = read(main("DashboardRenderer.java"));
        assertTrue(renderer.contains("onAnimationCancel"));
        assertTrue(renderer.contains("onAnimationEnd"));
        assertTrue(renderer.contains("if (completed[0]) return"));
    }

    @Test public void viewModelDelegatesClosedTodayCommandRouting() throws Exception {
        String viewModel = read(main("TaskViewModel.java"));
        String dispatcher = read(main("presentation/today/TodayCommandDispatcher.java"));
        assertTrue(viewModel.contains("new TodayCommandDispatcher(this)"));
        assertFalse(viewModel.contains("switch (value.kind)"));
        assertFalse(viewModel.contains("switch (command.kind)"));
        assertTrue(dispatcher.contains("switch (command.kind)"));
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
        assertTrue(Files.exists(main("domain/repository/DashboardReadRepository.java")));
        assertTrue(Files.exists(main("domain/repository/OccurrenceExecutionRepository.java")));
        assertTrue(Files.exists(main("domain/repository/RewardLedgerRepository.java")));
        assertTrue(Files.exists(main("domain/repository/MaterializationRepository.java")));
    }

    @Test public void todayOrderIsPureAndStepExecutionHasItsOwnService() throws Exception {
        String order = read(main("domain/today/TodayStepOrder.java"));
        assertFalse(order.contains("domain.repository"));
        assertFalse(order.contains("Repository repository"));
        String completion = read(main("domain/usecase/OccurrenceCompletionService.java"));
        assertFalse(completion.contains("toggleStep("));
        assertFalse(completion.contains("recordRepetitionResult("));
        assertFalse(completion.contains("advanceStepWithPlannedResult("));
        assertTrue(Files.exists(main("domain/usecase/StepExecutionService.java")));
        assertTrue(Files.exists(main("domain/usecase/OccurrenceCompletionService.java")));
        assertFalse(Files.exists(main("domain/usecase/CompletionService.java")));
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
        assertFalse(Files.exists(main("domain/repository/TaskRepository.java")));
        forEachJava(main("domain/usecase"), source -> assertFalse(source
                + " imports removed broad repository", read(source).contains(
                "domain.repository.TaskRepository")));
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
        assertTrue(source.contains("DatabaseMigrations.from("));
        assertTrue(source.contains("PRODUCTION_UPGRADE_SOURCE_VERSION"));
        for (int version = 1; version < 8; version++)
            assertFalse("unsupported migration " + version + " registered",
                    source.contains("MIGRATION_" + version + "_" + (version + 1)));
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
        for (String module : new String[]{"../core-domain", "../today-core", "."}) {
            Path source = Path.of(module, "src/main/java/de/thonktank/autosecretary",
                    relative);
            if (Files.exists(source)) return source;
        }
        return Path.of("app/src/main/java/de/thonktank/autosecretary", relative);
    }

    private static void forEachJava(Path directory, CheckedConsumer consumer) throws Exception {
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path source : (Iterable<Path>) files.filter(value -> value.toString()
                    .endsWith(".java"))::iterator) consumer.accept(source);
        }
    }

    private static boolean hasJava(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return false;
        try (Stream<Path> files = Files.walk(directory)) {
            return files.anyMatch(value -> value.toString().endsWith(".java"));
        }
    }

    private static String read(Path source) throws IOException {
        return new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
    }

    private interface CheckedConsumer { void accept(Path source) throws IOException; }
}
