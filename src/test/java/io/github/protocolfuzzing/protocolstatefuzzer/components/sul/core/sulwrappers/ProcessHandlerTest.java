package io.github.protocolfuzzing.protocolstatefuzzer.components.sul.core.sulwrappers;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ProcessHandlerTest {

    /** Reports its process alive forever, as after a termination that missed its budget. */
    private static final class NeverDyingProcessHandler extends ProcessHandler {

        NeverDyingProcessHandler(Process process) {
            super("true");
            this.currentProcess = process;
        }

        @Override
        public boolean isAlive() {
            return true;
        }
    }

    @Test
    public void failedTerminationReleasesTheProcessHandle() throws Exception {
        Process process = new ProcessBuilder("sleep", "30").start();
        try {
            NeverDyingProcessHandler handler = new NeverDyingProcessHandler(process);
            assertThrows(RuntimeException.class, handler::terminateProcess);
            assertNull(handler.currentProcess);
        }
        finally {
            process.destroyForcibly();
            process.waitFor();
        }
    }
}
