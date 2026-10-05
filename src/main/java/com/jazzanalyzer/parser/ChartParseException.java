package com.jazzanalyzer.parser;

/** The input could not be turned into a {@link com.jazzanalyzer.domain.Chart}. The message is safe to show to users. */
public class ChartParseException extends RuntimeException {

    public ChartParseException(String message) {
        super(message);
    }

    public ChartParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
