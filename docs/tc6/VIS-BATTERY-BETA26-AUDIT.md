# Vis Battery: BETA26 contract and 1.20.1 implementation

Authority is the pinned `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
The audit read `javap -c -p` for `BlockVisBattery`, `AuraHandler`, `AuraChunk`
and the `ConfigRecipes` battery section. In particular, original
`BlockVisBattery.func_185484_c` (`getPackedLightmapCoords`) has `sipush 180`
at bytecode offset17, followed by independent maxima of the low block-light
and high sky-light bytes; it never substitutes packed full-bright15.
Vanilla 1.12.2 `Block` (`aow`) was
also read against local `joined.srg` for hardness/resistance and random-tick
delegation. The reference checkout is navigation; the bytecode is authoritative.

## Original behavior

- ID `thaumcraft:vis_battery`. Full solid rock cube, stone sound, hardness .5,
  effective explosion resistance .5. Vanilla `setHardness(.5)` raises old
  internal resistance to .5*5; the effective getter divides by5. This is a
  direct vanilla `Block`, not `BlockTC`, and inherits no later device overrides.
  It can be harvested without a correct tool.
- Integer block property `charge=0..10`, default0. There is no tile entity,
  custom inventory, energy capability, GUI, owner or use-action. Stored charge
  persists in the ordinary chunk block state, not portable item NBT.
- The block randomly ticks. Vanilla's random-tick method delegates to the same
  overridden scheduled update, so both update paths perform the behavior below.
  Placement alone does not invent an initial scheduled charging update.
- On a server update, powered redstone takes precedence over all aura thresholds.
  If charge>0, add1 local vis, decrement charge by1 and schedule the next update
  after5 ticks. Empty powered blocks neither charge nor schedule continuation.
- Without power, read the actual local chunk's vis and base. If charge<10,
  `vis > base * .9D` and `vis > 1F`, drain1 vis, increase charge by1, then schedule
  after `100 + random.nextInt(100)` ticks: exactly100..199.
- Otherwise, if charge>0 and `vis < base * .75D`, add1 local vis, decrement charge
  by1, then schedule after `20 + random.nextInt(20)` ticks: exactly20..39.
  The comparisons promote float vis to double; all thresholds are strict.
  Equal75% and90%, the intervening band, empty low-aura and full high-aura
  batteries cause no transfer and no new continuation.
- Every powered neighbor notification schedules a first update after1 tick.
  Removing power does not separately schedule an update. Already pending ticks
  and random ticks can still subsequently act under the actual current power.
  Random ticks can interleave the scheduled loop; its delays are not absolute
  minimum intervals between transfers.
- Comparator output and emitted block light equal charge,0..10. Comparator
  output is not scaled to15. The full cube remains sturdy on all six faces.
- Metadata dropped is always0. Harvesting even a full battery yields one plain
  battery; stored charge is lost, is not emitted into aura and is not carried by
  the resulting item. A newly placed ordinary item starts at charge0.
- Powered release is not capped at aura base. Original `AuraChunk.setVis`
  clamps to32766, so a release at that maximum can discard one stored unit
  while still reducing charge. The port preserves that existing aura clamp.
- The original renderer also forces packed block-light byte to at least180,
  preserving higher environment light and the existing sky-light byte. This is
  a rendering override, separate from emitted charge-level block light.
- Original arcane recipe: bare `VISBATTERY`,50vis and **two of each of all six
  primal crystals**; `SSS / SRS / SSS`, S=Arcane Stone slab, R=Vis Resonator.
  It yields one uncharged battery. Research parents are RECHARGEPEDESTAL and
  CRYSTALFARMER; the observation payment belongs to the shared progression audit.

## Explicit port adaptations and integration

The original archived book text says95% for charging, but released
BlockVisBattery bytecode uses90%. Both are retained in their respective
roles: the unchanged original text is reference data, and gameplay follows
the released runtime predicate rather than silently correcting its threshold.

- `VisBatteryBlock` keeps the original catalogue ID, integer property and all
  eleven existing baked state models. Root registration creates
  `new thaumcraft.world.crystal.VisBatteryBlock()` and uses an ordinary `BlockItem`,
  removing the old visual-only tooltip. No BE registration or migration is needed:
  the previous catalogue's saved `charge` property uses the same values/name.
- Public `VisBatteryBlock.CHARGE` and `CAPACITY=10` are the native state/client
  inspection APIs. `randomTick` and scheduled `tick` share the implementation.
- Server updates require the server thread, an already loaded position and the
  current exact block state/identity. A stale state, removed device or unloaded
  remote position does no work and never initializes/loads a remote aura chunk.
- A debit must actually pay the entire1 vis; unexpected partial debit is refunded.
  A failed block-state write refunds charge payment; releases commit the state
  decrement before adding vis. These are narrow modern hardening of the original
  unchecked debit/write return values, not an altered player-facing charge rate.
- Client-only `BatteryBakedModel` restores the original packed block-light
  minimum180 on all eleven block variants, while retaining their original
  baked geometry/textures, vertex color/tint, face direction, normal, shading
  and per-quad ambient-occlusion flag. Only the UV2 block-light component is
  raised; stronger baked block light and the baked sky component survive.
  Both legacy and Forge layer-aware quad queries use detached copied vertices,
  cached by source-quad identity under a synchronized map for chunk workers.
  The inventory model and underlying shared baked source are never modified.
  Native emitted light still equals CHARGE0..10; no full-bright15 approximation,
  new emission, AO bypass or shader is added.
- Forge47.4.10 `IForgeVertexConsumer.applyBakedLighting` takes independent maxima
  between environment and baked UV2 unsigned-short components. Thus the baked
  minimum180 retains higher world block light and sky light in both ordinary
  and AO rendering paths. This is the modern rendering integration of the
  original block method, not a change to the server light engine.
- Read-only `BatteryBakedModel.verifyBake(Minecraft)` is exposed to the root
  integrated client smoke audit. It requires all11 charge states to use the
  wrapper, checks both query APIs against their actual original quads word by
  word (only UV2 may differ), confirms unchanged inventory rendering, and
  exercises Forge's actual lighting merge with five environment-light inputs
  per vertex. A synthetic copy also proves that baked light200/240 and baked
  sky light80/240 remain intact. Its `BakeAudit` result reports actual state,
  quad and lighting-check counts; defining it does not establish a QA pass.
- `VisBatteryGameTests` defines **16** scenarios: all states/physical/light/
  comparator/support APIs, native state persistence, random charge debit/exact
  delay, strict90%, independent>1 floor, capacity, low-aura refund/exact delay,
  strict75%/deadband, powered over-base discharge, empty powered inactivity,
  original32766 discard, ten-unit conservation, stale/replaced/unloaded guards,
  actual survival hand harvesting, native paid BlockItem placement and ordinary
  physical-redstone/scheduled full discharge.
- Tests replace only the current loaded fixture chunk's `AuraChunk` through
  test-local reflection and restore the previous record. Saturated flux1000
  prevents ordinary regeneration/diffusion during the native scheduler scenario;
  the battery's actual flux-independent mechanics stay unchanged. This does not
  add a production aura mutation API or supply a paid gameplay output.

Scenario definitions and a successful compile do not constitute integrated QA.
Current full-server/client results belong in `VALIDATION.md` after actual runs.
