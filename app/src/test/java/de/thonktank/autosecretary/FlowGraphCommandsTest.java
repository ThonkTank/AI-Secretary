package de.thonktank.autosecretary;

import static de.thonktank.autosecretary.FlowGraphExecutionTest.*;
import static org.junit.Assert.*;

import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import de.thonktank.autosecretary.domain.usecase.FlowGraphCommands;
import de.thonktank.autosecretary.domain.usecase.FlowGraphExecution;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** Exercises command atomicity with a rollback-capable, serializable store. Room coverage follows its adapter. */
public final class FlowGraphCommandsTest {
    @Test public void concurrentLaundryStartsReserveExactlyThreeDryingPlaces() throws Exception {
        Memory store = new Memory(Map.of("rack", 3, "machine", 1));
        FlowGraphDefinition laundry = laundry(false);
        List<FlowGraphCommands.Result> results = concurrently(12,
                i -> store.start("candidate:" + i, laundry, "wash", null, 2));
        assertEquals(3, results.stream().filter(result -> result.status == FlowGraphCommands.Status.CHANGED).count());
        assertEquals(3, store.runs.size());
        assertEquals(Long.valueOf(3), store.capacityExcluding("absent").otherRunsUsed.get("rack"));
        assertEquals(0, store.credits.size());
    }

    @Test public void washingMachineStaysOccupiedForTheWashWaitThenNextStartCanUseIt() {
        Memory store = new Memory(Map.of("rack", 3, "machine", 1));
        FlowGraphDefinition laundry = laundry(true);
        FlowGraphCommands.Result first = store.start("a", laundry, "wash", 20L, 2);
        assertEquals(FlowGraphCommands.Status.CHANGED, first.status);
        assertEquals(FlowGraphCommands.Status.CAPACITY_UNAVAILABLE,
                store.start("b", laundry, "wash", 20L, 2).status);
        store.now = 20L;
        FlowGraphRun ready = store.commands.settle(first.run.id).run;
        assertEquals(FlowGraphRun.State.AVAILABLE, node(ready, "hang").state);
        assertEquals(FlowGraphCommands.Status.CHANGED,
                store.start("b", laundry, "wash", 20L, 2).status);
    }

    @Test public void promptCancellationCreatesNoRunReservationOrReward() {
        Memory store = new Memory(Map.of("rack", 3, "machine", 1));
        assertEquals(FlowGraphCommands.Status.DURATION_REQUIRED,
                store.start("a", laundry(true), "wash", null, 2).status);
        assertTrue(store.runs.isEmpty());
        assertTrue(store.sources.isEmpty());
        assertTrue(store.credits.isEmpty());
    }

    @Test public void concurrentDoubleStartAndLastActionHaveOneRunAndOneCredit() throws Exception {
        Memory store = new Memory(Collections.emptyMap());
        FlowGraphDefinition flow = definition("s,a,b", "s:a,s:b", Collections.emptyMap());
        concurrently(8, i -> store.start("same-candidate", flow, "s", null, 2));
        assertEquals(1, store.runs.size());
        FlowGraphRun run = store.runs.values().iterator().next();
        concurrently(8, i -> store.commands.complete(run.id,
                node(run, i % 2 == 0 ? "a" : "b").id, null, i % 2 == 0 ? 3 : 5));
        assertEquals(1, store.credits.size());
        assertEquals(Long.valueOf(10), store.credits.get(run.collectionKey()));
        assertTrue(store.find(run.id).collected);
    }

    @Test public void lastWaitCollectionIsIdempotentAcrossCommandRecreation() throws Exception {
        Memory store = new Memory(Collections.emptyMap());
        FlowGraphRun run = store.start("s", definition("s", "",
                Map.of("s", FlowDelayPolicy.fixed(10))), "s", null, 4).run;
        store.now = 10L;
        store.commands.settle(run.id);
        assertTrue(store.credits.isEmpty());
        FlowGraphCommands recreated = new FlowGraphCommands(store, store, () -> store.now,
                () -> "restored:" + store.ids.incrementAndGet());
        concurrently(8, i -> recreated.collect(run.id));
        assertEquals(1, store.credits.size());
        assertEquals(Long.valueOf(4), store.credits.get(run.collectionKey()));
    }

