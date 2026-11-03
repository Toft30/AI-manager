package org.example;

import java.util.List;

/**
 * Representerer eit strukturert svar frå ManagerAgent etter nedbryting av ei oppgåve.
 */
public class TaskDecompositionResult {

    private List<String> subTasks;

    public TaskDecompositionResult() {
    }

    public TaskDecompositionResult(List<String> subTasks) {
        this.subTasks = subTasks;
    }

    public List<String> getSubTasks() {
        return subTasks;
    }

    public void setSubTasks(List<String> subTasks) {
        this.subTasks = subTasks;
    }
}
