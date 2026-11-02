package org.example;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    /**
     * Set opp ChatModel med Google Gemini.
     * @return Ein konfigurert ChatModel-instans.
     */
    @Bean
    public ChatModel chatModel() {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Miljøvariabelen GEMINI_API_KEY eller GOOGLE_API_KEY må setjast.");
        }

        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(1)
                .build();
    }

    /**
     * Knyttar ManagerAgent-grensesnittet til ChatModel og registrerer verktøy.
     * @param chatModel Modellen som skal brukast.
     * @param calendarAgent CalendarAgent-verktøyet for å planleggje møter.
     * @return Ein ferdig konfigurert ManagerAgent.
     */
    @Bean
    public ManagerAgent managerAgent(ChatModel chatModel, CalendarAgent calendarAgent) {
        return AiServices.builder(ManagerAgent.class)
                .chatModel(chatModel)
                .tools(calendarAgent)
                .build();
    }
}
