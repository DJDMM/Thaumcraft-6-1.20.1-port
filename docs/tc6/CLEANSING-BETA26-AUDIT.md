# Cleansing: Thaumcraft 6 BETA26

Scope: sanity soap, bath salts, purifying fluid, and Warp Ward. The original random warp events themselves are outside this implementation. This audit uses only TC6, not TC4/5 behavior.

## Evidence

- Pinned source `work/thaumcraft6-reference`, commit `954022bb777b7546281fb36df8522f0ba6b43f81`.
- Official `work/Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256 `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
- Original classes: `common/items/consumables/ItemSanitySoap.java`, `ItemBathSalts.java`, `common/blocks/misc/BlockFluidPure.java`, `common/lib/potions/PotionWarpWard.java`, `common/lib/events/EntityEvents.java`, `PlayerEvents.java`, `WarpEvents.java`, `common/lib/InternalMethodHandler.java`, `common/lib/capabilities/PlayerWarp.java`, `common/config/ConfigBlocks.java`, `common/config/Registrar.java`, `client/fx/FXDispatcher.java`, `client/fx/particles/FXGeneric.java`, `client/fx/ParticleEngine.java`.
- JAR `javap -c` confirms soap duration/threshold, source-only Ward grant, salt lifetime and expiry conversion, Warp Ward setup, and the unusual negative-warp overdraw branch below. This is a compiled BETA26 behavior, not a decompiler guess.
- Modern method names and constructors checked against Forge `1.20.1-47.4.10_mapped_official_1.20.1.jar` with `javap`; no Gradle or client launch performed by this owner.

## Wiring contract

The owner created only new `src/main/java/thaumcraft/equipment/cleansing/*.java` and this document. Root owns the following existing-file/resource edits:

1. Call `CleansingModule.register(modBus)` during normal module setup. It registers fluid types, fluids, effect, modern bucket, and its cosmetic S2C channel. Forge/client event subscribers in the package register themselves.
2. In the item factory, call `CleansingModule.create(CatalogModule.Spec)` before the generic fallback (or return its result specifically for `sanity_soap` / `bath_salts` in `ItemMechanics.create`). It returns `null` for unrelated IDs. Existing catalog IDs, stack limits, textures and item models are retained; these items no longer receive the visual-only tooltip.
3. In `CatalogBlocks.create`, return `CleansingModule.createPurifyingBlock()` for the existing `purifying_fluid` ID. Keep it without a BlockItem. It must replace the generic CatalogBlock: the fluid's lazy block supplier verifies the functional type. The actual block exposes `LiquidBlock.LEVEL` 0..15. No second registration of that block is made in the module.
4. Include `CleansingModule.ITEMS` in the creative tab if the modern replacement for the old universal bucket is to be shown. `PURE_BUCKET` ID is `thaumcraft:purifying_fluid_bucket`, maximum stack 1, empty-bucket remainder, rare. `PURE` is `thaumcraft:purifying_fluid`; `FLOWING_PURE` is `thaumcraft:flowing_purifying_fluid`; `PURE_TYPE` is `thaumcraft:purifying_fluid` in the FluidType registry.
5. Modify `ResearchEvents.decayTemporaryWarp` to return `false` while `CleansingSupport.isProtected(player)` is true. The original `PlayerEvents` skips the entire periodic warp-event check under Ward; temporary-warp decay was inside that check. Guarding the public decay method also covers direct callers. Future random warp-event dispatch must use the same Ward guard. Do not claim those events are already implemented.
6. Resources: blockstate `purifying_fluid.json` may use the ordinary `minecraft:block/water` model for every state; the actual world mesh comes from the fluid renderer. Bucket model uses `forge:fluid_container`, `forge:item/bucket` parent and `fluid: "thaumcraft:purifying_fluid"`. Warp Ward sprite is the exact 18x18 crop at **x54,y234** from original `textures/misc/potions.png` (index 3 + 2*8 = 19), saved as `textures/mob_effect/warp_ward.png`. Do not crop y36: the original icon's vanilla potion atlas base v is 198. Add effect/bucket translations, e.g. `effect.thaumcraft.warp_ward`, `item.thaumcraft.purifying_fluid_bucket`. Existing soap/salts/block translations remain usable.

`CleansingClientSetup` already registers **both** fluids as `RenderType.translucent` and the bucket's `DynamicFluidContainerModel.Colors`. Root should not duplicate these client handlers. Original `textures/misc/particles.png` and registered `craftstart.ogg` are already present; no particle PNG or new sound registry is required. The new effect uses Minecraft's normal effect duration, persistence, particles and S2C synchronization.

