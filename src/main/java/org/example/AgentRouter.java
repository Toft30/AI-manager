package org.example;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.memory.ChatMemory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sender oppgåva til den første Gemini-modellen i kjeda som er tilgjengeleg.
 *
 * <p>
 * Gratisnivået hos Google har ei lita dagskvote per modell. Når ein modell svarar 429,
 * blir han hoppa over til kvota er nullstilt (Google seier kor lenge i «retryDelay»),
 * så kvart spørsmål ikkje kastar bort eit kall på ein modell som uansett seier nei.
 * Andre feil, som 503 ved høg pågang, gir ein kort pause.
 * </p>
 */
public class AgentRouter {

    private static final Duration BUSY_PAUSE = Duration.ofMinutes(2);
    private static final Duration QUOTA_PAUSE = Duration.ofHours(1);
    private static final Pattern RETRY_DELAY = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)(?:\\.\\d+)?s\"");

    /**
     * Ein modell i kjeda.
     * @param model Namnet på Gemini-modellen.
     * @param agent Agenten som brukar modellen.
     */
    public record Route(String model, ManagerAgent agent) {
    }

    private final List<Route> routes;
    private final ChatMemory chatMemory;
    private final Supplier<String> clock;
    private final Supplier<String> memory;
    private final Map<String, Instant> pausedUntil = new ConcurrentHashMap<>();
    private volatile String lastModel = "-";

    public AgentRouter(List<Route> routes, ChatMemory chatMemory, Supplier<String> clock, Supplier<String> memory) {
        this.routes = List.copyOf(routes);
        this.chatMemory = chatMemory;
        this.clock = clock;
        this.memory = memory;
    }

    /**
     * Sender oppgåva til første tilgjengelege modell, og vidare nedover kjeda ved feil.
     * Den siste modellen blir alltid prøvd, så det finst eit svar eller ei ekte feilmelding.
     * @param task Oppgåva frå brukaren.
     * @return Svaret frå modellen.
     */
    public synchronized String ask(String task) {
        List<ChatMessage> memoryBefore = new ArrayList<>(chatMemory.messages());
        String now = clock.get();
        String learned = memory.get();
        RuntimeException lastError = null;

        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);
            boolean isLast = i == routes.size() - 1;
            if (!isLast && Instant.now().isBefore(pausedUntil.getOrDefault(route.model(), Instant.EPOCH))) {
                continue;
            }
            try {
                String answer = route.agent().delegateTask(task, now, learned);
                lastModel = route.model();
                return answer;
            } catch (RuntimeException e) {
                Duration pause = pauseFor(e);
                pausedUntil.put(route.model(), Instant.now().plus(pause));
                System.out.println("Modellen " + route.model() + " feila, hoppar over han i "
                        + pause.toMinutes() + " min: " + firstLine(e.getMessage()));
                // Det mislukka forsøket har alt lagt meldinga i minnet; rull tilbake så ho ikkje kjem to gonger.
                restoreMemory(memoryBefore);
                lastError = e;
            }
        }
        throw lastError;
    }

    /**
     * @return Modellen som svarte sist, til telemetri-panelet.
     */
    public String lastModel() {
        return lastModel;
    }

    private static Duration pauseFor(RuntimeException e) {
        String message = String.valueOf(e.getMessage());
        boolean quota = e instanceof RateLimitException || message.contains("\"code\": 429")
                || message.contains("RESOURCE_EXHAUSTED");
        if (!quota) {
            return BUSY_PAUSE;
        }
        Matcher m = RETRY_DELAY.matcher(message);
        return m.find() ? Duration.ofSeconds(Long.parseLong(m.group(1))) : QUOTA_PAUSE;
    }

    private void restoreMemory(List<ChatMessage> messages) {
        chatMemory.clear();
        messages.forEach(chatMemory::add);
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ");
        return flat.length() > 160 ? flat.substring(0, 160) : flat;
    }
}
