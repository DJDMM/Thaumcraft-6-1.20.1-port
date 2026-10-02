# Thaumcraft 6: мир, големы и Eldritch

Срез исследования: 30 сентября 2026. Цель совместимости — **Thaumcraft 6.1.BETA26, Minecraft 1.12.2**, затем перенос поведения на Forge 1.20.1. Материалы Thaumcraft 4/5 и дополнений не задают требования этого документа.

Полностью прочитаны **59 Markdown-страниц** Comprehensive Guide в разделах World Generation, Fundamentals, Golemancy и Eldritch. Это весь текст этих четырёх разделов, включая короткие страницы, заголовочные страницы и комментарии HTML. Это не утверждение о прочтении всех существующих руководств интернета или проверке всех классов мода. Перечень файлов сохранён в `work/tc6-read-trees.json` рабочего каталога.

Руководство используется как карта сценариев игрока; правила и спорные числа сверяются с кодом. Основные источники: [руководство, фиксированная ревизия](https://github.com/xiaoschannel/Minecraft-Guides-Thaumcraft6/tree/cd14373456bfa3090663eb9dbf804f369313a7d7/pages), [декомпилированный TC6, фиксированная ревизия](https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/tree/954022bb777b7546281fb36df8522f0ba6b43f81), [оригинальный BETA26 JAR, файл 2629023](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2629023). Текст ниже — самостоятельная спецификация, а не перепечатка руководства.

## 1. Мир и естественные ресурсы

Игрок исследует мир ради вис-кристаллов, янтаря, киновари, магических деревьев и существ. Magical Forest — отдельный биом с повышенным потенциалом ауры, характерной растительностью и Pech. Greatwood имеет обычные варианты и варианты с паутиной, пещерными пауками и добычей. Silverwood связан с shimmerleaf и поддержкой вис; листья и саженцы должны сохранять собственные правила, а не быть перекрашенным дубом. Shimmerleaf даёт quicksilver, cinderpearl — blaze powder, vishroom вызывает тошноту при контакте. В BETA26 есть также генерация малых Crimson-порталов; пустыня удобна для поиска, но совет «ищи только в пустыне» не является универсальным правилом спавна. Для переноса распределение по высоте и биомам придётся явно согласовать с расширенной высотой мира 1.20.1. Источники: [генератор мира][worldgen], [биомы][biomes], [растения][plants].

Часть строк в гайде чрезмерно обобщена. Например, утверждение о выпадении quicksilver из «деревьев» не задаёт точную таблицу добычи каждого блока; перед реализацией нужно сверить отдельно древесину, листья и shimmerleaf. Аналогично «taint превращает любой блок» неверно как техническое условие: проверяются материал, твёрдость, окружение и разрешённая область распространения.

Кристаллы — возобновляемая часть ауры, не просто руда. У кластера есть `size=0..3` и `gen=1..4`; добыча даёт `size+1` кристаллов, Silk Touch не сохраняет блок. Он прикрепляется к каменной поверхности, в том числе сверху и сбоку; потеря опоры приводит к выпадению. Шесть первичных вариантов дополняются flux-кристаллом. На случайном тике дополнительная проверка проходит с вероятностью `1/(3+gen)`. При вис ≤10 кластер уменьшается и отдаёт 10 вис; при вис >base+10 он может расти за 10 вис или распространяться при соблюдении ограничений поколения. Для flux-кристалла та же схема использует flux. Это важно: он не бесконечный бесплатный очиститель — при нехватке flux возвращает его. Преобразование обычного кристалла во flux-кристалл и точный инициатор такого преобразования ещё требуют отдельной проверки, их нельзя выводить только из этого класса. [BlockCrystal, рост и добыча][crystals].

## 2. Аура и flux

Хранить отдельно для каждого измерения и чанка: базовый потенциал, текущий vis и текущий flux. Flux — загрязнение окружающей ауры; warp — состояние игрока. Это разные системы, их нельзя объединять в одну шкалу.

Начальная база: среднее пяти биомных коэффициентов ×500×`(1+Gaussian×0.1)`, затем ограничение 0..500. Вис начинается с базы, flux — с нуля. Оригинальные биомные коэффициенты и выбор соседних проб нужно переносить отдельно от механизма сохранения. [AuraHandler:166–185][aurabase].

Оригинал обрабатывает ауру примерно раз в секунду отдельным потоком. В новом Forge вычисление следует выполнять безопасно на сервере, сохраняя результат правил; архитектурную гонку старого мода воспроизводить не требуется. Лунные таблицы восьми фаз:

| Величина | Значения по индексу фазы 0..7 |
|---|---|
| Восстановление vis за шаг | 0.25, 0.15, 0.10, 0.05, 0, 0.05, 0.10, 0.15 |
| Множитель допустимой базы | 1.15, 1.05, 1, 0.95, 0.85, 0.95, 1, 1.05 |
| Добавка flux в ветках загрязнения | 0.25 минус восстановление vis |

Из четырёх соседей выбираются подходящие получатели: vis может передаваться по 1 за шаг, если сосед ниже 75% текущего vis и его сумма vis+flux ниже лунной базы; flux перетекает по 1 при текущем значении выше `max(5,base/10)` и соседнем ниже текущего/1.75. Восстановление учитывает **сумму** vis+flux, поэтому загрязнение вытесняет полезную ёмкость. При flux >75% лунной базы на каждом шаге появляется вероятность запроса разлома `flux/5000`. Это не жёсткое правило «при 75% разлом немедленно появляется». [AuraThread:34–152][aurathread].

При порте нужны проверки сохранения и границ чанка, недопущения отрицательных значений, отсутствия генерации/загрузки соседнего мира ради диффузии и зависимости от времени/лунной фазы. Сохранить параметры случайности, но не требовать совпадения конкретного случайного мира между разными версиями Minecraft.

## 3. Разломы, стабилизация и очистка

При создании разлома выбирается позиция внутри загрязнённого чанка; в обычном измерении берётся поверхность, в измерении без небесного света применяется отдельный поиск свободной точки. Разлом не создаётся рядом с существующим в радиусе 32. Начальный размер равен целой части `sqrt(flux×3)` при значении корня >5; из ауры сразу списывается количество flux, равное корню. Поэтому один порог flux не гарантирует появление. Соседние игроки получают событие `f_toomuchflux`. [EntityFluxRift:290–324][riftspawn].

Разлом уничтожает пересекаемые разрушаемые блоки, повреждает существ и уничтожает предметы, попавшие в него. Его форма вычисляется из seed и размера, а не заменяется одной статичной моделью. Каждые 120 тиков стабильность падает на 0.2. Каждые 600 тиков возможен рост на 1 за `sqrt(size×2)` flux, если size<100 и состояние не VERY_STABLE. При отрицательной стабильности возможны события. [EntityFluxRift:190–269][rifttick].

| Состояние | Значение стабильности |
|---|---|
| VERY_STABLE | >50 |
| STABLE | от 0 до 50 включительно |
| UNSTABLE | больше −25 и меньше 0 |
| VERY_UNSTABLE | ≤−25 |

Сама стабильность ограничена −100..100. Взвешенные события имеют веса 50/10/20/20/1: wisp, большой taint seed, infectious vis exhaustion, flux-cloud заклинание, самопроизвольное схлопывание. Это веса выбора, а не независимые проценты каждого события. Seed-событие запрещено рядом с существующим seed и может полностью заменить разлом. Для wisp используется гауссово смещение, поэтому описание гайда как строго ограниченного куба 10×10×10 неточно. Эффект болезни в коде создаётся на 3000 тиков с amplifier=2; номер уровня из текста гайда не переносим без сверки. [События, пороги и веса][riftevents].

Stabilizer имеет буфер 15. Восстанавливая единицу энергии раз в 20 тиков, он загрязняет ауру на 0.25. Попытки воздействия проходят каждые 5 тиков; на одну успешную тратится 1 энергия и разлом получает +0.125 стабильности. Поиск идёт в расширенном на 8 блоков объёме. Это платная поддержка стабильности, не очистка окружающего flux. [TileStabilizer:36–90][stabilizer].

Causality Collapser переводит разлом в схлопывание. В процессе размер уменьшается каждый тик, а в ауру возвращается случайно vis или flux. В конце число void seeds равно `q=floor(sqrt(maxSize))`; вероятность primordial pearl — `q%`, начальная повреждённость выпавшей жемчужины 4..7. Для size=100 получаем 10 seeds и 10% вероятности, а не фиксированные 1/6. Очень крупные стартовые разломы могут иметь иной размер; максимальный размер роста 100 не доказывает абсолютный предел начального размера. При нестабильном схлопывании есть побочные эффекты и warp, а VERY_STABLE избегает этих веток. [EntityFluxRift:459–500][riftcollapse].

Flux Condenser превращает загрязнение ауры в транспортируемую Vitium-эссенцию, расходуя другую эссенцию. Он требует connected lattice сверху, ограничения высоты/дальности и обслуживания забивающихся фильтров; дерево соединений влияет на эффективность. В коде один успешный цикл снимает 1 flux и создаёт единицу выходной эссенции, буферы ограничены 100; интервал зависит от эффективного числа решёток и ограничен снизу 5 тиками. Это отдельная сеть/машина, не кнопка мгновенной очистки чанка. Перед реализацией полностью проверить обход связной решётки, стоимость, отказ за забитой решёткой и замену фильтра. [TileCondenser:58–203][condenser].

Зависимости исследований: FLUXCLEANUP требует FLUX, `f_toomuchflux`, VISBATTERY и наполненный Vitium-флакон; RIFTCLOSER требует FLUXRIFT, INFUSION, VISBATTERY и знания. Нельзя разрешать их сразу из одного скана произвольного предмета. [basics.json:297–339][basicend].

## 4. Taint и warp

Taint поддерживается сетью живых seeds. Конфигурация по умолчанию задаёт радиус 32 и коэффициент распространения 100; фактическая вероятность умножается на `0.001+2×fluxSaturation`. Распространение проверяет наличие seed, ограничения материала и твёрдости. Большая часть преобразований расходует 0.01 flux. На краю поддерживаемой области при наличии ≥5 flux может возникнуть новый seed за 5 flux. Поэтому убийство одного seed не очищает территории остальных. [TaintHelper:47–162][taint], [ModConfig:329–380][worldconfig].

Нужны отдельные жизненные циклы fibres, soil, rock, logs, crust, goo и существ: поддержание, распространение, затухание без seed, контактные эффекты, появление новых существ. Пример «грязь вернулась, камень стал porous stone» следует реализовывать через таблицу конкретных блоков, не восстанавливая произвольный старый blockstate автоматически. Равным образом не следует спавнить всех tainted mobs прямо из rift: краткие страницы гайда употребляют «из разломов» как описание общей цепочки заражения, а непосредственные источники у существ разные.

Warp хранится у игрока тремя независимыми числами: TEMPORARY, NORMAL, PERMANENT; стандартные операции ограничивают каждое 0..500. Есть также отдельный счётчик активности событий и warp от надетых/используемых вещей. Каждые 2000 тиков, если не включён wussMode и нет Warp Ward, проверяется событие; временный warp уменьшается на 1 при такой проверке. Вероятность/тяжесть учитывают счётчик, сумму warp и снаряжение. Снижение активности событий не означает исчезновение permanent warp. [PlayerWarp][warpstore], [PlayerEvents:328–330][warptiming], [WarpEvents:47–64][warpevents].

События включают ложные звуки и сообщения, vis exhaustion, unnatural hunger, sun scorned, размытие/слепоту, death gaze, пауков, туман с guardians и Crimson-портал. Лечимость разных эффектов различается; unnatural hunger явно допускает rotten flesh и zombie brains. В ветке успешного события при NORMAL+PERMANENT >10 открывается подсказка bath salts, >25 ELDRITCHMINOR, >50 ELDRITCHMAJOR. Эти внутренние маркеры сами по себе не заменяют полноценное открытие Eldritch-вкладки через книгу и void seed. [WarpEvents:69–233][warpeffects].

Каталог существ для порта включает Angry/Giant Angry Zombies, Pech, wisps, fire bats, eldritch guardians, Crimson cultists и оба зарегистрированных портала, taint seeds/prime seeds, taintacles, swarms/crawlers и thaumic slimes. У каждого требуются своя модель, AI, loot, сканы и реальные условия появления. Наличие зарегистрированного класса не доказывает естественный спавн или достижимость в survival. [ConfigEntities][entities].

**Расхождение по thaumic slime:** руководство описывает слияние до 100 HP/100 damage. В исследованном классе BETA26 такой логики нет: крупный slime выстреливает меньшим, уменьшается, после смерти делится на size штук размера 1; сила атаки size+1. Рост от flux goo находится в BlockFluxGoo. Слияние из текста гайда не следует переносить как подтверждённое правило TC6 без дополнительного доказательства. [EntityThaumicSlime:101–202][slime], [BlockFluxGoo][goo].

## 5. Големы: изготовление и параметры

Разблокировка GOLEMANCY требует ветвей ARTIFICE, AUROMANCY и INFUSION, знания по Golemancy/Basics и маркер `f_golem`, получаемый сканом подходящего голема/конструкта. Базовый разум MINDCLOCKWORK связан с ESSENTIASMELTER и HEDGEALCHEMY. Он открывает пресс и ведёт к CONTROLSEALS; дальнейшие материалы/детали требуют собственных исследований. Летающие ноги требуют LEVITATOR, но левитатор не является обязательным предварительным условием для **каждого** простого деревянного голема. [basics.json:228–243][golemunlock], [golemancy.json][golemresearch].

Конструктор выбирает материал, голову, руки, ноги и дополнение. Ключевой результат — объединение traits с взаимным погашением противоположностей: CLUMSY/DEFT, HEAVY/LIGHT, FRAGILE/ARMORED. Части должны храниться вместе с рангом, владельцем, цветом, точкой дома и опытом. [GolemProperties:38–162][golemprops], [EnumGolemTrait][traits].

| Материал | Базовое здоровье, HP | Броня, единицы | Урон при FIGHTER до иных модификаторов | Собственные traits |
|---|---:|---:|---:|---|
| Greatwood | 16 | 2 | 1 | LIGHT |
| Iron | 30 | 8 | 3 | HEAVY, FIREPROOF, BLASTPROOF |
| Clay | 20 | 4 | 2 | FIREPROOF |
| Brass | 26 | 6 | 3 | LIGHT |
| Thaumium | 34 | 10 | 4 | HEAVY, FIREPROOF, BLASTPROOF |
| Void | 30 | 6 | 4 | REPAIR |

Материалов **шесть**, в коротком списке гайда пропущен Void. Цифры здоровья из гайда обычно выражены сердцами: 8 сердец — это 16 HP; броня тоже может отображаться половиной числовых единиц. Не путать представление GUI с внутренними параметрами. Без FIGHTER базовое значение атаки равно нулю. [Регистрация материалов][golemmats], [атрибуты сущности][golemstats].

**Проверено по оригинальному JAR:** декомпилятор в строках 173 и 249 ошибочно записал `value *= (int)0.75`. В байткоде идут `i2d`, `ldc2_w 0.75`, `dmul`, `d2i`: правильно `value=(int)(value*0.75)`. Следовательно FRAGILE уменьшает здоровье и броню на четверть с округлением вниз, а не обнуляет их. Сверка: `EntityThaumcraftGolem.updateEntityAttributes`, offsets31–38; `func_70658_aO`, offsets58–65. Вывод javap сохранён в `work/golem-javap.txt`. Это практическое доказательство, почему репозиторий нельзя механически перекомпилировать и считать точным исходником.

Головы дают SMART/SCOUT и иногда FRAGILE; руки — DEFT, либо бой/BRUTAL/CLUMSY, либо BREAKER, либо RANGED; ноги — обычная ходьба, колесо, лазание, полёт. Дополнения усиливают защиту, дают бой или HAULER. Базовая рабочая область имеет радиус 32, со SCOUT —48. Все големы восстанавливают 1 HP каждые 100 тиков, REPAIR сокращает интервал до40. SMART позволяет повышать ранг до10; следующий ранг требует `(rank+1)^2×1000` опыта. Ранг добавляет здоровье, скорость и урон FIGHTER. [EntityThaumcraftGolem:170–193,284–333,617–635][golemstats].

Пресс расходует предметы из доступных инвентарей/игрока и Machina-эссенцию. Стоимость: число предметов-компонентов +2×число итоговых traits. Во время изготовления расходуется до1 Machina каждые5тиков; suction=128, вход с горизонтальных сторон и снизу, выходной слот содержит готовый предмет голема с конфигурацией. В новом сетевом коде обязательно проверять разрешённость деталей и исследования на сервере, наличие компонентов, занятость пресса и вместимость выхода до списания. [TileGolemBuilder:120–315][golembuilder].

## 6. Печати и система задач

Печать — адрес в мире **позиция+грань**, а не полноразмерный блок с постоянно видимой моделью. Bell показывает печати и рабочие области, открывает настройки и позволяет снять печать. Настройки включают область, фильтры, приоритет, владение/блокировку, цвет и redstone. Големов можно красить; предметный перенос голема должен сохранять необходимые свойства без дублирования предметов. [SealEntity][sealentity], [ItemGolemBell][bell].

В BETA26 зарегистрировано **16** рабочих печатей: 11 базовых и5 расширенных. В гайде указан старый/неполный счёт15 и пропущена Advanced Breaker. [ConfigItems:310–325][sealregistration].

| Печать | Назначение | Required | Forbidden |
|---|---|---|---|
| Collect | Поднять подходящие выпавшие предметы в области | — | CLUMSY |
| Store | Доставить в инвентарь либо выгрузить у точки | — | CLUMSY |
| Empty | Активно извлекать подходящие предметы из инвентаря | — | CLUMSY |
| Provide | Выдать предмет по запросу другой задачи/логистики | — | CLUMSY |
| Stock | Поддерживать заданный запас в инвентаре | — | CLUMSY |
| Breaker | Разрушать разрешённые блоки | BREAKER | — |
| Guard | Нападать на подходящие цели области | FIGHTER | — |
| Butcher | Убивать взрослых животных с сохранением пары | FIGHTER, SMART | — |
| Harvest | Собирать урожай, управлять пересадкой/поставкой семян | DEFT, SMART | — |
| Lumber | Разбирать связанные брёвна дерева | BREAKER, SMART | — |
| Use | Применять действие/предмет к цели | DEFT, SMART | — |

Расширенные Collect/Store/Empty добавляют требование SMART, Guard требует FIGHTER+SMART, Breaker — BREAKER+SMART. Параметры отличаются между классами, не следует автоматически давать все опции каждому виду. Источник — [классы печатей][seals], в особенности методы `getRequiredTags`/`getForbiddenTags` и их наследование. Гайд ошибочно приписывает Empty условие «только по запросу»; код Empty сам создаёт задачу каждые20тиков при найденном предмете, а Provide реализует запросы. [SealEmpty:43–105][empty].

TaskHandler хранит задачи по измерениям; есть задачи на блок и сущность, срок жизни, резервирование, назначенный голем, suspension/completion. Сортировка учитывает `distanceSquared-priority×256`. AI должен проверить владельца заблокированной печати, traits, специальный предикат задачи, радиус дома и доступность пути; потом зарезервировать задачу и выполнить её, освободив резерв при прерывании/смерти. Нельзя просто каждый тик телепортировать содержимое между контейнерами, минуя голема, или позволять нескольким големам оплатить/забрать один результат. [TaskHandler][tasks], [AIGoto][gototask], [AIGotoBlock][gotoblock].

Brain in a Jar — накопитель XP и вспомогательный объект для theorycrafting. Это не бесплатная кнопка Eldritch-исследований. Его радиус подбора/ёмкость/выдача XP ещё не сверены в этой работе, и должны браться из TileJarBrain перед реализацией.

## 7. Eldritch: что входит в базовый TC6

Подтверждённая цепочка: скан Crimson cultist → скрытая запись CrimsonRites → сдача экземпляра книги → UNLOCKELDRITCH при открытой AUROMANCY, знаниях Basics/Eldritch и скане void seed → BASEELDRITCH при выполненной Metallurgy. Сканирует общий класс EntityCultist с наследниками: заявление «обязательно отсканировать оба вида» не является точным условием этого флага. Void seed можно получить из схлопывания разлома; соответственно реальная цепочка включает инфузию/Vis Battery/Collapser. События высокого warp помогают встретить культ, но сами не завершают эту цепь. [ConfigResearch:164–173,223][researchscans], [basics.json:247–279][eldritchunlock].

В `eldritch.json` BETA26 **пять** записей: BASEELDRITCH (void metal и обычное снаряжение), VOIDSIPHON, VOIDSEERPEARL, VOIDROBEARMOR, PRIMALCRUSHER. Они имеют зависимости от других ветвей: Infusion, Curiosity Band, Fortress Armor, Elemental Tools соответственно. Исследования требуют Observation/Theory и добавляют warp. Это конечный объём штатной вкладки этого релиза; не подменять его расширенной вкладкой аддона. [eldritch.json][eldritchjson].

Void Siphon проверяет видимые живые разломы размером ≥2 на расстоянии8, каждую секунду набирает по `floor(sqrt(size))` прогресса с каждого, уменьшает стабильность на `sqrt(size)/15` и с вероятностью1/33 уменьшает размер. 2000 прогресса дают1 seed; полный выход останавливает накопление. Следовательно сифон не просто «бесследно высасывает flux из чанка» — он взаимодействует с сущностью разлома и дестабилизирует её. [TileVoidSiphon:23–106][siphon].

Voidseer’s Pearl даёт `floor(min(permanentWarp,100)/100×25)` процентов vis discount и `discount/5` gear warp. Void Thaumaturge Robes имеют другой закон: по5% vis discount и3 gear warp на часть; саморемонт1 прочности в секунду, у капюшона функции очков. Поэтому зависимость скидки роб от уровня warp, описанная в гайде, не подтверждается BETA26-кодом. [ItemVoidseerCharm:44–59][voidseer], [ItemVoidRobeArmor:88–114,202–204][voidrobes].

Primal Crusher объединяет добычу киркой/лопатой и область3×3, с саморемонтом. Void-инструменты/броня тоже самовосстанавливаются и имеют warp-свойства. Точные материалы ремонта, урон, цена инфузии и режимы sneak должны проверяться на уровне конкретного предмета, а не выводиться из общего описания «топовый инструмент».

**Граница незавершённого:** исходник содержит Greater Crimson Portal, боссов, Eldritch Eye и ссылки на `dimensionOuterId=-42`, но поиском по репозиторию не обнаружена регистрация отдельного Outer Lands измерения/WorldProvider. Наличие этих остатков не доказывает доступный в survival данж. Поэтому Outer Lands, открытие обелиска глазами и сюжетные данжи не входят в подтверждённый survival-объём этого документа. Добавления аддонов, включая Thaumic Augmentation, нужно маркировать отдельно и не импортировать в базовый TC6 по случайному гайду. Окончательную достижимость остаточных сущностей следует подтвердить на чистом оригинальном JAR; до этого статус — зарегистрировано/сохранилось в коде, путь получения не подтверждён. [ConfigEntities:70–84][entities], [ModConfig:375][worldconfig], [ThaumcraftWorldGenerator:54–90][worldgen].

## 8. Отличия текущего порта 0.2.0-dev и порядок работы

В текущем проекте есть каркас сохранения/диффузии ауры, руда, первичные кристаллы и два дерева; это ещё не мир TC6 целиком. Нет полноценного Magical Forest, связанных с ним существ и растений, жизненного цикла роста кристаллов, разломов, taint, warp, конденсатора и стабилизатора. Некоторые материал/предметные ID существуют раньше соответствующей механики: наличие void seed/ingot в creative не означает готовый Eldritch. Нет пресса, модульных големов, печатей и системы задач. Временные PORT-исследования не реализуют оригинальные зависимости перечисленных ветвей.

Рекомендуемая последовательность реализации:

1. Зафиксировать настоящую модель исследований/сканов/знаний, а также серверные проверки и миграцию сохранений.
2. Дополнить ауру ростом кристаллов, flux-инструментами и жизненным циклом разлома; затем taint и warp. Тестировать получение void seeds через штатный путь.
3. После сети эссенции и инфузии реализовать пресс, детали големов и устойчивую основу задач. Сначала Collect→Store и Empty→Store, затем Provide/Stock и работа с блоками/существами.
4. Включить Eldritch по реальным зависимостям и только затем void-пресс/снаряжение/сифон. Остаточные данжи рассматривать отдельно.

Приёмочные сценарии: сохранение ауры между перезапусками и измерениями; постепенный рост/схлопывание разлома; очистка загрязнения с реальными расходами; taint с несколькими seed; раздельное сохранение трёх типов warp; выпуск/подбор голема без потери конфигурации; два голема без двойного выполнения одной задачи; недоступная цель без вечного резервирования; сохранение breeding pair; ограничения владельца/цвета/redstone; невозможность пройти Eldritch без книги/скана/знаний. Это будущие проверки, не заявление, что они уже пройдены портом.

## 9. Непроверенные области и обнаруженные неоднозначности

- Не подтверждены игрой на оригинальном JAR все пути появления редких существ, остаточных боссов и порталов; регистрации недостаточно.
- Для worldgen не зафиксированы все точные частоты, loot и биомные исключения. Современная генерация потребует явного адаптационного решения.
- Не выполнено полное сопоставление всех деталей траекторий/коллизий/AI и всех рецептов големов. Карта классов и стадий дана для дальнейшей реализации.
- Нельзя копировать подозрительные арифметические выражения декомпиляции: случай FRAGILE подтверждён как ошибка через JAR.
- Гайд не является эталоном чисел: исправлены неполный перечень материалов/печатей, путаница Empty/Provide, оценка pearl chance, формула скидки Void Robes. Цифры HP сначала переводить из сердец в единицы, а не объявлять ошибкой.
- Подробности рандомных эффектов, например дистанционная формула при схлопывании (`distanceSq` в декомпиляции), требуют bytecode/игровой проверки перед буквальным переносом.

[worldgen]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/world/ThaumcraftWorldGenerator.java#L54
[biomes]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/tree/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/world/biomes
[plants]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/tree/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/blocks/world/plants
[crystals]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/blocks/world/ore/BlockCrystal.java#L87
[aurabase]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/world/aura/AuraHandler.java#L166
[aurathread]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/world/aura/AuraThread.java#L34
[riftspawn]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/entities/EntityFluxRift.java#L290
[rifttick]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/entities/EntityFluxRift.java#L190
[riftevents]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/entities/EntityFluxRift.java#L326
[riftcollapse]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/entities/EntityFluxRift.java#L459
[stabilizer]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/devices/TileStabilizer.java#L36
[condenser]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/devices/TileCondenser.java#L58
[basicend]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/research/basics.json#L297
[taint]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/blocks/world/taint/TaintHelper.java#L47
[worldconfig]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/config/ModConfig.java#L329
[warpstore]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/capabilities/PlayerWarp.java#L39
[warptiming]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/events/PlayerEvents.java#L328
[warpevents]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/events/WarpEvents.java#L47
[warpeffects]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/events/WarpEvents.java#L69
[entities]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/config/ConfigEntities.java#L70
[slime]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/entities/monster/EntityThaumicSlime.java#L101
[goo]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/blocks/world/taint/BlockFluxGoo.java#L47
[golemunlock]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/research/basics.json#L228
[golemresearch]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/research/golemancy.json#L71
[golemprops]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/golems/GolemProperties.java#L38
[traits]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/golems/EnumGolemTrait.java#L7
[golemmats]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/golems/GolemProperties.java#L199
[golemstats]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/golems/EntityThaumcraftGolem.java#L170
[golembuilder]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/crafting/TileGolemBuilder.java#L120
[sealentity]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/golems/seals/SealEntity.java
[bell]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/golems/ItemGolemBell.java
[sealregistration]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/config/ConfigItems.java#L310
[seals]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/tree/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/golems/seals
[empty]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/golems/seals/SealEmpty.java#L43
[tasks]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/golems/tasks/TaskHandler.java#L19
[gototask]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/golems/ai/AIGoto.java#L130
[gotoblock]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/golems/ai/AIGotoBlock.java#L35
[researchscans]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/config/ConfigResearch.java#L164
[eldritchunlock]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/research/basics.json#L247
[eldritchjson]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/research/eldritch.json#L1
[siphon]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/crafting/TileVoidSiphon.java#L23
[voidseer]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/items/baubles/ItemVoidseerCharm.java#L44
[voidrobes]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/items/armor/ItemVoidRobeArmor.java#L88
