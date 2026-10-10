# Flux Rift events, Flux Phage and Wisp: BETA26 adapter contract

Authority is the pinned `work/Thaumcraft-1.12.2-6.1.BETA26.jar` and source commit
`954022bb777b7546281fb36df8522f0ba6b43f81`. Read
[the core rift audit](FLUX-RIFT-BETA26-AUDIT.md) for cadence, geometry, generation,
collapse rewards and the original taint limitations. This document describes
implementation and test fixtures, not a claim that QA already passed.

Original source under `src/main/java/thaumcraft`:

- `common/entities/EntityFluxRift.java`, `executeRiftEvent` and static table.
- `common/entities/monster/EntityWisp.java`.
- `common/lib/potions/PotionInfectiousVisExhaust.java` and
  `common/items/casters/CasterManager.java`, maximum disease amplifier penalty.
- `common/items/casters/foci/FocusMediumCloud.java`, `FocusEffectFlux.java`,
  `api/casters/{FocusMediumRoot,FocusNode,NodeSetting}.java` and
  `common/entities/projectile/EntityFocusCloud.java`.
- `common/entities/monster/tainted/{EntityTaintSeed,EntityTaintSeedPrime}.java`.

## Event table and side effects

The five weights remain `50,10,20,20,1`, total101. World-owned RNG replaces
`Math.random()` as an explicit deterministic-world adaptation. Samples in the
unsupported prime-seed slot remain a no-op; they do not reroll another slot.

| Slot | Original event | Weight | Stability on `didit` | Near taint | Current adapter |
|---|---|---:|---:|---|---|
|0|Wisp|50|+5|allowed|Actual live Wisp; Gaussian position5,1/5 Vitium, original spawn checks|
|1|Prime Taint Seed|10|0|forbidden|Explicitly unsupported; no catalogue fake, aura pollution or rift discard|
|2|Flux Phage|20|+10|allowed|Living entities in native rift bounds inflated16,3000ticks/tier2, initial cure list cleared|
|3|Flux cloud|20|none|allowed|Closest player16, native cloud owned by that player, fixed Root/Cloud/Flux1 graph|
|4|Collapse|1|none|allowed|Requests the real core collapse, with no early shrink or duplicate reward|

Cloud's table cost10 is deliberately not applied: the original case3 never sets
`didit`. Radius is1..3. Duration samples original `min(size/2,30)..min(size,120)`;
`NodeSetting.setValue` advances its spinner to30 for values outside5..30,
including values below5. A lower clamp would change released behavior.
Cloud source is the selected player's eye minus0.10000000149011612;
Cloud supplies final power0.5 to the Flux1 suffix, giving2 actual magic damage.
Original free engine cast requires no item payment, XP, research or aura debit.
Only this server-generated fixed graph may mint the existing native detached
continuation; no client event or arbitrary graph request is introduced.
It inherits the existing cloud five-tick callbacks, living Integer/Long cooldown
quirk, save/owner validation, lifecycle cleanup and native particle adaptation.

Prime Taint Seed needs seed-area registration/removal, flux saturation feeding,
fibres, converted taint blocks, tiny tentacles and native tainted-mob immunity.
These are not operational in this slice. Its original catalogue entry remains
available for visual inspection only; that does not complete natural taint ecology.

## Flux Phage

Native `thaumcraft:infectious_vis_exhaust` preserves original color6706551 and
`potion.infvisexhaust` name. Every `duration%40==0`, the carrier's bounding box
inflated4 finds living entities, including cube corners. Already infected
entities are skipped. Positive tier produces6000ticks at tier-1; tier0 produces
ordinary6000/tier0 Vis Exhaust. Only the initiating rift instance clears curatives;
descendant Phage instances retain native ordinary milk curatives. Actual native
effect persistence retains the initial empty cure list. The existing Vis Exhaust
effect remains uncurable, matching the earlier released potion.
Actual caster consumption applies `(max(ordinary tier,infectious tier)+1)*10%`
once. Concurrent illnesses do not sum. The existing modern discount lower bound
remains unchanged. No full WarpEvents registry is implied.

## Wisp

The original `thaumcraft:wisp` registry and spawn egg now construct a live
Wisp subclass; the catalogue base only preserves its gallery/renderer APIs.
Other catalogue mobs retain their NoAI/persistence policy. Wisp has22HP,
3attack,5XP,0.9dimensions, original colored sprite sheets and true tagged crystal
death loot. It selects a primal in9/10 cases, otherwise a compound; the rift's
1/5 Vitium override is preserved. Actual aspect type is synchronized and saved
under original `Type`; existing visual Wisp saves clear their old NoAI/persistence
marker, while ordinary later persistence choices are retained.

