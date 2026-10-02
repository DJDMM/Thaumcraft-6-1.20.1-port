# Биомы Thaumcraft 6.1.BETA26: аудит исходника и официального JAR

Дата: 1 октября 2026 года. Объём: все четыре класса пакета `thaumcraft.common.world.biomes`, фактическая регистрация, спавны после post-init, растительность, биомные коэффициенты ауры и достижимость биомов. Проект порта не изменялся; Gradle и игровая проверка оригинального TC6 не запускались.

## Главные выводы

1. В BETA26 зарегистрированы **ровно три биома**: `thaumcraft:magical_forest`, `thaumcraft:eerie`, `thaumcraft:eldritch` (отображаемое название последнего — **Outer Lands**). Tainted Land / отдельного taint-биома в этом релизе нет.
2. В стандартное распределение биомов добавляется **только Magical Forest**, в обе группы Forge `WARM` и `COOL`, по конфигурируемому весу **5** по умолчанию. Это вес в списке, не вероятность «5% мира».
3. **Eerie и Eldritch зарегистрированы, но штатного серверного пути их создания/распространения в чистом BETA26 не обнаружено.** Нет добавления этих биомов в `BiomeManager`, серверных вызовов смены биома на них или регистрации измерения Outer Lands. Наличие конфигурации `dimensionOuterId=-42`, классов боссов, порталов и проверок этого ID не создаёт измерение.
4. Конструкторы биомов недостаточны для точной таблицы спавнов: post-init заменяет Pech в Magical Forest с `(20,1–2)` на **`(10,1–1)`**, добавляет Pech в Eerie и Eldritch, а Magical Forest получает Brainy Zombie `(10,1–1)`. Magical Forest и Eerie **не очищают стандартный список монстров**; дополнительные Witch/Enderman сохраняются рядом с исходными записями.
5. По штатным тегам aura modifier: Magical Forest **0.625**, Eerie **0.625**, Eldritch **11/24 ≈ 0.45833334**. Генерация ауры начинает с `vis=base`, `flux=0`; Eerie не означает стартовый flux, SPOOKY даёт **Ignis**, не Vitium/Alienis.
6. В лесу есть отдельная биомная декорация: преимущественно большие деревья из **ванильных oak log/leaves**, также Greatwood/Silverwood, ambient grass, mossy cobblestone blobs, большие грибы, vishroom. Глобальный генератор TC6 независимо добавляет редкие Greatwood/Silverwood. Shimmerleaf связан с успешной генерацией Silverwood; Cinderpearl связан с жарким песчаным биомом, а не с Magical Forest.

## Источники и способ проверки

Основной эталон: `work/Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256:

`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.

Полностью прочитаны закреплённые исходники `BiomeHandler.java`, `BiomeGenMagicalForest.java`, `BiomeGenEerie.java`, `BiomeGenEldritch.java`. Исследованы `Registrar`, `CommonProxy`, `ConfigEntities.postInitEntitySpawns`, соответствующий `ModConfig`, `ThaumcraftWorldGenerator`, `AuraHandler.generateAura`, `Utils.setBiomeAt/resetBiomeAt`, генераторы деревьев/цветов и растения. Байткод официального JAR сверялся через `javap -c`, для приватных методов также `-p`.

Сверены по JAR: регистрация и веса, климатические параметры, все три конструктора и цвета, выбор деревьев/травы, порядок декорации и её численные границы, теги и расчёт aura modifier, повторная регистрация DRY, global vegetation gate/chance, post-init спавны и дата Halloween, Silverwood→Shimmerleaf, формула начальной ауры, эффект Vishroom.

Дополнительно проверены наследуемые значения по официальному Minecraft 1.12.2 client JAR с SHA-1 `0f275bc1547d01fa5f56ba34bdc87d981ee12daf`, опубликованным в Mojang version metadata; имена сопоставлены с Forge MCP 1.12.2 SRG. Это проверка базовых классов/цветовых ресурсов, не запуск игры.

