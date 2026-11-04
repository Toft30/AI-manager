package org.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testar for datamodellane Task og SubTask samt strukturert oppgåveplanlegging (TaskPlan).
 */
@DataJpaTest
class TaskDecompositionTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private SubTaskRepository subTaskRepository;

    @Test
    @DisplayName("Skal lagre og kople SubTask til Task med rette statusar")
    void testTaskAndSubTaskPersistence() {
        Task task = new Task("Planlegg eit prosjektmøte neste veke");
        SubTask subTask1 = task.addSubTask("Sjekk kalenderen for ledige tidspunkt");
        SubTask subTask2 = task.addSubTask("Opprett møte i Google Kalender");

        Task savedTask = taskRepository.save(task);

        assertThat(savedTask.getId()).isNotNull();
        assertThat(savedTask.getSubTasks()).hasSize(2);
        assertThat(savedTask.getStatus()).isEqualTo(TaskStatus.MOTTATT);

        List<SubTask> subTasks = subTaskRepository.findAll();
        assertThat(subTasks).hasSize(2);
        assertThat(subTasks.get(0).getDescription()).isEqualTo("Sjekk kalenderen for ledige tidspunkt");
        assertThat(subTasks.get(0).getStatus()).isEqualTo(TaskStatus.MOTTATT);
        assertThat(subTasks.get(0).getTask().getId()).isEqualTo(savedTask.getId());

        // Oppdater status på deloppgåve
        SubTask firstSub = subTasks.get(0);
        firstSub.setStatus(TaskStatus.FULLFØRT);
        firstSub.setResult("Fann ledig tid måndag kl 10:00");
        subTaskRepository.save(firstSub);

        SubTask updatedSub = subTaskRepository.findById(firstSub.getId()).orElseThrow();
        assertThat(updatedSub.getStatus()).isEqualTo(TaskStatus.FULLFØRT);
        assertThat(updatedSub.getResult()).isEqualTo("Fann ledig tid måndag kl 10:00");
    }

    @Test
    @DisplayName("Skal støtte strukturert TaskPlan med deloppgåver")
    void testTaskPlanRecord() {
        List<String> steps = List.of(
                "Sjekk tilgjengelegheit i kalenderen",
                "Send invitasjon til deltakarar",
                "Oppdater prosjektloggen"
        );
        TaskPlan plan = new TaskPlan(steps);

        assertThat(plan.subtasks()).containsExactlyElementsOf(steps);
        assertThat(plan.subtasks()).hasSize(3);
    }
}
