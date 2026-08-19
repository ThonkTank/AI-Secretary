package de.thonktank.autosecretary.update.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import de.thonktank.autosecretary.update.application.VerifiedUpdate;
import de.thonktank.autosecretary.update.domain.ReleaseMetadata;
import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;

import org.junit.Test;

import java.io.File;
import java.util.Collections;

public final class UpdateUiControllerTest {
    @Test public void resumeHonorsThePresentationConfiguration() {
        FakeFlow enabledFlow = new FakeFlow();
        FakeFlow disabledFlow = new FakeFlow();

        controller(enabledFlow, new FakeDialogs(), new FakePlatform(), true).onResume();
        controller(disabledFlow, new FakeDialogs(), new FakePlatform(), false).onResume();

        assertEquals(1, enabledFlow.automaticChecks);
        assertEquals(0, disabledFlow.automaticChecks);
    }

    @Test public void availableEffectDelegatesBothUserChoicesExactlyOnce() throws Exception {
        FakeFlow flow = new FakeFlow();
        FakeDialogs dialogs = new FakeDialogs();
        UpdateUiController controller = controller(flow, dialogs, new FakePlatform(), true);
        UpdateEvent effect = UpdateEvent.available(updateInfo());

        controller.handleEffect(effect);
        controller.handleEffect(effect);
        assertNotNull(dialogs.available);
        dialogs.postpone.run();
        dialogs.accept.run();

        assertEquals(1, dialogs.availablePrompts);
        assertEquals(1, flow.postpones);
        assertEquals(1, flow.accepts);
    }

    @Test public void installerPermissionAndRetryArePlatformDecisions() throws Exception {
        FakeFlow flow = new FakeFlow();
        FakeDialogs dialogs = new FakeDialogs();
        FakePlatform platform = new FakePlatform();
        UpdateUiController controller = controller(flow, dialogs, platform, true);

        controller.handleEffect(UpdateEvent.install(verifiedUpdate()));
        assertEquals(1, dialogs.permissionPrompts);
        assertEquals(0, platform.installerLaunches);
        dialogs.openSettings.run();
        assertEquals(1, platform.settingsLaunches);

        controller.onInstallPermissionResult();
        assertEquals(0, flow.installRequests);
        platform.installAllowed = true;
        controller.onInstallPermissionResult();
        assertEquals(1, flow.installRequests);
    }

    @Test public void failedInstallerAndReportedFailureUseDialogAndNavigationPorts()
            throws Exception {
        FakeFlow flow = new FakeFlow();
        FakeDialogs dialogs = new FakeDialogs();
        FakePlatform platform = new FakePlatform();
        platform.installAllowed = true;
        platform.installerResult = false;
        UpdateUiController controller = controller(flow, dialogs, platform, true);

        controller.handleEffect(UpdateEvent.install(verifiedUpdate()));
        assertEquals("download failed", dialogs.error);
        dialogs.openReleases.run();
        assertEquals(1, platform.releaseLaunches);

        controller.handleEffect(UpdateEvent.error(UpdateFailure.Kind.NETWORK, null));
        assertEquals("check failed", dialogs.error);
    }

    private static UpdateUiController controller(FakeFlow flow, FakeDialogs dialogs,
                                                 FakePlatform platform,
                                                 boolean automaticChecks) {
        return new UpdateUiController(flow, dialogs, platform, (resource, arguments) -> {
            if (resource == de.thonktank.autosecretary.R.string.error_update_download)
                return "download failed";
            return "check failed";
        }, automaticChecks);
    }

    private static VerifiedUpdate verifiedUpdate() throws Exception {
        File apk = File.createTempFile("verified-update", ".apk");
        apk.deleteOnExit();
        return VerifiedUpdate.fromVerifiedFile(updateInfo(), apk);
    }

    private static UpdateInfo updateInfo() throws Exception {
        ReleaseMetadata metadata = ReleaseMetadata.create(3, "0.2.3",
                "de.thonktank.autosecretary", "AutoSecretary.apk", 1,
                String.join("", Collections.nCopies(64, "a")),
                String.join("", Collections.nCopies(64, "b")),
                String.join("", Collections.nCopies(40, "c")));
        return UpdateInfo.from(metadata, "https://github.com/AutoSecretary.apk");
    }

    private static final class FakeFlow implements UpdateFlow {
        private final MutableLiveData<UpdateUiState> state =
                new MutableLiveData<>(UpdateUiState.idle());
        private final MutableLiveData<UpdateEvent> events = new MutableLiveData<>();
        int automaticChecks;
        int manualActions;
        int accepts;
        int postpones;
        int installRequests;

        @Override public LiveData<UpdateUiState> state() { return state; }
        @Override public LiveData<UpdateEvent> events() { return events; }
        @Override public void automaticCheck() { automaticChecks++; }
        @Override public void manualAction() { manualActions++; }
        @Override public void accept(UpdateInfo update) { accepts++; }
        @Override public void postpone(UpdateInfo update) { postpones++; }
        @Override public void requestInstall() { installRequests++; }
    }

    private static final class FakeDialogs implements UpdateDialogs {
        UpdateInfo available;
        String error;
        Runnable postpone;
        Runnable accept;
        Runnable openSettings;
        Runnable openReleases;
        int availablePrompts;
        int permissionPrompts;

        @Override public void showAvailable(UpdateInfo update, Runnable postpone,
                                            Runnable accept) {
            available = update;
            this.postpone = postpone;
            this.accept = accept;
            availablePrompts++;
        }

        @Override public void showInstallPermission(Runnable openSettings) {
            this.openSettings = openSettings;
            permissionPrompts++;
        }

        @Override public void showError(String message, Runnable openReleases) {
            error = message;
            this.openReleases = openReleases;
        }
    }

    private static final class FakePlatform implements UpdatePlatform {
        boolean installAllowed;
        boolean installerResult = true;
        int installerLaunches;
        int settingsLaunches;
        int releaseLaunches;

        @Override public boolean canInstallPackages() { return installAllowed; }
        @Override public boolean openInstaller(VerifiedUpdate update) {
            installerLaunches++;
            return installerResult;
        }
        @Override public void openInstallSettings() { settingsLaunches++; }
        @Override public void openReleases() { releaseLaunches++; }
    }
}
