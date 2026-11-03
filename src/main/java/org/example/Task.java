package org.example;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ei oppgåve som er send til manager-agenten, lagra i databasen.
 */
@Entity
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @Column(length = 10000)
    private String result;

    private LocalDateTime createdAt;

    // EAGER så dashboardet får deloppgåvene med i JSON-svaret frå /api/tasks.
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<SubTask> subTasks = new ArrayList<>();

    protected Task() {
    }

    public Task(String description) {
        this.description = description;
        this.status = TaskStatus.MOTTATT;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<SubTask> getSubTasks() {
        return subTasks;
    }

    /**
     * Legg til ei deloppgåve. Ho blir lagra saman med oppgåva.
     * @param description Kva deloppgåva går ut på.
     * @return Den nye deloppgåva.
     */
    public SubTask addSubTask(String description) {
        SubTask subTask = new SubTask(description, this);
        subTasks.add(subTask);
        return subTask;
    }
}
