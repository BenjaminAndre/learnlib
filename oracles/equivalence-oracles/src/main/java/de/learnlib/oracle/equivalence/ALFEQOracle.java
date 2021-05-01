package de.learnlib.oracle.equivalence;

import de.learnlib.api.exception.SULException;
import de.learnlib.api.exception.exception.SafeException;
import de.learnlib.api.exception.exception.UnsafeException;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.ca.impl.compact.CompactFIFOA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.words.PhiChar;
import net.automatalib.words.Word;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Comparing to AL(F) with exceptions relative to safety
 * @param <A> A DFA automaton trying to guess AL(F)
 * @param <I> The input type
 */
public class ALFEQOracle<A extends CompactDFA<I>, I> implements EquivalenceOracle<A, PhiChar, Boolean> {

    private CompactFIFOA fifoa;
    private MembershipOracle memoracle;
    private List<Integer> badStates; //State in a compact DFA are bound to be stored as Integers

    // Needs to take a safety set into account. In the first place, juste a list of unwanted states
    public ALFEQOracle(CompactFIFOA<Character, Character> fifoa, MembershipOracle memoracle, List badStates) {
        this.fifoa = fifoa;
        this.memoracle = memoracle;
        this.badStates = badStates;
    }

    /**
     * Entry to the thread.
     * @param hypothesis
     *         the conjecture
     * @param inputs
     *         the set of inputs to consider, this should be a subset of the input alphabet of the provided hypothesis
     *         for our pupose, the entire alphabet
     * @return
     */
    @Override
    public @Nullable DefaultQuery<PhiChar, Boolean> findCounterExample(A hypothesis, Collection<? extends PhiChar> inputs){
        Word<I> counterExemple = this.fixpointCounterExemple(hypothesis);
        // First step : is L a fix point of F(A)
        if(counterExemple != null) {
            return new DefaultQuery(counterExemple);
        } else {
            // Second step : does it intersept an unsafe region
            counterExemple = getUnsafePath();
            if(counterExemple == null) {
                throw new SULException(new SafeException());
            } else {
                // Third step : is path valid ?
                if(isPathValid(counterExemple)) {
                    throw new SULException(new UnsafeException(counterExemple));
                } else {
                    return new DefaultQuery(counterExemple);
                }
            }
        }
    }



    /**
     * Applies F(L) and compares it to L.
     * @return
     */
    private Word<I> fixpointCounterExemple(A hypothesis){
        // We have A, we need it to be transformed to F(A) with our algorithm
        CompactDFA hypPrime = (CompactDFA) fifoa.applyFL(hypothesis);
        CompactDFA xored = DFAs.xor(hypPrime, hypothesis, hypothesis.getInputAlphabet());
        if(DFAs.acceptsEmptyLanguage(xored)){
            return null;
        } else {
            //todo return a word within xored
            return null;
        }
    }

    /**
     * Finds one counter-exemple of a path that leads to an unsafe state
     * @return
     */
    private Word<I> getUnsafePath() {
        return null;
    }

    /**
     * Found a word in L that's unsafe. If it's not in AL(F), L must be refined.
     * @param counterExemple
     * @return
     */
    private boolean isPathValid(Word<I> counterExemple) {
        return fifoa.isValidAnnotedTrace(counterExemple);
    }


}
