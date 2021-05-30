package de.learnlib.oracle.equivalence;

import de.learnlib.api.exception.SULException;
import de.learnlib.api.exception.exception.SafeException;
import de.learnlib.api.exception.exception.UnsafeException;
import de.learnlib.api.oracle.EquivalenceOracle.FIFOAEquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.ca.impl.compact.CompactFIFOA;
import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.Automata;
import net.automatalib.util.automata.ca.FIFOAs;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.words.PhiChar;
import net.automatalib.words.Word;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Comparing to AL(F) with exceptions relative to safety
 */
public class ALFEQOracle implements FIFOAEquivalenceOracle<PhiChar> {

    private CompactFIFOA fifoa;
    private MembershipOracle memoracle;
    private List<Integer> badStates; //State in a compact DFA are bound to be stored as Integers
    private CompactDFA emptyDFA;

    // Needs to take a safety set into account. In the first place, juste a list of unwanted states
    public ALFEQOracle(CompactFIFOA<Character, Character> fifoa, MembershipOracle memoracle, List badStates) {
        this.fifoa = fifoa;
        this.memoracle = memoracle;
        this.badStates = badStates;
        this.emptyDFA = new CompactDFA(fifoa.getAnnotationAlphabet());
        this.emptyDFA.setInitialState(0);
        this.emptyDFA.addState(true);
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
    public @Nullable DefaultQuery<PhiChar, Boolean> findCounterExample(DFA<?, PhiChar> hypothesis, Collection<? extends PhiChar> inputs){

        DefaultQuery<PhiChar, Boolean> counterExemple = this.fixpointCounterExemple(hypothesis);
        // First step : is L a fix point of F(A)
        if(counterExemple != null) {
            return counterExemple;
        } else {
            // Second step : does it intersept an unsafe region
            Word<PhiChar> unsafeExemple = getUnsafePath(hypothesis);
            if(unsafeExemple == null) {
                throw new SULException(new SafeException());
            } else {
                // Third step : is path valid ?
                if(isPathValid(unsafeExemple)) {
                    throw new SULException(new UnsafeException(unsafeExemple));
                } else {
                    return new DefaultQuery(unsafeExemple);
                }
            }
        }
    }


    /**
     * Applies F(L) and compares it to L.
     * Question is about comparing L and AL(F)
     * @return A counter exemple or null if none can be found
     */
    private DefaultQuery<PhiChar, Boolean> fixpointCounterExemple(DFA<?, PhiChar> hyp){
        // We have A, we need it to be transformed to F(A) with our algorithm
        CompactDFA<PhiChar> hypothesis = DFAs.minimize((CompactDFA < PhiChar >) hyp);
        CompactDFA<PhiChar> hypPrime = (CompactDFA) FIFOAs.applyFL(this.fifoa, hypothesis);
        hypPrime = DFAs.minimize(hypPrime);

        Word<PhiChar> ce = Automata.findSeparatingWord(hypPrime, hypothesis, hypothesis.getInputAlphabet());
        if(ce == null){ //no CounterExemple, L = F(L)
            return null;
        } else {
            //ce is a counterexemple of L=F(L). What's needed is a conterexemple of L=AL(F).
            if(hypothesis.accepts(ce)){//means ce in L
                return new DefaultQuery<>(ce);
            } else {
                if(this.fifoa.isCorrectAnnotatedTrace(ce)) {
                    return new DefaultQuery<>(ce);
                } else { //Most difficult : an invalid word in F(L) which means it is invalid in L too and that it cannot be in AL(F)
                    Word<PhiChar> cebeforefl = FIFOAs.reverseFL(this.fifoa, ce);
                    return new DefaultQuery<>(cebeforefl);
                }
            }
        }
    }

    /**
     * Finds one counter-exemple of a path that leads to an unsafe state
     * @return
     */
    private Word<PhiChar> getUnsafePath(DFA<?, PhiChar> hyp) {
        CompactDFA<PhiChar> hypothesis = (CompactDFA<PhiChar>) hyp;

        // Returns an unsafe path, being a path leading to a state not accepted
        for(Integer q : this.badStates) {//Iter on q in Q
            //Here, should iter on the list of regular languages
            CompactDFA<PhiChar> hcjr = FIFOAs.reversehcj(this.fifoa, hypothesis, q);
            List<Word<PhiChar>> ces = Automata.structuralCover(hcjr, hcjr.getInputAlphabet());
            for(Word<PhiChar> ce : ces) {
                if(!ce.isEmpty() && hcjr.accepts(ce)) { //Simplest found way to get a word from the automaton
                    return ce;
                }
            }
        }
        return null; //No counter example found
    }

    /**
     * Found a word in L that's unsafe. If it's not in AL(F), L must be refined.
     * @param counterExemple
     * @return
     */
    private boolean isPathValid(Word<PhiChar> counterExemple) {
        return (boolean) memoracle.answerQuery(counterExemple);
        //return fifoa.isValidAnnotedTrace(counterExemple);
    }



}