Для поведения Forge использован его первичный исходник: [BiomeDictionary 1.12.x](https://github.com/MinecraftForge/MinecraftForge/blob/1.12.x/src/main/java/net/minecraftforge/common/BiomeDictionary.java) и [EntityRegistry.addSpawn 1.12.x](https://github.com/MinecraftForge/MinecraftForge/blob/1.12.x/src/main/java/net/minecraftforge/fml/common/registry/EntityRegistry.java#L238). `addSpawn` изменяет существующую запись того же класса, если она есть; иначе добавляет новую. Явные теги TC6 уже присутствуют, поэтому автоматическая эвристика Forge не добавляет им SPARSE/HILLS/PLAINS из климата.

Все ссылки на исходники TC6 ниже считаются относительно `work/thaumcraft6-reference/src/main/java/`.

## Реестр и появление

Фактический вход — `thaumcraft/Registrar.java:107`, обработчик `RegistryEvent.Register<Biome>`. Каждый объект создаётся и регистрируется независимо от `generateMagicForest`; затем вызывается `BiomeHandler.registerBiomes()`. Только добавление Magical Forest в генерационные списки зависит от `CONFIG_WORLD.generateMagicForest` (по умолчанию `true`). `biomeMagicalForestWeight=5`, диапазон конфига 0–100. Числовые ID назначаются реестром Forge; не следует фиксировать ID из конкретного сохранения. `getFirstFreeBiomeSlot` в BiomeHandler — оставшаяся утилита, не текущий механизм регистрации.

| ID / имя | baseHeight | heightVariation | temperature | rainfall | Осадки | Surface / filler | Explicit Forge types |
| --- | ---: | ---: | ---: | ---: | --- | --- | --- |
| `thaumcraft:magical_forest` / Magical Forest | 0.2 | 0.3 | 0.8 | 0.4 | Разрешены | Grass / Dirt, наследуется | MAGICAL, FOREST |
| `thaumcraft:eerie` / Eerie | 0.125 | 0.4 | 0.8 | **0.5**, наследуется | `setRainDisabled()` | Grass / Dirt, наследуется | MAGICAL, SPOOKY |
| `thaumcraft:eldritch` / Outer Lands | 0.125 | 0.15 | 0.8 | 0.2 | Разрешены в свойствах биома | Dirt / Dirt, явно | MAGICAL, SPOOKY, END |

Высоты здесь — старые параметры 1.12 biome terrain, не абсолютные координаты Y и не готовые настройки современного noise generator. Отключение дождя Eerie не меняет его rainfall с наследуемых 0.5 на 0.

### Почему Eerie/Outer Lands нельзя заявлять как естественную генерацию BETA26

Поиск по всему исходнику и просмотр constant-pool строк **всех `thaumcraft/*.class`** официального JAR подтвердили:

- EERIE присваивается в Registrar, получает типы в BiomeHandler и читается в ограничении спавна Pech. В генераторах/тайлах/серверных событиях нет записи биома Eerie.
- ELDRITCH присваивается в Registrar, получает типы в BiomeHandler и читается в специальных условиях EntityTaintSeed/EntityTaintacle. Эти сравнения не создают сам биом.
- Нет вызова `registerDimension`, нет класса WorldProvider для Outer Lands, нет соответствующего BiomeProvider/ChunkGenerator. Конфиг `-42` и проверки измерения в PlayerEvents/EntityEvents/ThaumcraftWorldGenerator — остаточная поддержка.
- Единственная реализация смены — `Utils.setBiomeAt` (`common/lib/utils/Utils.java:127`): запись в 256-байтный X/Z biome array чанка и при необходимости отправка `PacketBiomeChange` в радиус 32. `resetBiomeAt` восстанавливает биом из provider. Внутренние вызовы — перегрузки/reset и клиентский `PacketBiomeChange$1`; нет игрового серверного вызывающего кода, который устанавливает Eerie/Eldritch.
- В JAR пакет биомов содержит только три BiomeGen-класса и BiomeHandler; отдельного taint-биома нет.

`ThaumcraftWorldGenerator.generateSurface` действительно генерирует mound с шансом 1/100 на подходящий новый/регенерируемый чанк и иначе пробует Lesser Crimson Portal с шансом 1/500; он **не меняет биом**. Ветка `worldGeneration` для `dimensionOuterId` лишь помечает чанк dirty и пропускает обычную TC world generation; она не создаёт Outer Lands. Серебряное дерево тоже не превращает окружение в Magical Forest в этом релизе.

Это статически подтверждённое отсутствие штатного пути в данном JAR, а не утверждение, что зарегистрированный биом невозможно принудительно разместить инструментом, настройкой другого генератора или аддоном. Внешние способы и чужие измерения не являются естественным TC6 BETA26 worldgen.

## Цвета и визуальные/игровые эффекты

Здесь указаны исходные возвращаемые RGB, до погоды, освещения, смешивания соседних биомов и resource-pack текстур.

| Биом | Grass | Foliage | Water multiplier | Sky |
| --- | --- | --- | --- | --- |
| Magical Forest, стандартный `blueBiome=false` | **`#55FF81`** / 5635969 | **`#66FFC5`** / 6750149 | **`#0077EE`** / 30702 | Vanilla temperature formula; при T=0.8 **`#78A7FF`** |
| Magical Forest, `blueBiome=true` | **`#66AACC`** / 6728396 | **`#77CCEE`** / 7851246 | `#0077EE` | Та же vanilla formula |
| Eerie | **`#404840`** / 4212800 | **`#404840`** / 4212800 | **`#2E535F`** / 3035999 | **`#222299`** / 2237081 |
| Outer Lands | Vanilla grass colormap | Vanilla foliage colormap | Наследуемый **`#FFFFFF`** / 16777215 | **`#000000`** / 0 |

Outer Lands не имеет собственной фиолетовой травы/воды или явного foliage override. В штатных Minecraft 1.12.2 colormap PNG при Y≤64, T=0.8 и rainfall=0.2 получаются grass **`#A2BA5D`**, foliage **`#8BA835`**; это вычисляемые значения, не константы TC6. На высоте температура меняется по vanilla формуле, resource pack также может изменить результат.

Magical Forest не имеет отдельного sky override; значение при T=0.8 вычислено по проверенной vanilla HSV-формуле `HSV(0.62222224 - clamp(T/3,-1,1)*0.05, 0.5 + clamp(T/3,-1,1)*0.1, 1)`. Старый water **multiplier** нельзя механически считать окончательным современным water color.

`client/ColorHandler.java:66` красит ambient grass по биому. Greatwood leaves используют биомный foliage color; **Silverwood leaves возвращают `#FFFFFF`**, то есть не получают цветной biome tint.

В классах биомов нет общего постоянного potion effect, урона, дополнительного собственного тумана или ambient soundtrack. Найденный клиентский `fogFiddled/fogDuration` принадлежит misc/warp-событиям, не условию нахождения в Eerie/Magical Forest. Реальные эффекты лесной флоры:

- **Vishroom** (`common/blocks/world/plants/BlockPlantVishroom.java:36`): при серверной коллизии живого существа шанс **1/5** наложить Nausea I на **200 тиков / 10 секунд**; light level 0.4 → 6, фиолетовые motes в 1/3 display ticks. Это действие гриба, не всего биома.
- **Shimmerleaf**: light level 0.4 → 6; светлые бирюзовые motes в 1/3 display ticks; обычная Plains plant, без эффекта на существа.
- **Ambient grass** (`common/blocks/world/BlockGrassAmbient.java:29`): клиентский вычисленный свет/угол суток, условие `4 + 2*i < 1 + nextInt(13)`; ищется обычная grass в случайном X/Z ±8 и вниз до 10 шагов, Y>50, затем wispy motes. Это локальная декоративная механика, преимущественно при низком свете, без расхода/генерации vis.
- SPOOKY/END типы Eerie/Eldritch участвуют в общей проверке чемпионов (`EntityEvents.java:285`): из случайного `c` вычитается 2 при разрешённых champion mobs, иначе 1; это один OR-блок, наличие обоих тегов не удваивает вычитание. Самостоятельного biome-wide debuff нет.

## Растительность и декорация

### Magical Forest: встроенный biome decorator

`BiomeGenMagicalForest.java:34–145`. В конструкторе: `treesPerChunk=2`, `flowersPerChunk=10`, `grassPerChunk=12`, `waterlilyPerChunk=6`, `mushroomsPerChunk=6`. Это количества **попыток vanilla decorator**, не гарантия стольких деревьев/кустов. Наследуется стандартный `extraTreeChance=0.1`.

Выбор каждой tree feature — последовательные проверки, а не три независимых веса:

| Feature | Условие | Итоговая вероятность выбора |
| --- | --- | ---: |
| Silverwood `(false,8,5)` | `nextInt(18)==0` | 1/18 ≈ 5.56% |
| Greatwood `(false,spiders)` | Первая проверка не прошла, `nextInt(12)==0` | 17/216 ≈ 7.87% |
| `WorldGenBigMagicTree(false)` | Обе не прошли | 187/216 ≈ 86.57% |

Silverwood tree height parameter — 8–12; global TC generator использует другой диапазон, 7–10. Greatwood получает spider variant с отдельным шансом **1/8**: успешное дерево ставит cave-spider spawner под основанием, пробует 50 мест для паутины рядом с его wood/leaves и сундук ещё на блок ниже с vanilla simple-dungeon loot. Это не Eerie-конверсия.

`WorldGenBigMagicTree` строит **vanilla oak log/leaves**, не Greatwood/Silverwood. Начальный `heightLimit=11+nextInt(11)` (11–21), с допустимым сокращением препятствием; объект `bigTree` кэшируется биомом, его ненулевой heightLimit не сбрасывается перед каждым generate. Поэтому нельзя считать 11–21 новым независимым броском для каждого дерева. Алгоритм имеет ветви/крону большого дерева; это не обычный маленький oak sapling generator.

Трава: feature **fern 1/4**, обычная grass **3/4**. Цветок выбирается **по координатному GRASS_COLOR_NOISE**, не равномерным RNG: `clamp((1+noise(x/48,z/48))/2,0,0.9999)` индексирует полный массив vanilla `EnumFlowerType`.

Порядок `decorate` подтверждён JAR:

1. До super-decorate — **до 3 попыток** найти vanilla grass в X/Z offsets 4–11, спускаясь с `world.getHeight` пока Y>30; первый успех заменяет **один** блок на ambient grass и прекращает цикл. Не три ambient blocks.
2. **0–2** вызова `WorldGenBlockBlob(MOSSY_COBBLESTONE,0)`; позиции X/Z 8–23 относительно переданного decorate pos.
3. Сетка **4×4**, в каждой клетке шанс **1/40** попытки большого vanilla mushroom, итого 16 trials (ожидание 0.4 попытки/вызов decorate, не гарантия результата). Offsets `k*4+9+nextInt(3)`.
4. `super.decorate`, исключения перехватываются и не прекращают оставшуюся TC-декорацию.
5. **8 попыток Vishroom** с X/Z 0–15, вниз с height пока Y>50 до vanilla grass; выше неё блок должен быть replaceable, а в любом из **26 соседей 3×3×3** должен быть wood log по `Utils.isWoodLog` (Forge wood/canSustainLeaves/ore-dict log). Тогда сверху ставится vishroom. Наличие только grassAmbient не удовлетворяет точному сравнению с `Blocks.GRASS`.

Эта биомная декорация **не проверяет `generateTrees` и TC blacklist**: отключение глобальной TC tree-generation не автоматически отключает деревья/растения собственного forest decorator.

### Независимая глобальная флора TC6

`ThaumcraftWorldGenerator.java:106,215,235,247`, зарегистрирован через `CommonProxy.java:63` с worldgen priority 0.

`generateAll` вызывает vegetation, когда dimension blacklist == -1, `generateTrees=true`, world type name не начинается с `flat`, и это newGen либо `regenTrees=true`. В `generateVegetation` любой biome blacklist != -1 у центра чанка (X/Z +8, Y=50) прекращает всю глобальную флору этого чанка.

- Silverwood: trial **`nextInt(80)==3`**, затем кандидат X/Z `chunk*16+8+random[-4,4]`, precipitation height. Gate: `greatwoodSupport/2 > nextFloat` **или** MAGICAL biome, отличающийся от Magical Forest, **или** vanilla biome ID 18/28 (Forest Hills/Birch Forest Hills). Для штатного Magical Forest support=1, значит дополнительная проверка 1/2; совместный шанс выбора до проверки пригодности местности — **1/160 чанка**. Генератор `(false,7,4)`.
- Greatwood: trial **`nextInt(25)==7`**; аналогичные координаты; gate `greatwoodSupport > nextFloat`. Для штатного Magical Forest support=1, gate всегда проходит, значит **1/25** trial до проверки местности. Spider variant — 1/8.
- Cinderpearl: после tree trials берётся height в центре чанка; biome topBlock должен быть **SAND**, температура позиции **строго >1.0**, затем шанс **1/30** вызвать flower cluster. Magical Forest не отвечает штатному условию.
- Успешный Silverwood с `doBlockNotify=false` включает `worldgen=true` и один вызов `WorldGenCustomFlowers(shimmerleaf,0)` около основания. Cluster делает **18 попыток**, X/Z разность двух `nextInt(8)` (−7..7), Y разность двух `nextInt(4)` (−3..3); требуется воздух и grass **или sand** ниже. У самого Shimmerleaf обычное sustained soil — grass/dirt; генератор непосредственно ставит блок, не проверяя plant survival predicate. При росте sapling с `doBlockNotify=true` эта worldgen flower ветка выключена.

### Eerie и Eldritch

Eerie задаёт `treesPerChunk=2`, `flowersPerChunk=1`, `grassPerChunk=2`, остальное наследует. Нет собственного override decorate/tree/grass/flower: при принудительной генерации это обычные vanilla tree/plant features с его палитрой и стандартными deco routines. Новая biome assignment сама по себе не перегенерирует уже существующую растительность.

Eldritch полностью переопределяет `decorate` **пустым методом**: biome decorator не размещает растения, деревья, ores, pools и т.п. через этот путь. Top/filler — dirt, sky black. Это не доказательство общей генерации Outer Lands: отдельного generator нет. Другой подключённый внешний/global generator, если такой биом принудительно размещён, не запрещён пустым decorate.

## Точные списки спавнов

Ниже `(weight, min–max group)`. Weight — относительный вес внутри **конкретного category list**, не процент и не самостоятельное разрешение спавна. Vanilla entity predicates, caps и difficulty продолжают действовать.

### Наследуемый Minecraft 1.12.2 baseline

| Category | Vanilla записи |
| --- | --- |
| CREATURE | Sheep `(12,4–4)`, Pig `(10,4–4)`, Chicken `(10,4–4)`, Cow `(8,4–4)` |
| MONSTER | Spider `(100,4–4)`, Zombie `(95,4–4)`, Zombie Villager `(5,1–1)`, Skeleton `(100,4–4)`, Creeper `(100,4–4)`, Slime `(100,4–4)`, Enderman `(10,1–4)`, Witch `(5,1–1)` |
| WATER_CREATURE | Squid `(10,4–4)` |
| AMBIENT / cave creatures | Bat `(10,8–8)` |

### Magical Forest

Все четыре vanilla lists сохраняются. CREATURE дополнительно Wolf `(2,1–3)` и Horse `(2,1–3)`. В MONSTER напрямую добавлены **дополнительные** Witch `(3,1–1)`, Enderman `(3,1–1)`, Vex `(1,1–1)`. Суммарный вес Witch=8, но это две записи; у Enderman вес 13 с различающимися группами двух записей.

При стандартных `allowSpawn*=true` итог post-init добавляет/меняет:

| TC entity | В конструкторе | После post-init |
| --- | --- | --- |
| Pech | `(20,1–2)` | **`(10,1–1)`**, существующая запись заменена |
| Wisp | `(20,1–2)` | Без изменения |
| Brainy Zombie | Нет | **`(10,1–1)`**, потому что Magical Forest есть в generation lists |
| Fire Bat | Нет | Только если запуск post-init пришёлся на **31 октября**, `(5,1–2)` при `allowSpawnFireBat=true` |

Отключение `generateMagicForest` оставляет биом зарегистрированным и не очищает constructor/магические Pech spawns, но исключает его из собственных WARM/COOL списков; поэтому post-init Brainy Zombie/Halloween Fire Bat путь через эти списки для него исчезает, если другой мод не добавил его туда.

### Eerie

Только CREATURE list очищен, затем туда добавлен **Bat `(3,1–1)`** — именно CREATURE, а не cave list. Поэтому обычные сельскохозяйственные animals удалены, но inherited cave Bat `(10,8–8)` и Squid `(10,4–4)` остаются. MONSTER baseline остаётся; дополнительные записи:

| Entity | Дополнительная запись | Config gate |
| --- | --- | --- |
| Witch | `(8,1–1)`; вместе с inherited Witch5 суммарный вес13 | Без gate |
| Enderman | `(4,1–1)`; inherited Enderman10 также остаётся | Без gate |
| Brainy Zombie | `(32,1–1)` | allowSpawnAngryZombie |
| Giant Brainy Zombie | `(8,1–1)` | allowSpawnAngryZombie |
| Wisp | `(3,1–1)` | allowSpawnWisp |
| Eldritch Guardian | `(1,1–1)` | allowSpawnElder |
| Pech | **`(10,1–1)`**, post-init MAGICAL registration | allowSpawnPech |

Стандартный TC post-init Brainy Zombie weight10 **не заменяет Eerie weight32**, поскольку Eerie не включён в generation lists. Наличие этой таблицы не доказывает, что Eerie естественно появляется.

### Eldritch / Outer Lands

Конструктор очищает **все четыре** vanilla lists. MONSTER получает Inhabited Zombie `(1,1–1)` и Eldritch Guardian `(1,1–1)` **без allowSpawnElder gate** в этом конструкторе. Post-init MAGICAL pass добавляет Pech `(10,1–1)`, если разрешён. Остальные категории пусты.

Pech затем проверяет `getCanSpawnHere`: биом должен иметь MAGICAL; nearby Pech count<4 в grow(16,16,16); в измерении, отличном от `overworldDim`, допускаются только конкретные **Magical Forest или Eerie**. Поэтому его добавленная Eldritch spawn entry не означает разрешённый обычный Pech spawn в гипотетическом Outer Lands dimension. Wisp ограничивает nearby count<8 и использует собственную darkness predicate; Guardian требует отсутствия другого Guardian в grow(32,16,32) и возвращает true для light-level predicate. Это entity checks поверх biome list.

## BiomeHandler: теги, аура, кристаллы и деревья

`registerBiomeInfo(type,auraLevel,aspect,greatwood,chance)` хранит значения по **Forge biome type**, не по biome ID. Итоговая таблица после всех вызовов:

| Type | auraLevel | Aspect | Greatwood support |
| --- | ---: | --- | ---: |
| WATER, OCEAN | 0.33 | Aqua | 0 |
| RIVER, WET | 0.4 | Aqua | 0 |
| LUSH | 0.5 | Aqua | 0.5 |
| HOT, MESA | 0.33 | Ignis | 0 |
| NETHER | 0.125 | Ignis | 0 |
| SPOOKY | 0.5 | Ignis | 0 |
| DENSE | 0.4 | Ordo | 0 |
| SNOWY, COLD | 0.25 | Ordo | 0 |
| MUSHROOM | 0.75 | Ordo | 0 |
| MAGICAL | 0.75 | Ordo | 1.0 |
| CONIFEROUS | 0.33 | Terra | 0.2 |
| FOREST | 0.5 | Terra | 1.0 |
| SANDY | 0.25 | Terra | 0 |
| BEACH | 0.3 | Terra | 0 |
| JUNGLE | 0.6 | Terra | 0 |
| SAVANNA | 0.25 | Aer | 0.2 |
| MOUNTAIN | 0.3 | Aer | 0 |
| HILLS | 0.33 | Aer | 0 |
| PLAINS | 0.3 | Aer | 0.2 |
| END | 0.125 | Aer | 0 |
| **DRY** | **0.125** | **Perditio** | 0 |
| SPARSE | 0.2 | Perditio | 0 |
| SWAMP | 0.5 | Perditio | 0.2 |
| WASTELAND | 0.125 | Perditio | 0 |
| DEAD | 0.1 | Perditio | 0 |

**Подтверждённая особенность binary:** DRY сначала зарегистрирован `(0.25,Ignis,false,0)` и в конце **повторно перезаписан** `(0.125,Perditio,false,0)`. В действующей HashMap остаётся последняя запись. Это не исправленная здесь опечатка декомпилятора.

`getBiomeAuraModifier` берёт арифметическое среднее auraLevel **всех** types биома. При exception, например неизвестный type, возвращает 0.5 для **всего биома**, а не усредняет только известные tags. Пустой set даёт float `0/0` (NaN), отдельной защиты нет. Для штатных TC6 биомов types заданы явно и известны:

| Биом | Расчёт | Modifier | Номинальный base при пяти одинаковых биомах и Gaussian=0 |
| --- | --- | ---: | ---: |
| Magical Forest | `(MAGICAL .75 + FOREST .5)/2` | 0.625 | 312 |
| Eerie | `(MAGICAL .75 + SPOOKY .5)/2` | 0.625 | 312 |
| Eldritch | `(.75 + .5 + END .125)/3` | ≈0.45833334 | 229 |

`AuraHandler.generateAura` проверяет biome blacklist центра; любой !=−1 прекращает генерацию. Затем берутся modifier центрального чанка и четырёх cardinal neighbours по центрам X/Z и **Y=50**, усредняются. `noise=float(1 + nextGaussian()*0.10000000149011612)`, `base=clamp((short)(life*500*noise),0,500)` с truncation/cast, затем `addAuraChunk(...,base,base,0)`. Поэтому 312/229 — условные no-noise значения однородного региона, а не фиксированная аура каждого чанка. У штатного `IWorldGenerator.generate` вызов generateAura идёт после worldGeneration; собственный generateNodes с конфиг-условием присутствует, но в данном worldgen path не используется.

`getRandomBiomeTag` выбирает **один Forge type равномерно**, затем возвращает его aspect; exception→null. Без сторонних тегов Magical Forest даёт Ordo/Terra по 1/2, Eerie Ordo/Ignis по 1/2, Eldritch Ordo/Ignis/Aer по 1/3. При crystal worldgen (`ThaumcraftWorldGenerator.java:184`) шанс 1/3 заменить обычный случайный primal variant на этот biome tag; это связь биома с кристаллами, не специальная Eldritch/flux-аура.

`getBiomeSupportsGreatwood` возвращает chance **первого** типа с greatwood=true в итерации Set; не max, не сумму, не среднее. Exception или отсутствие подходящего типа→0. Для штатных TC6 типов результат 1.0 у всех трёх: MF имеет MAGICAL+FOREST, оба support1; Eerie/Eldritch — MAGICAL support1. Для стороннего биома с CONIFEROUS/LUSH/MAGICAL порядок Set может изменить результат; нельзя заменять этот алгоритм «на максимальный шанс», выдавая это за BETA26.

## Границы выводов для переноса

Подтверждённый естественный survival-объём — Magical Forest и связанная с ним flora/spawn/aura спецификация. Зарегистрированные Eerie/Outer Lands можно сохранить как данные/совместимость с явным статусом, но добавление обычной генерации, обелисков с biome corruption, доступного Outer Lands dimension, постоянного фиолетового fog или tainted-biome conversion будет новым решением, а не восстановлением подтверждённого поведения BETA26.

Для 1.20.1 отдельно нужны решения по современной climate/noise интеграции, замене 1.12 biome dictionary, 3D biome storage вместо X/Z byte array, высотам flora вместо жёстких 30/50/256, и water multiplier. Эти решения здесь не реализованы. Спавны в отчёте учитывают post-init и сохранённый vanilla baseline; регистрация сущности/биома или наличие loot/resources не доказывают его естественную доступность.
