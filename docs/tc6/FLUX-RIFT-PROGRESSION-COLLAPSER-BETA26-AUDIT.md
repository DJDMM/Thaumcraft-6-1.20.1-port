# Flux studies and Causality Collapser: released BETA26 contract

Authority is the local `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`, and
the matching reference checkout at `954022bb777b7546281fb36df8522f0ba6b43f81`.
The original item and projectile were also read through `javap -c -p`; the
existing recipe was compared with `ConfigRecipes.initInfusion` and the pinned
`basics.json` study data. No TC4 research puzzle or Outer Lands prerequisite is used.

## Original discoveries and payments

The actual held-Thaumometer twentieth server tick keeps its existing strict
`flux > vis || flux > integer(base/3)` trigger. It opens hidden FLUX at stage1
once and gives5 XP. Adding support for its later chapters does not turn that
discovery into completion. An empty forged book request cannot start this hidden
root, even if the caller already owns a rift scan fact.

`ConfigResearch` registers two independent `ScanEntity` handlers for an actual
`EntityFluxRift`: `f_toomuchflux` and `!FluxRift`. The port's server scanner now
recognizes its working `FluxRiftEntity` class. Neither looking at it, holding a
collapser, reading a recipe nor requesting an arbitrary entity ID supplies the
proofs. They remain repeat-safe facts and award no invented XP or observations.
This works even though the original rift has no generic aspect reward. Natural
rift-generation nearby-player `f_toomuchflux` is another original path, managed
by the rift lifecycle implementation.

| Canonical entry | Exact server progression |
| --- | --- |
| FLUX | Existing aura event opens1 for5 XP; stage1 needs `f_toomuchflux`, then skips the empty conclusion to3 for another5 XP |
| FLUXRIFT | Completed FLUX and actual `!FluxRift`; its one empty stage completes to2 for5 XP |
| RIFTCLOSER | Completed FLUXRIFT, INFUSION and VISBATTERY; start1 for5 XP, pay16 raw Observation Auromancy +64 raw Theory Alchemy +32 raw Theory Auromancy, then skip empty conclusion to3 for another5 XP |

Expected-stage validation, actual held-book validation, server-thread/lifecycle
guards, atomic insufficient-payment rejection and strict completed parents use
the shared research path. The canonical implemented set is85, preserving82
earlier entries. This does not implement UNLOCKELDRITCH, BASEELDRITCH, MATSTUDVOID,
VOIDSIPHON, Crimson ecology or TC4 dungeon progression.

## Physical recipe and throwable

The existing `infusion/causalitycollapser` definition is unchanged:

- bare **started** `RIFTCLOSER` recipe gate, as original `isResearchKnown`;
- instability8 and50 Alienis +50 Vitium;
- central TNT and eight separate components in original order: morphic resonator,
  redstone block, Alumentum, any original Nitor color, vis resonator, redstone block,
  Alumentum, any original Nitor color;
- one Causality Collapser output, stack limit16 and no durability.

The working item retains the existing item ID/model. Actual use creates the
native throwable with owner, eye-height minus.1 position, speed.8, inaccuracy2
and pitch offset-5. The projectile's `shoot` override forces speed.8 even if
another caller requests a different velocity. Survival consumes one; creative
does not. Original TC6 adds neither a use-time research gate nor an item cooldown.
Modern hardening rejects dead/spectator/wrong-hand/wrong-level/off-thread actions
and retains the item if a Forge spawn callback rejects creation.

Native collision calls an explosion of strength2 with terrain damage, then
`setCollapse(true)` on working rifts whose bounding boxes intersect the cube
grown3 around the projectile. Pinned `EntityUtils.getEntitiesInRange` has no
spherical center-distance filter; a corner or a long rift spine can qualify.
The projectile is removed afterwards and an already removed impact cannot
explode or reset collapse snapshots again. Rift reward/NBT/snapshot quirks belong
to the separate lifecycle implementation and are not replaced by the collapser.

Minecraft1.20.1 native throwable motion, Forge collision/explosion callbacks,
spawn synchronization and TNT explosion interaction replace their1.12 APIs.
In particular native modern launch spread is not claimed to reproduce the old
Gaussian random sample bit for bit. The original renderer has no projectile
mesh: native interpolated Flame/EndRod trails are an explicit replacement for
the legacy FXDispatcher emitters. Ordinary item art is unchanged.

## Verification scope

New server tests exercise actual scanner targeting/commit versus pure hover,
both facts and repeats/reload, range/removed/held-item negatives, original aura
discovery followed by real book requests, exact knowledge payments, incomplete
parents and stale/no-book/spectator rejection. Throwable tests cover two paid
immediate uses, creative/offhand, native owner/motion save-load, spawn veto,
real native flight into a bedrock wall and strength2 explosion, plus the actual
impact callback's cube corners/distant rift/replay behavior.

The physical infusion test activates a real matrix through its caster block
callback and lets the ordinary server ticker charge stability from sixteen
colored candle pairs. It pays the actual100 essentia from two53-unit jars,
consumes central TNT/eight real pedestal reagents, checks3-unit remainders and
the normal output craft proof, then throws that manufactured output. Canonical
predecessors, raw research knowledge, arena/altar/candles/input/source jars are
explicit fixtures; no completed collapser, saved crafting plan or manual matrix
tick is supplied. This is not a full survival or independent multiplayer claim.

The pinned `ConfigResearch` original `ScanItem` for `ItemsTC.voidSeed` is also
connected to the actual `thaumcraft:void_seed` item and ItemEntity scanner path.
It supplies only `f_VOIDSEED` once. Possession/hover/ordinary Ender Pearl are
negative tests; the real held/drop specimen remains intact and the fact persists.
This resource proof does not complete or enable any unimplemented Eldritch gate.

Results/counts and final current client images belong in `VALIDATION.md` and the
versioned artifact report after the root agent performs the full fresh runs.
