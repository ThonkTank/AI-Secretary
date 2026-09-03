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
        assertTrue(Files.exists(main("presentation/today/TodayViewModel.java")));
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

    @Test public void activityHostsTodayRequestsButNotTodayUseCaseDispatch() throws Exception {
        String activity = read(main("MainActivity.java"));
        assertFalse(activity.contains("domain.usecase"));
        assertFalse(activity.contains("completeRemainingSteps.execute"));
        assertFalse(activity.contains("moveTodayStep.execute"));
        assertFalse(activity.contains(".execute("));
        assertTrue(activity.contains("todayViewModel::dispatch"));
        assertTrue(activity.contains("TodayAction.acknowledgeRequest"));
    }

    @Test public void screensShareInvalidationsWithoutActivityBrokerOrManualReloads()
            throws Exception {
        String activity = read(main("MainActivity.java"));
        String dashboard = read(main("presentation/today/TodayViewModel.java"));
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
        String actionSink = read(main("presentation/alltasks/AllTasksActionSink.java"));
        String composeHost = read(Path.of("src/main/kotlin/de/thonktank/autosecretary/"
                + "presentation/alltasks/AllTasksComposeHostView.kt"));
        String activity = read(main("MainActivity.java"));

        assertTrue(viewModel.contains("StateFlow<AllTasksScreenState> state()"));
        assertTrue(viewModel.contains("void dispatch(AllTasksAction action)"));
        assertFalse(viewModel.contains("LiveData"));
        assertFalse(viewModel.contains("MutableLiveData"));
        assertFalse(viewModel.contains("UiEvent"));
        assertFalse(viewModel.contains("events()"));
        assertTrue(actionSink.contains("void emit(AllTasksAction action)"));
        assertTrue(composeHost.contains("emit(AllTasksAction.queryChanged(it))"));
        assertTrue(composeHost.contains("this.screenState = state"));
        assertFalse(Files.exists(main("presentation/alltasks/AllTasksCoordinator.java")));
        assertTrue(activity.contains("allTasksViewModel::dispatch"));
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
                activity.indexOf("private void renderAllTasksState("));
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
        String viewModel = read(main("presentation/today/TodayViewModel.java"));
        String dispatcher = read(main("presentation/today/TodayCommandDispatcher.java"));
        assertTrue(viewModel.contains("new TodayCommandDispatcher(this)"));
        assertFalse(viewModel.contains("switch (value.kind)"));
        assertFalse(viewModel.contains("switch (command.kind)"));
        assertTrue(dispatcher.contains("switch (command.kind)"));
    }

    @Test public void todayAndShellHaveOneStateFlowOwnerWithoutLegacyBrokers()
            throws Exception {
        String today = read(main("presentation/today/TodayViewModel.java"));
        String state = read(main("presentation/today/TodayScreenState.java"));
        String shell = read(main("presentation/shell/AppShellViewModel.java"));
        String renderer = read(main("DashboardRenderer.java"));
        String activity = read(main("MainActivity.java"));

        assertTrue(today.contains("StateFlow<TodayScreenState> state()"));
        assertTrue(today.contains("public void dispatch(TodayAction action)"));
        assertTrue(state.contains("List<TodayRequest> requests"));
        assertTrue(state.contains("RewardEffectQueue.Snapshot rewards"));
        assertFalse(today.contains("LiveData"));
        assertFalse(today.contains("MutableLiveData"));
        assertTrue(shell.contains("MutableStateFlow<AppShellScreenState>"));
        assertTrue(renderer.contains("TodayAction.openTaskMenu"));
        assertFalse(renderer.contains("DashboardEvent"));
        assertFalse(activity.contains("TaskViewModel"));
        assertFalse(activity.contains("DashboardUiState"));
        assertFalse(activity.contains("UiEvent"));
        assertFalse(Files.exists(main("TaskViewModel.java")));
        assertFalse(Files.exists(main("DashboardUiState.java")));
        assertFalse(Files.exists(main("DashboardEvent.java")));
        assertFalse(Files.exists(main("UiEvent.java")));
    }

    @Test public void slicesAndFivePersistencePortsAreConcretePackageBoundaries()
            throws Exception {
        assertTrue(Files.isDirectory(main("presentation/alltasks")));
        assertTrue(Files.isDirectory(main("presentation/today")));
        assertTrue(Files.isDirectory(main("domain/schedule")));
        assertTrue(Files.isDirectory(main("domain/steps")));
        assertTrue(Files.isDirectory(main("domain/today")));
        assertTrue(Files.isDirectory(main("data/local")));
        for (String port : new String[]{"Catalog", "Step", "Today", "Flow", "Training"}) {
            Path source = main("domain/repository/" + port + "Repository.java");
            assertTrue(port, Files.exists(source));
            assertFalse(port + " port inherits another capability",
                    read(source).contains(" extends "));
        }
        for (String removed : new String[]{"TaskDefinition", "DashboardRead",
                "OccurrenceExecution", "RewardLedger", "Materialization", "TodayStepOrder",
                "ComboObligation", "StepFlowDefinition", "StepFlowRun"})
            assertFalse(Files.exists(main("domain/repository/" + removed + "Repository.java")));
        assertFalse(Files.exists(main("domain/schedule/TaskSchedule" + "Repository.java")));
        assertFalse(Files.exists(main("domain/steps/StepOrganization" + "Repository.java")));
    }

    @Test public void domainAndPresentationImportNeitherRoomNorDataLocalTypes()
            throws Exception {
        for (Path boundary : new Path[]{
                Path.of("../core-domain/src/main/java/de/thonktank/autosecretary/domain"),
                Path.of("../today-core/src/main/java/de/thonktank/autosecretary/presentation"),
                Path.of("src/main/java/de/thonktank/autosecretary/presentation"),
                Path.of("src/main/kotlin/de/thonktank/autosecretary/presentation")}) {
            forEachProductionSource(boundary, source -> {
                for (String line : read(source).split("\\R")) {
                    String value = line.trim();
                    if (!value.startsWith("import ")) continue;
                    assertFalse(source + " imports data.local", value.contains(".data.local."));
                    assertFalse(source + " imports a Room type",
                            value.matches(".*\\.Room[A-Za-z0-9_]*;?"));
                }
            });
        }
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
        for (String relative : new String[]{
                "domain/usecase/CreateTask.java",
                "domain/usecase/UpdateTask.java",
                "domain/schedule/MoveScheduleEntry.java",
                "domain/schedule/MoveTaskPlacement.java",
                "domain/steps/MoveTaskStep.java",
                "domain/steps/SwapTaskSteps.java"}) {
            String source = read(main(relative));
            assertFalse(relative + " imports execution repository", source.contains(executionPort));
            assertFalse(relative + " imports removed composition store",
                    source.contains("data.local.Task" + "Store"));
        }
        assertFalse(Files.exists(main("domain/repository/TaskRepository.java")));
        forEachJava(main("domain/usecase"), source -> assertFalse(source
                + " imports removed broad repository", read(source).contains(
                "domain.repository.TaskRepository")));
    }

    @Test public void compositionUsesFocusedBundlesPortsAndTransactionRunner() throws Exception {
        assertFalse(Files.exists(main("domain/repository/ApplicationTaskRepository.java")));
        assertFalse(Files.exists(main("domain/repository/TransactionalRepository.java")));
        assertFalse(Files.exists(main("domain/repository/FlowExecutionRepository.java")));
        assertFalse(Files.exists(main("domain/usecase/TaskUseCases.java")));
        assertTrue(Files.exists(main("domain/transaction/TransactionRunner.java")));
        for (String bundle : new String[]{"CatalogUseCases.java", "TodayUseCases.java",
                "FlowUseCases.java", "TrainingUseCases.java"})
            assertTrue(bundle, Files.exists(main("domain/usecase/" + bundle)));

        forEachJava(main("domain/repository"), source -> {
            String value = read(source);
            assertFalse(source + " inherits transaction execution",
                    value.contains("extends TransactionRunner")
                            || value.contains("extends TransactionalRepository"));
        });
        forEachJava(main("domain/usecase"), source -> {
            String value = read(source);
            assertFalse(source + " calls a repository transaction implicitly",
                    value.contains("repository.transactions.inTransaction(")
                            || value.contains("occurrences.inTransaction(")
                            || value.contains("training.inTransaction(")
                            || value.contains("ledger.inTransaction(")
                            || value.contains("flows.inTransaction(")
                            || value.contains("tasks.inTransaction("));
            assertFalse(source + " discovers repository capabilities at runtime",
                    value.matches("(?s).*instanceof\\s+[A-Za-z0-9_]+Repository.*"));
        });

        String container = read(main("AppContainer.java"));
        assertTrue(container.contains("CatalogUseCases catalog"));
        assertTrue(container.contains("TodayUseCases today"));
        assertTrue(container.contains("FlowUseCases flows"));
        assertTrue(container.contains("TrainingUseCases training"));
        assertFalse(container.contains("TaskUseCases"));
        assertFalse(container.contains("container.tasks"));
    }

    @Test public void roomPersistenceHasExactlyFiveFocusedAdaptersAndOneRunner()
            throws Exception {
        String composition = read(main("ApplicationUseCaseComposition.java"));
        String database = read(main("AppDatabase.java"));
        for (String slice : new String[]{"Catalog", "Step", "Today", "Flow", "Training"}) {
            String port = slice + "Repository";
            String adapter = "Room" + port;
            String dao = slice + "Dao";
            Path adapterSource = main("data/local/" + adapter + ".java");
            assertTrue(adapter, Files.exists(adapterSource));
            assertTrue(dao, Files.exists(main("data/local/" + dao + ".java")));
            String source = read(adapterSource);
            assertTrue(adapter + " implements its port", source.contains("implements " + port));
            assertFalse(adapter + " implements multiple ports",
                    source.contains("implements " + port + ","));
            assertTrue("composition creates " + adapter + " exactly once",
                    occurrences(composition, "new " + adapter + "(database") == 1);
            assertTrue("database exposes " + dao + " exactly once",
                    occurrences(database, "abstract " + dao + " ") == 1);
        }
        assertTrue(Files.exists(main("data/local/RoomTransactionRunner.java")));
        assertTrue(occurrences(composition, "new RoomTransactionRunner(database)") == 1);
        int productionRunnerCreations = 0;
        for (String source : new String[]{read(main("ApplicationUseCaseComposition.java")),
                read(main("data/local/RoomCatalogRepository.java")),
                read(main("data/local/RoomStepRepository.java")),
                read(main("data/local/RoomTodayRepository.java")),
                read(main("data/local/RoomFlowRepository.java")),
                read(main("data/local/RoomTrainingRepository.java"))})
            productionRunnerCreations += occurrences(source,
                    "new RoomTransactionRunner(database)");
        assertTrue(productionRunnerCreations == 1);
        assertFalse(Files.exists(main("data/local/Task" + "Store.java")));
        assertFalse(Files.exists(main("data/local/RoomTask" + "Repository.java")));
        assertFalse(Files.exists(main("data/local/Task" + "Dao.java")));
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
        for (String trainingSlice : new String[]{"ResolveTrainingLoadRequestTest.java",
                "LoadTrainingContextTest.java", "UndoLatestTrainingAdjustmentTest.java"}) {
            String source = read(tests.resolve("usecase").resolve(trainingSlice));
            assertFalse(trainingSlice + " uses execution acceptance store",
                    source.contains("InMemoryExecutionRepository"));
            assertTrue(trainingSlice + " uses focused training store",
                    source.contains("InMemoryTrainingRepository"));
        }
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

    @Test public void trainingDomainHasOneCanonicalStepAndResultLanguage() throws Exception {
        assertFalse(Files.exists(main("domain/model/TrainingAssistant" + "Config.java")));
        String template = read(main("domain/model/TaskStepTemplate.java"));
        String definition = read(main("domain/model/TaskStepDefinition.java"));
        String occurrence = read(main("domain/model/OccurrenceStep.java"));
        String flow = read(main("domain/model/FlowRunStepSnapshot.java"));
        for (String source : new String[]{template, definition, occurrence, flow}) {
            assertTrue(source.contains("public final StepPrescription prescription"));
            assertFalse(source.contains("public final StepAmount amount"));
            assertFalse(source.contains("public final RestTimerPolicy restTimerPolicy"));
            assertFalse(source.contains("public final ResistanceLoad plannedLoad"));
            assertFalse(source.contains("public final int targetRir"));
            assertFalse(source.contains("legacyTraining" + "Config"));
        }
        String progress = read(main("domain/model/RepetitionProgress.java"));
        assertTrue(progress.contains("public final List<SetResult> results"));
        assertTrue(progress.contains("public List<Integer> repetitions()"));
        assertFalse(progress.contains("actual" + "Repetitions"));
        assertFalse(progress.contains("public static RepetitionProgress restore("));
        String decision = read(main("domain/model/TrainingDecision.java"));
        assertTrue(decision.contains("public final StepPrescription nextPrescription"));
        assertTrue(decision.contains("public final TrainingAssistantState nextState"));
        assertFalse(decision.contains("public final StepAmount.SetsReps prescription"));
        assertFalse(decision.contains("public final ResistanceLoad load"));
        assertFalse(decision.contains("public final TrainingAssistantState state"));
    }

    @Test public void trainingAssistantUiHasOnePanelActionAndHandlerOwner() throws Exception {
        String row = read(main("ui/today/FocusStepRowView.java"));
        String panel = read(main("ui/today/TrainingAssistantPanelView.java"));
        String action = read(main("presentation/today/TrainingAssistantUiAction.java"));
        String todayAction = read(main("presentation/today/TodayAction.java"));
        String handler = read(main("presentation/today/TrainingAssistantActionHandler.java"));
        String viewModel = read(main("presentation/today/TodayViewModel.java"));

        assertTrue(row.contains("new TrainingAssistantPanelView"));
        assertTrue(row.contains("assistantPanel.bind("));
        assertFalse(row.contains("parseLoad("));
        assertFalse(row.contains("loadQuestion"));
        assertFalse(row.contains("undoAdjustment"));
        assertTrue(panel.contains("TrainingAssistantUiAction.ApplyLoad"));
        assertTrue(panel.contains("TrainingAssistantUiAction.NoHigherLoad"));
        assertTrue(panel.contains("TrainingAssistantUiAction.Later"));
        assertTrue(panel.contains("TrainingAssistantUiAction.Undo"));
        assertTrue(action.contains("public final String rawLoad"));
        assertTrue(action.contains("ResistanceLoad.Mode currentMode"));
        assertTrue(action.contains("ResistanceLoad.Unit currentUnit"));
        assertTrue(todayAction.contains("TRAINING_ASSISTANT"));
        assertFalse(todayAction.contains("APPLY_" + "TRAINING_LOAD"));
        assertFalse(todayAction.contains("NO_HIGHER_" + "TRAINING_LOAD"));
        assertFalse(todayAction.contains("LATER_" + "TRAINING_LOAD"));
        assertFalse(todayAction.contains("UNDO_" + "TRAINING_ADJUSTMENT"));
        assertTrue(handler.contains(
                "TrainingAssistantActionHandler(TrainingUseCases training)"));
        assertFalse(handler.contains("UiTextProvider"));
        assertFalse(handler.contains("TodayUiModel"));
        assertTrue(handler.contains("new BigDecimal"));
        assertTrue(handler.contains("replace(',', '.')"));
        assertTrue(handler.contains("movePointRight(3).longValueExact()"));
        assertTrue(viewModel.contains("trainingAssistantActions.handle(action)"));
        assertFalse(viewModel.contains("ResolveTrainingLoadRequest"));
        assertFalse(viewModel.contains("ResistanceLoad"));
        assertFalse(viewModel.contains("resolveTrainingLoadRequest"));
        assertFalse(viewModel.contains("undoLatestTrainingAdjustment"));
    }

    @Test public void stepSnapshotsHaveOneFactoryAndNamedRehydrationBoundaries()
            throws Exception {
        Path domain = Path.of("../core-domain/src/main/java");
        Path factory = domain.resolve(
                "de/thonktank/autosecretary/domain/usecase/StepSnapshotFactory.java");
        String factorySource = read(factory);
        assertTrue(factorySource.contains("fromTemplate("));
        assertTrue(factorySource.contains("carryForward("));
        assertTrue(factorySource.contains("fromFlow("));
        assertTrue(factorySource.contains("flowRun("));

        forEachProductionSource(domain, source -> {
            String name = source.getFileName().toString();
            String value = read(source);
            if (!name.equals("OccurrenceStep.java"))
                assertFalse(source + " creates OccurrenceStep directly",
                        value.contains("new OccurrenceStep("));
            if (!name.equals("FlowRunStepSnapshot.java"))
                assertFalse(source + " creates FlowRunStepSnapshot directly",
                        value.contains("new FlowRunStepSnapshot("));
            if (!name.equals("StepSnapshotFactory.java")) {
                assertFalse(source + " crosses occurrence rehydration boundary",
                        value.contains("OccurrenceStep.rehydrate("));
                assertFalse(source + " crosses flow rehydration boundary",
                        value.contains("FlowRunStepSnapshot.rehydrate("));
            }
        });
        forEachProductionSource(Path.of("src/main/java"), source -> {
            String value = read(source);
            boolean mapper = source.endsWith(Path.of("TaskEntityMapper.java"))
                    || source.endsWith(Path.of("StepFlowEntityMapper.java"));
            assertFalse(source + " creates a snapshot directly",
                    value.contains("new OccurrenceStep(")
                            || value.contains("new FlowRunStepSnapshot("));
            if (!mapper) {
                assertFalse(source + " crosses occurrence rehydration boundary",
                        value.contains("OccurrenceStep.rehydrate("));
                assertFalse(source + " crosses flow rehydration boundary",
                        value.contains("FlowRunStepSnapshot.rehydrate("));
            }
        });
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

    private static void forEachProductionSource(Path directory, CheckedConsumer consumer)
            throws Exception {
        if (!Files.isDirectory(directory)) return;
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path source : (Iterable<Path>) files.filter(value -> {
                String name = value.toString();
                return name.endsWith(".java") || name.endsWith(".kt");
            })::iterator) consumer.accept(source);
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

    private static int occurrences(String value, String fragment) {
        int count = 0;
        int start = 0;
        while ((start = value.indexOf(fragment, start)) >= 0) {
            count++;
            start += fragment.length();
        }
        return count;
    }

    private interface CheckedConsumer { void accept(Path source) throws IOException; }
}