    @Test public void ledgerFailureRollsBackActionClaimsAndCollectionMarkerTogether() {
        Memory store = new Memory(Map.of("rack", 1));
        FlowGraphDefinition flow = withLeases(definition("s,f", "s:f", Collections.emptyMap()),
                new FlowGraphDefinition.Lease("r", "rack", "s", "f", 1, false));
        FlowGraphRun run = store.start("a", flow, "s", null, 2).run;
        store.failCredit = true;
        assertThrows(IllegalStateException.class,
                () -> store.commands.complete(run.id, node(run, "f").id, null, 3));
        assertSame(run, store.find(run.id));
        assertFalse(store.find(run.id).collected);
        assertEquals(FlowResourceState.ACTIVE, store.find(run.id).leases.get(0).state);
        assertTrue(store.credits.isEmpty());
        store.failCredit = false;
        assertEquals(FlowGraphCommands.Status.CHANGED,
                store.commands.complete(run.id, node(run, "f").id, null, 3).status);
        assertEquals(Long.valueOf(5), store.credits.get(run.collectionKey()));
    }

    @Test public void capacityReductionPreservesExistingRunsAndRefusesOnlyNewClaims() {
        Memory store = new Memory(Map.of("rack", 3, "machine", 1));
        FlowGraphRun a = store.start("a", laundry(false), "wash", null, 2).run;
        FlowGraphRun b = store.start("b", laundry(false), "wash", null, 2).run;
        store.total.put("rack", 1);
        assertEquals(FlowGraphCommands.Status.CAPACITY_UNAVAILABLE,
                store.start("c", laundry(false), "wash", null, 2).status);
        store.commands.settle(a.id);
        store.commands.settle(b.id);
        assertEquals(Long.valueOf(2), store.capacityExcluding("none").otherRunsUsed.get("rack"));
    }

    @Test public void admissionReloadsTheCurrentDefinitionAndCannotStartARemovedCandidate() {
        Memory store = new Memory(Collections.emptyMap());
        assertEquals(FlowGraphCommands.Status.NOT_FOUND, store.commands.start("missing", null, 2).status);
        store.candidates.put("a", new FlowGraphCommands.Start(
                definition("s", "", Collections.emptyMap()), "s"));
        store.candidates.put("a", new FlowGraphCommands.Start(
                definition("s", "", Map.of("s", FlowDelayPolicy.rememberLast(20))), "s"));
        assertEquals(FlowGraphCommands.Status.DURATION_REQUIRED, store.commands.start("a", null, 2).status);
        assertTrue(store.runs.isEmpty());
        assertTrue(store.candidates.containsKey("a"));
        assertEquals(FlowGraphCommands.Status.CHANGED, store.commands.start("a", 20L, 2).status);
        assertFalse(store.candidates.containsKey("a"));
    }

    @Test public void zeroRewardCompletesWithoutAnInvalidZeroValueLedgerBooking() {
        Memory store = new Memory(Collections.emptyMap());
        FlowGraphRun run = store.start("s", definition("s", "", Collections.emptyMap()), "s", null, 0).run;
        assertTrue(run.collected);
        assertTrue(store.credits.isEmpty());
        assertEquals(FlowGraphCommands.Status.UNCHANGED, store.commands.collect(run.id).status);
    }

    @Test public void failedSingleStepPaymentRestoresItsCandidateAndRemovesTheUncommittedRun() {
        Memory store = new Memory(Collections.emptyMap());
        store.failCredit = true;
        assertThrows(IllegalStateException.class, () -> store.start("s",
                definition("s", "", Collections.emptyMap()), "s", null, 2));
        assertTrue(store.runs.isEmpty());
        assertTrue(store.sources.isEmpty());
        assertTrue(store.candidates.containsKey("s"));
        store.failCredit = false;
        assertEquals(FlowGraphCommands.Status.CHANGED, store.commands.start("s", null, 2).status);
        assertFalse(store.candidates.containsKey("s"));
        assertEquals(1, store.credits.size());
    }

