# HEDGEALCHEMY — release recipe and candle audit

Baseline: official **Thaumcraft 6.1.BETA26**, Minecraft 1.12.2.
Pinned JAR SHA256:
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
This is the original TC6 branch, not the TC4/TC5 aspect-link puzzle.

## Evidence and initialization order

The reference source is commit `954022bb777b7546281fb36df8522f0ba6b43f81`.
`ConfigRecipes.java:144–145` registers tallow/leather during normal recipes;
`:468–475` registers the ungated ordinary candle recipe and sixteen dye recipes;
`:548–556` registers the remaining eight crucible recipes in `postAspects`.
`CommonProxy.java:76–82` first runs `ConfigAspects.postInit()`, then
`ConfigRecipes.postAspects()`. The constructors do not contain guessed fixed
costs for the four duplication recipes or the three material transformations.

The pinned JAR's `javap -c -p ConfigRecipes` confirms the `AspectList(ItemStack)`
constructors and `.remove(AspectList)` calls. The pinned `AspectList(ItemStack)`
copies the result of `AspectHelper.getObjectAspects`; it never scales aspects by
the stack count. `remove(Aspect,int)` removes an entry when subtraction leaves
zero or a negative amount. It does not preserve a negative refund.

Reproducible local bytecode captures are `work/clockwork-021-recipes-javap.txt`,
`work/hedge-021-aspectlist-javap.txt`, `work/hedge-021-candle-javap.txt` and
`work/hedge-021-blocktc-javap.txt` in the workspace. These are inspection data,
not instructions or packaged mod resources. The complete ordinary vanilla
values come from `ConfigAspects` item/ore-dictionary definitions, including
string `Bestia5/Fabrico1`, wheat `Herba5/Victus5`, cobweb `Vinculum5/Bestia1`
and clay `Aqua5/Terra5`. Thus web requires only Vinculum5, not Bestia or Fabrico;
string retains Bestia5/Fabrico1, with no cost for wheat's Herba/Victus.

## All ten crucible recipes

| Original key | Required entered stage | Catalyst ×1 | Output | Cost with unmodified BETA26 vanilla aspects |
| --- | --- | --- | --- | --- |
| hedge_tallow | HEDGEALCHEMY@1 | Rotten Flesh | Tallow ×1 | Ignis1 |
| hedge_leather | HEDGEALCHEMY@1 | Rotten Flesh | Leather ×1 | Aer3, Bestia3 |
| hedge_gunpowder | HEDGEALCHEMY@2 | Gunpowder | Gunpowder ×2 | Ignis10, Perditio10, Alkimia5 |
| hedge_slime | HEDGEALCHEMY@2 | Slimeball | Slimeball ×2 | Aqua5, Victus5, Alkimia1 |
| hedge_glowstone | HEDGEALCHEMY@2 | dustGlowstone | Glowstone Dust ×2 | Sensus5, Lux10 |
| hedge_dye | HEDGEALCHEMY@2 | Dye metadata0 | Ink Sac ×2 | Aqua2, Bestia2 |
| hedge_clay | HEDGEALCHEMY@3 | Dirt metadata0 | Clay Ball ×1 | Aqua5 |
| hedge_string | HEDGEALCHEMY@3 | Wheat | String ×1 | Bestia5, Fabrico1 |
| hedge_web | HEDGEALCHEMY@3 | String | Cobweb ×1 | Vinculum5 |
| hedge_lava | HEDGEALCHEMY@3 | Empty Bucket | Lava Bucket ×1 | Ignis15, Terra5 |

The existing port resource IDs `thaumcraft:tallow` and `thaumcraft:leather` stay
stable for old datapacks and saves. The original recipe names remain in the
book archive. The eight new working recipe IDs keep `thaumcraft:hedge_*`.
The original glowstone ore ingredient maps to `forge:dusts/glowstone`.
Old vanilla dye metadata0 maps strictly to `minecraft:ink_sac`; modern black
dye is not a substitute in the ink duplication recipe. `minecraft:web` maps
to `minecraft:cobweb`. Dirt remains ordinary dirt, rather than the modern
whole dirt tag that also includes additional blocks.

`CrucibleRecipes.Entry.cost()` returns a detached list. A new optional
`aspect_cost` JSON object contains the plain one-item result ID and optional
plain one-item `subtract` ID. Four duplication formulas copy the current
server aspect definition; three transformation formulas subtract the entire
original catalyst definition. Fixed tallow/leather/lava recipes do not use
this field. Bundled `aspects` retains the audited vanilla values as reference,
while runtime and Thaumatorium consumers use the `cost()` accessor.
`AspectRegistry.getAspects` already returns detached definitions/resolution
results, so resolving/subtracting cannot mutate its snapshot or another recipe.

This is a deliberate modern reload adaptation: the original snapshots its
formulas once after all post-initialization aspect registration. The port
resolves against the latest fully published server aspect snapshot at use,
avoiding loader-order dependence and reflecting datapack aspect changes.
The vanilla results above remain identical. Resolution stays subject to the
existing bounded AspectRegistry resolver; source stacks have count1 and no
player-supplied NBT. Addons that changed aspects in BETA26 similarly changed
its postAspects recipe costs; the source formula is preserved, not replaced
by the archived book's fixed display numbers.

