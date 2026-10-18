# Source disclosure — Vibecraft mockup revision

Design proposal only; this does not claim the Java release has changed.

Use one quiet outlined ⓘ button beside a record or section heading. Share it across rows with the same provenance; add row or field controls only when their scope differs. Hover and keyboard focus reveal a short provider/edition summary. The button has an accessible name and linked description. Click, Enter or Space opens a persistent Sources dialog; native dialog keyboard behavior contains focus. Escape, Close and an outside click dismiss it and return focus to the source button.

The consistent details layout is: source, edition, build, applies-to scope, refreshed time, readable interpretation, then collapsed field history and technical evidence. Each provider keeps its own build/snapshot; never imply an addon was produced from the client build. Missing timestamps and hashes remain explicitly unavailable in these illustrative prototypes.

Verified provenance stays visually quiet. Unknown fields, hidden values, reported drops, conflicts and explicit Classic comparisons remain visible in the content. They must not disappear into the source icon. A future conflict indicator needs visible text as well as color.

Implemented in Explorer, cities, equipment research and recommendations mockups. The city guide's separate provenance card and equipment's large source explanation block are replaced by heading icons. Existing general source drawers remain available in historical Explorer code but current source controls use the shared dialog design.

Review: Firefox headless screenshots at 1440px dark and 420px light show a bounded readable dialog. Firefox WebDriver BiDi checks across all four pages confirm source controls, linked accessible descriptions, opening, initial focus, keyboard Tab to technical disclosure, disclosure click, Escape/focus restoration and outside-click dismissal (36 assertions). The offscreen headless browser does not activate CSS `:focus-within` as a foreground window; hover/focus styling is specified in CSS and supplemented with focus event state. No human hover/Enter/Space interaction is claimed.

## Map hierarchy follow-up

The Explorer map mockup now demonstrates `World → Kalimdor ▾ → Desolace ▾`, with a separate direct navigation label and sibling-picker arrow. World shows Kalimdor, Eastern Kingdoms and Zephras Isle together. The last zone arrow still offers sibling zones; a selected subzone extends the path to the lowest illustrated record. Current menu entries are checked and marked with `aria-current`; labels describe navigation and arrows describe selection.

Picker buttons support native keyboard activation. Opening focuses the current sibling; arrow keys, Home and End move among choices. Escape returns focus to its arrow, Tab dismisses, and outside click closes. Whole crumb segments wrap at 420px. History retains continent and zone context with the selected place.

Desolace uses an explicitly labelled artwork placeholder rather than mislabelling the available Westfall image. Its dedicated source icon describes illustrative hierarchy, not an archived map or verified region catalogue. Names and sibling choices are interaction examples, including the requested Zephras Isle; production must populate them from qualified game records.

Review routes: `index.html?screen=maps&menu=continent`, `index.html?screen=maps&menu=zone&theme=light`, `index.html?screen=maps&place=World`, and `index.html?screen=maps&place=Westfall` for the preserved archived map example. Fifteen Firefox BiDi checks passed for breadcrumb/menu selection, current state, ArrowDown, Escape and focus restoration, up-navigation, World destinations, Zephras selection, lowest subzone path, outside dismissal and independent source details. Wide dark, 420px dark and 420px light captures were produced outside the repository.
