# Infusion recipe behavior: official TC6 6.1.BETA26

Audit date: 3 October 2026. Target: Forge 1.20.1 / Java 17. This document records verified original behavior and the recipe implementation contract; it does not claim the matrix or all dependent gameplay systems are implemented.

Baseline: `work/Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256 `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`; pinned readable source commit `954022bb777b7546281fb36df8522f0ba6b43f81`. `javap -p -c` from JDK 17 verifies the methods below. The original JAR is authoritative; no TC4/TC5 or addon rules enter this contract.

## Binary evidence

| Class | SHA-256 of original class bytes |
| --- | --- |
| `thaumcraft/api/crafting/InfusionRecipe` | `f1db09ed48dfaafe9f1e8dffc829ca5df984c9adaacda8b415b3a503930353eb` |
| `thaumcraft/common/lib/crafting/InfusionEnchantmentRecipe` | `5367e0c2136c601ea5f68bf2e03e14c9a79bfa13c851710831dd9bda7261652c` |
| `thaumcraft/common/lib/crafting/InfusionRunicAugmentRecipe` | `19a1112da7369144410d0009d88fa442440282ee627c49e90aa1a5a6c9e00231` |
| `thaumcraft/api/crafting/IngredientNBTTC` | `1f4f0f18f08e428b9c40cbce2a43274b51a9f2d31d94554bf492d0868f069c16` |
| `thaumcraft/api/ThaumcraftApiHelper` | `3c955c806734b6c75a05e478e2a897517e18aefc9fc830139f6e2a2e13750b47` |
| `thaumcraft/api/ThaumcraftInvHelper` | `7b8014bb04e7fb9c10f957dedb119dba86a8eeeef863f814e41b156e73d27f7f` |
| `thaumcraft/common/lib/enchantment/EnumInfusionEnchantment` | `a235212468df2cea88e634225473e7dacd7b7b658ce51fd936ec18a88bbceae9` |
| `thaumcraft/common/lib/capabilities/PlayerKnowledge$DefaultImpl` | `5b6cd27a15ae197a15e193c0bcdcfb13f3aba35eeefbe5ab520e41f25967c774` |
| `thaumcraft/common/config/ConfigRecipes` | `487d32f43df9277fa845c129bc549da0126f34902a2d5089f9d97d204eb0f4e2` |
| `thaumcraft/common/tiles/crafting/TileInfusionMatrix` | `8495c28324c5c0661be382492cdbcfa8fec4aa6e5a3faa214ef3a523ffcd9b0d` |

`ConfigRecipes.initializeInfusionRecipes` has 56 real registration call sites: **47 ordinary + 8 enchantment + 1 runic**. Nine fake call sites expand into **11 display-only** registrations: eight enchantment previews and three runic previews. These are in the separate fake catalogue. `RunicArmorFake0/1/2` and `IE*FAKE` must never be registered as working recipes.

## Ordinary matching and ingredient semantics

`InfusionRecipe.matches` bytecode 0–65 requires a nonnull central ingredient, `IPlayerKnowledge.isResearchKnown(research)`, central matching (or `Ingredient.EMPTY` wildcard), and a bijection from every supplied component to every declared component via Forge `RecipeMatcher.findMatches`. Order around pedestals is irrelevant; duplicate ingredients need separate occupied pedestals. A stack count of two on one pedestal is one input, not two components. Extra occupied pedestals prevent an exact recipe match.

The release matrix copies each occupied component pedestal stack and its whole central stack before matching. It does not flatten counts into ingredient multiplicity. Modern plans keep that actual order/NBT for subsequent runtime checks, rather than replacing components with canonical examples.

`ThaumcraftApiHelper.getIngredient` preserves an existing `Ingredient`; tagged ItemStacks become `IngredientNBTTC`; everything else delegates to Forge CraftingHelper. Retain three distinctions:

- `Ingredient.fromItem(item)` matches all legacy damage values. This includes the primordial pearl in greater focus/Primal Crusher and several tool components.
- Plain `new ItemStack(item)` has exact legacy metadata/damage zero; explicit `32767` is a wildcard. The port maps metadata variants into separate registry IDs and compares modern damage separately for damageable items.
- `IngredientNBTTC.apply` bytecode 6–42 requires same item **and exact damage**, then `areItemStackTagsEqualRelaxed(prime, actual)`. Each required top-level tag must be present and `Tag.equals` the complete required value. Extra top-level actual tags are permitted; nested compounds/list values must be exactly equal, including numeric tag types and list order. A recursive subset comparator is wrong. Missing actual NBT is safely rejected instead of reproducing the original null dereference.

This matters for aspect crystals, SealButcher's guard seal and strong healing/regeneration potions. The explicitly constructed enchanted-book IngredientNBTTC has no required tag, so **any enchanted book** satisfies it; vanilla enchantment identity/level is not a component restriction. ESSENCE's plain crystalEssence accepts any aspect. SOUNDING and MirrorHand use `Items.MAP`, which maps to modern `filled_map`, not the empty-map item.

The read-only `BookRecipeCatalog` contains accurate finite display stacks/costs, but deliberately uses ordinary `Ingredient.of` for rendering and loses wildcard/exact damage and mutation-operation information. It is not a gameplay recipe registry. New gameplay data must reconstruct those source distinctions and keep source identities separate from display FAKE names.

## Research predicate

Original `PlayerKnowledge$DefaultImpl.isResearchKnown` bytecode 0–59 returns false for null, true for empty, otherwise requires the bare key in the player's research set and, for `KEY@N`, `getResearchStage(KEY) >= N`. Bare research needs only to be opened/in progress, **not completed**. Ordinary and runic matching call this method directly; enchantment matching calls `ThaumcraftCapabilities.knowsResearch`, which supports AND/OR groups but resolves the same known predicate. All eight base enchantment registrations use bare `INFUSIONENCHANTMENT`.

Modern `PlayerKnowledge.isResearchKnown` is the corresponding predicate. Its `knowsResearch` method retains strict completion/legacy aliases for existing arcane recipes and is not interchangeable. Existing survival reachability remains root-owned; registering a gated recipe does not make all archived late researches obtainable.

## Output modes and runtime boundary

Ordinary ItemStack output replaces the central item using a fresh copy of the declared result. Its original tags are the result template's tags, not all central tags. The matrix's `craftingFinish` bytecode 46–129 transfers **relative damage** from a damaged, damageable input to a damageable, currently undamaged replacement: `(int)(newMaxDamage * (float)oldDamage / oldMaxDamage)`. It does not copy central vanilla enchantments/custom names into ordinary replacement outputs.

Six ordinary registrations instead return an Object array `(tag name, typed tag)`. The matrix extracts that operation at craft start and overwrites one tag on the central item at finish. The modern plan assembles a full copied central stack at start, then changes only that typed tag:

| Recipe | Mutation |
| --- | --- |
| HelmGoggles | `goggles:1b` |
| MaskGrinningDevil | `mask:0` (int) |
| MaskAngryGhost | `mask:1` (int) |
| MaskSippingFiend | `mask:2` (int) |
| VerdantHeartLife | `type:1b` |
| VerdantHeartSustain | `type:2b` |

Mutation preserves custom names, all other NBT, damage and copied capabilities. The original matcher does not prevent reapplying the same goggles, changing an existing mask or replacing a heart type. Do not invent those restrictions.

Elemental replacement templates have original short `infench` entries: axe COLLECTOR1 then BURROWING1; pick REFINING1 then SOUNDING2; sword ARCING2; shovel DESTRUCTIVE1; crusher DESTRUCTIVE1 then REFINING1. Hoe has no predefined infusion enchantment. These are result-template data, not central enchantment preservation.

The recipe plan returns **unscaled** aspects and original recipe instability. `TileInfusionMatrix.craftingStart` bytecode 222–238 clamps matrix `costMult` to at least 0.5f, then 398–433 multiplies each already computed aspect cost using float and truncates to int; nonpositive amounts are discarded. Stability events, material consumption/container remainders, cost modifiers and crafting event dispatch remain matrix responsibilities. There is no recipe XP payment in these TC6 classes.

## Infusion enchantment rules

The original enum has 13 IDs; only eight recipes are registered. IDs 7/8 VISBATTERY/VISCHARGE and 9/10/11 SWIFT/AGILE/INFESTED remain unregistered in base BETA26. Vanilla enchantments and infusion enchantments are separate systems.

| Real recipe | Original enum ID | Max level | Eligible original classes | Base aspects | Second component (first is any enchanted book) |
| --- | ---: | ---: | --- | --- | --- |
| IEBURROWING | 2 | 1 | axe, pickaxe | Senses80, Earth150 | rabbit foot |
| IECOLLECTOR | 0 | 1 | axe, pickaxe, shovel, weapon | Desire80, Water100 | lead |
| IEDESTRUCTIVE | 1 | 1 | axe, pickaxe, shovel | Aversion200, Entropy250 | TNT |
| IEREFINING | 4 | 4 | pickaxe | Order80, Exchange60 | Salis Mundus |
| IESOUNDING | 3 | 4 | pickaxe | Senses40, Fire60 | filled map |
| IEARCING | 5 | 4 | weapon | Energy40, Air60 | redstone block |
| IEESSENCE | 6 | 5 | weapon | Beast40, Flux60 | any aspect crystal |
| IELAMPLIGHT | 12 | 1 | axe, pickaxe, shovel | Light80, Air20 | any registered nitor |

`InfusionEnchantmentRecipe.matches` bytecode 0–545 rejects empty central items, unknown research and levels at/above maximum. Original weapon eligibility means an ATTACK_DAMAGE modifier key on the central item's MAINHAND attributes, regardless of positive amount. Tool class checks apply to ItemTool; armor class checks map HEAD/CHEST/LEGS/FEET to helm/chest/legs/boots; bauble class checks map amulet/belt/ring and allow generic bauble; chargable requires IRechargable. None of the eight base entries use armor/bauble/chargable, but the general API supports them.

Modern tool classes map to DiggerItem and Forge ToolActions (axe/pickaxe/shovel); HoeItem must not acquire pickaxe/shovel classification just because it is damageable. Original weapon attribute semantics are retained. Primal Crusher's dual pickaxe/shovel actions qualify.

Costs: let `L = current target level + 1`. Count valid entries from `getInfusionEnchantments`, which includes duplicate valid IDs and ignores IDs outside 0..12. Subtract one if that list contains the target. Java float multiplier is **`L + otherCount * 0.33f`**, and each base amount is multiplied and truncated to int. It is not 1/3 double arithmetic or ceil. Vanilla `Enchantments` do not contribute. Null/empty input or `L > maxLevel` yields no costs. Instability remains **4** at every level.

Output copies the central stack, preserves vanilla enchantments/name/damage/capabilities, and increments or appends one target `infench` compound with **short** `id` and `lvl`. Existing valid/invalid list entries survive. When `nextInt(10) < number of existing valid infusion entries`, signed-byte `TC.WARP` is incremented by one. First infusion on an unenchanted item cannot add warp; existing vanilla enchantments alone cannot cause it. This is item warp, not a new direct player warp award.

The original matrix queries output twice at start (the first result is used only for Object-array type checking); its final output is fixed before the craft proceeds. A modern detached plan samples its actual output once. Preview, matching and cost getters do not sample randomness or mutate inventory; tests can inject deterministic random sources into the plan builder.

## Runic augmentation

Real `RunicArmor` accepts an ItemArmor or an IBauble with known RUNICSHIELDING. The base matcher does not restrict it to the fake mundane ring. Current original charge is the signed byte `TC.RUNIC`, default zero. `getComponents(input)` bytecode 0–60 returns one Salis Mundus, one amber and one more separate amber for every positive current charge.

For current charge C:

- Protect = `20 + (int)(20.0 * Math.pow(2.0, C))`.
- Crystal and Energy = Protect / 2 using integer division.
- Instability = `5 + C / 2` using Java integer division.
- Output copies the central item and sets `TC.RUNIC` to byte C+1.

| Current → next | Amber pedestals | Protect | Crystal | Energy | Instability |
| --- | ---: | ---: | ---: | ---: | ---: |
| 0 → 1 | 1 | 40 | 20 | 20 | 5 |
| 1 → 2 | 2 | 60 | 30 | 30 | 5 |
| 2 → 3 | 3 | 100 | 50 | 50 | 6 |
| 3 → 4 | 4 | 180 | 90 | 90 | 6 |

Original high-charge conversion saturates the double→int, then adding 20 overflows the int: charge 27+ produces nonpositive cost and therefore an empty AspectList. Byte 127→128 wraps negative. Modern hardening rejects negative charge and charge >26 when starting a new augment, preventing a free overflow upgrade; normal costs are unchanged. Original Baubles interfaces have no complete modern slot integration yet; known baseline accessory IDs remain augmentation-eligible, without claiming their wear callbacks/slots work.

## Full ordinary registry inventory

The source-line column refers to pinned ConfigRecipes.java. All costs are unmodified recipe costs before matrix modifiers. Detailed ordered components and typed display NBT are in `book_recipes.json`, while gameplay data additionally carries exact/wildcard damage and output-mode distinctions.

| Recipe path | Research | Instability | Base aspect amounts | Source line |
| --- | --- | ---: | --- | ---: |
| `sealharvest` | `SEALHARVEST` | 0 | herba 10, sensus 10, humanus 10 | 268 |
| `sealbutcher` | `SEALBUTCHER` | 0 | bestia 10, sensus 10, humanus 10 | 269 |
| `sealbreak` | `SEALBREAK` | 1 | instrumentum 10, perditio 10, humanus 10 | 270 |
| `crystalclusterair` | `CRYSTALFARMER` | 0 | aer 10, vitreus 10, vinculum 5 | 271 |
| `crystalclusterfire` | `CRYSTALFARMER` | 0 | ignis 10, vitreus 10, vinculum 5 | 272 |
| `crystalclusterwater` | `CRYSTALFARMER` | 0 | aqua 10, vitreus 10, vinculum 5 | 273 |
| `crystalclusterearth` | `CRYSTALFARMER` | 0 | terra 10, vitreus 10, vinculum 5 | 274 |
| `crystalclusterorder` | `CRYSTALFARMER` | 0 | ordo 10, vitreus 10, vinculum 5 | 275 |
| `crystalclusterentropy` | `CRYSTALFARMER` | 0 | perditio 10, vitreus 10, vinculum 5 | 276 |
| `crystalclusterflux` | `CRYSTALFARMER` | 4 | vitium 10, vitreus 10, vinculum 5 | 277 |
| `focus_2` | `FOCUSADVANCED@1` | 3 | praecantatio 25, ordo 50 | 278 |
| `focus_3` | `FOCUSGREATER@1` | 5 | praecantatio 25, ordo 50, vacuos 100 | 279 |
| `jarbrain` | `JARBRAIN` | 4 | cognitio 25, sensus 25, exanimis 25 | 280 |
| `visamulet` | `VISAMULET` | 6 | auram 50, potentia 100, vacuos 50 | 281 |
| `mirror` | `MIRROR` | 1 | motus 25, tenebrae 25, permutatio 25 | 291 |
| `mirrorhand` | `MIRRORHAND` | 5 | instrumentum 50, motus 50 | 292 |
| `mirroressentia` | `MIRRORESSENTIA` | 2 | motus 25, aqua 25, permutatio 25 | 293 |
| `elementalaxe` | `ELEMENTALTOOLS` | 1 | aqua 60, herba 30 | 297 |
| `elementalpick` | `ELEMENTALTOOLS` | 1 | ignis 30, metallum 30, sensus 30 | 301 |
| `elementalsword` | `ELEMENTALTOOLS` | 1 | aer 30, motus 30, aversio 30 | 304 |
| `elementalshovel` | `ELEMENTALTOOLS` | 1 | terra 60, fabrico 30 | 307 |
| `elementalhoe` | `ELEMENTALTOOLS` | 1 | ordo 30, herba 30, perditio 30 | 308 |
| `bootstraveller` | `BOOTSTRAVELLER` | 1 | volatus 100, motus 100 | 333 |
| `mindbiothaumic` | `MINDBIOTHAUMIC` | 4 | cognitio 50, machina 25 | 334 |
| `arcanebore` | `ARCANEBORE` | 4 | potentia 25, terra 25, machina 100, vacuos 25, motus 25 | 335 |
| `lampgrowth` | `LAMPGROWTH` | 4 | herba 20, lux 15, victus 15, instrumentum 15 | 336 |
| `lampfertility` | `LAMPFERTILITY` | 4 | bestia 20, lux 15, victus 15, desiderium 15 | 337 |
| `thaumiumfortresshelm` | `ARMORFORTRESS` | 3 | metallum 50, praemunio 20, potentia 25 | 338 |
| `thaumiumfortresschest` | `ARMORFORTRESS` | 3 | metallum 50, praemunio 30, potentia 25 | 339 |
| `thaumiumfortresslegs` | `ARMORFORTRESS` | 3 | metallum 50, praemunio 25, potentia 25 | 340 |
| `voidrobehelm` | `VOIDROBEARMOR` | 6 | metallum 25, sensus 25, praemunio 25, potentia 25, alienis 25, vacuos 25 | 341 |
| `voidrobechest` | `VOIDROBEARMOR` | 6 | metallum 35, praemunio 35, potentia 25, alienis 25, vacuos 35 | 342 |
| `voidrobelegs` | `VOIDROBEARMOR` | 6 | metallum 30, praemunio 30, potentia 25, alienis 25, vacuos 30 | 343 |
| `helmgoggles` | `FORTRESSMASK` | 5 | sensus 40, auram 20, praemunio 20 | 344 |
| `maskgrinningdevil` | `FORTRESSMASK` | 8 | cognitio 80, victus 80, praemunio 20 | 345 |
| `maskangryghost` | `FORTRESSMASK` | 8 | perditio 80, mortuus 80, praemunio 20 | 346 |
| `masksippingfiend` | `FORTRESSMASK` | 8 | exanimis 80, victus 80, praemunio 20 | 347 |
| `primalcrusher` | `PRIMALCRUSHER` | 6 | terra 75, instrumentum 75, perditio 50, vacuos 50, aversio 50, alienis 50, desiderium 50 | 351 |
| `verdantheart` | `VERDANTCHARMS` | 5 | victus 60, ordo 30, herba 60 | 352 |
| `verdantheartlife` | `VERDANTCHARMS` | 5 | victus 80, humanus 80 | 355 |
| `verdantheartsustain` | `VERDANTCHARMS` | 5 | desiderium 80, aer 80 | 358 |
| `cloudring` | `CLOUDRING` | 1 | aer 50 | 359 |
| `curiosityband` | `CURIOSITYBAND` | 5 | cognitio 150, vacuos 50, vinculum 100 | 360 |
| `charmundying` | `CHARMUNDYING` | 2 | victus 25 | 361 |
| `causalitycollapser` | `RIFTCLOSER` | 8 | alienis 50, vitium 50 | 368 |
| `voidsiphon` | `VOIDSIPHON` | 7 | alienis 50, perditio 50, vacuos 100, fabrico 50 | 369 |
| `voidseerpearl` | `VOIDSEERPEARL` | 8 | cognitio 150, vacuos 150, praecantatio 100 | 370 |

## Required implementation validation

Meaningful GameTests should establish: exactly 56 working registrations and no fake entries; full ordinary central/component/cost parity; order-independent exact component count; duplicate predicates with a non-greedy bijection; exact aspect/Potion NBT with tolerated extra root tags but rejected altered nested lists; wildcard versus zero damage; known versus completed research and @ stages; six central mutations preserving unrelated state; original predefined elemental result tags; deterministic enchantment cost/warp behavior, max-level rejection and invalid/duplicate entry handling; dynamic runic component/cost/output formulas and overflow rejection; fresh detached plans; safe JSON/network round trips; no inventory or knowledge changes from matching/previews.

Root owns Gradle/server/client QA and matrix integration. This agent does not launch Minecraft or claim those tests have passed before root executes them.
