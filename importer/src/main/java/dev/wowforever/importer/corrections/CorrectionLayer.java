package dev.wowforever.importer.corrections;

import dev.wowforever.importer.entity.EntityKind;

/**
 * One correction file applied to one entity kind.
 *
 * @param name         short layer name used in reports, e.g. {@code legacy/classicItemFixes}
 * @param relativePath the file, relative to the QuestieDB root
 * @param function     the qualified name of the function returning the correction table
 * @param kind         the entity kind the corrections address
 */
public record CorrectionLayer(String name, String relativePath, String function, EntityKind kind) {}
