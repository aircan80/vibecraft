### Task 8: Map layers, objective pins, coordinates and /way on every map

**Files:**
- Modify `{main}ui/widgets/MapView.java`:
  - new `PinKind`s, `Pin.number`, layers, focus and per-kind visibility;
  - quest-page giver discs;
  - zone caption, cursor-coordinates corner label, right-click "Copy /way";
  - `Projection.toMap`.
- Modify `{main}ui/widgets/PinClusters.java` (clusters per layer and objective number), `{main}ui/widgets/PinGlyphs.java` (+ gold disc), `{main}ui/widgets/PinLegend.java` (+ interactive layer toggles), `{main}ui/pages/SpawnSection.java` and `{main}ui/pages/ZoneMapPanel.java` (zone names into their maps).
- Create `{main}ui/WayCommand.java`, which moves `SpawnSection.wayCommand` here.
- Tests: modify `{test}ui/widgets/MapViewTest.java` and `{test}ui/widgets/PinLegendTest.java`; create `{test}ui/WayCommandTest.java`.

**Interfaces:**
- Consumes: 2B `MapView`, `Pin`, `Cluster`, `ClipboardPort`.
- Produces:
  - `PinKind` gains `KILL, COLLECT, INTERACT, EXPLORE`, plus `Layer layer()` with `enum Layer { GIVERS, OBJECTIVES, SPAWNS }`. Hit-test and paint order is givers > objectives > spawns.
  - `Pin` gains `int number` (the 1-based objective number, 0 for none). `static Pin objective(EntityRef ref, String name, double x, double y, PinKind kind, int number)`. The existing constructors pass 0.
  - `MapView.setZoneName(String)` and `MapView.setClipboard(ClipboardPort)`.
  - `MapView.setFocus(Integer objectiveNumber)` (null = all). Other objectives' pins are hidden and giver pins are drawn at 50 % alpha.
  - `MapView.setKindVisible(PinKind, boolean)`.
  - `Optional<Point2D.Double> MapView.coordinatesAt(int x, int y)`, `String MapView.coordinatesText()` (shown in the corner, e.g. "45.1, 69.3"), and `JPopupMenu MapView.contextMenuAt(int x, int y)` ("Copy /way Elwynn Forest 45.1 69.3").
  - `Projection.toMap(double cx, double cy) -> Optional<Point2D.Double>`, empty outside the drawn image.
  - `WayCommand.of(String zoneName, double x, double y) -> String`.
  - `PinLegend.interactive(MapView map)`, whose entries become toggles for the kinds present.

- [ ] **Step 1: failing tests.**
  - `MapViewTest.cursorCoordinatesInvertTheProjection`: run at `UIScale` 1.0 and 1.5 on a 1002×668 image in components 640 and 1002 px wide. For 20 random map points, `coordinatesAt(toComponent(p))` equals `p` within 0.2.
  - `MapViewTest.pointerBelowTheImageHasNoCoordinates`: a component taller than the drawn height, with a pointer at y = drawnHeight + 5, gives empty coordinates, an empty corner label and a context menu without a "/way" item.
  - `MapViewTest.rightClickCopiesWay`: zone "Elwynn Forest", a click at map (42.1, 65.9) → the menu item text is "Copy /way Elwynn Forest 42.1 65.9". Activating it puts that command, without "Copy ", in a fake `ClipboardPort`.
  - `MapViewTest.objectivePinsClusterPerObjective`: two KILL pins of objective 1 within 8 px form one cluster of 2. A KILL pin of objective 2 at the same spot is its own cluster. A QUEST_START at the same spot is hit first.
  - `MapViewTest.focusDimsGiversAndHidesOtherObjectives`: after `setFocus(2)`, `visiblePins()` has no objective-1 pins and keeps the givers. The painted giver disc pixel has alpha ≈ 128.
  - `MapViewTest.objectiveShapes`: painting KILL n=3 shows `#E5705C` at the centre ring and the digit "3"; COLLECT shows a `#7CC46B` diamond (corner pixels transparent); INTERACT shows `#6FA8DC`; EXPLORE has a transparent centre. Every shape has a `#0C1424` outline pixel and a `#F4EBD0` halo pixel.
  - `MapViewTest.questPageGiverIsAGoldDisc`: an emphasised QUEST_START paints `#FFD100` at the disc edge and `#0C1424` in the "!" glyph, in both themes.
  - `MapViewTest.zoneCaptionShown`: `setZoneName("Westfall")` paints a caption; its accessible description reads "Westfall, 3 pins". This closes the backlog §3 item "spawn maps do not name the zone".
  - `PinLegendTest.togglesHideKinds`: clicking "Kill" in an interactive legend calls `setKindVisible(KILL, false)`; KILL pins leave `visiblePins()`.
  - `WayCommandTest`: `of("Westfall", 42.34, 67.45)` gives "/way Westfall 42.3 67.5", and a null zone gives "/way 42.3 67.5".
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. Coordinates use the same `Projection` as painting, divided by `UIScale` nowhere else.
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): objective pin layers, map coordinates and /way on every map`.

