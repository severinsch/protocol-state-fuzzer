package io.github.protocolfuzzing.protocolstatefuzzer.components.sul.core.sulwrappers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * Pins the lock relation rather than provoking the race, which is a sub-millisecond window in
 * the JDK's spawn path.
 */
public class ForkSocketGuardTest {

    private static Thread lockInNewThread(Lock lock, CountDownLatch acquired) {
        Thread thread = new Thread(() -> {
            lock.lock();
            try {
                acquired.countDown();
            }
            finally {
                lock.unlock();
            }
        });
        thread.start();
        return thread;
    }

    @Test
    public void aSocketCloseExcludesAFork() throws InterruptedException {
        Lock close = ForkSocketGuard.socketCloseLock();
        CountDownLatch acquired = new CountDownLatch(1);
        Thread forker;
        close.lock();
        try {
            forker = lockInNewThread(ForkSocketGuard.forkLock(), acquired);
            assertFalse(acquired.await(200, TimeUnit.MILLISECONDS));
        }
        finally {
            close.unlock();
        }
        assertTrue(acquired.await(5, TimeUnit.SECONDS));
        forker.join();
    }

    @Test
    public void forksDoNotExcludeEachOther() throws InterruptedException {
        Lock fork = ForkSocketGuard.forkLock();
        CountDownLatch acquired = new CountDownLatch(1);
        fork.lock();
        try {
            Thread other = lockInNewThread(ForkSocketGuard.forkLock(), acquired);
            assertTrue(acquired.await(5, TimeUnit.SECONDS));
            other.join();
        }
        finally {
            fork.unlock();
        }
    }
}
