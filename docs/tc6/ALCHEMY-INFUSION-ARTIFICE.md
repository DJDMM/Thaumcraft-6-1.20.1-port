# Thaumcraft 6: алхимия, инфузия и устройства

Рабочая спецификация переноса на Forge 1.20.1, проверено 30 сентября 2026. Цель — **Thaumcraft 6.1.BETA26 для Minecraft 1.12.2**, включая hotfix1. Материалы TC4/TC5 и механики аддонов здесь не используются.

Прочитаны все **53 Markdown-страницы** разделов `06.alchemy`, `07.arcane-infusion`, `09.artifice` [TC6 Comprehensive Guide, commit cd143734](https://github.com/xiaoschannel/Minecraft-Guides-Thaumcraft6/tree/cd14373456bfa3090663eb9dbf804f369313a7d7/pages). Фактический список — `work/tc6-read-arcane.json`. Гайд даёт маршрут игрока, но содержит устаревшие числа и советы. Приоритет проверки: официальный BETA26 JAR → тексты и research JSON внутри него → декомпилированные классы → гайд. Это конспект и требования, не заявление о завершённом переносе.

Использован [официальный файл 2629023](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2629023), SHA-256 `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`. Ссылки на читаемый код ниже закреплены на commit `954022bb777b7546281fb36df8522f0ba6b43f81` репозитория TheDarkTower314. Это восстановленный код, а не безошибочный авторский исходник: два дефекта декомпиляции уже подтверждены байткодом.

## Основная цепочка зависимостей

- `BASEALCHEMY` → Alumentum и Metallurgy → Essentia Smelting → Warded Jars → Tubes. Thaumium Smeltery требует Tubes и завершения Metallurgy. Void Smeltery дополнительно зависит от Eldritch.
- Tubes → Centrifuge; Centrifuge + Thaumium Smeltery → Thaumatorium. Advanced Essentia Transport требует **и Thaumatorium, и Infusion**. Мехи приходят из Artifice и нужны для ветки Improved Smelting.
- `BASEINFUSION` → Infusion → Cloudstepper Ring / Infusion Boosting / Boots. Elemental Tools требуют Boots и Metallurgy; затем идут Infusion Enchantment → Runic Shielding. Stabilization скрыта за событием `!INSTABILITY` и также требует Metallurgy.
- Artifice пересекается с другими ветками: Mirrors и специальные лампы требуют Infusion; базовая турель требует Clockwork Mind из големостроения; продвинутая — Biothaumic Mind; Arcane Bore требует Infusion, базовую турель и Break focus.

Это связи из [alchemy.json][alchemy-json], [infusion.json][infusion-json], [artifice.json][artifice-json]. `@N`, скрытые открытия и промежуточные стадии нельзя заменять одним числом сканов. Расходуемые предметы, крафты, наблюдения и теории следует переносить из конкретной стадии JSON, а не из примерного порядка главы гайда.

## Алхимия: все темы раздела

| Тема | Требуемое поведение TC6 и связи |
|---|---|
| Начальная алхимия, Hedge Alchemy | Нагретый тигель с водой растворяет аспекты; подходящий катализатор завершает известный игроку рецепт. Нужны кварцевые осколки для извлечения кристаллов, цветной Nitor, свечи из tallow, рецепты размножения/превращения обычных материалов: leather, gunpowder, slime, ink, glowstone, clay, string, cobweb, lava. Примеры составов из модпаков не являются эталоном аспектов. |
| Alumentum | Топливо и бросаемый взрывчатый предмет. В essentia smelter также ускоряет стадию дистилляции. Долгое горение и скорость извлечения — разные эффекты. |
| Everfull Urn | Пополняет принимающие воду ёмкости в пределах двух блоков, наполняет переносные ёмкости; за 1000 mB воды тратит 1 vis ауры. Трубы воды подключаются сверху. Не бесплатный бесконечный источник независимо от ауры. [Книга][lang-urn]. |
| Essentia Smelting | Топливо → смесь аспектов предмета → отдельные аспекты в alembic. Суммарная внутренняя вместимость smelter 256; alembic хранит 128 одного аспекта. Вертикальная колонна до пяти alembic увеличивает хранение, не скорость основного процессора. [Smelter][smelter], [Alembic][alembic]. |
| Warded Jars и labels | Банка хранит 250 одного аспекта и сохраняет содержимое при переносе. Label фиксирует допустимый аспект даже после опустошения; phial переносит 10. Shift+ПКМ пустой рукой очищает содержимое, а по стороне этикетки снимает именно этикетку. Brass Lid Brace запрещает воздушный доступ; трубы остаются доступны. [Книга][lang-jars]. |
| Essentia Tubes | Сеть направляется величиной и типом suction. Нужны обычные, filtered, restricted, directional, valve, buffer и диагностический Resonator; нельзя подменять транспорт обычной жидкостной сетью. Подробности ниже. |
| Alchemical Metallurgy | Brass → Thaumium; металл, пластины, блоки, броня, инструменты и research gates. Металл из тигля служит основой устройств следующих ступеней. |
| Thaumium / Void Smeltery | Эффективность сохранения обычной единицы аспекта 80% / 90% / 95% для basic / thaumium / void. Более дорогая печь не автоматически самая быстрая; см. проверку JAR ниже. |
| Improved Distillation | Slurry Pump добавляет боковой процессор и колонну alembic. Vent снижает выброс flux, каждая доступная правильно ориентированная боковая вентиляция получает шанс около 1/3 погасить очередную потерянную единицу. Несколько дают убывающую отдачу; фронт, верх и низ для них не подходят. Обе части исследования существуют в BETA26. |
| Centrifuge | Принимает составной аспект снизу, отдаёт сверху **один** из двух компонентов на входную единицу, случайно выбирая компонент. Не удваивает эссенцию. Выходной buffer помогает избежать конфликта suction. [Centrifuge][centrifuge]. |
| Thaumatorium | Мультиблок: crucible + два Alchemical Construct + Salis Mundus. Нужен нагрев, вода не нужна. Игрок выбирает известный рецепт для катализатора; аппарат поочерёдно запрашивает недостающие аспекты, затем выдаёт результат вперёд/в инвентарь. Redstone останавливает работу. Mnemonic Matrix добавляет по две запоминаемые формулы к базовой одной. [Книга][lang-thaumatorium], [реализация][thaumatorium]. |
| Advanced Essentia Transport | Filling Transfuser переносит из подключённого источника в банки; Emptying — из банок в потребителя с запросом конкретного аспекта. Поиск направленный, параметр дальности 16. Не создаёт эссенцию и не отменяет требования устройства. [Книга][lang-transfuser], [поиск][essentia-search]. |
| Metal Purification | Native clusters железа, золота, киновари дают удвоенный обычный выход при плавке; полный список и совместимость с рудами должны следовать рецептам TC6. |
| Liquid Death | Опасная жидкость, убивает живых существ и позволяет получать их аспекты в кристаллах. Исследование связано с warp. Перенос требует damage source, entity death hooks и fluid поведения. |
| Bottled Taint | Источник заражения; зависит от исследования Hedge Alchemy и события избытка flux. Нельзя реализовывать изолированно без системы taint/seed/rift. Обещание гайда о бесконечном самостоятельном распространении не считать установленным правилом BETA26 без проверки взаимодействий taint. |
| Potion Sprayer | Зелье служит шаблоном; эссенция создаёт внутренние дозы, максимум восемь. Redstone выпускает одну дозу в область 3×3×3 перед устройством. Не универсальное изготовление бутылок зелий. [Книга][lang-sprayer]. |
| Bath Salts / Sanity Soap / Arcane Spa | Соль превращает воду в очищающую жидкость и даёт временную защиту от warp. Soap удаляет временный warp и с некоторой вероятностью уменьшает непостоянный; permanent warp не стирает. Защита/очищающая жидкость повышают шанс. Spa подаёт жидкость в область 5×5 сверху, принимает воду+соль либо другие жидкости, отключается redstone. Это разные эффекты, а не три одинаковых способа очистки warp. [Книга][lang-cleaning]. |

### Транспорт эссенции: численная спецификация

- Обычная труба хранит одну единицу одного типа. Притяжение передаётся как `соседнее suction − 1`; restricted делит его на два. Приём требует совместимого типа, своего suction строго больше соседнего и не ниже минимального suction источника. Перерасчёт обычной трубы идёт каждые 2 тика, попытка переноса — каждые 5. Несовместимые типы suction близкой силы вызывают venting на 40 тиков; это не доказательство утраты всей эссенции. [TileTube, 110–233][tube].
- Обычная банка: suction 32, с label 64, полная банка 0. Подключение трубы сверху. Void Jar: 32, либо 48 с label пока не заполнена; переполнение уничтожает избыток, **каждый вызов переполнения имеет шанс 1/250 выбросить 1 flux**. [Jar][jar], [Void Jar][void-jar].
- Buffer хранит суммарно **10**, допускает несколько типов. Базовое suction 1, с мехами `32 × число мехов`; синяя настройка стороны ограничивает до 1, красная до 0. Эта настройка управляет притяжением, а не является обычным запретом вывода. Более сильный потребитель получает приоритет. [Buffer][buffer]; вместимость подтверждена JAR, хотя текст книги пишет 8.
- Label/filter и valve нужны для маршрутизации. Направленная труба ограничивает направление распространения suction; это не механический насос. UI и части модели должны позволять переключать отдельные соединения кастером.
- Воздушный перенос работает через `IAspectSource` и учитывает запрет доступа brace. Infusion вызывает поиск с range 12, transfuser — 16. В коде это обход координатного объёма с направлением, а не сферический радиус; границы и порядок источников нужно тестировать отдельно. [Infusion consumption][infusion-consume], [EssentiaHandler][essentia-search].

### Проверено непосредственно в BETA26 JAR

Вывод `javap -c -p` сохранён локально в `work/tc6-bytecode/`, полные дампы в комплект документов не включаются. Версия, источник и хэш JAR также записаны в [reference-manifest.json](reference-manifest.json). Это проверка конкретных методов, не всего мода.

| Вопрос | Результат |
|---|---|
| Basic/thaumium/void дистилляция | `TileSmelter.getSpeed()` возвращает **15 / 10 / 15 тиков**. Утверждения книги о 2× thaumium и более быстром void не совпадают с фактическими интервалами BETA26. Сравниваем именно выдачу эссенции, не полный цикл обработки предмета. |
| Alumentum в smelter | Байткод: `i2d`, `ldc 0.8`, `dmul`, `d2i`. Интервалы становятся **12 / 8 / 12 тиков**. GitHub-код `speed *= (int)0.8` ошибочен: переносить его нельзя. |
| Flux при плавке | Проверка выполняется для каждой единицы аспекта: потеря при random > efficiency; для Vitium порог `efficiency × 0.66`. Вентили пытаются безопасно погасить каждую потерю; оставшиеся единицы загрязняют ауру. Нет отдельного универсального «25% на предмет» из гайда. |
| Цикл vent в smelter | В JAR после обработки каждой потери есть `iinc` внешнего счётчика; цикл конечный. Бесконечный вложенный цикл в декомпиляции — артефакт. |
| Buffer | Constructor, `addToContainer` и update подтверждают предел **10**, не 8 из книги. |
| Vis Generator | 1 vis превращается в 1000 FE; выдача максимум **20 FE/t**, не 10 из гайда. Проверены constructor, update и recharge. |
| Runic Shielding | Recipe.matches не ограничивает ранг тремя. На входном уровне `r` цена: `20 + 20 × 2^r` Protect, половина этого Crystal и Energy, `r+1` amber и Salis Mundus; нестабильность `5 + floor(r/2)`. Результат увеличивает `TC.RUNIC` на 1. Это не означает безопасную математическую бесконечность: исходное хранение byte и переполнение при больших значениях требуют отдельного решения, нельзя воспроизводить exploit. |

Минимальные координаты повторной проверки байткода указанного JAR:

- `thaumcraft.common.tiles.essentia.TileSmelter.func_73660_a`: offsets 86–93 — умножение интервала; `getSpeed`: 0–19 — выбор 15/10/15; `smeltItem`: 298–304 — продвижение счётчиков без бесконечного цикла.
- `thaumcraft.common.tiles.essentia.TileTubeBuffer.<init>`: offset 16 — константа 10; `addToContainer`: offset 14 — сравнение вместимости с 10.
- `thaumcraft.common.tiles.devices.TileVisGenerator.func_73660_a`: offsets 127–143 — ограничение выдачи 20; `recharge`: offsets 15–27 — запрос 1 vis и умножение на 1000.
- `thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe.matches`: offsets 0–88 — полный набор условий без предела 3; `getAspects`: offsets 9–28 — экспоненциальная цена; `getRecipeOutput`: offsets 12–31 — увеличение уровня и сохранение byte.

## Инфузия: точный ориентир BETA26

Алтарь формируется Salis Mundus, активируется кастером. Центральный предмет лежит на пьедестале на два блока ниже матрицы; боковые компоненты — на остальных пьедесталах. Сначала расходуется эссенция, затем по очереди компоненты, после чего заменяется центральный предмет. При недостатке ресурса процесс ждёт и продолжает терять стабильность; пропажу предмета нужно восстановить, а не нажать «готово». Требуется корректное сохранение незавершённого процесса. [Книга][lang-infusion], [TileInfusionMatrix][infusion-consume].

В **BETA26 стабильность переработана, Stabilizer не использует RF/FE** — это прямо отмечено в [официальном changelog](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2629023). Образцы TC6 до BETA26 нельзя автоматически использовать для численного поведения.

- Предел стабильности 25, нижняя граница −100. Вне крафта активный алтарь восстанавливает её. В цикле крафта сначала случайная потеря от 0 до `recipeInstability / modifier`, затем прибавляется восстановление окружения. Modifier зависит от текущего состояния: 5 / 6 / 7 / 8. Отрицательная стабильность допускает аварии. Нехватка эссенции добавляет отдельные потери. [Расчёт][infusion-stability].
- Окружение проверяется на ±8 по X/Z и от 7 ниже до 3 выше матрицы. Стабилизирующие блоки сопоставляются с противоположной точкой относительно матрицы на той же высоте. Несовпадение даёт штраф. Повторные пары одного вида блока имеют множитель **0.75^n**. Поэтому бесконечное добавление одинаковых свечей даёт всё меньше эффекта; глава гайда с гигантской площадкой свечей не является универсальным решением для BETA26. [Окружение][infusion-surroundings].
- Candles дают базово 0.1, Stabilizer 0.25. Проверять нужно тип блока/вариант и symmetry penalty, а не просто число декоративных предметов. Баланс заполненных пьедесталов тоже влияет через `BlockPedestal.hasSymmetryPenalty`. [Свечи][candle], [Stabilizer block][stabilizer-block], [Pedestal][pedestal].
- Stabilizer имеет внутренний защитный заряд до 15, восстанавливает единицу за 20 тиков с выделением 0.25 flux. Redstone Inlay связывает его с пьедесталами, проводя защитный заряд с ограничением дальности; пьедесталы также передают его. Защита перенаправляет некоторые аварии, которые иначе выбили бы/уничтожили предмет. Обычное redstone питание или FE capability здесь не заменяет эту механику. [TileStabilizer][stabilizer], [текст защиты][lang-stabilizer].
- Boost stones ставятся под четырьмя опорами: speed уменьшает цикл на 1 и увеличивает множитель цены на 0.01; cost увеличивает цикл на 1 и уменьшает цену на 0.02. Эти камни не требуют симметрии. Ancient/Eldritch опоры и пьедесталы имеют отдельные изменения стоимости, скорости и восстановления. Не сводить все улучшения к универсальному «уровню алтаря». [Код улучшений][infusion-surroundings].

### Результаты инфузии: все темы главы

| Тема | Поведение |
|---|---|
| Cloudstepper Ring | Bauble: повторный прыжок в воздухе и уменьшение урона от падения. |
| Arcane Paving Stones | Travel кратковременно усиливает перемещение. Barrier препятствует большинству существ и големам, пропускает игроков; сигнал redstone отключает. Цвета рун показывают активное препятствие, отключение и разрыв защиты. Не абсолютный щит от любых сущностей. |
| Verdant Charms | Базовый charm уже снимает отравления/подобные эффекты, а не только является ингредиентом. Lifegiver лечит, Sustainer даёт питание и воздух. Нужен внутренний vis-заряд; pedestal/Amulet of Vis относятся к зарядке. |
| Headband of Curiosity | Преобразует половину собираемого XP в небольшой объём исследовательского знания; не бесплатное прибавление опыта и теорий одновременно. |
| Boots of the Traveler | Скорость на суше/в воде, прыжок, переносимость падения за заряд. Открытие связано с ходьбой, бегом, плаванием, прыжками. |
| Elemental Tools | Axe: рубка связного дерева с дальнего блока и притяжение предметов; Pickaxe: native clusters и поиск руд; Sword: дополнительные цели, защитный вихрь/подъём; Shovel: область 3×3 добычи/строительства с ориентацией; Hoe: 3×3 пахота и ускорение роста за durability. Sneak подавляет специальные режимы, где это предусмотрено. |
| Thaumium Fortress Armor | Дополнительная прочность и сопротивление магии/огню, взаимодействие надетых частей. Параметры обычной thaumium брони не заменяют fortress. |
| Fortress Faceplates | Revealing даёт функции очков без их vis-discount и совместим с другой маской; Grinning Devil ослабляет тяжесть warp-событий, не их частоту; Angry Ghost может дать нападающему wither; Sipping Fiend может красть жизнь. |
| Infusion Enchantment | Collector, Burrowing, Refining, Sounding, Destructive, Arcing, Essence Harvester и **Lamplighter**, который гайд пропускает. У каждого собственные допустимые инструменты/ранги. Совместимы с обычными чарами; дополнительные разные infusion enchantments повышают цену и шанс Warping. |
| Runic Shielding | Возобновляемый защитный запас поверх здоровья на броне/baubles, восстановление за vis ауры; отдельные виды повреждений обходят защиту. Рецепт наращивания не ограничен тремя рангами; точные цены выше. |
| Crystal Farming | Инфузия превращает кристалл в посадочный вариант. Применяется к природным primal и flux-кристаллам; зависит от правил роста в мире, не распространяется автоматически на все составные аспекты. |
| Charm of Undying | Носимая версия Totem of Undying; скрытое открытие после исследования тотема. |

Основание таблицы: [тексты инфузии, строки 1426–1471][lang-infusion-products], [рецепт рунического усиления][runic], [исследования инфузии][infusion-json]. Crystal Farming в гайде расположена здесь, но её реальную research-ветку и worldgen/growth следует брать из JSON/кода, а не из позиции страницы.

## Artifice: все темы главы

| Устройство | Поведение и связи |
|---|---|
| Arcane Lamp | Дополнительные невидимые источники света до 16 блоков; удаляются после снятия лампы. Redstone выключает. Порог света в 1.20.1 требует сознательного сопоставления с оригинальным поведением, не автоматического изменения баланса. |
| Lamp of Growth | Расход Herba и ускорение растений в освещаемой области; redstone выключает. |
| Lamp of Fertility | Расход Desiderium, перевод подходящих животных в состояние размножения. Ограничение — 8 и более **одного вида**, а не восемь любых существ суммарно. |
| Magic Mirror | Связанная пара переносит предметы, в том числе между измерениями; живых существ не переносит. Память связи сохраняется при переносе блока. Транспорт загрязняет ауру. |
| Magic Hand Mirror | Только отправляет предметы в привязанное зеркало; может привязываться и к уже спаренному зеркалу. Утверждение гайда «только unlinked» расходится с книгой. |
| Essentia Mirrors | Расширяют воздушный доступ к источникам через связанную пару; напрямую трубы к ним эссенцию не подают. Включают загрязнение ауры. |
| Hungry Chest | Собирает касающиеся его выпавшие предметы; сам не выталкивает содержимое в соседние инвентари. |
| Arcane Levitator | Поднимает или при горизонтальном размещении перемещает существ/предметы. Базовая дальность 8 и расход 1 vis/с при работе; настройка меняет оба параметра. Sneak позволяет опускаться. |
| Infernal Furnace | Мультиблок из obsidian/netherbrick с Salis Mundus, без топлива; vis повышает скорость. Принимает предметы сверху, выдаёт спереди/в инвентарь, неплавящиеся уничтожает. Возможны flux, дополнительные nuggets/rare earth/meat nuggets; последние идут в Triple Meat Treat. |
| Arcane Bellows | Ускоряют обычные печи, нагрев crucible, smelter; воздействуют на скорость/бонусы infernal furnace и suction buffer. Redstone отключает. Конкретные допустимые стороны устройства проверяются отдельно. |
| Thaumic Dioptra | Карта высот vis/flux области диаметром 13 чанков; переключение кликом. Comparator читает уровень своего чанка. Не повод принудительно загружать всю область. |
| Arcane Pattern Crafter | Входной инвентарь сверху, выходной снизу; выбранный шаблон из одинакового материала, проверка настоящего crafting recipe. 1 vis за операцию; redstone отключает. Не универсальный 9-слотовый программируемый autocrafter. |
| Arcane Grappler | Притягивает игрока к зацепленному блоку, требует заряда; sneak отпускает. Не отменяет урон от падения. |
| Vis Generator | Отдаёт FE соседнему приёмнику со стороны выхода; 1000 FE за 1 vis, максимум 20 FE/t, redstone выключает. Этот генератор не требуется для питания стабилизаторов BETA26. |
| Arcane Ear | Слушает настроенный note block (нота и инструмент/материал), выдаёт импульс redstone; отдельный вариант переключает состояние. |
| Automated Crossbow | Турель с владельцем/целями, боеприпасами, здоровьем и ориентацией; заряжается стрелами из направленного вверх dispenser. Базовый guide даёт 24 блока и 15 HP; точные targeting/LOS/cooldown переносить из класса, отдельно проверив JAR. |
| Advanced Crossbow | Настройка типов целей, большая скорострельность и прочность; guide даёт 20 HP. Дополнительная зависимость от Biothaumic Mind. |
| Arcane Bore | Redstone запускает, требуется кирка; свойства инструмента и зачарования влияют на добычу, enchantability материала — на область. 1 vis за четыре блока, сильно замедленный износ кирки (книга: в среднем 50×), выдача рядом/в инвентарь; поворот ударом, кроме направления вниз. Взаимодействует с arcane activator rail. |

Перечень основан на [оригинальных текстах Artifice, строки 1480–1549][lang-artifice] и [research JSON][artifice-json]; отдельные числа турелей/дальности grappler остаются тестовыми ориентирами гайда, а не проверенными байткодом параметрами.

Глава гайда не покрывает **Redstone Relay** отдельно, но он есть в TC6 и ведёт к Ear/Generator: задняя настройка задаёт порог входа, передняя — силу выхода. Его нужно включать в порт. [Оригинальная запись][lang-artifice].

## Отличия от нашей 0.2.0-dev и порядок реализации

0.2 содержит базовый crucible, шесть рецептов (brass, thaumium, tallow, leather, nitor, alumentum), salis-преобразования, материалы/обычные thaumium инструменты, ауру и временные уроки. Это **не перенесённая промышленная алхимия и не система инфузии**. Банки, phial-транспорт, smeltery/alembic, трубы, centrifuge, Thaumatorium, transfusers, infusion altar/stability и перечисленные устройства/баubles ещё предстоит реализовать. Наличие архивной записи в Таумономиконе не равно наличию механики.

Рациональная последовательность: аспекты и надёжное их хранение → банки/phials/alembic/smelter → suction/трубы/буфер → Thaumatorium и воздушный транспорт → базовая инфузия и сохранение крафта → BETA26 stabilization/inlay → результаты инфузии и устройства. Research-условия, GUI, модели, FX и звуки должны подключаться к действующему серверному поведению каждой ступени.

Приёмочные сценарии: сохранение количества/типа эссенции при выгрузке; отсутствие дюпа при переносе банки и разрыве трубы; заполненная/пустая/label/brace банка; конфликт suction; void overflow; нехватка топлива/ауры; очередь Thaumatorium без потерь катализатора; нехватка эссенции или компонента во время инфузии; восстановление после перезапуска; симметрия и убывающая отдача; защита пьедестала без FE; смена измерения/недоступный чанк у зеркал; полный выходной инвентарь и сторонняя автоматика.

## Что осталось уточнить

Это чтение всего выбранного корпуса из 53 страниц и проверка ключевых связей, а не исчерпывающий аудит 902 Java-файлов репозитория. Перед переносом конкретного устройства нужны его server/client классы, recipe registration, исследование и небольшой эталонный опыт в оригинальном BETA26. Особенно это касается точных damage/charge/cooldown, multi-block teardown, зеркал с выгруженным получателем, taint, движения сущностей, всех рецептов чар и миграции Baubles на 1.20.1.

Установленные ошибки гайда/книги не следует молча исправлять новым балансом: зафиксированы отдельно и разрешены JAR там, где возможно. Новые ограничения для защиты от переполнения/дюпа также должны быть описаны как изменения порта. Внешний вид сверяется с подлинными ресурсами TC6 и кадрами оригинального клиента; иллюстрации гайда полезны для компоновки, но не заменяют текстуры и render-код.

[alchemy-json]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/research/alchemy.json#L4
[infusion-json]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/research/infusion.json#L18
[artifice-json]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/research/artifice.json#L4
[smelter]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/essentia/TileSmelter.java#L48
[alembic]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/essentia/TileAlembic.java#L30
[tube]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/essentia/TileTube.java#L110
[jar]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/essentia/TileJarFillable.java#L146
[void-jar]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/essentia/TileJarFillableVoid.java#L16
[buffer]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/essentia/TileTubeBuffer.java#L29
[centrifuge]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/essentia/TileCentrifuge.java#L204
[thaumatorium]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/crafting/TileThaumatorium.java#L166
[essentia-search]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/events/EssentiaHandler.java#L210
[infusion-consume]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/crafting/TileInfusionMatrix.java#L496
[infusion-stability]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/crafting/TileInfusionMatrix.java#L357
[infusion-surroundings]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/crafting/TileInfusionMatrix.java#L700
[stabilizer]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/devices/TileStabilizer.java#L43
[stabilizer-block]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/blocks/devices/BlockStabilizer.java#L70
[candle]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/blocks/basic/BlockCandle.java#L88
[pedestal]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/blocks/devices/BlockPedestal.java#L145
[runic]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/crafting/InfusionRunicAugmentRecipe.java#L45
[lang-cleaning]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/lang/en_us.lang#L1335
[lang-jars]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/lang/en_us.lang#L1348
[lang-thaumatorium]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/lang/en_us.lang#L1367
[lang-transfuser]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/lang/en_us.lang#L1371
[lang-urn]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/lang/en_us.lang#L1391
[lang-sprayer]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/lang/en_us.lang#L1395
[lang-infusion]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/lang/en_us.lang#L1407
[lang-stabilizer]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/lang/en_us.lang#L1411
[lang-infusion-products]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/lang/en_us.lang#L1426
[lang-artifice]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/lang/en_us.lang#L1480
