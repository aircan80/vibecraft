# Native city music

`build_city_music.py` retains the exact Forever 1.60.1.70450 source exports and builds `/data/city-music/catalog.json`. Outdoor capitals use `AreaTable.ZoneMusic`; Ironforge and Undercity use their explicit `WMOAreaTable` default-group assignments. All day/night SoundKitEntry variants, frequencies, conditions and volumes remain in the catalog. No Era music is substituted. Original CSV bytes are retained as reproducible gzip resources with SHA256 provenance.

The Java service downloads a selected track through `tools/npcmodels/native_asset_worker.py`, which verifies exact Blizzard build root membership and content MD5 before returning bytes. Asset downloads, decoding and playback use dedicated bounded workers. Navigation invalidates old playback, leaving stops it, repeated requests deduplicate, and new playback waits for the previous stop. The selected day/night playlist cycles as an app soundtrack preview; it does not emulate the client's randomized silence intervals. Mute and volume persist in preferences.

Java Sound MP3/Vorbis providers are pinned to `com.googlecode.soundlibs:mp3spi:1.9.5.4` and `vorbisspi:1.0.3.3` from Maven Central, with JLayer, JOrbis and Tritonus dependencies. The shaded JAR merges their service files. Their parent artifact declares LGPL 2.1; its license is included under `licenses/LGPL-2.1-soundlibs.txt`. Upstream source/package project: https://github.com/pdudits/soundlibs.

Configuration:

- `wowforever.music.enabled=false` disables acquisition/playback for automated acceptance; default true.
- `wowforever.music.cache` controls small result/log cache files.
- `wowforever.nativeAssets.cache` or `NPC_PREVIEW_ASSET_CACHE` controls the shared content-addressed raw asset cache.
- `wowforever.music.worker` / `wowforever.music.python` configure the native acquisition helper.
- `WOWFOREVER_CASC_METADATA` or `NPC_PREVIEW_METADATA` may point at metadata seeds; native hashes are still checked.

Generate catalog:

```bash
python3 tools/music/build_city_music.py --database app/src/main/resources/data/wowforever.sqlite \
  --source .local/city-music-acquisition --output app/src/main/resources/data/city-music
```

Smoke test the actual packaged Java Sound providers without a speaker device:

```bash
javac -d /tmp/city-music-check tools/acceptance/CityMusicDecodeSmoke.java
java -cp /tmp/city-music-check:/absolute/path/to/review.jar CityMusicDecodeSmoke /absolute/path/to/verified/track.bin
```
