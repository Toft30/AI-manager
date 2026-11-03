package org.example;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

/**
 * Eit verktøy for å oppdatere status på oppgåver og deloppgåver i databasen frå AI-manageren.
 */
@Component
public class TaskUpdaterTool {

    private final TaskRepository taskRepository;
    private final SubTaskRepository subTaskRepository;

    public TaskUpdaterTool(TaskRepository taskRepository, SubTaskRepository subTaskRepository) {
        this.taskRepository = taskRepository;
        this.subTaskRepository = subTaskRepository;
    }

    /**
     * Oppdaterer status for ei oppgåve.
     *
     * @param taskId ID-en til oppgåva i databasen.
     * @param newStatus Den nye statusen (MOTTATT, PÅGÅANDE, FULLFØRT eller FEILA).
     * @return Eit strukturert svar om oppdateringa gjekk bra.
     */
    @Tool("Updates the status of a task in the database. Only use it when you are given a task id.")
    public String updateTaskStatus(@P("The task id") long taskId,
                                   @P("New status: MOTTATT, PÅGÅANDE, FULLFØRT or FEILA") String newStatus) {
        TaskStatus status = parse(newStatus);
        if (status == null) {
            return ToolResponse.failed("Ukjend status «" + newStatus + "». Bruk ein av " + Arrays.toString(TaskStatus.values())).toString();
        }
        System.out.println("AI oppdaterer status for oppgåve " + taskId + " til: " + status);
        return taskRepository.findById(taskId).map(task -> {
            task.setStatus(status);
            taskRepository.save(task);
            return ToolResponse.ok("Status for oppgåve " + taskId + " er no " + status).toString();
        }).orElse(ToolResponse.failed("Fann inga oppgåve med id " + taskId).toString());
    }

    /**
     * Oppdaterer status for ei deloppgåve, til dømes for å melde at ho ikkje let seg gjere.
     *
     * @param subTaskId ID-en til deloppgåva.
     * @param newStatus Den nye statusen.
     * @param note Kort forklaring, særleg når status er FEILA.
     * @return Eit strukturert svar om oppdateringa gjekk bra.
     */
    @Tool("Updates the status of a subtask. Use it with status FEILA when you cannot complete the subtask you are working on.")
    public String updateSubTaskStatus(@P("The subtask id") long subTaskId,
                                      @P("New status: PÅGÅANDE, FULLFØRT or FEILA") String newStatus,
                                      @P("Short explanation, especially when it failed") String note) {
        TaskStatus status = parse(newStatus);
        if (status == null) {
            return ToolResponse.failed("Ukjend status «" + newStatus + "». Bruk ein av " + Arrays.toString(TaskStatus.values())).toString();
        }
        System.out.println("AI oppdaterer status for deloppgåve " + subTaskId + " til: " + status);
        return subTaskRepository.findById(subTaskId).map(subTask -> {
            subTask.setStatus(status);
            if (note != null && !note.isBlank()) {
                subTask.setResult(note);
            }
            subTaskRepository.save(subTask);
            return ToolResponse.ok("Status for deloppgåve " + subTaskId + " er no " + status).toString();
        }).orElse(ToolResponse.failed("Fann inga deloppgåve med id " + subTaskId).toString());
    }

    /**
     * Tolkar status frå modellen, òg utan særnorske teikn («FULLFORT», «pagaande»).
     */
    private static TaskStatus parse(String value) {
        if (value == null) {
            return null;
        }
        String wanted = fold(value);
        return Arrays.stream(TaskStatus.values()).filter(s -> fold(s.name()).equals(wanted)).findFirst().orElse(null);
    }

    private static String fold(String s) {
        return s.trim().toUpperCase(Locale.ROOT).replace("Å", "A").replace("Ø", "O").replace("Æ", "AE");
    }
}
