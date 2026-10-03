# Break focus effect: TC6 BETA26 implementation audit

Scope: the BREAK effect and its real progressive, server-owned block breaker.
The current linear graph runs at final power1 and has one target. This document
does not claim that Plan/Scatter/Split or their multiple-target delivery works.

## Authoritative reference

Pinned `work/Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Readable reference commit `954022bb777b7546281fb36df8522f0ba6b43f81`.
The official `javap -c -p` output of `FocusEffectBreak`, `ServerEvents`,
`BlockUtils` and `FocusEngine` was checked against the readable source.
The confirmed release formulas and relevant branches are recorded below.

## Settings, effect and sound

- Research `FOCUSBREAK`, key `thaumcraft.BREAK`, aspect Perditio.
- Power1..5; Fortune0..4; Silk0/1, defaults1/0/0. These options may be enabled
  together; the port does not invent a mutually exclusive Silk/Fortune setting.
- Complexity `power*3 + silk*4 + (fortune==0 ? 0 : (fortune+1)*3)`.
- The release returns **true on every target type**. Only block targets emit
  Break impact FX and schedule work. Entity targets receive no Break damage.
- Original cast sound: vanilla END_GATEWAY_SPAWN, PLAYERS, volume0.1,
  pitch `2+gaussian*0.05000000074505806`.
- The modern impact dust approximates original custom FXGeneric sprite704+q*3.
  It is restricted to players within64 blocks, and is separate from the real
  vanilla progressive crack animation. Custom original sprite animation remains
  a visual adaptation; it is not claimed as exact renderer parity.

## Progressive mining and the zero-based target ordinal

The official effect calculates:

```
strength   = power * finalPower
durability = (float)sqrt(hardness * 100F)
delay      = (int)(durability / strength / 3F * num)
targetVis  = .25F + (silk ? .25F : 0F) + fortune * .1F
```

**`num` is the zero-based target ordinal**, not the number of targets. The
official FocusEngine initializes it to0, calls execute for the first target,
then increments it. Consequently, a normal Touch/Bolt/single-projectile Break
has **no initial delay**. It still requires repeated mining ticks; substituting
`num=1` for a single target would incorrectly add latency. The ordinary public
effect entry point uses finalPower1/ordinal0; a separate overload retains these
two original engine inputs for later multiple-target integration.

For a positive delay, the original RunnableEntry decrements it on each END
while it is positive, then activates on the following END. On an active END:

1. Require the captured block state to still be the same state, adequate target
   chunk aura, mining permission and nonnegative hardness.
2. Send crack id `pos.hashCode()` and stage
   `(int)((1F-currentDurability/maxDurability)*10F)` **before subtraction**.
3. Subtract strength exactly once. If durability remains positive, retain work
   for the next END. Otherwise call the real harvest helper, clear cracks with
   stage-1, and drain targetVis.

Stone hardness1.5 has durability approximately12.247449: power1 completes on
the thirteenth active END, power5 on the third. A second target at power1 has
delay4 and begins progress on the fifth END; it completes on the seventeenth.
Block-state and mining-permission checks are deferred until that activation;
temporarily replacing and restoring the block during a positive delay does not
cancel the original scheduled target. World/owner/loaded bounds remain guards
during the delay as an explicit modern safeguard.
Zero-hardness blocks complete on the first active END. Java's float NaN stage
conversion for0/0 produces stage0, followed immediately by the clear packet.

Insufficient aura discards work; it does not pause, retry later or partially
pay. Changed states are discarded and their cracks cleared. The extra cost is
from the **target chunk**, independent of the already-paid cast price and gear
discounts. Creative still pays the target cost, while producing no ordinary
survival loot or XP.

The release charges after calling `BlockUtils.harvestBlock` **without testing
its boolean result**. Forge cancellation therefore still incurs targetVis.
The port retains this behavior, including callbacks that change the state
during the break event; the replacement itself is protected from harvest.

## Main-hand loot, enchantment precedence and XP

The original harvest uses `alwaysDrop=true`: a caster or unsuitable main-hand
tool does not deny drops by tier. The **actual main-hand at completion** is
copied; it is not frozen at casting time and is not swapped with a fake player.

If Silk is enabled, or the configured Fortune is greater than the player's
original equipment Fortune maximum, the copied tool is replaced by the original
`enchanted_placeholder`. Only the main-hand's enchantment map is copied to this
placeholder. Silk adds Silk TouchI; Fortune becomes
`max(configuredFortune, mainHandFortune)`. Other main-hand NBT/item identity is
not copied in this override branch. If no override is necessary, the real
main-hand copy, its existing Silk/Fortune and other NBT remain the loot context.
Vanilla's actual loot table decides Silk priority when both enchantments exist.

Forge's real break event runs against the unchanged real player/main-hand before
constructing the override loot stack. Its positive XP result is retained, even
if configured Silk subsequently changes the drop. Neither actual main-hand nor
offhand caster receives durability wear or enchantment/NBT mutations.

Harvest invokes `onDestroyedByPlayer`, `Block.destroy`, and `Block.playerDestroy`
with the actual captured block entity and constructed loot stack. This preserves
native loot modifiers and block-entity data, including modern decorated-pot
sherds. No synthetic ore/drop table replaces vanilla/Forge loot generation.
The separate active elemental-tool mining enchantment pipeline is not added to
spell harvesting: its tier, no-block-entity and tool-wear assumptions differ.

## Queue lifecycle and explicit modern hardening

- END processing runs at LOWEST after projectile continuations. Work created
  by a harvest callback is detached until the next END; a recursion guard keeps
  that callback from running the detached queue again during its own harvest.
- The queue has a4096-entry per-level cap. This is a modern bound, not an
  original BETA26 constant. State/player/aura accesses run on the server thread.
- Dead/spectator/changed-world casters, out-of-world targets, unloaded chunks,
  invalid settings and nonfinite vectors/power/hardness are rejected. Negative
  hardness is skipped without enqueue rather than carrying a NaN until tick.
- Loaded target checks never force chunks into memory. Modern spawn protection
  and Adventure block-action restrictions are checked throughout progress.
- Forge callback replacement is rechecked before removal so a newly installed
  block is not harvested with stale permission and loot context.
- Cancellation, insufficient aura, permission loss and stale state explicitly
  clear cracks. Original BETA26 left some of those crack states to client expiry;
  immediate clearing is deliberate port cleanup.
- The original BreakData/RunnableEntry queues were transient, not SavedData.
  World unload/server stop clears all pending work and crack packets; a save
  does not serialize operations or resurrect a player/harvest after restart.
  A surviving server session continues its transient queue normally.

## Verification inventory

`FocusBreakGameTests` provides19 meaningful server checks: real END timing;
zero-based ordinal delay and actual ClientboundBlockDestructionPacket progression;
fractional final power/zero hardness; Silk placeholder/enchantment/NBT precedence;
conditional Fortune override; native ore loot at all five Fortune settings;
completion main-hand, one real Forge event and XP; actual block-entity NBT loot;
cancelled Forge break/cost/no retry; callback replacement; prior state replacement;
insufficient aura discard; creative target-chunk cost; duplicate-target loot/debit;
callback-created detached work/recursion; Adventure/dead owner; loaded-only/entity/
bedrock behavior; unload cleanup; malformed/off-thread rejection.

These are test definitions. Successful execution counts and gameplay-client
evidence belong to VALIDATION.md only after the integrated checks have run.
