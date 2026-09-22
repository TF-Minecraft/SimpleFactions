# Roadmap

## Next

Everything SimpleFactions owns is shipped. Diplomacy polish, the chronicle snapshot, war companies and declare codes are all done, and assassins have been dropped.

- **Map chronicle events** - other member; SF hooks for ProvinceSystem (`war_declared`, `battle_scheduled`, `battle_result`, `province_occupied`, `war_ended`)

## Shipped

- Province fertility score 0-100 on the grid (`FertilityProvinceResolver`); crop growth and harvest quality are Cooking ([fertility.md](./fertility.md), [cooking/docs/crops.md](../../cooking/docs/crops.md))
- Declare codes and ticket gate - staff mint a one-time code in Discord with the `factions` cog's `/warcode mint`, the attacking leader types it in chat, and it pins the war goal so the picker is skipped. Realm-scoped and hashed in ProvinceSystem (`war_declare_codes`), reached through TFMCWeb's gateway, which injects the realm id. Redeemed only once `declareWar` returns a war, so a navy-gate refusal does not burn a ticket. `simplefactions.admin` bypasses the gate, which is what lets it fail closed. Staff look up faction ids with `/war admin factions [filter]`.
- War companies - six-phase program, all phases done: [planning/war-companies/00-index.md](./planning/war-companies/00-index.md) (lock), [01-phases.md](./planning/war-companies/01-phases.md) (batches), [mercenaries.md](./mercenaries.md) (reference). Army recruitment rule, guild dividends, mercenary companies and slots, contracts and the market, war participation with attendance and shared lives, wages, and company reputation. Company PvP stats are `GuildModifier` entries on the company, not normal guild upgrades, and only apply while a member fights as a hireling.
- Guild dividends - leader sets a percent of the guild's dividend base; eligible members split it equally on the daily tick, the faction withholds dividend tax, and the shares show in `/ledger`. Previous-tick membership is required by default (`dividend-require-previous-tick-membership`) so nobody joins on payday.
- Chronicle snapshot - `chronicle.json` uploaded every 300 s with per-faction wealth, prestige, rank and territory for season graphs ([map-export.md](./map-export.md), [planning/chronicle-export/00-index.md](./planning/chronicle-export/00-index.md)). Includes the prestige idempotency fix and `PrestigeRank` persistence.
- Guild ↔ faction GUI links; Friendly attitude used-cap; rival/hostile/unfriendly relative-prestige diplomatic capacity curve
- Council-forced white peace and surrender (political action on a chosen war; sticky offer or immediate surrender)
- NAP treaty overlay (diplomacy slot right of the nation icon; stacks with tributary; `blocks-war` declare block)
- Automated campaign wars (pathfinder, initiative, occupation bulge, battle scheduling)
- Inter-vassal wars (peer/cousin declare, CTA for all wars, liege transit, internal subjugate via `transferSubject`): [planning/inter-vassal-wars/00-index.md](./planning/inter-vassal-wars/00-index.md)
- War-goal declare and auto-apply through Phase 8 (navy gate, relation/title/law/pillage goals, movement apply gate, civil wars, inter-vassal)
- Civil wars (temp rebels, land split, untangle then apply; no auto reparations on defender win)
- Pillage war type (one-battle settlement; distinct from campaign raids)
- Warbands, military commitment, collective lives, casualty ledger
- Battle runtime (field, siege, raid templates), battle dev mode for staging
- Campaign time dev mode (`/war admin time`, route **Starts in** countdown)
- Strategic retreat during voting (concede slots without initiative cost; **Retreated** route lore)
- Mid-fight battle retreat (`/warband retreat` on started campaign field/siege battles; ledger casualties only)
- Campaign GUI live refresh (1s) and vote-close hour lock
- Campaign battle schedule, fort/port ZOC, naval invasions, dual-leg counter-push
- Installation transfer with province owner; wartime occupation then revert at peace
- War campaign map export: route line, battle pins, `occupied_by_*` on `wars[]`, `occupied_by` on `province_data`
- Installations (fort, port, airport), settlements, province grid
- Vehicle berths at installations, personal slot limits, battle vehicle eligibility
- Campaign installation picks, vehicle in-play, siege fort on schedule slot
- Campaign raids (inter-battle installation assaults)

Canonical war gameplay spec: [wars.md](./wars.md)

Scratch list: [TODO.md](../TODO.md).
