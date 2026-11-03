package org.example;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Agent som bryt ned ei stor oppgåve i mindre deloppgåver.
 *
 * <p>
 * Han har verken samtaleminne eller verktøy, så planlegginga ikkje fyller samtalen med ODIN
 * og berre kostar eitt kall mot Gemini.
 * </p>
 */
public interface PlannerAgent {

    /**
     * Bryt ned ein stor forespørsel til ei liste med mindre deloppgåver.
     *
     * @param taskDescription Skildring av oppgåva frå brukaren.
     * @param now Noverande dato og klokkeslett, så relative datoar blir rette.
     * @param maxSubTasks Det høgaste talet på deloppgåver.
     * @return Strukturert resultat som inneheld ei liste av deloppgåver.
     */
    @SystemMessage("Du er ein analytisk planleggjar for AI-assistenten ODIN. Bryt ned den gitte forespørselen "
            + "til ei logisk rekkjefølgje av konkrete deloppgåver som ODIN kan gjere éi etter éi med verktøya sine "
            + "(Google Kalender: sjekke ledig tid, opprette, liste og slette møte). "
            + "Bruk så få deloppgåver som mogleg, aldri fleire enn {{maxSubTasks}}. "
            + "Er forespørselen enkel, er éi deloppgåve nok. Skriv kvar deloppgåve som ein sjølvstendig instruks "
            + "med alle detaljar ho treng, som tidspunkt og namn, sidan ho blir utført åleine. "
            + "Akkurat no er det {{now}}. Skriv på same språk som brukaren.")
    TaskDecompositionResult decomposeTask(@UserMessage String taskDescription, @V("now") String now,
                                          @V("maxSubTasks") int maxSubTasks);
}
