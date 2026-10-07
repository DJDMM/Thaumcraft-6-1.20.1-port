# Arcane Levitator: BETA26 contract and 1.20.1 adaptation

Authority is the pinned `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Read `javap -c -p` for `TileLevitator`, `BlockLevitator`, `BlockTCDevice`,
`BlockTCTile`, `BlockTC`, and the Levitator section of `ConfigRecipes`.
The existing reference snapshot is useful navigation, not a substitute for those instructions.
Original vanilla facing was checked against the locally pinned official 1.12.2 client
`fa.a(et,vp)`, mapped to `EnumFacing.func_190914_a` by `joined.srg`.

## Original behavior

- ID `thaumcraft:levitator`; normal baked on/off JSON models, no dedicated TESR.
  Six `facing` values and enabled bit. Powered redstone disables the beam.
- `ranges={4,8,16,32}`, default index1 (8). Tiny rear-button right click cycles
  8 ->16 ->32 ->4 ->8, clears actual range, marks/synchronizes the tile and plays
  `thaumcraft:key` at volume .5/pitch1. There is no invented GUI, upgrade slot,
  ownership requirement or research requirement on use.
- The tiny selectable button is on the rear of the beam: UP y.0625..125,
  DOWN y.875..9375; corresponding X/Z strips for horizontal orientations.
  Its other dimensions are .375..625. Large body excludes the rear .125 strip.
  Original special ray tracing prioritizes the button, with ordinary body fallback.
- Placement near the block (absX/Z from block center <2) faces UP if eyeY-blockY>2,
  DOWN if blockY-eyeY>0, otherwise opposite horizontal facing. Sneaking reverses it.
  It uses position/eye height rather than modern pitch-based placement.
- `rangeActual` starts0. Each tick tests distance `1+counter%configuredRange`.
  Air extends actual range; an opaque sample truncates a previously longer beam
  to the sample's distance and resets counter to-1 before its increment.
  An initial obstacle does not itself extend the beam. The inserted-obstacle
  distance inclusion is an original quirk, not silently corrected.
- Whenever server `vis<10`, drain up to one real local aura unit, then set
  `vis=(int)(vis+drained*1200F)`. Refill also happens when powered or empty.
  This is an integer energy buffer, not continuous per-tick aura use.
- While enabled, actualRange>0 and vis>0, query the device's one-block column
  plus actual range in the facing direction. Eligible: item entities, pushable
  entities, and specifically horses even if their pushability says false.
  No invented magnet radius, living-only test or owner filter.
- Add `.1F` on the facing axis. For an airborne horizontal target, damp negativeY
  by `.8999999761581421` then add `.07999999821186066`. Clamp each axis to
  +/-`.3499999940395355`. Sneaking UP instead only damps negativeY, with no
  acceleration or cap in that branch. Every eligible target resets fallDistance0.
- Debit `configuredRange*2` for each moved target and stop once buffer<=0.
  A final positive remainder smaller than cost still moves one target and can
  leave negative buffer debt. No extra charge merely for an empty beam.
- Save/sync only byte `range` and int `vis`; counter and actual range are transient.
  Buffer debits dirty the server every twentieth scan counter; refill syncs immediately.
- Effective hardness2/resistance12, wood sound/material, harvestable by hand;
  self-drop is one plain levitator and does not preserve internal energy/settings.
- Arcane recipe: LEVITATOR,35vis,Aer1, `WIW / BNB / WGW`, planks4,
  thaumium plate1,iron plate2,nitor1,simple mechanism1. Pinned ConfigRecipes
  confirms dictionary `plateThaumium`, `plateIron`, `plankWood`, `nitor`.

## Explicit port adaptations

- Keeps catalogue ID and twelve on/off/facing state models. New BE save ID is
  `thaumcraft:arcane_levitator`; registration must select the operational block.
- Modern tags replace dictionary categories: `minecraft:planks`,
  `forge:plates/thaumium`, `forge:plates/iron`, `forge:nitor`.
- All block/entity work stays on loaded chunks; a bounded32-position field
  check never loads another chunk. Facing change clears the old checked beam,
  and malformed range/buffer NBT is bounded to0..3 and -63..1209.
- Standard physical block use performs an actual player-eye button retrace using
  Forge block reach. Shape selection shows the button when that ray reaches it;
  collision remains the large body. No copied CodeChickenLib implementation.
- Server motion is marked for native velocity synchronization. Both sides retain
  predicted motion like the original tile. Native END_ROD replaces original
  custom levitator particles; positions/chances/directions keep the release formulas.
- Modern translatable chat needs `%s` placeholders for both range and cost; the
  original key uses String.format `%s`/`%d`. Runtime localization is configured
  by shared client/resource integration, not by an invented meter interface.

`LevitatorGameTests` defines14 focused scenarios: actual entity motion/debit/caps,
sneak damping, horizontal compensation, down-item movement, eligibility/column
boundaries, negative debt, fractional aura/redstone refill, physical signal pause,
opaque scanning, controls/save/malformed input, native ticker displacement,
actual rear-button use, six-face placement geometry and exact recipe grid.
Definitions are not passed checks. Current integrated evidence belongs in VALIDATION.md.
