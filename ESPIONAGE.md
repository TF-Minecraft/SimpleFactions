# Espionage and special positions

Foreign faction and guild menus show public identity and flavor details, including
faction leaders' character names when available, government, rank, tier, titles, settlements, culture,
religion, guild types, allies and subjects. Prestige is public and its ranking
uses exact values. Other figures are hidden or shown as intelligence ranges.
Members retain exact information about their own faction and guilds. This covers
in-game menus; website exports and staff administration commands are unchanged.

A faction without an eligible, living Spymaster reveals exact information to
everyone, including its guild menus, complete rosters, ledgers and wealth rankings.
No daily estimates are generated for an unguarded faction. This takes effect when
menus are reopened, including after removal, permanent character death or a solo
leader becoming ineligible. Appointing an eligible Spymaster restores the usual
intelligence checks, even if their aptitude is 0. Public viewing does not grant
management authority or access to private sabotage settings. Staff alone retain
the additional Minecraft account names provided by their bypass permission.

Without an eligible Spymaster, a faction also receives no daily reports about
protected foreign factions. Previously cached reports become unreadable immediately;
private fields remain Unknown and the menu says "Report quality: Absent" with
"The Spymaster's office stands vacant; no findings reach your court."
Unguarded foreign factions and normally public information remain visible.

## Staff viewing permission

Set the permission node in `plugins/SimpleFactions/special-positions.yml`:

```yaml
espionage:
  bypass-permission: simplefactions.espionage.bypass
```

After editing this setting, use `/faction reloadconfigs` in game. Missing or blank
values use the default node above. Changing the node does not assign permissions;
grant the configured node through LuckPerms.

Grant `simplefactions.espionage.bypass` in LuckPerms to staff who need the original exact
faction and guild menus, complete rosters, ledgers and rankings. It defaults to
false and must be assigned explicitly. These viewers skip daily intelligence
gathering and its notices; their views do not generate or change faction reports.
Removing the permission restores the usual membership and intelligence checks.
The permission grants information access; faction management and private
Spymaster conduct retain their existing leader and office-holder requirements.
For the staff track (`staff_player` → `staff_inactive` → `staff`), grant the node
explicitly to `staff` and deny it for `staff_inactive`; explicit denials inherited
from lower ranks override a generic `*` grant. On dev the active grant uses
`server=dev` so it does not change permissions on other servers.
Test promotion/demotion on a foreign faction with an eligible Spymaster: own and
unguarded faction information stays exact at every rank. Reopen the menu after a rank change.

## Spymaster appointment

Open **Special Positions** in your faction menu or use `/faction positions`, then
select **Spymaster**. This is an office directory, ready for additional positions.
Only the faction leader can appoint/remove the Spymaster. The appointee must be
an online member with an active RPCharacters character. The faction leader is
ineligible unless the faction has only one member across all its guilds. A solo
leader uses 25% of their permanent base aptitude, rounded down. If another member
joins, the leader's office becomes vacant when it is next checked. Missing or
ineligible Spymasters have aptitude 0.

- `/faction spymaster <player>` appoints a member.
- `/faction spymaster remove` leaves the office vacant.
- `/faction espionage` opens the holder's private settings, or the position menu
  for other members.

Only the appointee receives the roleplay appointment letter and their aptitude
in chat. Member and candidate entries use character names and guild affiliations;
staff bypass additionally displays account names. Invitations and appointment
commands accept either full character names (including spaces) or Minecraft
account names. Only online players can be invited; matching is exact and ignores
case. Duplicate character names require the account name.

New solo factions automatically assign the founder to the office, subject to the
existing solo-leader aptitude penalty. This automatic assignment does not consume
the first deliberate appointment. The first deliberate appointment is free and
causes no unrest. Every later appointment costs **250d** from the faction treasury
and causes **−10 stability points**, fading linearly over **7 elapsed real days**.
Removing an office holder does not reset the history. Unrest persists across
restarts, progresses during downtime, and repeated replacements stack their own
decaying penalties. Existing dev offices count as having used their first appointment.
These defaults are configurable (zero disables the corresponding cost or penalty):

