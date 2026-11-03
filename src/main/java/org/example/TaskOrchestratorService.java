package org.example;

import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Ei teneste for å orkestrere AI-oppgåver og databaselagring.
 */
@Service
public class TaskOrchestratorService {

    private final ManagerAgent managerAgent;
    private final TaskRepository taskRepository;

    public TaskOrchestratorService(ManagerAgent managerAgent, TaskRepository taskRepository) {
        this.managerAgent = managerAgent;
        this.taskRepository = taskRepository;
    }

    /**
     * Køyrer arbeidsflyten: Opprettar Task, bryt ned oppgåva, lagrar SubTasks,
     * delegerer deretter kvar SubTask og oppdaterer status i DB.
     *
     * @param taskDescription Skildring av oppgåva.
     * @return Eit oversyn over prosessen.
     */
    public String orchestrateTask(String taskDescription) {
        // 1. Lagre hovedoppgåva i db som MOTTATT
        Task taskEntity = new Task(taskDescription, "MOTTATT");
        taskRepository.save(taskEntity);

        // 2. Be AI-manageren om å bryte ned oppgåva
        TaskDecompositionResult result = managerAgent.decomposeTask(taskDescription);

        if (result == null || result.getSubTasks() == null || result.getSubTasks().isEmpty()) {
            taskEntity.setStatus("FEILA");
            taskRepository.save(taskEntity);
            return "Kunne ikkje bryte ned oppgåva.";
        }

        // 3. Lagre deloppgåvene (SubTasks) i databasen som PÅGÅANDE
        List<String> subTasks = result.getSubTasks();
        for (String subTaskDesc : subTasks) {
            SubTask subTask = new SubTask(subTaskDesc, "PÅGÅANDE", taskEntity);
            taskEntity.addSubTask(subTask);
        }

        taskEntity.setStatus("PÅGÅANDE");
        taskRepository.save(taskEntity);

        StringBuilder workflowLog = new StringBuilder("Startar delegering for oppgåve " + taskEntity.getId() + ":\n");

        // 4. Løkke for å delegere kvar enkelt deloppgåve
        for (SubTask subTask : taskEntity.getSubTasks()) {
            workflowLog.append("- Delegerer: ").append(subTask.getDescription()).append("\n");

            // Brukar manageren til å ta seg av spesifikke deloppgåver (som kan involvere verktøy som CalendarAgent)
            String subTaskResponse = managerAgent.delegateTask(subTask.getDescription());

            workflowLog.append("  Svar: ").append(subTaskResponse).append("\n");

            // Marker deloppgåva som FULLFØRT
            subTask.setStatus("FULLFØRT");
        }

        // 5. Oppdater hovudstatus når alt er ferdig
        taskEntity.setStatus("FULLFØRT");
        taskRepository.save(taskEntity);

        workflowLog.append("Heile prosessen er fullført!");
        return workflowLog.toString();
    }
}
