package io.github.protocolfuzzing.protocolstatefuzzer.components.learner.oracles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import de.learnlib.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.query.DefaultQuery;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.automaton.transducer.impl.CompactMealy;
import net.automatalib.word.Word;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RandomWpMethodEQOracleTest {

    private static final Alphabet<String> ALPHABET = Alphabets.fromList(List.of("a"));

    /** One state, one self-loop, so the WP generator always has something to sample. */
    private static CompactMealy<String, String> hypothesis() {
        CompactMealy<String, String> mealy = new CompactMealy<>(ALPHABET);
        int s0 = mealy.addInitialState();
        mealy.addTransition(s0, "a", s0, "out");
        return mealy;
    }

    /** An oracle that answers exactly as the hypothesis does, so no counterexample exists. */
    private static MealyMembershipOracle<String, String> agreeing(AtomicInteger queryCount) {
        return queries -> queries.forEach(query -> {
            queryCount.incrementAndGet();
            Word<String> out = Word.epsilon();
            for (int i = 0; i < query.getInput().length(); i++) {
                out = out.append("out");
            }
            query.answer(out.suffix(query.getSuffix().length()));
        });
    }

    @Test
    public void findsNoCounterExampleWhenTheOracleAgrees() {
        AtomicInteger queryCount = new AtomicInteger();
        RandomWpMethodEQOracle<String, String> oracle = new RandomWpMethodEQOracle<>(List.of(agreeing(queryCount)), 1,
            2, 20, 42L);

        assertNull(oracle.findCounterExample(hypothesis(), ALPHABET));
        assertEquals(20, queryCount.get());
    }

    @Test
    public void findsACounterExampleWhenTheOracleDisagrees() {
        MealyMembershipOracle<String, String> disagreeing = queries -> queries.forEach(query -> {
            Word<String> out = Word.epsilon();
            for (int i = 0; i < query.getSuffix().length(); i++) {
                out = out.append("other");
            }
            query.answer(out);
        });

        RandomWpMethodEQOracle<String, String> oracle = new RandomWpMethodEQOracle<>(List.of(disagreeing), 1, 2, 20,
            42L);

        DefaultQuery<String, Word<String>> ce = oracle.findCounterExample(hypothesis(), ALPHABET);
        assertNotNull(ce);
    }

    /**
     * A worker that fails must not be reported as a completed search that found no
     * counterexample: the learner reads null as "the hypothesis is equivalent" and would
     * silently finish learning with an unverified hypothesis.
     */
    @Test
    public void aFailingWorkerAbortsTheSearchInsteadOfReportingEquivalence() {
        RuntimeException failure = new IllegalStateException("non-deterministic query");
        MealyMembershipOracle<String, String> failing = queries -> {
            throw failure;
        };

        RandomWpMethodEQOracle<String, String> oracle = new RandomWpMethodEQOracle<>(List.of(failing), 1, 2, 20, 42L);

        try {
            oracle.findCounterExample(hypothesis(), ALPHABET);
            fail("expected the failed worker to abort the search");
        }
        catch (RandomWpMethodEQOracle.EquivalenceOracleFailedException e) {
            assertSame(failure, e.getCause());
            assertTrue(e.getMessage().contains("non-deterministic query"));
        }
    }

    /** The same holds when only one of several workers fails. */
    @Test
    public void aSingleFailingWorkerAmongSeveralAbortsTheSearch() {
        AtomicInteger queryCount = new AtomicInteger();
        MealyMembershipOracle<String, String> failing = queries -> {
            throw new IllegalStateException("non-deterministic query");
        };

        RandomWpMethodEQOracle<String, String> oracle = new RandomWpMethodEQOracle<>(
            List.of(agreeing(queryCount), failing, agreeing(queryCount)), 1, 2, 200, 42L);

        try {
            oracle.findCounterExample(hypothesis(), ALPHABET);
            fail("expected the failed worker to abort the search");
        }
        catch (RandomWpMethodEQOracle.EquivalenceOracleFailedException e) {
            assertNotNull(e.getCause());
        }
    }
}
