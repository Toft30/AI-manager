package org.example;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
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
     * @return Ein router som vel første tilgjengelege modell.
     */
    @Bean
    public AgentRouter agentRouter(ChatMemory chatMemory, CalendarAgent calendarAgent) {
        List<String> models = new ArrayList<>();
        models.add(modelName);
        fallbackModels.stream().map(String::trim).filter(m -> !m.isEmpty() && !models.contains(m)).forEach(models::add);

        List<AgentRouter.Route> routes = new ArrayList<>();
        for (int i = 0; i < models.size(); i++) {
            // Ingen nye forsøk før den siste modellen: neste modell i kjeda er det nye forsøket.
            int retries = i == models.size() - 1 ? maxRetries : 0;
            routes.add(new AgentRouter.Route(models.get(i), agent(models.get(i), retries, chatMemory, calendarAgent)));
        }
        return new AgentRouter(routes, chatMemory, calendarAgent::getCurrentDateTime);
    }

    private ManagerAgent agent(String name, int retries, ChatMemory chatMemory, CalendarAgent calendarAgent) {
        return AiServices.builder(ManagerAgent.class)
                .chatModel(gemini(name, retries))
                .chatMemory(chatMemory)
                .tools(calendarAgent)
                .build();
    }
}
