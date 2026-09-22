# Dev config and bypasses

Running list of **dev-only config, commented-out checks, and test bypasses** on the SimpleFactions test server. Revert or replace before production.

**War gameplay spec:** [wars.md](./wars.md) · **Map upload:** [map-export.md](./map-export.md)

---

## `config.yml` (dev server template)

Shipped default: `src/main/resources/config.yml`. Live server merges overrides into `plugins/SimpleFactions/config.yml`.

Campaign keys (`war.*`) are in **`war.yml`** (`src/main/resources/war.yml` → `plugins/SimpleFactions/war.yml`).

| Key | Dev value | Production target | Notes |
|-----|-----------|-------------------|-------|
| Header comment | Was `dev server template` | Now `map-reference: main (world: TFMC_Map)` | Documents intent only |
| `map-reference` | `main` on prod file; use `dev` on test | `main` (or live map id) | Drives TFMCWeb upload/regen paths |
| `enable-chronicle` | `true` | `true` | Chronicle snapshot upload. Set `false` to stop the chronicle without taking the map down; `enable-map` still gates it |
| `war.require_declare_code` (`war.yml`) | `false` to declare without a Discord ticket | `true` (**shipped value**) | On, an attacking leader must type a staff-minted Discord code, which pins the war goal. `simplefactions.admin` bypasses it |
| `war.declare_code_timeout_seconds` (`war.yml`) | `10` | `10` | How long to wait for ProvinceSystem before refusing the declare. The gate fails closed |
| `war.battle_cadence.provinces_between_battles` (`war.yml`) | `3` | `3` (or higher after playtest) | Field battle cadence |
| `war.battle_loot.mode` (`war.yml`) | `COMMAND` | `COMMAND` | `COMMAND` runs every line in `commands` from console once per rewarded player; `ITEM` gives a TLibs item instead. An unrecognised value falls back to `COMMAND`. See [wars.md](./wars.md#battle-loot) |
| `war.battle_loot.commands` (`war.yml`) | Point it at a throwaway command you can watch in console | The real crate-key command | Used when `mode` is `COMMAND`. Both `%player%` and `#player#` are replaced with the player name. An empty list means no loot |
| `war.battle_loot.item` (`war.yml`) | `v.diamond` | The real reward item | Used when `mode` is `ITEM`. TLibs path (`v.`, `ia.`, `m.`, `modeled.`). Blank means no loot |
| `war.battle_loot.item_amount` (`war.yml`) | `1` | `1` | Stack size for `ITEM` mode. Clamped to at least `1` |
| `max-prestige-playtime-exponent` | `5`, or lower to reach the ceiling sooner while testing | `5` | Caps the per-member playtime prestige curve. `5` means a member tops out at 32, at 500 online hours. Needs RPCharacters; the term is 0 without it. See [prestige.md](./prestige.md) |
| `installations.*.construction-time` | `10` to watch a build finish in a session | `432000` fort / `259200` port+airport (**shipped values**) | Seconds |
| `mercenary-formation-seconds` | `10` to test the founding flow | `86400` (24 h) | Real seconds, same tick model as construction. Shipped file has the production value |

**Tick model:** faction `tick()` runs **once per real second** (`FactionManager` timer every 20 ticks). Construction and regiment expansion `timeLeft` decrement **once per second**, so `10` = **10 seconds**, not 10 days.

Mercenary key reference (all keys with defaults): [mercenaries.md](./mercenaries.md).

---

## `regiments.yml`

All four now ship production values. Lower any of them to `10` to test the expansion queue, then restore.

| Regiment | Key | Shipped value | Dev override |
|----------|-----|---------------|--------------|
| professional | `expansion-time` | `21600` (6 h) | `10` |
| militia | `expansion-time` | `43200` (12 h) | `10` |
| levy | `expansion-time` | `43200` (12 h) | `0` for instant expansion |
| mercenary | `expansion-time` | `86400` (24 h) | `10` |

Queue ticks once per faction second (same as construction).

`Guilds/company-upgrades.yml` uses `expansion-time: 86400` for all three company upgrades; lower it the same way to test the purchase queue.

---

## `Guilds/upgrades.yml`

All four upgrades now use `expansion-time: 21600` (6 h), matching the `Upgrade.java` loader fallback. Lower to `10` to test the purchase queue, then restore.

| Upgrade id | Shipped `expansion-time` |
|------------|--------------------------|
| `max_admin_power` | 21600 |
| `max_diplomatic_capacity` | 21600 |
| `admin_power_gain` | 21600 |
| `auto_dealer_tables` | 21600 |

---

## Code bypasses (must revert)

| Location | What | Production action |
|----------|------|-------------------|
| `RestServer.java` L24 | `REGEN_HASH` hardcoded | Move to config / env |

---

## Admin / debug commands (intentional staff tools)

| Command | Purpose |
|---------|---------|
| `/war list` | Open war list GUI |
| `/war admin status <id>` | JSON war state (campaign, initiative, proposals) |
| `/war admin path <id>` | Regenerate campaign route |
| `/war admin end <id>` | Force-end a war (no goal apply, no reparations) |
| `/war admin win <id> attacker\|defender` | End war with victory outcome (goal apply or reparations) |
| `/war admin devmode on\|off\|status` | Volatile war devmode (roster fill + unrestricted raid launch) |
| `/war admin raid resetquota <id> [aggressor\|defender\|both]` | Clear daily raid quota for current battle day |
| `/faction fullregen <map>` | Trigger map regen via TFMCWeb |
| `/faction regen` | Queue nation upload + regen |

---

## Timing quirks (verify before prod)

| System | Actual tick | UI / docs say | Action |
|--------|-------------|---------------|--------|
| Administrative power gain | Every **3600 s** (`FactionManager.timer % 3600`) | Lore shows `+N/hour` | Matches lore |
| Stability / pillage decay | Same hourly `powerTick` | `%/hour` lore; pillage `POWER_TICKS_PER_DAY = 24` | Matches lore |
| Movement organization | Once per daily tick (`Faction.newDay`) | GUI `per day`; `base` 30 | ~3.3 days to 100 org at 100% power, 1 cause |
| Faction daily cycle | `timer >= 86400` (~24 h real time) | Daily guild income, loans, movement org | Real-time days on test server |
| Diplomacy opinion drift | `RelationManager.tick` every **3600 s** | - | Real-time hour |
| Map partial upload | `MapSystem` full update every **3600 s** | - | Real-time hour |

---

## Wars - dev-friendly settings

| Item | Dev setting | Prod |
|------|-------------|------|
| Declare without Discord ticket | `war.require_declare_code: false` | Declare codes + ticket gate |
| Declare GUI pre-checks | Live: opinion threshold, duplicate war, target online | Same, plus the code gate |
| Faction id lookup for minting | `/war admin factions [filter]` | Same (staff only) |

### Declare codes (test server E2E)

Prerequisites: `war.require_declare_code: true` in `war.yml`, the `factions` cog loaded and pointed at the same ProvinceSystem as TFMCWeb, and its `realm_id` matching what TFMCWeb reports on this server. Run the declare as a **non-admin** leader, or the gate bypasses itself.

1. `/war admin factions` in game, copy the attacker and defender ids.
2. `/warcode mint <attacker> <defender> <goal>` in Discord, copy the code.
3. `/warcode list` shows the id, the pairing and the goal, and **no** code text.
4. As the attacking leader, diplomacy → **Declare War**. Expect a chat prompt, not the goal picker.
5. Type a wrong code. Expect `Invalid war code` and no state change.
6. Type the real code. Expect `Code accepted. War goal: <goal>` and the goal's **own** sub-picker (or confirm for War, Tributary, Usurp, Open Market). The goal picker must never appear.
7. Confirm. Check `plugins/SimpleFactions/Logs/war.log` for a `DECLARE_CODE` line next to the `DECLARE` line.
8. `/warcode list` no longer lists that id.
9. Repeat step 4 with the same code. Expect `This code has already been used`.
10. Mint another code, then make the declare fail on purpose (naval path with no port, or an ineligible goal target). The code must survive: `/warcode list` still shows it and a second attempt works.
11. Stop ProvinceSystem, mint nothing, and try to declare. Expect a refusal, not a free declare. Then repeat as an admin (`simplefactions.admin`) and confirm the goal picker opens with no prompt.

### Battle scheduling timeline (Europe/Paris)

Defaults under `war.battle_schedule`:

- **Vote open:** when next battle is pending (at declare or after battle end).
- **`defender_choice_deadline_hour` (12):** auto Push (winner) or Attack (loser after Hold) if unresolved.
- **`vote_close_hour` (16):** tally, schedule or postpone (`battleDay` +1, votes persist).
- **First battle day:** calendar day after declare.

**Dev surface (remove before prod):**

| Mechanism | Detail |
|-----------|--------|
| `/war admin schedule <id>` | Admin subcommands: `opencvote`, `closevote`, `skipday`, `castvote <hour> [attacker\|defender\|both]`, `forcequorum`, `setscheduled <iso>`, `battlecreate`, `battledelete`, `battlestart`, `winbattle attacker\|defender`, `choice push\|hold\|attack\|accept` (aliases: `battlechoice`, `defenderchoice`, …) |
| `war.battle_voting.dev_min_players: 1` | Test-server quorum override. **Removed from the shipped `war.yml`**; add the key back to lower quorum, delete it to fall back to `min_players: 4` |
| Shortened hour keys | Test server may use e.g. 10/11/12/13 if order constraint holds |

---

## War dev mode

| Mechanism | Detail |
|-----------|--------|
| `/war admin devmode on\|off\|status` | Volatile in-memory toggle; resets on restart. Admin only. |
| `war.devmode.phantom_count` | Default 10 phantom UUIDs on manual `/warband create` or campaign `battlecreate` when devmode on |
| Raid QA | With devmode on, raid launch ignores battle day, raid hour window, and daily quota (mutex still applies). |
| `battle.capture_min_players: 1` | Min players at capture zone |
| Campaign join rules | Side membership check; roster cap = preview collective lives; bypass faction `WarbandSlot` for campaign battles |

**Solo staging workflow:**

1. `/war admin devmode on`
2. Declare war; open/close vote with `dev_min_players: 1`
3. `/warband create` or war GUI - phantoms in lore when devmode on
4. `/battle join <id> attacker` - wrong side rejected; roster cap enforced
5. Fight solo (`capture_min_players: 1`), verify lives/casualties/commitments
6. `/war admin devmode off`; restart clears devmode

---

## Campaign time dev mode

| Mechanism | Detail |
|-----------|--------|
| `/war admin time add 1h 31m` | Advance spoofed Paris schedule clock (compound tokens: `1h31m`, `1d`, `90s`) |
| `/war admin time reset` | Restore real time; resets hour gate and cancels raid Bukkit tasks |
| `/war admin time status` | Show offset, Paris date/hour, spoofed flag, active war count |
| `/war admin time skip-to-battle-day <warId>` | Jump to 00:00 Paris on war's `battleDay` (fixes `first_battle_day_after_declare` tomorrow default) |
| Route / schedule GUI | Gray **Starts in X** under **Next battle** on active route slot; schedule panel when `SCHEDULED` |

**Notes:**

- Admin only (`Permissions.isAdmin`); offset is volatile (lost on restart).
- Each mutating command runs `BattleScheduleTickService.onClockOffsetChanged()` then `tick(CampaignClock.now())` for immediate effect.
- Does **not** affect faction daily timer, guild income, or vehicle upkeep.
- `war admin schedule` subcommands remain for low-level overrides; prefer `war admin time` for schedule E2E.

**Typical workflow:**

1. Declare war (two factions).
2. `war admin time skip-to-battle-day <warId>` (or `add 1d` if battle day is tomorrow).
3. `add` into vote window; cast votes in GUI.
4. `add` past `vote_close_hour` - vote closes, phase `SCHEDULED`.
5. Confirm route shows **Starts in X**; `add` until fight starts.
6. Post-battle: `add` to `defender_choice_deadline_hour` for hold/push auto-resolve.
7. Raid window: `add` into raid hours; muster/fight via tick (no long real wait while spoofed).
8. `war admin time reset` when done.

---

## Campaign UX (test server E2E)

Prerequisites: `war.battle_voting.dev_min_players: 1`, optional `/war admin devmode on`.

**War setup (preferred):** declare → `war admin time skip-to-battle-day <id>` → vote in GUI → `war admin time add` past vote close → confirm **Starts in X** on route → `add` to fight time.

**War setup (manual override):** `war admin schedule <id> opencvote` → `castvote` → `forcequorum` → `closevote` (or `setscheduled` / `battlecreate`) when you need to bypass clock logic.

**Battle prep:** campaign warbands start empty until signup; staff `/battle edit` for spawns/jails/points; **siege:** Contest Area → click Min/Max at fort corners; `/warband join`. Optional: shorten `battle.signup_reminder_seconds_before` (e.g. `60`, `30`) to verify chat reminders before fight time.

**Fight loop:** `war admin time add` to fight time (tick runs on each add) or `war admin schedule <id> battlestart`; if siege contest is missing, countdown shows **Cannot start** and belligerents get a chat warning; casualties apply; war returns to **VOTING**.

**Cleanup:** `war admin time reset`; `/war admin devmode off`; battles/warbands persist on disk (`plugins/SimpleFactions/Battles/`, `Warbands/`).

### Strategic retreat (test server E2E)

Prerequisites: same as **Campaign UX** (`dev_min_players: 1`, optional `/war admin devmode on`).

1. Declare war; `war admin time skip-to-battle-day <id>`.
2. Open campaign GUI as **pushed** war leader during `VOTING` (defender on invasion push).
3. Confirm **Retreat** (slot 46) visible; pusher war leader does not see it.
4. Confirm retreat: route slot shows **Retreated**; schedule index and cursor advance; initiative fuel unchanged.
5. Hour votes still present; phase stays `VOTING`.
6. Second retreat in same window (if next slot exists): next slot **Retreated**; still `VOTING`.
7. Siege slot retreat: fort controller flips without a fought battle.
8. `war admin time add` past `vote_close_hour`: retreat button and hour toggles lock (GUI refreshes within ~1s).
9. Counter-push (`toward_aggressor_capital`): **attacker** (pushed) may retreat; counter schedule index advances.
10. `retake_objective` phase: retreat hidden / rejected.

See [planning/campaign-retreat/05-docs-verify.md](./planning/campaign-retreat/05-docs-verify.md) for the full manual matrix.

### Battle retreat (test server E2E)

Prerequisites: campaign battle running; set `battle.retreat_min_elapsed_seconds: 0` in config for fast testing (restore `1200` after).

1. Declare war; schedule and start a campaign field or siege battle (`war admin time` / `war admin schedule <id> battlestart`).
2. As **warband leader**: `/warband retreat` - confirm GUI with warning lore.
3. Cancel - battle continues.
4. Confirm - opponent wins; success message; campaign advances as a normal battle loss.
5. Take some deaths before retreat - verify partial casualties in `war admin status` / commitment rows.
6. Restore cooldown to `1200`; retry within 20 min - rejected with remaining-time message (no GUI).
7. Non-leader or non-warband player - rejected on command.
8. Campaign raid battle - rejected.

See [planning/battle-retreat/04-docs-verify.md](./planning/battle-retreat/04-docs-verify.md) for the full manual matrix.

### Mercenary companies (test server E2E)

Prerequisites: two factions and two players, `war.battle_voting.dev_min_players: 1`, optional `/war admin devmode on`, and `/war admin time` for the campaign clock. Lower `mercenary-formation-seconds` (`config.yml`), `mercenary.expansion-time` (`regiments.yml`) and the company `expansion-time` keys (`Guilds/company-upgrades.yml`) to `10` so the 24 h timers finish inside a session; restore them afterwards.

Commands: `/company <found|invite|accept|decline|kick|expand|draft|offer|contracts>`, `/mercenaries [list|hire <name>]`, `/ledger`.

1. `/company found <name>` - 100 d charged once; wait out formation; confirm 1 slot.
2. `/company expand` with the slot empty - refused; enlist, retry, restart mid-expansion and confirm it resumes.
3. Confirm the mercenary regiment appears in **no** faction military screen and moves no faction totals.
4. Sign a contract at the company's home settlement as a council member; try again from another province - refused, naming the settlement.
5. Fight a battle: confirm the company in the war screen with promised slots, lives folding in filled and attending slots only, and a dual-role player subtracted once.
6. Run a daily tick mid-contract: six ledger lines with correct signs, wages in the soldier's `/ledger`.
7. Restore the timer keys.

The money and reputation checks need a faction daily tick, which is real time (`timer >= 86400`) and is **not** moved by `war admin time`.

See [planning/war-companies/08-verify.md](./planning/war-companies/08-verify.md) for the full 21-step matrix.

---

## Related docs

- [roadmap.md](./roadmap.md) - what is still planned for production
- [wars.md](./wars.md) - full war spec
- [mercenaries.md](./mercenaries.md) - mercenary config keys and defaults
- [map-export.md](./map-export.md) - `map-reference` and regen hash
