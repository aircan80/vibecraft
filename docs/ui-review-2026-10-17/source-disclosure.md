# Source disclosure — Vibecraft mockup revision

Design proposal only; this does not claim the Java release has changed.

Use one quiet outlined ⓘ button beside a record or section heading. Share it across rows with the same provenance; add row or field controls only when their scope differs. Hover and keyboard focus reveal a short provider/edition summary. The button has an accessible name and linked description. Click, Enter or Space opens a persistent Sources dialog; native dialog keyboard behavior contains focus. Escape, Close and an outside click dismiss it and return focus to the source button.

The consistent details layout is: source, edition, build, applies-to scope, refreshed time, readable interpretation, then collapsed field history and technical evidence. Each provider keeps its own build/snapshot; never imply an addon was produced from the client build. Missing timestamps and hashes remain explicitly unavailable in these illustrative prototypes.

Verified provenance stays visually quiet. Unknown fields, hidden values, reported drops, conflicts and explicit Classic comparisons remain visible in the content. They must not disappear into the source icon. A future conflict indicator needs visible text as well as color.

Implemented in Explorer, cities, equipment research and recommendations mockups. The city guide's separate provenance card and equipment's large source explanation block are replaced by heading icons. Existing general source drawers remain available in historical Explorer code but current source controls use the shared dialog design.

Review: Firefox headless screenshots at 1440px dark and 420px light show a bounded readable dialog. Firefox WebDriver BiDi checks across all four pages confirm source controls, linked accessible descriptions, opening, initial focus, keyboard Tab to technical disclosure, disclosure click, Escape/focus restoration and outside-click dismissal (36 assertions). The offscreen headless browser does not activate CSS `:focus-within` as a foreground window; hover/focus styling is specified in CSS and supplemented with focus event state. No human hover/Enter/Space interaction is claimed.