## Sanity soap

Original maximum use duration is **100** and animation is **BLOCK**. Right click begins using the active hand and succeeds. `onUsingTick` computes `100 - count` and calls the old `stopActiveHand` only when that value is **greater than 95**. The stopped-use callback has the same strict condition. Thus elapsed 95 does nothing; elapsed 96 consumes one soap and cleanses. The modern equivalent is `LivingEntity.releaseUsingItem()`, because modern `stopUsingItem()` alone does not call `Item.releaseUsing`.

Completed soap shrinks by one even in creative. Server-only mutation uses the actual hand stack. Normal removal strength is **1**, plus **1** for active Warp Ward, plus **1** if the block at floor(player x/y/z) is purifying fluid. The fluid bonus does not require a source; flowing fluid qualifies. A bath by itself changes no warp balances. Soap wipes **all temporary warp**, leaves permanent warp untouched, and attempts the normal-warp change described next. It has no research gate and can be used with zero warp; it is still consumed.

### BETA26 overdraw quirk

`InternalMethodHandler.addWarpToPlayer(player, amount, type)` reads current warp. If `amount < 0 && current + amount < 0`, it assigns **`amount = current`**, a positive delta, before `PlayerWarp.add`. JAR bytecode explicitly copies local current into amount; it does not negate it. This branch is retained in `CleansingSupport.originalNormalWarpDelta` instead of silently correcting the original.

| Normal before | Strength | Normal after |
| --- | --- | --- |
| 0 | 1/2/3 | 0 (soap skips the normal call) |
| 1 | 1 | 0 |
| 1 | 2 or 3 | 2 |
| 2 | 2 | 0 |
| 2 | 3 | 4 |
| 3 | 3 | 0 |
| 12 | 3 | 9 |

Each warp component remains capped at 500 by the store. A positive overdraw delta updates the original warp counter to the total current balances. Negative changes leave that counter unchanged. Temporary wipe passes exactly minus the current amount, so it never enters this overdraw branch. Root's KnowledgeStore handles saved state, discovery notification and S2C research sync; the cleansing helper only reproduces the original delta.

## Bath salts

There is **no right-click water conversion** in TC6. The dropped item's custom lifespan is **200 ticks**. At the normal item-expire event, it checks the block containing the item: exactly `Blocks.WATER`, metadata 0. Modern equivalent is an actual `Blocks.WATER` block with a source fluid state; waterlogged blocks, flowing water and other mod fluids fail. If valid, that one block becomes the purifying source. The expire event is not canceled, so **the entire dropped stack disappears**, including a stack of 64. Salt outside valid source water simply expires. The port preserves that inefficient whole-stack consumption.

The event subscriber runs on the logical server. Another mod canceling expiry is respected rather than performing a conversion for an item that is not expiring. This is a compatibility adaptation; TC6's default listener did not receive canceled events either.

## Purifying fluid

`FluidPure` uses original vanilla `blocks/water_still` / `blocks/water_flow`, light **5**, rarity **RARE**, ARGB color decimal **2013252778**. Modern texture names are `minecraft:block/water_still` and `minecraft:block/water_flow`; tint is used as the original 32-bit value, including alpha. Material was a distinct `MaterialLiquid(MapColor.SILVER)`, not vanilla `Material.WATER`; the modern type does not hydrate, drown, swim, extinguish or support boats. Map color is light gray. The horizontal collision multiplier is **1 - quantaPercentage/2**; a full/source block halves x/z motion, leaving y untouched.

Ward is granted only on the server, only to players, only from a **source**, and only if they do not already have Ward. Granting it then removes that source to air. A protected player leaves the source intact; flowing liquid and non-player entities grant nothing and consume nothing. Flow can still spread from remaining sources.

Let `p` be permanent warp alone (neither normal/temporary nor armor-derived effective warp). Set `d = max(1, floor(sqrt(p)))`; duration is **min(32000, 200000/d)** ticks, amplifier 0, ambient true, particles true. Representative exact durations:

| Permanent warp | Divisor | Ward ticks |
| --- | --- | --- |
| 0 | 1 | 32000 |
| 48 | 6 | 32000 |
| 49 | 7 | 28571 |
| 100 | 10 | 20000 |
| 500 | 22 | 9090 |

