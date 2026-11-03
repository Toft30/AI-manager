package org.example;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for lagring og henting av oppgåver.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {
}