```yaml
espionage:
  appointments:
    repeat-cost: 250.0
    stability-penalty: 10.0
    penalty-days: 7.0
```

Aptitude rolls **once per character**, independent of faction membership. Leaving,
rejoining, transferring factions, removing/reappointing, deleting a faction and
restarting do not reroll it. A server-wide registry persists these values in
`plugins/SimpleFactions/Cache/character-aptitudes.json`. Existing aptitude history
from the first dev build is imported on startup without rerolling. If the first
build gave a character multiple faction-specific values, the existing registry
wins, otherwise the first faction ID alphabetically supplies the permanent value.
Offices and daily reports remain in the existing faction JSON.

## Configurable aptitude weights

Edit `espionage.aptitude.attribute-weights` in SimpleFactions `special-positions.yml`, then
use `/faction reloadconfigs` in game or restart. Weights can be positive, zero or
negative. Defaults:

```yaml
espionage:
  aptitude:
    attribute-weights:
      intelligence: 3.0
      wisdom: 2.5
      charisma: 1.5
      dexterity: 1.0
      constitution: -2.0
      strength: -2.0
```

Use permanent MMOCore attribute bases (creation, traits and allocated points;
equipment bonuses excluded), capped to 0-16 and centred at 6:

```
base aptitude = clamp(round(50 + sum(weight * (attribute - 6)))
                      + uniformInteger(-20, 20), 0, 100)
```

Low mental/social scores and high strength/constitution penalise aptitude.
Physical specialists with mental attributes at 6 and strength/constitution at
16 roll 0-30. Strong mental/social builds with low physical attributes can reach
100; physical builds with poor judgment can reach 0. Non-finite config weights
fall back to defaults. Config changes affect new characters' first rolls only;
existing permanent aptitudes are retained.

## Daily intelligence and rankings

Any `/faction` or `/guild` command that opens a GUI requests the faction's
reports for protected foreign factions when your own Spymaster is eligible, including list, menu, positions, espionage
and Spymaster settings commands.
The first member to open a GUI by command that UTC day triggers the checks; all faction members
share the reports. Faction/guild clicks, sorting and periodic GUI refreshes only read cached
reports. They never roll or send intelligence messages. Commands that do not
open a menu never gather intelligence.
Protected uncached information stays hidden until a faction member with an eligible
Spymaster opens a GUI by command. Unguarded faction information is always exact.
One roleplay notice goes to the requesting player when new reports are created.

Days run from midnight to midnight **UTC**. Each faction rolls offense and defense
once per day:

```
offense = round(1.25 * effective aptitude + mean(3 uniformInteger(-75, 75))) - offensiveSabotage
defense = round(1.25 * effective aptitude + mean(3 uniformInteger(-75, 75))) - defensiveSabotage
margin = observer.offense - target.defense
```

Averaged luck makes a full 0-versus-100 upset exceptionally rare (roughly 0.002% with defaults), while smaller aptitude gaps can still be overcome. Luck spread, draw count and aptitude multiplier are configurable. Each ordered
observer/target pair has one snapshot per day. Reopening, replacing Spymasters,
sabotage changes and restarts do not reroll the day's intelligence. A vacant office
immediately disables its faction's reports and exposes its own information;
refilling it restores access to any still-current cached reports. Other changes affect
the next uncached rolls. A recreated target faction receives a fresh report.

| Margin | Intelligence | Approximate range width | Known non-leader members |
| --- | --- | --- | --- |
| <= 0 | Rumours | Up to 300% | 20% |
| 1-29 | Rumours | 300% of magnitude | 20% |
| 30-64 | Broad estimates | 150% | 40% |
| 65-99 | Reliable estimates | 40% | 60% |
| >= 100 | Detailed estimates | 20% | 80% |

Samples are rounded down, capped at 23, and saved with the report so reopening
cannot reveal extra names. They contain character names and guild affiliations.
The faction leader is always displayed separately alongside the member-count
range, regardless of quality. Guild rosters use the same sample, filtered by
guild. Guild leaders and special-office identities use their configurable tier gates.
All rosters distinguish the leader, guild leaders and office holders. The leader
uses the faction's custom ruler title, defaulting to Leader. Subjects belong only
to their own roster. The realm guild always comes first, followed by other guilds
in descending visible wealth (estimated midpoints for foreign views).

