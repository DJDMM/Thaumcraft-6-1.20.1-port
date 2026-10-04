# Essentia centrifuge — BETA26 production integration 0.19/0.20

This is the next devices slice, separate from completion of the 21 focus definitions.
Source baseline: `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`;
source mirror commit `954022bb777b7546281fb36df8522f0ba6b43f81`.
`javap -c -p thaumcraft.common.tiles.essentia.TileCentrifuge` confirms the
counter, sided suction, one random immediate component and API quirks below.
Read alongside `TileCentrifuge`, `BlockCentrifuge`, `BlockTCTile`,
`TileCentrifugeRenderer`, `ModelCentrifuge`, `ConfigRecipes` and the original
`alchemy.json` entry. Neither a TC4 research puzzle nor a new recipe is inferred.

## Production wiring

- The bootstrap calls
  `thaumcraft.essentia.centrifuge.CentrifugeModule.register(modBus)` before
  registry events. The client renderer subscriber retains the defensive
  `CENTRIFUGE.isPresent()` check, and the registered working type is now live.
- `CatalogBlocks.create` dispatches `CentrifugeModule.handlesBlock(id)` to
  `CentrifugeModule.createBlock()` before generic CatalogueBlock creation.
  The catalogue item supplier creates `CentrifugeBlockItem` for this ID;
  its own builtin renderer displays the original machine geometry instead
  of the generic CatalogueBlockItem extension, which requires CatalogueBlock.
  `thaumcraft:centrifuge` and its original item/resource entries are retained.
  The dedicated BE save ID is
  `thaumcraft:essentia_centrifuge`. The old visual type still accepts
  centrifuge for legacy save recovery; the separate migration changes only
  a storage-free `CatalogBlockEntity` anchor under a working centrifuge block.
- `CENTRIFUGE` belongs to `ResearchProgression`'s implemented canonical set,
  bringing the 0.19 total to 43 (the complete focus slice reached 42).
  The active 0.20 slice adds ESSENTIASMELTERTHAUMIUM, THAUMATORIUM and
  INFUSIONSTABLE, making the current canonical set 46. The final 0.20 server
  run passed 603/603 after the book requirement fix; the hidden client passed
  22/22, and final build/resource checks passed.
  Its original alchemy JSON is loaded by the book: completed
  `TUBES` parent, stage 1 costs exactly `THEORY;ALCHEMY;1` (32 raw theory),
  stage 2 is the recipe page. Bare recipe `CENTRIFUGE` uses the original
  **known** predicate: the physical recipe is accessible at started stage 1.
  The knowledge payment and final recipe-only stage still require the normal
  book transitions; manufacturing does not pay or complete research by itself.
- Original `pump` sound mapping is present in `assets/thaumcraft/sounds.json`:
  `{"category":"block","sounds":["thaumcraft:pump1","thaumcraft:pump2","thaumcraft:pump3"]}`.
  The three new OGG assets are copied byte-for-byte from the pinned JAR.
- New `data/thaumcraft/recipes/arcane/centrifuge.json` uses the existing
  `thaumcraft:arcane_shaped` serializer. Recipe reference displays already
  have the original `thaumcraft:Centrifuge`; no book layout rewrite is needed.
  Physical recipe: 100 vis, Ordo 1, Perditio 1; ` T /RCP/ T ` with tube,
  morphic resonator, alchemical construct and simple mechanism.

The shared bootstrap, catalogue factory/item supplier, canonical progression,
recipe and sound mapping are production-integrated. ContainerArcaneWorkbench
in BETA26 calls IPlayerKnowledge.isResearchKnown on arcane recipe research,
unlike the focus editor's knowsResearchStrict. ArcaneRecipe.unlocked now uses
the original started bare-key / entered-stage@N predicate and separately
retains legacy PORT recipe aliases. Aliases do not grant canonical stages;
strict completed bare focus-node gates are unchanged.

## Server contract

Two distinct slots each hold exactly one essentia unit. Empty unpowered input
has downward suction 128; filled input 64; all other faces 0. Redstone reduces
downward suction to 0 and pauses drawing, the process counter and conversion.
Only DOWN advertises input, UP advertises output; neither external suction
setter nor aspect-list setter does anything. An empty pair draws one compound
from a connectable output below on every fifth idle callback. Suction must
strictly exceed the source suction and meet its minimum. The draw sets 39 and
the same callback decrements to 38. Direct insertion starts 39. At 0, an empty
output receives one uniformly random **immediate** component and input clears.
The other component is lost, without extra flux. Full output prevents conversion
but not countdown; no automatic draw occurs while either slot is occupied.

Original saved/synced fields are only `aspectIn` and `aspectOut`. `process`,
idle count, rotor speed and angle are not persisted. A loaded pending input
therefore converts on its first unpowered callback if output is free. Do not
“fix” this with a saved countdown. Ordinary block destruction returns an empty
block item; `BlockTCTile` spills only the finished UP output as one flux and
loses pending input. No input essence is stored in loot.

Positive-amount low-level quirks are retained: `addEssentia` ignores its face,
accepts one regardless of the positive amount, and does not reject redstone
direct insertion. `takeFromContainer` clears one matching output for any positive
amount; `takeEssentia` still requires UP and returns the requested positive
amount, although the slot contained one. Native devices ask for exactly one.
Null/nonpositive requests, null containment and malformed saved primal input
are explicitly rejected as port hardening. Loaded-neighbour checks never force
chunks. Addon aspect definitions with missing immediate components are rejected.

## Client contract