    private static FlowGraphDefinition laundry(boolean wait) {
        return withLeases(definition("wash,hang,unhang,put", "wash:hang,hang:unhang,unhang:put",
                        wait ? Map.of("wash", FlowDelayPolicy.rememberLast(20)) : Collections.emptyMap()),
                new FlowGraphDefinition.Lease("m", "machine", "wash", "wash", 1, true),
                new FlowGraphDefinition.Lease("r", "rack", "wash", "unhang", 1, false));
    }

    private interface ConcurrentAction<T> { T execute(int index); }

    private static <T> List<T> concurrently(int count, ConcurrentAction<T> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> { start.await(); return action.execute(index); }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(10, TimeUnit.SECONDS));
            return results;
        } finally { pool.shutdownNow(); }
    }

    private static final class Memory implements FlowGraphCommands.Store, TransactionRunner {
        final AtomicInteger ids = new AtomicInteger();
        Map<String, FlowGraphRun> runs = new LinkedHashMap<>();
        Map<String, String> sources = new HashMap<>();
        Map<String, FlowGraphCommands.Start> candidates = new HashMap<>();
        Map<String, Long> credits = new HashMap<>();
        final Map<String, Integer> total;
        long now;
        boolean failCredit;
        boolean inside;
        final FlowGraphCommands commands;

        Memory(Map<String, Integer> total) {
            this.total = new HashMap<>(total);
            commands = new FlowGraphCommands(this, this, () -> now, () -> "id:" + ids.incrementAndGet());
        }

        FlowGraphCommands.Result start(String sourceKey, FlowGraphDefinition definition, String stepId,
                                       Long chosenDelay, long tau) {
            synchronized (this) {
                if (!sources.containsKey(sourceKey))
                    candidates.putIfAbsent(sourceKey, new FlowGraphCommands.Start(definition, stepId));
            }
            return commands.start(sourceKey, chosenDelay, tau);
        }

        @Override public synchronized <T> T inTransaction(Transaction<T> transaction) {
            if (inside) return transaction.execute();
            Map<String, FlowGraphRun> previousRuns = new LinkedHashMap<>(runs);
            Map<String, String> previousSources = new HashMap<>(sources);
            Map<String, FlowGraphCommands.Start> previousCandidates = new HashMap<>(candidates);
            Map<String, Long> previousCredits = new HashMap<>(credits);
            inside = true;
            try { return transaction.execute(); }
            catch (RuntimeException failure) {
                runs = previousRuns; sources = previousSources; credits = previousCredits;
                candidates = previousCandidates;
                throw failure;
            } finally { inside = false; }
        }

        @Override public FlowGraphRun find(String id) { return runs.get(id); }
        @Override public FlowGraphRun findBySourceKey(String key) { return runs.get(sources.get(key)); }
        @Override public FlowGraphCommands.Start currentStart(String key) { assertTrue(inside); return candidates.get(key); }
        @Override public boolean consumeStart(String key) { assertTrue(inside); return candidates.remove(key) != null; }
        @Override public void insert(String source, FlowGraphRun run) {
            assertTrue(inside);
            if (sources.containsKey(source) || runs.containsKey(run.id)) throw new IllegalStateException("duplicate");
            sources.put(source, run.id); runs.put(run.id, run);
        }
        @Override public void update(FlowGraphRun run) { assertTrue(inside); runs.put(run.id, run); }
        @Override public void credit(String key, String runId, long tau) {
            assertTrue(inside);
            if (failCredit) throw new IllegalStateException("ledger unavailable");
            Long existing = credits.putIfAbsent(key, tau);
            if (existing != null && existing != tau) throw new IllegalStateException("conflicting reward");
        }
        @Override public FlowGraphExecution.Capacity capacityExcluding(String runId) {
            Map<String, Long> used = new HashMap<>();
            for (FlowGraphRun run : runs.values()) if (!run.id.equals(runId))
                for (FlowGraphRun.Lease lease : run.leases) if (lease.state.consumesCapacity())
                    used.merge(lease.resourceId, (long) lease.units, Long::sum);
            return new FlowGraphExecution.Capacity(total, used);
        }
    }
}
