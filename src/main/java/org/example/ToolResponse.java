package org.example;

/**
 * Eit strukturert svar frå eit verktøy, så manageren kan vurdere om det gjekk bra.
 *
 * <p>
 * Verktøya returnerer {@code {"status": "fullført"|"feila", "partialResult": "..."}} som tekst.
 * Ved «feila» prøver manageren å rette det éin gong før han gir opp.
 * </p>
 *
 * @param status «fullført» eller «feila».
 * @param partialResult Kva som skjedde, eller kva som gjekk gale.
 */
public record ToolResponse(String status, String partialResult) {

    public static ToolResponse ok(String result) {
        return new ToolResponse("fullført", result);
    }

    public static ToolResponse failed(String reason) {
        return new ToolResponse("feila", reason);
    }

    @Override
    public String toString() {
        return "{\"status\": \"" + escape(status) + "\", \"partialResult\": \"" + escape(partialResult) + "\"}";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
