package de.thonktank.autosecretary.presentation.alltasks;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Atomic management-screen state: render model and ordered confirmable host work. */
public final class AllTasksScreenState {
    public final AllTasksUiState content;
    public final List<AllTasksRequest> requests;

    public AllTasksScreenState(AllTasksUiState content, List<AllTasksRequest> requests) {
        if (content == null || requests == null)
            throw new IllegalArgumentException("Complete screen state is required");
        this.content = content;
        this.requests = Collections.unmodifiableList(new ArrayList<>(requests));
    }

    public AllTasksScreenState withContent(AllTasksUiState value) {
        return new AllTasksScreenState(value, requests);
    }

    public AllTasksScreenState enqueue(AllTasksRequest value) {
        for (AllTasksRequest request : requests) if (request.sameWorkAs(value)) return this;
        ArrayList<AllTasksRequest> next = new ArrayList<>(requests);
        next.add(value);
        return new AllTasksScreenState(content, next);
    }

    public AllTasksScreenState acknowledge(String id) {
        ArrayList<AllTasksRequest> next = new ArrayList<>(requests.size());
        for (AllTasksRequest request : requests) if (!request.id.equals(id)) next.add(request);
        if (next.size() == requests.size()) return this;
        return new AllTasksScreenState(content, next);
    }

    @Nullable public AllTasksRequest request(String id) {
        for (AllTasksRequest request : requests) if (request.id.equals(id)) return request;
        return null;
    }

    @Nullable public AllTasksRequest firstRequest() {
        return requests.isEmpty() ? null : requests.get(0);
    }
}
