# Ауромантия и доступ к раннему наполнению — BETA26, этап 0.16

Эталон — официальный `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Читаемый источник закреплён на commit `954022bb777b7546281fb36df8522f0ba6b43f81`.
Использованы исходные `assets/thaumcraft/research/{basics,auromancy,infusion}.json`,
`ConfigRecipes`, `ConfigResearch`, `EntityEvents.entityHurt`, `IngredientNBTTC`.
Оригинальный `entityHurt` проверен также через `javap -c -p` официального JAR:
fire damage → player → knowsResearchStrict `BASEAUROMANCY@2` → однократный `f_onfire`.
Данные этих трёх исследований в порте побайтово совпадают с оригиналом.
TC4/TC5, addon-фокусы и шестиугольная исследовательская головоломка не применяются.

## Реализованная цепочка

К существующим 18 каноническим записям добавлены **пять**, итого **23**:
`BASEAUROMANCY`, `RECHARGEPEDESTAL`, `BOOTSTRAVELLER`, `ELEMENTALTOOLS`, `ARMORFORTRESS`.
Ни один исходный parent не удалён и не заменён PORT-псевдонимом.

| Запись | Исходные parents | Оплачиваемая стадия и подтверждение |
| --- | --- | --- |
| BASEAUROMANCY | UNLOCKAUROMANCY | Стадия 1: реально изготовить lesser focus. Стадия 2: реально изготовить focal manipulator и получить `f_onfire`. Стадия 3 — заключение без новой оплаты. |
| RECHARGEPEDESTAL | BASEAUROMANCY | 1 THEORY AUROMANCY = 32 raw; вторая стадия — заключение. |
| BOOTSTRAVELLER | INFUSION, RECHARGEPEDESTAL | 1 THEORY INFUSION = 32 raw, обнаружить `!motus`, факты m_walker/m_runner/m_swimmer/m_jumper. |
| ELEMENTALTOOLS | BOOTSTRAVELLER, METALLURGY@3 | По 1 THEORY INFUSION и BASICS = 32 + 32 raw; изготовить все пять thaumium axe/sword/pick/shovel/hoe. |
| ARMORFORTRESS | METALLURGY@3, BOOTSTRAVELLER | 1 THEORY INFUSION = 32 raw и обнаруженный `!praemunio`. |

Начало исследования, достижение `KEY@N` и полное завершение остаются разными
фактами. Bare parent требует полного завершения; `METALLURGY@3` проверяет
достигнутую стадию 3. Предмет в инвентаре, просмотр рецепта и вход в GUI
не создают craft proof. Крафт подтверждают существующие серверные callbacks:
успешная оплата crucible, взятие результата arcane bench и Forge vanilla result-slot
craft event. Факты и знания сохраняются общим UUID-keyed SavedData v2 и
синхронизируются существующим research protocol 4.

Получение `f_onfire` не требует уже изготовленного собственного огненного
фокуса: на стадии 2 достаточно реального fire damage, например от огня.
Один только burning timer, неогненный damage либо урон до стадии 2 не дают
факт. Повторный fire event не выдаёт повторную награду и не завершает запись
без manipulator craft. Применён modern `DamageTypeTags.IS_FIRE` вместо
1.12 `DamageSource.isFireDamage`; dead/spectator/off-thread исключены.
Доставка action-bar сообщения проверяет наличие connection, поэтому offline
QA player получает сохранённый факт без обращения к отсутствующей сети.

Существующий исходный периодический worker проверяет строгие пороги каждые
200 ticks: walk > 160000 cm, sprint > 80000 cm, swim > 8000 cm, jump > 500.
Достижение ровно порога не удовлетворяет требованию. Обнаружение Motus и
Praemunio использует настоящие scan aspect definitions; наличие лодки или
щита без сканирования не создаёт `!motus`/`!praemunio`.

## Три исходных рецепта для basic auromancy

| Рецепт | Gate | Исходная оплата |
| --- | --- | --- |
| `thaumcraft:focus_1` | UNLOCKAUROMANCY | Ordo crystal с одним аспектом amount 1; Vitreus 20 + Praecantatio 10 + Auram 5 в crucible. Обычный crucible commit расходует один catalyst и до 50 mB воды. |
| `thaumcraft:tablestone` | Нет | Обычный shaped: `SSS` / `W W`; smooth stone slabs и stone. |
| `thaumcraft:wand_workbench` | BASEAUROMANCY@2 | Shaped `ISI` / `BRB` / `GTG`; iron plates, arcane slab/stone, vis resonator, gold ingots, stone table; 100 vis, Terra 1 + Aqua 1 crystal. |

Перенос `Blocks.STONE_SLAB` в `minecraft:smooth_stone_slab` является явной
адаптацией flattened 1.20 ID. Stone допускает Forge tag и vanilla stone,
чтобы обычная базовая среда не зависела от сторонних oreDictionary entries.
Iron/gold используют существующие Forge plate/ingot tags.

Манипулятор открывается **на стадии 2**, иначе его собственный required_craft
создал бы тупик. Arcane/crucible bare recipe gates сохраняют исходное
`isResearchKnown`, а изготовление graph внутри manipulator применяет
`knowsResearchStrict`: ROOT/TOUCH/FIRE требуют полностью завершённый
BASEAUROMANCY. Подробнее — [аудит графа](FOCUS-GRAPH-BETA26-AUDIT.md).
Существующие рецепты resonator/gauntlet/recharge pedestal остаются исходными.

Crucible recipe API дополнен optional `catalyst_nbt`. Это именно relaxed
top-level `IngredientNBTTC` сравнение: каждый требуемый root tag должен
целиком совпасть; лишние посторонние root tags разрешены. `Aspects` сравнивается
как полный NBT list: Fire, смешанный Ordo+Fire и Ordo amount 2 не заменяют
один исходный Ordo crystal. Parsed requirement и accessor дают detached copies;
сохраняется прежний five-argument Entry constructor для старых callers.

## Какие из 56 рецептов наполнения стали доступны

**Девять из 56** зарегистрированных рецептов имеют достижимые канонические
research paths через поддерживаемую цепочку; **47** сохраняют поздние gates:

- `infusion/bootstraveller`;
- `infusion/elementalaxe`;
- `infusion/elementalsword`;
- `infusion/elementalpick`;
- `infusion/elementalshovel`;
- `infusion/elementalhoe`;
- `infusion/thaumiumfortresshelm`;
- `infusion/thaumiumfortresschest`;
- `infusion/thaumiumfortresslegs`.

Это число путей, достижимых при сборе исходных знаний/фактов/материалов,
а не автоматическая разблокировка всех девяти новому персонажу. Infusion
использует исходный **bare known** gate: после начала соответствующей записи
сервер принимает рецепт; порт не изобретает completed gate для этих рецептов.
Число проверено на всех 56 данных и GameTest по настоящему RecipeManager,
а не подсчитано по строкам книги.

Эти результаты уже имеют действующие consumers из этапа 0.10: заряд и
передвижение traveller boots/recharge pedestal; свойства Fortress без масок;
пять Elemental tools, их исходные infench и добыча/строительство/рост/
притяжение/ARCING/SOUNDING. Условия `ELEMENTALTOOLS` проверены по исходному
JSON, а наличие consumers — по [аудиту инструментов](EQUIPMENT-TOOLS-BETA26-AUDIT.md)
и существующим equipment GameTests. Их ранее описанные modern adaptations
и ограничения остаются: AOE чужих BlockEntity, отдельные custom FX,
глобальные rare native-cluster drops, ESSENCE/LAMPLIGHT не объявлены готовыми.

`FORTRESSMASK`, `INFUSIONENCHANTMENT`, `RUNICSHIELDING`, `FOCUSADVANCED`,
`FOCUSGREATER`, поздние focus effects/media и прочие unsupported descendants
не становятся поддерживаемыми от завершения basic auromancy. Готовая модель
предмета, справочная node definition и зарегистрированный рецепт не являются
подтверждением оригинальных механик. Полная ауромантия не заявляется законченной.

## Проверки этого изменения

`AuromancyProgressionGameTests` содержит **10** новых server checks:
exact Ordo NBT и 35 aspects; настоящий crucible debit и отличие владения от
craft; стадийный fire event/сохранение; цепь BASEAUROMANCY → RECHARGEPEDESTAL →
BOOTSTRAVELLER → ELEMENTALTOOLS/ARMORFORTRESS через настоящий crucible,
arcane commit, vanilla result slots, статистику/scan data и оплату без
прямых grants этих пяти новых стадий;
атомарная 32-raw recharge оплата; movement/scan boots requirements;
METALLURGY@3 и Praemunio Fortress; точные девять reachable recipes;
пять реальных vanilla result-slot thaumium crafts и 32+32-raw Elemental оплата;
строгий BASEAUROMANCY gate исходного ROOT/TOUCH/FIRE.

Boundary tests намеренно создают started/stage fixtures для проверки отказов.
Upstream UNLOCKAUROMANCY/INFUSION/METALLURGY в изолированных unit fixtures
настраиваются отдельно; это не утверждение, что каждый тест заново проходит
весь survival с добычи первого кристалла. Интеграционная basic цепь **не**
выставляет stages пяти новых записей или их craft proofs вручную:
доказательства создают рабочие устройства, а этапы — `ResearchNetwork.processAdvance`.
Elemental test использует настоящий `ResultSlot.remove/onTake`, расходует
registered shaped inputs и вызывает штатный Forge craft hook; preview не
создаёт proof. Failed requirements и stale requests не списывают знания/XP
частично и не платят следующий этап повторно.

Агент этой progression части не запускал Gradle/client. Число написанных
тестов не означает успешность общего прогона; root фиксирует окончательные
server/client результаты, JAR и ограничения в `VALIDATION.md`.
