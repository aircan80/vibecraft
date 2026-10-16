package dev.wowforever.importer.source;

import java.nio.file.Path;

/** Local checkout roots of the two upstream sources, as resolved by {@link SourceFetcher}. */
public record SourceRoots(Path questieDb, Path atlasLoot) {}
