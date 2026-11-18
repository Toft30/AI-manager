package org.example;

/**
 * Ein feil frå modellkjeda med ei kort melding som kan lesast høgt for brukaren.
 * Den eigentlege feilen frå Gemini ligg i {@link #getCause()} og blir logga, ikkje lesen opp.
 */
public class AgentException extends RuntimeException {

    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