Ranges are rounded, asymmetric snapshots containing the true value when generated.
They never collapse to an exact number, including zero. Reports cover members,
wealth, prosperity, daily net income, professional army, levies, mercenaries,
installations, stability and administrative power. Professional army counts filled
professional soldier slots, excluding equipment and mercenaries. Guild wealth,
member count, net income and trade power have separate estimates.

Missing or unusably broad estimates display as gray **Unknown**. Counts, prosperity
and trade power cannot be negative; guild member estimates respect the configured
guild capacity. Stability estimates are limited to 0–100% and become Unknown if
they exceed their tier's configured maximum span (65 points at Broad, 50 at Reliable/Detailed). The faction tooltip shows the stability
state at the bounded range's midpoint, using the original state colors; the
Government item shows the range in that same color. Unbounded metrics become
Unknown when their interval is too broad relative to its midpoint. Wealth and
income retain meaningful negative estimates for debt and deficits. These rules
also apply when reading older cached reports, without rerolling or changing them.

Prestige ranking uses public exact values. Wealth/member rankings and guild
rankings compare known range midpoints, with exact values for the viewer's own
faction. Unknown entries appear alphabetically after ranked known entries and have
no numeric rank. Guild income leaderboards use the same intelligence policy.
Foreign tooltips retain the same colors, spacing and field order as own entries;
foreign faction and guild views retain their familiar slot layouts. Foreign
menus allow diplomacy and guild browsing, plus read-only masked ledgers, military,
government, laws, installations and upgrade windows.
Private menus recheck membership before interacting or refreshing.

Foreign ledgers show daily income, expenses, net income, and a reported accounts
menu in the original ledger slots; available cashflows appear under Income or
Expenses, without a long list of Unknown categories. Trade breakdowns and dividends use the same
snapshot estimates. Dividend percentages stay within 0–100%; income and expense
cashflows respect their valid signs. Missing new fields in an older report stay
Unknown until the next day's report; opening a ledger never regenerates it.
Report headings use RPCharacters' lore calendar (year offset and era), and name
the observing faction's Spymaster in their roleplay closing line. Daily report
refresh boundaries remain UTC.

## Private sabotage

The Spymaster can select their office and click their own head to inspect private
conduct. Offensive and defensive sabotage are separate voluntary settings, both
**disabled (0)** on appointment. Click to cycle through reductions of 0, 25, 50,
75 and 100 roll points, or use:

```
/faction spymaster sabotage offense <0|25|50|75|100>
/faction spymaster sabotage defense <0|25|50|75|100>
```

Only the office holder can see/change these controls. Preferences persist through
restarts and reset on a new appointment. Selecting 0 disables that side again.
Existing daily rolls and reports remain unchanged.

## Development verification

Run `mvn verify` with Java 21. Tests cover attribute weights and extremes, daily
luck, estimate bounds, caching, character aptitude transfer/persistence/migration,
leader eligibility, solo penalties, sampled rosters, rank privacy and permissions.
On dev, use `/faction menu` then compare reports from two members, test prestige
and wealth sorting, browse foreign guilds and inspect private conduct. This work
is deployed only to TFMCDev01; production changes follow the separate live process.

## Dedicated configuration and disclosure policy

All office/espionage settings live in `plugins/SimpleFactions/special-positions.yml`.
On first startup, old `config.yml` espionage values migrate, preserving custom
weights, costs and permission nodes. The old main file is backed up before its
espionage section is removed. Existing dedicated values always win; missing
settings are populated from defaults on startup or reload.

