# Механики всех предметов TC6 BETA26

Проверено 2 октября 2026 года. База: закреплённый `work/Thaumcraft-1.12.2-6.1.BETA26.jar`, исходники `work/thaumcraft6-reference` и текущий порт Forge 1.20.1. Это TC6, без переноса поведения TC4/TC5. Ведомость охватывает **108 исходных регистраций / 185 metadata-форм ConfigItems** из `assets/thaumcraft/catalog/items.json`; дополнительные варианты NBT не увеличивают это число. BlockItem устройств, 200 блоков и 43 EntityType — отдельные ведомости.

Состояние таблицы — до завершения текущего пакета оборудования после 0.9.1. Работающие модули перечислены отдельно от каталога. Будущая фабрика инструмента, класс брони или регистрация интерфейса не считаются готовой механикой без проверки её потребителя. Для всех строк ещё требуется соответствующий исходный рецепт и research gate; архив книги не доказывает достижимость предмета в survival.

## Заряд и его потребители

Итог пакета 0.10 описан в [EQUIPMENT.md](../../EQUIPMENT.md) и [VALIDATION.md](../../VALIDATION.md): 29 armor items, 17 tools/weapons, recharge pedestal/runic armor shielding, food/curios/loot/pearl/sanity и цепочка очищения. Таблица ниже сохраняет исходную ведомость зависимостей **на входе в пакет**; слова «до пакета visual» не являются текущим статусом перенесённых классов. Рецепты, research gates и зависимые системы не помечаются готовыми вместе с предметом.

`RechargeHelper.NBT_TAG` — **`tc.charge`**, целый NBT Int. Заряжаемость определяется `IRechargable`, а не наличием ключа. Неподходящий/пустой stack возвращает charge `-1`, подходящий без NBT — `0`. Заряд прибавляется до `getMaxCharge`; успешное потребление уменьшает тот же ключ. Максимумы: Traveller Boots 240, Verdant Charm 200, Grapple Gun 100. Заряд не равен durability и не является запасом vis кастерной перчатки TC4.

| Источник или потребитель | Исходный ритм и результат |
| --- | --- |
| Общий `rechargeItem` | Вспомогательный метод; сам не тикает. Документация рекомендует общий ритм 5 тиков. Ограничивает запрос свободной ёмкостью; при игроке проверяет `AuraHelper.shouldPreserveAura`. |
| Recharge Pedestal | Только сервер, `counter++ % 10 == 0`, до 5 vis/заряда в одном stack. Не требует игрока; обновляет контейнер/блок после успеха. |
| Amulet of Vis, found/crafted | Только надетый Bauble, сервер, раз в 40/5 тиков. Ищет hotbar 0–8, затем Baubles по слотам, затем armorInventory 0–3; первый успешный stack получает **1**, после чего метод возвращается. Main inventory 9–35 и offhand отсутствуют в этом исходном обходе. |
| Traveller Boots | Сервер раз в 20 тиков: уменьшает отдельный Int `energy`, либо при `energy <= 0` тратит 1 `tc.charge` и ставит `energy=60`. Скорость/прыжок проверяют наличие `tc.charge`, а не `energy`. Это не списание заряда за каждый шаг. |
| Verdant Charm | Сервер раз в 20 тиков: Wither 20, Poison 10, Flux Taint 5; первый успешный эффект завершает обработку. `type=1`: 5 заряда за 1 HP; `type=2`: 1 за воздух до 300 при air<100 либо 1 за 1 food/0.3 saturation. |
| Grapple Gun | Серверный запуск/отпускание grapple entity, 1 заряд при успешном запуске. Чанк, связь с игроком и поведение полёта принадлежат EntityGrapple. |

