# 3DCasino

[English](README.md) | [简体中文](README.zh-CN.md)

Twelve physical Casino games for Minecraft, with animated machines, private player controls and native Dialog menus.

**3DCasino 1.0.0** uses one JAR on Java 25 across these tested servers.

| Server | Tested Minecraft versions |
| --- | --- |
| Paper | 26.1.1, 26.1.2, 26.2, 26.3 |
| Purpur | 26.1.2, 26.2, 26.3 |
| Spigot / CraftBukkit | 26.1, 26.1.1, 26.1.2, 26.2, 26.3 |
| Folia | 26.1.2, 26.2, 26.3 |

Each combination was tested with all twelve machines, all four placement directions, Dialog input and a server restart. Folia checks also moved the player between machines in separate regions and verified that payments ran on the thread responsible for that player.

## Install and play

1. Download the plugin JAR and optional resource pack from the [1.0.0 release](https://github.com/GoldenEggOVO/3DCasino/releases/tag/v1.0.0).
2. Stop the server and back up its plugin data before replacing a JAR.
3. Put one shaded `3DCasino-1.0.0.jar` in `plugins/`; do not install `original-*.jar`.
4. Start the server and run `/3dcasino`. An administrator can place a machine from the game menu or `/3dcasino create <game>`.

Players operate buttons and controls on the machine. Sneak and right-click a machine to open its settings. Owners and administrators may remove idle machines. Each player has their own stake and preferences; a machine's placement does not assign one shared stake to everybody.

3DCasino runs independently without client mods or an extra Core plugin. CraftEngine and a resource pack are optional; Vault plus an enabled economy provider is needed only for positive stakes. Without the resource pack, machines use native Display models. Loaded resource packs provide matching machine artwork. Menus use native Dialog text buttons. The resource-pack switch is in **Settings**. On subpages, click the yellow **[Back]** link below the description to return one level. Press Esc to close the menu.


## Games and commands

| Game | ID |
| --- | --- |
| Plinko | `plinko` |
| Mines | `mines` |
| Blackjack | `blackjack` |
| Crash | `crash` |
| Slots | `slots` |
| Duck Race | `duck_race` |
| Wheel of Fortune | `wheel_of_fortune` |
| Money Wheel | `money_wheel` |
| Penguin Cross | `penguin_cross` |
| Keno | `keno` |
| HiLo | `hilo` |
| Dragon Tower | `dragon_tower` |

| Player command | Purpose |
| --- | --- |
| `/3dcasino [menu]` | Open the game catalog |
| `/3dcasino settings` | Open settings, including the resource-pack switch |
| `/3dcasino create <game> [skin-id]` | Place a machine; administrator only |
| `/3dcasino remove [game]` | Remove your idle machine; administrators can manage nearby machines |
| `/3dcasino reload-models` | Reload custom machine definitions; administrator only |
| `/3dcasino reload` | Reload configuration and translations; administrator only |
| `/3dcasino reload-pack` | Rebuild editable resources; administrator only |

`3dcasino.use` defaults to everyone; `3dcasino.admin` defaults to operators. Commands require a player. Rendering-mode, pack-source and integration changes need a restart. This plugin has no PlaceholderAPI expansion.

## Configuration and data

Settings are in `plugins/3DCasino/config.yml`:

| Key | Default | Purpose |
| --- | --- | --- |
| `language` | `en_US` | Generated language catalog in `languages/`; `zh_CN` is included |
| `menu-enabled` | `true` | Enable Dialog menus |
| `rendering.mode` | `auto` | Enabled CraftEngine selects `mixed`, otherwise `vanilla`; explicit `vanilla`, `mixed`, `resource-pack` also work |
| `rendering.resource-pack.source` | `craftengine` | Unified CraftEngine delivery; alternatives: `local`, `external` |
| `rendering.resource-pack.url` | Empty | URL of the ZIP matching this JAR for external delivery |
| `rendering.resource-pack.local.bind-address` | `0.0.0.0` | Standalone HTTP listener |
| `rendering.resource-pack.local.port` | `8138` | Standalone HTTP port |
| `rendering.resource-pack.local.public-url` | Empty | Optional public base URL; empty uses the player's connection hostname |
| `economy.enabled` | `false` | Enable positive stakes through Vault |
| `economy.stakes.minimum` / `maximum` | `1.0` / `1000.0` | Limits for positive total stakes, including Blackjack doubles |

New personal stakes start at **0**. With an available economy provider, players choose an amount from machine settings. Each accepted round or Plinko ball is charged before play advances. Returns include the stake and round down to currency precision. Disabling economy resets positive personal stakes to zero; players explicitly choose a positive amount after re-enabling it.

Leaving, death, world changes, unloads and restarts forfeit unfinished rounds. Captured cashouts and Plinko outcomes are recorded before animation completes. Known rejected payments retry periodically. Vault has no idempotency key: uncertain transactions are retained for administrator review and are not automatically repeated. Do not edit or discard payment records while the server runs.

| Path under `plugins/3DCasino/` | Contents |
| --- | --- |
| `casino/placements.json` | Machine placements and saved definitions |
| `casino/players/` | Personal machine preferences and settlement summaries |
| `casino/payments.json` | Durable payment journal |
| `casino/machines/` | Optional custom YAML machine definitions |
| `languages/` | Player-facing translations |
| `resource-pack/`, `resource-pack.zip` | Editable pack inputs and generated plugin pack |

Edit generated translations without changing keys or `{placeholders}`, then run `/3dcasino reload`. Missing standard English/Chinese entries use their bundled translations; other locales fall back to English. A successful reload closes open Casino menus for all players. Open a new menu to see the selected language. If a translation fails validation, existing menus and translations stay active.

## Resource maintenance

The namespace is `3dcasino`. Default CraftEngine registrations are installed from the JAR into `plugins/CraftEngine/resources/3dcasino/`, preserving existing entries and adding missing defaults. Casino and Tabletop have separate resources; shared card artwork is self-contained in each distribution. CraftEngine delivery uses its complete unified pack and existing host.

For routine texture changes, edit the generated server `resource-pack/assets/3dcasino/` files and run `/3dcasino reload-pack`. On startup, the plugin upgrades unmodified stock models and stock pack metadata from the 26.2-only pack. Custom files retain their contents. Invalid edits retain the last usable archive. Wait for rebuild completion before another CraftEngine generation operation. Machines use native geometry until the player has loaded the pack and the models are ready. Each plugin removes only its own resource-pack request.

Local and external packs use the item models shipped in the JAR. To use them without CraftEngine, set `rendering.mode` to `mixed` or `resource-pack`. For standalone `source: local`, expose port 8138 or reverse-proxy `/3dcasino/`. For `source: external`, publish the ZIP matching the JAR and configure its URL; the checksum is bundled. Restart after changing source or listener settings.

Source assets live under `resource-pack/assets/3dcasino/`: models in `models/item/`, definitions in `items/`, and images in `textures/`. Casino-specific files use `casino/<game>/`; immutable shared cards use `shared/playing_cards/`. Default registrations are under `resource-pack/craftengine/`. `originals/`, `licenses/` and `sources.json` retain source artwork and attribution; originals are excluded from the client ZIP. Native geometry is in `src/main/resources/vanilla-models/casino/`, with its index in `vanilla-models/index.json`. Changing signed native card artwork also requires regenerating textures and compiling; ordinary pack reloads do not replace head skins.

## Offline switch from integrated Casino data

Validate a full backup locally before using this build on a server. No live deployment or data migration is performed by the plugin.

1. Stop the server cleanly and back up worlds, `plugins/3DGames/`, `plugins/3DCasino/` if present, and custom CraftEngine/resources.
2. Copy the **complete** `plugins/3DGames/casino/` directory to `plugins/3DCasino/casino/`. Do not overwrite an existing Casino dataset without first resolving which copy is authoritative.
3. In copied placement/custom definitions, manually change only Casino-owned `3dgames:` resource references within `definition.models` and `parts` to their matching `3dcasino:` references. Preserve third-party namespaces. Keep payment records, amounts, transaction state, UUIDs, ownership and coordinates unchanged.
4. Update custom Casino CraftEngine registrations, material/model paths and resource-pack URLs to the new namespace. Default registrations come from each JAR. Use separate configurations and resource folders for both plugins.
5. Test restored placements, private preferences, unsettled payments, both commands, resource switching and each plugin’s occupancy on the backup copy before the server switch. Retain the old data and backup; do not run two plugins against one journal or delete the original directory.

## Source and build

```text
3DCasino/
├── src/             Java, defaults, translations, native models and tests
├── resource-pack/   Client assets, CraftEngine registrations and source artwork
├── tools/           Packaging, native-head tools and checks
├── pom.xml          Maven configuration
└── LICENSE          Code license
```

Java is under `src/main/java/GoldenEggOVO/TDCasino/`. `TDCasinoPlugin.java` wires the plugin; `game/` contains the twelve rules and machines, `machine/` owns sessions/definitions/persistence, `render/` handles geometry and audiences, `menu/` owns Dialog pages, `economy/` contains the payment journal, and `Core/platform/` isolates server-specific scheduling, messages and native dialogs. `Core/pack/` manages pack delivery, and `Core/render/` tracks viewers and display movement. `command/`, `integration/`, `interaction/` and `text/` handle their corresponding boundaries. Tests mirror this layout; no external Core service is required.

Use JDK 25, Maven 3.9+ and Python 3.12+:

```sh
mvn -B -ntp clean
python tools/package_resource_pack.py
mvn -B -ntp package
python -m unittest discover -s tools/tests -t . -p "test_*.py"
python tools/package_source.py
```

Build the pack before Maven so the JAR contains the matching ZIP and checksum. Outputs are `target/3DCasino-1.0.0.jar`, `target/3DCasino-resource-pack.zip`, its manifest and `target/3DCasino-source.zip`. Java results are in `target/surefire-reports/`. Head-artwork tools may need Pillow; runtime needs no MineSkin key. Automated tests do not establish client visual, sound or FPS acceptance.

For compatibility checks with local server builds, use `python tools/tests/server/run_compat_matrix.py --help`. The two-player resource-pack test is `tools/tests/server/pack_probe/run_pack_layers.py`. Both use temporary worlds on loopback. The model parser in `tools/tests/client/CasinoModelProbe.java` runs with the official client libraries for the version being checked.

## License and attribution

Plugin code is [GPL-3.0-or-later](LICENSE). Casino rules, geometry and controllers adapt GoldenEggOVO/3DCasinoGames 0.6.0-beta.5-SNAPSHOT; text utilities derive from its `a4ddd20` source under GPL-3.0. Corresponding source and license notices accompany the distribution. Gson is bundled and relocated under **Apache-2.0**. Adventure and Kyori Option are bundled under MIT for servers without Adventure. JOML comes from the server; Vault's API is optional and not bundled. Tabletop's chess, Xiangqi, Gomoku and Chinese Checkers libraries are not used by this Casino plugin.

Resource-pack faces and the blue card back use George Blackwell's [Pixel Playing Cards Pack](https://georgeblackwell.itch.io/playing-cards-sprite-pack), **[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)**. Original 60×84 images are under `resource-pack/originals/shared/textures/playing-cards/`; derived faces keep the aspect ratio, use nearest-neighbor enlargement and map onto machine card geometry. Keep author credit, license links and modification notices with distributions.

Native card artwork includes Minecraft 26.2 zombie, skeleton and creeper face pixels from the client; these textures belong to **Mojang/Microsoft**, separately from the GPL code. Card layout, suits and Wither-style pixel artwork are project work. Signed skin properties are bundled; API keys and generation caches are excluded. Source details, checksums and asset notices are retained with the resource sources and in `resource-pack/licenses/`.

The card-dealing sound uses an edited mono sample from Kenney’s [Casino Audio 1.1](https://kenney.nl/assets/casino-audio), licensed under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/). The original OGG, checksum and license are kept with the sources. Players who load the pack hear `3dcasino:cards.deal`; other players hear the vanilla page-turn sound.
