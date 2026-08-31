package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.domain.model.MissedOccurrenceMode;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAssistantPolicy;
import de.thonktank.autosecretary.domain.model.TrainingAssistantProfile;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingDecision;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.model.TrainingPrescription;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TrainingLoadRequestPersistenceRobolectricTest {
    private static final String DATABASE = "training-load-request-restart";

    @Test public void openQuestionSurvivesRepositoryAndDatabaseRecreation() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE);
        AppDatabase first = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .allowMainThreadQueries().build();
        RoomTaskRepository repository = new RoomTaskRepository(first);
        TaskId taskId = TaskId.of("task");
        LocalDate today = LocalDate.of(2026, 8, 31);
        repository.insertTask(Task.restore(taskId, "Training", Recurrence.DAILY, 1, 0,
                false, "", false, false, today, null, null, today, 1, false, null,
                TaskBoundKind.FOREVER, null, null, null, null, "",
                MissedOccurrenceMode.COLLAPSE));
        ResistanceLoad load = ResistanceLoad.numeric(ResistanceLoad.Mode.EXTERNAL,
                ResistanceLoad.Unit.KG, 50_000);
        TaskStepTemplate template = new TaskStepTemplate("step", taskId, 0, "Rudern", 0, 0,
                new StepPrescription(StepAmount.setsReps(3, 12), RestTimerPolicy.inherit(),
                        new TrainingPrescription(load, 2)),
                new TrainingAssistantProfile(TrainingAssistantPolicy.defaults(
                        TrainingMuscleGroup.BACK), new TrainingAssistantState(
                        TrainingAssistantState.Status.ACTIVE, 5, 0, 0)), "",
                StepActivationKind.SCHEDULED);
        repository.insertTemplates(Collections.singletonList(template));
        repository.insertTrainingLoadRequest(TrainingLoadRequest.open("request", template.id,
                "occ-step", TrainingDecision.LoadDirection.PROGRESS, load, today,
                repository.nextTrainingAuditOrder(), TrainingDecision.RULE_VERSION));
        first.close();

        AppDatabase reopened = Room.databaseBuilder(context, AppDatabase.class, DATABASE)
                .allowMainThreadQueries().build();
        TrainingLoadRequest restored = new RoomTaskRepository(reopened)
                .openTrainingLoadRequest(template.id);
        assertNotNull(restored);
        assertEquals(TrainingDecision.LoadDirection.PROGRESS, restored.direction);
        assertEquals(Long.valueOf(50_000), restored.currentLoad.milliUnits);
        assertEquals(TrainingDecision.RULE_VERSION, restored.ruleVersion);
        reopened.close();
        context.deleteDatabase(DATABASE);
    }
}
