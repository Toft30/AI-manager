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
    private final DialectMemory dialectMemory;
    private final TaskOrchestratorService taskOrchestratorService;

    public TaskController(AgentRouter agentRouter, TaskRepository taskRepository, ChatMemory chatMemory,
                          DialectMemory dialectMemory, TaskOrchestratorService taskOrchestratorService) {
        this.agentRouter = agentRouter;
        this.taskRepository = taskRepository;
        this.chatMemory = chatMemory;
        this.dialectMemory = dialectMemory;
        this.taskOrchestratorService = taskOrchestratorService;
    }

    /**
     * Sender inn ei oppgåve til manager-agenten og lagrar ho med status og resultat.
     * Døme på bruk: GET /api/task?task=Schedule a meeting for tomorrow at 10 AM
     *
     * @param task Oppgåveskildring som skal sendast til manageren.
     * @param plan Om oppgåva skal brytast ned i deloppgåver først. Kostar fleire kall mot Gemini.
     * @return Den lagra oppgåva med status og svar frå AI-modellen.
     */
    @GetMapping("/api/task")
    public Task delegateTask(@RequestParam(defaultValue = "Schedule a meeting for tomorrow at 10 AM") String task,
                             @RequestParam(defaultValue = "false") boolean plan) {
        System.out.println("Tek imot oppgåve via HTTP" + (plan ? " (med plan): " : ": ") + task);
        Task saved = taskRepository.save(new Task(task));

        saved.setStatus(TaskStatus.PÅGÅANDE);
        saved = taskRepository.save(saved);

        try {
            // Tydelege rettingar blir lagra før modellen svarar, så han ser dei i ordboka med ein gong.
            dialectMemory.learnFromText(task);
            if (plan) {
                return taskOrchestratorService.orchestrate(saved);
            }
            saved.setResult(agentRouter.ask(task));
            saved.setStatus(TaskStatus.FULLFØRT);
        } catch (Exception e) {
            saved.setResult("Feil: " + spokenError(e));
            saved.setStatus(TaskStatus.FEILA);
        }
        return taskRepository.save(saved);
    }

    /**
     * Gir ei kort feilmelding som kan lesast høgt. Den fulle feilen, ofte lang JSON frå Google, blir berre logga.
     */
    static String spokenError(Exception e) {
        if (e instanceof AgentException) {
            System.out.println("Oppgåva feila: " + e.getCause());
            return e.getMessage();
        }
        System.out.println("Oppgåva feila uventa: " + e);
        return "Noko gjekk gale. Sjå loggen for detaljar.";
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
