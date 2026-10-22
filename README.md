# Hearthstone (Fabric, Minecraft 26.2)

A WoW-style Hearthstone. Hold right-click to raise the stone and channel for 10 seconds; if you keep holding, stay still and don't take damage, you teleport to your bed (or respawn anchor), across dimensions if needed. A 30-minute cooldown then starts.

- Cast time, cooldown and movement tolerance: `HearthConfig.java`
- Interrupts: releasing right-click, moving, taking damage, switching slots
- Hold-up pose: `getUseAnimation` in `HearthstoneItem.java` (SPYGLASS by default; BOW, TOOT_HORN, BRUSH etc. also work)
- Needs a valid, unobstructed bed (set by sleeping in it). Respawn anchors work too and are not drained.
- Craft: stone in the corners, wool on the edges, ender pearl in the middle (`data/hearthstone/recipe/hearthstone.json`)
- Also in the Tools & Utilities creative tab

## Easiest build: let GitHub do it (nothing to install)

1. Make a free account at github.com and create a new repository (private is fine).
2. Click "uploading an existing file" and drag in everything from this folder, including the hidden `.github` folder. (If your file explorer hides it, turn on "show hidden items".)
3. Commit. Open the repo's **Actions** tab; the "Build Hearthstone" run starts by itself (about 2-3 minutes).
4. When it's green, click the run, scroll to **Artifacts**, and download `hearthstone-jar`. Unzip it to get `hearthstone-1.0.0.jar`.
5. In Prism, open your 26.2 instance, go to Mods, Add file, and pick that jar. Fabric API must be in the same instance.

If the run fails, open it, copy the red error text, and send it to me.

## Manual build

Requires JDK 25 and Fabric API 0.152.x for 26.2 in your instance.

    gradle wrapper --gradle-version 9.5.1    # only needed once; or copy the wrapper from the Fabric template
    ./gradlew build

The jar is in `build/libs/hearthstone-1.0.0.jar` (not the `-sources` one). Drop it in your instance's `mods` folder next to Fabric API.

For a dev client: `./gradlew runClient`.