All recipes still use the already-audited crucible transaction: heated water,
original highest-total-cost recipe selection, exact one catalyst and aspect
debits, up-to-50mB water debit, protected physical output and committed craft
proof. Leather therefore wins over tallow if all its six required aspects are
available. The last1mB permits one final craft. An unknown or insufficient
recipe falls back to normal catalyst dissolution, without manufacturing
output, successful-craft water debit or craft evidence. No container remainder
is created in addition to the lava bucket: the empty bucket is its catalyst.

## Stage and old-profile compatibility

The canonical parent is completed BASEALCHEMY. Stage1 advertises tallow and
leather; stage2 adds duplication and candles; stage3 adds transformations;
stage4 is the final text. Each paid first/second/third stage retains one raw
observation point (16 raw units) and its original craft evidence.
Canonical progression is owned by `ResearchProgression`, separately from
these working recipes. Recipes open on **entered** `@1/@2/@3` stages; crafting
itself does not advance or complete HEDGEALCHEMY.

Existing `PlayerKnowledge.knowsResearch` retains `PORT_TALLOW` as recipe-only
access to HEDGEALCHEMY@1. It does not grant stage2, canonical stages or completed
HEDGEALCHEMY/MINDCLOCKWORK prerequisites. New profiles cannot acquire the
superseded lesson. This is old port compatibility, not an original TC6 shortcut.
The shipped `required_craft` value `thaumcraft:leather` is an unregistered
obsolete ID in the pinned JAR and is ignored, as already audited for the book.
It must not be repaired into an invented mandatory vanilla leather craft.
The real leather recipe nevertheless records its actual `minecraft:leather`
craft evidence after manufacture. Tallow retains its valid original proof.

## Candles: ordinary recipes and working physical blocks

Original `TallowCandle`: one vertical string above two individual tallow gives
**three white candles**. The recipe is ordinary crafting and has no direct
research/vis/crystal cost. Every one of the sixteen original colours also has
an ungated shapeless recipe: one matching dye and **any colour of tallow candle**
gives one recoloured candle. `thaumcraft:tallow_candles` contains all sixteen
BlockItems; no vanilla candle is silently accepted. Original `lightblue` and
`silver` IDs stay stable; modern dye names are `light_blue` and `light_gray`.

Modern dye tags supply the sixteen new dye items; the four original 1.12 dye
forms remain accepted separately for candle recolouring: bone meal → white,
lapis → blue, cocoa beans → brown, ink sac → black. These extra modern dye
items are an explicit registry-split adaptation, not a change to the strict
ink-sac duplication catalyst.

The sixteen original block/item IDs, tint callbacks, models, textures and
animation assets are retained. Catalogue registration now creates
`TallowCandleBlock` and ordinary `BlockItem`, so a working candle no longer
shows the catalogue's visual-only tooltip. The BETA26 `BlockCandle` bytecode
confirms:

- Hardness0.1, cloth sound, coloured map value, no collision/solid face.
- Exact selection AABB `(0.375,0,0.375)–(0.625,0.5,0.625)`.
- Always-lit light14: old `setLightLevel(.9375)` truncates `.9375×15` to14.
- Effective explosion resistance1.5. `BlockTC` first calls resistance2, then
  hardness1.5; old resistance writes ×3 and hardness raises the internal value
  to at least hardness×5. The later candle hardness0.1 does not lower it; old
  getter divides by5. Modern `strength(.1,1.5)` stores the effective values.
- Upper-face support below, refused unsupported placement, single self-drop
  and removal on lost support. There is no correct-tool requirement.
- Smoke and flame at centre `(x+.5,y+.7,z+.5)` on display ticks; no invented
  extinguish, ignition, count-stacking or waxing mechanic.

Forge's modern `isFaceSturdy(...,UP)` is the explicit replacement for old
`World.isSideSolid(...,UP)`. The ordinary shape/model registration avoids a
new BE or model migration. Existing InfusionStability recognizes the same
`candle_*` IDs with original0.1 per matching pair and per-block diminishing
returns; original `hasSymmetryPenalty=false` applies to matching candles,
whereas different blocks/unpaired positions still use the matrix mismatch
penalty. No separate vanilla candle behavior or vanilla lighting state is used.

## Meaningful verification added

`HedgeAlchemyGameTests` adds **nine** server definitions:

1. Ten pinned costs/stages, seven source formulas, detached results, strict
   ink-sac catalyst and complete-list positive subtraction.
2. Higher-cost leather selection followed by independently paid tallow,
   unchanged entered stage and real craft facts.
3. Existing PORT_TALLOW profile pays only tier1 and cannot bypass tier2 or
   canonical completion.
4. All four actual two-output duplications with exact aspects/water/one
   catalyst, preserved remaining catalyst NBT and real craft evidence.
5. Actual dirt-to-clay and wheat-to-string-to-web route with only positive
   difference costs and physically produced string used as the next input.
6. Actual lava output using an empty bucket and the last1mB, no extra empty
   bucket, and waterless refusal without payment.
7. Original unknown/insufficient duplication fallback dissolution, with no
   manufactured output, water debit or craft proof.
8. Real CraftingMenu result-slot payment for the three-white-candle grid,
   all256 colour/dye routes and all four original dye items, no invented gate.
9. All sixteen original outlines/light/hardness/resistance/item types,
   absence of collision/tool gate, exact single drop and removal on lost support.

These definitions are not a claim of passed execution. The root task runs
integrated server/client/build validation and records the actual results in
`VALIDATION.md` and the versioned artifact report. Fixtures directly entering
research stages and preheating/loading a crucible isolate manufacturing
behavior; they do not establish independent multiplayer or an entire fresh
survival playthrough.
