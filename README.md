# SimpleFactions

The name stems from its original concept of a simple nation system, but it has since grown far beyond that.

This plugin adds factions (nations) to the game that can interact with each other through a complex diplomacy and economy system.

**Full docs:** [docs/README.md](docs/README.md) - wars, installations, settlements, map export, vehicles, and dev config.

You will not be able to run this as a standalone program, as it depends on other plugins and the Spigot server environment.

## Why This Project Is Interesting
This plugin implements strategy-game-style diplomacy between nations and a fully original map/border system using a REST connection to a Python program I wrote myself:  
[ProvinceSystem](https://github.com/Drefvelin/ProvinceSystem)

Highlights include:

- **Nested Relationships** -- Factions can have subjects, and those subjects can have their own subjects.
- **Tax Simulation** -- When a player (or faction) earns money, their faction (or overlord) can automatically tax their income.

I drew heavy inspiration from strategy games by Paradox Interactive and leveraged my experience with object-oriented programming to implement parts of their systems. The project contains many interconnected classes that must remain consistent to avoid desynchronization.

This project also demonstrates cross-language integration through **ProvinceSystem**, requiring careful planning to keep server state synchronized between the Java plugin and the Python backend.

## Features
- Faction objects that function as nations in-game  
  ([Faction.java](src/main/java/me/Plugins/SimpleFactions/Objects/Faction.java))
- Faction relationships  
  ([RelationManager.java](src/main/java/me/Plugins/SimpleFactions/Managers/RelationManager.java))
- **Automated campaign wars**, installations (forts/ports/airports), settlements, and map export to tfminecraft.net  
  ([docs/wars.md](docs/wars.md), [docs/installations.md](docs/installations.md), [docs/map-export.md](docs/map-export.md))
- Titles (Kingdom, Duchy, etc.) connected with the REST server and the TitleManager  
  ([ProvinceSystem](https://github.com/Drefvelin/ProvinceSystem),  
  [TitleManager.java](src/main/java/me/Plugins/SimpleFactions/Managers/TitleManager.java))

## Technical Overview
- Java 17, Spigot API 1.20  
- Built using Maven

### Architecture
- The main class initializes managers and overall plugin setup  
  ([SimpleFactions.java](src/main/java/me/Plugins/SimpleFactions/SimpleFactions.java))
- Configuration files (and titles from JSON) are loaded and stored via the Loader classes  
  ([Loaders](src/main/java/me/Plugins/SimpleFactions/Loaders/))
- `FactionManager` handles faction creation, lookups, member/leader queries, and database calls  
  ([FactionManager.java](src/main/java/me/Plugins/SimpleFactions/Managers/FactionManager.java))
- The `Inventory` package contains the extensive GUI classes players interact with  
  ([Inventory](src/main/java/me/Plugins/SimpleFactions/Managers/Inventory/))
- `MapSystem` is the primary interface for REST communication, while `RestServer` manages the actual requests and responses  
  ([MapSystem.java](src/main/java/me/Plugins/SimpleFactions/Map/MapSystem.java),  
  [RestServer.java](src/main/java/me/Plugins/SimpleFactions/REST/RestServer.java))

## Key Challenges Solved

### Map System
Most Minecraft plugins that work with territory rely on the game's built-in region system (chunks). Chunks are small, perfectly square units, which makes borders look artificial. Since our world uses a fixed 4096×4096 coordinate grid, I realized I could generate an image of the same resolution and map each world coordinate directly to a pixel.

By drawing irregular “blobs” of unique RGB colors on this image, I created natural-looking provinces where:

> **province = the color of the pixel at your current coordinate**

The Minecraft plugin handles the coordinate and faction logic, while the external Python service (**ProvinceSystem**) processes the image and exposes province/border data on our website:  
https://www.tfminecraft.net/

A major challenge was keeping both systems synchronized efficiently. The Python service can take several minutes to redraw the full map, so regenerating unchanged regions was wasteful. To solve this, I implemented a queued update system that tracks exactly which provinces or borders changed in the Minecraft server state, and sends only incremental updates to the Python service. This dramatically reduced processing time and ensured consistent, near-real-time synchronization between the two systems.

---

### Money Flow and Taxation
Introducing a virtual economy requires strong guarantees against exploits (e.g., infinite money loops). One of the biggest challenges was implementing **nested taxation**:

- A faction can tax its members.
- A faction can be a subject to an overlord, which may also have its own overlord.
- Foreign income can be taxed depending on diplomatic relations.

The system needed to propagate tax values through these nested relationships without ever allowing the combined tax rate to exceed 100% - otherwise it would generate money that never existed. I solved this by implementing a clamped, hierarchical taxation model that guarantees consistency and prevents exploitable edge cases.

## AI Tools
I used **ChatGPT** primarily for troubleshooting and generating small amounts of code, such as the logic for loading titles from JSON.

For architecture, class design, and the overall structure of the plugin, I relied on my own judgment. In my experience, AI struggles to maintain coherent object-oriented structure in larger projects, so all high-level design, class relationships, and system architecture were created and implemented manually.

Most of the plugin was written without AI assistance to maintain control over the design and ensure consistency. **ChatGPT** was also used to help format and refine this README.

