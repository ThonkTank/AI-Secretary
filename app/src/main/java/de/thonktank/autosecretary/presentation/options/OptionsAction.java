package de.thonktank.autosecretary.presentation.options;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.update.domain.UpdateInfo;
import de.thonktank.autosecretary.domain.model.ComboPolicy;

/** Closed input boundary for options, permissions and the update workflow. */
public abstract class OptionsAction {
    private OptionsAction() { }

    public static final class ThemeSelected extends OptionsAction {
        public final UiThemeMode mode;
        private ThemeSelected(UiThemeMode mode) { this.mode = required(mode); }
    }
    public static final class FocusStepLimitSelected extends OptionsAction {
        public final FocusStepLimit limit;
        private FocusStepLimitSelected(FocusStepLimit limit) { this.limit = required(limit); }
    }
    public static final class RestTimerDefaultChanged extends OptionsAction {
        public final int seconds;
        private RestTimerDefaultChanged(int seconds) {
            if (seconds < 1) throw new IllegalArgumentException("Rest timer must be positive");
            this.seconds = seconds;
        }
    }
    public static final class ComboPolicySelected extends OptionsAction {
        public final ComboPolicy policy;
        private ComboPolicySelected(ComboPolicy policy) { this.policy = required(policy); }
    }
    public static final class PermissionObserved extends OptionsAction {
        public final boolean granted;
        public final boolean showRationale;
        private PermissionObserved(boolean granted, boolean showRationale) {
            this.granted = granted;
            this.showRationale = showRationale;
        }
    }
    public static final class CalendarPermissionSelected extends OptionsAction {
        private CalendarPermissionSelected() { }
    }
    public static final class Resumed extends OptionsAction { private Resumed() { } }
    public static final class ManualUpdateSelected extends OptionsAction {
        private ManualUpdateSelected() { }
    }
    public static final class UpdateAccepted extends OptionsAction {
        public final String requestId;
        public final UpdateInfo update;
        private UpdateAccepted(String requestId, UpdateInfo update) {
            this.requestId = requiredText(requestId);
            this.update = required(update);
        }
    }
    public static final class UpdatePostponed extends OptionsAction {
        public final String requestId;
        public final UpdateInfo update;
        private UpdatePostponed(String requestId, UpdateInfo update) {
            this.requestId = requiredText(requestId);
            this.update = required(update);
        }
    }
    public static final class InstallPermissionResult extends OptionsAction {
        public final boolean granted;
        private InstallPermissionResult(boolean granted) { this.granted = granted; }
    }
    public static final class InstallerFailed extends OptionsAction {
        public final String requestId;
        private InstallerFailed(String requestId) {
            this.requestId = requiredText(requestId);
        }
    }
    public static final class RequestAcknowledged extends OptionsAction {
        public final String requestId;
        private RequestAcknowledged(String requestId) { this.requestId = requiredText(requestId); }
    }

    public static OptionsAction themeSelected(UiThemeMode mode) { return new ThemeSelected(mode); }
    public static OptionsAction focusStepLimitSelected(FocusStepLimit limit) {
        return new FocusStepLimitSelected(limit);
    }
    public static OptionsAction restTimerDefaultChanged(int seconds) {
        return new RestTimerDefaultChanged(seconds);
    }
    public static OptionsAction comboPolicySelected(ComboPolicy policy) {
        return new ComboPolicySelected(policy);
    }
    public static OptionsAction permissionObserved(boolean granted, boolean showRationale) {
        return new PermissionObserved(granted, showRationale);
    }
    public static OptionsAction calendarPermissionSelected() {
        return new CalendarPermissionSelected();
    }
    public static OptionsAction resumed() { return new Resumed(); }
    public static OptionsAction manualUpdateSelected() { return new ManualUpdateSelected(); }
    public static OptionsAction updateAccepted(String requestId, UpdateInfo update) {
        return new UpdateAccepted(requestId, update);
    }
    public static OptionsAction updatePostponed(String requestId, UpdateInfo update) {
        return new UpdatePostponed(requestId, update);
    }
    public static OptionsAction installPermissionResult(boolean granted) {
        return new InstallPermissionResult(granted);
    }
    public static OptionsAction installerFailed(String requestId) {
        return new InstallerFailed(requestId);
    }
    public static OptionsAction acknowledgeRequest(String id) {
        return new RequestAcknowledged(id);
    }

    private static String requiredText(String value) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("Text is required");
        return value;
    }
    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("Action value is required");
        return value;
    }
}
