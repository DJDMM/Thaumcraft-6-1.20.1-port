# Пресс големов — контракт Thaumcraft 6.1.BETA26

Это аудит исходного поведения, а не отчёт об успешно проверенном переносе.
Эталон — официальный `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Зеркало исходников закреплено на `954022bb777b7546281fb36df8522f0ba6b43f81`.
Числа, ветвления и спорные формулы проверены через `javap -p -c` оригинального JAR.

Рабочий срез0.22 переводит MINDCLOCKWORK из частичной стадии2 в полностью
поддержанную исходную прогрессию: платёж32+32 Theory завершает запись на4,
открывает её заключение/схему пресса и strict базовые parts. Полный canonical
набор теперь51, не50 плюс partial. CONTROLSEALS/SEALCOLLECT/SEALSTORE и
поздние part/material branches остаются unsupported и не открываются.
Серверные проверки описанного перехода расширены, но их добавление само
по себе не подтверждает успешный прогон или завершённость AI/печати.

## Исследования: формирование и изготовление имеют разные gates

`ConfigRecipes.initializeMultiblockRecipes`, offsets1999–2317, регистрирует
`DustTriggerMultiblock("MINDCLOCKWORK", ...)` и каталог `thaumcraft:GolemPress`.
`DustTriggerMultiblock.getValidFace`, offsets0–24, проверяет именно
`IPlayerKnowledge.isResearchKnown`, то есть **начатый** MINDCLOCKWORK. Это
не gate @3 и не требование завершения. Физический пресс можно сформировать
на уже поддержанной стадии2; показ его схемы в обычной книге сохраняет
отдельное исходное ограничение на текущую/завершённую стадию.

Но `GuiGolemBuilder.initGui` фильтрует каждый материал, голову, руки, ноги
и дополнение через `ThaumcraftCapabilities.knowsResearchStrict`. Bare-ключи
здесь требуют **завершения**. BASIC head/arms, WALKER/ROLLER legs и NONE/HAULER
addon требуют завершённого MINDCLOCKWORK; WOOD требует MATSTUDWOOD. Поэтому
начатый MINDCLOCKWORK@2 позволяет построить машину, но не даёт исходного
доступа к её базовым сборкам.

Следующий исходный платёж MINDCLOCKWORK — 32 raw Theory Artifice и32 Theory
Golemancy. JSON содержит три стадии; пустое заключение пропускается при
завершении до записанного stage4. Поддержка естественного изготовления
базового голема требует перенести этот переход, а не заменить strict gate
на started/@2. CONTROLSEALS и прочие unsupported siblings нельзя объявлять
рабочими вследствие завершения Mind: доступность записи знания и выполнение
механики печати остаются отдельными состояниями.

В исходном серверном `TileGolemBuilder.startCraft` нет повторной проверки
исследований или занятости процесса: GUI служит единственным ограничением
выбора. Для современного C2S серверная проверка того же strict набора,
контекста открытого меню, диапазона и корректных ID — явное усиление
целостности, сохраняющее исходные разрешённые сборки. Она не является
основанием снизить research gate. Отказ busy/replayed запросу также должен
быть обозначен как усиление: оригинальный прямой start может заменить уже
оплаченное задание и потерять его материалы.

## Многоблочная конструкция и ориентация

Размер массива 2×2×2, индексы `[слой сверху][x][z]`:

```text
верх:  [ [ wildcard, wildcard ], [ bars, wildcard ] ]
низ:   [ [ cauldron, anvil ], [ upward piston, stone table ] ]
```

Все три `null` верхние клетки — **wildcard**, не обязательный воздух.
Поршень — обычный `PISTON.getDefaultState().with(FACING, UP)`, сравнивается
целым исходным состоянием; выдвинутый, направленный иначе или липкий не
подходят. Bars/cauldron/anvil/stone table заданы как Block, поэтому в1.12
проверяют ID блока без metadata: соединения решётки, вода котла и поворот
или износ наковальни не меняют успешность. В1.20 разделённые damaged anvil
и water cauldron IDs требуют осознанного отображения этих1.12 вариантов,
если они принимаются переносом; это не основание принять lava/powder-snow
cauldron или другой вид поршня.

Исходный matcher перебирает горизонтали **SOUTH, WEST, NORTH, EAST**;
их horizontalIndex0,1,2,3 подтверждены vanilla1.12 классом `fa`, mapping
`joined.srg:268`. Он вращает каждый слой вправо `3 - horizontalIndex`
раза. Положение builder и его FACING соответствуют найденной ориентации,
а не стороне клика/повороту игрока: GP3 не имеет applyPlayerFacing.
Относительно поршня/builder:

| FACING | Котёл | Каменный стол | Наковальня | Решётка |
|---|---|---|---|---|
| NORTH | SOUTH | EAST | SOUTH+EAST | UP |
| SOUTH | NORTH | WEST | NORTH+WEST | UP |
| WEST | EAST | NORTH | EAST+NORTH | UP |
| EAST | WEST | SOUTH | WEST+SOUTH | UP |

То есть котёл сзади, стол справа по часовой стрелке, наковальня сзади
справа. Поиск начала от клика перебирает yy,xx,zz от−2 до0 включительно,
сначала yy, затем xx, затем zz. Он не требует кликнуть именно поршень:
произвольный клик с подходящим началом в этом диапазоне может найти пресс.

Формирование меняет только пять указанных клеток: piston→golem_builder,
остальные четыре→свои placeholder. Wildcard-клетки не изменяются.
Исходный generic dust trigger ставит swaps через серверную очередь,
не имеет дополнительного vis/crystal/essentia платежа и вызывает generic
craft event с infernal furnace даже для этого blueprint. Такой событие
не следует превращать в invented golem crafting proof. Цена Salis остаётся
общим контрактом самого предмета и его существующего ritual handler.

## Разборка, placeholders и сохранение компонентов конструкции

`BlockGolemBuilder.destroy`, offsets0–264, просматривает x/z−1..1,
y0..1 от builder и заменяет найденные placeholderBars/Anvil/Cauldron/Table
их обычными **default** состояниями. Он не восстанавливает предыдущие
воду, износ, поворот или connections. В исходнике это поиск типов в объёме,
а не сохранённый список владельца; точное восстановление только своей
конструкции/loaded boundaries в современном BE — отдельное усиление.
Если ломается placeholder, его ±1 куб находит builder, запускает разборку
и заменяет builder на обычный поршень UP. Если ломается сам builder, он
дропает **обычный поршень**, остальные части остаются блоками мира.
Сломанный placeholder дропает ровно соответствующий source item сmetadata0.
Выходной инвентарь обрабатывает inherited BlockTCTile destruction отдельно;
не дублировать сохранённые placer drops при втором callback разборки.

Исходный static ignore подавляет рекурсию. Pinned bytecode содержит
`if_acmpne`/`if_acmpeq` для startpos, то есть сравнение **ссылок** BlockPos,
не `.equals`. Временная повторная установка ломаемого placeholder внутри
синхронной разборки не должна превращаться в дополнительный loot callback.
Перенос обязан явно определить защиту от рекурсии и one-time drops вместо
автоматического копирования этого reference identity артефакта.

Все части INVISIBLE, не full/opaque cube; placeholder push reaction BLOCK,
no Silk harvest, hardness2.5. Placeholder cauldron светится13. Builder
через BlockTCDevice наследует BlockTCTile hardness2, soundSTONE;
его setResistance20 даёт effective resistance12 после old setter/getter
преобразований. Placeholders
с hardness2.5 получают effective resistance2.5 вследствие old setHardness
minimum. Эти значения не должны копироваться как современные strength2.
Правый клик любой части открывает меню builder19, placeholder ищет его
в своём кубе±1; отдельный research gate на открытие меню отсутствует.

## Упаковка props, компоненты и Traits

`props` — NBTTagLong; ByteBuffer **big endian**. Bytes0..5:
material,head,arms,legs,addon,rank. Material занимает самый старший байт,
не младший. Остальные два байта в штатных предметах нулевые. Отрицательные,
неизвестные индексы, нештатный rank и хвостовые биты C2S следует отклонять
как malformed данные; сохранённые штатные long обязаны round-trip.

Trait set собирается в порядке material→head→arms→legs→addon. Добавление
trait с присутствующим opposite **удаляет opposite и не добавляет новый**.
Пары: HEAVY/LIGHT, DEFT/CLUMSY, FRAGILE/ARMORED. Это взаимное погашение,
не «последний побеждает». Компоненты агрегируются по item+damage+полный NBT
в новые копии. Начало — **2 base +1 mech**, затем arms,legs,head,addon;
строки `base`/`mech` добавляют по одному соответствующему материалу.

### Материалы, ID и собственные показатели

Все mech — simple mechanism. Health modifier прибавляется к исходным10HP;
damage используется только у FIGHTER. Числа ниже из pinned initializer.

| ID / материал | Завершённый gate | Base | healthMod / armor / damage | Traits |
|---|---|---|---|---|
| 0 WOOD | MATSTUDWOOD | greatwood plank | 6 /2 /1 | LIGHT |
| 1 IRON | MATSTUDIRON | iron plate | 20 /8 /3 | HEAVY,FIREPROOF,BLASTPROOF |
| 2 CLAY | MATSTUDCLAY | terracotta | 10 /4 /2 | FIREPROOF |
| 3 BRASS | MATSTUDBRASS | brass plate | 16 /6 /3 | LIGHT |
| 4 THAUMIUM | MATSTUDTHAUMIUM | thaumium plate | 24 /10 /4 | HEAVY,FIREPROOF,BLASTPROOF |
| 5 VOID | MATSTUDVOID | void plate | 20 /6 /4 | REPAIR |

### Головы

| ID / часть | Gates, все strict | Добавочные компоненты | Traits |
|---|---|---|---|
| 0 BASIC | MINDCLOCKWORK | clockwork mind | — |
| 1 SMART | MINDBIOTHAUMIC | biothaumic mind | SMART,FRAGILE |
| 2 SMART_ARMORED | MINDBIOTHAUMIC,GOLEMCOMBATADV | biothaumic mind,brass plate,base,white wool | SMART |
| 3 SCOUT | GOLEMVISION | clockwork mind,vision module | SCOUT,FRAGILE |
| 4 SMART_SCOUT | GOLEMVISION,MINDBIOTHAUMIC | biothaumic mind,vision module | SCOUT,SMART,FRAGILE |

### Руки

| ID / часть | Strict gate | Добавочные компоненты | Traits |
|---|---|---|---|
| 0 BASIC | MINDCLOCKWORK | — | — |
| 1 FINE | MATSTUDBRASS | simple mechanism,base | DEFT,FRAGILE |
| 2 CLAWS | GOLEMCOMBATADV | aggression module,shears2,base | FIGHTER,CLUMSY,BRUTAL |
| 3 BREAKERS | GOLEMBREAKER | diamond2,base,piston2 | BREAKER,CLUMSY,BRUTAL |
| 4 DARTS | GOLEMCOMBATADV | aggression module,dispenser2,arrow32,mech | FIGHTER,CLUMSY,RANGED,FRAGILE |

### Ноги и дополнения

| Категория, ID / часть | Strict gate | Добавочные компоненты | Traits |
|---|---|---|---|
| legs0 WALKER | MINDCLOCKWORK | base,mech | — |
| legs1 ROLLER | MINDCLOCKWORK | bowl2,leather,mech | WHEELED |
| legs2 CLIMBER | GOLEMCLIMBER | flint4,base,mech2 | CLIMBER |
| legs3 FLYER | GOLEMFLYER | levitator,brass plate4,slime ball,mech | FLYER,FRAGILE |
| addon0 NONE | MINDCLOCKWORK | — | — |
| addon1 ARMORED | GOLEMCOMBATADV | base4 | ARMORED,HEAVY |
| addon2 FIGHTER | SEALGUARD | aggression module,mech | FIGHTER |
| addon3 HAULER | MINDCLOCKWORK | leather,chest | HAULER |

WOOD+BASIC+BASIC+WALKER+NONE=props0: **greatwood plank3, simple mechanism2,
clockwork mind1**, LIGHT, **8 Machina**. ROLLER/NONE: plank2,mechanism2,
bowl2,leather1,mind1, LIGHT+WHEELED, **12 Machina**. WALKER/HAULER adds
leather1/chest1 and HAULER, итого12. ROLLER/HAULER —16. Это четыре базовых
сборки при только завершённых MINDCLOCKWORK и MATSTUDWOOD; новые material/
part research ещё не следуют из переноса машины.

## Изготовление, платежи, эссенция и опасные release quirks

В прессe один выходной slot0, штатный предмет `thaumcraft:golem` с
**только props long**, count1. Выход можно начать, если slot пуст либо
содержит такой же item+metadata+**полностью равный NBT**, count ниже max.
Имя, xp или другой лишний NBT уже существующего placer препятствуют stack.
Выход допускает automation insertion только GolemPlacer и extraction
с любой стороны; GUI slot только output. Нельзя без объяснения превратить
вставленный несовпадающий placer в потерянный предмет.

`startCraft` сразу снимает все physical components, затем ставит стоимость
`2 * finalTraitCount + sum(allComponentCounts)` единиц **Machina**.
Vis, XP, кристаллов и redstone pause здесь нет. Материалы ищутся в пяти
смежных inventory (DOWN,NORTH,SOUTH,WEST,EAST, неUP) и основном inventory
игрока. `checkCraft` сообщает только adjacent наличии; GUI дополнительно
читает player inventory. Старые ore dictionary совпадения BASEORE не
эквивалентны произвольным современным substitutes.

Исходный InventoryUtils сперва требует **всю** стоимость каждого
агрегированного компонента либо в adjacent суммарно, либо у игрока;
разделить один компонент 2+1 между chest/player при цене3 нельзя. Commit
при этом может снять часть из adjacent и затем **полную** стоимость из
player, если adjacent недостаточны; это confirmed double-debit bug
(`consumeItemsFromAdjacentInventoryOrPlayer`, source48–65/pinned method).
Современные атомарные reservations без такого излишнего списания — явная
адаптация, а не скрытое изменение research или nominal recipe. Не делать
read-only preview платящим, не позволять callbacks consume twice.

Сервер увеличивает transient ticks каждый tick; **каждый пятый** при cost>0
и golem>=0 использует bufferedEssentia или draw, сбрасывает buffer, cost−1.
Тот же последний tick создаёт placer. Connectable/input — DOWN и четыре
горизонтали; UP запрещён, output отсутствует. Suction Machina128, пока
cost>0/golem>=0, иначе0; minimum0; публичные stored type/amount=null/0.
draw проходит VALUES **DOWN,UP,NORTH,SOUTH,WEST,EAST**, connectable helper
отсеиваетUP. Попавшийся connectable peer, у которого canOutputTo=false,
**немедленно прекращает весь поиск**, вместо продолжения к следующему.
Иначе peer suction строго<128 и успешный takeMachina1. Нет проверки его
getEssentiaType, собственного redstone состояния или minimum suction.

Low-level addEssentia принимает один буфер, если активен/пуст и aspectMachina;
исходник **игнорирует amount и face**, даже amount0/отрицательное. Hardened
nonpositive/null/forbidden-face rejection следует записать адаптацией,
если перенос её делает. bufferedEssentia и ticks не сериализуются;
сохраняются golem/cost/mcost и output inventory. Поэтому reload теряет
одну уже внесённую, ещё не зачтённую buffer единицу, сдвигает cadence;
сохранённый golem>=0 заново выводит props/components. Плохое props при
load сбрасывает работу, cost0/golem−1.

**Выход, занятый в последний tick:** cost сначала становится0; если
выход уже full/неравный, placer не выпускается, complete=false и golem
остаётся≥0. Последующие ticks вообще не входят в cost>0 ветку, даже если
выход освободить: исходное paid задание навсегда зависает, suction0.
Это подтверждено update offsets39–52 и88–239, не decompiler ошибка.
Paid pending-output retry и сохранение такого задания — полезное, но
обязательное явно отмеченное исправление; не утверждать, что retry был
оригинальной механикой. Нельзя снимать ещё Machina/компоненты на retry.

## GUI, модель и размещение результата

Штатный GUI208×224, исходный `gui_golembuilder.png`; output(160,104),
inventory3×9 с(24,142), hotbar(24,200), Machina cost справа, bar46×6.
При cost>0 GUI отключает selectors/start; preview показывает finalTraits,
component counts и half-unit health/armor/damage icons. Исходный OBJ
`models/block/golembuilder.obj`, texture `textures/blocks/golembuilder.png`,
отдельная группа `press` опускается `sin(angle)*0.625`.
WavefrontObject читает V как `1-v`, но исходный renderer дополнительно
переворачивает текстурную матрицу (`scale(1,-1,1)`). В итоге пресс использует
исходный V. Отдельный `ObjMesh.getBlock` сохраняет это преобразование;
обычный загрузчик моделей существ продолжает использовать `1-v`.
Лавовая поверхность учитывает угловую систему UtilsFX: quad0..1,
scale0.625, translate(-0.3125,0.625,1.3125), rotateX(-90). Её итоговые
границы x[-0.3125,0.3125], y0.625, z[0.6875,1.3125]. Современная
поверхность использует спрайт лавы из block atlas и full-bright lighting;
свет и частицы являются адаптациями современного renderer API.

Client press angle+6 до90 при cost>0 **и golem>0**; id0 деревянный basic
walker/none поэтому в оригинале **не запускает animation**, хотя сервер
его производит. После завершения angle−3. Vent/sizzle с60, idle pressed
vents сchance1/8. Визуальное исправление id0 анимации требует отдельного
обозначения, если принято; renderer binding сам по себе её не доказывает.

Зеркало Java ошибочно пишет fragile `hh *= (int)0.75`: pinned Gui offsets
1477–1484 и Entity.updateEntityAttributes31–38 выполняют **double *0.75,
затем cast результата**. HP=trunc((10+healthMod)*.75 приFRAGILE)+rank;
armor=trunc(max(materialArmor*1.5,armor+1)) приARMORED, затемtrunc(*.75)
приFRAGILE. Damage0 безFIGHTER; fighter materialdamage, brutal max(*1.5,+1),
entity добавляет rank*.25; GUI показывает половину значений без rank.
Не переносить нулевые fragile HP/armor из ошибки декомпиляции.

`ItemGolemPlacer.onItemUseFirst` требует solid material клика, server
pos.offset(side), edit permission; создаёт Owned/validspawn golem, owner
UUID, props и optional xp, initial spawn/home. Только успешный spawn
снимает один предмет внеcreative. Клиент возвращаетPASS. Исходник не
добавляет research/печать requirement и не проверяет воздух следующего
блока/collision перед spawn; modern loaded/collision/Forge callback guards
должны обозначаться адаптациями. Выход пресса — предмет **для настоящего
размещения сущности**, а не spawn egg или декоративный block.

Изготовление placer, модель и базовые атрибуты не доказывают перенос
seal task scheduling, owner following, inventories, combat/parts functions,
rank XP, bell pickup и всех traits. Если этот срез оставляет AI/печати
нерабочими, документация и предмет/GUI должны честно показывать эту границу;
не выдавать NoAI catalogue entity за полноценного исходного голема.

## Воспроизводимая локальная проверка источников

В workspace `work/golem-022-*-javap.txt` сохранены builder,block,properties,
recipes,dust,placeholder,inventory,gui,placer,entity,capabilities и vanilla
facing outputs. Эти диагностические файлы не входят в распространяемый
source ZIP. Главные source paths: common/tiles/crafting/TileGolemBuilder;
common/blocks/crafting/BlockGolemBuilder; common/blocks/misc/BlockPlaceholder;
common/lib/crafting/DustTriggerMultiblock и Matrix; common/golems/
GolemProperties,ItemGolemPlacer,EntityThaumcraftGolem; client/gui/
GuiGolemBuilder; common/lib/utils/InventoryUtils; assets/thaumcraft/research/
golemancy.json. В0.22 подключены полный исходный платёж Mind,
формирование/производство/меню пресса и исходные ресурсы GUI/деталей.
Финальные645 серверных тестов,6 сцен пресса и25 регрессионных сцен
прошли; обе клиентские JVM работали на скрытых рабочих столах без
переключения input desktop. Сборка, просмотр31 снимка, границы фикстур
и артефактов записаны отдельно в [VALIDATION.md](../../VALIDATION.md).
