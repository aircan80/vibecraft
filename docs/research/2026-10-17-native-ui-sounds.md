# Native interface sounds

Five short effects use exact Forever `wow_classic_beta` / `1.60.1.70450` SoundKitEntry assignments. Kit semantic labels were cross-checked against Blizzard's UI source mirror; no Era sound bytes are substituted. Reproducible table URLs, table SHA256, semantic-source SHA256, selected kit rows and entry rows are retained in `app/src/main/resources/data/ui-sounds/catalog.json`. Native content hashes and actual decoded PCM counts are in the adjacent proof JSON.

| Action | SoundKit | FileDataID |
| --- | ---: | ---: |
| Open window / navigate | 850 | 567490 |
| Close window | 851 | 567464 |
| Confirm action | 852 | 567481 |
| Select tab | 841 | 567422 |
| Toggle | 856 | 567407 |

`UiSoundService.shared().configure(product, build)` enables only the exact installed catalog identity and prewarms five assets on a bounded worker. `play(Cue)` never reads a file or waits for network/audio on Swing's EDT. Cold actions are silently dropped, global feedback is debounced at 120 ms, queued feedback expires after 200 ms, and only one audio voice plays. Asset and PCM size limits are 2 MiB. Music and interface effects have separate preferences and players; effects default to 22% volume. `wowforever.sound.enabled=false` disables all effect acquisition and playback for acceptance runs. Closing the service cancels acquisition and audio.

The existing VorbisSPI decoder returned zero PCM bytes for the three shortest valid native clips. `ShortVorbis` uses the already bundled JOrbis packet API to decode every audio packet, then writes a local WAV on the asset worker. All five actual native assets now decode to nonempty PCM; no new runtime external program or dependency is required. No original native file is modified. Native files remain in the authenticated content cache; WAVs are derived local data.

Integration: configure after catalog load, close during application shutdown, add `UiSoundPanel(service)` to settings, and call deliberate OPEN/CLOSE/CONFIRM/TAB/TOGGLE hooks. Do not attach feedback to typing, hover, live search, worker completion or every generic Swing property change. Do not play an action twice through both navigation and button listeners. Programmatic tab restoration must remain silent. Missing assets or audio output fail quietly.

Verification: four focused backend tests cover wrong-build refusal, cold-click behavior, off-EDT IO/playback, debounce, mute and volume bounds. All five actual root-verified files were downloaded and decoded using the packet decoder. Human listening and final GUI hook acceptance remain integration checks.
