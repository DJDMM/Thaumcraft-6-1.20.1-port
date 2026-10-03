# Focus Projectile: BETA26 contract and1.20.1 runtime

Audited2026-10-03. Scope is the PROJECTILE intermediary and its regular,
bouncy, hostile-seeking and friendly-seeking options. This does not implement
the other late delivery media or infer mechanics from TC4/TC5.

## Primary evidence

The behavioral baseline is the pinned official
`Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Readable reference commit `954022bb777b7546281fb36df8522f0ba6b43f81`
was compared with `javap -c -p` disassembly of FocusMediumProjectile,
EntityFocusProjectile and its impact Runnable, EntityUtils/Utils,
FocusEngine/FocusPackage and NodeSetting. The original research JSON has
exact byte parity with the pinned reference. No relevant source/binary
discrepancy was found.

Inherited physics were independently checked in Mojang's official
[Minecraft1.12.2 client binary](https://piston-data.mojang.com/v1/objects/0f275bc1547d01fa5f56ba34bdc87d981ee12daf/client.jar),
SHA1 `0f275bc1547d01fa5f56ba34bdc87d981ee12daf`, SHA256
`8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f`.
Official version metadata verifies the SHA1. Forge's
[1.12.2 SRG mapping archive](https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.12.2/mcp-1.12.2-srg.zip)
maps `aev` to EntityThrowable. Stable39 MCP maps the fields/methods.
The only applicable [Forge EntityThrowable patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.12.x/patches/minecraft/net/minecraft/entity/projectile/EntityThrowable.java.patch)
adds the impact-cancellation hook after the Nether portal special case.
Local evidence/provenance is in `work/vanilla-1.12.2-audit/` and mod
disassemblies in `work/auromancy-next-bytecode/`; these audit downloads are
not shipped with the mod.

## Node, payment and delivery

- Key `thaumcraft.PROJECTILE`, aspect MOTION (`motus`), strict creation
  research `FOCUSPROJECTILE@2`. Supplies TARGET and TRAJECTORY; requires
  TRAJECTORY and is an intermediary.
- Speed1..5, default1, actual launch speed `speed/3F`.
- Option0=regular,1=bouncy,2=seek hostile,3=seek friendly. The whole
  option setting requires completed `FOCUSPROJECTILE`; initial option0
  remains legal at stage2.
- Complexity is `4 + (speed-1)/2` using integer division, plus0/3/5/5
  for options0/1/2/3. There is no damage penalty, caster-velocity bonus
  or inaccuracy.
- Initial position is trajectory source plus normalized direction times
  `caster.width*2.1`. Direct shooting uses the original float square root.
- Casting owns the single vis debit, sound and player-shared cooldown.
  The entity holds an immutable compiled plan and an index strictly
  following its PROJECTILE node. A hit continues only this suffix;
  subsequent PROJECTILE nodes create further intermediaries. The
  compiler's32-node limit bounds the sequence.
- Ordinary server hits capture previous projectile position and normalized
  current motion; entity-hit coordinates become current projectile position.
  The projectile is discarded immediately. The original delay-zero Runnable
  runs the remaining focus package at world-tick END. The port uses an END
  queue at HIGH priority, ahead of Earth breaking. Resume does not debit
  vis, reset cooldown, repeat cast sound or recheck research knowledge.
- Block misses never execute the remaining effects. Bounce block hits never
  execute them either. A bouncy projectile still executes its suffix when it
  hits an entity.

## Movement, collision and bounce

Entity ID `thaumcraft:focus_projectile` retains0.15x0.15 size,
64-block tracking,20-tick update interval and velocity updates. The old
catalogue registration is replaced without changing the saved ID.

The original EntityThrowable moves after collision handling, then applies
float air drag0.99 or water drag0.8 to all axes, then subtracts gravity:
0.01F regular/bouncy or0.005F seeking. It creates four water bubbles.
Rotation lerps by0.2. Movement continues inside that same vanilla update
even after impact discarded the entity; the port preserves this order
without permitting a second callback from a removed entity.

The initial block ray is `rayTraceBlocks(start,end,false,false,false)`:
no fluid stop and no exclusion of blocks lacking collision boxes. It clips
the ray end before entity selection. Eligible collidable entities are
queried in the projectile box expanded by motion and inflated1; each target
box is inflated0.30000001192092896. The nearest intercept wins. Nether
portals bypass the impact event. A canceled Forge impact continues movement.

The old temporary owner-ignore rule is deliberately retained rather than
modern Projectile's `leftOwner` predicate. The launch constructor preassigns
the caster as `ignoreEntity`, with timer0. A matching entity refreshes the
timer2; a nonmatching collidable candidate overwrites the seen flag with
false. If the final flag is false, `ignoreTime--<=0` clears the ignored
entity. With no ignore entity in the first tick, the first collidable entity
is temporarily ignored, even if it is not the caster. This is an actual
vanilla1.12 quirk, not a permanent immunity to the owner.

Bouncy block hits ignore blocks with empty collision boxes. Otherwise:

1. Subtract old motion from position.
2. Invert the hit-face axis; vertical inversion uses -0.9.
3. Multiply all motion by0.9: reflected X/Z=-0.9, reflected Y=-0.81.
4. Subtract normalized reflected motion times0.05000000074505806
   from position, using a float square root.
5. Play leash-knot-place at volume0.25/pitch1 on server. If reflected
   speed is below0.2, discard without executing the suffix.
6. Continue the vanilla movement using reflected motion, then drag/gravity.

## Seeking and friendship

After movement, every fifth tick an unassigned seeking projectile searches
a16-block cube centered on its position. Candidates are sorted by distance
to entity position, not bounding-box center. A visible, nonremoved candidate
must satisfy a1.75-radian full cone aperture (about100.27 degrees) and
projection below16. The apex is projectile position plus original eye height
`height*0.85F`; the target uses its midheight. The cone uses strict comparisons.
Visibility is a separate midheight-to-midheight block ray.

Friendship means identical entity, a direct/indirect riding relation, an
allied scoreboard team, owned tame entity, or any player while server PVP is
disabled. Passive animals are otherwise nonfriendly: hostile seeking can
select a cow. Friendly seeking can select the caster. Two unrelated passengers
on the same vehicle are not automatically friends.

Each tick with a target, desired direction points to target feet plus
`height*0.6`. New motion is
`normalize(normalize(current)+normalize(desired)*0.275)*length(current)`.
Speed after drag/gravity is preserved. Only after steering, every fifth
tick, the target is cleared if removed, outside the cone or occluded.
Acquisition occurs before clearing, so a lost target waits until the next
fifth tick for reacquisition. The original target is neither saved nor synced.

## Persistence, ownership and explicit adaptations

The original saves `pack` and `special`; EntityThrowable saves ownerName,
tile/inGround/shake fields. Vanilla does not persist ticksExisted, ignore
timer or seeking target. Consequently the lifetime starts over on reload.
The port also resets those transient values; at age1200 it remains legal,
at1201 it discards. It does not add an age tag.

The modern entity saves typed `PaidGraph`, `Capacity`, `Next`, `Special`
alongside Projectile's owner UUID and vanilla entity transform/motion.
Capacity must be15/25/50 and determines the actual focus tier. Restore uses
bounded `FocusGraph.read` and `FocusCompiler.compile` with research true,
derives costs/settings/color again, and validates that Next follows
PROJECTILE and the option matches that node. No inventory ItemStack/NBT
alias survives. Invalid tags or old `VisualOnly` catalogue saves have no
plan and discard harmlessly. Option, owner entity ID, terminal-effect color
and effect key are synchronized; execution graph is server-only. Forge's
entity spawning packet is used.

Explicit modern adaptations:

- Movement uses Projectile as a lifecycle/owner base and implements audited
  throwable collision itself; modern `leftOwner` is not used for filtering.
  The engine supplies tickCount and previous-transform updates.
- Modern OUTLINE block shapes approximate the old outline bounding-box ray;
  modern COLLIDER shapes approximate visibility's skip-no-collision-box ray.
  Detailed modern stair/fence/plant shape differences are not falsely claimed
  to be voxel-identical to1.12. Current fluids/entity lifecycle/portal transfer
  remain Minecraft1.20.1 behavior.
- A missing, removed, non-ServerPlayer or different-dimension caster is rejected
  before movement and again at queued execution. Dead/spectator casters and
  removed/dead/different-world targets at END do not execute. This prevents
  effects in the caster's new world after a dimension change.
- Explicit `setOwner(null)` clears both effective ownership and its saved tag.
  Modern Projectile otherwise retains its cached owner UUID; a reload must
  not resurrect an explicitly cleared paid continuation.
- Nonfinite position/motion and motion squared above64 are discarded. Launch
  and collision/visibility rays never load absent server chunks.
- Pending impacts have a4096-entry level limit and detached64-entry FIFO
  batches per END. Work above64 is deferred to a later END; level/server
  unload clears pending references. This is bounded scheduling hardening.

## Client appearance and verification

Original RenderNoProjectile has no item mesh and emits colored fire motes
and one effect particle per tick. FXFireMote uses atlas cell7 on64x64,
half-size `0.1*7=0.7`, alpha0.5 and16-tick shrinking particles. The port
renders the same colored atlas cell as a camera-facing glow and adds one
native colored dust/effect trail per visible tick. Its core geometry and
native particles replace the original custom FX particle lifecycle; this is
an explicit visual adaptation, not a claim that the old FX engine is ported.
The shared `textures/misc/particles.png` has exact BETA26 byte parity,
SHA256 `3c31a3bed7a007ef4d671745bd627112c79d1ac70049e23194a1454929f17000`.
Neutral old catalogue previews without an effect key render harmlessly blank.

Nine GameTests cover real cast->moving hit->END effect with one vis debit and
shared cooldown; air/water physics; vertical bounce and empty collision shapes;
typed detached save/load and harmless old saves; owner loss/lifetime/invalid
launch; nearest passive hostile seeking and target loss; Forge impact veto;
and queued removal plus nested-suffix execution. They are intentionally passed
to the parent task for compilation/server execution; this audit-writing child
does not claim an unrun test or independent-client gameplay validation.

## Related progression correction

Official1.12 binary proves WitherSkull, DragonFireball, SmallFireball and
LargeFireball all extend EntityFireball. The modern `f_fireball` scan/hurt
family is AbstractHurtingProjectile, including WitherSkull. Modern `Fireball`
alone is too narrow. Arrow and llama-spit research facts also require actual
server entity targeting independently of an empty aspect map; hover remains
read-only. These progression integration changes belong to the parent task.
