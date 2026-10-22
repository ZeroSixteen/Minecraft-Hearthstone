# Hearthstone (Fabric, Minecraft 26.2)

A WoW-style Hearthstone. Hold right-click to raise the stone and channel (10 seconds by default); if you keep holding, stay still and don't take damage, you teleport to your bed (or respawn anchor), across dimensions if needed. A cooldown (30 minutes by default) then starts.

## Setting your home

Right-click a bed. This works in the daytime too (vanilla sets your respawn point when you click a bed, even if you can't sleep yet), and sleeping in it works as well. The Hearthstone always returns you to your current respawn point.

## Config

On first launch the mod creates `config/hearthstone.json` in your instance folder (in Prism: instance, then the `minecraft` folder, then `config`). Edit it with any text editor; changes apply the next time you start a cast, no restart needed.

| Key | Default | Meaning |
| --- | --- | --- |
| `castSeconds` | 10.0 | How long the channel takes |
| `cooldownMinutes` | 30.0 | Cooldown after a successful hearth (0 = none, 0.5 = 30 seconds) |
| `moveToleranceBlocks` | 0.25 | How far you can drift before the cast is interrupted |
| `interruptOnMove` | true | false lets you move while channeling |
| `interruptOnDamage` | true | false keeps channeling when you get hurt |
| `showCastBar` | true | Blue cast bar at the top of the screen |

## Other details

- Interrupts: releasing right-click, moving, taking damage, switching slots
- Progress shows on a boss-style cast bar and in chat, not the action bar, so it doesn't clash with other mods that write there
- Hold-up pose: `getUseAnimation` in `HearthstoneItem.java` (SPYGLASS by default; BOW, TOOT_HORN, BRUSH etc. also work)
- Respawn anchors work too and are not drained
- Craft: stone in the corners, wool on the edges, ender pearl in the middle (`data/hearthstone/recipe/hearthstone.json`)
- Also in the Tools & Utilities creative tab, or `/give @s hearthstone:hearthstone`

## Easiest build: let GitHub do it (nothing to install)

1. Make a free account at github.com and create a new repository (private is fine).
2. Upload the project files, then add `.github/workflows/main.yml` with the contents of `.github/workflows/build.yml` from this folder.
3. Open the repo's **Actions** tab; the "Build Hearthstone" run starts by itself (about 2-3 minutes).
4. When it's green, click the run, scroll to **Artifacts**, and download `hearthstone-jar`. Unzip it to get `hearthstone-1.0.0.jar`.
5. In Prism, open your 26.2 instance, go to Mods, Add file, and pick that jar. Fabric API must be in the same instance.

## Manual build

Requires JDK 25 and Fabric API 0.152.x for 26.2 in your instance.

    gradle wrapper --gradle-version 9.5.1    # only needed once; or copy the wrapper from the Fabric template
    ./gradlew build

The jar is in `build/libs/hearthstone-1.0.0.jar` (not the `-sources` one). Drop it in your instance's `mods` folder next to Fabric API.

For a dev client: `./gradlew runClient`.
