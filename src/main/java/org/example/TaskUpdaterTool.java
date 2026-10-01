package org.example;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * Eit verktøy for å oppdatere status på oppgåver i databasen frå AI-manageren.
 */
@Component
public class TaskUpdaterTool {

    private final TaskRepository taskRepository;

    public TaskUpdaterTool(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * Oppdaterer status for ei oppgåve.
     *
     * @param taskId ID-en til oppgåva i databasen.
     * @param newStatus Den nye statusen (t.d. "PÅGÅANDE", "FULLFØRT", "FEILA").
     * @return Ei stadfesting på at statusen vart oppdatert.
     */
    @Tool("Updates the status of a specific task in the database.")
    public String updateTaskStatus(Long taskId, String newStatus) {
        System.out.println("AI oppdaterer status for Task ID " + taskId + " til: " + newStatus);

        return taskRepository.findById(taskId).map(task -> {
            task.setStatus(newStatus);
            taskRepository.save(task);
            return "Status for oppgåve " + taskId + " er no oppdatert til " + newStatus;
        }).orElse("Kunne ikkje finne oppgåve med ID " + taskId);
    }
}
