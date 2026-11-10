# SimpleFactions

> Nations, diplomacy, and political strategy for TF-Minecraft.

SimpleFactions turns groups of players into nations with territory, rulers, economies, and relationships. Its name comes from an early, simpler nation system; the project has grown into a broad political simulation inspired by Paradox Interactive's strategy games.

Players shape a world of provinces and titles, organise settlements and guilds, and navigate alliances, subjects, taxation, and war. Their nations also appear on the connected web map, making changes to the political landscape visible beyond the game.

## Features

- **Provinces and titles** — govern territories with natural-shaped borders and titles such as duchies and kingdoms.
- **Layered diplomacy** — form alliances, establish subject and overlord relationships, negotiate treaties, and impose trade embargoes.
- **National economies** — manage wealth and taxation, including income moving through relationships between members, nations, and overlords.
- **Settlements and guilds** — establish named cities and capitals and organise groups within a nation.
- **Military infrastructure** — construct forts, ports, airports, and train stations; transport networks extend guild trade and production.
- **Espionage** — appoint a Spymaster, keep regiments and vehicles secret, and obtain shared daily intelligence estimates.
- **Campaign warfare** — pursue war goals through scheduled battles, player voting, warband participation, and campaign progression.

Use `/faction findvehicles personal` to locate your current character's vehicles.
Faction leaders can also use `/faction findvehicles pool` or
`/faction findvehicles <installation id>`. Tab completion suggests the available
choices. Each vehicle shows its world and coordinates when known, including
stored vehicles. For an installation named `pool` or `personal`, use
`/faction findvehicles installation:<id>`; tab completion supplies this form.

## A shared political world

[TFMCWeb](https://github.com/TF-Minecraft/TFMCWeb) presents the world map, borders, settlements, and military activity on the website.

The website's war pages follow each war as it happens: who declared it and who joined, the battle day, every battle with its losses per faction, and how the war ended. SimpleFactions keeps these events and finished battle records in `plugins/SimpleFactions/WarHistory/outbox.jsonl` until the website confirms them, so nothing is lost while it is unreachable. `war.history.enabled` in `war.yml` turns this off. Staff can check what is waiting with `/war admin history status` and send battles fought before the war pages existed with `/war admin history upload-battles`.

Players can also vote on the first battle day and the battle hour on a war's page, and a faction leader can answer a call to arms there or from a Discord DM. The website queues each of these; every five seconds SimpleFactions fetches them, checks each exactly as it checks the same click in game (a web vote does not need the player to be online), and tells the website whether it was applied or why not. The results of the last 1000 are kept in `WarHistory/actions-applied.json`, so an action is never applied twice. `war.history.actions_enabled` in `war.yml` turns this off; `/war admin history status` shows it too.

## Project background

Created by Drefvelin, with inspiration from Paradox Interactive. The original project notes credit ChatGPT for troubleshooting, some code assistance, and README editing.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/SimpleFactions/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

[Espionage and special positions](https://github.com/TF-Minecraft/Docs/blob/main/projects/SimpleFactions/docs/espionage.md)

## Tests and coverage

Run `mvn clean verify` with Java 21, as CI does. The build needs the private
dependency jars and shared TF-Minecraft plugins described in the project
documentation.

The suite in `src/test` uses JUnit 5 and Mockito, mocking the Paper and plugin
APIs rather than starting a server. It checks plugin logic, not behaviour on a
live Paper server. Surefire writes reports to `target/surefire-reports/`. JaCoCo
writes HTML and XML reports to `target/site/jacoco/`; `verify` requires 100%
production line coverage with no class or package exclusions. Branch coverage
is reported separately. CI uploads both test and coverage reports.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
