package org.example;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;

/**
 * Konfigurasjonsklasse for AI og LangChain4j-komponentar.
 */
@Configuration
public class AiConfiguration {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.8-flash}")
    private String modelName;

    @Value("${gemini.timeout-seconds:60}")
    private long timeoutSeconds;

    @Value("${gemini.max-retries:3}")
    private int maxRetries;

    @Value("${gemini.thinking-level:LOW}")
    private GeminiThinkingConfig.GeminiThinkingLevel thinkingLevel;

    @Value("${gemini.primary-max-retries:0}")
    private int primaryMaxRetries;

    @Value("${gemini.fallback-model:gemini-3.5-flash}")
    private String fallbackModelName;

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
     * Hovud- og reservemodellen deler det same minnet.
     * @return Eit minne med dei siste 30 meldingane.
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.withMaxMessages(30);
    }

    /**
     * Knyttar ManagerAgent-grensesnittet til hovudmodellen og registrerer verktøy.
     * @param chatMemory Samtaleminnet.
     * @param calendarAgent CalendarAgent-verktøyet for å planleggje møter.
     * @return Ein ferdig konfigurert ManagerAgent.
     */
    @Bean
    @Primary
    public ManagerAgent managerAgent(ChatMemory chatMemory, CalendarAgent calendarAgent) {
        // Få forsøk her, så reservemodellen tek over raskt når hovudmodellen er overbelasta.
        return agent(modelName, primaryMaxRetries, chatMemory, calendarAgent);
    }

    /**
     * Same agent på ein reservemodell, som blir brukt når hovudmodellen er overbelasta (503).
     * @param chatMemory Samtaleminnet.
     * @param calendarAgent CalendarAgent-verktøyet for å planleggje møter.
     * @return Ein ManagerAgent på reservemodellen.
     */
    @Bean
    public ManagerAgent fallbackManagerAgent(ChatMemory chatMemory, CalendarAgent calendarAgent) {
        return agent(fallbackModelName, maxRetries, chatMemory, calendarAgent);
    }

    private ManagerAgent agent(String name, int retries, ChatMemory chatMemory, CalendarAgent calendarAgent) {
        return AiServices.builder(ManagerAgent.class)
                .chatModel(gemini(name, retries))
                .chatMemory(chatMemory)
                .tools(calendarAgent)
                .build();
    }
}
