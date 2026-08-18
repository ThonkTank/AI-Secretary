package de.thonktank.autosecretary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Coalesces geometry builds and publishes immutable render data from worker threads. */
final class WoodGrainRenderPipeline {
    static final int MAXIMUM_CACHE_BYTES = 4 * 1024 * 1024;
    private static final WoodGrainRenderCache CACHE =
            new WoodGrainRenderCache(MAXIMUM_CACHE_BYTES);
    private static final WoodGrainRenderBuilder BUILDER = new WoodGrainRenderBuilder();
    private static final Map<String, CompletableFuture<WoodGrainRenderData>> IN_FLIGHT =
            new ConcurrentHashMap<>();
    private static final AtomicInteger BUILD_COUNT = new AtomicInteger();
    private static final AtomicReference<String> LAST_BUILD_THREAD = new AtomicReference<>();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "wood-grain-geometry");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private WoodGrainRenderPipeline() { }

    static CompletableFuture<WoodGrainRenderData> request(WoodGrainRenderRequest request) {
        WoodGrainRenderData cached = CACHE.get(request.key);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        CompletableFuture<WoodGrainRenderData> future = IN_FLIGHT.computeIfAbsent(
            request.key, ignored -> CompletableFuture.supplyAsync(() -> {
                BUILD_COUNT.incrementAndGet();
                LAST_BUILD_THREAD.set(Thread.currentThread().getName());
                WoodGrainRenderData data = BUILDER.build(request);
                CACHE.put(request.key, data);
                return data;
            }, EXECUTOR));
        future.whenComplete((data, failure) -> IN_FLIGHT.remove(request.key, future));
        return future;
    }

    static void awaitIdleForTest() {
        while (true) {
            List<CompletableFuture<WoodGrainRenderData>> futures =
                    new ArrayList<>(IN_FLIGHT.values());
            if (futures.isEmpty()) return;
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    static void clearForTest() {
        awaitIdleForTest();
        CACHE.clear();
        BUILD_COUNT.set(0);
        LAST_BUILD_THREAD.set(null);
    }

    static int cacheBytesForTest() { return CACHE.bytes(); }
    static int cacheEntriesForTest() { return CACHE.size(); }
    static int buildCountForTest() { return BUILD_COUNT.get(); }
    static String lastBuildThreadForTest() { return LAST_BUILD_THREAD.get(); }
}
