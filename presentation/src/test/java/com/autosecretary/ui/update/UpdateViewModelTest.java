package com.autosecretary.ui.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.autosecretary.application.update.UpdateInfo;
import com.autosecretary.application.update.UpdateRepository;
import com.autosecretary.application.update.VerifiedUpdate;
import com.autosecretary.application.update.UpdateCheckResult;
import com.autosecretary.application.update.DownloadTicket;
import com.autosecretary.application.update.DownloadProgress;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Executors;

public final class UpdateViewModelTest {
    @Rule public final InstantTaskExecutorRule liveData = new InstantTaskExecutorRule();
    private final java.util.concurrent.ExecutorService io = Executors.newSingleThreadExecutor();

    @After public void close() { io.shutdownNow(); }

    @Test
    public void permissionAndInstallerAreDistinctOneShotEffects() throws Exception {
        File apk = File.createTempFile("verified-update", ".apk");
        Files.write(apk.toPath(), new byte[] {1});
        UpdateInfo info = update();
        SavedStateHandle saved = new SavedStateHandle();
        UpdateViewModel model = new UpdateViewModel(saved, new FakeRepository(info, apk), io,
                Runnable::run);
        model.check();
        await(() -> model.state().getValue() instanceof UpdateUiState.Available);
        model.download();
        await(() -> model.state().getValue() instanceof UpdateUiState.Ready);

        model.requestInstall(false);
        assertTrue(model.effects().getValue()
                instanceof UpdateUiEffect.OpenUnknownSourcesSettings);
        long settingsId = model.effects().getValue().id();
        model.consumeEffect(settingsId);
        assertNull(model.effects().getValue());
        model.continueInstall(false);
        assertNull(model.effects().getValue());

        model.continueInstall(true);
        assertTrue(model.effects().getValue() instanceof UpdateUiEffect.OpenInstaller);
        model.consumeEffect(model.effects().getValue().id());
        model.continueInstall(true);
        assertNull(model.effects().getValue());
    }

    @Test
    public void verifiedFileAndPendingInstallerSurviveSavedStateRecreation() throws Exception {
        File apk = File.createTempFile("verified-update", ".apk");
        Files.write(apk.toPath(), new byte[] {1});
        UpdateInfo info = update();
        SavedStateHandle firstState = new SavedStateHandle();
        UpdateViewModel first = new UpdateViewModel(firstState, new FakeRepository(info, apk), io,
                Runnable::run);
        first.check();
        await(() -> first.state().getValue() instanceof UpdateUiState.Available);
        first.download();
        await(() -> first.state().getValue() instanceof UpdateUiState.Ready);
        first.requestInstall(false);
        first.consumeEffect(first.effects().getValue().id());

        // SavedStateHandle does not expose its map. Copy the public persisted keys explicitly.
        SavedStateHandle restoredState = new SavedStateHandle();
        restoredState.set("update.versionCode", info.versionCode());
        restoredState.set("update.versionName", info.versionName());
        restoredState.set("update.packageName", info.packageName());
        restoredState.set("update.apkUrl", info.apkUrl());
        restoredState.set("update.apkSize", info.apkSizeBytes());
        restoredState.set("update.sha256", info.sha256());
        restoredState.set("update.signer", info.signerSha256());
        restoredState.set("update.verifiedPath", apk.getAbsolutePath());
        restoredState.set("update.installPending", info.versionCode());
        restoredState.set("update.settingsOpened", true);
        UpdateViewModel restored = new UpdateViewModel(
                restoredState, new FakeRepository(info, apk), io, Runnable::run);

        assertTrue(restored.state().getValue() instanceof UpdateUiState.Ready);
        restored.continueInstall(true);
        assertTrue(restored.effects().getValue() instanceof UpdateUiEffect.OpenInstaller);
        assertEquals(info.versionCode(), ((UpdateUiEffect.OpenInstaller)
                restored.effects().getValue()).update().info().versionCode());
    }

    private static UpdateInfo update() {
        return new UpdateInfo(2001202, "2.1.1", "com.autosecretary",
                "https://github.com/ThonkTank/AI-Secretary/releases/download/"
                        + "android-2001202/AutoSecretary.apk",
                1, "a".repeat(64), "b".repeat(64));
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 3_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) Thread.sleep(5);
        if (!condition.getAsBoolean()) throw new AssertionError("Zustand nicht erreicht");
    }

    private record FakeRepository(UpdateInfo update, File apk) implements UpdateRepository {
        @Override public UpdateCheckResult check() {
            return new UpdateCheckResult.Available(update);
        }
        @Override public DownloadTicket enqueue(UpdateInfo ignored) {
            return new DownloadTicket(7, update.versionCode());
        }
        @Override public DownloadProgress query(DownloadTicket ticket) {
            return new DownloadProgress.Complete();
        }
        @Override public VerifiedUpdate verify(DownloadTicket ticket) {
            return new VerifiedUpdate(update, apk);
        }
        @Override public void cancel(DownloadTicket ticket) { }
        @Override public void cleanup(long installedVersionCode) { }
    }
}