Байткод `RechargeHelper.rechargeItem` подтверждает `AuraHelper.drainVis(..., false)` (offset 74) **до** `f2i` (77): дробный vis может уйти из ауры без целого начисленного заряда. Это подтверждённая особенность BETA26, не ошибка декомпиляции. `shouldPreserveAura` — завершённое `AURAPRESERVE` и `vis/base < 0.1`; RechargeHelper проверяет его только при ненулевом player, поэтому пьедестал с player=null этот guard не применяет. Порт вправе явно защитить отрицательный запрос, повреждённый NBT и клиентские мутации; сохранение дроби вместо исходного округления должно быть обозначено адаптацией. При recharge важны сохранение общего vis/charge, ограничение ёмкости, отсутствие повторного списания и синхронизация actual inventory stack.

## Vis discount, Warp и специальная броня

`CasterManager.getTotalVisDiscount` суммирует **надетые Baubles и четыре слота брони**, затем вычитает `(max(exhaustion amplifiers)+1)*10`, если есть обычный/инфекционный Vis Exhaustion; результат `/100F`. Inventory/offhand сами по себе не дают скидку. Goggles 5%; cloth chest/legs 3%, boots 2%; каждая Void Robe 5%; crimson robe/boots 1%; Apprentice Ring 5%; Voidseer `int(min(permanentWarp,100)/100F*25)` процентов и gear Warp `discount/5`.

Потребители: проверка/оплата arcane workbench, его GUI, `ItemCaster.getConsumptionModifier`. **JAR** у обоих server workbench классов выполняет `int(vis * (1 - discount))` (`fmul`, затем `f2i`): ContainerArcaneWorkbench offsets 41–58, SlotCraftingArcaneWorkbench 139–150. GUI использует ту же формулу на 125–134, показанный процент — `int(discount*100F)` на 136–142. Видимое в исходнике `vis *= (int)(1-discount)` — ошибочная декомпиляция. Перчатка ограничивает consumption modifier снизу 0.1; нельзя автоматически переносить это ограничение на исходный workbench. Нужны единая серверная цена, такая же цена preview, повторная проверка непосредственно перед commit и сохранение crystal/ingredient/remainder semantics.

Gear Warp: `PlayerEvents.getFinalWarp` складывает `IWarpingGear.getWarp` и Byte **`TC.WARP`**. `WarpEvents.getWarpFromGear` обходит **только main hand**, броню и Baubles; offhand не участвует в оригинале. Предмет в сумке не наращивает постоянное сохранённое Warp. Снаряжение влияет на эффективное Warp, тяжесть события и спад warpCounter. Полная система проверяет событие раз в 2000 тиков при отсутствии Warp Ward и выключенном wussMode. В порте 0.9.1 есть лишь ограниченное хранение/спад temporary Warp; такой storage не реализует все события.

Fortress/ Void Robe реализуют `ISpecialArmor` Forge 1.12. Соотношения для каждой части: обычное `defense/25`; магия `defense/35`, priority 1; Fortress огонь/взрыв `defense/20`, priority 1; остальные unblockable `0`. Верхняя граница поглощения зависит от remaining durability. `damageArmor` не изнашивает их от FALL. Fortress дополнительно изменяет Armor/Toughness properties по надетым fortress частям в armorInventory 1–3. Исходный счётчик имеет `++q <= 1`, а не равномерный бонус за каждую часть; NBT `mask` проверяется по **наличию**, поэтому маска 0 не равна отсутствию маски.

Потребитель [Forge 1.12 ISpecialArmor](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.12.x/src/main/java/net/minecraftforge/common/ISpecialArmor.java) сначала применяет special absorption, затем один обычный CombatRules pass по остатку и исходным armor attributes плюс дополнительным properties. `handleUnblockableDamage` по умолчанию false; TC6 классы его не переопределяют. Поэтому сам метод getProperties описывает magic/fire branch, но vanilla unblockable MAGIC/ON_FIRE до него не допускаются. IN_FIRE и подходящие explosions могут использовать специальную ветку. Порт должен сохранять эту разницу и не выполнять второй обычный armor pass поверх уже полностью обработанного damage.

