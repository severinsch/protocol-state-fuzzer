package io.github.protocolfuzzing.protocolstatefuzzer.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CleanupTasksTest {

    private static final int THREADS = 8;
    private static final int TASKS_PER_THREAD = 10_000;

    @Test
    public void concurrentSubmitsAreAllExecuted() throws InterruptedException {
        CleanupTasks cleanupTasks = new CleanupTasks();
        AtomicInteger executed = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < TASKS_PER_THREAD; j++) {
                        cleanupTasks.submit(executed::incrementAndGet);
                    }
                }
                catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread: threads) {
            thread.join();
        }

        assertEquals(null, failure.get());
        cleanupTasks.execute();
        assertEquals(THREADS * TASKS_PER_THREAD, executed.get());
    }
}
