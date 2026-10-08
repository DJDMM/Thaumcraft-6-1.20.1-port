# Void / Eldritch: BETA26 prerequisites and remaining runtime

This is a roadmap, not a claim that the following world mechanics have been ported.
The 0.24 baseline has 78 supported canonical records. Its Void/Eldritch gates remain
closed. The next 0.25 scope is ESSENTIATRANSPORT; it does not complete this roadmap.

The authority is the pinned `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
All eight research JSON files in that JAR were compared byte for byte with the
reference source at commit `954022bb777b7546281fb36df8522f0ba6b43f81` and match.
Aura trigger, rift spawn payment, collapse rewards/effect distance and NBT quirks
below were also checked using the pinned bytecode. Source paths refer to that
reference checkout, under `src/main/java/thaumcraft` unless otherwise stated.

## Original research chain

Observation costs below are raw multiples of 16; Theory costs are raw multiples
of 32. Warp values are the original stage values, before the normal/permanent
split performed by the research system.

| Entry | Original dependencies and payment |
| --- | --- |
| FLUX | Hidden root; first stage requires `f_toomuchflux`, followed by the empty concluding stage |
| FLUXRIFT | `FLUX` and actual `!FluxRift` scan; one empty stage |
| CRYSTALFARMER | `ORE`, `INFUSION`, `!ORECRYSTAL`; Observation Auromancy16/Basics16 and one exact crystal of each primal aspect |
| VISBATTERY | `RECHARGEPEDESTAL`, `CRYSTALFARMER`; Observation Auromancy16/Artifice16 |
| RIFTCLOSER | `FLUXRIFT`, `INFUSION`, **VISBATTERY**; Observation Auromancy16, Theory Alchemy64/Auromancy32 |
| UNLOCKELDRITCH | `CrimsonRites`, `UNLOCKAUROMANCY`, actual `f_VOIDSEED`; Observation Basics16/Eldritch16, warp5 |
| BASEELDRITCH | `METALLURGY`, `UNLOCKELDRITCH`; Theory Basics32/Alchemy32/Eldritch32, warp2 |
| MATSTUDVOID | `MATSTUDTHAUMIUM`, `BASEELDRITCH`, actual `f_MATVOID`; one empty stage |
| VOIDSIPHON | `BASEELDRITCH`, `INFUSION`; Observation Eldritch16/Theory Eldritch32, warp1 |

The study definitions are `assets/thaumcraft/research/basics.json`,
`auromancy.json`, `eldritch.json` and `golemancy.json` in the original JAR.
`ORE` is a one-stage hidden canonical record. `!ORECRYSTAL` is an original scan
fact/addendum trigger, not another canonical book record.

The existing port already handles Crimson Rites curio consumption, including
its actual-warp greater-than-20 gate, and the original Void material IDs and
ordinary plate/nugget/block conversions. Their presence does not establish
natural acquisition or unlock BASEELDRITCH.

## Rift generation and lifecycle

`common/world/aura/AuraThread.java` processes aura about once a second. A chunk
becomes a rift candidate when flux is greater than 75% of its lunar-adjusted
base, with random chance `flux / 500 / 10`. The original pending map holds one
candidate per dimension and later candidates can overwrite earlier ones.
`common/lib/events/ServerEvents.java` processes it on the server, skipping spawn
when `wussMode` is enabled.

`common/entities/EntityFluxRift.java` selects a random X/Z in that chunk and the
precipitation surface. A dimension without skylight searches upward from y10.
Another rift within32 blocks prevents spawn. The size expression is
`sqrt(flux * 3)`, which must exceed5. A successful spawn stores its truncated
integer size but debits the **untruncated float** expression from flux.
Players within the surface block's AABB grown32 receive `f_toomuchflux`.
`common/config/ConfigResearch.java` also gives both `f_toomuchflux` and
`!FluxRift` for an actual rift entity scan.

Stability is clamped to [-100,100] and loses0.2 every120 ticks. Every600 ticks,
offset by entity ID, a rift below size100 can consume `sqrt(size * 2)` flux and
grow by1 unless stability exceeds50. The original entity traces one random
segment through terrain and removes breakable collidable blocks without loot;
nearby noncreative entities receive2 out-of-world damage, and item entities
are removed. These are runtime hazards, not a visual catalogue effect.

The weighted events are Wisp50, Prime Taint Seed10, infectious vis exhaustion20,
Flux Cloud20 and natural collapse1. Some successful events improve stability;
the cloud and collapse cases preserve their original `didit` behavior. A real
rift port must explicitly account for the currently unported Wisp/Prime ecology
and infectious effect rather than silently spawning NoAI visual placeholders.

A collapsing rift snapshots its size, loses1 size per tick and adds either1 vis
or1 flux each tick. At completion it drops `floor(sqrt(snapshotSize))` Void Seeds.
That same integer is the independent Primordial Pearl chance in percent; pearl
damage is4 through7. Natural collapse means initial seeds do not require a
collapser, although acquiring them by waiting for a rare event is dangerous.

Pinned quirks requiring deliberate preservation or a documented adaptation:

- Collapse effect strength uses **distance squared /32**, not distance /32.
- VERY_UNSTABLE falls through Weakness and then normal/temporary Warp effects.
- Stability is written as float but read as integer on load.
- Calling `setCollapse(true)` again replaces the reward size snapshot; the NBT
  load path also does this. Do not accidentally describe corrected behavior as
  original bytecode behavior.

## Paid late devices and materials

`common/config/ConfigRecipes.java` defines:

- Causality Collapser: RIFTCLOSER infusion, instability8, Alienis50/Vitium50;
  central TNT, morphic resonator, vis resonator, redstone blocks2, Alumentum2,
  Nitor2. The throwable uses speed0.8, inaccuracy2 and pitch offset-5. Its impact
  explodes at strength2 and marks nearby rifts within3 for collapse.
- Void Ingot: BASEELDRITCH crucible, one Void Seed plus Metallum10/Vitium5 yields
  one Void Ingot. This crucible path was absent in 0.24.
- Void Siphon: VOIDSIPHON infusion, instability7, Alienis50/Perditio50/Vacuos100/
  Fabrico50; central Void Metal block, Arcane Stone2, complex mechanism, brass
  plates2 and Nether Star.

`common/tiles/crafting/TileVoidSiphon.java` runs every20 enabled ticks, accepts
visible living rifts within8 with size at least2, adds integer sqrt(size) progress,
decreases stability by sqrt(size)/15 and has a1/33 chance to shrink each rift.
Every2000 progress produces one Void Seed in its real one-slot inventory.
Automation extracts on every face and cannot insert. This is a further resource
path once the initial materials and Eldritch studies are genuinely reachable.

The existing Infusion Stabilizer already self-charges fifteen energy in the
port; its rift consumer remains absent in 0.24. Original
`common/tiles/devices/TileStabilizer.java` checks an AABB grown8, spends one energy
for0.125 stability every5 eligible ticks and adds5 delay per treated rift. It
skips a rift whose stability already exceeds50.

## Crimson Rites and the supported original world

Crimson Rites consumption alone does not supply its book. Original
`common/lib/events/EntityEvents.java` adds the book after a real nonfake player
kills a cultist: chance1/4 initially,1/20 after `!CrimsonCultist@2`, or1/50 if the
player already carries it. The port's catalogue cultists/portals are visual.

`common/world/ThaumcraftWorldGenerator.java` naturally spawns Lesser Cultist
Portals on valid surface material in the overworld: after a1/100 mound branch
fails, a separate1/500 portal roll can succeed. Portal spawning and actual
cultist combat/loot must be connected before advertising ordinary Crimson Rites
acquisition and the BASEELDRITCH unlock chain.

The pinned BETA26 JAR contains no Outer Lands WorldProvider, dimension/maze
generator, Eldritch altar or ring classes/active dungeon chain. Eldritch biome
and boss assets/entities do exist. Do not infer a TC4-style portal/dungeon from
those remaining assets or import older-version mechanics as original TC6.

## Coherent implementation order

1. Add the ore scan facts/ORE, actual Crystal Farmer crafting/growth and Vis
   Battery storage. This closes the genuine RIFTCLOSER dependency and adds useful
   gameplay without opening unavailable Eldritch studies.
2. Connect actual Flux Rift generation/hazards/rewards, stabilizer treatment and
   real collapser crafting/projectile; finish FLUX/FLUXRIFT/RIFTCLOSER.
3. Implement natural Lesser Portal/cultist ecology and Crimson Rites loot, then
   UNLOCKELDRITCH/BASEELDRITCH, Void metallurgy and MATSTUDVOID factory gates.
4. Add the working Void Siphon and remaining recipes reached by those materials.

Each stage needs ordinary paid gameplay tests and accurate scope statements.
Saved scan facts, catalogue items, display recipes or supplied finished outputs
must not substitute for its natural resource path.