Fortress helmet `goggles` даёт функции `IGoggles/IRevealer` по наличию ключа; **не даёт отдельную vis-скидку Goggles**. `mask=0` уменьшает тяжесть Warp на `2+nextInt(4)`; `mask=1` при входящем ударе от LivingEntity даёт атакующему Wither 80 при `random < damage/10`; `mask=2` лечит атакующего владельца на 1 при `random < damage/12`. Это потребители `WarpEvents`/`EntityEvents`, а не свойства только модели или tooltip.

`TC.RUNIC` — отдельный Byte, не `tc.charge`. Runic shielding суммирует armor/Baubles раз в 20 тиков, работает через absorption и server aura; задержки восстановления оригинал измеряет wall-clock и получает из config. Портирование зачарованного item NBT без процесса восстановления, исключений damage и удаления лишней absorption не считается shielding.

## Материалы и локальные характеристики

Порядок armor defense в таблице: **boots, legs, chest, helm**. Durability умножается на исходные базы `13,15,16,11`; Goggles и Traveller Boots имеют отдельное max damage **350**, Crimson Boots используют обычное **IRON**, не cultist robe material.

| Материал | Tool level / durability / speed / damage / enchantability либо armor multiplier / defense / enchantability / toughness | Repair |
| --- | --- | --- |
| Thaumium tool | 3 / 500 / 7 / 2.5 / 22 | Thaumium ingot |
| Void tool | 4 / 150 / 8 / 3 / 10 | Void ingot |
| Elemental tool | 3 / 1500 / 9 / 3 / 18; ItemElementalHoe отдельно возвращает enchantability 5 | Thaumium ingot |
| Crimson Blade CVOID | 4 / 200 / 8 / 3.5 / 20 | Void ingot |
| Primal Crusher PRIMALVOID | 5 / 500 / 8 / 4 / 20 | Void ingot |
| Thaumium armor | 25 / 2,5,6,2 / 25 / 1 | Thaumium ingot |
| Special cloth/goggles/traveller | 25 / 1,2,3,1 / 25 / 1 | Fabric для cloth; brass для Goggles; leather для boots |
| Void armor | 10 / 3,6,8,3 / 10 / 1 | Void ingot |
| Void Robe | 18 / 4,7,9,4 / 10 / 2 | Void ingot |
| Fortress | 40 / 3,6,7,3 / 25 / 3 | Thaumium ingot |
| Cultist Plate | 18 / 2,5,6,2 / 13 / 0 | Iron ingot |
| Cultist Robe | 17 / 2,4,5,2 / 13 / 0 | Iron ingot |
| Cultist Leader | 30 / 3,6,7,3 / 20 / 1 | Iron ingot |

Void tools, Void Armor/Robe, Crimson Blade и Primal Crusher ремонтируют 1 damage каждые 20 тиков в инвентаре LivingEntity; броня имеет также worn tick. При надевании игроком оба исходных hooks вызываются: перенос сохраняет ремонт до 2 пунктов worn armor и 1 carried gear на границе 20 тиков. В исходных void tools/weapon `onUpdate` нет client-side guard; у void armor он есть. Серверная авторитетность tools — явная адаптация. Void Sword накладывает Weakness 60; остальные void tools — 80 через left-click hook; Crimson Blade Weakness 60 + Hunger 120, с исходными PVP guards.

## Полная ведомость 185 форм

«Локально» означает, что механику можно реализовать предметом/vanilla hooks и уже существующими stores, но не что она уже готова. «Система» означает существенную зависимость от отдельной TC6 серверной системы. Декоративные ингредиенты не нуждаются в выдуманном right-click действии; их полнота проверяется через recipes/loot/ore handling, которые их создают и расходуют.

