package com.autosecretary.application.ai;

import com.autosecretary.domain.WorkItem;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public interface AiProposalGateway {
    @FunctionalInterface
    interface ModelSource { InputStream open() throws Exception; }

    boolean hasModel();
    Future<?> installBundledModel(Runnable onSuccess, Consumer<Throwable> onError);
    Future<?> importModel(ModelSource source, Runnable onSuccess, Consumer<Throwable> onError);
    Future<?> propose(
            String instruction,
            List<WorkItem> current,
            Consumer<BulkChangeProposal> onSuccess,
            Consumer<Throwable> onError);
}
