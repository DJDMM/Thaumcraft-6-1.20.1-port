# Early arcane equipment, BETA26 audit

Status: five datapack recipes, six equipment transaction tests and three crystal interoperability tests implemented. The integrated Forge GameTest server passed all 208 required tests, including these nine. Client recipe pages and paid research transitions are checked separately; see [VALIDATION.md](../../VALIDATION.md).

## Required research keys (integration notice)

- Goggles: **completed `UNLOCKARTIFICE`**, 50 vis, **no primal crystals**.
- Enchanted Fabric: **completed `UNLOCKINFUSION`**, 5 vis, **no primal crystals**.
- Robe chest, legs, boots: **completed `UNLOCKINFUSION`**, 100 vis each, **no primal crystals**.
- `BASEARTIFICE` / `BASEINFUSION` do not gate these recipes in BETA26; the unlock entries carry them.
- Scribing tools bottle recipe and ink refill already work in this port; no additional prerequisite is needed here.

Verified both pinned source ConfigRecipes.java lines 194–198 and official JAR `javap -c -p thaumcraft.common.config.ConfigRecipes`, offsets 1310–1765. Saved bytecode under `work/early-configrecipes-javap.txt`. All five constructors pass `aconst_null` for their primal AspectList, so zero crystal cost is intentional, not a missing feature.

Original patterns/output (one item each):

| Original recipe | Pattern rows | Ingredients | Research | vis |
| --- | --- | --- | --- | --- |
| `EnchantedFabric` | ` S ` / `SCS` / ` S ` | Four ore `string`, wool wildcard metadata 32767 | UNLOCKINFUSION | 5 |
| `RobeChest` | `I I` / `III` / `III` | Eight fabric | UNLOCKINFUSION | 100 |
| `RobeLegs` | `III` / `I I` / `I I` | Seven fabric | UNLOCKINFUSION | 100 |
| `RobeBoots` | `I I` / `I I` | Four fabric | UNLOCKINFUSION | 100 |
| `Goggles` | `LGL` / `L L` / `TGT` | Four ore leather, two ore ingotBrass, two thaumometers | UNLOCKARTIFICE | 50 |

Modern equivalents: `forge:string`, `minecraft:wool` (all sixteen colors), `forge:leather`, `forge:ingots/brass`. Original recipe IDs are lowercased by ResourceLocation and modern folder-scoped recipe IDs are explicit adaptations. Book aliases `thaumcraft:enchantedfabric`, `thaumcraft:robechest`, `thaumcraft:robelegs`, `thaumcraft:robeboots`, `thaumcraft:goggles` resolve to the current `thaumcraft:arcane/...` recipes.

No cloth helmet exists in this slice or original BETA26 recipes. No ArcaneRecipe format or transaction changes are needed. Existing equipment discount applies at crafting through `int(vis*(1-discount))`.

## Added server coverage

`thaumcraft.research.EarlyArcaneGameTests` has six required tests:

1. Two real paid thaumometers (missing crystal fails without mutation) become goggles. Goggles require canonical completion, fail at 49 vis, charge 50 vis and consume both paid lenses without any primal.
2. All sixteen wool colors make one fabric for four string and 5 vis with empty crystal slots. Locked attempts fail.
3. Eight fabric → chest robe, 100 vis, real result-slot pickup.
4. Seven fabric → leggings, 100 vis, real shift-click destination.
5. Four fabric → boots, 100 vis, pattern shifted to bottom two rows and real hotbar swap.
6. Actual worn goggles/cloth robe discounts: 5 vis → 4, 100 vis → 87. Menu previews and committed charges agree; 86-vis attempted robe leaves inputs and aura intact.

The three robe tests preserve seven installed crystals of each primal and record actual `PlayerEvent.ItemCraftedEvent` evidence. Recipe transaction tests set research stages with a package-private fixture; root's progression tests validate the actual unlock payments/parents.

New recipe IDs: `thaumcraft:arcane/goggles`, `thaumcraft:arcane/enchanted_fabric`, `thaumcraft:arcane/robe_chest`, `thaumcraft:arcane/robe_legs`, `thaumcraft:arcane/robe_boots`. All outputs and shapes match BETA26. Forge 47.4.10 bundled tags for `forge:leather` and `forge:string` were verified in its mapped JAR.

## Follow-up: crystal interoperability

Implemented common `AspectCrystalItem.isCrystal`, `crystalAspect`, `primalAspect` and `matchesPrimal` helpers. Original single-aspect NBT and earlier `vis_crystal_*` IDs share slot validation, arcane payment/shift routing, Salis tag identity, and acquisition facts. Legacy IDs remain compatible; mixed representations of the same aspect never count twice for Salis.

**Salis aspect rule:** BETA26 `RecipeMagicDust` accepts **any three different contained aspects**, including single compound-aspect crystals. It does not call `isPrimal`; bytecode offsets 193–268 read the first contained tag and compare uniqueness. Hence compound-aspect crystals are rejected by the arcane six primal slots but intentionally accepted by Salis. Reference `work/salis-mundus-javap.txt`; pinned `RecipeMagicDust.java`. Do not convert Salis to a primal-only rule.

`SlotCrystal` bytecode offsets 28–48 reads only `getAspects()[0]`; it does not literally require a list size of one. Valid original crystals are single-aspect. Null/empty/multiple-aspect NBT is explicitly hardened here by rejection rather than reproducing BETA26's null crash or accepting the first of malformed entries. Reference `work/slot-crystal-javap.txt`. Acquisition still uses item class regardless of initialization, as original `PlayerEvents` does.

Three new `CrystalInteropGameTests` exercise actual paid thaumometer output with six original NBT primals, shift routing, all slot gates, bypass attempts via direct inventory setters, mixed original/legacy Salis with true vanilla result clicks and original remainders, duplicate-tag rejection, and real partial ItemEntity pickup enabling Strange Dreams. The Salis test also guards original valid compound-aspect acceptance. All three passed in the integrated 208-test server run.
