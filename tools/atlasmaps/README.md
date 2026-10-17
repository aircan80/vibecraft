# Atlas floor maps

`python3 -m pip install -r tools/atlasmaps/requirements.txt` then `python3 tools/atlasmaps/prepare.py OUT` downloads two pinned GitHub source archives, verifies their SHA-256, and converts seven original Atlas BLP maps to 1024px JPEG. No addon Lua executes. Only the reviewed Forever dataset is read, including the Classic Deadmines art appropriate to this client; retail Deadmines layouts are excluded.

The prepared manifest retains source paths and legend line numbers. Numbered and lettered legend entries retain Atlas order, and subordinate entries remain unnumbered. Atlas names remain independent of the app's encounter list. The importer links only a unique, exact NPC name; addon NPC IDs never establish canonical identity. No custom pins are projected on these images.

Run `java -cp importer/target/importer.jar dev.wowforever.importer.AtlasMapsWorkflow CANDIDATE.sqlite OUT` on a candidate copy. The workflow verifies every prepared artifact against the compiled pins, archives the unmodified ZIPs, original BLP/Lua/readme/GPL, and prepared derivatives, and adds only the map tables and additive provenance. The caller performs preservation checks, integrity/foreign-key checks, and atomic publication. Never point this helper at the running application's database.

Module: https://github.com/arithmandar/Atlas-Kalimdor-and-Eastern-Kingdoms/tree/19c74eae268360cabdab7269b161b4f08b7f7426

Core GPL: https://github.com/arithmandar/Atlas/tree/7787b5b2adfedf3e3e3c62838b81c0db6bd5d7ec

Map: Atlas (GPL-2.0) by Dan Gilbert, Lothaer, Arith Hsu and the Atlas Team. Forever maps were drawn for the Forever beta. Owner approval is recorded in product backlog decision 26; no courtesy email is sent.
