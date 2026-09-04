package io.github.protocolfuzzing.protocolstatefuzzer.components.learner.oracles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.transducer.impl.CompactMealy;
import net.automatalib.word.Word;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pins the randomized access sequence search of {@link WpEQSequenceGenerator}: it must
 * return a real access sequence for every reachable state, and it must do so in time
 * that grows with the size of the automaton rather than with the number of simple paths
 * through it.
 */
public class WpEQSequenceGeneratorTest {

    /**
     * The topology that used to defeat the search. The initial state has exactly one
     * edge leaving it, into {@code b1}; {@code b1..bk} form a complete digraph, and
     * every one of them reaches the target state.
     * <p>
     * The search walks backwards from the target into the component. It only succeeds by
     * stepping from {@code b1} into the initial state, so as soon as it enters {@code b1}
     * and picks a different predecessor first, {@code b1} is marked and the initial state
     * is out of reach until that branch is abandoned. With a mark that is cleared on
     * backtracking, abandoning the branch means enumerating every simple path through the
     * remaining component, which is factorial in {@code k}.
     * <p>
     * States: {@code 0} initial, {@code 1..k} the component, {@code k+1} the target.
     * Inputs: {@code x1..xk} move inside the component, {@code aT} reaches the target,
     * {@code a0} leaves the initial state.
     */
    private static CompactMealy<String, String> denseComponent(int k) {
        List<String> symbols = new ArrayList<>();
        for (int j = 1; j <= k; j++) {
            symbols.add("x" + j);
        }
        symbols.add("aT");
        symbols.add("a0");
        Alphabet<String> alphabet = Alphabets.fromList(symbols);

        CompactMealy<String, String> mealy = new CompactMealy<>(alphabet);
        int initial = mealy.addInitialState();
        int[] component = new int[k + 1];
        for (int i = 1; i <= k; i++) {
            component[i] = mealy.addState();
        }
        int target = mealy.addState();

        for (String symbol: alphabet) {
            mealy.addTransition(initial, symbol, symbol.equals("a0") ? component[1] : initial, "o");
            mealy.addTransition(target, symbol, target, "o");
        }
        for (int i = 1; i <= k; i++) {
            for (String symbol: alphabet) {
                int successor;
                if (symbol.equals("aT")) {
                    successor = target;
                } else if (symbol.equals("a0")) {
                    successor = component[i];
                } else {
                    successor = component[Integer.parseInt(symbol.substring(1))];
                }
                mealy.addTransition(component[i], symbol, successor, "o");
            }
        }
        return mealy;
    }

    /** A small automaton with a cycle, a branch and a sink. */
    private static CompactMealy<String, String> assortedTopology() {
        Alphabet<String> alphabet = Alphabets.fromList(List.of("a", "b", "c"));
        CompactMealy<String, String> mealy = new CompactMealy<>(alphabet);
        int s0 = mealy.addInitialState();
        int s1 = mealy.addState();
        int s2 = mealy.addState();
        int s3 = mealy.addState();

        mealy.addTransition(s0, "a", s1, "o");
        mealy.addTransition(s0, "b", s0, "o");
        mealy.addTransition(s0, "c", s2, "o");
        mealy.addTransition(s1, "a", s2, "o");
        mealy.addTransition(s1, "b", s0, "o");
        mealy.addTransition(s1, "c", s3, "o");
        mealy.addTransition(s2, "a", s3, "o");
        mealy.addTransition(s2, "b", s1, "o");
        mealy.addTransition(s2, "c", s2, "o");
        mealy.addTransition(s3, "a", s3, "o");
        mealy.addTransition(s3, "b", s3, "o");
        mealy.addTransition(s3, "c", s0, "o");
        return mealy;
    }

    private static WpEQSequenceGenerator<String, Word<String>, Integer> generatorFor(
        CompactMealy<String, String> mealy) {
        return new WpEQSequenceGenerator<>(mealy, mealy.getInputAlphabet());
    }

    /** Asserts that the word really drives the automaton from the initial state to the state. */
    private static void assertAccessSequence(CompactMealy<String, String> mealy, Integer state,
        Word<String> word) {
        assertNotNull(word);
        assertEquals("not an access sequence for state " + state + ": " + word, state, mealy.getState(word));
    }

