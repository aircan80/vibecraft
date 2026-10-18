# Pinned research references

Explorer Journal now offers **Pinned research** in its sidebar. On a loaded entity, geographic map, item-research catalog, Forever catalog, or unfiltered entity catalog, press **Ctrl+D** to save or remove the current reference. Open a saved reference with **Enter** or the Open button; remove it with **Delete** or Remove. Empty lists explain how to begin.

References retain the actual `ViewTarget` identifiers and the page's displayed label, rather than copying entity data. Opening uses ordinary controller navigation, including Back/Forward and the existing missing-entity page. Stored references persist across restart through the existing Journal `UiState.pinned` preference. The existing 50-reference and preference byte limits still apply, oldest first. Labels are bounded to 160 characters and Unicode is preserved.

The versioned reference key contains the active Forever database build ID. Pinning remains unavailable when the database has no build identity. Foreign-build, historical unscoped, corrupt, and unknown serialized references are ignored. The forthcoming catalog-session foundation can provide its full edition/projection/build/locale identity to `MainView.configureResearchScope`; this slice does not enable another game or relabel its data. References are personal research shortcuts, separate from character progress and wishlists.

Searches and filtered entity lists are not saved yet: silently restoring an unfiltered list would lose their meaning. Home and Pinned research cannot pin themselves. Pinning is disabled from navigation start until a page commits, and while replacing its detail component; no old target can inherit the next page's loading label.

Validation: all application sources compiled with Java 21 in an isolated directory. Six focused JUnit tests passed: restart/Unicode/full target identity; duplicate toggling and scope isolation; bounds/corruption; unsupported transient views; Enter/Delete behavior; and a real fixture-database controller journey opening an absent NPC, then Back/Forward through the references page without resetting history. Full integrated Maven verification and native Ctrl+D acceptance remain root integration checks.

This is the bounded D17 reference-list slice. D19 recent history, richer Home personalization, and item comparison remain separate roadmap work.
