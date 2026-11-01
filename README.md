# BotMod (Fabric 26.2)

Server-side combat bots. Bots are real server-side players with no network connection, so they cost
CPU only (no client, no bandwidth). Behaviours: heal, retreat-to-heal, shield, totem, shield-breaking
with an axe, potions (healing / strength / speed / fire res), and formations.

## Build
Requires **JDK 25**.
1. Copy `gradlew`, `gradlew.bat` and the `gradle/wrapper` folder from the official Fabric example mod
   (https://github.com/FabricMC/fabric-example-mod) into this folder (or run `gradle wrapper`).
2. `./gradlew build`  ->  jar in `build/libs/botmod-1.0.0.jar`

## Install
Put the jar plus **Fabric API** into the `mods` folder of a 26.2 Fabric **server** (or single-player world).

## Commands (op level 2)
| Command | What it does |
|---|---|
| `/bots spawn <count> [prefix]` | Queue bots at your position (joins a few per tick) |
| `/bots kit` | Give all bots a netherite PvP kit (sword, axe, shield, totems, gapples, potions) |
| `/bots formation <line/column/wedge/circle/box/spread> [spacing]` | Set formation |
| `/bots follow` | Formation follows you (position + facing) |
| `/bots target <player>` / `/bots target none` | Attack a specific player |
| `/bots aggro on/off` | Attack nearby hostile mobs when no player target |
| `/bots hold on/off` | on = stay in formation, don't chase |
| `/bots hp <retreat> <resume>` | HP (half-hearts) to flee/heal at and to resume fighting at |
| `/bots budget <n>` | Max bot brains per tick (lower = less lag) |
| `/bots stats`, `/bots remove` | Info / remove all |

## Scaling to 10k
Vanilla's server loop ticks every player, tracks entities and sends chunks per player, so ~10k full
players on one JVM will not hold 20 TPS on typical hardware. To get as close as possible:
- `server.properties`: `max-players=20000`, `view-distance=2`, `simulation-distance=2`, `network-compression-threshold=-1`
- Use lots of RAM (`-Xmx16G`+), Aikar-style GC flags, and a fast CPU (single-thread speed matters most).
- Spawn in waves (`/bots spawn 500`, wait, repeat) and lower `/bots budget` if TPS drops.
- Spread bots out so they don't all share one chunk area.

## Known API friction (could not compile-test against 26.2 when this was written)
If the build errors, these are the most likely spots; fix by checking names with `javap` on the
unobfuscated 26.2 jar in the Loom cache:
- `BotCommands`: command permission check (`Commands.hasPermission(...)`)
- `BotPlayer`/`BotManager`: `ServerPlayer` constructor, `placeNewPlayer`, `CommonListenerCookie.createInitial`
- `BotBrain`: `Inventory.getSelectedSlot/setSelectedSlot`, `PotionContents.is(...)`, `Potions.*` holders,
  `gameMode.useItem(...)`, `bot.attack(...)`