    @Test
    public void everyStateGetsAnAccessSequenceThatReachesIt() {
        CompactMealy<String, String> mealy = assortedTopology();
        WpEQSequenceGenerator<String, Word<String>, Integer> generator = generatorFor(mealy);

        for (int seed = 0; seed < 200; seed++) {
            Random rand = new Random(seed);
            for (Integer state: mealy.getStates()) {
                assertAccessSequence(mealy, state, generator.getRandomAccessSequence(state, rand));
            }
        }
    }

    /** The initial state is reached by the empty word, with no search at all. */
    @Test
    public void theInitialStateGetsTheEmptyAccessSequence() {
        CompactMealy<String, String> mealy = assortedTopology();
        WpEQSequenceGenerator<String, Word<String>, Integer> generator = generatorFor(mealy);

        assertEquals(Word.epsilon(), generator.getRandomAccessSequence(mealy.getInitialState(), new Random(1)));
    }

    /** The search is randomized, so it must not collapse onto a single access sequence. */
    @Test
    public void theSearchStaysRandomized() {
        CompactMealy<String, String> mealy = assortedTopology();
        WpEQSequenceGenerator<String, Word<String>, Integer> generator = generatorFor(mealy);

        Set<Word<String>> words = new HashSet<>();
        Random rand = new Random(42);
        Integer target = 3;
        for (int i = 0; i < 200; i++) {
            words.add(generator.getRandomAccessSequence(target, rand));
        }
        assertTrue("expected several distinct access sequences, got " + words, words.size() > 1);
    }

    /** A path that revisits a state would be a needlessly long test prefix. */
    @Test
    public void theAccessSequenceVisitsEachStateAtMostOnce() {
        CompactMealy<String, String> mealy = assortedTopology();
        WpEQSequenceGenerator<String, Word<String>, Integer> generator = generatorFor(mealy);

        Random rand = new Random(7);
        for (int i = 0; i < 500; i++) {
            for (Integer state: mealy.getStates()) {
                Word<String> word = generator.getRandomAccessSequence(state, rand);
                Set<Integer> seen = new HashSet<>();
                Integer current = mealy.getInitialState();
                assertTrue(seen.add(current));
                for (String symbol: word) {
                    current = mealy.getSuccessor(current, symbol);
                    assertTrue("access sequence revisits a state: " + word, seen.add(current));
                }
            }
        }
    }

    /**
     * The regression test for the equivalence round that hangs instead of aborting.
     * <p>
     * 18 states and 18 inputs, which the previous search could not finish: it enumerated
     * simple paths through the dense component, of which there are on the order of 14!.
     * The timeout is two orders of magnitude above what the search now needs, so it only
     * fires on the exponential behaviour and never on a slow machine.
     */
    @Test(timeout = 30_000L)
    public void aDenseComponentDoesNotStallTheSearch() {
        CompactMealy<String, String> mealy = denseComponent(16);
        WpEQSequenceGenerator<String, Word<String>, Integer> generator = generatorFor(mealy);
        Integer target = mealy.size() - 1;

        Random rand = new Random(42);
        for (int i = 0; i < 1000; i++) {
            assertAccessSequence(mealy, target, generator.getRandomAccessSequence(target, rand));
        }
        for (int seed = 0; seed < 100; seed++) {
            Random seededRandom = new Random(seed);
            for (Integer state: mealy.getStates()) {
                assertAccessSequence(mealy, state, generator.getRandomAccessSequence(state, seededRandom));
            }
        }
    }

    /** An unreachable state has no access sequence, and that must stay an explicit failure. */
    @Test
    public void anUnreachableStateIsReportedInsteadOfSilentlySkipped() {
        Alphabet<String> alphabet = Alphabets.fromList(List.of("a"));
        CompactMealy<String, String> mealy = new CompactMealy<>(alphabet);
        int s0 = mealy.addInitialState();
        int orphan = mealy.addState();
        mealy.addTransition(s0, "a", s0, "o");
        mealy.addTransition(orphan, "a", orphan, "o");

        WpEQSequenceGenerator<String, Word<String>, Integer> generator = generatorFor(mealy);
        try {
            generator.getRandomAccessSequence(orphan, new Random(1));
            fail("expected an unreachable state to be reported");
        }
        catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Access sequence could not be generated"));
        }
    }
}
