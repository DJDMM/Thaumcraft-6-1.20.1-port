# TC6 BETA26 equipment armor audit and implementation

Audit date: 2026-10-02. This document concerns Thaumcraft **6.1.BETA26**, not TC4 or TC5.

Pinned reference: `work/thaumcraft6-reference`, commit `954022bb777b7546281fb36df8522f0ba6b43f81`. Official binary: `work/Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA256 `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.

## Integration contract

The existing catalog factory now constructs operational armor. The original textures, `CatalogArmorExtension`, and legacy geometry are retained. New classes live under `thaumcraft.equipment.armor`; `ArmorEffects` and `GogglesArmorClient` register themselves through sided Forge event subscribers. No additional module registration is required.

Root-owned wiring:

- Replace EquipmentModule's four anonymous thaumium ArmorItems with `CatalogArmorItem.thaumium(ArmorItem.Type)`. `Tc6ArmorMaterials.THAUMIUM` is also available as an ArmorMaterial. Do not register duplicate item IDs.
- Root's charge, warp, discount interfaces and `RechargeSupport` are used directly. Traveller boots never recharge themselves; capacity is 240 and HUD policy PERIODIC. Root's recharge pedestal is the recharge consumer.
- Goggles have a real container-content renderer in `GogglesArmorClient`, reading synchronized `IAspectContainer` block entities. To avoid a second generic block-composition overlay when holding a scanner, the scanner renderer should defer when `GogglesArmorClient.hasContainerPopup(targetBlockPos)` is true. Existing scanner Java was not edited by the armor owner.
- `GogglesArmorSupport.hasGoggles(Player)` and `hasRevealer(Player)` provide the original marker consumers for other devices. Goggles alone **do not** enable the thaumometer aura gauge.
- `FortressArmorSupport.reduceWarpEventSeverity(Player,int,RandomSource)` is the original mask-0 hook for a future operational warp-event selector. It changes severity, not stored warp. The current port has knowledge warp storage but no original WarpEvents selector; this hook is not silently substituted with periodic removal of warp.
- Root's global EquipmentTooltips owns charge/warp/vis/infusion tooltip lines. CatalogArmorItem adds only fortress goggles/mask attachments, avoiding duplicate global lines.
- Existing recipe data and accessory slot integration remain outside this armor change. Goggles can be worn in the helmet slot and queried in the original available slots; no fictitious Baubles inventory is introduced.

No Gradle or Minecraft launch was performed by the armor owner. Root must compile and exercise server/client checks after shared wiring.

## Exact material and item values

The native 1.12 base durability multipliers are feet 13, legs 15, chest 16, head 11. All values below are per item, before enchants. Arrays in ThaumcraftMaterials use **feet, legs, chest, head**. Material toughness applies independently to each worn piece; every TC6 material has zero knockback resistance.

| Family / registered pieces | Max damage H/C/L/B | Defense H/C/L/B | Toughness | Enchantability | Rarity | Repair |
|---|---|---|---:|---:|---|---|
| Thaumium, H/C/L/B | 275/400/375/325 | 2/6/5/2 | 1 | 25 | uncommon | thaumium ingot |
| Cloth robes, C/L/B | —/400/375/325 | —/3/2/1 | 1 | 25 | uncommon | enchanted fabric |
| Void metal, H/C/L/B | 110/160/150/130 | 3/8/6/3 | 1 | 10 | uncommon | void ingot |
| Void robes, H/C/L | 198/288/270/— | 4/9/7/— | 2 | 10 | epic | void ingot |
| Fortress, H/C/L | 440/640/600/— | 3/7/6/— | 3 | 25 | rare | thaumium ingot |
| Crimson plate, H/C/L | 198/288/270/— | 2/6/5/— | 0 | 13 | uncommon | iron ingot |
| Crimson robes, H/C/L | 187/272/255/— | 2/5/4/— | 0 | 13 | uncommon | iron ingot |
| Crimson praetor, H/C/L | 330/480/450/— | 3/7/6/— | 1 | 20 | rare | iron ingot |
| Crimson boots, B | —/—/—/195 | —/—/—/2 | 0 | 9 | uncommon | iron ingot |
| Goggles, H | 350/—/—/— | 1/—/—/— | 1 | 25 | rare | brass ingot |
| Traveller boots, B | —/—/—/350 | —/—/—/1 | 1 | 25 | rare | leather |

There are 29 registered armor pieces across these families, including the four pre-existing thaumium pieces owned by EquipmentModule. Neither fortress nor void robes has a registered matching boot, and cloth robes have no helmet. Crimson boots use vanilla IRON, not CULTIST_PLATE.

