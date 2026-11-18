package org.example;

import java.util.List;

/**
 * Representerer ein strukturert plan med deloppgåver frå PlannerAgent.
 *
 * <p>
 * Denne record-klassen blir brukt for å ta imot strukturert output frå språkmodellen
 * når ei hovudoppgåve blir broten ned i mindre deloppgåver.
 * </p>
 *
 * @param subtasks Liste over konkrete deloppgåver som skal utførast.
 */
public record TaskPlan(List<String> subtasks) {
}
