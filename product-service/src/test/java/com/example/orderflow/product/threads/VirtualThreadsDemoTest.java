package com.example.orderflow.product.threads;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Virtual Threads (Java 21+) - demo: logi, czasy, porownanie")
class VirtualThreadsDemoTest {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadsDemoTest.class);

    private static final int TASKS = 10_000;
    private static final int IO_MILLIS = 20;
    private static final int PLATFORM_POOL = 200;

    @Test
    @DisplayName("1. Zadanie na virtual thread - isVirtual()=true (kontrast z platform thread)")
    void shouldRunTaskOnVirtualThread() throws Exception {
        var virtualThread = new AtomicReference<Thread>();
        var platformThread = new AtomicReference<Thread>();

        try (ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor()) {
            virtual.submit(() -> virtualThread.set(Thread.currentThread())).get();
        }

        try (ExecutorService platform = Executors.newFixedThreadPool(1)) {
            platform.submit(() -> platformThread.set(Thread.currentThread())).get();
        }

        Thread vt = virtualThread.get();
        Thread pt = platformThread.get();

        log.info("VIRTUAL  thread -> name='{}', isVirtual={}", vt.getName(), vt.isVirtual());
        log.info("PLATFORM thread -> name='{}', isVirtual={}", pt.getName(), pt.isVirtual());

        assertThat(vt.isVirtual()).as("watek z virtual executora").isTrue();
        assertThat(pt.isVirtual()).as("watek z klasycznej puli").isFalse();
    }

    @Test
    @DisplayName("2. Porownanie czasow: " + TASKS + " zadan I/O - virtual vs pula " + PLATFORM_POOL)
    void shouldBeFasterForBlockingIo() throws Exception {
        log.info("=== START: {} zadan, kazde blokuje {}ms (symulacja I/O: DB/siec) ===", TASKS, IO_MILLIS);
        log.info("Rdzenie CPU (rozmiar puli carrier threads dla virtual): {}",
                Runtime.getRuntime().availableProcessors());

        // --- Wariant A: klasyczna pula platform threads (jak Tomcat bez virtual threads) ---
        long platformMs;
        try (ExecutorService platform = Executors.newFixedThreadPool(PLATFORM_POOL)) {
            platformMs = runAndMeasure(platform, null);
        }
        log.info("PLATFORM (pula {}): {} ms", PLATFORM_POOL, platformMs);

        Set<Long> distinctVirtualIds = ConcurrentHashMap.newKeySet();
        long virtualMs;
        try (ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor()) {
            virtualMs = runAndMeasure(virtual, distinctVirtualIds);
        }
        log.info("VIRTUAL  (1 watek/zadanie): {} ms, distinct virtual threads = {}",
                virtualMs, distinctVirtualIds.size());

        double speedup = (double) platformMs / Math.max(1, virtualMs);
        log.info("=== WYNIK: virtual jest ~{}x szybszy dla tego I/O-bound obciazenia ===",
                String.format("%.1f", speedup));

        assertThat(distinctVirtualIds).hasSize(TASKS);
        assertThat(virtualMs).as("virtual szybszy niz ograniczona pula").isLessThan(platformMs);
    }

    private long runAndMeasure(ExecutorService executor, Set<Long> idCollector) throws InterruptedException {
        var latch = new CountDownLatch(TASKS);
        long start = System.nanoTime();
        for (int i = 0; i < TASKS; i++) {
            executor.submit(() -> {
                try {
                    if (idCollector != null) {
                        idCollector.add(Thread.currentThread().threadId());
                    }
                    // Symulacja blokujacego I/O (czekanie na MongoDB/Redis/HTTP).
                    Thread.sleep(IO_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        return (System.nanoTime() - start) / 1_000_000;
    }
}
