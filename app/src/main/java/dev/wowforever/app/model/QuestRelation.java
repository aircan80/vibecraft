package dev.wowforever.app.model;

/**
 * One {@code quest_relation} row: quest {@code questId} relates to {@code relatedId} as {@code kind}
 * ({@code pre_single}, {@code pre_group}, {@code next}, {@code child}, {@code parent}, {@code exclusive},
 * {@code group}, {@code breadcrumb_for} or {@code breadcrumb}). {@code relatedId} may name a quest the database does
 * not have.
 */
public record QuestRelation(long questId, long relatedId, String kind) {}
