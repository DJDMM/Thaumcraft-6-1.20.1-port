# World crystal growth and harvesting: BETA26

Authority is the pinned `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
`BlockCrystal`, `CrystalModel`, `MeshModel`, `SoundsTC` and the crystal generation
branch are compared with reference commit `954022bb777b7546281fb36df8522f0ba6b43f81`.
The growth, drops, collision, state metadata, random call ordering and support
rules were checked in the actual release bytecode with Java17 `javap -c -p`.
The ordinary three-by-three generation slice was also read from the original
world generator; its modern configured-feature frequency and extended-height
adaptations already documented in BIOMES.md remain in place.

## Seven original block forms

`crystal_aer`, `crystal_ignis`, `crystal_aqua`, `crystal_terra`, `crystal_ordo`,
`crystal_perditio` and `crystal_vitium` all use the same operational block.
There is no separate Crystal Farmer machine: the research supplies the seven
infusion recipes for actual plantable cluster BlockItems.

Original listed properties are `size`0..3 and `gen`1..4, default0/1. Metadata
stores `size | ((gen-1)<<2)`. The six old support booleans are dynamic model data.
The earlier port's saved `facing` and `waterlogged` properties remain loadable;
new placement rejects liquid instead of inheriting vanilla amethyst waterlogging.
Survival and rendering use every rock support, rather than only the saved facing.
An earlier saved cluster defaults to original size0/generation1 when those new
properties are absent. It subsequently follows paid native random ticks.

Hardness is0.25, effective explosion resistance0.25, light1, no required-tool gate,
and no sturdy faces. The original sound has volume0.5 and pitch1 and uses the
same `thaumcraft:crystal` sample for break/step/place/hit/fall. The inherited
vanilla amethyst projectile chime is explicitly suppressed.

The original sample must be present as an actual client resource as well as a
registered SoundEvent. Client2 exposed a catalogue omission: neither its
`sounds.json` entry nor `sounds/crystal.ogg` had previously been copied. Both
are now included. The event retains the pinned `thaumcraft:crystal` name and
`stream:false`; the old JSON category is represented by the native block sound
call's `SoundSource.BLOCKS` in1.20.1. The5524-byte OGG is copied byte-for-byte from
the pinned JAR, SHA256
`2737cd4dcda0d15e79c99a15b134e81f643cfff9235ff1277bc51651263fd0d7`.
This adds one original sound resource to the45 previously copied OGG files,
for46 in the current port; it does not change any existing sample. Source hash
and JSON validation do not by themselves establish audible client playback;
that fix still requires the next fresh native client run.

**Collision is empty in the original release.** Pinned
`BlockCrystal.func_180646_a(IBlockState,IBlockAccess,BlockPos)` has exactly
`0: aconst_null; 1: areturn`. This overrides the vanilla collision method;
it must not be replaced with the selection outline. The outline is a half cube
beside its sole rock support, and a full cube with zero or multiple supports.
The block's non-sturdy faces and empty collision remain distinct from that outline.

## Paid native random ticks

Each ordinary random tick first rolls `nextInt(3+generation)==0`. It uses vis for
the six primal forms and flux for Vitium. The corresponding other pool is untouched.

| Condition | Original resulting behavior |
| --- | --- |
| Pool<=10 and size>0 | Lose one size and return10 to the same pool; no material drop |
| Pool<=10 and size0 touches a six-face neighbor of the exact same block | Disappear, return10, no loot |
| Pool<=10 and isolated size0 | Preserve the last seed; no return or payment |
| Pool>10 but <=auraBase+10 | No growth or spread |
| Pool>auraBase+10, size<3 and size<`5-generation+packedPosition%3` | Debit10 and increase size by1 |
| Pool>auraBase+10, at the above growth cap, generation<4 | Attempt one paid spread |
| Generation4 at cap | Neither spread nor debit |

Equality at the high threshold does not grow. The original Java signed `%3` is
retained, including negative X coordinates; it is not floorMod. `packedPosition`
explicitly reproduces the Minecraft1.12 X26/Y12/Z26 bit layout. Minecraft1.20's
`BlockPos.asLong()` has different Y/Z bit positions and would change the cap.
New negative heights use the original masked twelve-bit Y field as an explicit
adaptation to the extended modern world height.

Spread samples X/Y/Z independently from -1..1 using **world.random**, rejects its
own position, liquid and nonreplaceable targets, then rolls world.random1/16
and requires a solid rock support. A successful target costs10 from the parent's
chunk, produces a size0 child, and normally increments generation. A separate
`updateTick` random1/6 roll keeps the parent's generation instead. A fourth-
generation cluster cannot spread even if it happens to be larger than its cap.
No research, redstone or bonemeal condition is added to the block's growth.

Native generation retains only the six primal forms, sets size1..3/generation1
and accepts dry replaceable cells with a solid rock face. Vitium requires its
actual paid Farmer infusion; this slice adds no invented natural Flux-crystal
biome generation.

## Support, drops and modern safety

Original support means a six-face neighbor of Material.ROCK whose inward face
is solid. Minecraft1.20 removed that material classification. The extensible
`thaumcraft:crystal_support` block tag explicitly maps ordinary stone, rock ores,
bricks, obsidian and corresponding modern stone materials. Wood, glass, dirt
and metal storage blocks are excluded; a sturdy face alone is insufficient.
The tag is an explicit modern mapping, not an assertion that every addon defines
its old material. Support checks never force-load neighboring chunks.

The mapping includes the original full-cube Vis Battery, the three stone TC
stairs (Arcane, Arcane Brick, Ancient), and all four stone half/double slab
pairs (Arcane Stone, Arcane Brick, Ancient, Eldritch). Pinned
`BlockVisBattery` and `BlockSlabTC` constructors use Material.ROCK; the latter
selects Material.WOOD only for the separately registered Greatwood/Silverwood
slabs. `BlockStairsTC` inherits its model block's material, and ConfigBlocks
supplies BlockStoneTC for those three stone stairs. The remaining registered
BlockStoneTC/BlockStonePorous full cubes and matrix upgrades are included too.
An earlier incomplete support tag omitted these known original rock forms.

For1.20.1, the tag explicitly names vanilla stone-material slab/stair variants,
including newer Blackstone/Deepslate families. Vanilla's stone wall group is
included through `#minecraft:walls`; the existing `#minecraft:stone_bricks`
group supplies those full brick variants. The general slab/stair groups are
deliberately excluded because they also contain wood. Petrified Oak Slab is
included as the vanilla stone-material exception to its wooden appearance.
Membership alone never makes a face solid: native `isFaceSturdy` still rejects
the open half of a slab or stair and any non-sturdy wall face. Addon material
classification remains an explicit datapack extension point.