Source: `api/ThaumcraftMaterials.java`, `common/config/ConfigItems.java:238–265`, and all eleven `common/items/armor/Item*.java` classes. Official JAR `javap -c -p` confirmed overrides 350/240, rarity/warp/discount values, repair methods, and the fortress loop behavior.

## Vis discount, warp and dye

- Goggles: 5% discount; no inherent warp.
- Cloth chest/legs: 3% each; cloth boots: 2%; no inherent warp.
- Void metal: 1 inherent gear warp per piece; no discount.
- Void robes: 5% discount and 3 inherent gear warp per piece.
- Crimson robes and crimson boots: 1% discount and 1 inherent gear warp per piece.
- Fortress, crimson plate, praetor, thaumium and traveller boots: zero inherent warp and zero discount. A goggles attachment does not turn fortress armor into an IVisDiscountGear item with a 5% discount.

Discounts are additive percentage points. Gear warp is an equipment contribution; wearing armor does not award permanent character warp. Root's existing NBT/infusion additions can operate through the shared interfaces.

Both dyeable families use `display.color` integer NBT and default **6961280**. The original hasColor returns true even without a custom color. Dye recipes must therefore blend against this default color. Washing removes only `display.color`, restoring the default, and consumes one level of water even when no custom color was present. The modern adapter predicts success client-side and performs the mutation only on the server. Non-water cauldrons are not accepted.

