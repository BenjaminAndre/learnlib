package de.learnlib.oracle.membership;

import de.learnlib.api.oracle.SingleQueryOracle;
import net.automatalib.automata.ca.FIFOA;
import net.automatalib.words.PhiChar;
import net.automatalib.words.Word;

public class FIFOTraceSimulatorOracle implements SingleQueryOracle<PhiChar, Boolean>, SingleQueryOracle.SingleQueryOracleDFA<PhiChar> {

    private FIFOA fifoa;

    public FIFOTraceSimulatorOracle(FIFOA fifoa) {
        this.fifoa = fifoa;
    }

    /**
     * Answers to Membership query about the automaton of the annoted trace of the FIFO automaton
     * @param input a gamma word in Phi, representing an annoted trace
     * @return if it corresponds to a valid execution in the automaton
     */
    @Override
    public Boolean answerQuery(Word<PhiChar> input) {
        // Can't give the work to the automaton as it wouldn't make sense.
        boolean answer = fifoa.validateTrace(input);
        return answer;
    }

    // TODO explain what is prefix and suffix
    @Override
    public Boolean answerQuery(Word<PhiChar> prefix, Word<PhiChar> suffix) {
        Word<PhiChar> input = prefix.concat(suffix);
        return answerQuery(input);
    }
}
