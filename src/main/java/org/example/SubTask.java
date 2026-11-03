package org.example;

import jakarta.persistence.*;

/**
 * Representerer ei deloppgåve for agent-systemet i databasen.
 */
@Entity
public class SubTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;

    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Task task;

    public SubTask() {
    }

    public SubTask(String description, String status, Task task) {
        this.description = description;
        this.status = status;
        this.task = task;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }
}
