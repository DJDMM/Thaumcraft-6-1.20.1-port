# Early device ingredients — BETA26 audit

Актуализация0.21: исходные контракты ниже сохранены как история0.20. Естественный компонентный путь `MINDCLOCKWORK@2 → mind_clockwork → Brain Box` теперь доступен; полное завершение разума, пресс/ИИ/печати ещё не перенесены. Подробности: [стадии0.21](GOLEMANCY-COMPONENT-PROGRESSION-BETA26-AUDIT.md), [физический рецепт](CLOCKWORK-COMPONENTS-BETA26-AUDIT.md), [Hedge Alchemy](HEDGE-ALCHEMY-BETA26-AUDIT.md).

Baseline: `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`;
source mirror commit `954022bb777b7546281fb36df8522f0ba6b43f81`.
Source references: `ConfigRecipes` lines 184–185, 237, 250;
`ConfigItems` line 184; `ToolEvents.harvestBlockEvent` line 187.
`javap -c -p` on the pinned binary confirms all three crafting gates, prices,
patterns and crystals, and the harvest bonus's double thresholds and metadata 10.

## Corrected research dependency

The earlier 0.19 centrifuge documents called its ingredient acquisition a
Golemancy dependency. That was inaccurate. The release registers the simple
and complex mechanisms under **BASEARTIFICE**, and the morphic resonator under
**BASEALCHEMY**. Both are already implemented canonical roots. Neither adds a
new research key, stage, knowledge debit, Golemancy prerequisite or scan fact.

`BASEARTIFICE` has parent `UNLOCKARTIFICE`, one empty requirement stage and
`ROUND,HIDDEN` metadata. `BASEALCHEMY` likewise has `UNLOCKALCHEMY`, one empty
stage and `ROUND,HIDDEN`. Their existing normal unlock paths remain authoritative.
Bare arcane recipe research uses BETA26 `isResearchKnown` and permits stage 1;
crafting does not advance or complete an entry. Existing strict focus gates
are unaffected.

## Physical arcane recipes

| Result | Research | Vis | Crystals | Pattern and ingredients |
| --- | --- | ---: | --- | --- |
| `mechanism_simple` ×1 | BASEARTIFICE | 10 | Ignis 1, Aqua 1 | ` B /ISI/ B `: B brass plate, I iron plate, S wooden stick |
| `mechanism_complex` ×1 | BASEARTIFICE | 50 | Ignis 1, Aqua 1 | ` M /TQT/ M `: M simple mechanism, T thaumium plate, Q piston |
| `morphic_resonator` ×1 | BASEALCHEMY | 50 | Aer 1, Ignis 1 | ` G /BSB/ G `: G glass pane, B brass plate, S rare earth |

OreDictionary mappings are represented by existing Forge ingredient tags:
`forge:plates/brass`, `forge:plates/iron`, `forge:plates/thaumium`,
`forge:rods/wooden` and `forge:glass_panes`. The recipe uses the real registered
catalogue result IDs and original count one. The existing workbench server
transaction pays each occupied cell once, each physical crystal once and aura
once, preserves the unused input stack's NBT and handles crafting remainders.
These component recipes do not require a custom result NBT mutation.

Existing ordinary plate recipes convert three brass, iron or thaumium ingots
into three respective plates. Brass and thaumium keep their existing original
METALLURGY crucible recipes. The resonator's original `ItemsTC.nuggets,1,10`
is **rareearth**, mapped to `thaumcraft:nugget_rareearth`. Metadata 5 is
quicksilver: `nugget_quicksilver` is explicitly rejected as the resonator core.
No vanilla substitute, guaranteed ore recipe or quicksilver substitution is added.

## Original natural rare-earth acquisition

The pinned `ToolEvents.harvestBlockEvent` independently appends one rare-earth
nugget before its tool enchantment handling. It requires a server-side harvest,
non-null block and `!isSilkTouching()`. No player research, special tool,
Refining enchantment or Fortune multiplier is required.

