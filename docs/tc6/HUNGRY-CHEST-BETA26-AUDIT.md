# Hungry Chest: BETA26 contract and operational predecessor

Pinned authority: `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Audited bytecode: `BlockHungryChest`, `TileHungryChest`,
`TileHungryChestRenderer`, HungryChest registration in `ConfigRecipes`.
This device is a real prerequisite of LEVITATOR, not an automatically completed fixture.

## Original behavior

- One independent27-slot chest. `TileHungryChest` extends vanilla Chest;
  adjacency checking explicitly marks itself checked without finding adjacent chests.
  Neighboring Hungry Chests never become54-slot/double chests.
- Physical ItemEntity collision inserts through the inventory from UP.
  It has no attraction range or periodic vacuum scan. Pickup delay, owner and
  thrown-item age are not checked. Full chest keeps the item; partial insertion
  writes the exact remainder back; fully inserted item is removed.
- Item/metadata/NBT matching is the inventory's ordinary exact stack matching.
  Successful/partial insertion plays generic eat at volume.25 and pitch
  `(randFloat-randFloat)*.2+1`; then inventory is dirty. No invented hunger fuel.
- Right click directly opens a native27-slot chest menu. There is no solid-lid
  or cat obstruction predicate. Normal storage, shift transfer and comparator
  fullness are inherited vanilla chest behavior.
- Placement's final state faces opposite placer horizontal facing, regardless
  of sneaking. Horizontal rotations retain the same BE inventory.
- Shape and collision box: (.0625,0,.0625) to(.9375,.875,.9375).
  Nonopaque/nonfull, no sturdy block face, hardness2.5; setHardness supplies
  effective explosion resistance2.5. No correct-tool requirement.
- Breaking spills inventory and one plain chest. Inventory is not copied into
  a dropped item's NBT. Comparator is updated. A second removal cannot repeat drops.
- Renderer uses original64x64 `textures/models/chesthungry.png`, vanilla single
  ModelChest geometry, rotation from cardinal metadata and cubic eased lid angle.
- Arcane recipe HUNGRYCHEST15vis/Terra1/Aqua1: `WTW / W W / WWW`.
  Exactly seven Greatwood planks and one `trapdoorWood` dictionary ingredient;
  ordinary vanilla planks and iron trapdoor are not substitutes.

## Explicit port adaptations

- Same block/item `thaumcraft:hungry_chest`; typed working BE `hungry_chest`.
  Native modern ChestBlockEntity storage/ChestMenu retains27 slots and all-side
  Forge inventory access. Block is not a vanilla ChestBlock, avoiding adjacency.
- Operational state has exactly four cardinal FACING values, matching pinned
  `PropertyDirection.create("facing",EnumFacing.Plane.HORIZONTAL)`. The historical
  visual catalogue's UP/DOWN Hungry states were extractor scaffolding and are not
  placement options. Shared baked-state census/fixtures must reflect this correction.
  Native single-chest geometry points south at yaw0; renderer uses negative
  `Direction.toYRot()`, so the lock/front faces the stored cardinal FACING.
- Native container opener tracking/scheduled recount replaces old manual player
  count, while open/close block events update lid animation and neighbor signals.
  It intentionally overrides the modern chest counter's sound callback because
  Hungry state has no native ChestType property.
- Native single-chest model layer uses matching64x64 geometry/UV layout and
  original Hungry texture, with modern render buffers. Item renderer shows the
  same closed single chest after shared builtin/entity model integration.
- `minecraft:wooden_trapdoors` replaces the original trapdoor dictionary category.
- Collision insertion uses detached snapshots and an in-progress guard; comparator
  updates are explicit. This is defensive modern integration, not new vacuum behavior.

`HungryChestGameTests` defines9 focused scenarios: real collision capture,
tagged partial remainder, full/different-NBT rejection, separate adjacent/all-face
inventory, native covered-lid menu/openers, comparator/save, one-time destruction,
native entity-tick capture and exact recipe grid. Definitions do not establish
passing integrated server/client checks; those belong in VALIDATION.md.
