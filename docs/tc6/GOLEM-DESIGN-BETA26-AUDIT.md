# Данные конструкции голема — BETA26

## Актуальный статус 0.23

Доступность изготовления расширена в **0.23**: дерево, железо, глина, латунь и таумий; все головы/руки/дополнения и три наземных типа ног имеют поддержанные строгие исследования. Пустотный материал и Flyer остаются закрытыми. Формат props и расчёт цены сохранены. Ограничения деревянных сборок ниже описывают историческую 0.22. Рабочая сущность и AI: [аудит](GOLEM-ENTITY-BETA26-AUDIT.md), [печати](SEAL-SYSTEM-BETA26-AUDIT.md).

## Исходный контракт и история 0.22

Эталон: официальный `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Зеркало исходников: `954022bb777b7546281fb36df8522f0ba6b43f81`.
Этот документ описывает свойства и расчёт компонентов; работа пресса,
транзакции физических inventory и ограничения AI описаны отдельно в
[аудите пресса](GOLEM-PRESS-BETA26-AUDIT.md).

## Формат и границы

`ThaumcraftApiHelper.setByteInLong/getByteInLong` использует стандартный
big-endian `ByteBuffer`. Поэтому индексы идут от **старшего** байта:

| Byte index | Значение | Исходные ID |
|---|---|---|
| 0 | Material | 0–5 |
| 1 | Head | 0–4 |
| 2 | Arms | 0–4 |
| 3 | Legs | 0–3 |
| 4 | Addon | 0–3 |
| 5 | Rank | Естественные ранги 0–10 |
| 6, 7 | Не используются | 0 |

`props=0L` — WOOD/BASIC/BASIC/WALKER/NONE, rank0. Pinned
`GolemProperties` setters/getters подтверждают индексы; `addRankXp` в
`EntityThaumcraftGolem` останавливает естественный рост на10.

Адаптация защиты: `GolemDesign.parse/fromLong/design` возвращает `Optional`
и отклоняет несуществующие ID, значения подписанных отрицательных байтов,
rank>10 и ненулевые резервные байты. `-1L` остаётся машинным sentinel, а не
конструкцией. Допустимый сохранённый rank читается для характеристик;
**новое изготовление допускает только rank0**, поскольку исходный GUI
строит новый `GolemProperties.fromLong(0L)` и никогда не задаёт опыт.
Подмена rank в C2S не должна давать бесплатное развитие.

## Исходные материалы

Все материалы используют `mechanism_simple`. `base` ниже соответствует
исходному конкретному ItemStack, включая metadata; flattening переводит
plate metadata0/1/2/3 в отдельные ID brass/iron/thaumium/void.

| ID/key | Research | base | healthMod / armor / damage | itemColor | Traits |
|---|---|---|---|---|---|
| 0 WOOD | MATSTUDWOOD | plank_greatwood | 6 / 2 / 1 | 5059370 | LIGHT |
| 1 IRON | MATSTUDIRON | plate_iron | 20 / 8 / 3 | 16777215 | HEAVY, FIREPROOF, BLASTPROOF |
| 2 CLAY | MATSTUDCLAY | minecraft:terracotta | 10 / 4 / 2 | 13071447 | FIREPROOF |
| 3 BRASS | MATSTUDBRASS | plate_brass | 16 / 6 / 3 | 15638812 | LIGHT |
| 4 THAUMIUM | MATSTUDTHAUMIUM | plate_thaumium | 24 / 10 / 4 | 5257074 | HEAVY, FIREPROOF, BLASTPROOF |
| 5 VOID | MATSTUDVOID | plate_void | 20 / 6 / 4 | 1445161 | REPAIR |

Непрефиксированные ID предметов в таблицах относятся к `thaumcraft`;
обычные bowls/leather/chest/wool/flint и т. п. — к `minecraft`.

## Все зарегистрированные детали

`base` и `mech` обозначают выбранные base и mechanism материала.
Исходный plate без metadata — **brass**, modules0/1 — **vision/aggression**,
mind0/1 — **clockwork/biothaumic**, wool без metadata — **white_wool**.
Это конкретные исходные формы, а не произвольные современные item tags.

| Категория, ID/key | Research (все) | Дополнительные компоненты | Traits |
|---|---|---|---|
| Head0 BASIC | MINDCLOCKWORK | mind_clockwork1 | — |
| Head1 SMART | MINDBIOTHAUMIC | mind_biothaumic1 | SMART, FRAGILE |
| Head2 SMART_ARMORED | MINDBIOTHAUMIC + GOLEMCOMBATADV | mind_biothaumic1, plate_brass1, base1, white_wool1 | SMART |
| Head3 SCOUT | GOLEMVISION | mind_clockwork1, module_vision1 | SCOUT, FRAGILE |
| Head4 SMART_SCOUT | GOLEMVISION + MINDBIOTHAUMIC | mind_biothaumic1, module_vision1 | SCOUT, SMART, FRAGILE |
| Arms0 BASIC | MINDCLOCKWORK | — | — |
| Arms1 FINE | MATSTUDBRASS | mechanism_simple1, base1 | DEFT, FRAGILE |
| Arms2 CLAWS | GOLEMCOMBATADV | module_aggression1, shears2, base1 | FIGHTER, CLUMSY, BRUTAL |
| Arms3 BREAKERS | GOLEMBREAKER | diamond2, base1, piston2 | BREAKER, CLUMSY, BRUTAL |
| Arms4 DARTS | GOLEMCOMBATADV | module_aggression1, dispenser2, arrow32, mech1 | FIGHTER, CLUMSY, RANGED, FRAGILE |
| Legs0 WALKER | MINDCLOCKWORK | base1, mech1 | — |
| Legs1 ROLLER | MINDCLOCKWORK | bowl2, leather1, mech1 | WHEELED |
| Legs2 CLIMBER | GOLEMCLIMBER | flint4, base1, mech1, mech1 | CLIMBER |
| Legs3 FLYER | GOLEMFLYER | levitator1, plate_brass4, slime_ball1, mech1 | FLYER, FRAGILE |
| Addon0 NONE | MINDCLOCKWORK | — | — |
| Addon1 ARMORED | GOLEMCOMBATADV | base1 ×4 | ARMORED, HEAVY |
| Addon2 FIGHTER | SEALGUARD | module_aggression1, mech1 | FIGHTER |
| Addon3 HAULER | MINDCLOCKWORK | leather1, chest1 | HAULER |

Pinned `GolemProperties.<clinit>`: материалы offsets0–477, головы478–1045,
руки1046–1634, ноги1635–2096, add-ons2097–2401. ID задаются порядком
исходных register calls. Сохранены оригинальные icon/name/description
ключи и цвет материала; здесь не регистрируются новые предметы или модели.

## Объединение traits и компонентов

Порядок traits **material → head → arms → legs → addon**. Противоположности:
DEFT/CLUMSY, HEAVY/LIGHT, FRAGILE/ARMORED. `addTraitSmart`, offsets0–51,
при наличии opposite **удаляет старый trait и не добавляет новый**.
Это взаимное уничтожение, а не правило «последний побеждает». Повторение
одинакового trait не увеличивает количество traits.

Порядок компонентов другой: **base×2, mech → arms → legs → head → addon**.
`generateComponents` offsets24–95 вызывает добавление именно в этом
порядке. `addToList`, offsets25–54, объединяет лишь одинаковые
item/metadata **и одинаковые tags**, складывая counts; новая строка
создаётся через `copy` на58–78. Все современные выдаваемые списки имеют
новые ItemStack каждый раз, а определения не хранят mutable stacks.

Итоговый merged список — единый источник для machine/UI стоимости и
резервирования. Например, brass + fine + climber + smart armored head +
armored addon даёт **10 brass plates, 4 simple mechanisms, 4 flint,
1 biothaumic mind, 1 white wool**; повторённые base/mech и прямая brass
plate не могут резервировать тот же исходный слот отдельными строками.
Iron base и прямая brass plate при этом остаются разными требованиями.
Модель не дебитует inventory: атомарная проверка/оплата принадлежит прессу.

`TileGolemBuilder.startCraft`: Machina cost = **2×число итоговых traits +
сумма counts итоговых компонентов**. Rank в эту цену не входит.

| Деревянная конструкция с Basic head/arms | Merged компоненты | Machina |
|---|---|---|
| WALKER/NONE | planks3, mechanisms2, clockwork mind1 | 8 |
| ROLLER/NONE | planks2, mechanisms2, bowls2, leather1, clockwork mind1 | 12 |
| WALKER/HAULER | planks3, mechanisms2, clockwork mind1, leather1, chest1 | 12 |
| ROLLER/HAULER | planks2, mechanisms2, bowls2, leather2, clockwork mind1, chest1 | 16 |

## Статистика и исследования

Max health = 10+healthMod; при FRAGILE multiply0.75, truncate to int;
затем +rank. Armor при ARMORED = truncate(max(armor×1.5, armor+1)); при
FRAGILE multiply0.75, truncate. Attack=0 без FIGHTER; иначе material.damage,
при BRUTAL max(damage×1.5, damage+1), затем +rank×0.25. Movement multiplier
=1+rank×0.025+LIGHT0.2−HEAVY0.175−FLYER0.33+WHEELED0.25.

**Не копировать ошибочный cast из декомпилированного Java:** pinned
`EntityThaumcraftGolem.updateEntityAttributes` offsets31–38 и
`getTotalArmorValue`58–65 выполняют `i2d; ldc 0.75; dmul; d2i`, не
`int(0.75)` до умножения. Деревянная Fragile конструкция имеет12HP/1armor,
а не0. GUI `BRUTAL` без Fighter может показывать половину сердца урона,
но реальный entity attack остаётся0; метод `attackDamage` хранит реальный
показатель, а не этот исторический GUI артефакт.

Pinned `GuiGolemBuilder.initGui` проверяет `knowsResearchStrict` для
**каждого** массива part.research. Bare keys требуют завершения;
MINDCLOCKWORK@2 или просто наличие clockwork mind недостаточно для выбора
головы, рук, ног и add-on. Открытые исследования материалов — отдельный
gate. `accessible/canManufacture` применяет тот же strict predicate и
дополнительно исключает неподдержанные текущим портом канонические записи.
Сохранённые PORT aliases и вручную проставленные стадии ещё не перенесённых
поздних ветвей не открывают эти профили. Reference `choices(Category)`
содержит все24 исходных part definitions; `choices(Category, knowledge)`
возвращает только разрешённые.

После добавления полного MINDCLOCKWORK в0.22 доступны только деревянные
Basic головы/руки, Walker/Roller и None/Hauler, то есть четыре исходные
конструкции выше. Другие материалы, головы, combat/climber/flyer и combat
add-ons остаются закрытыми до переноса своих канонических исследований.
Формулы их свойств и конкретные компоненты сохранены как проверяемая
справка; это не утверждение об работающем боевом, летающем или seal AI.

## Проверки

`GolemDesignGameTests` добавляет6 серверных сценариев: исходный default и
detached ItemStacks; все2400 допустимых combinations и malformed bytes;
шесть metadata/stat profiles; cancellation/fragile/rank formulas;
повторённые компоненты до резервирования; strict complete predicates,
locked late branches и legacy aliases. Выполнение интегрированной сборки,
server/client QA и фактические результаты фиксируются корневым агентом в
`VALIDATION.md`; наличие этих сценариев не означает их успешный запуск.