| Original ore | Chance per non-silk harvest |
| --- | ---: |
| Diamond | 0.05 |
| Emerald | 0.075 |
| Lapis | 0.01 |
| Coal | 0.001 |
| Lit or unlit Redstone | 0.01 |
| Nether Quartz | 0.01 |
| Thaumcraft Amber | 0.05 |
| Thaumcraft Quartz | 0.05 |

Bytecode offsets 54–58 confirm `nextFloat`, `f2d`, `double 0.05` and strict
comparison; offsets 87–91, 120–124 and 153–157 confirm 0.075, 0.01 and
0.001. Offsets 326–343 append exactly `new ItemStack(ItemsTC.nuggets,1,10)`.
Iron, copper, gold and cinnabar do **not** receive this harvest bonus. Their
separate original smelting-bonus registrations are not ordinary harvest rules.

Forge 1.20 removed `HarvestDropsEvent`. `RareEarthOreLootModifier` supplies its
equivalent through the real global loot pipeline, appending to the existing
generated list. Its codec is registered by the narrowly scoped MOD-bus
`RareEarthLootRegistration` subscriber; the additive global modifier JSON uses
`replace:false`. It requires a block-state context, checks the actual tool's
Silk Touch enchantment and rolls once only for one of the audited block IDs.
No block-state parameter means no change, so entity/chest loot is unaffected.

Explicit modern adaptations: lit/unlit redstone is the same modern block with
a `LIT` property; vanilla deepslate counterparts preserve their corresponding
ore family's chance. No arbitrary Forge ore tag broadens the rule. The modifier
uses the server loot context RNG, since modern Forge has no harvest-event world
RNG phase; the distribution and strict float-to-double comparison are preserved,
but the original world's global random sequence is not claimed to match.
Normal block loot, Forge protection, actual harvesting and other global loot
modifiers remain in their normal pipeline. Temporary focus Break/Exchange and
tool mining use the same actual loot path where they ordinarily generate drops.

Original infernal-furnace smelting bonuses (ConfigRecipes 519–531) remain a
separate unimplemented device mechanic. They are unnecessary for the audited
natural ore harvest route and are not replaced with guaranteed furnace recipes.

## Integration and tests

New recipes are loaded by the existing `thaumcraft:arcane_shaped` serializer;
the original book already displays all three under BASEARTIFICE/BASEALCHEMY.
There is no duplicate book recipe or research progression registration.
The live centrifuge retains its original `CENTRIFUGE` research and recipe:
100 vis, Ordo 1/Perditio 1, two tubes, actual morphic resonator, alchemical
construct and actual simple mechanism. Completing this ingredient slice does
not complete all Golemancy, late mob/material world sources or other devices.

`EarlyDeviceIngredientGameTests` defines five required scenarios:

1. Real result-slot crafting of a simple mechanism at started BASEARTIFICE,
   atomic locked/9-vis rejection, exact 10-vis/five-item/two-crystal payment
   and actual craft fact without research completion.
2. Two paid simple mechanisms enter the actual complex recipe; 49-vis rejection
   preserves them, then exact 50-vis/five-item/two-crystal payment produces one.
3. Quicksilver rejection; real rare-earth core with remaining-stack NBT;
   missing Aer and 49-vis atomic rejection; exact 50-vis result-slot transaction.
4. Independent pinned threshold array and mixed seeded RNG oracle for all
   original ore identities (including both redstone states); positive and
   negative bonus branches, one appended nugget, preserved initial drops,
   Silk Touch exclusion and no RNG debit for excluded ores.
5. The registered codec/datapack affects actual seeded vanilla diamond loot
   without changing its diamond count. An actually obtained rare-earth stack
   is consumed into a resonator; a paid simple mechanism joins it in the paid
   centrifuge grid. Total vis debit is 50 + 10 + 100, with all physical
   ingredients/crystals consumed and no research manufacturing side effect.

Research and plate/other device inputs in these tests are explicit fixtures;
the ore source and the three intermediate crafting payments execute the real
server loot and workbench paths. Test definitions do not establish a pass.
Integrated run results and final artifacts belong in `VALIDATION.md` only after
the parent task runs the current suite and resource validation.
