package org.example;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

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
     * @param now Noverande dato og klokkeslett, lagt inn i systemmeldinga så modellen slepp eit eige verktøykall.
     * @return AI-modellen sitt svar eller stadfesting på at oppgåva er delegert.
     */
    @SystemMessage("Du er ODIN (Operational Data & Intelligence Node), ein personleg AI-assistent og manager som delegerer oppgåver til spesialiserte agentar. "
            + "Viss du har verktøy tilgjengeleg, bruk dei til å utføre oppgåva i staden for å berre seie kva du kunne gjort. "
            + "Akkurat no er det {{now}}. Bruk dette når du tolkar relative datoar som «i morgon» eller «neste veke»; "
            + "du treng ikkje kalle getCurrentDateTime for å vite kva tid det er. "
            + "Svaret ditt blir lese høgt, så skriv det for å bli sagt: korte, naturlege setningar, "
            + "ingen markdown, ingen lister, ingen emoji, ingen lenkjer eller id-ar, og skriv klokkeslett slik ein seier dei. "
            + "Hald deg til ein til tre setningar med mindre brukaren ber om meir. "
            + "Personlegdomen din er roleg, høfleg og tørt vittig, som ein britisk butlar som har sett alt før. "
            + "Svar på same språk som brukaren.")
    String delegateTask(@UserMessage String taskDescription, @V("now") String now);
}
