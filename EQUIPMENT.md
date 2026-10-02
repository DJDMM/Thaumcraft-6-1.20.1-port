# Оборудование и расходуемые предметы 0.10

Эталон — официальный Thaumcraft **6.1.BETA26**. Числа проверены по исходным материалам и JAR. Внешний вид уже существовал в 0.8; этот этап добавляет поведение. Исходные рецепты, инфузия и весь research gate оборудования ещё не завершены.

## Броня

Порядок значений: шлем / нагрудник / поножи / ботинки; «—» означает отсутствие предмета. Toughness указан на одну часть.

| Комплект | Защита | Прочность | Toughness | Ремонт |
| --- | --- | --- | ---: | --- |
| Thaumium | 2 / 6 / 5 / 2 | 275 / 400 / 375 / 325 | 1 | Thaumium |
| Cloth | — / 3 / 2 / 1 | — / 400 / 375 / 325 | 1 | Enchanted Fabric |
| Void | 3 / 8 / 6 / 3 | 110 / 160 / 150 / 130 | 1 | Void Metal |
| Void Robe | 4 / 9 / 7 / — | 198 / 288 / 270 / — | 2 | Void Metal |
| Fortress | 3 / 7 / 6 / — | 440 / 640 / 600 / — | 3 | Thaumium |
| Crimson Plate | 2 / 6 / 5 / — | 198 / 288 / 270 / — | 0 | Iron |
| Crimson Robe | 2 / 5 / 4 / — | 187 / 272 / 255 / — | 0 | Iron |
| Praetor | 3 / 7 / 6 / — | 330 / 480 / 450 / — | 1 | Iron |
| Crimson Boots | — / — / — / 2 | — / — / — / 195 | 0 | Iron |
| Goggles | 1 / — / — / — | 350 / — / — / — | 1 | Brass |
| Traveller Boots | — / — / — / 1 | — / — / — / 350 | 1 | Leather |

Fortress учитывает комплект, `goggles` и `mask`. Три части без маски дают суммарно 19 Armor / 12 Toughness, маска добавляет 3 Armor. Специальное поглощение от магии/огня/взрыва и vanilla-защита применяются последовательно, как ISpecialArmor BETA26. Mask 1 может наложить Wither на нападающего, Mask 2 — лечить владельца. Mask 0 имеет hook уменьшения тяжести warp; весь WarpEvents ещё нужен. Очки показывают состав рабочих контейнеров эссенции.

Void Armor/Robe чинятся каждые 20 тиков: до 1 единицы в инвентаре, до 2 при надевании игроком — результат двух исходных callbacks. Саморемонт не тратит vis. Броня и инструменты сообщают исходный gear Warp; это не автоматическое увеличение постоянного искажения.

Скидка vis: Goggles 5%, Cloth chest/legs 3% и boots 2%, Void Robe по 5%, Crimson Robe/Boots по 1%. Магический верстак проверяет и оплачивает `int(vis * (1 - discount))`, GUI показывает ту же цену.

## Инструменты и оружие

| Материал | Уровень | Прочность | Скорость добычи | Материальный бонус урона | Enchantability |
| --- | ---: | ---: | ---: | ---: | ---: |
| Thaumium | 3 | 500 | 7 | 2.5 | 22 |
| Void | 4 | 150 | 8 | 3 | 10 |
| Elemental | 3 | 1500 | 9 | 3 | 18 |
| Crimson Blade | 4 | 200 | 8 | 3.5 | 20 |
| Primal Crusher | 5 | 500 | 8 | 4 | 20 |

Thaumium/Elemental ремонтируются таумием; Void/Crimson/Primal — void metal. Материальный бонус не равен полному атрибуту атаки игрока: мечи Thaumium 6.5, Void/Elemental 7, Crimson 7.5; топоры 9; Crusher 8.5. Elemental Hoe переопределяет enchantability на 5.

- Void инструменты, Crimson Blade и Crusher чинятся раз в 20 тиков. Void даёт Weakness, Crimson — Weakness/Hunger, Elemental Pick — огонь; сохраняются исходные проверки PvP.
- Elemental Pick и Crusher поддерживают добычу площади; Shift позволяет точечную работу. BURROWING/REFINING/COLLECTOR проходят через реальные серверные проверки и обычные loot drops.
- Elemental Shovel строит плоскость по образцу выбранного блока, расходуя соответствующие предметы инвентаря; **F** меняет режим, клиент показывает контур. Сервер повторно проверяет действие.
- Elemental Hoe обрабатывает землю/рост, Elemental Sword поднимает существ и применяет ARCING, Elemental Axe притягивает предметы. SOUNDING показывает кольца найденной руды.
- Исходные infusion-enchantments хранятся в `infench` (short `id`/`lvl`). Creative/crafted stacks получают начальные зачарования исходного предмета. Сырой `/give` их не добавляет — как оригинальная регистрация.

