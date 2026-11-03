package org.example;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * REST-kontroller for å sende oppgåver til AI Manageren og hente dei ut att.
 */
@RestController
public class TaskController {

    private final ManagerAgent managerAgent;
    private final ManagerAgent fallbackManagerAgent;
    private final TaskRepository taskRepository;
    private final ChatMemory chatMemory;

    private static final Duration PRIMARY_PAUSE = Duration.ofMinutes(5);
    private volatile Instant primaryPausedUntil = Instant.EPOCH;

    public TaskController(ManagerAgent managerAgent,
                          @Qualifier("fallbackManagerAgent") ManagerAgent fallbackManagerAgent,
                          TaskRepository taskRepository, ChatMemory chatMemory) {
        this.managerAgent = managerAgent;
        this.fallbackManagerAgent = fallbackManagerAgent;
        this.taskRepository = taskRepository;
        this.chatMemory = chatMemory;
    }

    /**
     * Sender inn ei oppgåve til manager-agenten og lagrar ho med status og resultat.
     * Døme på bruk: GET /api/task?task=Schedule a meeting for tomorrow at 10 AM
     *
     * @param task Oppgåveskildring som skal sendast til manageren.
     * @return Den lagra oppgåva med status og svar frå AI-modellen.
     */
    @GetMapping("/api/task")
    public Task delegateTask(@RequestParam(defaultValue = "Schedule a meeting for tomorrow at 10 AM") String task) {
        System.out.println("Tek imot oppgåve via HTTP: " + task);
        Task saved = taskRepository.save(new Task(task));

        saved.setStatus(TaskStatus.PÅGÅANDE);
        saved = taskRepository.save(saved);

        List<ChatMessage> memoryBefore = new ArrayList<>(chatMemory.messages());
        try {
            // Når hovudmodellen nettopp har feila, gå rett til reservemodellen i staden for å vente på ny 503.
            if (Instant.now().isBefore(primaryPausedUntil)) {
                throw new IllegalStateException("hovudmodellen er på pause etter ein feil");
            }
            saved.setResult(managerAgent.delegateTask(task));
            saved.setStatus(TaskStatus.FULLFØRT);
        } catch (Exception e) {
            System.out.println("Hovudmodellen feila, prøver reservemodellen: " + e.getMessage());
            if (Instant.now().isAfter(primaryPausedUntil)) {
                primaryPausedUntil = Instant.now().plus(PRIMARY_PAUSE);
            }
            // Det mislukka forsøket har alt lagt meldinga i minnet; rull tilbake så ho ikkje kjem to gonger.
            restoreMemory(memoryBefore);
            try {
                saved.setResult(fallbackManagerAgent.delegateTask(task));
                saved.setStatus(TaskStatus.FULLFØRT);
            } catch (Exception fallbackError) {
                restoreMemory(memoryBefore);
                saved.setResult("Feil: " + fallbackError.getMessage());
                saved.setStatus(TaskStatus.FEILA);
            }
        }
        return taskRepository.save(saved);
    }

    private void restoreMemory(List<ChatMessage> messages) {
        chatMemory.clear();
        messages.forEach(chatMemory::add);
    }

    /**
     * Hentar alle oppgåver, til bruk i dashboardet.
     *
     * @return Liste over alle oppgåver.
     */
    @GetMapping("/api/tasks")
    public List<Task> getTasks() {
        return taskRepository.findAll();
    }

    /**
     * Startar ein ny samtale, slik at ODIN gløymer det som vart sagt før.
     */
    @PostMapping("/api/reset")
    public void reset() {
        chatMemory.clear();
    }
}
