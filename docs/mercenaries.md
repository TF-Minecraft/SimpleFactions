# Mercenary companies

A **mercenary company** is a band of soldiers for hire, hosted and owned by a guild but with its own membership. A company is hired by contract onto one side of a war, fights only where its contract sends it, and is never a belligerent.

**Gameplay lock:** [planning/war-companies/00-index.md](./planning/war-companies/00-index.md) - that file is the decision record and wins over anything here.
**War-side rules** (participants, lives, attendance, loyalty): [wars.md](./wars.md).

---

## How it works

| Piece | Rule |
|-------|------|
| **One company per guild** | The host guild owns it. Company money is guild money; there is no second bank |
| **Leader** | Always the guild leader, and leadership follows when the guild leader changes |
| **Membership** | Invite and accept, not restricted by guild, faction or nationality. A player may be in one company at a time |
| **Slots** | A company owns slots, one soldier each. It cannot hire more players than slots, nor promise more slots in overlapping contracts than it owns |
| **Expansion** | Blocked while any slot sits unfilled, so capacity is built in peacetime rather than conjured when war breaks out. Survives a restart mid-expansion |
| **Upgrades** | Health, mana and mana regen, capped. The buffs apply **only** while the player fights as a hired mercenary, never in the world and never in a battle they joined as a normal faction fighter |
| **Contracts** | Every figure is an absolute denar value written into the contract at signing, never a percentage of anyone's income. Signing is **local**: you must be at the company's home settlement |
| **Wages** | An active share of what the slot earns, plus an optional flat peacetime wage per day. Both take a base and per-player overrides, and both are paid by the host guild |
| **Reputation** | An int from 0 to 100 starting at 50, shown on `/mercenaries` and stamped on the contract book at signing |

**Termination:** the duration elapsing ends a contract normally. Dropping below the promised slots pays the breach refund and takes a large reputation hit. Host guild bankruptcy terminates with no refund, because a bankrupt guild is inert in both directions. A loyalty conflict appearing mid-contract terminates with no refund and no reputation change, since neither party caused it, and the days already served are still paid.

## Money

Six ledger lines move on a mid-contract daily tick. The company host guild reads `MERCENARY_CONTRACT` as income and `REFUND_PAYMENTS` plus `WAGE_PAYMENTS` as expenses; the hiring faction capital reads `MERCENARY_PAYMENTS` as an expense and `REFUNDS` as income. Contract income and refunds are separate lines and are never netted against each other. The soldier sees `WAGES` in `/ledger`.

Contract income is gross-counted business income, so it feeds guild tax and the tribute and reparations bases. Refunds are not gross-counted: taxing compensation for a failure to deliver would make the refund figure mean different things per faction tax rate.

Slot upkeep is billed to the host guild through `MILITARY_UPKEEP` and company upgrade upkeep through `UPGRADES_UPKEEP`, alongside whatever the guild already pays. A bankrupt host guild silently voids every contract it holds, which is why the company screen shows daily burn, expected contract income, net position, and a warning when burn exceeds what the guild earns.

---

## Config

Keys live in three files. Nothing here is a percentage of income by design.

### `config.yml` (read into `Cache` by `ConfigLoader`)

| Key | Default | Role |
|-----|---------|------|
| `mercenary-formation-cost` | `100.0` | One-off charge, taken from the host guild when the charter is requested |
| `mercenary-formation-seconds` | `86400` | Founding time. The company arrives with 1 slot |
| `mercenary-slot-upkeep` | `8.0` | Denars per slot per day, paid by the host guild, not by or to the soldier |
| `mercenary-min-price-per-battle` | `50.0` | Floor on price per slot per battle. A company may charge more |
| `mercenary-min-price-per-day` | `10.0` | Floor on price per slot per day. Charged on battle days too |
| `mercenary-max-contract-days` | `14` | Longest contract duration |
| `mercenary-default-breach-refund` | `500.0` | Pre-fill only; the real figure lives on the contract |
| `dividend-require-previous-tick-membership` | `true` | Blocks payday-joining for guild dividends |

### `regiments.yml`

| Key | Default | Role |
|-----|---------|------|
| `mercenary.expansion-time` | `86400` | Slot expansion time. Blocked while an unfilled slot exists |
| `mercenary.mercenary` | `true` | Keeps the type out of every faction military; only a company clones it |
| `mercenary.upkeep` | `8.0` | Mirror of `mercenary-slot-upkeep`; the ledger reads the `config.yml` key |

### `Guilds/company-upgrades.yml`

| Key | Default | Role |
|-----|---------|------|
| `<upgrade>.upkeep` | `10` | Denars per day **per purchased level** |
| `<upgrade>.max-level` | `10` | Hard cap. Guild upgrades omit this key and stay uncapped |
| `<upgrade>.expansion-time` | `86400` | Purchase time |

Shipped upgrades: `company_health` (+0.5 max health per level), `company_mana` (+1 max mana per level), `company_mana_regen` (+0.1 mana regen per level).

### Not config keys

- **Dividend percent** starts at 0% and is a per-guild field the guild leader sets in the guild GUI, not a YAML key.
- **Reputation** starts at 50, is persisted on the company, and moves only through contract outcomes.
- **Wage terms** are per company: an active percentage and a flat peacetime figure, each with per-player overrides.

### The one non-configurable rule

The **absence refund per slot per battle must be at least the per-slot per-battle price**, validated when the contract is created. A smaller refund would make no-showing more profitable per head than fighting, which inverts the entire incentive, so there is deliberately no key to lower it.

---

## Timing on the test server

All three time keys are **real seconds** on the once-per-second faction tick, so `86400` is 24 hours of wall clock, not a campaign day. Dev overrides and the manual verification matrix are in [dev-config.md](./dev-config.md).

## Worked example

At the config minimums with a 20% active wage base, a soldier earns **2 denars per day** and **10 denars per battle**. A battle day pays both.

---

## Related documentation

| Doc | Topic |
|-----|--------|
| [wars.md](./wars.md) | Participants, shared lives, attendance, loyalty |
| [planning/war-companies/00-index.md](./planning/war-companies/00-index.md) | Gameplay lock (decision record) |
| [planning/war-companies/08-verify.md](./planning/war-companies/08-verify.md) | In-game verification matrix |
| [dev-config.md](./dev-config.md) | Dev-only timings and bypasses |
| [roadmap.md](./roadmap.md) | Shipped vs planned |
