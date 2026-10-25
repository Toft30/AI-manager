package org.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST-kontroller for å sende oppgåver til AI Manageren og hente dei ut att.
 */
@RestController
public class TaskController {

    private final ManagerAgent managerAgent;
    private final TaskRepository taskRepository;

    public TaskController(ManagerAgent managerAgent, TaskRepository taskRepository) {
        this.managerAgent = managerAgent;
        this.taskRepository = taskRepository;
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
            saved.setResult(managerAgent.delegateTask(task));
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
}