Не завершены ESSENCE/LAMPLIGHT, вся инфузия зачарований и глобальные native-cluster drops. AOE не ломает BlockEntity. Старые Material/oreDictionary адаптированы к современным тегам, часть особых FX заменена частицами. [Точный аудит](docs/tc6/EQUIPMENT-TOOLS-BETA26-AUDIT.md) описывает границы.

## Заряд, пьедестал и руническая защита

Заряд — отдельный Int `tc.charge`, не durability. Recharge Pedestal принимает один поддерживаемый предмет и на сервере каждые 10 тиков переносит до 5 vis из ауры. ПКМ вставляет/выдаёт stack; поддерживаются сохранение, сетевой update и item capability.

Traveller Boots вмещают 240 зарядов. Отдельный счётчик `energy` расходуется раз в 20 тиков, при нуле оплачивается 1 заряд и ставится 60. Сохраняются ускорение/прыжок, step height и снижение fall damage. Само надевание не заряжает ботинки.

Runic Shielding читает Byte `TC.RUNIC` на надетой броне. Восстанавливает 1 absorption за 1 vis каждые 2 секунды, после полного истощения ждёт 4 секунды; ритм wall-clock соответствует BETA26. Инфузия нанесения рунической защиты и accessory slots ещё нужны.

## Расходуемые предметы и Warp

- Шесть мясных кусочков: Food 1 / saturation modifier 0.3, употребление 10 тиков.
- Triple Meat Treat: 6 / 0.8, можно есть сытым; шанс Regeneration.
- Zombie Brain: 4 / 0.2; 10% normal Warp +1, иначе temporary +1..3. Фактический BETA26 не накладывает Hunger: callback не вызывает super.
- Curios начисляют исходные диапазоны наблюдений/теорий. Eldritch и Crimson Rites дают normal/temporary Warp. Rites требует actual Warp >20, завершает свою запись и может добавить permanent Warp.
- Loot bags common/uncommon/rare выпускают 8..12 настоящих предметов из исходных взвешенных таблиц. В survival расходуется один мешок; в creative vanilla gameMode восстанавливает число предметов после callback. Список potion types адаптирован к реестру 1.20.1.
- Primordial Pearl имеет 8 damage-стадий, не ремонтируется и не зачаровывается; crafting remainder уменьшает её размер до полного расхода.
- Sanity Checker показывает original HUD при удержании в любой руке. Permanent, normal и temporary Warp сохраняются отдельно и синхронизируются server → client. Полный набор warp events ещё не реализован.

Соли для ванны нужно бросить в источник воды: выброшенный stack истекает через 200 тиков и целиком превращает источник в очищающую жидкость. Источник жидкости при соприкосновении с игроком выдаёт Warp Ward и исчезает; текущая защита не продлевается повторно. Длительность зависит от permanent Warp: `min(32000, 200000 / max(1, floor(sqrt(warp))))` тиков. Современное отдельное ведро заменяет Forge universal bucket 1.12.

Мыло используется удержанием ПКМ, завершение после более чем 95 тиков; ранний отпуск не расходует предмет. Удаляет temporary Warp, сохраняет permanent и изменяет normal на 1, +1 при Ward, +1 стоя в очищающей жидкости. Сохранена подтверждённая особенность BETA26: попытка удалить больше имеющегося normal Warp передаёт **положительное текущее значение**, поэтому малое значение может удвоиться. Это не утверждение, что мыло всегда уменьшает normal Warp. Исходная графика Ward перенесена из атласа в отдельный sprite без изменения пикселей. [Аудит очищения](docs/tc6/CLEANSING-BETA26-AUDIT.md) фиксирует механику и адаптации; защита от всех событий требует будущего WarpEvents.

## Команды для проверки (читы включены)

```mcfunction
/give @s thaumcraft:fortress_helm{goggles:1b,mask:1}
/give @s thaumcraft:fortress_chest
/give @s thaumcraft:fortress_legs
/give @s thaumcraft:traveller_boots
/give @s thaumcraft:recharge_pedestal
/give @s thaumcraft:elemental_pick{infench:[{id:3s,lvl:2s},{id:4s,lvl:1s}]}
/give @s thaumcraft:elemental_shovel{infench:[{id:1s,lvl:1s}]}
/give @s thaumcraft:void_chest{"TC.RUNIC":3b}
/give @s thaumcraft:sanity_checker
```

Проверки и ограничения: [VALIDATION.md](VALIDATION.md). Исходные числа брони: [armor audit](docs/tc6/EQUIPMENT-ARMOR-BETA26-AUDIT.md); зависимости всех 185 форм: [ведомость](docs/tc6/ITEM-MECHANICS-BETA26-INVENTORY.md).
