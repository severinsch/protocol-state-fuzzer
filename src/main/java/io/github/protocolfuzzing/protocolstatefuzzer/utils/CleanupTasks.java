package io.github.protocolfuzzing.protocolstatefuzzer.utils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Collection of tasks which are executed on termination, an alternative to shutdown hook.
 * <p>
 * The task list is thread-safe. {@link #submit(Runnable)} is reached from SUL
 * {@code pre()}/{@code post()} implementations, and those run concurrently whenever the
 * learner is configured with parallel membership or equivalence oracles — LearnLib's
 * {@code StaticParallelOracle} gives each chain its own SUL but they share one
 * {@code CleanupTasks}. A plain {@link java.util.ArrayList} here corrupts under concurrent
 * {@code add}: it throws {@code ArrayIndexOutOfBoundsException} out of {@code submit} and,
 * more quietly, drops entries — a cleanup task that silently never runs. Observed against
 * this class as {@code Index 110 out of bounds for length 109} with twelve concurrent SULs.
 * <p>
 * {@link CopyOnWriteArrayList} rather than a synchronized list because submission is
 * rare relative to nothing else touching the list, and because it lets {@link #execute()}
 * iterate safely even if a late task is submitted during shutdown.
 */
public class CleanupTasks {

    /** List of cleanup tasks to be run. */
    protected List<Runnable> tasks;

    /**
     * Constructs a new instance with an empty {@link #tasks} list.
     */
    public CleanupTasks() {
        tasks = new CopyOnWriteArrayList<>();
    }

    /**
     * Adds a new runnable task.
     *
     * @param runnable task to be run during {@link #execute()}
     */
    public void submit(Runnable runnable) {
        tasks.add(runnable);
    }

    /**
     * Executes the stored {@link #tasks} consecutively.
     */
    public void execute() {
        for (Runnable task: tasks) {
            task.run();
        }
    }
}
