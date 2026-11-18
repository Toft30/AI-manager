package org.example;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testar modellkjeda i AgentRouter.
 */
class AgentRouterTest {

    private final AtomicLong changes = new AtomicLong();
    private final AtomicInteger fallbackCalls = new AtomicInteger();

    private final ManagerAgent fallback = (task, now, memory) -> {
        fallbackCalls.incrementAndGet();
        return "svar frå reserve";
    };

    private AgentRouter router(ManagerAgent first) {
        return new AgentRouter(List.of(
                new AgentRouter.Route("fyrste", first, null),
                new AgentRouter.Route("reserve", fallback, null)),
                MessageWindowChatMemory.withMaxMessages(10), () -> "no", () -> "", changes::get);
    }

    @Test
    void provarNesteModellNarFyrsteFeilarUtanEndringar() {
        AgentRouter router = router((task, now, memory) -> {
            throw new RuntimeException("503 UNAVAILABLE");
        });

        assertThat(router.ask("hei")).isEqualTo("svar frå reserve");
        assertThat(router.lastModel()).isEqualTo("reserve");
    }

    @Test
    void provarIkkjeNesteModellNarKalenderenAltErEndra() {
        AgentRouter router = router((task, now, memory) -> {
            changes.incrementAndGet(); // møtet vart oppretta ...
            throw new RuntimeException("503 UNAVAILABLE"); // ... men svaret etterpå feila
        });

        assertThatThrownBy(() -> router.ask("lag eit møte i morgon kl. 10"))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("Kalenderen vart endra");
        assertThat(fallbackCalls).hasValue(0);
    }

    @Test
    void girKortMeldingNarAlleModellaneFeilar() {
        AgentRouter router = new AgentRouter(List.of(new AgentRouter.Route("einaste", (task, now, memory) -> {
            throw new RuntimeException("{\"error\": {\"code\": 429, \"status\": \"RESOURCE_EXHAUSTED\"}}");
        }, null)), MessageWindowChatMemory.withMaxMessages(10), () -> "no", () -> "", changes::get);

        assertThatThrownBy(() -> router.ask("hei"))
                .isInstanceOf(AgentException.class)
                .hasMessage("Gemini-kvota er brukt opp for no. Prøv igjen seinare.");
    }
}