| Формы (ID без namespace) | Число | Исходная механика и зависимости; состояние на входе в пакет |
| --- | ---: | --- |
| amber, quicksilver, ingot_thaumium, ingot_void, ingot_brass, nugget_iron, nugget_copper, nugget_tin, nugget_silver, nugget_lead, nugget_quicksilver, nugget_thaumium, nugget_void, nugget_brass, nugget_quartz, nugget_rareearth, cluster_iron, cluster_gold, cluster_copper, cluster_tin, cluster_silver, cluster_lead, cluster_cinnabar, cluster_quartz, fabric, vis_resonator, tallow, mechanism_simple, mechanism_complex, plate_brass, plate_iron, plate_thaumium, plate_void, filter, morphic_resonator, mirrored_glass, void_seed, mind_clockwork, mind_biothaumic, module_vision, module_aggression, jar_brace, grapple_gun_tip, grapple_gun_spool | 44 | ItemTCBase: ингредиенты, loot/ore/smelting/рецепты. Некоторые материалы уже работают в начальной алхимии; полный набор рецептов отсутствует. jar_brace применяется к рабочим банкам. Остальные не требуют симуляции несуществующего use. Clusters зависят от native processing/refining, minds/modules — от construct AI. |
| chunk_beef, chunk_chicken, chunk_pork, chunk_fish, chunk_rabbit, chunk_mutton | 6 | Локально: Food 1/0.3, use duration 10; источники — Infernal Furnace/meat processing. До пакета обычные visual items. |
| celestial_notes_sun, celestial_notes_stars_1..4, celestial_notes_moon_1..8 | 13 | Работают celestial scanning/card inputs 0.6; не прочитывать их как новую теорию по произвольному C2S сообщению. |
| seal_blank, seal_pickup, seal_pickup_advanced, seal_fill, seal_fill_advanced, seal_empty, seal_empty_advanced, seal_harvest, seal_butcher, seal_guard, seal_guard_advanced, seal_lumber, seal_breaker, seal_use, seal_provider, seal_stock, seal_breaker_advanced | 17 | Система: server SealHandler, placement/ownership, per-seal GUI/config, tasks/reservations, persistent golem AI. Каталог/иконки не выполняют задачу. |
| baubles_amulet_mundane, baubles_ring_mundane, baubles_girdle_mundane, baubles_ring_apprentice, baubles_amulet_fancy, baubles_ring_fancy, baubles_girdle_fancy | 7 | Адаптация надетых Baubles нужна прежде consumers. Apprentice Ring даёт 5% скидки, остальные — заготовки/украшения; у всех возможен Runic NBT. |
| thaumium_pick, thaumium_shovel, thaumium_axe, thaumium_sword, thaumium_hoe | 5 | Уже обычные рабочие TieredItems; проверить фактическую добычу/оружейные атрибуты/repair. Это не elemental abilities. |
| void_pick, void_shovel, void_axe, void_sword, void_hoe | 5 | Локально: TieredItems, self-repair, Weakness/PVP guards, gear Warp 1. На входе visual; общие consumers Warp должны быть отдельно отмечены. |
| elemental_pick, elemental_shovel, elemental_axe, elemental_sword, elemental_hoe | 5 | Базовый tier локален; полный эффект — native clusters, infusion enchantment hooks, AOE harvesting/building, связная рубка дерева, attraction/vortex, growth/permissions/sneak. Один TieredItem недостаточен. |
| crimson_blade, primal_crusher | 2 | Локальные stats/self-repair/Warp 2 и weapon effects; Crusher дополнительно pickaxe+shovel и исходные Destructive/Refining infusion enchantments. На входе visual. |
| thaumium_helm, thaumium_chest, thaumium_legs, thaumium_boots | 4 | Уже vanilla armor с исходными материалами. Полнота recipes/research отдельна. |
| void_helm, void_chest, void_legs, void_boots | 4 | Локально: исходные armor stats, repair, self-repair, Warp 1; на входе временные leather stats. |
| cloth_chest, cloth_legs, cloth_boots | 3 | Локально: исходный материал, dye `display.color`, cauldron wash, скидки 3/3/2%. Требует оплаты crafting/caster. |
| void_robe_helm, void_robe_chest, void_robe_legs | 3 | Локально: материал, dye/wash, self-repair, 5%/часть, Warp 3/часть, revealing head. Специальный damage путь и revealing UI consumers нужны отдельно. |
| fortress_helm, fortress_chest, fortress_legs | 3 | Материал, ISpecialArmor set/remaining durability; NBT goggles/mask, combat/Warp consumers. На входе только worn geometry. |
| crimson_plate_helm, crimson_plate_chest, crimson_plate_legs | 3 | Локально: Cultist Plate материал/repair; источники loot и cultist AI не реализованы. |
| crimson_robe_helm, crimson_robe_chest, crimson_robe_legs | 3 | Локально: Cultist Robe материал, скидка 1/часть, Warp 1/часть. |
| crimson_praetor_helm, crimson_praetor_chest, crimson_praetor_legs | 3 | Локально: Cultist Leader материал/repair; custom geometry уже есть. |
| crimson_boots | 1 | Обычный IRON armor + скидка/Warp 1; не материал SPECIAL. |
| goggles | 1 | SPECIAL helmet max damage 350, brass repair, 5%, IGoggles/IRevealer и Bauble HEAD. Визуальная модель уже есть; весь revealing UI требует фактических tiles/aspects. |
| traveller_boots | 1 | SPECIAL boots max damage 350, leather repair; charge/energy, движение, прыжок +0.275, fall `max(0, damage/2-1)`, step restore. На входе лишь визуальная броня. |
| amulet_vis_found, amulet_vis_crafted | 2 | Надетый источник recharge, 40/5 тиков; не самозаряжаемый амулет. Требует адаптации equipment slots. |
| verdant_charm | 1 | Charge 200, NBT type 0/1/2, последовательность лечения выше; часть Flux Taint зависит от TC6 potion. |
| cloud_ring | 1 | Система надетых предметов + double jump state/client movement sync, fall reduction; не бесконечный jump boost. |
| voidseer_charm | 1 | Discount/Warp на основе permanent Warp; без permanent store нельзя объявлять рабочим исходный scaling. |
| curiosity_band | 1 | Server XP pickup: половина XP, вероятностные category observation/theory; не client-awarded знание. Нужен надетый слот. |
| charm_undying | 1 | Server LivingDeath hook, одноразовое удаление equipped charm, исходные totem HP/effects/stat/criteria; не passive бессмертие. |
| curio_arcane, curio_preserved, curio_ancient, curio_eldritch, curio_knowledge, curio_twisted, curio_rites | 7 | Локально с knowledge store: category-specific + random-category observation/theory, consumption. Eldritch/Rites Warp; Rites actualWarp>20, CrimsonRites research, возможное permanent Warp. На входе visual. |
| loot_bag_common, loot_bag_uncommon, loot_bag_rare | 3 | Server 8–12 loot draws, бросает EntityItem и тратит bag; исходные rarity loot pools нужны. Нет универсального placeholder-списка наград. |
| thaumonomicon, thaumonomicon_cheat | 2 | Обычная книга работает; cheat original grants research и отдельный режим должен проверяться отдельно от read-only archive порта. |
| focus_1, focus_2, focus_3 | 3 | Система FocusPackage/engine, мощности 15/25/50, дерево medium/effect/modifier, зарядка в Focal Manipulator, NBT/share/color и GUI. Пока лишь NBT-внешний вид. |
| phial_empty, phial_filled | 2 | Работают server jar transfer 10, inventory conversion, filters/capacity/NBT и scan contents 0.9. Alembic/smelter ещё отдельны. |
| label_blank, label_filled | 2 | Работают canonical label crafting/full-phial remainder, применение/снятие и drop NBT 0.9. Не производят аспекты фильтра при scan. |
| turret_basic, turret_advanced, turret_bore | 3 | Система owned constructs: placement permissions/bounds, inventory/ammo, target AI/LOS, GUI/upgrades; bore настоящий добывающий loop, vis/износ/drop. Entity-каталог NoAI не заменяет. |
| alumentum | 1 | Уже рабочий projectile и алхимический материал; исходные fuel/recipe свойства проверять отдельно. |
| bath_salts | 1 | Lifespan dropped item 200; ItemExpireEvent превращает исходную воду в purifying fluid. Требует fluid/Warp Ward. |
| bottle_taint | 1 | Server throwable + EntityBottleTaint impact, taint/flux system; эффект-каталог не делает projectile рабочим. |
| caster_basic | 1 | Система ICaster: focus insertion/selection, casting/cooldown, aura payment, focus engine/packets. TC6 перчатка не хранит заряд `tc.charge`. |
| causality_collapser | 1 | Server projectile + рифт/коллапс, причинность/flux и loot; требуется настоящая Flux Rift механика. |
| creative_flux_sponge | 1 | Creative-only server уборка flux/taint, finite bounds; aura store есть, полная taint/Rift система отсутствует. |
| crystal_essence | 1 | Аспектный контейнер, NBT/scan/name; исходные dynamic аспектные формы, crucible/crystal creation не равны шести обычным primal vis crystals порта. |
| enchanted_placeholder | 1 | Служебный item для рецептов/отображения infusion enchantment; не самостоятельный craft-результат для gameplay. |
| focus_pouch | 1 | Локальный server menu/container: 18 focus slots, ItemStackHelper NBT, held/worn identity, anti-dup на закрытии/смене stack. До пакета только icon. |
| golem_bell | 1 | Система: golem pickup/selection, sealing GUI, ownership, tasks и persistent drop props/xp. |
| golem | 1 | Server placement owned golem, `props` Long big-endian, xp/rank, traits/material/head/arms; visual item/entity имеют геометрию, рабочий AI отсутствует. |
| grapple_gun | 1 | Charge 100 + EntityGrapple physics/link/ownership, release on sneak, no отмены fall damage. До пакета visual. |
| hand_mirror | 1 | NBT link XYZ/dimension, linking to actual TileMirror и item-send GUI, remote loaded receiver/flux/anti-dup; renderer link=1 не реализует транспорт. |
| pech_wand | 1 | Расходуемый источник знания/FOCUSPECH после BASEAUROMANCY, observation/theory, не традиционный кастующий wand. |
| primordial_pearl | 1 | Durability/forms 0–2 pearl, 3–5 nodule, 6–7 mote; crafting remainder damage+1 только при damage<7, no repair/enchant. До пакета отображение/название и durability, без полного remainder contract. |
| resonator | 1 | Read-only server face-specific IEssentiaTransport amounts/suction, retrace subHit, special Buffer/Condenser readout. Банки уже дают полезную часть; pipe/condenser остались системами. |
| sanity_checker | 1 | Warp HUD/actual warp consumer; полный permanent/normal/temp store и server sync нужны до достоверного показания. |
| sanity_soap | 1 | Use >95/100 ticks: normalWarp -1..3 (Ward/purifying fluid), весь temporaryWarp очищается; server store, fluid/potion, sound/FX. Не удаляет permanent Warp. |
| scribing_tools | 1 | Работают theory-table ink/durability как часть исследовательского среза; полный refill recipe нужно проверять отдельно. |
| salis_mundus | 1 | Уже начальные block transformations; полный список исходных multi-blocks/permissions/recipe conditions отдельный. |
| thaumometer | 1 | Работают server scan/known status, aura HUD/target packets 0.9.1; common 9 range/block/opposite-hand HUD — документированные адаптации. |
| triple_meat_treat | 1 | Локально Food 6/0.8, always edible, Regeneration 100 с вероятностью .66; источники/recipe separate. |
| brain | 1 | Food 4/0.2; сервер: .1 вероятность normalWarp+1, иначе temporary+1..3. Hunger 30/.8 настроен в конструкторе, но onFoodEaten не вызывает super и не применяет его — проверенный JAR quirk (override offsets 0–58). Unnatural Hunger consumer отдельный. |
| **Всего** | **185** | Каждая форма manifest входит ровно в одну строку. |

