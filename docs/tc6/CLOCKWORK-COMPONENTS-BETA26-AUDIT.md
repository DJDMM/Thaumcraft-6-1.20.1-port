# Clockwork mind components — BETA26 audit

Baseline: official `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
The mirror source is pinned to commit
`954022bb777b7546281fb36df8522f0ba6b43f81`. This audit concerns the physical
clockwork mind recipe and its existing Brain Box consumer, not the Golem Press,
golem entity AI or control seal runtime.

## Research path and partial boundary in 0.21

The full canonical set contains **50** keys: the previous 46 plus HEDGEALCHEMY,
UNLOCKGOLEMANCY, BASEGOLEMANCY and MATSTUDWOOD. **MINDCLOCKWORK is not counted
as fully implemented.** Its supported progression is only **0 → 1 → 2**,
which makes the original physical mind recipe naturally reachable.

UNLOCKGOLEMANCY keeps its completed parents UNLOCKARTIFICE, UNLOCKAUROMANCY
and UNLOCKINFUSION, the actual `f_golem` scan and 16 raw Observation each
in Golemancy and Basics. Its completed payment opens the empty BASEGOLEMANCY
intro as the original sibling. The original `EntityGolem.class,true` scan
includes Iron Golem, Snow Golem and Shulker; the modern match is AbstractGolem.
Because the port's visual catalogue flattened EntityOwnedConstruct's hierarchy,
the four audited registered IDs `golem`, `turret_basic`, `turret_advanced` and
`arcane_bore` also retain that scan fact. An item, hovered target or arbitrary
unrelated mob must not replace the actual server scan. This is a scan contract,
not completed spawning, AI or manufacturing for those catalogue entities.

Starting MINDCLOCKWORK requires **completed** BASEGOLEMANCY, ESSENTIASMELTER
and HEDGEALCHEMY. Its first payment retains `!cognitio`, `!victus` and
16 raw Observation Golemancy. The empty, no-parent MATSTUDWOOD sibling opens
when the mind entry starts. The entered second stage exposes the exact recipe
below, but the next request returns **UNSUPPORTED without charging**: the
original stage-two 32 raw Theory Artifice and 32 raw Theory Golemancy payment
would reveal the still-unported press. No artificial completion is recorded,
and CONTROLSEALS/other mind-completion parents do not qualify.

HEDGEALCHEMY retains all ten original crucible conversions, three 16-raw
Observation Alchemy payments, original craft proofs, computed aspect costs
and white/16 recolored tallow candle recipes. The malformed required craft
`thaumcraft:leather` is ignored as in BETA26 rather than replaced by an invented
vanilla leather proof. New acquisition of PORT_TALLOW is removed; a previously
saved recipe alias still works for its old recipe access, never as a completed
HEDGEALCHEMY parent. The natural component route therefore preserves the
original preceding research rather than opening mind fabrication for free.

## Exact released recipe

`javap -c -p thaumcraft.common.config.ConfigRecipes` on the pinned release
confirms `thaumcraft:MindClockwork` at arcane-registration bytecode offsets
7904–8056:

- Research predicate: **MINDCLOCKWORK@2**, not bare MINDCLOCKWORK and not
  completed MINDCLOCKWORK. Stage two must have been entered.
- Vis: **25** before the existing original equipment discount calculation.
- Physical primal crystals: **Ignis 1 and Ordo 1**.
- Output: `new ItemStack(ItemsTC.mind,1,0)`, equivalent to the registered
  modern item **thaumcraft:mind_clockwork ×1**.
- Shape: **`" P " / "PGP" / "BCB"`**. `P` is `paneGlass`, `G` is the exact
  `ItemsTC.mechanismSimple`, `B` is `plateBrass` and `C` is vanilla comparator
  (`Items.field_151132_bS` in release bytecode).

The release class's constant pool was also read directly to confirm the
spaces in `" P "` and the complete `PGP`/`BCB` rows, independently of javap's
display whitespace. Source `ConfigRecipes` line 252 agrees. `ConfigItems`
line 197 registers mind variants `clockwork, biothaumic`; the catalogue's
metadata-zero mapping is therefore the clockwork form, not biothaumic.

The new datapack registration is
`data/thaumcraft/recipes/arcane/mind_clockwork.json`, using the established
`thaumcraft:arcane_shaped` serializer. The existing original book display
remains authoritative; no extra book record, invented research payment or
crafting completion callback is added. The existing workbench validates the
current research, grid, crystals and aura again when the result is taken.

The seven consumed grid items are three panes, one simple mechanism, two
brass plates and one comparator. A complex mechanism, repeater, redstone or
iron plate must not substitute for an exact part. The result receives no
ingredient NBT. Any unconsumed part of an input stack retains its own NBT.

Modern adaptations are limited to split metadata IDs and Forge ingredient
tags: old `paneGlass` maps to `forge:glass_panes`, and `plateBrass` maps to
`forge:plates/brass`, consistently with the established morphic resonator
recipe. Crystal compatibility uses the existing contained-aspect and legacy
primal item formats; both still pay exactly one matching Ignis and Ordo.

## Ingredients and the Brain Box route

No magical tallow or flint appears in the physical MindClockwork recipe.
Tallow matters because **completed HEDGEALCHEMY is a research parent** of
MINDCLOCKWORK, alongside BASEGOLEMANCY and ESSENTIASMELTER. That dependency
must not be removed merely because this arcane grid uses other items.

The simple mechanism is already the paid BASEARTIFICE recipe audited in
[EARLY-DEVICE-INGREDIENTS-BETA26-AUDIT.md](EARLY-DEVICE-INGREDIENTS-BETA26-AUDIT.md):
10 vis, Ignis 1/Aqua 1, two brass plates, two iron plates and one wooden stick.
Its brass plates have the existing ordinary three-ingot-to-three-plate recipe;
the brass ingot source is the original METALLURGY@1 crucible recipe, iron ingot
plus five Instrumentum. Iron plates retain their existing ordinary
three-iron-ingot-to-three-plate recipe. Glass panes and comparator use vanilla
recipes and their ordinary ingredients. This slice adds no replacement
smelting recipe, guaranteed ore bonus or Golemancy gate to those earlier items.

The existing `arcane/mnemonic_matrix.json` consumes the **actual clockwork
mind**, four iron plates and four amber into `brain_box ×1`: THAUMATORIUM,
50 vis, Terra 1/Ordo 1. It is the original `MnemonicMatrix` recipe from
`ConfigRecipes` line 250. Together with a paid simple mechanism and the new
mind, the three arcane steps debit **10 + 25 + 50 = 85 vis**, Ignis 2,
Aqua 1, Ordo 2 and Terra 1, before any legitimate equipment discounts.
The natural knowledge path is implemented separately; its progression tests
define actual scan/book transactions with explicit predecessor fixtures.
This recipe must not manufacture or complete research. The honest stage-two
boundary preserves the working component path without claiming a finished
Golemancy branch.

## Deliberately separate original paths

CONTROLSEALS is a sibling unlocked through completion of MINDCLOCKWORK; its
one stage displays GolemBell and SealBlank. These are not recipes of the mind
stage itself, and this component slice does **not** implement their mechanics
or register their recipes.

For later work, pinned bytecode also confirms:

- `GolemBell` is an **ordinary ungated shaped recipe**, `" QQ" / " QQ" /
  "S  "`, four `gemQuartz` and one `stickWood`. Do not invent vis, crystals or
  an arcane research predicate simply because the book displays it.
- `SealBlank` is an **arcane shapeless** CONTROLSEALS recipe: 20 vis, Aer 1,
  clay ball, actual magical tallow, `dyeRed` and any original `nitor`, output
  three blank seals. Tallow is not interchangeable with rotten flesh in this
  grid; rotten flesh belongs to its separate HEDGEALCHEMY@1 crucible source.

The Golem Press appears on the final MINDCLOCKWORK page but its paid Machina
manufacturing, selection GUI and usable constructed golems remain unfinished.
Completing an available research page or crafting a clockwork mind does not
establish those device/entity mechanics. Biothaumic minds, control seal tasks,
later materials and modules retain their own separate original gates.
MINDCLOCKWORK's server and book therefore stop at stage two; its third page
remains an archived reference rather than a reachable working press unlock.

## Defined validation

`thaumcraft.golemancy.components.ClockworkComponentGameTests` defines three
required server scenarios using the actual ArcaneWorkbench result slot:

1. Unknown research and entered stage one cannot pay; entered stage two
   exposes exactly 25 vis and Ignis/Ordo. Missing Ordo and 24 vis are atomic
   rejections. A valid craft debits seven physical parts and both crystals,
   preserves remaining brass-plate NBT, produces a clean mind/craft fact and
   leaves the research at stage two without granting CONTROLSEALS.
2. A genuinely paid simple mechanism is taken from the result slot, then
   consumed into a paid mind, which in turn is consumed into the existing
   mnemonic matrix recipe. Total debit is exactly 85 vis and all physical
   grids/crystals are empty afterwards; all three real craft facts appear.
3. Complex mechanism, repeater and iron-plate substitutions fail without
   debit. Malformed Perditio in the Ordo slot cannot partially spend the valid
   Ignis. Compatible legacy Ignis/Ordo then pay exactly one crystal each.

Research stages, aura and ordinary ingredient supplies in these component
tests are explicit fixtures. The intermediate outputs, result transactions
and physical payments execute the real server path. These definitions do not
claim completed QA, full survival or multiplayer validation. Actual integrated
suite, hidden-desktop client and artifact results belong in `VALIDATION.md`
after the parent task runs them.
