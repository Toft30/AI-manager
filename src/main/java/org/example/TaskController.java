package org.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-kontroller for å teste AI Manageren.
 */
@RestController
public class TaskController {

    private final ManagerAgent managerAgent;

    public TaskController(ManagerAgent managerAgent) {
        this.managerAgent = managerAgent;
    }

    /**
     * Eit enkelt endepunkt for å sende inn ei oppgåve til manager-agenten.
     * Døme på bruk: GET /api/task?task=Schedule a meeting for tomorrow at 10 AM
     *
     * @param task Oppgåveskildring som skal sendast til manageren.
     * @return AI-modellen sitt svar.
     */
    @GetMapping("/api/task")
    public String delegateTask(@RequestParam(defaultValue = "Schedule a meeting for tomorrow at 10 AM") String task) {
        System.out.println("Tek imot oppgåve via HTTP: " + task);
        return managerAgent.delegateTask(task);
    }
}
