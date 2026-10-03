# Air, Frost and Earth: TC6 BETA26 implementation audit

Scope: the three elemental effects added after ROOT/TOUCH/FIRE, with unmodified
final power 1. Projectile delivery and progression have their own audits. This
document does not claim that Scatter/Split or the remaining focus effects work.

## Authoritative reference

`work/Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Readable reference commit: `954022bb777b7546281fb36df8522f0ba6b43f81`.
The pinned bytecode in `work/auromancy-next-bytecode/FocusEffectAir.txt`,
`FocusEffectFrost.txt`, `FocusEffectEarth.txt`, `BlockUtils.txt`,
`ServerEvents.txt` and `FocusEngine.txt` confirms the formulas, branch returns,
sound parameters, queued harvest and cancellation debit described here.
The preliminary source/bytecode comparison is `work/auromancy-next-audit.md`.

## Preserved release behavior

| Effect | Settings | Damage at final power 1 | Other behavior |
| --- | --- | --- | --- |
| Air | power1..5, default1 | 1+power | Living knockback strength damage*0.25, ordinary resistance-aware API |
| Frost | power1..5, default1; duration2..10, default2 | 3+power | Slowness20*duration ticks, amplifier `(int)(1F+power/3F)` |
| Earth | power1..5, default1 | 2*power | Soft block breaker threshold hardness<=damage/25F |

All three use thrown damage with the **hit entity as immediate source** and
caster as true source. This unusual attribution is actual BETA26 behavior.
Damage acceptance does not gate Air's extra knockback or Frost's Slowness.
Air's absent-trajectory fallback uses the target's yaw. Air and Earth return
true on entity hits even if damage was refused. Frost always returns false;
Earth block hits also return false after successful enqueue. These values are
not spell-payment results: the original FocusEngine ignores effect returns.

Air has no block transformation. Its impact sound is ender-dragon flap,
PLAYERS volume0.5/pitch0.66. Cast sounds retain the release parameters:

| Effect | Cast sound | Volume | Pitch |
| --- | --- | --- | --- |
| Air | original `thaumcraft:wind` | 0.125 | 2 |
| Frost | vanilla zombie-villager cure | 0.2 | 1+gaussian*0.05000000074505806 |
| Earth | vanilla dragon-fireball explosion | 0.25 | 1+gaussian*0.05000000074505806 |

`scripts/extract_elemental_assets.py` verifies the pinned SHA-256 and copies
only the original wind definition and wind1/wind2 OGG files. It preserves the
rest of the port's sound catalogue. This is sound-byte parity, not a claim
that the custom original TC6 particle renderer has been ported.

## Frost source-water sphere

Radius is `min(16F,2*power)` in the present unmodified scope. The inclusive box
uses the hit block coordinates plus/minus radius, with double-coordinate
flooring. Candidate **centers** must be within that radius of the actual hit
vector. This is a three-dimensional sphere, not a Frost Walker surface disk.
Only vanilla WATER blocks with liquid LEVEL0 qualify. Flowing water and
waterlogged blocks are excluded. No surface-air, biome-temperature or light
condition is added. Ice collision/placement feasibility is checked using
`canSurvive` and `isUnobstructed` with an empty collision context.

The result is real vanilla FROSTED_ICE with its default state, and a scheduled
block tick at `60+nextInt(61)` ticks. Subsequent melting remains vanilla1.20.1
behavior. Cancelled Forge placement restores the prior source water and does
not schedule an ice tick.

## Earth END queue, loot and vis

The captured state is queued at delay0 and processed in WorldTick END at
LOWEST priority, after the projectile continuation. Power5's threshold is0.4:
glass0.3 qualifies; normal dirt0.5, stone1.5 and logs2 do not. Negative hardness
may enter the original enqueue branch, but is rejected at processing, so
bedrock is never harvested. Changed state or missing local0.1 vis discards the
operation, without retry or partial debit.

The additional0.1 vis belongs to the target block's chunk, independent of the
caster's already-paid focus cost. After preliminary permission/aura/state gates,
the real Forge break event runs. **A cancelled break still charges0.1 vis**,
because original ServerEvents drains after calling BlockUtils.harvestBlock
without checking its return. This is intentional release parity.

Harvest retains the real main-hand stack copy, including existing Silk Touch
and Fortune enchantments. `alwaysDrop=true` bypasses tool tier; no synthetic
loot or alternate fake player is supplied. The implementation passes the
captured block entity to Block.playerDestroy, invokes onDestroyedByPlayer and
Block.destroy, and applies positive Forge event XP. Survival uses the real loot
pipeline; creative removes without ordinary drops/XP. The main-hand tool and
offhand caster incur no durability or NBT changes from this helper.

The existing private ToolMining helper was inspected but cannot be reused: it
requires a suitable tool tier, excludes block entities and damages the tool.
The Earth helper therefore follows its Forge1.20.1 removal/loot APIs while
retaining BETA26's different always-drop and no-wear contract. This does not
add the separate active TC6 tool mining enchantment pipeline to spell harvest.

## Explicit modern adaptations and hardening

- Execution and queue mutation run only on the server thread. Invalid settings,
  nonfinite vectors, mismatched levels, removed entity targets, dead casters and
  spectator casters are rejected. Original projectile effects can still use a
  nonnull dead owner; the alive/spectator owner checks are explicit hardening.
- Block candidates must be loaded, inside build height/world border and pass
  ServerLevel.mayInteract plus the player's block-action restrictions. Queries
  never force-load chunks. Protection/adventure gates are intentionally stronger
  than the old Frost placement check.
- Frost posts cancellable Forge EntityPlaceEvent per changed position, with
  DOWN as the original placement face; cancelled positions roll back.
- Earth rechecks captured state after Forge break callbacks. A callback that
  replaces a block is not allowed to harvest that replacement. As with original
  cancellation, the post-harvest-call debit still applies.
- The Earth queue has a4096-operation per-level cap, clears on world unload and
  server stop, and defers reentrant event-created operations to the next END.
  These bounds/lifecycle handling are modern safeguards, not BETA26 constants.
- Impact FX use native cloud/snowflake/colored dust approximations, manually
  restricted to players within64 blocks. They do not reproduce original custom
  Air/Frost/Earth particles or animations. Native damage and ice melting follow
  modern vanilla/Forge behavior where the old Minecraft API differs.

## Verification inventory

`FocusEffectsGameTests` contains18 meaningful server tests:

1. Air formula and real Forge hurt-source attribution.
2. Invulnerable Air target still knocked back; full resistance respected.
3. Target-yaw fallback and absence of an Air block transformation.
4. Frost damage, duration, amplifier threshold and false success return.
5. Invulnerable Frost target still receives Slowness.
6. Vertical source-water sphere under a roof, flowing/waterlogged exclusions and
   genuine inclusive60..120 scheduled frosted-ice ticks.
7. Sphere geometry anchored to actual hit vector.
8. Entity collision rejection and cancelled placement rollback/no ice tick.
9. Adventure protection and remote loaded-only execution.
10. Earth formula, damage source and rejected-damage true return.
11. Soft-block thresholds, bedrock rejection and END-only processing.
12. Target-chunk extra vis independent of empty caster chunk.
13. Missing aura and changed state discarded without retry/debit.
14. Actual cancelled Forge break retains block but charges original0.1.
15. Real main-hand Silk Touch glass loot, Forge XP and unchanged tool/caster.
16. Soft decorated-pot block entity removed with real decorated loot NBT.
17. Creative no ordinary loot and adventure no pre-harvest debit.
18. Invalid settings/nonfinite vectors/off-thread calls cannot mutate effects,
    world, queue or aura.

The tests use connected server-player fixtures because Forge's harvest/effect
APIs send packets. They assert actual world states, live entities, ItemEntity
loot, ExperienceOrb values and aura values; no mocked harvest outcomes.
Compilation, full server suite and hidden client QA are performed by the
integrating agent. Merely adding these tests does not establish that they passed.
