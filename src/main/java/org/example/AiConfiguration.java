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

    @Value("${GOOGLE_API_KEY:${GEMINI_API_KEY:}}")
    private String apiKey;

    /**
     * Set opp ChatLanguageModel med Google Gemini.
     * @return Ein konfigurert ChatLanguageModel-instans.
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Miljøvariabelen GOOGLE_API_KEY eller GEMINI_API_KEY må setjast i miljøet/application.properties.");
        }

        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-1.5-flash")
                .build();
    }

    /**
     * Knyttar ManagerAgent-grensesnittet til ChatLanguageModel og registrerer verktøy.
     * @param chatLanguageModel Modellen som skal brukast.
     * @param calendarAgent CalendarAgent-verktøyet for å planleggje møter.
     * @return Ein ferdig konfigurert ManagerAgent.
     */
    @Bean
    public ManagerAgent managerAgent(ChatLanguageModel chatLanguageModel, CalendarAgent calendarAgent) {
        return AiServices.builder(ManagerAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(calendarAgent)
                .build();
    }
}
