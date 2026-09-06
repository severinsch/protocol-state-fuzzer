package io.github.protocolfuzzing.protocolstatefuzzer.components.sul.core.sulwrappers;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Keeps {@link ProcessBuilder#start()} from overlapping a SUL's socket close, in a fuzzer whose
 * SULs run in parallel.
 * <h2>The race this exists for</h2>
 * <p>
 * Spawning a process copies the JVM's <em>entire file-descriptor table</em> into the child.
 * The child closes the inherited descriptors before it execs the target binary — on Linux the
 * JDK's helper does that by walking {@code /proc/self/fd} — but until it has, it holds a
 * duplicate of every socket the JVM had open at the instant of the fork, including sockets
 * belonging to other threads' SULs.
 * <p>
 * A UDP (or TCP) port stays bound in the kernel for as long as <em>any</em> descriptor
 * referring to that socket is open. So if one thread closes its server socket while another
 * thread is inside {@code ProcessBuilder.start()}, the port is not released when
 * {@code close()} returns: it is released when the forked child gets around to closing its
 * inherited copy. A SUL that closes and immediately re-binds the same port — the normal
 * shape of a per-query SUL — then loses the race and sees {@code BindException: Address
 * already in use} on a port nothing owns a few hundred microseconds later.
 * <p>
 * Measured on a 12-SUL fuzzer at ~0.9% of queries with a child that does nothing but
 * {@code exec /bin/true}, 0% with no child process at all, and ~14% with 4000 extra
 * descriptors open in the JVM — the window scales with the size of the descriptor table,
 * which is the signature of the copy-then-close mechanism rather than of anything the child
 * does.
 * <h2>The contract</h2>
 * <p>
 * Forks are concurrent with each other and closes are concurrent with each other; only the
 * two together are unsafe. That is a readers/writers problem, so forks take the shared lock
 * and closes take the exclusive one. Both sections are microseconds long, and a fork already
 * serialises inside the JVM's spawn path, so the added contention is not measurable against a
 * query.
 * <p>
 * <b>Hold each lock across the racing call and nothing else.</b> In particular a launch's
 * post-spawn {@code startWait} sleep MUST be outside the fork lock: holding it there would
 * block every SUL's teardown for the length of that sleep.
 * <p>
 * This is a process-wide guard because the descriptor table it protects is process-wide:
 * the threads that race have no reference to each other, and the child inherits from all of
 * them at once.
 */
public final class ForkSocketGuard {

    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

    private ForkSocketGuard() {}

    /**
     * The lock to hold across a {@code ProcessBuilder.start()} — shared, so concurrent
     * launches do not serialise against one another.
     *
     * @return the shared side of the guard
     */
    public static Lock forkLock() {
        return LOCK.readLock();
    }

    /**
     * The lock to hold across a socket {@code close()} whose port is going to be re-bound —
     * exclusive against every in-flight fork.
     *
     * @return the exclusive side of the guard
     */
    public static Lock socketCloseLock() {
        return LOCK.writeLock();
    }
}