Void and void-robe items contain **two independent original repair entry points**: onUpdate and onArmorTick, each -1 item damage every 20 entity ticks, server-side. The port retains both. Carried stacks repair by 1 per twenty ticks; worn player stacks can repair by 2 because Forge invokes both inventory and armor callbacks. This deliberately preserves the source rather than silently deduplicating the calls. [Forge's 1.12 inventory patch](https://github.com/MinecraftForge/MinecraftForge/blob/1.12.x/patches/minecraft/net/minecraft/entity/player/InventoryPlayer.java.patch) adds the second armor callback. The Forge 1.20 IForgeItem callback also invokes both pathways. Repairs stop at zero, require a living inventory holder, do not consume vis, and do not happen to a dropped item.

## Fortress set, attachments and special damage

The official JAR confirms the unusual loop: original getProperties inspects armor inventory indices 1..3 (legs/chest/head); it adds one Armor and one Toughness for the **first** fortress piece it encounters, plus one Armor for **every fortress piece with any present mask NBT key**. This loop is repeated for each fortress item whose properties are queried.

For p fortress pieces and m mask-bearing fortress pieces, added armor is **p × (1 + m)** when p > 0, and added toughness is **p**. Ordinary material defense/toughness remains in addition. Examples:

- One unmasked helm: material 3 armor / 3 toughness, plus 1 / 1.
- Complete unmasked H/C/L set: material 16 / 9, plus 3 / 3, giving 19 / 12.
- Complete set with one masked helm: material 16 / 9, plus 6 / 3, giving 22 / 12.
- Illicit masks on every fortress piece still count by key presence, matching BETA26. The value is not checked when computing protection bonuses.

Only players get these extra set attributes. A mob gets the ordinary material attributes. The port uses removable, transient armor/toughness attribute modifiers, updates on equipment changes and player ticks, and catches attachment NBT changes without adding permanent entity state.

Original per-piece special absorption:

- Fortress: ordinary ratio defense/25, priority 0; magic ratio defense/35, priority 1; otherwise fire or explosion ratio defense/20, priority 1.
- Void robes: ordinary defense/25, priority 0; magic defense/35, priority 1.
- Other unblockable sources have ratio zero. AbsorbMax is maxDamage + 1 - currentDamage.
- Direct special armor damage is max(1, floor(absorbedDamage)); the original damageArmor callback exempts exactly FALL.
- These special ratios precede the remaining vanilla armor/toughness pass. They are not merely additional visible armor points.

Forge 1.20 has no ISpecialArmor. The port applies capped priority groups through LivingHurtEvent, then lets modern vanilla apply ordinary attributes/durability to the remainder. The pinned reference builds with Forge 14.23.5.2860. Its default handleUnblockableDamage is false, and neither TC6 class overrides it: sources mapped to modern BYPASSES_ARMOR are consequently excluded, even though getProperties contains a magic branch. [Forge's original special armor contract](https://github.com/MinecraftForge/MinecraftForge/blob/1.12.x/src/main/java/net/minecraftforge/common/ISpecialArmor.java) explains the separate extra attributes and callback gate. The port retains the material/base attributes through the modern vanilla pass; it does not recreate engine-wide double counting of unrelated vanilla armor from the older framework.

Mask behavior is from `EntityEvents.java:137–153` and `WarpEvents.java:63–67`:

- Mask 0, Grinning Devil: subtract 2 + random.nextInt(4) from the selected warp-event severity. It does not reduce permanent/temporary warp. Its hook is exposed; original warp-event selection is still absent in the port.
- Mask 1, Angry Ghost: when the wearer is hurt by a living attacker, chance incomingDamage/10 to apply WITHER for 80 ticks, amplifier 0, to the attacker.
- Mask 2, Sipping Fiend: when the wearer's damage source hurts a target, chance incomingDamage/12 to heal the wearer by 1 health point.
- Probabilities are not clamped; damage above the denominator guarantees success. Only a player wearing a fortress head item with the corresponding mask activates the latter effects.
- Presence of `goggles` NBT, even byte zero, enables the goggles/revealer methods. Original mask and goggles geometry remains unchanged.

## Traveller boots

The official bytecode confirms max damage 350, max charge 240, and `energy` integer NBT timer. Every twenty server player ticks:

1. If energy > 0, decrement it.
2. Otherwise consume one charge and set energy to 60 if the charge consumption succeeded.
3. Save energy whether consumption succeeded or not.

Thus a charge is debited while standing idle; an energy=60 cycle takes 61 timer updates before the next debit. Movement uses the charge count read at the beginning of the armor tick, **not** energy. When the last charge is consumed, stored energy does not continue powering movement. The last charged tick can still move because the hasCharge result was sampled first.

With charge > 0, no creative flight, and forward movement > 0:

- Ground: add a forward relative impulse .05; underwater ground: .0125.
- Air in water: additional forward relative impulse .025.
- Original jumpMovementFactor becomes .05 in air; modern Player removed that mutable field. The adapter adds the difference from modern .02 normal / .026 sprinting air input. This is a documented physics adaptation and needs gameplay confirmation.
- While not sneaking, step height becomes 1. Previous height is restored upon sneaking or removal of the boots. The source does not restore merely because charge empties or forward movement stops; that quirk remains. The port applies the height on both sides for server/client collision consistency instead of only on the old client.
- The LivingJumpEvent handler adds **.2750000059604645** vertical motion when charged, without a sneaking/flight condition.
- FALL damage becomes max(0, damage/2 - 1); cancel when the result is below 1. This protection does **not** require charge and applies only to original FALL's modern DamageTypes.FALL mapping.

Root recharge utilities own `tc.charge`; `energy` retains its original spelling. No passive aura drain/recharge, damage-based charging, or invented durability cost was introduced.

## Goggles mechanics and visual verification needed

Original EntityUtils.hasGoggles checks main hand, four armor slots, and Baubles. It intentionally omits offhand. hasRevealer checks both hands, armor and Baubles. The port implements these available-slot rules. Void-robe HEAD returns true; its other pieces return false. Fortress attachment checks NBT presence. Baubles HEAD rendering/equipping awaits an actual accessory integration.

BETA26 goggles show container essentia and device popups. They do not reveal old TC4 aura nodes; TC6 has no such nodes. A container's world popup does not require prior scanning or Shift. Current popup renders synchronized IAspectContainer contents in rows of five, with original tint/75% opacity/light UV2 (0,220), original horizontal order, readable count geometry, original above-container/face offset, and the same tick-based easing adaptation already used by the scanner. The original scanner's unknown-question-mark suppression is not imported; it was never a goggles requirement.

Operational containers (including jars) are supported immediately. Future devices can use IGoggles/IRevealer and the helper; the old note-block querying and custom IGogglesDisplayExtended text network are not fabricated for catalog-only devices.

Root should verify:

- All 29 item values and lazy repair ingredients; four thaumium registrations use the new factory.
- Cloth/void robe dye crafting and cauldron reset, retaining default color.
- Carried and worn void repair entry points, without repair below zero.
- Fortress set updates 4/4 for one helm and 19/12 or 22/12 for a complete unmasked/masked set; removal clears transient modifiers.
- Mask 1/2 deterministic probability-one hits and mask-0 exposed severity hook.
- Traveller timer, last-charge quirk, charged jump, uncharged FALL protection and removal/sneak step restoration.
- Client worn goggles, void-robe helm and fortress attached goggles reveal an actual filled jar's amount; chest/legs without a valid revealer do not.
- Goggles popup numbers/asymmetric amounts, synchronized amounts, no scanner popup overlap after root integration, GUI-hidden and equipment-removal gating.
