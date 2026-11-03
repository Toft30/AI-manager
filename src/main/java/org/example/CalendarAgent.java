package org.example;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * Ein under-agent som har ansvaret for å handtere kalenderhendingar.
 */
@Component
public class CalendarAgent {

    /**
     * Eit strukturert svar for verktøyet.
     */
    public static class ToolResponse {
        public String status;
        public String partialResult;

        public ToolResponse(String status, String partialResult) {
            this.status = status;
            this.partialResult = partialResult;
        }

        @Override
        public String toString() {
            return "{ \"status\": \"" + status + "\", \"partialResult\": \"" + partialResult + "\" }";
        }
    }

    /**
     * Ein verktøymetode for å planleggje eit møte.
     * Returnerer strukturert feedback.
     *
     * @param time Tidspunkt for møtet.
     * @param details Detaljar for møtet.
     * @return Ei strukturert stadfesting på at møtet er planlagt i form av ToolResponse (JSON string).
     */
    @Tool("Schedules a meeting at a specific time with the given details. Returns a structured JSON response.")
    public String scheduleMeeting(String time, String details) {
        System.out.println("CalendarAgent mottok forespørsel: Tidspunkt = " + time + ", Detaljar = " + details);

        // Simulerer ein feil viss tidspunktet manglar
        if (time == null || time.isEmpty()) {
            return new ToolResponse("feila", "Møtetidspunkt manglar. Ver venleg å gje eit gyldig tidspunkt.").toString();
        }

        // Her ville ein vanlegvis integrert med ein ekte kalender-API
        return new ToolResponse("fullført", "Møte er vellykka planlagt til " + time + " med detaljar: " + details).toString();
    }

    /**
     * Ein verktøymetode for å sjekke om brukaren er tilgjengeleg på eit gitt tidspunkt.
     * @param time Tidspunktet ein ynskjer å sjekke.
     * @return Ei stadfesting på om tidspunktet er ledig eller opptatt i form av ToolResponse.
     */
    @Tool("Checks if the user is available at a specific time or if the calendar is already booked.")
    public String checkAvailability(String time) {
        System.out.println("CalendarAgent sjekkar tilgjenge for: " + time);
        // Her ville me normalt gjort eit reelt oppslag mot ein kalender.
        // For no returnerer me berre at det er ledig.
        return new ToolResponse("fullført", "Tidspunktet " + time + " er ledig i kalenderen.").toString();
    }
}