Natural degeneration returns aura without dropping crystals. Physical harvest
and final support loss instead yield **size+1** `crystal_essence` items carrying
the original single-aspect `Aspects` NBT, for all seven forms. Fortune and Silk
Touch have no count or block-item branch. Native loot tables aggregate the old
list of single crystals into an equivalent stack and keep modern Forge loot and
player break callbacks. Removing one support retains a cluster if another rock
face remains; final support loss drops once. There is no XP yield.

Modern hardening rejects stale tick state, off-server-thread calls and unloaded
sources or targets, and refunds the exact ten paid units if a state placement
fails. Replayed neighbor callbacks cannot create another drop after removal.
These guards are explicit adaptations rather than extra original mechanics.

## Original procedural appearance

The original `models/obj/crystal.obj` is copied byte-for-byte from the pinned JAR.
It has eight named prism groups and96 triangle faces. The native baked model
selects **size+1 groups per rock face**, with the original shuffled face-seed
offsets UP0/DOWN5/EAST10/WEST15/NORTH20/SOUTH25. Pinned CrystalModel bytecode
reads NORTH at1232 and adds20 at1326; it reads SOUTH at1491 and adds25 at1585.
The initial port accidentally interchanged those two seeds; final review
corrected them before final integrated QA. Simultaneous attachments and all
six original rotation/translation transforms are retained. Generation affects
runtime growth, not the number of displayed prisms. The release's all-six-
enclosed guard yields no quads and is preserved. The original raw Wavefront V
coordinates, crystal texture, aspect tint and packed block-light minimum180
are retained, alongside current world sky light. No new raster assets are made.

Existing planter inventory sprites and four Vitium inventory OBJ bands remain;
the actual world renderer now uses the original common procedural crystal mesh.
The earlier flat crossed planter sprite is no longer the planted world's model.

## Validation scope

`CrystalGrowthGameTests` defines32 tests covering actual registered states,
activation misses, all six primal debits, Flux-only payment/refund, both exact
thresholds, same-block/diagonal degeneration, original packed position,
generation caps and one-in-six/one-in-sixteen rolls, dry replacement and liquid
refusal, all six support faces, original loot for every size/aspect and enchantment,
one-time support drops, native survival harvest with Forge veto, saved poses,
wet-save compatibility and stale/unloaded callbacks. The preceding worldgen
test now checks exact original size0 contained-crystal loot instead of the
prototype's randomized Fortune/Silk legacy shard table.

Four final-review regressions exercise actual survival BlockItem payment and
placement on the original battery, all four TC stone slab pairs and all three
TC stone stair forms, ordinary Stone/Smooth Stone and modern Deepslate slabs
and stairs. They check both solid vertical faces, slab double states, battery
charge changes and Stone-to-Battery support replacement without loot. Rejected
open slab/stair faces and physically solid wooden Greatwood/Silverwood/Oak
forms leave the stack count and target world unchanged. These are additional
test definitions pending root's fresh complete server run.

These are test definitions, not a claim that this new suite or the native client
has passed. Final run logs, image review and packaging evidence are recorded by
the root validation stage. Farmer progression/paid infusion and Vis Battery
contracts are audited separately. Flux Rift/Eldritch ecology remains outside
this crystal-growth implementation.
