# Owner input and external acceptance

Development continues while these are pending. None requires stopping independent work.

- **Real Forever collector capture:** when a compatible Forever client is available, run the existing collector and import a genuine capture. Synthetic tests do not replace this acceptance.
- **Experimental appearance capture:** `tools/collector/WowForeverAppearanceCollector/` is a separate, opt-in prototype for build 1.60.1.70450. A real target/model API check is needed before promoting observations into the shipped appearance catalog. Observed appearances may be phase/transform specific; they are not canonical defaults.
- **NPC appearance priority:** a genuine Forever observation for NPC246, “Auntie” Bernice Stonefield, would close her missing NPC→display link. The installed exact-build Creature catalog has no entry for her. This is separate from humanoid portrait-composition work, which continues on already-mapped NPCs. Do not substitute an Era model or treat a phase-specific observation as a universal appearance.
- **Interface sound listening:** exact native clips, packaged decoding, actual output-device playback and independent settings are verified. Optional owner feedback can confirm comfortable22% default volume and preferred navigation/tab cues; coding does not wait.
- **City music listening:** automated checks verify exact-build assets, decoding and an available output device. Owner feedback can confirm audible playback, mute/volume and stop-on-leave behavior in their desktop session.
- **Design preferences:** review `http://127.0.0.1:8766/recommendations.html`, with city/equipment mockup links. Preferences can steer upcoming slices; current implementation does not wait for optional feedback.

Current data gaps remain explicitly unknown. Classic Era records are never used to silently fill Forever gaps.
