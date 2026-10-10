# Flux Rift, stabilizer and Causality Collapser: BETA26 contract

This is a behavioral audit of **Thaumcraft 6.1.BETA26 for Minecraft 1.12.2**,
made on 2026-10-10. It specifies the original release and the integration work
needed by the Forge 1.20.1 port. It is not evidence that any new runtime or test
already passes. The current baseline has 82 complete canonical records; the
0.26.1 scanner starts FLUX at stage 1 but does not finish its rift chain.

Authority: local `work/Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
The source checkout is `work/thaumcraft6-reference`, commit
`954022bb777b7546281fb36df8522f0ba6b43f81`. Both identities were checked during
this audit. The released JAR takes precedence over guides and decompilation.
No TC4 nodes, tainted biome, Outer Lands maze or altar portal is implied here.

## Sources and bytecode checks

All source paths below are relative to `src/main/java/thaumcraft` in the pinned
reference checkout:

- `common/entities/EntityFluxRift.java`: geometry, synchronized state, spawning,
  update order, events, collapse rewards and persistence.
- `common/world/aura/AuraThread.java` and `common/lib/events/ServerEvents.java`:
  one-second candidate generation and server-thread pending-candidate dispatch.
- `common/tiles/devices/TileStabilizer.java`: charge, exact treatment and delay.
- `common/items/consumables/ItemCausalityCollapser.java` and
  `common/entities/projectile/EntityCausalityCollapser.java`: actual item debit,
  throw, explosion and repeated collapse request.
- `common/lib/utils/EntityUtils.java` and `RandomItemChooser.java`: spatial query
  semantics and event distribution.
- `common/config/ConfigResearch.java`, `ConfigRecipes.java` and original
  `assets/thaumcraft/research/{basics,auromancy}.json`: scan facts and paid gates.
- `client/renderers/entity/RenderFluxRift.java`,
  `common/lib/potions/PotionInfectiousVisExhaust.java`,
  `api/potions/PotionFluxTaint.java` and `api/potions/PotionVisExhaust.java`:
  visuals and effect consumers.

`javap -c -p` on the pinned JAR confirmed all five main classes above. Temporary
bytecode transcripts are retained under `work/rift-audit`. Particularly important
branches are `EntityFluxRift.setCollapse` offsets 0..12, read-NBT 30..49,
createRift 201..246, update 403..706, completeCollapse 167..452;
`TileStabilizer.update` 105..171 and tryAddStability 121..174; the collapser's
onImpact 10..96; and `AuraThread.processAuraChunk` 572..639. These offsets refer
to their own methods, not line numbers or names from another version.

The vanilla 1.12.2 client JAR and `joined.srg` already retained under
`work/vanilla-1.12.2-audit` additionally confirm NBTTagFloat.getInt:
`gb.e()` invokes `rk.d(float)`, mapped to MathHelper.floor. Consequently reading
the original float Stability through getInteger **floors negative values**;
for example -0.2 reloads as -1, not 0. Positive 0.2 reloads as 0.

## Aura candidate and successful spawn

The original aura worker wakes at roughly 1000 milliseconds, avoiding a second
pass when world time is unchanged. It processes the aura after local diffusion
and regeneration. Lunar maximum multipliers, phases 0..7, are
`1.15, 1.05, 1, .95, .85, .95, 1, 1.05`.

For each processed chunk, let B be base times its lunar multiplier and F the
updated flux. A candidate requires **F > B * .75**, strictly, and
`random.nextFloat() < F / 500f / 10f`. This is not a constant 10% roll and the
original does not clamp the probability separately. The pending map holds one
BlockPos `(chunkX*16, 0, chunkZ*16)` per dimension. Later successful candidates
overwrite earlier ones. World END processing consumes the pending candidate
every 20 world-tick-counter ticks, starting counter 0; wussMode skips spawning
but still removes the candidate. A failed attempt is not retained for retry.

`createRift` then:

1. Adds independent random offsets 0..15 to chunk-origin X/Z. It uses the
   precipitation surface. In a dimension without skylight it starts at y10
   and searches upward in random steps 1..5 until air; aborts above actualHeight-5.
   The final surface must be strictly below actualHeight-4.
2. Rejects when another rift intersects the point-centred AABB grown 32.
   `EntityUtils.getEntitiesInRange` is an **AABB intersection query**, without
   a radial distance filter. A diagonally distant or long rift can qualify.
3. Creates a seed from world RNG, centres the rift at surface +(.5,.5,.5),
   chooses yaw 0..359 and pitch 0, and reads flux from that surface chunk.
4. Computes `double size = sqrt(F * 3f)` with the multiplication done as float.
   It must be **greater than 5**, and the world spawn callback must succeed.
5. Only after successful spawn sets integer size `(int)size` and drains
   **`(float)size` flux**, not the truncated integer. No payment or research fact
   is awarded for a rejected spawn.
6. Gives `f_toomuchflux` once to players intersecting the surface block's full
   one-block AABB grown 32, with actionbar `tc.fluxevent.3`. This differs from the
   point-centred rift spacing query. It does not directly give `!FluxRift`.

Only an actual rift entity scan gives both `f_toomuchflux` and `!FluxRift`.
Merely seeing the renderer, obtaining the item, hovering or checking aura must
not award either scan fact.

## Entity state, shape and update order

Synchronized defaults are Seed=0, Size=5, Stability=0f and Collapse=false.
Stability clamps to [-100,100]. Stability classes are:

| Float value | Class |
| --- | --- |
| greater than 50 | VERY_STABLE |
| 0 through 50, inclusive | STABLE |
| greater than -25 and less than 0 | UNSTABLE |
| -25 or below | VERY_UNSTABLE |

The entity does not move through `move`, burn, render on fire, or implement
ordinary living-entity health/combat. It derives its bounding box from its actual
seeded spine; width=max(X span,Z span), height=Y span and Y offset=-height/2.
The original maxima start with Double.MIN_VALUE, not negative infinity. The
original seed setter alone does not rebuild geometry; size/position changes do.
Any corrected setter refresh or more defensive bounds must be documented.

Shape uses `new java.util.Random(seed)` and a normalized three-Gaussian starting
direction, its opposite, and two positions starting at zero. Steps are
`ceil(size / 3f)`, initial girth=size/300f and decrement=girth/steps. Each step
subtracts girth first, applies independent Gaussian*.33 pitch and yaw rotations
to the right branch, advances .2 and appends, then performs the equivalent left
branch and prepends. This **interleaved RNG draw order** is part of the shape.
Finally append/prepend an extra .1 guide endpoint with width zero. Positive size
therefore gives `2*ceil(size/3f)+2` points. The guide points also participate in
the original hazard segment selection.

After the base entity tick and any size rebuild, server update order is:

1. If seed is zero, choose a new entity RNG integer.
2. Process one random adjacent spine segment's terrain and entity hazard.
3. If point count <3 and not already collapsing, request collapse.
4. If collapsing, decrement size, add one aura unit, maybe make a small explosion,
   then complete and return when the new size <=1.
5. Every 120 entity ticks, subtract .2 stability.
6. At `tickCount % 600 == entityId % 600`, evaluate paid growth and then event.
7. Every 300 ticks while still alive, play evilportal with volume
   `.15000000596046448 + gaussian*.066` and pitch `.75 + gaussian*.1`.

Growth reads flux, computes `sqrt(size * 2)` with integer multiplication first,
and requires available flux >= that double amount, size<100 and class other than
VERY_STABLE. It drains the float amount and increments size by one. **100 is a
growth ceiling, not an original universal size clamp**: spawning or external
NBT can begin above 100. Collapsing rifts still pass through periodic growth and
event checks until the terminal return; no `!collapse` guard exists here.

## Terrain and entity hazards

Every server tick chooses an index in `[0,pointCount-2]`. It raytraces from point
index to index+1, offset by entity position, with liquids not stopping the trace.
For a returned nonair block whose hardness>=0 and canCollideCheck(state,false),
it sends break event 2001 then sets air. There is no loot/XP harvesting call and
no original mobGriefing condition. Ordinary modern block removal can still have
native block-entity replacement behavior; do not assert all container contents
are destroyed without checking that behavior.

Entity hazard is **a point-centred AABB grown .5 at the FIRST endpoint**, not a
capsule along the whole segment and not .5 Euclidean distance. Exclude this rift
and already-dead entities. Creative players are skipped; other entities receive
2 OUT_OF_WORLD damage, then ItemEntity is explicitly killed even when damage
returns false. The original catches exceptions around each damage/removal call.
This runs before collapse rewards so a terminal reward is not immediately eaten
by the same rift. Unbreakable blocks and liquids must not become ordinary loot.

## Weighted unstable events

At the same 600-tick phase **after possible growth**, if stability<0 and
`nextInt(1000) < abs(stability)+currentSize`, choose exactly one weighted event.
`RandomItemChooser` uses Math.random()*sumWeight (sum=101), not entity RNG for
the chooser. Replacing that global RNG with an injected modern RNG is an
explicit determinism adaptation, not the original draw stream.

| Event | Weight | Actual effect | Stability addition |
| --- | --- | --- | --- |
| Wisp | 50 | Gaussian*5 offset each axis; 1/5 world-RNG Flux type; requires getCanSpawnHere and successful spawn | +5 only on success |
| Prime Taint Seed | 10 | Reject near existing taint seed, no reroll. Cast Gaussian-offset coordinates to int (truncate, not floor), add .5 X/Z. Requires valid/successful spawn; boost=currentSize, pollute integer(size/2), kill rift | +0 |
| Infectious vis exhaustion | 20 | All living targets intersecting rift's own bounding box grown16, 3000 ticks, amplifier2; clear instance curatives; player actionbar tc.fluxevent.2 | +10 if any target, even if an individual addPotionEffect throws |
| Flux Cloud | 20 | Closest player within16; root targets that same player at .5; cloud radius inclusive1..3, duration inclusive min(size/2,30)..min(size,120), then Flux effect; free FocusEngine cast | **No addition**, original didit remains false |
| Natural collapse | 1 | setCollapse(true) | No addition |

The Prime near-seed restriction is the only event with nearTaintAllowed=false.
Both cloud and collapse keep didit=false; the cloud's configured cost10 does
not actually restore stability. A failed selected event does not select another.

Infectious effect pulses every40 remaining-duration ticks. It searches the
infected target's bounding box grown4. For each living target without the
infectious effect, amplifier>0 spreads infectious 6000 ticks at amplifier-1;
otherwise spreads ordinary VisExhaust 6000 ticks/amplifier0. The lower-strength
copies use default curatives; only the initial rift instance explicitly clears
them. Ordinary VisExhaust's potion tick is empty; its gameplay cost penalty is
a separate caster consumer. The preimplementation0.26.1 ordinary marker and
absent infection did not establish this event. Active0.27 now has the native
infectious pulse and caster-cost consumer; see RIFT-EVENTS-WISP-BETA26-AUDIT.md
and the final fresh evidence in VALIDATION.md.

## Collapse, effects and persistence quirks

Every `setCollapse(true)` snapshots currentSize to MaxSize, even if Collapse was
already true. False does not reset MaxSize. Each collapse tick decrements size
by1, then chooses +1 Vis or +1 Flux with nextBoolean. It independently has a
1/10 roll for an explosion at three Gaussian*2 offsets with strength
nextFloat()/2 and terrain destruction disabled. That does not make it harmless
to living entities. Size<=1 completes after that tick's aura addition.

Completion computes `q=(int)sqrt(MaxSize)`: q separate Void Seed drops; one
independent `nextInt(100)<q` Pearl roll, with original pearl metadata4..7. The
chance is q percent, not a guaranteed Pearl or a q/100 chance for each seed.
At ordinary size100 q=10. Pearl is rolled before seeds. It sends the black Bamf
packet within64, applies living effects, and then kills the entity.

Effects query a point AABB grown32, but severity uses **distance squared /32**:
`w=(int)((1-distanceSquared/32)*factor)` and only w>0 is applied. The practical
effect radius is approximately sqrt(32), not32, and integer rounding reduces
the positive range differently for each factor.

- VERY_UNSTABLE adds FluxTaint for w*20 ticks, factor120, then falls through.
- UNSTABLE adds Weakness for w*20 ticks, factor300, then falls through.
- STABLE adds factor25 NORMAL Warp **and the same amount TEMPORARY Warp** to
  players; it does not grant permanent Warp. Creative players are not explicitly
  exempted by this collapse loop.
- VERY_STABLE gets none of these completion effects, but still gets rewards.

FluxTaint's original consumer heals tainted/champion-type13 targets by1, leaves
undead targets unaffected and damages other targets by1 with TC taint damage
every `40 >> amplifier` ticks (or every tick when nonpositive). The baseline
port's simple magic damage awaits those champion/tainted ecology branches.

NBT writes MaxSize/int, RiftSize/int, RiftSeed/int, Stability/**float** and
collapse/boolean. Reads MaxSize, then size, then seed, then Stability through
**getInteger**, then calls setCollapse. Therefore a reloaded collapsing entity
replaces saved MaxSize with its smaller current size and can pay fewer seeds;
fractional negative Stability floors. These quirks are confirmed bytecode.
Transient age/last-size and geometry are not stored in custom fields.

## Stabilizer payment and collapser

Stabilizer increments its local ticks first. While energy<15 it gains1 every20
ticks and pollutes .25 Flux, then marks/syncs and updates neighbours. No aura
Vis debit, redstone gate, direction restriction or line-of-sight test exists.

If energy>0, delay<=0 and ticks%5==0, query rifts intersecting the full block AABB
grown8. For each not-dead rift whose stability class is not VERY_STABLE, spend
**one energy** through mitigate(1), add **.125 stability**, and add5 delay. It
can treat multiple rifts in that same pass until energy reaches zero. The delay
does not stop the current loop; it is decremented once after the pass. A value
exactly50 still takes one treatment to50.125. Collapsing rifts are not excluded.
Energy is persisted/synced; delay/ticks are transient. Original readSyncNBT caps
only above15; a lower-bound clamp is modern hardening.

Causality Collapser stacks to16, has no durability and no research check on use.
Ordinary right click consumes1 except creative, plays egg throw volume.3 and
pitch `.4/(random*.4+.8)`, then server-spawns the original throwable. The throw
uses pitch offset-5, speed.8 and inaccuracy2; its vector shoot override forces
speed.8 even if another caller supplies a different velocity. Remaining gravity,
drag/collision/owner behavior is inherited from the vanilla throwable.

On any server impact, first creates a strength2 **terrain-damaging** explosion,
then queries rifts intersecting a point AABB grown3, calls setCollapse(true) for
all of them including already-collapsing ones, and removes itself. The boolean
in this 1.12 createExplosion overload is terrain destruction, not a flaming
explosion setting. An impact guard on an already removed projectile is modern
lifecycle hardening; native impact must never be invoked twice for rewards.

Original paid recipe: RIFTCLOSER infusion, instability8, Alienis50/Vitium50,
central TNT; Morphic Resonator, Redstone Block, Alumentum, any Nitor,
Vis Resonator, Redstone Block, Alumentum, any Nitor. It produces one collapser.
RIFTCLOSER requires FLUXRIFT, INFUSION and VISBATTERY, with raw Observation
Auromancy16 and Theory Alchemy64/Auromancy32. FLUX requires f_toomuchflux plus
its empty final chapter; FLUXRIFT requires FLUX and !FluxRift and has one empty
stage. Reference displays and starting FLUX are not completed paid progression.

## Port integration and honest scope

- Replace catalogue `flux_rift` and `causality_collapser` entity factories in
  `catalog/entities/VisualEntitiesModule` while preserving registry IDs;
  currently both are VisualEffectEntity. Select operational renderers explicitly.
  Existing catalogue saves carry VisualOnly and neutral size/seed data; choose
  and document how they migrate without treating a supplied visual as a newly
  generated paid survival rift.
- Move/share the existing `CatalogEffectGeometry.riftPath` math into a common
  nonclient geometry class so server hazard, bounds and client spine agree.
  Current six-sided polycone and four-pass radii are retained reference math;
  the modern end-portal shader and omitted original goggles depth override are
  explicit material/visual adaptations. A render-only mesh cannot prove hazards.
- Extend existing `world/aura/AuraManager` loaded-only server-thread tick after
  its diffusion/regeneration; retain candidate replacement semantics. Mapping
  precipitation height to modern heightmaps, 20 server ticks instead of a worker
  wall-clock second, modern build bounds and unloaded-chunk rejection are
  adaptations to state explicitly. Do not introduce forced chunk loading.
- Connect the existing InfusionStabilizerBlockEntity charge/payment path to
  actual operational rifts without replacing matrix/inlay consumer semantics.
- Add both facts only through the actual scanner commit hook in
  `research/AuromancyProgressionEvents`; permit scan handlers independent of
  generic positive aspects. Complete the original FLUX/FLUXRIFT/RIFTCLOSER
  stages with the existing strict ResearchProgression resource transaction.
- Register the actual collapser item class at its existing catalogue item ID,
  original infusion recipe in native recipe data and actual projectile impact.
  Existing Void Seed/Pearl item IDs are available, but their supplied creative
  stacks or display recipes are not natural acquisition evidence.
- Wisp AI, Prime Seed/taint ecology, infectious effect and free Cloud execution
  are additional real consumers. If any remain unavailable, state that the event
  branch remains incomplete; do not substitute NoAI catalogue mobs and call
  all five weighted events implemented. Reuse paid/fact/Warp APIs with their
  server lifecycle guards; read-only snapshot paths must remain mutation-free.

## Meaningful validation cases

Suggested cases exercise boundaries, native consumers and conservation:

1. Lunar strict75% candidate boundary, original variable probability, two
   candidates replacing one dimension slot, wussMode consumption and no retry.
2. Actual loaded spawn success at fractional sqrt size: truncated stored size
   and exact untruncated Flux debit; failure callback/nearby rift/top-height
   rejection leaves Flux/facts unchanged. Distinguish box corners from radius.
3. Actual nearby player f_toomuchflux once; distant player none; real scanner
   commit gives both facts once, hover neither; strict paid RIFTCLOSER gates.
4. Reproducible shared positive-size spine, widths and bounds on both sides,
   opposite endpoints and unchanged RNG draw order. No client class required
   by a dedicated-server load.
5. Native tick destroys breakable traced block without stone loot, preserves
   unbreakable/liquid, applies OUT_OF_WORLD2 at first-point AABB, creative
   exemption and ItemEntity removal even after rejected damage.
6. 120/600 phase behavior, exact growth sqrt debit,50/50.125 and size99/100
   boundaries; demonstrate the original collapsing growth/event ordering.
7. Successful/failed event branches, taint-near rejection without reroll,
   infectious3000/amp2 and spread6000/amp1, cloud actual lack of+10 stability,
   and native free spell event rather than a placeholder.
8. Real stabilizer ticks: one rift consumes1/+ .125; multiple in one pass share
   energy and accumulate delay; exact50 receives treatment; no LOS/facing gate,
   unrelated matrix consumer still works and no overdraft occurs.
9. Real item use consumes exactly1/creative0, native projectile travels and
   impacts; strength2 explosion, box-grown3 multiple targets, no repeated impact
   after removal. Real infusion consumes central/eightcomponents/100essentia.
10. Native collapse completion pays floor(sqrt(snapshot)) seeds and one Pearl
    chance, adds exactly one aura unit per collapse tick, applies squared-distance
    fallthrough effects/normal+temporary Warp, and cannot pay a removed entity
    twice. Capture item drop totals before unrelated hazards consume them.
11. Save/reload explicitly demonstrates negative fractional Stability flooring
    and collapse MaxSize overwrite; repeat collapser requests overwrite too.
    If either is corrected, assert the documented adaptation instead.
12. Fresh server suite and an owned hidden-desktop client show actual generated
    rift, scan/paid book progression, paid collapser crafting/throw/collapse,
    real reward pickup and changing original shape/stability/collapse visuals.
    Supplied predecessor research/aura/ingredients/deterministic RNG are named
    fixtures; never claim full survival or independent multiplayer from them.


### Outline erosion parity

BETA26 `rayTraceBlocks(v1,v2,false)` delegates with ignoreBlockWithoutBoundingBox=false. Selectable BlockBush/torches/crystals can be hit even with null physical collision. Modern `OUTLINE` plus `Fluid.NONE` and nonempty outline preserves this; `COLLIDER` would incorrectly spare these blocks. Negative-hardness bedrock and fluids remain. Native segment fixtures assert actual outline hits/erasure and no ordinary drops, with liquid/bedrock controls.
