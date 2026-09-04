package io.github.protocolfuzzing.protocolstatefuzzer.components.learner.oracles;

import de.learnlib.oracle.EquivalenceOracle;
import de.learnlib.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.query.DefaultQuery;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implements an equivalence test by applying the WP-method test on the given
 * hypothesis automaton as described in "Test Selection Based on Finite State Models" by
 * {@literal S. Fujiwara et al}.
 * <p>
 * Adapted from an EQ oracle implementation in LearnLib's development branch not
 * available in the version we use, see
 * <a href=
 * "https://github.com/mtf90/learnlib/blob/develop/oracles/equivalence-oracles/src/main/java/de/learnlib/oracle/equivalence/RandomWpMethodEQOracle.java">RandomWpMethodEQOracle</a>.
 * Our adaptation is randomizing access sequence generation.
 * <p>
 * Instead of enumerating the test suite in order, this is a sampling implementation:
 * <ol>
 * <li>Sample uniformly from the states for a prefix
 * <li>Sample geometrically a random word
 * <li>Sample a word from the set of suffixes / state identifiers (either local or global).
 * </ol>
 * <p>
 * There are two parameters:
 * <ul>
 * <li>minimalSize determines the minimal size of the random word. This is useful when one first
 * performs a
 * W(p)-method with some depth and continues with this randomized tester from that depth onward
 * <li>rndLength determines the expected length of the random word. The expected length in effect is
 * minimalSize + rndLength.
 * In the unbounded case it will not terminate for a correct hypothesis.
 * </ul>
 *
 * @param <I> input symbol type
 * @param <O> output symbol type
 */
public class RandomWpMethodEQOracle<I, O> implements EquivalenceOracle.MealyEquivalenceOracle<I, O> {

    /** Stores the constructor parameter. */
    protected List<MealyMembershipOracle<I, O>> sulOracles;

    /** Stores the constructor parameter. */
    protected int minimalSize;

    /** Stores the constructor parameter. */
    protected int rndLength;

    /** Stores the constructor parameter. */
    protected int bound;

    /** Stores the constructor parameter. */
    protected long seed;

    /**
     * Constructs a new instance from the given parameters, which represents an unbounded testing
     * oracle.
     *
     * @param sulOracles  the oracles which answer tests
     * @param minimalSize the minimal size of the random word
     * @param rndLength   the expected length (in addition to minimalSize) of random word
     * @param seed        the seed to be used for randomness
     */
    public RandomWpMethodEQOracle(List<MealyMembershipOracle<I, O>> sulOracles,
        int minimalSize, int rndLength, long seed) {

        this.sulOracles = sulOracles;
        this.minimalSize = minimalSize;
        this.rndLength = rndLength;
        this.seed = seed;
        this.bound = 0;
    }

    /**
     * Constructs a new instance from the given parameters, which represents a bounded testing oracle.
     *
     * @param sulOracles  the oracles which answer tests
     * @param minimalSize the minimal size of the random word
     * @param rndLength   the expected length (in addition to minimalSize) of random word
     * @param bound       the bound (set to 0 for unbounded).
     * @param seed        the seed to be used for randomness
     */
    public RandomWpMethodEQOracle(List<MealyMembershipOracle<I, O>> sulOracles,
        int minimalSize, int rndLength, int bound, long seed) {

        this.sulOracles = sulOracles;
        this.minimalSize = minimalSize;
        this.rndLength = rndLength;
        this.bound = bound;
        this.seed = seed;
    }

    /**
     * Tries to find a counterexample using {@link #doFindCounterExample(MealyMachine, Collection)}.
     *
     * @param  hypothesis the hypothesis to be searched
     * @param  inputs     the inputs to be used
     *
     * @return            the counterexample or null
     */
    @Override
    public @Nullable DefaultQuery<I, Word<O>> findCounterExample(
        MealyMachine<?, I, ?, O> hypothesis, Collection<? extends I> inputs) {

        return doFindCounterExample(hypothesis, inputs);
    }

