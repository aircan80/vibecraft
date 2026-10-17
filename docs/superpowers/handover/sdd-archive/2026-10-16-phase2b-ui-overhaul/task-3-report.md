# Task 3 report

Implemented: TableLinks single click on link cells (opens that cell's own entity via a new 3-arg bindActivation; LinkTable uses it), second click of a double-click ignored, double-click on plain cells and Enter still open the row's first link, hover tracking (Cell record, hoveredCell(table)), hand cursor, mouse exit clears; LinkRenderer underlines the hovered link cell. MouseNavigation (install/uninstall, handle, Predicate seam via package-private create(back, forward, predicate)). Back/forward are icon buttons (arrow-left/right.svg, accessible names, tooltips) via MainFrame.historyButton; MouseNavigation installed in attach().

TDD: tests written first (TableLinksTest, MouseNavigationTest, MainFrameContentTest addition); full `-pl app -am test` and `verify` green.

Notes: headless MouseEvent constructor rejects buttons > 3, so tests override getButton(). The seam takes Runnables rather than AppController (final class, no mockito); install() still takes AppController as decided. Files: TableLinks, LinkRenderer, MouseNavigation, MainFrame, detail/LinkTable, two SVGs, three test classes.
