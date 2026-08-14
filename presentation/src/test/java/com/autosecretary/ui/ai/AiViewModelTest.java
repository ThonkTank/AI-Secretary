package com.autosecretary.ui.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.autosecretary.application.ai.AiConsentPort;
import com.autosecretary.application.ai.AiProposalGateway;
import com.autosecretary.application.model.ModelDownloadProgress;
import com.autosecretary.application.model.ModelDownloadTicket;
import com.autosecretary.application.model.ModelRepository;
import com.autosecretary.application.model.ModelStatus;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Path;
import java.util.concurrent.Executors;

public final class AiViewModelTest {
    @Rule public final InstantTaskExecutorRule liveData = new InstantTaskExecutorRule();
    private final java.util.concurrent.ExecutorService io = Executors.newSingleThreadExecutor();

    @After public void close() { io.shutdownNow(); }

    @Test public void modelNetworkStartsOnlyAfterConsentAndExplicitTap() throws Exception {
        FakeConsent consent = new FakeConsent();
        FakeModels models = new FakeModels();
        AiViewModel viewModel = new AiViewModel(new NoopEditor(), consent, models, io,
                Runnable::run);

        assertTrue(viewModel.state().getValue() instanceof AiUiState.Missing);
        assertEquals(0, models.enqueues);
        viewModel.installModel(false);
        assertEquals(0, models.enqueues);
        assertFalse(consent.accepted());

        viewModel.acceptTermsAndInstall();
        await(() -> viewModel.state().getValue() instanceof AiUiState.Ready);
        assertTrue(consent.accepted());
        assertEquals(1, models.enqueues);
        assertEquals(1, models.verifications);
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 3_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) Thread.sleep(5);
        if (!condition.getAsBoolean()) throw new AssertionError("Zustand nicht erreicht");
    }

    private static final class FakeConsent implements AiConsentPort {
        boolean accepted;
        @Override public boolean accepted() { return accepted; }
        @Override public void accept() { accepted = true; }
    }

    private static final class FakeModels implements ModelRepository {
        int enqueues;
        int verifications;
        @Override public ModelStatus status() { return new ModelStatus.Missing(303_950_933); }
        @Override public ModelDownloadTicket enqueue() {
            enqueues++;
            return new ModelDownloadTicket(9, "model", "revision");
        }
        @Override public ModelDownloadProgress query(ModelDownloadTicket ticket) {
            return new ModelDownloadProgress.Complete();
        }
        @Override public Path verifyAndActivate(ModelDownloadTicket ticket) {
            verifications++;
            return Path.of("model.task");
        }
        @Override public void cancel(ModelDownloadTicket ticket) { }
    }

    private record NoopEditor() implements AiProposalGateway {
        @Override public java.util.concurrent.Future<?> propose(
                String instruction,
                java.util.List<com.autosecretary.domain.WorkItem> current,
                java.util.function.Consumer<com.autosecretary.application.ai.BulkChangeProposal> success,
                java.util.function.Consumer<Throwable> error) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
