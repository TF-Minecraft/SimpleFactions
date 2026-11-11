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

A field battle with capture points ends when a side runs out of lives, retreats, or wins by control. Each second, the side holding more points than any other gains `per_second` × its share of the points, and holding every point multiplies that by `all_points_multiplier`. A point counts as held at 50% capture. The first side to `target` wins. The settings are under `battle.control` in `config.yml` (defaults: 1000, 0.5 and 4, so holding 2 of 3 points takes about 50 minutes and holding all of them about 8). Staff can switch control victory off for one field battle with the **Control victory** button in `/battle edit` before it starts, for a lives-only fight. Sieges keep their contest timer.

Campaign battles wear armies down through wounds. When a field battle or siege starts, every force on a side puts lives into the side's shared pool: each fighting faction's regiments allowed in that battle, each non-fighting subject's levy (its levy share of every regiment, militia included; all of its militia when the battle is on its own province), and each hired mercenary company's members on the roster. When it ends, the side's deaths become wounds: split between forces by the lives each brought, militia first inside a force, then law and paid slots in proportion. No force loses more than it brought. Law-granted slots are full again after every battle. Wounded paid slots cannot fight and cost no upkeep, and they are lost when the war ends. Mercenary companies never lose slots; their wounds heal by `mercenary_heal_percent` of the company every `mercenary_heal_hours`. A faction has one army for all its wars, and recruiting during a war reinforces it. The settings are under `war.battle_military` in `war.yml`.

Each war keeps a ledger of its battles, so staff can correct losses at any time before it ends. `/war admin losses <war>` lists the battles and what the war's end will take, and `/war admin losses <war> <battle#> attacker|defender set|add|remove <deaths> [reason]` corrects a battle's deaths. `/war admin army <faction>` shows each regiment's slots, wounds per war and what can fight. `/war admin wounds <war> <faction> <regiment> add|remove <lives> [reason]` and `/war admin wounds company <guild> [set <lives>]` change wounds directly. Before a campaign battle starts, `/battle lives <battle> <side> set|add|remove <lives>` sets the lives that side starts with, and `auto` goes back to what its forces give.

Use `/faction findvehicles personal` to locate your current character's vehicles.
Faction leaders can also use `/faction findvehicles pool` or
`/faction findvehicles <installation id>`. Tab completion suggests the available
choices. Each vehicle shows its world and coordinates when known, including
stored vehicles. For an installation named `pool` or `personal`, use
`/faction findvehicles installation:<id>`; tab completion supplies this form.

## A shared political world

[TFMCWeb](https://github.com/TF-Minecraft/TFMCWeb) presents the world map, borders, settlements, and military activity on the website.

The website's war pages follow each war as it happens: who declared it and who joined, the battle day, every battle with its losses per faction, and how the war ended. SimpleFactions keeps these events and finished battle records in `plugins/SimpleFactions/WarHistory/outbox.jsonl` until the website confirms them, so nothing is lost while it is unreachable. `war.history.enabled` in `war.yml` turns this off. Staff can check what is waiting with `/war admin history status` and send battles fought before the war pages existed with `/war admin history upload-battles`.

Players mark when they can fight in game (`/war vote <war id>`, or "When can you fight?" in the campaign view) or on a war's page, and a faction leader can answer a call to arms there or from a Discord DM. The website queues each of these; every five seconds SimpleFactions fetches them, checks each exactly as it checks the same click in game (a web mark does not need the player to be online), and tells the website whether it was applied or why not.

### Battle availability

Each player marks every upcoming date and start time they could fight; a mercenary under contract marks for the faction that hired them. Each date is decided on its own, 48 hours before its battle window opens (21:00 CET two days before): it becomes a battle day when, at one start time, `min_per_side` (3) of each side marked it, or the whole side if it has fewer active members (members away four weeks do not count). Otherwise there is no battle that day. 24 hours before the window (21:00 CET the day before), a battle day's start time is fixed: the time most of both sides can make, then the most people, then the earliest. After `max_days_without_battle` (3) dates in a row without a battle, the last one is a battle day anyway, at its most marked time; if one side marked nothing that day, its battle is autoresolved. At most two battle days wait to be fought. A declared war's grid starts a week ahead, and its buildup freeze lasts until the first battle. Reminders go to those who have not marked, in game and through the website's Discord DMs. The settings are under `war.availability` in `war.yml`. Staff use `/war admin schedule <war> availability|lockday|unlockday|sethour|skipday|castvote`. The results of the last 1000 are kept in `WarHistory/actions-applied.json`, so an action is never applied twice. `war.history.actions_enabled` in `war.yml` turns this off; `/war admin history status` shows it too.

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
