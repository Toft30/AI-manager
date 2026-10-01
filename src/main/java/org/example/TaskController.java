package org.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * REST-kontroller for å teste AI Manageren.
 */
@RestController
public class TaskController {

    private final TaskOrchestratorService orchestratorService;
    private final TaskRepository taskRepository;

    public TaskController(TaskOrchestratorService orchestratorService, TaskRepository taskRepository) {
        this.orchestratorService = orchestratorService;
        this.taskRepository = taskRepository;
    }

    /**
     * Eit enkelt endepunkt for å sende inn ei oppgåve til manager-agenten.
     * Døme på bruk: GET /api/task?taskDescription=Schedule a meeting for tomorrow at 10 AM
     * Oppgåva blir analysert, broten ned i deloppgåver, og delegert vidare.
     *
     * @param taskDescription Oppgåveskildring som skal sendast til manageren.
     * @return Resultatlogg frå orkestreringa.
     */
    @GetMapping("/api/task")
    public String delegateTask(@RequestParam(defaultValue = "Schedule a meeting for tomorrow at 10 AM") String taskDescription) {
        System.out.println("Tek imot oppgåve via HTTP: " + taskDescription);
        return orchestratorService.orchestrateTask(taskDescription);
    }

    /**
     * Hentar alle tasks med tilhøyrande subtasks.
     * Blir brukt asynkront (fetch) av frontend dashboardet.
     *
     * @return Liste over oppgåver (Task)
     */
    @GetMapping("/api/tasks")
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }
}
