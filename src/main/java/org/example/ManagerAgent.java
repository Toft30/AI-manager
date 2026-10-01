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
     * @param taskDescription Skildring av oppgåva frå brukaren, inkludert oppgåve-ID for databaselogging.
     * @return AI-modellen sitt endelege svar etter evaluering.
     */
    @SystemMessage({
        "Du er ein Manager AI-agent. Di oppgåve er å delegere oppgåver til andre spesialiserte agentar.",
        "Når du delegerer, MÅ du inkludere tydelege rammevilkår og kontekst.",
        "Underagentane returnerer eit strukturert JSON-svar med 'status' og 'partialResult'.",
        "Du MÅ evaluere resultatet frå underagenten. Viss status er 'feila' eller resultatet ikkje er godt nok,",
        "skal du automatisk sende eit nytt direktiv med korreksar tilbake til agenten heilt til det blir bra.",
        "Når ei oppgåve er under arbeid, bruk TaskUpdaterTool for å oppdatere databasen til status 'PÅGÅANDE'.",
        "Når du er heilt ferdig og resultatet er godkjent, bruk TaskUpdaterTool til å setje status til 'FULLFØRT'.",
        "Bruk den gitte taskId i taskDescription til å gjere oppdateringane."
    })
    String delegateTask(String taskDescription);

    /**
     * Bryt ned ein stor forespørsel til ei liste med mindre deloppgåver.
     *
     * @param taskDescription Skildring av oppgåva frå brukaren.
     * @return Strukturert resultat som inneheld ei liste av deloppgåver.
     */
    @SystemMessage("Du er ein analytisk Manager AI-agent. Di oppgåve er å bryte ned den gitte komplekse forespørselen til ei logisk rekkjefølgje av mindre, konkrete deloppgåver. Returner eit strukturert JSON-svar som inneheld ei liste over desse deloppgåvene.")
    TaskDecompositionResult decomposeTask(String taskDescription);
}
