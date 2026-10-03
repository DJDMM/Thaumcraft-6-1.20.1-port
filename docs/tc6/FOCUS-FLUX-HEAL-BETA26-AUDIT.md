# Flux and Heal: TC6 BETA26 implementation audit

Scope: the two effects introduced in 0.18, with final power 1 in the supported
linear focus graph. This does not enable Scatter/Split power modification,
Cloud/Spellbat repeated execution, full world flux/taint, or advanced/greater
focus crafting. Bolt, Break and canonical research progression have separate
audits. A Flux spell is an effect, not the world Flux Rift mechanic.

## Authoritative baseline

Pinned `work/Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Readable reference commit `954022bb777b7546281fb36df8522f0ba6b43f81`.
Both classes were inspected directly with `javap -c -p`, including
`getComplexity`, `getDamageForDisplay`, `execute`, `createSettings`, `onCast`
and `renderParticleFX`. The release bytecode agrees with the readable Java for
the behavior implemented here.

The original vanilla client JAR in `work/vanilla-1.12.2-audit` and its joined
SRG mapping were also checked. DamageSource `ur.b(vg,vg)` is the mapped
`causeIndirectMagicDamage`: it creates `indirectMagic`, enables unblockable
damage and marks the source as magic. It does not set the projectile flag.
SoundEvents `qf.aj`, mapped to `field_187542_ac`, is explicitly initialized
from `block.chorus_flower.grow`. EntityLivingBase `vp.cc`, mapped to
`isEntityUndead`, compares its creature attribute to UNDEAD.

## Preserved runtime behavior

| Effect | Setting | Complexity | Behavior at final power 1 |
| --- | --- | --- | --- |
| Flux | power1..5, default1 | power*3 | Deals 3+power indirect magic damage to any entity hit |
| Heal | power1..5, default1 | power*4 | Living targets heal power; undead instead receive power*1.5 indirect magic damage |

Flux's immediate damage source is the **hit entity**, and its true source is
the caster. Heal's damage to undead uses the **caster for both sources**.
This difference is confirmed in BETA26 and is intentionally preserved.
`DamageTypes.INDIRECT_MAGIC` retains modern armor bypass and witch-resistant
magic classification without falsely flagging the source as a projectile.
The actual Minecraft/Forge damage API handles invulnerability, absorption,
resistance, enchantments, armor, death, combat attribution and callbacks.

Heal invokes `LivingEntity.heal`, retaining maximum-health capping, the
non-resurrection behavior and real cancellable/adjustable Forge healing
events. Vanilla1.20.1 `isInvertedHealAndHarm` checks MobType.UNDEAD, matching
the old vanilla undead predicate; modern custom entity overrides are honored.
No friendly-only restriction, invented regeneration potion or undead healing
fallback is added. Nonliving targets receive Flux through their own hurt API;
Heal only sends impact FX for them. Neither effect transforms blocks or
charges additional target-chunk vis.

Both original effects **always return false**, even after accepted damage or
healing. The focus executor ignores this return, as the original engine does.
Payment/cooldown belongs to the actual cast and is not refunded by that return.
Both cast sounds are chorus-flower growth, PLAYERS category, volume2,
pitch `2F + (float)(gaussian*0.10000000149011612)`, at the caster block above.

## Native visual adaptation and execution guards

Original impact packets cover players within64 blocks. The helper retains
this distance and sends native colored dust: purple for Flux and white for
Heal. The original Flux `FXGeneric` smoke has randomized purple colors,
15..24-tick age, atlas animation128..141, loop, varying size and gravity;
Heal has white particles,10..19-tick age and alpha0.7. The native dust
approximations do **not** reproduce those custom particle animations.
They are explicitly a visual adaptation, not original FX parity.

Like the existing elemental implementation, execution must occur on the
server thread with a living nonspectator caster in the same level. Null
arguments, misses, nonfinite vectors, wrong keys, foreign/removed entities,
invalid/unknown/null-valued settings and power outside1..5 are rejected
before particles or gameplay mutation. Target locations and entity positions
must be loaded and inside build-height/world-border bounds. No query
force-loads remote chunks. These are deliberate modern hardening measures;
the older bytecode has fewer guards.

The public helper is `AdvancedFocusEffects.apply(level,caster,node,target,
direction)` and `playCastSound(level,caster,key)`. Their integration lives
in FocusExecution/FocusCasting; the helper itself never reads client-supplied
costs or edits focus stacks, knowledge, research or aura.

## Verification inventory

`AdvancedFocusEffectsGameTests` supplies19 meaningful server tests:

1. Flux low/high power and real Forge source/type/tag attribution.
2. Actual worn diamond armor installed by a real living tick, bypassed by Flux.
3. Rejected Flux damage leaves health/debuff/fire state unchanged.
4. Missing power resolves to the original default1 for both effects.
5. Flux uses a nonliving ItemEntity's own hurt/destruction API.
6. Heal low/high amounts and maximum-health cap.
7. Forge heal amount modification and cancellation honored.
8. Undead low/high formula, armor bypass and distinct caster/caster sources.
9. Invulnerable undead receives neither damage nor a healing fallback.
10. Dead living targets are not resurrected.
11. Heal does not destroy or mutate nonliving targets.
12. Block hits leave blocks and target-chunk aura unchanged.
13. Out-of-range/unknown/null settings cannot change health.
14. Null/miss/nonfinite arguments cannot change health.
15. Removed/foreign/unloaded targets, dead casters and spectators rejected.
16. Off-thread damage/healing/sound calls rejected without health mutation.
17. Real installed ROOT->Heal self cast: health, derived vis cost and cooldown,
    despite the original false effect return.
18. Real installed TOUCH->Flux cast: live target, one debit and cooldown.
19. Real installed TOUCH->Heal cast: actual ray target and single derived debit.

Fixtures use connected ServerPlayers for real Forge/vanilla packet callbacks.
Compilation, full server suite and hidden client QA are performed by the
integrating agent; the presence of the tests does not establish that they
passed. Hidden client fixtures also do not prove an independent multiplayer
session or an entire survival playthrough.