`espionage.intelligence.tiers` controls margin thresholds, uncertainty, roster
fractions and useful range widths. Lower tiers show broader ranges when useful.
`espionage.intelligence.minimum-tiers` controls each field's minimum tier.
Professional army and mercenary counts, administrative power, individual
cashflows and dividends require Reliable by default. Stability, levies,
installations and ledger totals require Broad. Members and wealth can appear at
Rumours. Office identities require Reliable and office aptitude requires Detailed.
An `unknown` threshold disables that field. Invalid or unconfigured fields fail
closed. `cashflows` accepts individual enum-category overrides such as `TRADE`.
These gates apply when generating **and reading** reports, including old caches.
Roster names also obey roster/guild-members gates. Faction leaders remain public;
guild leaders use guild-leader, and office identities use office-holder. Reports
always have at least Rumours quality, including negative margins. Every guild in
a faction shares that same daily quality.

Foreign Special Positions is a read-only report using the same daily comparison.
Office names/vacancy and aptitude ranges are saved snapshots; holder-only sabotage
choices are never included. Staff bypass can see exact foreign offices without
receiving appointment authority. All faction/guild leader labels use character
names, with Minecraft names in parentheses for bypass viewers.

## Staff test refresh

`/faction reloadespionage` reloads `special-positions.yml`, clears every faction's
daily offensive/defensive rolls and reports, then immediately rebuilds all foreign
reports. It works in game and from console, requires
`simplefactions.espionage.reload` (configurable via `espionage.reload-permission`,
default false), and intentionally overrides the once-per-day rule for testing.
Reopen menus afterward to see refreshed accounts. It preserves permanent aptitude,
office holders, sabotage preferences, appointment counts, treasury and unrest.
`/faction reloadconfigs` reloads settings without regenerating reports.

### Read-only foreign navigation

Foreign menus use the original inventory sizes, category slots, template icons
and back routes. Guild branches return to their normal building slots; their
levels/effects are estimates gated by `buildings`. Guild upgrades and the upgrade
queue use `upgrades`. The military view opens normally; its regiment counts are
gated by professional-army/levies and its training queue by `training` (Reliable
by default). Numeric tax rates use `taxes`, selected laws use `laws`, and
installation identity/levels/construction use `installation-details`. All use
the faction's cached report. Hidden categories keep their menu entry with
Unknown details. Foreign views cannot submit proposals, train troops, upgrade
buildings, cancel queues or modify another faction. Periodic refreshes never
replace a masked snapshot with an exact submenu. Ledger detail windows retain
the original 27-slot layout; cashflow estimates remain in the Ledger tooltip.

Generated daily reports always start at Rumours. A missing or invalid report
uses the zero-information category **Botched report**, with private fields Unknown.

Office appointments, removals and sabotage changes acknowledge success only after
the faction save succeeds. Failed saves restore the prior office state and refund
any appointment charge; faction JSON is staged before replacing the previous save.
A founder aptitude save failure leaves the office initialization pending and retries
when the office is checked while the founder has an active character. It never
finalizes a failed aptitude roll as a permanent zero or consumes the free appointment.
Founders without an active character (or without RPCharacters enabled) also remain
pending. With MMOCore absent, aptitude uses RPCharacters' saved attribute values.
Foreign reports use Unknown for missing character names; own views, staff bypass
and invitations retain their account-name fallback. Daily refreshes batch faction
saves once per changed faction. A startup failure before faction restoration
completes cannot overwrite faction saves or the saved timer during shutdown.

An empty or ineligible special office applies a persistent stability penalty until
filled, including offices never deliberately assigned and holders who leave. The
per-office setting `positions.spymaster.vacancy-stability-penalty` defaults to 10
points; zero disables it. This is independent of the seven-day replacement unrest.
RPCharacters permanent character death removes that character's Spymaster office,
preserving paid appointment history. Ordinary Minecraft respawns and cancelled
character deaths do not remove the office. Removal is confirmed after the death
event commits, and loaded dead-character assignments are rejected when checked.
Pending founder appointments retain their character identity when an aptitude
save fails; an unrelated character death cannot cancel that pending appointment.
Changed pending identities are saved immediately during office lookups, or once
with the faction's report batch, so they survive an unexpected process exit.
Failed immediate saves restore the previous identity and keep initialization
pending, so a later lookup can retry the binding without consuming an appointment.