Wisp uses the original manual flight steering (0.5horizontal/0.7vertical with
0.1 smoothing and0.6vertical damp), retaliation200ticks, rare1/1000 closest-player
acquisition with original second decrement,16attack range,20charge, negative
-20..-1 reload,0.4 moving hit roll/3damage and0.66 stationary roll/4damage.
Invulnerable/spectator/dead targets are cleared. Original eight-in16 population,
Peaceful exclusion, randomized sky/local-light gates, collision/liquid exclusion
and two-per-chunk cluster limit apply to the real rift spawn. Ordinary mob
despawn remains active. Original live/death media are copied byte-for-byte from
the pinned JAR: `wisplive1/2/3.ogg`, `wispdead.ogg`, plus existing zap media.
Native colored particles replace the old custom lightning network effect.

Loaded chunks/world border/build height checks precede Wisp placement,
movement and visibility. The visibility rectangle is bounded to16chunk deltas
per horizontal axis; it cannot force-load a distant retaliatory attacker's path.
This is a modern lifecycle safeguard. Native entity queries never generate
terrain. Natural biome Wisp spawn lists and full taint ecology are outside this
event adapter slice; a working rift Wisp is not evidence for every world spawn.

## Meaningful server fixtures

`RiftEventsGameTests` contains16 new tests for exact weighted partition/costs,
blocked seed no-op, collapse request, infection range/cure/save, missing target
and removed rift, lower-tier infection/cure cascade, real caster price, free
native cloud plus actual callback damage and persistence, missing-player cloud,
real Wisp stats/loot/migration/flight/retaliation,20tick stationary zap,
creative exclusion/collision and the original eight-Wisp population limit.
The existing catalogue policy test distinguishes this operational Wisp from
remaining NoAI gallery mobs. These are prepared fixtures, not full survival or
independent-client multiplayer validation; final QA belongs to VALIDATION.md.

The first charge fixture used the3x3x3 empty template but placed a target outside
its owned region. The second native run proved that its ray was not actually
visible. The charge fixture now uses the9x5x9 `essentia_network` allocation,
clears only its own1..7/1..4/3..5 interior, places both actors inside it and
checks a real solid wall prevents charge before removing that same wall. The
per-step nineteen/twenty counter and exact4damage assertions remain; fixture
seed5 excludes only the independent1/1000 target replacement. Native Wisp
visibility, lifecycle safeguards and combat probabilities are unchanged.

## Operational collapse/matrix taint poison

`FluxTaintEffect` replaces the former plain magic-damage approximation in the
existing native `flux_taint` effect. Original `PotionFluxTaint` and
`api/damagesource/DamageSourceThaumcraft` require one point of **taint** damage,
armor bypass and the magic flag; undead are immune and `ITaintedMob` heals1.
The native damage registry now has `thaumcraft:taint`, message `taint`, no
difficulty scaling and zero exhaustion (original armor-bypass source has zero
exhaustion). Minecraft `bypasses_armor`, `witch_resistant_to` and
`avoids_guardian_thorns` tags translate
those released flags; armor bypass also inherits native shield bypass. Ordinary
Resistance/enchantment handling is retained. No attacker is invented.

The runtime cadence is the exact Java expression `40 >> amplifier`, including
its five-bit shift-distance mask for large effect tiers. Values that shift to0
are ready every duration tick; damage still follows native hurt immunity.
Tainted classification presently admits the eight original registered classes
represented by the catalogue adapter: Thaumic Slime, Crawler, Swarm, three
Taintacles and two Seeds. Three original zombie catalogue classes retain their
original undead immunity alongside native `MobType.UNDEAD`. This narrow class
mapping does not make their NoAI catalogue into an operational ecology.

Original Champion modifier13 also heals1. Its actual champion attribute/system
has not been ported; no persistent NBT flag, invented attribute or registry
prefix stands in for it. The effect heals tainted mobs before checking undead,
matches ordinary native milk cure, preserves saves and uses the original
Russian/English death strings copied from BETA26. This completes the existing
taint poison consumer, not taint block spreading or Champion/WarpEvents.

`FluxTaintGameTests` adds10 meaningful checks: original normal/high-tier cadence,
native40-duration health pulses, actual Forge damage source/tags/exhaustion,
real armor bypass and ordinary Resistance, native/catalogue undead immunity,
eight exact class healing/max clamp, forged NBT rejection, native milk cure,
one-health lethal damage/removed-target guard and native effect persistence.
Final fresh full server/client evidence remains separate in VALIDATION.md.
