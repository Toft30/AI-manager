package org.example;

import dev.langchain4j.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST-kontroller for å sende oppgåver til AI Manageren og hente dei ut att.
 */
@RestController
public class TaskController {

    private final AgentRouter agentRouter;
    private final TaskRepository taskRepository;
    private final ChatMemory chatMemory;

    public TaskController(AgentRouter agentRouter, TaskRepository taskRepository, ChatMemory chatMemory) {
        this.agentRouter = agentRouter;
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

        try {
            saved.setResult(agentRouter.ask(task));
            saved.setStatus(TaskStatus.FULLFØRT);
        } catch (Exception e) {
            saved.setResult("Feil: " + e.getMessage());
            saved.setStatus(TaskStatus.FEILA);
        }
        return taskRepository.save(saved);
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
