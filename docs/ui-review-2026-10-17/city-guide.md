# City guide design

Reviewable responsive prototype: [cities.html](mockups/cities.html). Open directly in a browser; no server or build is required. It reuses the existing mockup’s exact serif, green-charcoal/ivory palettes, spacing and navigation shell. Theme switch, tabs and explanatory controls work. No audio is bundled or simulated as playing.

## Composition

The guide should feel like arriving somewhere, rather than opening another zone table. A wide editorial hero introduces the city with its native breadcrumb, large serif name and source-qualified identity. Its adjacent soundscape card makes the new music feature visible without pushing geography below the fold. Keep title and controls readable; do not replace map art with invented city illustrations.

Overview pairs an expansive district directory with a narrower travel/people column. A record opens the actual district or exact entity. The existing map and inspector remain intact under **Map & records**. Dedicated Districts and Travel tabs use the same data as Overview, rather than maintaining divergent copies. Services & people can expose only genuinely city-linked NPC/vendor/quest records; available coverage may be legacy-scoped and needs that qualification. Do not infer a bank, auction house or trainer from an evocative district name.

For narrow windows, hero and music control stack, the overview becomes one column, and tabs stay horizontally reachable. Keep primary music actions at least 40px high. Navigating back must restore tab, selected district/entity and map state.

## Soundscape behavior

On entering a supported city, resolve and play its local source-backed city music when music is enabled. A persistent mute preference must survive navigation; entering the city must not override it. Leaving or changing cities should stop/replace the prior track so multiple tracks never overlap. Reopening the same city should not restart a track needlessly.

Show clear state text: **Playing**, **Paused**, **Muted**, **Loading**, or **Music unavailable**. Offer Pause/Resume, Mute and volume. Label the actual track when the asset has a trustworthy name; otherwise show “City music” with its source in details. A ZoneMusic ID is a linkage, not proof a playable audio asset is present. Never show a fabricated progress indicator or claim playback succeeded from metadata alone. Loading/decoding runs away from the Swing event thread.

The prototype intentionally displays unavailable audio. Its disclosure explains the eventual controls; browser clicks do not emit fake success.

## Sample-data boundaries

Directly checked the bundled native archive, AreaTable: city **Stormwind City, 1519**, child **Valley of Heroes, 1617**, and **Stormwind Harbor, 17203**. City record links ZoneMusic **13**. This is partial archived geography, not a complete familiar-game district list. Snapshot-specific source rows must remain available in production. Prototype travel/services copy describes coverage behavior and asserts no specific route, NPC, vendor count or quest.

## Acceptance

- Desktop city shows its identity, music state, first recorded districts and travel entry without an expanse of empty framing.
- Hero typography and surfaces match the agreed journal mockups rather than default bold Swing labels.
- Every visible directory result has an actual record/navigation target; missing map art has an explicit fallback.
- Services, quest and travel lists retain their own data scopes; no placeholder fixtures ship as real records.
- Compact shell remains navigable, tabs reachable, and no horizontal page scrollbar appears.
- Playback state reflects the audio engine, mute persists, and city changes do not overlap tracks.

Implementation coordination: `JournalCityPage` can wrap the existing `JournalZonePage` and preserve its map/inspector/records behavior. Native geography belongs in an off-EDT immutable guide snapshot; presentation should not query the archive in tab listeners.
