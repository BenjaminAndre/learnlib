package de.learnlib.api.exception.exception;

import net.automatalib.words.Word;

public class UnsafeException extends Exception {

    private Word counterExemple;

    public <I> UnsafeException(Word<I> counterExemple) {
        this.counterExemple = counterExemple;
    }

    @Override
    public String toString() {
        return "UnsafeException{" +
                "counterExemple=" + counterExemple +
                '}';
    }
}
