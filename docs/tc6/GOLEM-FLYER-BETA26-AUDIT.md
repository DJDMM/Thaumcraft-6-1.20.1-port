# Летающий голем — BETA26

Эталон — официальный `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Исходный mirror commit `954022bb777b7546281fb36df8522f0ba6b43f81`.
Проверены `javap -c -p` для `EntityThaumcraftGolem$FlyingMoveControl`,
`PathNavigateGolemAir`, `FlightNodeProcessor`; прочитаны `EntityThaumcraftGolem`,
`AIFollowOwner`, `GolemLegLevitator` и оригинальные part definitions.

## Свойства и движение

Ноги FLYER — исходный индекс3 в байте legs, то есть `3L << 32` для
остальной стандартной деревянной конструкции. Их gate — завершённый
`GOLEMFLYER`, компоненты — Levitator1, латунные пластины4, slimeball1,
simple mechanism1. Traits FLYER и FRAGILE участвуют в общем оригинальном
порядке отмены противоположных traits. Это не отдельный разрешающий флаг AI.

FLYER снижает итоговый множитель движения на0.33. Для обычного деревянного
голема с LIGHT получается0.87, maxHP12, armor1; FRAGILE умножает здоровье/броню
на0.75 до преобразования в int. Исходное Java mirror содержит неверный
порядок cast, официальный bytecode подтверждает умножение до cast.
Начальное здоровье по-прежнему10, а не автоматическое заполнение максимума.

Оригинальный `FlyingMoveControl` получает желаемую точку от навигатора.
При MOVE_TO он вычисляет длину полного 3D вектора и прибавляет к motion
нормализованные X/Z компоненты с коэффициентом0.033×speed,
Y с коэффициентом0.0125×speed. Вертикальная тяга слабее горизонтальной.
Когда расстояние меньше среднего размера bounding box, helper переходит
в WAIT и **однократно** сокращает motion по всем осям вдвое. WAIT не
добавляет искусственного вертикального покачивания/тяги. Yaw/bodyYaw
направлены по горизонтальному motion, в бою — к цели. Эти формулы
реализует действующий `GolemFlyingMoveControl`.

Гравитация отключена, падение не наносит урон. В оригинале нет отдельного
travel override или ванильного FlyingMoveControl: обычный LivingEntity
обрабатывает заданное motion, столкновения и drag с noGravity. Перенос
аналогично использует движение LivingEntity1.20.1 и не создаёт noclip.
Drag и столкновения остаются современными vanilla/Forge: это адаптация
движка, а не обещание побитово одинаковой траектории между1.12.2 и1.20.1.
При смене на нелетающую конструкцию rebuild сбрасывает noGravity и
возвращает Ground/Climber navigator и обычный MoveControl.

## Поиск воздушного пути

Предварительный0.23 использовал vanilla `FlyNodeEvaluator`; он не был
точным аналогом исходного TC6. Теперь `GolemNavigation.Air` устанавливает
собственный `FlightNodes`, который сохраняет:

- ровно шесть соседей по EnumFacing/Direction, без диагональных bird nodes;
- старт по floor(minBoundingBoxX), floor(minY+0.5), floor(minZ);
- проверку полного занимаемого объёма entityWidth×entityHeight×entityDepth;
- проход только через air/исходно passable клетки, без требования пола;
- отсутствие водного штрафа и наземных опасностей в cost: исходный public
  getPathNodeType возвращает WATER, но getWaterNode создаёт только свободные
  WALKABLE nodes с обычным нулевым costMalus;
- проверку пути от середины тела и переход к следующему узлу при distance²
  меньше width²;
- пропуск до шести следующих узлов, только если distance²<=36 и collider
  raytrace свободен; конечная точка луча дополнительно поднята наheight/2;
- разрешённое обновление пути независимо от нахождения на земле/в воде.

Современный PathFinder передаёт целевые **целочисленные BlockPos**, поэтому
FlightNodes переводит x/z к центру блока перед вычитанием половины ширины.
Иначе механическое перенесение старой формулы смещало бы target на один
блок на запад/север. Modern PathFinder сохраняет собственный ограниченный
поиск и stuck detection. Legacy Block.isPassable адаптирован через пустой
collision shape или `isPathfindable(AIR)`: растения, жидкости и открытые
двери могут проходиться, полный solid блок не может. Raytrace использует
COLLIDER/Fluid.NONE, как исходные stopOnLiquid=false и collision-only ray.

Существующая гарантия loaded-only сохранена: перед созданием native
PathNavigationRegion проверяется весь регион, каждый целевой chunk и
build height проверяются без загрузки отсутствующих chunks. Это явное
ужесточение переноса; возле края загруженного мира AI может отложить путь.

## Владелец, дом, печати и сохранение

Летающая конструкция использует те же оригинальные home/follow goals,
переключение колокольчиком, seal claim и расстояния исполнения. Для
FollowOwner сохраняются начало>=10, окончание около2, recalc10ticks и
fallback teleport только при неудачном path и distance>=12; flight сама
по себе не включает отдельную телепортацию. При following нет seal tasks.

Air navigator вызывается из реального SealTaskGoal. Предмет на высокой
платформе не выдаётся голему удалённо: сначала нужен путь и claim,
затем фактическое приближение и штатная completion соответствующей печати.
Props, owner, home, rankXP и действительные руки сохраняются; текущий
path/task/claim не записываются в NBT. Исходные исследовательские gates
производства не заменяются проверкой наличия FLYER trait у сохранённого
валидного предмета. Размещение приобретённого placer сохраняет общие
правила действующего предмета и расходует его после успешного spawn.

Клиентская функция GolemLegLevitator вызывает original fly particle
при !onGround либо каждом пятом grounded tick, точка y+0.1, velocity
(gaussian/100,-0.1,gaussian/100). Современный renderer и native particle
заменяют FXDispatcher; это визуальная адаптация, не функция подъёма.

## Проверки этого среза

Добавлен `GolemFlyerGameTests` с шестью сценариями:

1. Физическое применение placer, единственный debit, восстановление
   FLYER/owner/home/XP/cargo и отсутствие сохранённого task.
2. Audited anisotropic acceleration, arrival damping и WAIT/no-fall.
3. Реальный PathFinder строит воздушный путь выше стены: шесть face nodes,
   elevated target без пола, ни одного solid node.
4. Обычные ServerLevel ticks действительно поднимают голема к новому
   высокому home и удерживают над землёй: нет ручных tick/move/teleport.
5. Владелец включается реальным bell interaction; native follow проходит
   над стеной и разрывом пола, каждый шаг<1, initial distance10..12
   исключает fallback teleport.
6. Физическая pickup seal даёт claim, native путь и действительный сбор
   трёх diamonds с высокой платформы; cargo переживает reload, task нет.

Материалы/исследование Direct/грамотно загруженная arena являются fixtures.
Эти тесты не устанавливают весь survival путь производства Levitator и
не устанавливают визуальное качество частиц. Успех полного запуска и
клиентские свидетельства записываются в VALIDATION.md **после выполнения**,
а не выводятся из наличия тестов или этого аудита.
