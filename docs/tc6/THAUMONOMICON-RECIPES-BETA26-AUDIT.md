# BETA26 Thaumonomicon recipe displays

Baseline: official Thaumcraft **6.1.BETA26**, JAR SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`,
pinned decompilation commit `954022bb777b7546281fb36df8522f0ba6b43f81`.
The release's research JSON is read directly from that JAR. Recipe declarations,
finite loops and custom display behavior come from `ConfigRecipes`,
`GuiResearchPage.addRecipesToList`, `InfusionEnchantmentRecipe`,
`InfusionRunicAugmentRecipe`, `EnumInfusionEnchantment` and `ConfigAspects`.

`scripts/extract_book_recipes.py` regenerates
`assets/thaumcraft/research/book_recipes.json`. It verifies the original JAR hash,
rejects unknown recipe expressions and expands only the 37 registered aspects;
the commented-out WEATHER/Tempestas experiment is deliberately excluded.
No generated row is installed as a crafting operation or grants knowledge.

## Coverage

Every one of the **203 distinct recipe keys** in all original stages/addenda is
classified: **184 direct recipes, 9 groups, 6 blueprints, 4 original unregistered
names**. There are **375 finite display recipes**: 149 ordinary crafting,
89 arcane, 78 crucible, 47 infusion, 8 infusion enchantment, 3 runic examples and
1 Salis Mundus custom display. The ordinary recipes include otherwise unreferenced
members needed for group expansion and output navigation. Multiblocks use the
separate blueprint catalogue/renderer.

Groups preserve original members: 37 aspect crystals, 16 nitor dyes, 16 banners,
17 tallow-candle recipes, 12 thaumium recipes, 12 void recipes, 3 brass recipes,
6 bauble recipes, 3 scribing-tool recipes and 39 label recipes. The release uses
the group `jarlabels`; the research reference `JarLabelEssence` itself has no
registered original recipe. The port's actual synchronized NBT-aware label recipe
still provides a working representative display for that name.

Original unregistered references are **`arcane_brick`, `arcane_stone`,
`nitorcolor`, `JarLabelEssence`**. They remain classified as absent in the original
catalogue; no guessed fallback cost or recipe is created. The release's real
`StoneArcane`, `BrickArcane`, `nitorgroup` and labels remain available under their
registered keys. `RecipeMisc` exists in the original API but is never registered
or referenced by base BETA26, so it has no fabricated base-mod display row.

## Preserved details

- Shaped grids retain their width, height and empty cells. Shapeless recipes have
  an explicit marker. Vis, six primal crystal costs and original research strings,
  including stage/compound gates, are retained independently of implementation.
- All compound/primal crystal, filled-phial, label and potion ingredients retain
  exact aspect/Potion NBT. Metadata-only forms map to the port's explicit item
  registry, including seal and turret variants. Wildcard wool, fish, coal and
  original metadata item variants retain alternative stacks.
- Infusion components retain order and central input. Object-array outputs mutate
  a copy of the central item with the original typed byte/int tag: fortress
  goggles/masks and verdant-heart variants do not become a generic output.
- Elemental tools/primal crusher retain their original `infench` lists with
  short IDs/levels. The eight enchantment previews show the original fake central
  tool, level-one output and instability 4. Costs grow dynamically for higher
  levels and existing enchants; the preview is labelled as a representative
  dynamic example. TC6 has no TC4 infusion XP requirement here.
- Runic examples show inputs 0/1/2 and outputs 1/2/3, salis plus 1/2/3 amber,
  Protect 40/60/100, Crystal/Energy 20/30/50 and instability 5/5/6, from the actual
  release formula `20 + 20 * 2^currentCharge`.
- Hedge duplication costs come from the pinned original aspect table.
  Clay-minus-dirt is Aqua5 and web-minus-string is Trap5, rather than all aspects
  of the output. Generic duplicated powders retain the release's whole costs.
- Liquid Death originally outputs Forge 1.12's universal fluid bucket. Since its
  modern bucket mechanic is not implemented, the book uses a vanilla bucket
  display with the original `Fluid:{FluidName:"liquid_death",Amount:1000}` NBT and
  an explicit display-adaptation note. This never behaves as a filled bucket.
- Copper/tin/silver/lead purification is conditionally registered in the original
  based on other mods' ores. Its reference rows retain a conditional note. An
  absent ore tag stays empty; a cluster is never substituted for raw ore.

## Runtime boundary and validation

`BookRecipeCatalog` is common read-only data. Getter results are fresh stacks,
ingredients, aspect lists and JSON copies. `BookRecipeViews` first resolves
current synchronized server recipes and crucible snapshots, including datapack
overrides. A matching modern recipe identity or exact output/NBT/count suppresses
the pinned display. Every remaining view has `reference=true`; a known research
gate does not turn that display into working gameplay.

Seven GameTests validate the complete key inventory, all 375 registered item/NBT
displays, original late costs/gates, typed infusion/potion/enchanted outputs,
dynamic runic examples, finite group variants and hedge/phial details. Root runs
the serialized Gradle server/client checks; this agent does not launch Minecraft.
Visual proof and final test counts belong in the build's validation report.
