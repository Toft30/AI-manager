package org.example;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * Ein under-agent som har ansvaret for å handtere kalenderhendingar.
 */
@Component
public class CalendarAgent {

    /**
     * Ein verktøymetode for å planleggje eit møte.
     * @param time Tidspunkt for møtet.
     * @param details Detaljar for møtet.
     * @return Ei stadfesting på at møtet er planlagt.
     */
    @Tool("Schedules a meeting at a specific time with the given details.")
    public String scheduleMeeting(String time, String details) {
        System.out.println("CalendarAgent mottok forespørsel: Tidsunkt = " + time + ", Detaljar = " + details);
        // Her ville ein vanlegvis integrert med ein ekte kalender-API
        return "Møte er vellykka planlagt til " + time + " med detaljar: " + details;
    }
}
