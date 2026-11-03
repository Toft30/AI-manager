package org.example;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Ei teneste for å orkestrere store oppgåver: bryt dei ned i deloppgåver og utfør dei éi etter éi.
 *
 * <p>
 * Kvar deloppgåve kostar eitt kall mot Gemini, pluss eitt for planen og eitt for oppsummeringa,
 * så dette blir berre brukt når brukaren ber om det (/plan i HUD-en), ikkje for vanlege spørsmål.
 * </p>
 */
@Service
public class TaskOrchestratorService {

    static final int MAX_SUBTASKS = 4;

    private final AgentRouter agentRouter;
    private final TaskRepository taskRepository;
    private final SubTaskRepository subTaskRepository;

    public TaskOrchestratorService(AgentRouter agentRouter, TaskRepository taskRepository,
                                   SubTaskRepository subTaskRepository) {
        this.agentRouter = agentRouter;
        this.taskRepository = taskRepository;
        this.subTaskRepository = subTaskRepository;
    }

    /**
     * Køyrer arbeidsflyten: bryt ned oppgåva, lagrar deloppgåvene, delegerer kvar av dei
     * og oppdaterer status i databasen undervegs, så dashboardet kan vise framdrifta.
     *
     * @param task Oppgåva, alt lagra i databasen.
     * @return Oppgåva med deloppgåver, status og eit kort, talevennleg svar.
     */
    public Task orchestrate(Task task) {
        String description = task.getDescription();

        // 1. Be planleggjaren om å bryte ned oppgåva.
        TaskDecompositionResult plan = agentRouter.plan(description, MAX_SUBTASKS);
        List<String> steps = plan == null || plan.getSubTasks() == null ? List.of()
                : plan.getSubTasks().stream().filter(s -> s != null && !s.isBlank()).limit(MAX_SUBTASKS).toList();
        if (steps.isEmpty()) {
            task.setStatus(TaskStatus.FEILA);
            task.setResult("Eg klarte ikkje å bryte ned oppgåva.");
            return taskRepository.save(task);
        }

        // 2. Lagre deloppgåvene, så dei får id og blir synlege i dashboardet.
        steps.forEach(task::addSubTask);
        task.setStatus(TaskStatus.PÅGÅANDE);
        task = taskRepository.save(task);
        List<Long> ids = task.getSubTasks().stream().map(SubTask::getId).toList();

        // 3. Delegere kvar deloppgåve. Statusen blir lesen på nytt frå databasen etterpå,
        //    sidan modellen kan ha sett ho til FEILA med TaskUpdaterTool.
        List<String> outcomes = new ArrayList<>();
        boolean allDone = true;
        for (int i = 0; i < ids.size(); i++) {
            SubTask subTask = subTaskRepository.findById(ids.get(i)).orElseThrow();
            subTask.setStatus(TaskStatus.PÅGÅANDE);
            subTaskRepository.save(subTask);

            String prompt = "Deloppgåve " + (i + 1) + " av " + ids.size() + " (deloppgåve-id " + subTask.getId()
                    + ") i oppgåva «" + description + "»: " + subTask.getDescription();
            String answer;
            try {
                answer = agentRouter.ask(prompt);
            } catch (Exception e) {
                subTask.setStatus(TaskStatus.FEILA);
                subTask.setResult("Feil: " + e.getMessage());
                subTaskRepository.save(subTask);
                return finish(task.getId(), TaskStatus.FEILA,
                        "Eg stoppa ved deloppgåve " + (i + 1) + " fordi Gemini ikkje svarte. Prøv igjen litt seinare.");
            }

            SubTask fresh = subTaskRepository.findById(subTask.getId()).orElseThrow();
            boolean failed = fresh.getStatus() == TaskStatus.FEILA;
            fresh.setStatus(failed ? TaskStatus.FEILA : TaskStatus.FULLFØRT);
            fresh.setResult(failed && fresh.getResult() != null ? fresh.getResult() + " – " + answer : answer);
            subTaskRepository.save(fresh);

            allDone &= !failed;
            outcomes.add((i + 1) + ". " + fresh.getDescription() + (failed ? " (feila): " : ": ") + answer);
        }

        // 4. Éi deloppgåve treng inga oppsummering; fleire får eitt kort, samla svar.
        String summary = outcomes.size() == 1 ? stripNumber(outcomes.get(0)) : summarize(description, outcomes);
        return finish(task.getId(), allDone ? TaskStatus.FULLFØRT : TaskStatus.FEILA, summary);
    }

    private String summarize(String description, List<String> outcomes) {
        try {
            return agentRouter.ask("Alle deloppgåvene i oppgåva «" + description + "» er gjennomgått. Resultat: "
                    + String.join(" ", outcomes) + " Oppsummer kort for brukaren kva som vart gjort, og nemn det som feila.");
        } catch (Exception e) {
            return String.join(" ", outcomes);
        }
    }

    private static String stripNumber(String outcome) {
        int colon = outcome.indexOf(": ");
        return colon >= 0 ? outcome.substring(colon + 2) : outcome;
    }

    private Task finish(Long taskId, TaskStatus status, String result) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus(status);
        task.setResult(result);
        return taskRepository.save(task);
    }
}
