# Infusion altar and pedestal: official TC6 6.1.BETA26 audit

Baseline: official `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA256 `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`; reference source commit `954022bb777b7546281fb36df8522f0ba6b43f81`. This document describes release mechanics; implementation claims belong in the stage release notes. No TC4/TC5 altar behavior is assumed.

## Assembly and activation

`ConfigRecipes.initialize`, `DustTriggerMultiblock`, `Part`, `ItemMagicDust`:

| Relative height | Required before dust | Result |
| --- | --- | --- |
| y2 | Infusion matrix at center | Unchanged matrix |
| y1 | Arcane stone at four corners (+/-1,+/-1) | Four air blocks |
| y0 | Arcane pedestal at center; arcane stone at four corners | Pedestal unchanged; four arcane pillars |

Eight stones, one pedestal and one matrix make the normal altar. At its unrotated corners the pillar facing order is EAST, NORTH, SOUTH, WEST for x0/z0, x0/z2, x2/z0, x2/z2. Blueprint matches all horizontal rotations and search offsets around the clicked block. Null cells impose **no predicate**, including the matrix-to-center-pedestal gap. Block sources compare block identity; state sources compare complete state identity.

Dust requires known INFUSION, not strict completed INFUSION. It does nothing when sneaking or the player cannot edit. Successful use consumes one Salis Mundus unless creative. There is no vis payment. Targets are queued with Part default priority50; replaced stones become pillar blocks or air. Matrix and pedestal have null targets and retain their tile data. The release generic multiblock executor incorrectly fires an infernal-furnace craft event for every multiblock. A modern implementation should not manufacture that unrelated craft fact; this is a documented correction, not an original matrix craft event.

The advanced blueprints use Ancient stone/pillars or Eldritch tile/pillars. Their center pedestal source is the complete pedestal state with charge2 (Ancient) or charge1 (Eldritch), confirmed in official bytecode. These gates are INFUSIONANCIENT/INFUSIONELDRITCH; displaying them does not imply Outer Lands mechanics are implemented.

`TileInfusionMatrix.validLocation` accepts any pedestal two blocks below the matrix and any pillar at all four (+/-1,-2,+/-1) corners. Pillar material/facing need not match, and the gap need not be air. This loose formed check differs from strict dust assembly.

`ItemCaster.onItemUseFirst` calls the block/tile IInteractWithCaster hook before focus use and requires no installed focus. Matrix's release hook always returns false: an inactive valid altar becomes active and plays craftstart; an active idle altar tries craftingStart. Thus assembly, activation and craft start are distinct operations. No aura/charge drain occurs in this hook. Modern interaction-result routing may return an appropriate consumed result to avoid fallback actions, while preserving the distinct state transitions.

## Pedestal inventory and interaction

`BlockPedestal`, `TilePedestal`, `TileThaumcraftInventory`, `InventoryUtils.dropItemsAtEntity`, `BlockTCTile.breakBlock`, official bytecode:

- Arcane, Ancient and Eldritch variants share one inventory slot with maximum1 and synced slot0. Blockstate charge ranges0..15; defaultstate0.
- Any item can be inserted into an empty pedestal. Insertion checks the **selected main-hand item** first, but copies and consumes one item from the interaction hand. This off-hand quirk exists in the release bytecode. Main hand empty prevents off-hand insertion; main hand occupied permits off-hand insertion. Sneaking has no separate behavior.
- Insertion consumes one held item even in creative mode. Full original ItemStack metadata/NBT is copied. Insertion sound is item pickup, volume0.2, pitch ((rand-rand)*0.7+1)*1.6.
- Clicking an occupied pedestal drops its copied item at player x/z and player y+eyeHeight/2, then clears the slot. It does not directly add the item to the player's inventory and does not replace it with a held item. Sound pitch uses factor1.5 instead of1.6.
- Breaking the pedestal drops its inventory at the block through normal InventoryHelper behavior. Charge/state-only changes must not drop contents.
- Every face exposes slot0, insertion is allowed only when empty, extraction is always allowed. The original Forge sided wrapper is available on six real directions; its hasCapability/getCapability behavior on null direction is inconsistent. Modern null-side support is an explicit capability compatibility correction.
- Slot mutations mark dirty and immediately sync. Save format uses vanilla Items list / byte Slot. Client sync resets all synced slots before applying data, so removed contents disappear.
- Original direct setter caps stack size but stores the supplied reference. Modern insertion should copy ownership to prevent alias-based duplication and reject malformed slot/count NBT; these are server/inventory correctness hardening, not a new gameplay cost.

Renderer `TilePedestalRenderer`: item center x/z0.5, y0.75; global scale1.25; rotation ticks%360 using view entity age + partial; stack displayed count1 with EntityItem hoverStart0. Each render creates a fresh EntityItem with age0 and invokes its renderer with partial0, so the item's origin includes constant0.1 +0.25*groundScaleY but **does not bob over time**. Pedestal body is baked geometry, not drawn by that tile renderer. Render bounding box is one block wide and two blocks high. The original inherited collision/selection box remains a full block despite isFullCube/isOpaqueCube=false; the old visual catalogue's narrowed pedestal shape was provisional.

## Surroundings, symmetry and upgrades

`TileInfusionMatrix.getSurroundings` scans x/z -8..8 inclusive and y matrix+3 down to matrix-7 inclusive, excluding the entire center x/z column. It includes every peripheral pedestal, without ray obstruction or height-floor filtering. Occupied peripheral slots form the recipe component list; unexpected extras invalidate matching. The center pedestal is the input/output only.

All pedestal variants are IInfusionStabiliserExt. Arcane/Ancient stabilization amount0; Eldritch0.1. A symmetric pedestal pair has penalty0.1 only when exactly one is occupied; item identity/NBT/count do not matter. Stabilizing blocks compare same block type and same stabilization amount across opposite x/z at identical y. Equivalent symmetric pairs add diminishing returns base*0.75^previousSameBlockCount; mismatch subtracts max(amounts).

Base cycleTime10, costMult1.0. Four Ancient pillars: cycleTime-1, costMult-0.1, stabilityReplenish-0.1. Four Eldritch pillars: cycleTime-3, costMult+0.05, stabilityReplenish+0.2. Speed/cost blocks are individually sampled under the four pillars, matrix-relative y-3: speed cycleTime-1 and costMult+0.01; cost cycleTime+1 and costMult-0.02. Each surrounding Eldritch pedestal adds0.0025 costMult; Ancient subtracts0.01. CountDelay is integer cycleTime/2; crafting aspect costs truncate amount*max(costMult,0.5).

Pedestal charge>0 can search horizontal increasing-charge paths for a Stabilizer; recursive search stops at >=5. Charge rendering and inlay shield events are separate from generic inventory behavior.

## Canonical research and component recipes

| Entry | Canonical prerequisite / cost |
| --- | --- |
| UNLOCKAUROMANCY | Completed UNLOCKALCHEMY; stage1 m_deepdown and m_uphigh; stage2 real crafted vis_resonator and caster_basic |
| INFUSION | Completed BASEINFUSION; stage1 16 raw observation INFUSION,32 raw theory INFUSION,stone,feather,10-unit Aer phial; stage2 real crafted matrix; empty conclusion |
| INFUSIONBOOST | Completed INFUSION;64 raw theory INFUSION |
| INFUSIONSTABLE | INFUSION, METALLURGY@3, !INSTABILITY;16 observation +32 theory INFUSION,redstone,10-unit Vitium phial |
| RECHARGEPEDESTAL | Completed BASEAUROMANCY;32 theory AUROMANCY |
| BOOTSTRAVELLER | INFUSION and RECHARGEPEDESTAL;32 theory INFUSION;!motus,m_walker,m_runner,m_swimmer,m_jumper |
| ELEMENTALTOOLS | BOOTSTRAVELLER and METALLURGY@3;32 theory each INFUSION/BASICS;craft five thaumium tools |
| ARMORFORTRESS | METALLURGY@3 and BOOTSTRAVELLER;32 theory INFUSION and !praemunio |
| FORTRESSMASK | ARMORFORTRESS;32 theory INFUSION |
| INFUSIONENCHANTMENT | ELEMENTALTOOLS;64 theory INFUSION; malformed obtain ID is discarded by original loader |
| RUNICSHIELDING | INFUSIONENCHANTMENT;32 theory INFUSION,amber; malformed obtain ID is discarded by original loader |

BASEAUROMANCY itself requires focus_1 crafting and then f_onfire plus wand_workbench crafting. The recharge/boots/tools/armor branches must not be opened by silently bypassing that unported focus path.

Original ConfigResearch periodic facts: while UNLOCKAUROMANCY stage1 but not stage2, y<10 awards m_deepdown and y>getActualHeight()*0.4 awards m_uphigh. Movement facts are independent: WALK_ONE_CM>160000, SPRINT_ONE_CM>80000, SWIM_ONE_CM>8000, JUMP>500, all strict greater-than. PlayerEvents invokes this check every200player ticks; retain that cadence and use real server statistics, not client position sampling. New Overworld vertical bounds require an explicit adaptation for upper-height scaling.

| Recipe | Gate | Vis | Crystals |
| --- | --- | ---: | --- |
| InfusionMatrix | INFUSION@2 |150| one of each six primals |
| ArcanePedestal | INFUSION |10|none|
| vis_resonator | UNLOCKAUROMANCY@2 |50|Air1,Water1|
| caster_basic | UNLOCKAUROMANCY@2 |100|one of each six primals|
| rechargepedestal | RECHARGEPEDESTAL |100|Air1,Order1|
| RedstoneInlay (2) | INFUSIONSTABLE |25|Water1|
| Stabilizer | INFUSIONSTABLE |250|Earth1,Water1,Entropy1|
| MatrixMotion | INFUSIONBOOST |500|Air1,Order1|
| MatrixCost | INFUSIONBOOST |500|Air1,Water1,Entropy1|

Arcane stone ordinary recipe is eight ore-stone plus one crystal essence =>9stone; 2x2 arcane stone =>4bricks; three stones/bricks in a row =>6matching slabs. These ordinary crafts have no direct research gate. Matrix uses four arcane **bricks** and center nitor. Pedestal uses six arcane **stone slabs** and center arcane stone.
