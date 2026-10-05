# Registered control seal actions — TC6 6.1.BETA26

This audit concerns the pinned original release `Thaumcraft-1.12.2-6.1.BETA26.jar`,
SHA-256 `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
The Java reference checkout is commit `954022bb777b7546281fb36df8522f0ba6b43f81`.
TC4/TC5 research puzzles, unofficial addons and unregistered seal proposals are excluded.
Implementation/test definitions are present for 0.23; current QA results belong only
in `VALIDATION.md` after the integrated server/client runs actually complete.

## Registration and actions

`ConfigItems.initSeals`, lines 310–325, registers **16** behavior types in this order.
Metadata zero is the blank item, not a seventeenth action. Acquired seal items may
be placed without a research check in the release; research gates their recipes.

| Metadata | Type | Action and original restrictions |
| --- | --- | --- |
| 1 | pickup | Every five ticks, one grounded item entity without pickup delay; one filter; refuses CLUMSY; remainder stays on the entity. |
| 2 | pickup_advanced | Pickup with nine filters/configurable metadata, NBT, ore and namespace matching; requires SMART; refuses CLUMSY. |
| 3 | fill | Every 20 ticks produces an inventory/world delivery ticket; one filter, optional whitelist target count; refuses CLUMSY. |
| 4 | fill_advanced | Nine filters, existing-stack-only switch and the same target count; requires SMART and refuses CLUMSY. |
| 5 | empty | Every 20 ticks chooses a sided inventory stack; ticket lifespan five; one filter; refuses CLUMSY. |
| 6 | empty_advanced | Nine filters, cyclic whitelist selection and leave-one; requires SMART and refuses CLUMSY. |
| 7 | harvest | Grown crop harvesting, actual seed replant and optional provisioning; requires DEFT + SMART. |
| 8 | butcher | Checks every 200 ticks; chooses one adult, untamed animal only when three valid animals of its class exist; requires FIGHTER + SMART. |
| 9 | guard | Checks every 20 ticks and assigns a real attack target; hostile mobs by default; requires FIGHTER. |
| 10 | guard_advanced | Adds configurable animal/player target families; player family respects server PVP; requires FIGHTER + SMART. |
| 11 | lumber | One area position per tick; repeatedly harvests the farthest connected log of the same species; requires BREAKER + SMART. |
| 12 | breaker | One area position per tick, filter on block item; work counter hardness × 10 decreases by 21; requires BREAKER. |
| 13 | use | Every five ticks, real FakePlayer block interaction with carried items, left/right, air, empty hand, sneaking and provision switches; requires DEFT + SMART. |
| 14 | provider | Nine filters; fulfills requests inside squared distance 4096; fetch followed by a carrier-specific destination ticket; single/leave-one options; refuses CLUMSY. |
| 15 | stock | Nine whitelist target counts; compares actual inventory and requests its missing items, rather than producing items; refuses CLUMSY. |
| 16 | breaker_advanced | Nine filters, optional Silk Touch; counter decreases by seven when silky; requires BREAKER + SMART. |

Normal item filters default to blacklist=true, exact metadata and NBT=true, ore
and namespace=false. Stock is always a whitelist. Basic Empty/Fill/Pickup retain
the original fixed options even though their advanced subclasses expose toggles.
All filter snapshots and inventory payments use copies. World destinations retain
actual dropped item stacks; manufactured/provided inputs are never replaced by
count-only marker tasks. Traits, home, color, ownership, stopped redstone and task
reservation are checked by the shared server service before behavior execution.

## Confirmed release details

* Pinned `SealBreaker.isValidBlock` rejects a whitelist block if **any** nonempty
  filter does not match. This AND whitelist is intentionally retained; ordinary
  item seal whitelists use OR. Bytecode is saved in ignored `work/seal-breaker-023-javap.txt`.
* Pinned `SealButcher.tickSeal` increments its herd count only for valid adult,
  untamed animals. The mirror's apparent unconditional loop increment is a
  decompiler artifact; bytecode is saved in `work/seal-butcher-023-javap.txt`.
* Pinned `SealUse.onTaskCompletion` removes its selected carried stack before
  applying the empty-hand override. If empty hand is enabled while it has a
  selected stack, that stack is discarded. This destructive release quirk is
  retained and covered explicitly, rather than silently refunded. Bytecode is
  saved in `work/seal-use-023-javap.txt`.
* A Fill task can be generated again as soon as an old task is reserved. A
  provider fetch pays its source before the golem travels; destination tasks are
  tied to that carrier. Seal-target requests leave the fetched items carried for
  that seal's later work; inventory/entity requests receive delivery tasks.
* Breaker uses the actual Forge break event and block/BE loot/XP without tool
  wear. The counter and cracked block feedback continue at the scheduler's
  ten-tick work interval; Silk Touch costs seven instead of 21 per work step.
* Lumber preserves the original upward-first reach-two walk, strict increase in
  distance, X/Z ±24 and Y ±48 boundary, and species/legacy damageDropped identity.
  Axis does not split one species into separate trees. Cancellation stops the
  ticket, and the original base remains until outer logs have been removed.
* Harvest excludes stems. Original ModConfig explicitly registers melon/pumpkin
  fruits and mature Nether Wart; those are included. Vanilla mature wheat, carrots,
  potatoes, beetroot, Nether Wart and cocoa are mapped to their actual seeds; cactus/cane
  only harvest above a block of their same species. Replant descriptors persist;
  live task identifiers, counters and inventory selection caches do not.
* Crop harvesting never manufactures a seed. A later replant needs one real
  carried seed, uses its actual item callback, and can request one from a provider.
  Farmland which reverted to dirt/grass is hoed by an actual FakePlayer hoe use.

## Exact research and recipes

23 canonical paths are added to the previous 51, total **74**:
CONTROLSEALS, SEALCOLLECT, SEALSTORE, SEALEMPTY, SEALPROVIDE, SEALSTOCK,
SEALGUARD, SEALBUTCHER, SEALUSE, SEALHARVEST, SEALBREAK, SEALLUMBER,
MINDBIOTHAUMIC, MATSTUDIRON, MATSTUDCLAY, MATSTUDBRASS, MATSTUDTHAUMIUM,
GOLEMBREAKER, GOLEMCOMBATADV, GOLEMDIRECT, GOLEMLOGISTICS, GOLEMCLIMBER,
GOLEMVISION. The archived canonical JSON, parents, facts, stages, item requirements
and payments are unchanged. Flyer's LEVITATOR parent, Void's BASEELDRITCH parent
and Jar Brain remain unsupported, so their reference data cannot grant manufacture.

MINDCLOCKWORK's actual `siblings` list contains CONTROLSEALS and MATSTUDWOOD.
Original `ResearchManager.progressResearch` recursively calls `completeResearch`
for eligible siblings. Completing Mind therefore completes CONTROLSEALS →
SEALCOLLECT → SEALSTORE, all original one-empty-stage records, and awards 20 XP
including the Mind advancement. It does not freely complete Empty/Guard/Biothaumic
or any paid seal. At the initial Mind start, CONTROLSEALS' strict Mind parent is
still incomplete; only the wooden study can be revealed then.

All nine paid seal studies cost exactly 32 raw Golemancy Theory. Empty requires
`!vacuos`, Provide `!desiderium`, Guard `!mortuus`, Butcher `!bestia`, Use
`!instrumentum`, Harvest/Lumber `!herba`, Break `!perditio`; Stock has no aspect
fact. Biothaumic requires `f_BRAIN`, 32 Theory Golemancy and 16 Observation Artifice;
the final stage has original warp three. Logistics keeps completed Provider/Direct
parents and pays 16 Observation Basics + 16 Observation Golemancy, a filled map
and ender eye. Material/part studies retain their real scans and physical items.

The existing four exact infusion recipes — MindBiothaumic, SealHarvest,
SealButcher and SealBreak — are retained. Thirteen new crucible definitions are:

| Original recipe | Catalyst | Aspects | Gate |
| --- | --- | --- | --- |
| SealCollect | blank | Desiderium 10 | SEALCOLLECT |
| SealCollectAdv | pickup | Sensus 10 + Cognitio 10 | SEALCOLLECT && MINDBIOTHAUMIC |
| SealStore | blank | Aversio 10 | SEALSTORE |
| SealStoreAdv | fill | Sensus 10 + Cognitio 10 | SEALSTORE && MINDBIOTHAUMIC |
| SealEmpty | blank | Vacuos 10 | SEALEMPTY |
| SealEmptyAdv | empty | Sensus 10 + Cognitio 10 | SEALEMPTY && MINDBIOTHAUMIC |
| SealProvide | advanced empty | Permutatio 10 + Desiderium 10 | SEALPROVIDE |
| SealStock | fill | Cognitio 10 + Desiderium 10 | SEALSTOCK |
| SealGuard | blank | Aversio 20 + Praemunio 20 | SEALGUARD |
| SealGuardAdv | guard | Sensus 20 + Cognitio 20 | SEALGUARD && MINDBIOTHAUMIC |
| SealLumber | breaker | Herba 40 + Sensus 20 | SEALLUMBER |
| SealUse | blank | Fabrico 20 + Sensus 10 + Cognitio 20 | SEALUSE |
| SealBreakAdv | breaker | Sensus 10 + Cognitio 10 + Instrumentum 20 | SEALBREAK && MINDBIOTHAUMIC |

Blank: shapeless clay ball + tallow + red dye + any original Nitor, 20 vis and
Aer one, produces three, gate CONTROLSEALS. The modern `forge:nitor` tag includes
all original sixteen flattened colors and the existing yellow alias. Bell is an
ordinary ungated shaped recipe ` QQ / QQ /S  ` with four quartz gems and one
wooden stick. Aggression: SEALGUARD, 50 vis, Ignis one, ` R /RTR/PGP` with four
glass panes, blaze powder, two brass plates and simple mechanism. Vision:
GOLEMVISION, 50 vis, Aqua one, `B B/E E/PGP` with two bottles, two fermented eyes,
two brass plates and simple mechanism.

## Explicit modern adaptations and boundaries

Forge item tags replace OreDictionary; flattened metadata families are resolved
by the shared seal filter. Sided inventory endpoints must support mutable slot
snapshots so callback failure can roll back the exact paid stacks. Endpoints over
256 slots and unsupported nonmutable endpoints fail closed. Capability identity,
simulations, full changed-slot contents and reentry are checked. Loose filter
matches select actual NBT stack families independently instead of synthesizing
one new metadata/NBT stack from different items. Original world drops remain
actual entities, and use/break/hoe/plant respect modern Forge callbacks.

Per-golem FakePlayer profiles, bounded loaded-only operations, protection of
GameMaster blocks, invalid NBT rejection, carrier-specific delivery reservation,
and safe crack cleanup are explicit hardening. Crop recognition currently maps
the original vanilla crop registry rather than importing removed 1.12 IGrowable
or an invented broad 1.20 crop API. Modern native crop items perform actual
placement/consumption. Custom third-party clickable/standard/stacked crops need
a modern crop registration adapter before claiming addon parity.

Research support does not prove natural acquisition of currently unfinished
world-mob loot. Server fixtures explicitly supply predecessor research, scan facts,
ingredients and manufactured golem properties. The integrated client separately
checks real C2S menus, paid manufacture/placement and visible seal/task actions.
These are not a complete survival playthrough or independent multiplayer proof.
