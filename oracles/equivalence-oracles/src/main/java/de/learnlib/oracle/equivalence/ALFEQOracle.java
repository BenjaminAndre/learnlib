package de.learnlib.oracle.equivalence;

import de.learnlib.api.exception.SULException;
import de.learnlib.api.exception.exception.SafeException;
import de.learnlib.api.exception.exception.UnsafeException;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.ca.impl.compact.CompactFIFOA;
import net.automatalib.automata.concepts.TransitionAction;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.words.Alphabet;
import net.automatalib.words.PhiChar;
import net.automatalib.words.Word;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Comparing to AL(F) with exceptions relative to safety
 * @param <S> State type of automaton
 * @param <A> A DFA automaton trying to guess AL(F)
 * @param <I> The input type
 */
public class ALFEQOracle<S, A extends CompactDFA<I>, I>
       implements EquivalenceOracle<A, I, Boolean> {

    private CompactFIFOA fifoa;
    private List<S> badStates;

    // Needs to take a safety set into account. In the first place, juste a list of unwanted states
    public ALFEQOracle(CompactFIFOA fifoa, List<S> badStates) {
        this.fifoa = fifoa;
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
    public @Nullable DefaultQuery<I, Boolean> findCounterExample(A hypothesis, Collection<? extends I> inputs){
        Word<I> counterExemple = this.fixpointCounterExemple(hypothesis);
        // First step : is L a fix point of F(A)
        if(counterExemple != null) {
            return new DefaultQuery<>(counterExemple);
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
                    return new DefaultQuery<>(counterExemple);
                }
            }
        }


    }


    /**
     * Applies F(L) and compares it to L.
     * @return
     */
    private Word<I> fixpointCounterExemple(A hypothesis){
        return null;
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


    // Returns F(A) to check if it is the same as A
    private CompactDFA applyF(A hypothesis) {
        Alphabet alphabet = hypothesis.getInputAlphabet();
        // input alphabet contains characters 'a'..'b'
        CompactDFA nDFA = new CompactDFA(alphabet);

        //Maps old states names to new ones
        Integer[] states = new Integer[hypothesis.getStates().size()];

        for(int i = 0; i < hypothesis.getStates().size(); i++) {
            states[i] = nDFA.addIntState();
        }

        nDFA.setInitialState(states[hypothesis.getInitialState()]);


        //Iterate all transitions in the L candidate automaton
        for(int thetaF : hypothesis.getTransitions()) {
            TransitionAction.Action thetaAction = fifoa.getTransitionProperty(thetaF).getAction();
            //Cases based on definition of F, and Post
            // Invalid case is subtle here : avoided because only consider correct theta
            // Case one : easy, no deriv to do
            if(thetaAction == TransitionAction.Action.PUSH || thetaAction == TransitionAction.Action.PASS) {
                // Add theta as is
                nDFA.setTransition( states[hypothesis.getTransitionOriginState(thetaF)],
                                    new PhiChar(thetaF),
                                    states[hypothesis.getTransitionTargetState(thetaF)]
                        );
            // Case two : complicated : need to deriv from fifoa
            } else {
                // Action.PULL ?

                // Need to find in fifoa all the paths CM that go to arcs that do c!m
                // Once they've been found, find all the shortest paths to this theta (getTransitionOrder)

                // Create paths leading to CM if not existing
                // Add CM but barred
                // Add pathes from CM to state before theta if not existing
                // Add a last arc from last state to theta target state
            }

        }
        return nDFA;
    }

}
