package de.thonktank.autosecretary;

import de.thonktank.autosecretary.domain.model.StepFlowSetup;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Rotation-safe editable graph and capacity draft, independent from Android widgets. */
public final class FlowSetupDraft {
    public final List<StepTransition> transitions;
    public final List<Lease> leases;

    public FlowSetupDraft(List<StepTransition> transitions, List<Lease> leases) {
        if (transitions == null || leases == null)
            throw new IllegalArgumentException("Flow draft is incomplete");
        this.transitions = Collections.unmodifiableList(new ArrayList<>(transitions));
        this.leases = Collections.unmodifiableList(new ArrayList<>(leases));
    }

    public static FlowSetupDraft empty() {
        return new FlowSetupDraft(Collections.emptyList(), Collections.emptyList());
    }

    public static FlowSetupDraft from(StepFlowSetup setup) {
        if (setup == null) return empty();
        List<Lease> leases = new ArrayList<>();
        for (StepResourceLease lease : setup.resourceLeases)
            leases.add(Lease.from(lease));
        return new FlowSetupDraft(setup.transitions, leases);
    }

    List<StepResourceLease> domainLeases(TaskId taskId) {
        List<StepResourceLease> values = new ArrayList<>();
        for (Lease lease : leases)
            values.add(new StepResourceLease(lease.id, taskId, lease.acquireStepId,
                    lease.releaseStepId, lease.resourceId, lease.units));
        return values;
    }

    public static final class Lease {
        public final String id;
        public final String resourceId;
        public final String acquireStepId;
        public final String releaseStepId;
        public final int units;

        public Lease(String id, String resourceId, String acquireStepId,
                     String releaseStepId, int units) {
            if (id == null || id.isEmpty() || resourceId == null || resourceId.isEmpty()
                    || acquireStepId == null || acquireStepId.isEmpty()
                    || releaseStepId == null || releaseStepId.isEmpty() || units < 1)
                throw new IllegalArgumentException("Capacity rule is incomplete");
            this.id = id;
            this.resourceId = resourceId;
            this.acquireStepId = acquireStepId;
            this.releaseStepId = releaseStepId;
            this.units = units;
        }

        static Lease from(StepResourceLease value) {
            return new Lease(value.id, value.resourceId, value.acquireStepId,
                    value.releaseStepId, value.units);
        }
    }
}
