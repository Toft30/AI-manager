package org.example;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * Hovudklassen for AI Manager applikasjonen.
 * Denne klassen set opp språkmodellen og instansierer ManagerAgent.
 */
public class Main {

    /**
     * Inngangspunkt for applikasjonen.
     *
     * @param args Kommandolinjeargument (ikkje i bruk).
     */
    public static void main(String[] args) {
        System.out.println("Starting AI Manager...");

        // Hent API-nøkkel frå miljøvariabelen OPENAI_API_KEY for å unngå hardkoding
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Feil: OPENAI_API_KEY miljøvariabelen er ikkje sett.");
            return;
        }

        // Opprett ein OpenAiChatModel instans
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o-mini") // Brukar ein vanleg og kostnadseffektiv modell
                .build();

        // Kople ManagerAgent-grensesnittet mot chatmodellen via AiServices
        ManagerAgent manager = AiServices.builder(ManagerAgent.class)
                .chatLanguageModel(model)
                .build();

        System.out.println("ManagerAgent initialized. Sending a test task...");

        // Eit enkelt kall for å teste at alt fungerer
        String response = manager.delegateTask("Schedule a meeting for tomorrow at 10 AM");

        System.out.println("Response from ManagerAgent:\n" + response);
    }
}