## Границы пакета и необходимые проверки

Локальные характеристики, dye, self-repair, оружейные debuffs, часть charging/movement, еда и simple consumption доступны без полной машинной инфраструктуры. Изменять существующий каталог нужно сохранением ID, legacy metadata/NBT и клиентских extensions. Baubles требуют явной выбранной замены: перенос в offhand или считать весь main inventory надетым — изменение баланса, не исходное поведение.

Полнота **всех** предметов требует отдельных больших систем: Focus engine/manipulator/GUI; infusion/enchantment hooks и recipes; полноценный Warp/potions/fluid; Baubles-equivalent equipped slots и sync; projectile physics/таint/Rift; mirrors и remote transport; owned turret/bore AI; golem seals/tasks/ownership/persistence. Нельзя маркировать все 185 строки implemented после корректной замены лишь vanilla stats.

Приёмка EquipmentGameTests должна использовать actual registered items и runtime consumers: worn entity hurt (обычный, magic/fire/explosion/bypass, fall), фактические main-hand attack/armor attributes, harvest/drop/durability, vanilla tool use, correct repair ingredients, inventory/worn ticks на границах cadence с обоими исходными armor hooks, helmet NBT presence/value и set composition, charge conservation/capacity/NBT save/reload, прыжок/forward/sneak/flying/fall/step restore. Arcane tests должны оплачивать реальный craft со скидкой и сохранять atomic rejection/stale-preview guards. Отдельно проверять, что клиент или C2S выбор target/type/amount не может присвоить знания, заряд либо постоянное Warp без серверной реконструкции, владения и оплаты. Таблица соответствия item IDs не заменяет эти сценарии.

