package org.example;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

/**
 * Konfigurasjonsklasse for AI og LangChain4j-komponentar.
 */
@Configuration
public class AiConfiguration {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String modelName;

    /**
     * Set opp ChatLanguageModel med Google Gemini.
     * @return Ein konfigurert ChatLanguageModel-instans.
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Miljøvariabelen GEMINI_API_KEY eller GOOGLE_API_KEY må setjast.");
        }

        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    /**
     * Knyttar ManagerAgent-grensesnittet til ChatLanguageModel og registrerer verktøy.
     * @param chatLanguageModel Modellen som skal brukast.
     * @param calendarAgent CalendarAgent-verktøyet for å planleggje møter.
     * @param taskUpdaterTool Verktøyet for å oppdatere task-status.
     * @return Ein ferdig konfigurert ManagerAgent.
     */
    @Bean
    public ManagerAgent managerAgent(ChatLanguageModel chatLanguageModel, CalendarAgent calendarAgent, TaskUpdaterTool taskUpdaterTool) {
        return AiServices.builder(ManagerAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(calendarAgent, taskUpdaterTool)
                .build();
    }
}
