package org.example;

public class ManagerAgent {

    public ManagerAgent() {
        // Her kan ein setje opp LangChain4j ChatLanguageModel, minne, etc.
        System.out.println("ManagerAgent initialized.");
    }

    public void delegateTask(String taskDescription) {
        System.out.println("ManagerAgent received task: " + taskDescription);
        // Her kan AI-manageren analysere oppgåva og bestemme kva agent som skal ta seg av den.
        // Til dømes, viss oppgåva handlar om kalenderen, kan ManagerAgent kontakte ein CalendarAgent.

        System.out.println("ManagerAgent is analyzing and delegating the task...");
    }
}