## Проверяемые исходники

- `thaumcraft/common/config/ConfigItems.java`, `thaumcraft/api/ThaumcraftMaterials.java`, manifest `assets/thaumcraft/catalog/items.json` — регистрация/числа/формы.
- `thaumcraft/api/items/RechargeHelper.java`, `IRechargable`, `IVisDiscountGear`, `IWarpingGear`; `ItemAmuletVis`, `ItemBootsTraveller`, `ItemVerdantCharm`, `ItemGrappleGun`, `TileRechargePedestal` — charge/cadence.
- `CasterManager`, `ItemCaster`, `ContainerArcaneWorkbench`, `SlotCraftingArcaneWorkbench` — скидка и действительная оплата. JAR javap проверены для rounding и charge drain.
- `ItemFortressArmor`, `ItemVoidRobeArmor`, `ItemVoidArmor`, `ItemRobeArmor`, cultist armor; `PlayerEvents`, `EntityEvents`, `WarpEvents` — armor, masks, runic, gear Warp и потребители.
- `ItemVoid*`, `ItemCrimsonBlade`, `ItemPrimalCrusher`, `ItemElemental*` и infusion enchantment consumers — tool stats/local actions и полный объём специальных режимов.
- `ItemCurio`, `ItemPechWand`, `ItemPrimordialPearl`, `ItemLootBag`, consumables, `ItemFocusPouch`, golem/seal/construct item classes — actions и границы систем.

Числа специального damage пути здесь описывают source/JAR контракт; это не утверждение, что текущий Forge 1.20.1 уже применяет исходный `ISpecialArmor` алгоритм. Правила адаптации к современным DamageType tags/event ordering, charging hardening и системе надетых украшений необходимо зафиксировать после окончательного implementation review.
