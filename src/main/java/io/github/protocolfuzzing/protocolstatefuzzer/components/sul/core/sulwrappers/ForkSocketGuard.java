package io.github.protocolfuzzing.protocolstatefuzzer.components.sul.core.sulwrappers;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Keeps process launches and socket closes apart. A child process inherits the JVM's open
 * sockets until it execs, so a port closed during a launch cannot be re-bound immediately.
 * {@link ProcessHandler} takes the fork lock; a SUL that closes and re-binds a port takes the
 * socket close lock.
 */
public final class ForkSocketGuard {

    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

    private ForkSocketGuard() {}

    /**
     * Returns the fork lock.
     *
     * @return the lock to hold across {@code ProcessBuilder.start()}
     */
    public static Lock forkLock() {
        return LOCK.readLock();
    }

    /**
     * Returns the socket close lock.
     *
     * @return the lock to hold across a socket close
     */
    public static Lock socketCloseLock() {
        return LOCK.writeLock();
    }
}
