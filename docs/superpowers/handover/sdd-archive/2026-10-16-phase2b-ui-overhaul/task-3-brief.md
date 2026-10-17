### Task 3: Links behave like links; mouse navigation

**Files:** modify `ui/TableLinks.java` (single-click opens a link cell; `MouseMotionListener` tracks the hovered cell → `LinkRenderer` underlines + hand cursor; double-click still works), `ui/LinkRenderer.java`, create `ui/MouseNavigation.java` (`install(AppController, Window)`: `Toolkit.addAWTEventListener(MOUSE_EVENT_MASK)`, on `MOUSE_RELEASED` with `getButton()` 4 → back, 5 → forward, only when the event's window is the app window and the source is not a text component with its own binding), replace the back/forward glyph buttons with SVG icons (`arrow-left.svg`, `arrow-right.svg`), `MainFrame` wiring.
**Tests:** `TableLinksTest.singleClickOnLinkCellOpens`, `singleClickOnNonLinkCellDoesNothing`, `hoverTracksCell`; `MouseNavigationTest` (synthesised `MouseEvent`s with button 4/5 on a headless `JPanel` owned by a stub "window" → back/forward called; events on a `JTextField` ignored; events for another window ignored).
- [ ] Commit `feat(ui): single-click links, hover affordance and mouse back/forward`.

