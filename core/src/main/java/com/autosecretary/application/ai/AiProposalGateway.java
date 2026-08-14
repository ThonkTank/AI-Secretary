package com.autosecretary.application.ai;

import com.autosecretary.domain.WorkItem;

import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public interface AiProposalGateway {
    Future<?> propose(
            String instruction,
            List<WorkItem> current,
            Consumer<BulkChangeProposal> onSuccess,
            Consumer<Throwable> onError);
}
