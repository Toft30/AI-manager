package org.example;

import dev.langchain4j.service.SystemMessage;

/**
 * Grensesnitt for Manager Agent i Multi-Agent Systemet.
 * Denne agenten er ansvarleg for å ta imot oppgåver frå brukaren
 * og delegere dei vidare til andre spesialiserte agentar (f.eks. CalendarAgent).
 *
 * <p>
 * Andre agentar kan integrerast ved å registrere dei som verktøy (tools)
 * i AiServices-oppsettet, for eksempel ved bruk av @Tool-annotasjonen
 * på metodar i dei andre agent-klassane.
 * </p>
 */
public interface ManagerAgent {

    /**
     * Delegerer oppgåva basert på brukaren sin input.
     *
     * @param taskDescription Skildring av oppgåva frå brukaren.
     * @return AI-modellen sitt svar eller stadfesting på at oppgåva er delegert.
     */
    @SystemMessage("Du er ein Manager AI-agent. Di oppgåve er å delegere oppgåver til andre spesialiserte agentar. Viss du har verktøy tilgjengeleg, bruk dei til å utføre oppgåva.")
    String delegateTask(String taskDescription);
}
