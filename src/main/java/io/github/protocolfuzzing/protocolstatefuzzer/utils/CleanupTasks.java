package io.github.protocolfuzzing.protocolstatefuzzer.utils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Collection of tasks which are executed on termination, an alternative to shutdown hook.
 * <p>
 * Thread-safe: SULs running under parallel oracles share one instance and may call
 * {@link #submit(Runnable)} concurrently.
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
