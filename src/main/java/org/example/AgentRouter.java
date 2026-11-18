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
import java.util.function.Function;
import java.util.function.LongSupplier;
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
 *
 * <p>
 * Har eit forsøk alt endra kalenderen før det feila, blir neste modell <em>ikkje</em> prøvd,
 * sidan han ville gjort same endringa ein gong til (til dømes to like møte).
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
     * @param planner Planleggjaren som brukar same modell.
     */
    public record Route(String model, ManagerAgent agent, PlannerAgent planner) {
    }

    private final List<Route> routes;
    private final ChatMemory chatMemory;
    private final Supplier<String> clock;
    private final Supplier<String> memory;
    private final LongSupplier changes;
    private final Map<String, Instant> pausedUntil = new ConcurrentHashMap<>();
    private volatile String lastModel = "-";

    /**
     * @param routes Modellane i kjeda, hovudmodellen først.
     * @param chatMemory Samtaleminnet som alle modellane deler.
     * @param clock Gir noverande tidspunkt til systemmeldinga.
     * @param memory Gir ordboka til systemmeldinga.
     * @param changes Tel kor mange endringar verktøya har gjort utanfor appen, til dømes i kalenderen.
     */
    public AgentRouter(List<Route> routes, ChatMemory chatMemory, Supplier<String> clock, Supplier<String> memory,
                       LongSupplier changes) {
        this.routes = List.copyOf(routes);
        this.chatMemory = chatMemory;
        this.clock = clock;
        this.memory = memory;
        this.changes = changes;
    }

    /**
     * Sender oppgåva til første tilgjengelege modell, og vidare nedover kjeda ved feil.
     * Den siste modellen blir alltid prøvd, så det finst eit svar eller ei ekte feilmelding.
     * @param task Oppgåva frå brukaren.
     * @return Svaret frå modellen.
     */
    public synchronized String ask(String task) {
        String now = clock.get();
        String learned = memory.get();
        return firstAvailable(route -> route.agent().delegateTask(task, now, learned));
    }

    /**
     * Bryt ned ei oppgåve i deloppgåver med første tilgjengelege modell.
     * @param task Oppgåva frå brukaren.
     * @param maxSubTasks Det høgaste talet på deloppgåver.
     * @return Deloppgåvene.
     */
    public synchronized TaskPlan plan(String task, int maxSubTasks) {
        String now = clock.get();
        return firstAvailable(route -> route.planner().decomposeTask(task, now, maxSubTasks));
    }

    private <T> T firstAvailable(Function<Route, T> call) {
        List<ChatMessage> memoryBefore = new ArrayList<>(chatMemory.messages());
        RuntimeException lastError = null;

        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);
            boolean isLast = i == routes.size() - 1;
            if (!isLast && Instant.now().isBefore(pausedUntil.getOrDefault(route.model(), Instant.EPOCH))) {
                continue;
            }
            long changesBefore = changes.getAsLong();
            try {
                T answer = call.apply(route);
                lastModel = route.model();
                return answer;
            } catch (RuntimeException e) {
                Duration pause = pauseFor(e);
                pausedUntil.put(route.model(), Instant.now().plus(pause));
                System.out.println("Modellen " + route.model() + " feila, hoppar over han i "
                        + pause.toMinutes() + " min: " + firstLine(e.getMessage()));
                // Det mislukka forsøket har alt lagt meldinga i minnet; rull tilbake så ho ikkje kjem to gonger.
                restoreMemory(memoryBefore);
                if (changes.getAsLong() != changesBefore) {
                    // Neste modell ville gjenteke verktøykall som alt har lukkast, og til dømes laga møtet på nytt.
                    throw new AgentException("Kalenderen vart endra, men Gemini svarte ikkje etterpå. "
                            + "Sjekk kalenderen før du prøver igjen.", e);
                }
                lastError = e;
            }
        }
        throw new AgentException(lastError != null && isQuota(lastError)
                ? "Gemini-kvota er brukt opp for no. Prøv igjen seinare."
                : "Gemini svarte ikkje. Prøv igjen litt seinare.", lastError);
    }

    /**
     * @return Modellen som svarte sist, til telemetri-panelet.
     */
    public String lastModel() {
        return lastModel;
    }

    private static boolean isQuota(RuntimeException e) {
        String message = String.valueOf(e.getMessage());
        return e instanceof RateLimitException || message.contains("\"code\": 429")
                || message.contains("RESOURCE_EXHAUSTED");
    }

    private static Duration pauseFor(RuntimeException e) {
        if (!isQuota(e)) {
            return BUSY_PAUSE;
        }
        Matcher m = RETRY_DELAY.matcher(String.valueOf(e.getMessage()));
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
