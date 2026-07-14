package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.R;
import com.autosecretary.app.AppCompositionRoot;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskDataService;
import com.autosecretary.features.task.application.assistant.AssistantChatUseCase;
import com.autosecretary.features.task.application.assistant.AssistantChatUseCase.AssistantTurn;
import com.autosecretary.features.task.application.assistant.ConfirmAssistantProposalUseCase;
import com.autosecretary.features.task.application.UndoTaskChangesUseCase;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.ui.assistant.AssistantUiState.Status;
import com.autosecretary.features.task.ui.assistant.TaskAssistantFragment;
import com.autosecretary.features.task.ui.assistant.TaskAssistantViewModel;
import com.autosecretary.features.task.ui.assistant.TaskAssistantViewModelFactory;
import com.autosecretary.features.task.ui.list.TaskViewModelFactory;
import com.autosecretary.shared.WidgetRefreshNotifier;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.SynchronousExecutorService;

import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class TaskAssistantFragmentLifecycleTest extends AutoSecretaryRobolectricTest {
    private AppCompositionRoot originalRoot;

    @After
    public void tearDown() throws Exception {
        if (originalRoot != null) {
            setCompositionRoot(ApplicationProvider.getApplicationContext(), originalRoot);
        }
    }

    @Test
    public void pendingMdSendSurvivesAssistantFragmentRecreationAndCompletion() throws Exception {
        AutoSecretaryApplication application = ApplicationProvider.getApplicationContext();
        originalRoot = getCompositionRoot(application);
        ManualChatUseCase chatUseCase = new ManualChatUseCase();
        setCompositionRoot(application, new TestCompositionRoot(application, chatUseCase));

        HostActivity activity = Robolectric.buildActivity(HostActivity.class).setup().get();
        TaskAssistantViewModel viewModel = new ViewModelProvider(
                activity, application.getTaskAssistantViewModelFactory()).get(TaskAssistantViewModel.class);
        viewModel.setDraftText("Pflege diese Aufgaben ein");
        viewModel.setPendingAttachment("aufgaben.md", "text/markdown",
                "- [ ] Steuerunterlagen sortieren".getBytes(StandardCharsets.UTF_8));

        showAssistant(activity);

        assertEquals("Pflege diese Aufgaben ein",
                ((EditText) activity.findViewById(R.id.AssistantInstruction)).getText().toString());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.AssistantAttachmentChip).getVisibility());

        assertTrue(viewModel.sendDraft());
        assertTrue(viewModel.isSending());
        assertEquals(Status.PENDING, viewModel.getState().getValue().exchanges().get(0).status());
        assertPendingRendered(activity);

        activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.Container, new Fragment())
                .commitNow();
        showAssistant(activity);

        assertTrue(viewModel.isSending());
        assertPendingRendered(activity);

        chatUseCase.succeed(new AssistantTurn("Vorschlag steht bereit.", "", List.of()));

        assertFalse(viewModel.isSending());
        assertEquals(Status.ANSWERED, viewModel.getState().getValue().exchanges().get(0).status());
        assertEquals(View.GONE, activity.findViewById(R.id.AssistantLoading).getVisibility());
        assertEquals("Vorschlag steht bereit.",
                ((TextView) activity.findViewById(R.id.ExchangeAnswer)).getText().toString());
    }

    private static void assertPendingRendered(HostActivity activity) {
        assertEquals("Pflege diese Aufgaben ein",
                ((TextView) activity.findViewById(R.id.ExchangeUserText)).getText().toString());
        assertTrue(((TextView) activity.findViewById(R.id.ExchangeAttachment))
                .getText().toString().contains("aufgaben.md"));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.AssistantLoading).getVisibility());
        assertEquals(activity.getString(R.string.task_assistant_status_pending),
                ((TextView) activity.findViewById(R.id.ExchangeStatus)).getText().toString());
    }

    private static void showAssistant(HostActivity activity) {
        activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.Container, new TaskAssistantFragment())
                .commitNow();
    }

    private static AppCompositionRoot getCompositionRoot(AutoSecretaryApplication application) throws Exception {
        Field field = AutoSecretaryApplication.class.getDeclaredField("appCompositionRoot");
        field.setAccessible(true);
        return (AppCompositionRoot) field.get(application);
    }

    private static void setCompositionRoot(AutoSecretaryApplication application,
                                           AppCompositionRoot compositionRoot) throws Exception {
        Field field = AutoSecretaryApplication.class.getDeclaredField("appCompositionRoot");
        field.setAccessible(true);
        field.set(application, compositionRoot);
    }

    public static final class HostActivity extends AppCompatActivity {
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            FrameLayout container = new FrameLayout(this);
            container.setId(R.id.Container);
            setContentView(container);
        }
    }

    private static final class TestCompositionRoot extends AppCompositionRoot {
        private final TaskAssistantViewModelFactory assistantFactory;
        private final TaskViewModelFactory taskFactory;
        private final SynchronousExecutorService ioExecutor = new SynchronousExecutorService();

        TestCompositionRoot(Application application, ManualChatUseCase chatUseCase) {
            super(application);
            assistantFactory = new TaskAssistantViewModelFactory(
                    chatUseCase,
                    (ConfirmAssistantProposalUseCase) null,
                    (UndoTaskChangesUseCase) null);
            taskFactory = new NoopTaskViewModelFactory();
        }

        @Override
        public synchronized TaskAssistantViewModelFactory getTaskAssistantViewModelFactory() {
            return assistantFactory;
        }

        @Override
        public synchronized TaskViewModelFactory getTaskViewModelFactory() {
            return taskFactory;
        }

        @Override
        public SynchronousExecutorService getIoExecutor() {
            return ioExecutor;
        }
    }

    private static final class NoopTaskViewModelFactory extends TaskViewModelFactory {
        NoopTaskViewModelFactory() {
            super(noopTaskDataService(), null, null, noopRegenerateUseCase(), null, null, null,
                    new WidgetRefreshNotifier() {
                        @Override
                        public void refreshTaskWidgets() {
                        }

                        @Override
                        public void refreshBudgetWidgets() {
                        }
                    });
        }

        private static TaskDataService noopTaskDataService() {
            Executor direct = Runnable::run;
            return new TaskDataService(null, null, null, new SynchronousExecutorService(), direct, null) {
                @Override
                public void loadAllMapped(Consumer<List<TaskListItem>> onLoaded) {
                    onLoaded.accept(List.of());
                }
            };
        }

        private static RegenerateScheduleUseCase noopRegenerateUseCase() {
            return new RegenerateScheduleUseCase(null, null, new SynchronousExecutorService(),
                    Runnable::run, () -> false) {
                @Override
                public void execute(Consumer<Result> onDone) {
                    onDone.accept(new Result(0, List.of()));
                }
            };
        }
    }

    private static final class ManualChatUseCase extends AssistantChatUseCase {
        private Consumer<AssistantTurn> result;

        ManualChatUseCase() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public void send(String userText, Attachment attachment, boolean thinkingEnabled,
                         Consumer<AssistantTurn> onResult, Consumer<String> onError,
                         Consumer<String> onProgress) {
            result = onResult;
        }

        void succeed(AssistantTurn turn) {
            result.accept(turn);
        }
    }
}
