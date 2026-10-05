package com.jazzanalyzer.parser;

/** The file is valid MusicXML but has no {@code <harmony>} chord symbols to analyse. */
public class NoChordSymbolsException extends ChartParseException {

    public NoChordSymbolsException() {
        super("The file contains no chord symbols (<harmony> elements). "
                + "Only lead sheets with chord symbols can be analysed.");
    }
}
