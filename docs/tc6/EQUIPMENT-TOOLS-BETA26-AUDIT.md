# Инструменты и оружие: TC6 BETA26

Эталон — закреплённый официальный `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Аудит 2 октября 2026. Это отдельный срез, не утверждение о готовности всех предметов.

## Подтверждённые материалы

`ThaumcraftMaterials.<clinit>`, `ItemCrimsonBlade.<clinit>` и
`ItemPrimalCrusher.<clinit>` сверены через `javap -c -p` оригинального JAR.

| Материал | Harvest level TC6 | Прочность | Скорость добычи | Bonus damage материала | Enchantability | Ремонт |
|---|---:|---:|---:|---:|---:|---|
| Thaumium | 3 | 500 | 7 | 2.5 | 22 | Таумиевый слиток |
| Void | 4 | 150 | 8 | 3 | 10 | Пустотный слиток |
| Elemental | 3 | 1500 | 9 | 3 | 18 | Таумиевый слиток |
| Crimson void | 4 | 200 | 8 | 3.5 | 20 | Пустотный слиток |
| Primal void | 5 | 500 | 8 | 4 | 20 | Пустотный слиток |

Elemental hoe переопределяет enchantability на **5**. Все три вида топоров
вызывают исходный custom `ItemAxe(material,8,-3)`: modifier damage=8,
то есть суммарный урон игрока 9; в modern AxeItem надо вычесть material bonus из
constructor damage. Крушитель получает modifier `3.5+4=7.5`, скорость атаки −2.8.
Нельзя заменять все эти инструменты одним vanilla tier.

Void инструменты, Crimson Blade и Crusher чинятся на 1 damage раз в 20 тиков
у живого носителя, в том числе вне выбранного слота. Их warp соответственно 1/2/2.
Void pick/shovel/axe/hoe накладывают Weakness I на 80 тиков при left-click entity;
Void sword — на 60 при успешном ударе; Crimson — Weakness 60 + Hunger 120.
PvP-disabled player targets исключаются.

## Исходный NBT зачарований

Это `infench: [{id: short, lvl: short}]`, а не vanilla `Enchantments`.
Ordinal: COLLECTOR=0, DESTRUCTIVE=1, BURROWING=2, SOUNDING=3, REFINING=4, ARCING=5.
Исходные creative stacks и infusion outputs:

- axe: BURROWING 1 + COLLECTOR 1;
- pick: REFINING 1 + SOUNDING 2;
- sword: ARCING 2;
- shovel: DESTRUCTIVE 1;
- crusher: DESTRUCTIVE 1 + REFINING 1.

Сырой `/give` не должен автоматически создавать эти записи. Интеграция
использует `ToolItems.initializeStack(ItemStack)` только для default/creative/crafted
стека и сохраняет уже существующий `infench`. Общему исходному enum нужны
`addInfusionEnchantment`, `getInfusionEnchantmentLevel` и перечисленные constants.

## Интеграция с общим модулем

Контракт: `ToolItems.create(CatalogModule.Spec)` возвращает Item или null,
`ToolItems.thaumium(String)` создаёт пять обычных инструментов. `ToolItems.registerTiers()`
регистрирует собственные tiers; прежний EquipmentModule tier нужно связать с
`ToolMaterials.THAUMIUM`, а не регистрировать второй объект под тем же ID.
Новые classes/events/network находятся исключительно в `equipment/tools`.

`EquipmentModule` уже связан с общим `ToolMaterials.THAUMIUM`, а creative sample
catalog вызывает `initializeStack`. Для F-клавиши нужны переводы
`key.thaumcraft.tool_mode` и `key.categories.thaumcraft`. Звуки `wind` и `wandfail`
берутся из исходных assets; запасные vanilla sounds нужны только при отсутствующем
registry entry.

## Реализованное поведение

- Пять инструментов Thaumium, пять Void, Crimson Blade, пять Elemental и
  Primal Crusher используют отдельные исходные materials, реальные mining/tool
  actions, main-hand damage/speed, ремонт и self-repair/warp, где это было в TC6.
  У Crusher нет добавленного Weakness и нет выдуманной Epic rarity.
- Elemental pick поджигает цель на 2 секунды. Shift-use с SOUNDING тратит 5
  durability и показывает исходные группированные ore ripples: диапазон `4+rank*4`,
  26 соседей с одинаковым state, средний центр, задержка `distance*3`, 44 ticks,
  frames 240–254, исходные цвета и alpha keys, additive/alpha без depth test.
- Elemental axe при удержании use притягивает ItemEntity из сферы 10 блоков;
  исходная формула ускорения и ограничение ±0.25 сохранены.
- BURROWING с Shift bypass удаляет один дальний блок исходного greedy search
  (logs reach 2, ores 1; пределы X/Z 24, Y 48), а не всё дерево за один клик.
- DESTRUCTIVE с Shift bypass добывает до восьми эффективных соседей в плоскости
  последней нажатой грани. REFINING сохраняет исходный шанс `(1+rank)*0.125`.
  COLLECTOR переводит обычные добытые/убитые ItemEntity в движение к владельцу,
  оставляя обычный pickup и переполнение инвентаря.
- Важное уточнение из `EntityFollowingItem`: параметр **10** обозначает тип
  синего эффекта, а не задержку в 10 тиков; исходный homing age начинается с 20.
- Elemental shovel строит девять блоков копируемого state из инвентаря,
  умеет grass→dirt fallback и три исходные ориентации NBT `or`; F циклически
  меняет режим main-hand лопаты на сервере. Shift включает preview.
- Elemental hoe обрабатывает 3×3 tilling, Shift оставляет обычный одиночный HoeItem;
  при отсутствии вспаханного блока применяет обычное Forge bonemeal за 3 durability.
- Elemental sword поднимает носителя, смягчает падение, отталкивает соседних mobs,
  тратит 1 durability каждые 20 ticks и играет исходный wind. Reset floating counter
  использует проверенный SRG `f_9737_` (`aboveGroundTickCount`), с guard для offline
  FakePlayer. ARCING поражает до rank дополнительных не союзных Mob на половину
  attack damage, применяет ответные enchant effects и исходный knockback.

## Явные адаптации 1.20.1 и ограничения

Modern Forge удалил `HarvestDropsEvent`. Ограниченный server-thread harvest context
вызывает настоящий `Block.playerDestroy` и преобразует только новые ItemEntity,
сохраняя loot tables, Fortune/Silk Touch и Forge global loot modifiers. BlockEntity
блоки остаются в обычном пути vanilla без дополнительных действий; это не полная
поддержка AOE всех сторонних контейнеров. Каждый дополнительный/дальний блок
отдельно проходит protection/BreakEvent; центральный уже проверенный BreakEvent
не дублируется. Отменённое разрушение/placement не потребляет предметы/прочность.

Original tiers переведены в Forge sorted tiers с отдельными пустыми requirement
tags `needs_thaumium_tool`, `needs_elemental_tool`, `needs_void_tool`,
`needs_crimson_tool`, `needs_primal_tool`. Нельзя выдавать всем tiers один
`minecraft:needs_diamond_tool`: Forge считает его требованием самого высокого
tier и ошибочно запрещает добывать obsidian нижним инструментам. Это подтверждено
`TierSortingRegistry.isCorrectTierForDrops` mapped JAR, а не предположение.

Старые Material/oreDictionary заменены modern mineable/tier tags. Скорость и
способность Crusher добывать stone/metal/shovel/taint блоки работают через эти tags;
блоки других модов без корректных tags не считаются автоматически известными.
REFINING принимает modern raw iron/gold/copper и deepslate variants как явную
адаптацию к новой добыче, а ore tags tin/silver/lead/copper — к oreDictionary.

Мутации мира/инвентаря и оплаты выполняются только на сервере. Для строительной
лопаты после выполненного placement возвращается CONSUME вместо исходного FAIL,
чтобы modern обработчик не запускал вторую руку или GUI. Каждая цель дополнительно
проверяет world border, loaded chunk, permission, survival/collision и Forge place
event. Нулевая дистанция магнита защищена от NaN; greedy search имеет конечный
предел глубины. Collector прекращает tracking при logout/смене измерения/после
1200 ticks и возвращает прежние physical flags предмета.

Preview использует modern wireframe; wind/slash/bamf/collector визуальный feedback
пока представлен обычными particles. Это не полная копия исходных smoke spiral,
PacketFXSlash, bamf и special following-entity renderer. Ore ripples используют
точный исходный atlas и параметры. Общие enchant ESSENCE/LAMPLIGHT, глобальные
редкие native-cluster drops, рецепты исследования/инфузии и fake thaumcraft bore
не относятся к этому инструментальному срезу и здесь не заявлены реализованными.

## Проверка

Характеристики и спорные формулы сверены с original JAR/source; mapped Forge JAR
использован для API, floating counter и sorted-tier ошибки. Root запускает общий
build/GameTest suite; первый запуск обнаружил tier-tag ошибку, она исправлена.
В этом audit не заявлена финальная успешность общей сборки или gameplay QA:
их результат хранится отдельно в root validation logs.
