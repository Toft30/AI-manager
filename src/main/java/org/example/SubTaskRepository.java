package org.example;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for å handtere SubTask-entitetar mot databasen.
 */
@Repository
public interface SubTaskRepository extends JpaRepository<SubTask, Long> {
}
