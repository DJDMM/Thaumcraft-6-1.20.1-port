# Thaumcraft 6: исследования, сканирование и ауромантия

Спецификация для переноса **Thaumcraft 6.1.BETA26, Minecraft 1.12.2 → Forge 1.20.1**. Составлена 30 сентября 2026 года. TC4 и TC5 не использовались как источники механик.

Прочитаны полностью **44 Markdown-файла** разделов `03.getting-started` и `05.auromancy` гайда Mephie/Tyith. Проверяемый список находится в `work/tc6-read-scan.json`. Фактические условия и численные параметры ниже опираются преимущественно на TC6-код и данные книги, а не на советы по прохождению. Это тематическая спецификация, не утверждение, что проверена каждая строка всех классов мода.

Приоритет доказательств: официальный релиз [6.1.BETA26, файл 2629023](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2629023) → его игровые данные/байткод → [зафиксированный исходный репозиторий](https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/tree/954022bb777b7546281fb36df8522f0ba6b43f81) → [зафиксированный гайд TC6](https://github.com/xiaoschannel/Minecraft-Guides-Thaumcraft6/tree/cd14373456bfa3090663eb9dbf804f369313a7d7). Репозиторий представляет восстановленный код; подозрительные выражения нельзя автоматически считать авторским замыслом. Формула Exchange отдельно проверена через `javap -c -p` в оригинальном JAR.

## Что представляет собой исследование

Таумономикон — дерево связанных исследований с категориями, координатами узлов, метаданными оформления, стадиями и дополнениями. Открытие записи, завершение стадии и завершение всей записи — разные состояния. Ключ `EXAMPLE@2` означает достижение стадии 2, а не обязательное завершение записи. Стадии начинаются с 1; исследование завершено, когда сохранённая стадия превышает число его стадий. Помимо записей книги есть служебные открытия: аспекты, сканы, факты путешествий, попадания снарядов и созданные вещи. [Модель знания][knowledge-model], [переходы стадий][progress].

При переносе нужны пять разных требований:

| Тип | Проверка | Что расходуется |
|---|---|---|
| `parents` | Родительские исследования/стадии разрешают доступ | Сам факт открытия не расходуется |
| `required_research` | Открытые аспекты, сканы и специальные события | Не расходуются |
| `required_craft` | Сервер ранее зарегистрировал изготовление нужного предмета | Сам предмет сдавать не нужно; `/give` не заменяет факт крафта |
| `required_item` | Подходящие предметы находятся у игрока | Предметы расходуются при завершении |
| `required_knowledge` | Достаточно знания нужного типа и категории | Знание расходуется |

Оригинальный обработчик использует отдельные маркеры крафта `[#]…`, проверяет требуемые исследования и вычитает знание в сырых единицах. В порте необходима **атомарная** серверная операция: сначала проверить все требования и родительские связи, затем списать ресурсы и сменить стадию. В восстановленном обработчике списание предметов расположено перед некоторыми последующими проверками — этот порядок не надо повторять как потенциальный способ потерять ресурсы. [Обработчик завершения][stage-payment].

`HIDDEN` не означает «ещё не реализовано»: это штатное скрытое исследование TC6. `SPIKY`, `ROUND`, `REVERSE`, координаты, родители и дополнения должны сохраняться как данные. Нынешние `PORT_*` и флаг `supported` — временная модель порта, не оригинальная система.

## Начальная последовательность и открытия разделов

1. Подобрать кристалл эссенции: появляется `!gotcrystals`. При пробуждении выдаётся журнал Strange Dreams и `!gotdream`. В конфигурации оригинала есть обход сна `noSleep`. Подбор Таумономикона отдельно записывает `!gotthaumonomicon`. Это события подбора/пробуждения, а не автоматическая выдача книги при создании мира. [PlayerEvents][dream].
2. Salis Mundus превращает книжный шкаф в Таумономикон. `FIRSTSTEPS` привязан к `!gotthaumonomicon`; его стадии требуют изготовить магический верстак, затем таумометр и потратить одну Observation категории Basics. Следующая страница содержит дальнейшие рецепты. [Начальные записи][basics-start].
3. `THEORYRESEARCH` следует за `KNOWLEDGETYPES` и требует **изготовления** scribing tools и research table. Знание не должно выдаваться за простое наличие этих предметов. [Теории в JSON][basics-start].
4. `UNLOCKALCHEMY`: после `FIRSTSTEPS` нужны по одной Observation Basics и Alchemy, изготовление тигля, затем жёлтого Nitor. Именно эта цепь открывает полноценную алхимию. [Разблокировки][basics-unlocks].
5. `UNLOCKAUROMANCY`: после алхимии посетить глубины и высоту, затем изготовить vis resonator и caster's gauntlet. В 1.12.2 низ — **Y < 10**, верх — **Y > 0,4 × actualHeight** (обычно >102,4). События записываются, пока исследование уже начато на первой стадии и ещё не достигло второй. В 1.20.1 с нижней границей −64 нельзя молча сохранить Y<10 как эквивалент «дна»: адаптация высот требует отдельного решения и проверки. [Условия высоты][height].
6. `UNLOCKARTIFICE`: `UNLOCKALCHEMY` + `METALLURGY@2`, одна Observation Artifice, открытия `!sensus` и `!machina`.
7. `UNLOCKINFUSION`: `UNLOCKALCHEMY`, одна Observation Infusion, открытия `!auram` и `!praecantatio`.
8. `UNLOCKGOLEMANCY`: Artifice + Auromancy + Infusion, по одной Observation Golemancy/Basics, факт `f_golem`; вместе открывается `BASEGOLEMANCY`. [Разблокировки][basics-unlocks].

Цепи не следует заменять общим порогом «сканируй N предметов»: это меняет и темп, и направление прохождения.

## Знание и сканирование

Два типа знания — **Observation** и **Theory**, каждый хранится отдельно по исследовательской категории. **16 сырых единиц Observation = 1 полная Observation; 32 сырых единицы Theory = 1 полная Theory.** Остаток сохраняется. Отображаемое целое — `floor(raw/progression)`. Это не «16/32 разных скана». [EnumKnowledgeType][knowledge-units], [хранение и округление][knowledge-values].

Сканирование предмета/существа получает его аспекты и считает прибавку каждой категории по её весовой формуле. При обычном множителе:

`gain(category) = ceil(sqrt(sum(aspectAmount × categoryWeight / 10)))`.

Один скан может дать разное количество сырого знания сразу нескольким категориям. Весы у Basics, Auromancy, Alchemy, Artifice, Infusion, Golemancy и Eldritch различны. [Формула][category-formula], [веса категорий][categories], [начисление при скане][scan-generic].

Один и тот же объект может сработать одновременно в нескольких скан-обработчиках: общий прирост знаний и конкретное открытие исследования. Уникальность общего скана строится по ID существа либо предмета; для предметов без прочности учитывается вариант metadata, а износ инструмента не создаёт новые открытия. [ScanGeneric][scan-generic], [ScanningManager][scan-manager].

Скан **блока с инвентарём** в базовом TC6 также сканирует содержимое доступного сверху item handler: ограничение — **100 непустых слотов**, а не первые 100 индексов и не 100 уникальных предметов. Для воды и лавы предусмотрено представление соответствующим ведром. Возможность водить таумометром по предметам внутри открытого GUI инвентаря из гайда относится к **Thaumic Inventory Scanning**, это не обязательная базовая функция TC6. [Скан контейнера][scan-manager]; дополнение явно названо в [главе гайда о таумометре](https://github.com/xiaoschannel/Minecraft-Guides-Thaumcraft6/blob/cd14373456bfa3090663eb9dbf804f369313a7d7/pages/03.getting-started/10.thaumometer/docs.md).

Визуальная обратная связь включает подсветку ещё не изученных объектов, различие нового/повторного скана, обнаруженные аспекты и показ ауры. Для порта нужна сверка отдельно дальности скана блока, существа и декоративной подсветки: оригинальный предмет использует разные пути raytrace. Нельзя выводить все дистанции из одной константы 9. [ItemThaumometer][thaumometer].

## Небесные наблюдения

`CELESTIALSCANNING` следует за `THEORYRESEARCH` и требует по одной Observation Basics, Artifice и Auromancy. После завершения можно сканировать небо с таумометром, имея бумагу и scribing tools. [Запись исследования][celestial-entry].

Проверки: Overworld, открытое небо над игроком, взгляд вверх, нужное исследование. Для солнца/луны нужен взгляд приблизительно в их положение: допуск yaw <10°, pitch <7°. Ночью направление, не попавшее в луну, интерпретируется как одна из четырёх сторон звёздного неба. [Условия и вычисления][sky].

**Всего 13 вариантов заметок:** 1 солнце + 4 стороны звёздного неба + 8 фаз луны. Гайд ошибочно пишет 14. За один мировой день можно получить одну солнечную, одну текущую лунную и по одной заметке каждой из четырёх сторон: максимум шесть при выполнении условий. Маркер содержит `totalWorldTime/24000`, небесный объект и фазу/сторону; повтор в тот же день не выдаёт предмет. Устаревшие дневные маркеры удаляются. [13 предметных вариантов][sky-items], [выдача и дневные ключи][sky].

На успешную запись расходуется лист бумаги. В `ScanSky` наличие scribing tools проверяется, но **его прочность здесь не уменьшается**; исследовательский стол имеет отдельный механизм расхода чернил. Не переносить расход со стола на скан неба без осознанного изменения правил. При полном инвентаре заметка выпадает рядом. Небесные заметки используются в theorycraft, а их эссенцию следует брать из таблицы аспектов оригинала, не фиксировать произвольной константой из совета гайда.

## Исследовательский стол и карточки

Сессия содержит вдохновение, начальное вдохновение, выбранные aids, бонусные вытягивания, заблокированные категории, процентный прогресс, предложенные/выбранные карточки и последнюю карточку. Это сериализуемое состояние стола, связанное с исследующим игроком. [ResearchTableData][theory-data].

Начальное вдохновение:

`min(15, round(5 + 0,5 × completedSpiky + 0,1 × completedHidden))`.

Каждое выбранное вспомогательное средство уменьшает доступное стартовое вдохновение на 1 и добавляет специальные карты. Поиск блоков — **X/Z ±4, Y ±1**, то есть объём 9×3×9, а не сфера радиусом 4; для aids-сущностей применяется радиус 5. [Вдохновение][inspiration], [поиск aids][table].

В базовом TC6 зарегистрированы книжный шкаф, мозг в банке, glyphed stone, несколько порталов, предметы основных ремёсел, стол зачарований и маяк. Механика aid — добавление набора карточек, а не универсальное прямое прибавление процентов за каждый стоящий рядом блок. Несколько одинаковых блоков не дают отдельные одинаковые кнопки: aids собираются по ключу. [Регистрация aids/cards][cards-register], [поиск][table].

Обычное вытягивание предлагает 2 карты, бонусное может предложить 3, если есть `bonusDraws`. На вытягивание тратится бумага; на успешную активацию карты тратятся чернила и её стоимость вдохновения. Карты с отрицательной стоимостью возвращают вдохновение, но оно ограничено стартовым максимумом. Карта может требовать только наличие предмета либо его расход — у расходуемого предмета в книге стоит золотой восклицательный знак. Карты могут расходовать ванильные уровни опыта или накопленные Observation. [Вытягивание][theory-data], [оплата карт][table-container].

Завершение доступно при `inspiration <= 0`. Проценты категории преобразуются в сырые Theory: `round(percent/100 × 32)`. Категории сортируются по процентам; первоначально только первая получает полную прибавку, остальные — `int(max(1, raw × 2/3))`. Карты могут увеличить число категорий без штрафа через `penaltyStart`. При одинаковых процентах не следует обещать «обе первые получают 100%»: проверка использует позицию в отсортированном списке. Частичный процент и результат >100% допустимы. [Финал сессии][table].

Прочитаны определения стоимости, требований и действия карточек. Ниже — существенные классы поведения для порта; точные списки предметов следует брать из соответствующих классов.

| Группа карт | Поведение, которое требуется сохранить |
|---|---|
| Study / Experimentation | Study: +15…25% доступной категории за 1 вдохновение. Experimentation: случайная категория +15…30%, Basics +1…10%, за 2; может выходить за уже открытые ветви |
| Analyze / Inspired | Analyze обменивает 1 полную Observation на +25…50% выбранной категории и +5% Basics, цена 2 вдохновения. Inspired усиливает лидера на `10 + floor(current/2)`, цена 2 |
| Ponder / Rethink / Reject | Ponder распределяет 25% и даёт Basics+5/bonus draw; Rethink возвращает 1 вдохновение, снимает до 10% прогресса, даёт bonus draw и Basics+1…10; Reject блокирует категорию, цена 0 |
| Balance / Notation | Balance выравнивает незаблокированные категории и увеличивает `penaltyStart`; Notation переносит часть прогресса из слабейшей в сильнейшую |
| Celestial | Потребляет две **разные конкретные** небесные заметки, +25…50% лидирующей категории. Солнце увеличивает `penaltyStart`, луна даёт bonus draw, звёзды могут дать Eldritch и временный warp |
| Concentrate / Reactions / Synthesis | Работа с аспектными кристаллами. Первые две требуют наличие, Synthesis потребляет кристаллы компонентов и выдаёт кристалл результата; возможен возврат вдохновения |
| Calibrate / Tinker / Mind Over Matter | Artifice: фиксированный прирост+bonus draw, исследование предмета либо его расход. Стоимость зависит от выбранного предмета/его аспектов |
| Measure / Channel / Infuse | Infusion: измерение, наличие фиала либо расход фиала вместе с предметом; разные награды |
| Focus / Awareness / Spellbinding | Auromancy: +15%/bonus draw; +20% с шансом Eldritch и normal warp; обмен до 5 уровней XP по 5% за уровень |
| Sculpting / Scripting / Synergy | Golemancy: глина расходуется; scripting дополнительно тратит бумагу/чернила; synergy переводит 15% из Artifice/Alchemy/Infusion в 30% Golemancy и расширяет отсутствие штрафа |
| Curio / Enchantment / Beacon | Расход curios даёт тематические результаты; зачарование расходует 5 XP levels; маяк возвращает 2 вдохновения, даёт bonus draw и расширение `penaltyStart` |
| Dark Whispers / Glyphs / Portal / Revelation / Realization | Сильные или широкие прибавки связаны с temporary/normal warp; Dark Whispers снимает уровни игрока и зависит от исходного их числа |

Источники: [базовые карточки](https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/tree/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/research/theorycraft), [тематические карточки](https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/tree/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/research/theorycraft), [Celestial: требования и эффекты][card-celestial]. Наличие файла класса ещё не доказывает его доступность в игре: например, `CardDragonEgg` и `CardTruth` существуют, но не фигурируют в прочитанном списке регистрации `ConfigResearch.initTheorycraft`. Перед переносом отдельно сверить набор реально зарегистрированных карт с JAR. В `CardAnalyze.initialize` обнаружен подозрительный выбор категории через ещё не установленный `cat`; фактическую доступность этой карты нужно дополнительно проверить в оригинале.

## Перчатка и граф фокуса

В TC6 пользователь строит заклинание из **Root → Medium/Modifier → Effect**, а не применяет систему жезлов из старых версий. Root выдаёт и текущую траекторию взгляда, и самого заклинателя как цель. Medium обычно принимает траекторию и выдаёт цель. Effect принимает цель. Поэтому эффект, соединённый прямо с Root, действует на самого игрока: например, Heal лечит, Fire поджигает. [Контракты узлов][focus-types], [оригинальный текст книги][focus-book].

Перчатка хранит вставленный фокус; обычная `caster_basic` использует ауру текущего чанка. Заклинание запускается сервером после проверки cooldown и оплаты vis с учётом скидки. Фокус — не заряжаемая батарейка. Выбор через клавишу F и снятие через sneak+F описаны в интерфейсах TC6; при переносе это переназначаемые key mappings. Pouch служит хранилищем фокусов и интегрируется с поясным Baubles-слотом. Не переносить чужую систему слотов как скрытую обязательную зависимость без решения по 1.20.1. [Предмет перчатки][caster].

Основа фокуса ограничивает сложность: **Lesser 15, Advanced 25, Greater 50**. Lesser создаётся в тигле: катализатор Ordo crystal, 20 Vitreus +10 Praecantatio +5 Auram. Advanced требует infusion: Lesser, две quicksilver, diamond, ender pearl, 25 Praecantatio +50 Ordo, instability 3. Greater: Advanced, две quicksilver, primordial pearl, nether star, 25 Praecantatio +50 Ordo +100 Vacuos, instability 5. [Регистрация ёмкостей][focus-cap], [рецепты][focus-recipes].

Фокус конструируется на Focal Manipulator. Для каждого узла с аспектом нужен один кристалл этого аспекта. Повторы **одного и того же типа** узла увеличивают стоимость сложности: множители 1; 1,5; 2; 2,5…; вклад каждого узла приводится к целому отдельно. При общей сложности `C` и ёмкости основы `M`:

| Величина | Правило TC6 |
|---|---|
| Vis для записи фокуса | `10 × C + floor(M/5)` |
| XP для записи | `max(1, round(sqrt(C)))` **уровней**, не сырых очков опыта |
| Vis одного применения | `C/5.0`, далее модификатор расхода перчатки/снаряжения |
| Cooldown | `max(5, floor(C/5) × floor(C/4))` тиков |
| Скорость записи | Один раз в 20 тиков поглощается до 20 vis; недостаток ауры приостанавливает завершение |

Манипулятор с зарядником сверху берёт vis из области 3×3 чанка. После оплаты всех vis граф сериализуется в предмет; вынутый в процессе предмет прерывает запись. Проверка сложности, доступности узлов/настроек, связности графа, отсутствия циклов, лимитов и оплаты должна быть серверной и атомарной. Не следует копировать положение списания XP перед проверкой кристаллов из восстановленного `startCraft`. [Создание/оплата/запись][focus-craft], [цена каста/cooldown][focus-cost].

### Узлы и стоимость сложности

В формулах `p` — power, `d` — duration, `f` — fortune, `s` — silk, деление целых округляется вниз. Это сложность отдельного узла до надбавки за повтор. Значения взяты из методов `getComplexity/createSettings` классов [foci][foci-code].

| Узел | Параметры и сложность | Смысл |
|---|---|---|
| Touch | 2 | Ближний выбор цели |
| Bolt | 5 | Прямое попадание, без дополнительных настроек |
| Projectile | `4 + floor((speed−1)/2)`; bounce +3; seeking +5; speed 1…5 | Перенос оставшегося графа снарядом; none/bounce/hostile/friendly |
| Mine | 4; hostile/friendly | Отложенное срабатывание по цели |
| Cloud | `4 + 2×radius + floor(duration/5)`; radius 1…3, duration 5…30 | Облако; множитель силы 0,5 |
| Spellbat | 8; hostile/friendly | Существо-посредник; множитель силы 0,33 |
| Plan | 4; full/surface | Объём/поверхность для работы с блоками; дальнейшие ограничения надо брать из обработчика области |
| Scatter | `int(max(2, 2×(forks−cone/45.0)))`; forks 2…10, cone 10/30/60/90/180/270/360° | Разброс траекторий; множитель силы `2/forks` |
| Split Target / Split Trajectory | 4 / 5 | Две ветви целей / траекторий; нельзя смешивать их типы связи |
| Air | `2p`, p 1…5 | Урон `(1+p)×finalPower`, отбрасывание |
| Earth | `3p`, p 1…5 | Урон `2p×finalPower`; разрушение только достаточно мягких блоков, не замена Break |
| Fire | `d+2p`, p 1…5, d 0…5 | Урон `(3+p)×finalPower`, горение; d=0 отключает поджигание |
| Frost | `d+2p`, p 1…5, d 2…10 | Урон и замедление, сила замедления зависит от параметров; не всегда фиксированный Slowness III |
| Flux | `3p`, p 1…5 | Магический урон |
| Heal | `4p`, p 1…5 | Лечение/взаимодействие с нежитью |
| Curse | `d+3p`, p 1…5, d 1…10 | Проклятие и последующие эффекты |
| Break | `3p+4s+(f==0 ? 0 : 3×(f+1))`; p 1…5, f 0…4, s 0/1 | Разрушение с отдельными настройками добычи |
| Exchange | В BETA26 фактически `3×(f+1)` при допустимых настройках | Замена выбранным блоком из инвентаря, silk/fortune; см. замечание о формуле ниже |
| Rift | `3+floor(d/2)+floor(depth/4)`; d 2…10, depth 8/16/24/32 | Временный проход; это не Flux Rift из системы загрязнения |

**Exchange проверен по оригинальному JAR:** условие байткода — `(5 + 4×silk + fortune == 0) ? 0 : 3×(fortune+1)`. При штатных неотрицательных настройках левая ветвь недостижима: базовая сложность 3, silk не добавляет сложность. Это похоже на ошибку приоритета операторов самого BETA26, а не только декомпиляции. Верный порт должен явно решить, сохраняет ли такую особенность или исправляет её; нельзя молча выдавать «логичную» формулу `5+4s+…` за поведение оригинала. [Место в исходнике][exchange].

### Как открываются эффекты и модификаторы

Точные данные лежат в [auromancy.json][auro-json]. Для UI и условий использовать сами записи, не вручную восстановленный порядок картинок.

| Исследование | Основные требования |
|---|---|
| BASEAUROMANCY | После UNLOCKAUROMANCY изготовить Lesser Focus, затем Focal Manipulator и получить огненный урон после открытия нужной стадии |
| FOCUSELEMENTAL | BASEAUROMANCY; Observation Auromancy 1; `!aer`, `!gelum`, `!terra` |
| FOCUSFLUX | FOCUSELEMENTAL; Theory Auromancy 1; `!vitium` |
| FOCUSHEAL / FOCUSEXCHANGE | После FOCUSFLUX; Theory Auromancy 1; `!victus` / `!permutatio` |
| FOCUSCURSE | FOCUSFLUX и `!Pechwand`; Theory Auromancy 1, `!mortuus`; warp в записи |
| FOCUSBREAK | FOCUSFLUX; Theory Auromancy 1; `!perditio`; сдача предметов с Silk Touch I и Fortune I |
| FOCUSRIFT | FOCUSBREAK + FOCUSEXCHANGE; Observation Eldritch 1 и Theory Auromancy 1; `!vacuos`; warp в записи |
| FOCUSPROJECTILE | BASEAUROMANCY; Theory Auromancy 1, `!motus`; затем `f_arrow`, `f_fireball`, `f_spit` |
| FOCUSBOLT | Уже FOCUSPROJECTILE@2; Observation+Theory Auromancy по 1; `!potentia` |
| FOCUSSCATTER / FOCUSSPLIT | Bolt+Projectile → Scatter → Split; по одной Observation/Theory Auromancy |
| FOCUSMINE | Projectile; по Theory Artifice/Auromancy; tripwire hook; `!vinculum` |
| FOCUSCLOUD | Mine и `!DRAGONBREATH`; по Theory Alchemy/Auromancy; dragon breath; `!alkimia` |
| FOCUSSPELLBAT | Mine, `f_BAT`, `!Firebat`; по Theory Auromancy/Eldritch; `!bestia`; warp в записи |
| FOCUSPLAN | Exchange+Projectile; Theory Auromancy 1; `!fabrico` |
| FOCUSADVANCED / FOCUSGREATER | BASEAUROMANCY+INFUSION → Advanced; Advanced+PRIMPEARL → Greater; нужны крафт соответствующей основы и Theory Auromancy |

Существенное уточнение к гайду: для Projectile есть **и сканирование** стрелы/снаряда, **и события получения урона**. Получать три удара — допустимый путь прохождения, но не единственный кодовый триггер. Скан обычной стрелы-предмета тоже зарегистрирован. Сам базовый Projectile доступен через `FOCUSPROJECTILE@2`, а его дополнительные режимы требуют полного `FOCUSPROJECTILE`. [Регистрация сканов][special-scans], [события урона][hurt], [настройки Projectile][projectile].

`warp:2` указан у Curse, Rift и Spellbat. Значение JSON нельзя без проверки называть «ровно 2 permanent warp»: ResearchManager делит поступивший warp между NORMAL и PERMANENT, учитывает текущую/финальную стадию и `wussMode`. Карты могут отдельно давать TEMPORARY и NORMAL. Нужен сценарный тест фактически выданного значения при переходе каждой стадии и сравнительная проверка оригинального JAR. [Расчёт warp при исследовании][progress].

## Сопутствующие устройства

Recharge Pedestal открывается после BASEAUROMANCY за Theory Auromancy; заряжает подходящие предметы. Vis Amulet требует также Infusion и две Theory Auromancy. Workbench Charger следует за Recharge Pedestal, требует Observation+Theory Auromancy; расширяет забор vis верстака/манипулятора до текущего и восьми соседних чанков. Vis Battery требует Recharge Pedestal **и CRYSTALFARMER**, по Observation Auromancy/Artifice. Следовательно, одного раннего скана кристалла недостаточно для всего пути к батарее: CRYSTALFARMER связан с Infusion. [Исследования устройств][auro-devices], [область зарядника][focus-craft].

Указанные в гайде ёмкость батареи 10 и режим красного камня требуют отдельной сверки соответствующего блока/тайла перед реализацией; здесь они не объявляются подтверждёнными численными константами. Аналогично не надо превращать советы «сделайте вокруг алтаря 8 пьедесталов» или спорное «больше 12 оккультных предметов вредно» в правило ядра инфузии: её геометрию и стабильность сверяет отдельная спецификация.

## Отличия нынешнего порта 0.2 и порядок замены временной модели

По прочитанным `PlayerKnowledge`, `ResearchCatalog` и реестру Java-файлов текущего проекта:

| Сейчас | Требуется для TC6 |
|---|---|
| Счётчик уникальных сканов, набор аспектов и `PORT_*` | Раздельные сырые Observation/Theory по категориям + оригинальные стадии/служебные факты |
| Временные рецептурные aliases, например BASEALCHEMY→PORT_ALCHEMY | Родители, требования и `KEY@stage` по настоящим JSON |
| Архив страниц и список кнопок | Карта исследований с исходными координатами, связями, видимостью, стадиями, дополнениями и страницами книги |
| Таумометр со сканами предметов/блоков/существ | Оригинальные категорийные начисления, скан контейнеров, специальные события, небесные наблюдения |
| Нет полной карточной системы | Research table, scribing tools/paper, persistent session, aids, cards, финальная конверсия знания |
| Нет кастующего графа и перчатки | Основы 15/25/50, граф, манипулятор, оплата и синхронизация, эффекты, физика посредников, pouch и key mappings |

Сначала следует ввести серверную модель оригинальных знаний и стадий, согласовать перенос уже сохранённых `PORT_*`, затем восстановить представление книги поверх этих данных. Отдельно добавляются table/cards и celestial, затем ветка gauntlet/focus. Просто нарисовать дерево поверх счётчика сканов недостаточно для верного поведения TC6.

## Проверки, которые должны сопровождать реализацию

- 15/16/17 raw Observation и 31/32/33 raw Theory правильно показываются, сохраняются и списываются; остаток не теряется.
- Один предмет не фармит знания изменением названия/прочности; один скан может открыть несколько относящихся к нему фактов. Скан контейнера не превышает 100 непустых слотов.
- `required_craft` отличается от наличия вещи, `required_item` расходуется, `required_research` не расходуется. Невыполнимая стадия не тратит ничего. Повторные пакеты не выдают повторные награды.
- Небо: 13 вариантов, повтор в день запрещён, новый день разрешён, неверное измерение/крыша/отсутствие бумаги не выдают предмет. Квота определяется временем мира, а не локальными часами клиента.
- Теория корректно переживает сохранение/загрузку и выдаёт сырые знания ровно один раз; `penaltyStart`, частичные проценты, отрицательная цена карты и расходуемые предметы покрыты проверками.
- Граф фокуса проверяется сервером: недоступные узлы, неизвестные настройки, циклы, отсутствующий Root и превышение сложности отвергаются до оплаты. Повторные узлы, целочисленное округление, vis/XP/crystals/cooldown сравниваются с эталоном.
- Смена измерения, отрицательные координаты, отсутствующий/выгруженный чанк и два клиента у одного устройства не приводят к двойному крафту или чтению чужих знаний.

## Оставшиеся неопределённости

Точная физика всех фокусных сущностей, ограничения Plan/Break/Exchange, расход vis на каждый блок массовой операции, совместимость защиты регионов, параметры Recharge Pedestal/Vis Battery и вся система warp-событий не проверены здесь полностью. Формулы узлов прочитаны в исходниках, но не все классы сверены по байткоду оригинального JAR. Необходимые адаптации высоты мира, Baubles и миграции временного сохранения 0.2 не следует выдавать за поведение оригинала. Исследование этих границ продолжать перед реализацией соответствующей подсистемы.

[knowledge-model]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/capabilities/IPlayerKnowledge.java#L31-L62
[knowledge-units]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/capabilities/IPlayerKnowledge.java#L151-L167
[knowledge-values]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/capabilities/PlayerKnowledge.java#L171-L198
[progress]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/research/ResearchManager.java#L100-L219
[stage-payment]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/network/playerdata/PacketSyncProgressToServer.java#L78-L146
[dream]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/events/PlayerEvents.java#L134-L174
[basics-start]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/research/basics.json#L3-L55
[basics-unlocks]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/research/basics.json#L144-L245
[height]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/config/ConfigResearch.java#L300-L310
[category-formula]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/research/ResearchCategory.java#L53-L76
[categories]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/config/ConfigResearch.java#L138-L144
[scan-generic]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/research/ScanGeneric.java#L38-L75
[scan-manager]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/research/ScanningManager.java#L40-L125
[thaumometer]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/items/tools/ItemThaumometer.java#L60-L137
[celestial-entry]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/research/basics.json#L283-L295
[sky]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/research/ScanSky.java#L20-L124
[sky-items]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/items/curios/ItemCelestialNotes.java#L13-L17
[theory-data]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/research/theorycraft/ResearchTableData.java#L18-L293
[inspiration]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/research/theorycraft/ResearchTableData.java#L326-L367
[table]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/crafting/TileResearchTable.java#L78-L160
[table-container]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/container/ContainerResearchTable.java#L50-L120
[cards-register]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/config/ConfigResearch.java#L227-L275
[card-celestial]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/research/theorycraft/CardCelestial.java#L51-L116
[focus-types]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/tree/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/casters
[focus-book]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/lang/en_us.lang#L1555-L1558
[caster]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/items/casters/ItemCaster.java#L106-L190
[focus-cap]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/config/ConfigItems.java#L275-L279
[focus-recipes]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/config/ConfigRecipes.java#L278-L279
[focus-craft]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/crafting/TileFocalManipulator.java#L110-L299
[focus-cost]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/items/casters/ItemFocus.java#L157-L168
[foci-code]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/tree/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/items/casters/foci
[exchange]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/items/casters/foci/FocusEffectExchange.java#L41-L44
[auro-json]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/research/auromancy.json#L3-L330
[special-scans]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/config/ConfigResearch.java#L218-L221
[hurt]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/events/EntityEvents.java#L111-L135
[projectile]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/items/casters/foci/FocusMediumProjectile.java#L15-L72
[auro-devices]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/resources/assets/thaumcraft/research/auromancy.json#L332-L415
