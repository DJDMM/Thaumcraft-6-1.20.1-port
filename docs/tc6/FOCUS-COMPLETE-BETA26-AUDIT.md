# Все фокусные узлы и основы — контракт TC6 6.1.BETA26

Этап **0.19.0-dev**, Forge 1.20.1 / Java 17. Официальный эталон:
`Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Читаемый исходник закреплён на commit
`954022bb777b7546281fb36df8522f0ba6b43f81`;
происхождение — [reference-manifest.json](reference-manifest.json).
TC4/TC5, дополнения и ранние beta не используются.

Этот документ дополняет исторические аудиты графа/манипулятора 0.16,
стихий/снаряда 0.17 и Bolt/Flux/Heal/Break 0.18. Их утверждения о закрытых
runtime-узлах относятся к тем версиям. В 0.19 поддерживаются **21/21**:
ROOT; TOUCH/PROJECTILE/BOLT/CLOUD/MINE/PLAN/SPELLBAT;
FIRE/AIR/FROST/EARTH/FLUX/HEAL/BREAK/CURSE/EXCHANGE/RIFT;
SCATTER/SPLITTARGET/SPLITTRAJECTORY.

## Источники и разделение оригинала/адаптации

В закреплённом дереве изучены `FocusMediumCloud/Mine/Plan/SpellBat`,
`FocusModScatter/SplitTarget/SplitTrajectory`, `FocusModSplit`,
`FocusEffectCurse/Exchange/Rift`, `FocusEngine/FocusPackage`,
`EntityFocusCloud/EntityFocusMine/EntitySpellBat/EntitySpecialItem`,
`TileHole`, `BlockEffect`, `ItemCaster`, `CasterManager`,
`ServerEvents`, `ConfigResearch`, `ConfigRecipes`, `auromancy.json`,
`eldritch.json` и `scans.json`. Формулы узлов также сверены при первоначальном
аудите графа. В официальном JAR через `javap -c -p` отдельно подтверждены
необычные Integer/Long операции Cloud, 40-тиковый счётчик/сброс при загрузке
Mine, gravity .01 и множитель Split .75; precedence Exchange подтверждён
в [аудите графа](FOCUS-GRAPH-BETA26-AUDIT.md).

Старый `EntityThrowable` и `updateAITasks` заменены современными сущностями
и ручными collision/flight шагами. API геометрии, частиц и состояния блоков
изменился; побочное поведение ванильных классов двух версий не объявляется
тождественным. Loaded-only выбор, проверка владельца/состояния после callback,
лимиты очередей и строгая форма пакетов — явная серверная адаптация.

## Граф и один оплаченный cast

`FocusCompiler` принимает один ROOT, уникальные ID, согласованные
parent/children, достижимое дерево без циклов, не более 32 узлов и
ограниченные координаты/settings. Эффект имеет ноль детей, оба Split —
ровно двух, остальные узлы — одного. Проверяются исходные supply contracts,
строгие исследования узлов/настроек и capacity. Bare node gates требуют
завершения; исходный `FOCUSPROJECTILE@2` сохраняет стадийную семантику.
Дополнительная проверка BASEAUROMANCY выполняется обработчиком стола.

Plan является exclusive medium: совместим с ROOT и модификаторами, но не
с другими non-root medium. Scatter является exclusive modifier и допускается
один раз во всём графе. Множитель повторения одинакового key остаётся
`.5*(номер_появления+1)`; каждое слагаемое сложности приводится к int отдельно.
Нормализация и вычисление повторений идут детерминированным BFS вместо
зависимости оригинального редактора от порядка HashMap — явная адаптация.
Кристалл требуется за каждое вхождение с аспектом; RGB всех конечных
эффектов усредняется, alpha непрозрачен.

`FocusPlan.packageNbt` сохраняет original `index/power/complexity/nodes`.
Split записывает исходную двойную оболочку `node.packages.packages[]`,
каждая ветка содержит собственный список `nodes`. `FocusStacks` ограниченно
читает вложенные ветки, восстанавливает parent/children, повторно компилирует
и вычисляет цену/цвет. Переданные item caches и branch power/complexity не
являются авторитетными. Циклы, лишние/неправильные packages, неправильные
типы и превышения capacity отклоняются. Исходная сортировка `srt` использует
верхнюю последовательность до Split; это не уникальная подпись всех веток.

При применении каждому cast назначается новая execution UUID. Она сохраняется
на оплаченных intermediary entities, но не определяет цену готового предмета.
Это сохраняет исходный сброс hurt-window при нескольких попаданиях одного
заклинания и отделяет последующие cast. `FocusExecution` переносит силу,
target ordinal, источник/направление через ветки и суффиксы; бюджет операций
4096 ограничивает разветвлённый runtime. Ни media callbacks, ни Split не
вызывают новую оплату/cooldown/onCast. Effect ordinal считается по
плотному массиву реальных целей, а не по fork index с промахами. Plan
объединяет TARGET массив всех входных траекторий до выполнения веток;
каждый отдельный Projectile impact продолжает свой одноцелевой массив
с ordinal 0, включая загрузку старого paid save с fork ordinal.
Для сохранённой delivery проверяются
реальный parent ID и владение, а не ошибочное предположение `nextIndex-1`.

## Новые medium и модификаторы

| Узел | Complexity / настройки | Передача и сила |
| --- | --- | --- |
| CLOUD | `4+radius*2+duration/5`; radius 1–3, duration 5–30 | TARGET, множитель .5 |
| MINE | 4; target enemy/friend | TARGET+TRAJECTORY, множитель 1 |
| SPELLBAT | 8; target enemy/friend | TARGET, множитель .33 |
| PLAN | 4; full/surface | TARGET, множитель 1 |
| SCATTER | `int(max(2,2*(forks-cone/45f)))`; forks 2–10 | TRAJECTORY, множитель `2/forks` |
| SPLITTARGET | 4 | TARGET двум веткам, множитель .75 |
| SPLITTRAJECTORY | 5 | TRAJECTORY двум веткам, множитель .75 |

Деление Cloud duration/5 целочисленное. Cone принимает исходные
10/30/60/90/180/270/360; Gaussian шум каждой координаты равен
`nextGaussian()*.007499999832361937*cone`, затем вектор нормализуется.
Множитель применяется один раз до передачи в media, включая последующие
projectile и эффекты. Split Target убирает траекторию из supply, Split
Trajectory убирает target; следующие узлы обязаны удовлетворять контракту.

**Cloud.** Позиция stationary, размер `radius*2` по горизонтали/.5 по высоте,
жизнь `duration*20` тиков. Раз в пять тиков живые существа выбираются в
кубическом AABB радиуса: исходный `EntityUtils.getEntitiesInRange` тоже
использует AABB, не проверку сферического расстояния. Включается сам кастер.
Пара Cloud немного раздвигается. За pulse выпускается `radius` случайных
лучей к блокам; target continuation выполняется на END. **В BETA26 map
объявлен Long→Long, но living ID ищется через Integer и записывается через
Long.** Поэтому living cooldown реально не срабатывает: каждое попадание
раз в пять тиков сохранено. BlockPos ищется как Long и соблюдает общие 2000ms.
Порт разделяет map по ServerLevel и очищает на unload/stop, удаляет истёкшие
записи; это защита от коллизий измерений/утечки, не новая задержка.

**Mine.** Исходный `shoot(...,0,0)` даёт нулевую начальную скорость;
gravity .01, drag .99 или .8 в воде. Столкновение вооружает; counter 40
уменьшается только у вооружённой мины. После нуля раз в пять тиков проверяется
AABB радиуса 1. Allied/pet/passenger/PvP правила отличают friend/enemy.
На каждую подходящую живую цель назначается отдельная оплаченная delivery
с delay 0,1,…; при наличии хотя бы одной мина исчезает. Reload вооружённой
мины сбрасывает counter в 0 по BETA26. Предельный возраст 1200 тиков,
отсутствующий/невалидный владелец или выгруженная позиция завершают сущность.
Вооружение не обнуляет motion и не прекращает movement/drag/gravity:
исходный EntityThrowable продолжал их и после impact. Рабочая мина наследует
modern Projectile и вызывает Forge onProjectileImpact; veto не вооружает.
Forge listener может удалить entity вместо cancel. После callback
`isRemoved` немедленно прекращает tick, а перед countdown/detonation
проверяется снова: удалённая мина не вооружается и не добавляет оплаченный суффикс.
Это соответствует исходному `isEntityAlive` guard после EntityThrowable
update; callback removal не превращается в дополнительное попадание.
Original pushOutOfBlocks заменён modern moveTowardsClosestSpace с исходным
четвертным затуханием скорости при нахождении внутри блока.

**SpellBat.** MAX_HEALTH 5; ручной поиск ближайшей подходящей живой цели в
AABB радиуса 12, оригинальные signum-сглаживание полёта и вертикальное
затухание .6000000238418579. Для атаки нужна видимость, вертикальное
пересечение, расстояние меньше `max(2.5,width*1.1)` и нулевой attackTime.
Атака задаёт attackTime 40, продолжает оплаченный граф и уменьшает здоровье
мыши на 1. Владелец задаёт команду; враждебная мышь исключает invulnerable
creative-игрока. Исходный ignore block triggers перенесён через
isIgnoringBlockTriggers: SpellBat не активирует нажимные плиты.
Срок 600 тиков. Health сохраняется штатным Mob NBT,
attackTime/flightTarget не сохраняются. Порт сохраняет возраст Mine/Bat
дополнительно: reload не позволяет бесконечно продлевать время жизни.

**Plan.** Original caster NBT `areax/y/z/d`, radii default/max 3, dimension
all/X/Z/Y. G циклически меняет радиус выбранных осей, Ctrl+G — оси;
server получает только action 0/1, сам выбирает удерживаемый кастер с Plan.
Trace 16 блоков; full volume сдвинут внутрь по стороне на её радиус,
включает непустые блоки (максимум 343). Surface обходит связную открытую
плоскость с идентичным состоянием; исходное переставление осей у стороны Z
сохранено. Sorted targets получают нулевые ordinal по дальности от hit.
Client preview/HUD read-only; chunk forcing исключён.

`PaidFocusContinuation` сохраняет detached graph/capacity/next/power/ordinal,
UUID владельца и execution UUID; load заново компилирует graph и проверяет
medium settings. Callback queue ограничена 8192 и обрабатывает detached
batch до 512 готовых операций на END HIGH; смена мира, смерть, удаление
цели, unload и неверный UUID не создают новый cast.

## Curse, Exchange и временный Rift

**Curse:** complexity `duration+power*3`, power 1–5, duration 1–10.
Damage `(1+power)*finalPower`, indirect magic с hit entity/caster.
Potion amplifier `max(0,int(power*finalPower/2))`; Poison длится `20*duration`.
Затем chance .85: Slowness, Weakness, Mining Fatigue, Hunger, Unluck;
chance уменьшается на .15 **только после успешного предыдущего добавления**.
Первые две длительности равны duration, fatigue×2, hunger/unluck×3.
Отмена damage не отменяет исходный последующий набор ailments. Return false
после выполнения сохранён.

Для block hit Curse проверяет центры **нижних твёрдых блоков** в сфере
`min(8,1.5*power*finalPower)` относительно hitVec; sap появляется в воздухе
над ними. `effect_sap` невидим, имеет light 7, контакт переносит на END
Wither40 amplifier0, Slow40 amplifier1 и Hunger40 amplifier1, ambient=true.
Уже Wither не обновляется; исходный Eldritch roster исключён. Random tick
удаляет sap без loot. Оригинальный `jacobs.ogg` добавлен из закреплённого JAR.

**Exchange:** silk 0/1, fortune 0–4. Подтверждённая исходная формула
`(5+4*silk+fortune==0)?0:3*(fortune+1)` при штатных значениях даёт
`3*(fortune+1)`; silk не увеличивает complexity. Shift+ПКМ выбирает
state-sensitive silk sample, записывает item count1 в original caster NBT
`picked`, не берёт vis и не меняет блок. BE/контейнеры исключены;
device handlers сохраняют приоритет. Без Exchange выбранное NBT не
выдаётся аксессором и Shift не запускает cast.

Exchange добавляет виртуальную swap-операцию на END, требует прежнее точное
состояние, main-inventory sample с точным NBT и достаточную target-chunk
ауру `.25+silk*.25+fortune*.1`. Same-block swap по item identity не тратит
ресурсы. Survival списывает один sample, target vis и возвращает реальные
loot в инвентарь/в мир, без инструментального wear/XP. Creative проверяет
ауру, но пропускает этот дополнительный debit/sample/loot. Замена BlockItem
использует default state, а nonblock sample становится неподвижным рабочим
SpecialItem с компенсацией gravity и immunity к explosion. Forge placement
veto и изменение состояния callback сохраняют ресурсы; неуспешная установка
возвращает платёж. Queue transient, максимум 4096, detached/reentry guard,
unload/stop очищают её.

**Rift:** это portable passage, не мировая сущность Flux Rift.
Complexity `3+duration/2+depth/4`, duration 2–10, depth 8/16/24/32.
Сканирует внутрь от hit side, пока `distance<depth*finalPower`, затем
использует исходный byte `distance+1`. BE первого центра на первом тике
создаёт восемь поперечных соседей и следующий центр; вход/плоскости 3×3 и
один terminal-air segment сохранены. Countdown `20*duration` восстанавливает
каждый блок. Старый числовой ID+metadata заменён полным registry BlockState
NBT; save/load сохраняет properties. Containers/BE, bedrock, hole,
doors/beds/pistons/infernal furnace и unbreakable блоки исключены;
replaceable water/plants отвергаются, terminal air допустим. Защита мира,
loaded chunks и повторная проверка после Forge placement обязательны.
Идентификатор dormant Outer Lands отвергается, нового измерения не создаётся.

## Каноническая прогрессия и 15/25/50

В 0.19 добавлены 11 обычных записей и два исходных скрытых скана:
фокусный срез даёт **42 канонических ключа**. После интеграции CENTRIFUGE
текущий полный реестр содержит **43**; контракт первого устройства следующего
пункта — [CENTRIFUGE-INTEGRATION-NOTE.md](CENTRIFUGE-INTEGRATION-NOTE.md).
Raw единицы: Theory 32,
Observation 16. Все parents в таблице должны быть завершены; специальные
факты приобретаются реальным сканом, hover/preview не выдают знание.

| Исследование | Parents | Требования текущей стадии |
| --- | --- | --- |
| FOCUSCURSE | FOCUSFLUX, `!Pechwand` | Mortuus, Theory Auromancy 32 |
| FOCUSEXCHANGE | FOCUSFLUX | Permutatio, Theory Auromancy 32 |
| FOCUSRIFT | FOCUSBREAK, FOCUSEXCHANGE | Vacuos, Observation Eldritch 16, Theory Auromancy 32 |
| FOCUSPLAN | FOCUSEXCHANGE, FOCUSPROJECTILE | Fabrico, Theory Auromancy 32 |
| FOCUSMINE | FOCUSPROJECTILE | Vinculum, tripwire hook 1, Theory Artifice 32 + Auromancy 32 |
| FOCUSCLOUD | FOCUSMINE, `!DRAGONBREATH` | Alkimia, dragon breath 1, Theory Alchemy 32 + Auromancy 32 |
| FOCUSSPELLBAT | FOCUSMINE, `f_BAT`, `!Firebat` | Bestia, Theory Auromancy 32 + Eldritch 32 |
| FOCUSSCATTER | FOCUSBOLT, FOCUSPROJECTILE | Observation Auromancy 16 + Theory Auromancy 32 |
| FOCUSSPLIT | FOCUSSCATTER | Observation Auromancy 16 + Theory Auromancy 32 |
| FOCUSADVANCED | BASEAUROMANCY, INFUSION | Настоящий craft focus_2, Theory Auromancy 32 |
| FOCUSGREATER | FOCUSADVANCED, PRIMPEARL | Настоящий craft focus_3, `f_onfire`, Theory Auromancy 32 |

Curse/SpellBat получают warp на финальной prose стадии, Rift — при уходе
из первой; подтверждённый переход даёт 1 normal + 1 permanent, без temporary.
Штатный replay не повторяет оплату/warp. Основной инвентарь и общие
резервирования требований сохраняются; offhand hook/breath не оплачивает
required_item. Наличие focus_2/3 не заменяет required_craft.

`PRIMPEARL` wildcard scan принимает pearl/nodule/mote (в том числе damage
0/3/7), `!Firebat` принимает реальную `thaumcraft:fire_bat`. Оба завершают
свои оригинальные пустые записи stage2 и дают 5 XP один раз. Arbitrary book
request без discovery marker запрещён. FireBat одновременно даёт `f_BAT`;
обычная Bat даёт только `f_BAT`. Pech body не заменяет scan Pech Wand,
ванильная Ender Pearl не заменяет Primordial Pearl.

Advanced infusion использует stage gate **FOCUSADVANCED@1**, instability3,
central Lesser + quicksilver/diamond/quicksilver/Ender Pearl,
Praecantatio25/Ordo50. Greater: **FOCUSGREATER@1**, instability5,
central Advanced + quicksilver/Primordial Pearl any/quicksilver/Nether Star,
Praecantatio25/Ordo50/Vacuos100. Tier capacity 15/25/50; обе основы заменяются,
старое имя/package не копируются. После настоящего craft можно оплатить
финальную стадию исследования. Из 56 infusion recipes для **11** реализованы
исследования и работающий крафт; обычные survival-источники установлены для
прежних **9** рецептов экипировки. Advanced/Greater проверены через реальный
алтарь с заданными prerequisites: исходные мировые источники Firebat/Pech и
Primordial Pearl через Flux Rift ещё не завершены. Остальные **45** сохраняют
поздние gates.

## Визуальные и мировые границы

Граф манипулятора сохраняет исходные GUI/icons/settings, обе дочерние ветки,
цвет смешанных effects и настоящее создание. Mine и SpellBat используют
перенесённые модели и оригинальные textures. Cloud/Mine/Bat и новые impacts
используют цветные native dust particles вместо полного старого
ParticleEngine/эффектно-специфической анимации. Звёздные грани прохода
используют End Portal shader вместо GL11 старого рендера. Контуры Plan и
современный HUD показывают selection, серверная геометрия остаётся отдельной.
Это явные визуальные адаптации, не заявление о попиксельном равенстве FX.

Прежние harmless catalogue IDs сохранены; рабочие IDs:
`thaumcraft:focus_cloud_spell`, `focus_mine_spell`, `spell_bat_spell`,
`focus_exchange_item`. Catalogue `focus_cloud/focus_mine/spell_bat/special_item`
не заменяются при регистрации/загрузке. `hole/effect_sap` используют прежние
block IDs через рабочую фабрику, hole получает рабочий BE.

Естественные spawn/AI FireBat и Pech/wand получение пока относятся к
следующим мировым механикам; scan существующего настоящего образца работает.
Primordial Pearl scan/Greater работают, но оригинальные мировые источники
жемчужины, Flux Rift consumers и остальные Eldritch/world механики не
объявлены завершёнными. Focus pouch/Baubles selection, Vis Battery и
другие устройства тоже остаются отдельными задачами.

## Выполненные проверки и границы QA

Текущий полный server log `validation/focus-019-gametest8.log`
завершил **577/577** обязательных тестов и BUILD SUCCESSFUL, включая
Mine/SpellBat/ordinal регрессии, центрифугу и удаление мины из Forge impact
callback без вооружения или продолжения оплаченного графа.
Отдельный hidden client run `full-019-client8` прошёл **33/33** сцены;
с отдельным Bolt кадром сохранены и просмотрены **34** снимка.
EssentiaProduction `centrifuge-019-client2` прошёл **12/12** сцен: совместно
просмотрены **46** кадров. Финальные build/resource проверки также прошли.
Сводка, исходные логи и ограничения — в [VALIDATION.md](../../VALIDATION.md)
и [отчёте 0.19](../../validation/artifact-report-0.19.json).
Meaningful server checks:

- `CompleteFocusGraphGameTests`: ветки/двойной package roundtrip,
  обе формы Split, исключительность Plan/Scatter, supply/price/power gates.
- `CompleteFocusExecutionGameTests`: два эффекта одной цели и уменьшающие
  множители/продолжение без повторного платежа.
- `FocusTargetOrdinalGameTests`: плотная нумерация реальных Touch targets,
  агрегированный массив Plan и ordinal0 отдельного Projectile impact.
- `FocusMediaGameTests`: реальные Cloud pulses и paid reload/owner,
  Mine counter/reload/one detonation/движение/Forge impact veto и отдельная
  callback-removal регрессия на trigger tick Age 5 с положительным контролем:
  та же неудалённая мина действительно ставит paid continuation и наносит урон.
  Проверяется атакующая
  SpellBat/нажимная плита, Plan full/axis controls,
  forged paid saves и неверный UUID.
- `RemainingFocusEffectsGameTests`: Curse source/debuff probabilities/sap,
  physical picker/Exchange inventory/loot/Silk/Fortune/target aura/creative/
  callbacks/unload, все оси/глубина Rift, blacklist/BE/protection, full-state
  reload и восстановление настоящими world ticks.
- `RemainingFocusProgressionGameTests`, `FocusTierInfusionGameTests`:
  настоящие сканы, строгие parents, exact raw payments и расход hook/breath,
  warp/replay, pearl/firebat скрытые записи, обе основы через реальный
  алтарь/банки/компоненты и save/reload оплаченного Greater-процесса.

Изолированный клиентский сценарий расширен до **33 сцен + отдельный Bolt
кадр**: добавлены настоящий branch editor/C2S craft, Curse, Shift Exchange
pick и physical exchange, Rift, Cloud/Mine/Bat, Scatter projectile fan и
Plan G packet/HUD/preview. Финальный `full-019-client8` очищает fixture,
оставляет стол пустым для media-кадров, задаёт footing камеры и удаляет cow
из ракурса Mine/Bat. Все 34 снимка просмотрены в четырёх contact sheets;
split-editor, Bat-flight, Mine-armed и Rift-passage также полноразмерно.
GUI не перекрыт, missing textures не обнаружены. Для маленькой серой Mine
подтверждены armed state, ресурсы, пульс и renderer binding; детальное
попиксельное совпадение mesh этим кадром не установлено. Native FX остаются
обозначенной визуальной адаптацией.
Клиент работает в собственном QA-мире на неактивном Win32 desktop без
активации окна/переключения/глобального ввода. JVM exit 0; input desktop
Default до/после; максимум 16 окон JVM на её isolated desktop.
Поздние media-scenes явно
используют creative fixture, чтобы собственное Cloud не убило кастера;
исходная cast-vis оплата сохраняется. Исследования/часть готовых фокусов
заданы fixture. Полное survival и независимые multiplayer-клиенты этими
тестами не подтверждены.