An existing Ward is not extended or replaced by another source bath, and that source remains in the world. Once it expires, another bath can grant it again. Soap can receive both bonuses when standing in a remaining source while already protected, or in flowing purifying fluid.

The port uses an actual `ForgeFlowingFluid` source/flowing pair and `LiquidBlock`, allowing world placement, spreading, bucket pickup and dispensing through modern native fluid APIs. Infinite source creation is disabled. Tick delay 5, slope distance 4, level decrease 1; these correspond to the original default water-viscosity, eight-quanta fluid parameters. The original Forge 1.12 Classic flow algorithm is not copied verbatim: the modern falling LEVEL bit and height mesh are a deliberate API adaptation. Falling fluid collision uses full amount; display bubbles on falling fluid use its top surface instead of applying `8-meta` to a modern meta >=8. Native modern custom-fluid immersion/flow and fluid-item physics remain modern, even though vanilla-water tags/properties are not added; flow pushing is disabled to avoid inventing a vanilla-water current for the distinct original material. Exact legacy vertical immersion physics have not been claimed.

The modern bucket's dispenser behavior is registered explicitly with Forge `DispenseFluidContainer` in common setup; inheriting `BucketItem` alone does not register it. A GameTest ticks the actual dispenser and requires a source placed in front of it and an empty bucket retained in its inventory.

## Warp Ward

Original potion registration uses color decimal **14742263**, beneficial classification, no tick action, icon (3,2), and effectiveness 0.25. Modern ID is `thaumcraft:warp_ward` (the old mixed-case `warpWard` registry ID cannot be used as a modern ResourceLocation). There is no damage reduction, hunger cure or general invulnerability. Presence suppresses warp-event dispatch; soap checks it for one extra normal-warp-removal strength. Its old splash-effectiveness property is not used because this item chain never makes a splash potion. No brewing recipe or bottle version is invented.

## Visual and network contract

- While using soap, original client callback makes 10 pink bubbles per callback around x/z `position-.5+random[0,1)`, y `bbox.minY+random*height`, color (1,.8,.9). There is a 20% local chorus-flower death sound, volume .1, pitch 1.5..1.7.
- On accepted completion, there are 40 bubbles around x/z `position-.5+random[0,1.5)`, color (1,.7,.9), and original craftstart sound volume .25, pitch 1. Sound is now sent by the server, and the cosmetic completion packet goes to the player and tracking viewers. It cannot request or award warp changes. Dimension is checked on client receipt; stale packets cannot draw in a different world. This server confirmation avoids optimistic client cleanse feedback.
- Soap bubbles reproduce `FXDispatcher.crucibleBubble`: age 15..24, scale 0.3–0.6, random Gaussian velocity increments .002 on each axis, gravity -.001 with original .04 factor, friction .9800000190734863, and final frames array [65,66,66]. Frame lookup deliberately retains the source's reverse `maxAge-age` indexing.
- Purifying fluid random display has 10% white bubble chance: age 10..19, scale 0.3–0.6, random movement .001, gravity -.01, alpha .25, frame64 with final [65,66]. Lava-pop display sound has 2% chance, volume 0.1–0.2, pitch .9..1.05.
- Atlas is original `textures/misc/particles.png`, a 64x64 grid, cells64..66. Camera-facing quads use original half-size .1*scale, collision and ordinary world light. Original FXGeneric defaults to ParticleEngine layer0: SRC_ALPHA/ONE additive blending, depth testing, no depth writes. The modern batch uses that blend via LIGHTNING_TRANSPARENCY and PARTICLES_TARGET. Bubble lists clear on client world changes, pause with the game and are batched once after ordinary particles. Hiding the HUD does not hide world particles.
- Warp and item changes are server-only. Minecraft use-state/effect synchronization and the existing ResearchNetwork carry state. CleansingNetwork contains exactly one S2C cosmetic message and no C2S discovery/cleansing mutation request.

## Verification handoff

No Gradle, GameTest or live-client run was made by this owner. Root should compile after the freeze, then verify: original 95/96 release boundary and auto completion; creative consumption; complete TEMP wipe with PERMANENT unchanged; normal-overdraw examples above; salt expiry at200 and complete-stack destruction; source-only conversion; source-only Ward and removal; existing Ward retaining a source; exact Ward duration at0/49/100/500 permanent; protected scheduled TEMP decay; source/flowing tint+transparency, bucket color and placement/pickup; effect icon; soap bubbles and remote-view completion. Random warp effects are still unimplemented and must remain reported as such.
