# SimpleFactions

> Nations, diplomacy, and political strategy for TF-Minecraft.

SimpleFactions turns groups of players into nations with territory, rulers, economies, and relationships. Its name comes from an early, simpler nation system; the project has grown into a broad political simulation inspired by Paradox Interactive's strategy games.

Players shape a world of provinces and titles, organise settlements and guilds, and navigate alliances, subjects, taxation, and war. Their nations also appear on the connected web map, making changes to the political landscape visible beyond the game.

## Features

- **Provinces and titles** — govern territories with natural-shaped borders and titles such as duchies and kingdoms.
- **Layered diplomacy** — form alliances, establish subject and overlord relationships, negotiate treaties, and impose trade embargoes.
- **National economies** — manage wealth and taxation, including income moving through relationships between members, nations, and overlords.
- **Settlements and guilds** — establish named cities and capitals and organise groups within a nation.
- **Military infrastructure** — construct forts, ports, airports, and train stations with `/faction construct <fort|port|airport|train_station> <name>`.
- **Espionage** — appoint a Spymaster, protect faction secrets, and obtain shared daily intelligence estimates.
- **Campaign warfare** — pursue war goals through scheduled battles, player voting, warband participation, and campaign progression.

## Installation trade

Ports, airports and train stations carry a guild's trade and production further, and so do the sea lanes and railways between them. Trade can board a line at any province along it, and it is strongest on and off at installations.

A guild uses installations in its own realm fully. An embargo or a war closes them. Otherwise access is the better of the trade agreement and the two realms' economy laws. An isolationist host stays closed to foreigners without an agreement.

| Economy law | Grants to foreign guilds | Own guilds' reach abroad |
|---|---|---|
| Free trade | 50% | +10% |
| Decentralized | 35% | 0 |
| Mercantilism | 15% | +20% |
| Protectionism | 10% | 0 |
| Isolationism | 0 | -25% |

Config keys:

- `installation-trade.transport` — rail, sea, and air, each with `trade`, `production`, and `kept-per-1000-blocks`
- `installation-trade.corridor-share`
- Relation types: `installation-access` and `blocks-installations`
- Law modifier: `installation_access`

## A shared political world

[ProvinceSystem](https://github.com/TF-Minecraft/ProvinceSystem) presents the world map, borders, settlements, and military activity on the website.

## Project background

Created by Drefvelin, with inspiration from Paradox Interactive. The original project notes credit ChatGPT for troubleshooting, some code assistance, and README editing.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/SimpleFactions/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

[Espionage and special positions](https://github.com/TF-Minecraft/Docs/blob/main/projects/SimpleFactions/docs/espionage.md)

## Tests

Run `mvn clean verify` with Java 21, as CI does. The build needs the private
dependency jars and shared TF-Minecraft plugins described in the project
documentation.

The suite in `src/test` uses JUnit 5 and Mockito, mocking the Paper and plugin
APIs rather than starting a server. It checks plugin logic, not behaviour on a
live Paper server. Surefire writes reports to `target/surefire-reports/`. No
coverage gate is enforced.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