    /**
     * Implements the search technique.
     *
     * @param  <S>        the type of states
     * @param  hypothesis the hypothesis to be searched
     * @param  inputs     the inputs to be used
     *
     * @return            the counterexample or null
     */
    public <S> @Nullable DefaultQuery<I, Word<O>> doFindCounterExample(MealyMachine<S, I, ?, O> hypothesis,
        Collection<? extends I> inputs) {
        WpEQSequenceGenerator<I, Word<O>, S> generator = new WpEQSequenceGenerator<>(hypothesis, inputs);

        Random rand = new Random(seed);
        List<S> states = new ArrayList<>(hypothesis.getStates());

        AtomicInteger globalCounter = new AtomicInteger(0);
        Object lockObject = new Object();
        ConcurrentMap<Integer, DefaultQuery<I, Word<O>>> counterExamples = new ConcurrentHashMap<>();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();

        List<Thread> threads = new ArrayList<>();

        for (MealyMembershipOracle<I, O> oracle: sulOracles) {
            Thread thread = new Thread(() -> {
                try {
                    while (globalCounter.get() < bound) {
                        DefaultQuery<I, Word<O>> query;
                        int ticket;
                        // The whole word is built under the lock so that the ticket order
                        // and the draws taken from the single seeded rand stay in lockstep:
                        // that is what makes a run with a given seed reproducible whatever
                        // the number of workers. The price is that a slow generation parks
                        // every worker, so the generator must stay cheap — see the note on
                        // WpEQSequenceGenerator#getRandomAccessSequence about the search
                        // that used to be exponential and stalled entire rounds here.
                        synchronized (lockObject) {
                            ticket = globalCounter.getAndIncrement();
                            if (ticket >= bound) {
                                break;
                            }
                            WordBuilder<I> wb = new WordBuilder<>(minimalSize + rndLength + 1);
                            wb.append(generator.getRandomAccessSequence(
                                states.get(rand.nextInt(states.size())), rand));
                            wb.append(generator.getRandomMiddleSequence(minimalSize, rndLength, rand));
                            wb.append(generator.getRandomCharacterizingSequence(wb, rand));

                            query = new DefaultQuery<>(wb.toWord());
                        }
                        oracle.processQueries(Collections.singleton(query));
                        Word<O> hypOutput = hypothesis.computeOutput(query.getInput());
                        if (!Objects.equals(hypOutput, query.getOutput())) {
                            counterExamples.put(ticket, query);
                            globalCounter.set(bound);
                        }
                    }
                }
                catch (RuntimeException | Error e) {
                    // Record the failure so the joining thread can rethrow it, and stop
                    // the other workers: the search has not completed, so its result may
                    // no longer be trusted. Rethrown so the JVM still reports the dying
                    // thread the way it always has.
                    workerFailure.compareAndSet(null, e);
                    globalCounter.set(bound);
                    throw e;
                }
            });
            threads.add(thread);
            thread.start();
        }

        for (Thread thread: threads) {
            try {
                thread.join();
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // A worker that throws dies, join() returns normally, and counterExamples is
        // empty — so without this the method would return null, which the learner
        // reads as "the hypothesis is equivalent". An aborted search would then be
        // indistinguishable from a completed one, silently ending learning with an
        // unverified hypothesis. This matters most for the exception the oracle stack
        // raises on purpose: CESanitizingSULOracle rethrows a non-deterministic query
        // when skipNonDetTests is false, which is a deliberate hard stop and is one
        // when the search runs on the calling thread.
        Throwable failure = workerFailure.get();
        if (failure != null) {
            throw new EquivalenceOracleFailedException(
                "Equivalence search aborted: a worker thread failed with "
                    + failure.getClass().getName() + ": " + failure.getMessage(),
                failure);
        }

        return counterExamples.isEmpty() ? null : counterExamples.get(Collections.min(counterExamples.keySet()));
    }

    /**
     * Thrown when a worker thread of the parallel equivalence search fails, so that an
     * aborted search is never reported as a completed one that found no counterexample.
     */
    public static class EquivalenceOracleFailedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new instance from the given parameters.
         *
         * @param message the detail message
         * @param cause   the failure of the worker thread
         */
        public EquivalenceOracleFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