All six original cuboids and UV coordinates are reproduced in modern
ModelParts using the original 64×32 `textures/models/centrifuge.png`. Top/bottom
remain fixed; crossbar, both chambers and core rotate around Y at block center.
Original tick acceleration is +2 up to 20 degrees/tick, braking −0.5; it plays
the pump sound across each half-turn threshold. Renderer uses the tick angle
without interpolating it, matching the original unused partialTicks parameter.
The world block renders invisibly to avoid duplicate catalogue geometry;
its dedicated BER renders the actual machine. Dedicated BlockItem/extension
renders the same original geometry at rotation 0 without the generic catalogue
tooltip that claims the item has no mechanics. Source sets `mirror=true` only
after each `addBox`: vanilla 1.12 constructs each ModelBox using the preceding
false value, so the working model keeps those effective unmirrored UVs rather
than applying the late field to newly built modern boxes.

## Historical 0.19 server and client evidence

The verified 0.19 `validation/focus-019-gametest8.log` completed all 577
required server tests with BUILD SUCCESSFUL, including the 13 centrifuge
scenarios and the final Mine callback-removal regression.
`CentrifugeGameTests` defines 11 scenarios: actual registered tile/render shape;
faces/suction/primal rejection; seeded immediate-component oracle; fifth-idle
draw and same-tick countdown; redstone pause; occupied-output countdown; API
quirks and malformed input; original reload shortcut and both-slot packet;
real registered tickers through three tubes to a jar; block destruction flux/loot;
worker-load/bounded-END legacy migration; actual physical arcane grid/vis/crystals.
`CentrifugeProgressionGameTests` adds 2 scenarios for completed TUBES gating,
31/35 raw-theory atomic rejection/payment, recipe-only final stage/save/replay,
and an actual workbench transaction at the original bare research stage,
99 vis rejection, five physical ingredients, two crystals and 100 vis exact debit.
`EssentiaProductionClientSmokeTest` now defines 12 scenes including actual
`centrifuge` and `centrifuge-recipe` captures. The first adds compound input
through the real server API to three working BEs: redstone-paused input,
processing input and one finished immediate component. The client waits for
actual block/BE sync, checks paused/rotating/output state, renders the live
BER and original item sprite, and writes THAUMCRAFT_CENTRIFUGE_CLIENT_FLOW.
The next opens the canonical book entry and actual arcane recipe; showing
the recipe cannot mutate server knowledge. Model/texture/renderer audits
include the centrifuge and 14 functional blocks.

The final `centrifuge-019-client2` run passed **12/12** scenes with
`THAUMCRAFT_ESSENTIA_PRODUCTION_CLIENT_SMOKE_OK` and JVM exit code 0.
The log is `validation/essentia-production-client-centrifuge-019-client2-client.log`;
the matching run/desktop JSON records 16 owned windows on the isolated desktop
and an unchanged input desktop, Default before and after. The 12-scene contact
sheet and full-size centrifuge model/recipe captures were reviewed successfully.
This result covers the EssentiaProduction fixture. Auromancy `full-019-client8`
separately passed 33 scenes and saved a dedicated Bolt frame; all 34 of those
captures were reviewed, bringing the combined review to 46 images. The 12
EssentiaProduction captures were reviewed in two contact sheets and the 34
Auromancy captures in four. Final build/resource checks also passed.
See [VALIDATION.md](../../VALIDATION.md) and the
[0.19 report](../../validation/artifact-report-0.19.json) for complete evidence
and fixture limitations.
The isolated launcher mutes sound, so it does not establish
an audible pump-sound review; the mapping/assets and runtime half-turn calls
are separate from what that silent fixture observes. No visible client,
desktop switch, foreground activation or global input is required.

## Remaining device scope

This note covers the centrifuge. The earlier claim that its simple mechanism
and morphic resonator required Golemancy was incorrect. In BETA26 both simple
and complex mechanisms use BASEARTIFICE, and the resonator uses BASEALCHEMY.
The 0.20 slice implements their paid arcane recipes and the original natural
rare-earth harvest route. The resonator requires metadata 10 rareearth,
not metadata 5 quicksilver; no substitute or new research key is introduced.
See [EARLY-DEVICE-INGREDIENTS-BETA26-AUDIT.md](EARLY-DEVICE-INGREDIENTS-BETA26-AUDIT.md).
The final 0.20 integrated server run automation-020-gametest5 passed 603/603,
including its five new component scenarios. EssentiaProduction
thaumatorium-020-client5 passed 22/22 with exit 0, unchanged Default input
desktop and 16 owned windows; 1084 baked states and 17 working blocks were
audited. Final build passed, resources validated 2783 JSON / 3542 files.
The historical 0.19 centrifuge pass did not test these newly added paths.

Thaumatorium and INFUSIONSTABLE progression are integrated in 0.20. The
working machine has the actual Salis blueprint, shared catalyst inventory,
server-derived recipe menu and C2S selection, two-level suction, redstone,
paid output/container flow, persistent state and Brain Box capacity. See
[THAUMATORIUM-BETA26-AUDIT.md](THAUMATORIUM-BETA26-AUDIT.md). Its actual
Salis and menu C2S, dust2->1/coal2->1, typed sources50->25, buffered24->25->0
and one Alumentum in the front chest were checked in the final 22-scene
client. Research, source contents and the capacity3 Brain Box are explicit
fixtures, not proof of a full survival route. Final evidence and artifacts:
[VALIDATION.md](../../VALIDATION.md), [0.20 report](../../validation/artifact-report-0.20.json).
Brain Box's mind_clockwork remains a real unimplemented MINDCLOCKWORK/Golemancy
acquisition path; the main Thaumatorium does not require this optional upgrade.
Advanced transport, mirrors/transfusers, golem automation and other Artifice
devices remain separate work. Opening CENTRIFUGE alone never completes them.
