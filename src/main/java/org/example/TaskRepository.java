package org.example;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for å handtere Task-entitetar mot databasen.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
}
