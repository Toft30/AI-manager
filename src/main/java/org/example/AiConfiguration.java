package org.example;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Konfigurasjonsklasse for AI og LangChain4j-komponentar.
 */
@Configuration
public class AiConfiguration {

    /** Det modellen får sjå når eit verktøy kastar eit uventa unntak. */
    static final String UNEXPECTED_TOOL_ERROR = ToolResponse.failed(
            "Verktøyet feila uventa. Ikkje prøv det same kallet igjen; fortel brukaren kort at det ikkje gjekk.")
            .toString();

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.8-flash}")
    private String modelName;

    @Value("${gemini.fallback-models:gemini-3.5-flash,gemini-3.5-flash-lite}")
    private List<String> fallbackModels;

    @Value("${gemini.timeout-seconds:60}")
    private long timeoutSeconds;

    @Value("${gemini.max-retries:3}")
    private int maxRetries;

    @Value("${gemini.thinking-level:LOW}")
    private GeminiThinkingConfig.GeminiThinkingLevel thinkingLevel;

    /**
     * Lagar ein Gemini-modell.
     * @param name Namnet på modellen.
     * @param retries Kor mange gonger han skal prøve på nytt ved feil som 503.
     * @return Ein konfigurert ChatModel-instans.
     */
    private ChatModel gemini(String name, int retries) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Miljøvariabelen GEMINI_API_KEY eller GOOGLE_API_KEY må setjast.");
        }

        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(name)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(retries)
                // Gemini 3 tenkjer før svaret; lågt nivå gir raskare svar.
                // Tankane må sendast tilbake til modellen når han brukar verktøy.
                .thinkingConfig(GeminiThinkingConfig.builder().thinkingLevel(thinkingLevel).build())
                .returnThinking(true)
                .sendThinking(true)
                .build();
    }

    /**
     * Samtaleminnet til ODIN, så han hugsar det som vart sagt tidlegare i samtalen.
     * Alle modellane i kjeda deler det same minnet.
     * @return Eit minne med dei siste 30 meldingane.
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.withMaxMessages(30);
    }

    /**
     * Lagar kjeda av modellar: hovudmodellen først, så reservemodellane i rekkjefølgje.
     * @param chatMemory Samtaleminnet.
     * @param calendarAgent CalendarAgent-verktøyet for å planleggje møter.
     * @param dialectMemory Ordboka, som både blir lagd i systemmeldinga og gir ODIN verktøy for å lære.
     * @param taskUpdaterTool Verktøyet for å oppdatere status på oppgåver og deloppgåver.
     * @return Ein router som vel første tilgjengelege modell.
     */
    @Bean
    public AgentRouter agentRouter(ChatMemory chatMemory, CalendarAgent calendarAgent, DialectMemory dialectMemory,
                                   TaskUpdaterTool taskUpdaterTool) {
        List<String> models = new ArrayList<>();
        models.add(modelName);
        fallbackModels.stream().map(String::trim).filter(m -> !m.isEmpty() && !models.contains(m)).forEach(models::add);

        List<AgentRouter.Route> routes = new ArrayList<>();
        for (int i = 0; i < models.size(); i++) {
            // Ingen nye forsøk før den siste modellen: neste modell i kjeda er det nye forsøket.
            int retries = i == models.size() - 1 ? maxRetries : 0;
            ChatModel model = gemini(models.get(i), retries);
            ManagerAgent agent = AiServices.builder(ManagerAgent.class)
                    .chatModel(model)
                    .chatMemory(chatMemory)
                    .tools(calendarAgent, dialectMemory, taskUpdaterTool)
                    // Feil i argumenta frå modellen er skrivne for han, så han kan rette dei og prøve igjen.
                    .toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())
                    // Verktøya svarar sjølve med ToolResponse ved kjende feil. Uventa unntak kan innehalde
                    // filstiar eller svar frå Google, så modellen får berre ei generell melding og den
                    // eigentlege feilen blir logga. Å feile heile kallet ville fått AgentRouter til å
                    // prøve neste modell, som kunne gjenteke verktøykall som alt hadde lukkast.
                    .toolExecutionErrorHandler(ToolExecutionErrorHandler.sendGenericMessageToLlmUnlessVisibleToLlm(
                            UNEXPECTED_TOOL_ERROR))
                    .build();
            PlannerAgent planner = AiServices.builder(PlannerAgent.class)
                    .chatModel(model)
                    .build();
            routes.add(new AgentRouter.Route(models.get(i), agent, planner));
        }
        return new AgentRouter(routes, chatMemory, calendarAgent::getCurrentDateTime, dialectMemory::render,
                calendarAgent::changeCount);
    }
}
