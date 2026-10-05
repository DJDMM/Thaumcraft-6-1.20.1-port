# Действующие големы, размещение и колокольчик — BETA26

Эталон — официальный `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Зеркало Java — commit `954022bb777b7546281fb36df8522f0ba6b43f81`.
Прочитаны `EntityThaumcraftGolem`, `EntityOwnedConstruct`, `ItemGolemPlacer`,
`ItemGolemBell`, `AIFollowOwner`, `AIGotoHome`, `AIArrowAttack`,
`GolemArmDart`, `GolemLegWheels`, `GolemLegLevitator`, обе реализации
навигации, `EntityGolemDart` и `EntityGolemOrb`. Противоречия декомпилятора
проверены `javap -c -p` по pinned JAR. Этот документ описывает контракт
реализации; результаты QA указываются отдельно только после реального запуска.

## Размещение и владение

`GolemPlacerItem` заменяет визуальный предмет с существующим ID
`thaumcraft:golem`. Поставить его можно на сторону твёрдого блока.
Сервер создаёт сущность в соседней клетке с координатами x/z +0.5,
назначает UUID игрока, `validSpawn`, исходные big-endian `props`, `xp`
и дом в клетке появления. Полный валидный сохранённый rank допускается
при повторном размещении; это отличается от производства новых rank0
в прессе. Владение и размещение предмета не требуют дополнительного
исследования: оригинальные исследования ограничивают получение деталей
и рецептов. Не вводится искусственный research gate на приобретённый placer.

Наследуемые базовые характеристики — движение0.3, follow range40,
начальные10HP и reward5XP. При появлении исходный placer вызывает
`onInitialSpawn/updateEntityAttributes`, которые задают максимальное
здоровье по материалу, но **не лечат до максимума**: WOOD появляется
с10/16HP, IRON с10/30HP. Это подтверждено отсутствием вызова setHealth
в pinned placer. Постепенное восстановление лечит1HP каждый100ticks;
REPAIR сокращает период до40. Свойства и расчёт максимумов описаны в
[аудите конструкции](GOLEM-DESIGN-BETA26-AUDIT.md).

UUID владельца, флаг валидного появления, `props`, `rankXP`, `gflags`,
`color`, `homepos` и руки сохраняются. Исходные `OwnerUUID` строки и
современные UUID tags читаются. Визуальные старые saves `VisualOnly`
не получают автоматически владельца, разрешённое появление или AI.
Оригинальные невалидные summon/spawn-egg constructs удаляются на сервере;
готовый placer является штатным путём создания действующего голема.

## Управление и дом

Владелец с зажатым sneak может забрать голема **любой рукой/предметом**:
голем сбрасывает переносимые стеки и ровно один placer с `props`/`xp`,
освобождает задачу и удаляется. Цвет, дом и прежний владелец не
переносятся в placer — оригинальный предмет сохраняет только свойства
и опыт. Чужой игрок не может управлять или забирать construct.

Обычное применение колокольчика к голему переключает following/stay
при **начатом** `GOLEMDIRECT`, без требования полного завершения.
Following — bit1 (`2`), combat — bit3 (`8`). Режим следования выбирает
другие goals и не исполняет seal tasks. Оригинальный вызов
`detachHome()` сразу сопровождается `updateEntityAttributes()`, который
снова назначает сохранённый home/radius; эта особенность сохранена:
following имеет сохранённый дом, но не использует home/task goal.
Stay переносит дом в текущую клетку. Радиус32, для SCOUT48;
follow range40/56. Follow goal начинает движение при расстоянии10,
заканчивает возле2, перестраивает путь каждые10ticks и, если путь
недоступен при расстоянии>=12, пробует безопасный внешний периметр
5×5 вокруг владельца. Скорость этого goal1.0, как в оригинале.

Home goal проверяется после первых10проверок, затем с паузой50;
движение начинается при squared distance>=5 и останавливается около3.
Исходная recovery-процедура first-run/in-wall/out-of-world ищет вверх
до твёрдого потолка и переносит к дому только при свободном bounding box.
Проверка ограничена высотой мира и уже загруженными клетками.

Оригинальные dye metadata хранятся в color1..16: black1, white16.
Современное соответствие — `16-DyeColor.id`. Краситель расходуется
и в creative, как в исходном entity interaction. Color0 означает
отсутствие цветового ограничения задач.

## Руки, задачи и бой

Обычный голем переносит один стек в MAINHAND, HAULER — два в MAINHAND
и OFFHAND. Item/damage/full NBT должны совпадать для объединения.
Пустая рука принимает предложенный стек целиком, как в оригинале;
расчёт свободного пустого слота использует64. `dropItem(null)` снимает
первый стек, запрос снимает не больше запрошенного количества. HAULER
перемещает вторую руку в первую после освобождения. Публичные списки
и возвращаемые стеки отделены копиями от состояния сущности.

`ThaumcraftGolemEntity` реализует `SealWorker` и включает реальный
`SealTaskGoal` с priority3. Core управляет claim/path/perform/release
по серверным задачам, home, цвету и traits. Pickup, смерть, удаление,
смена following и перестройка properties освобождают reservation.
Нет клиентского назначения задач или NoAI-имитации.

FIGHTER включает melee1.15, ответ на нанесённый урон и, при following,
защиту владельца от последнего нападавшего/цели владельца. RANGED
включает оригинальную dart AI: range16, интервалы20..25ticks,
20видимых ticks до остановки пути, сброс цели за32блоками.
Материал/BRUTAL/rank задают реальные attack/armor attributes.
FIREPROOF исключает fire damage, любой голем игнорирует cactus;
BLASTPROOF ограничивает explosion damage до min(maxHP/2,damage×0.3).
FLYER и CLIMBER не получают fall damage. WHEELED задаёт step0.5,
остальные0.6. SCOUT изменяет home/follow range.

SMART получает8XP за фактически убитого Mob. Порог `(rank+1)^2×1000`,
максимум rank10. За одно начисление ранг повышается **не больше одного**,
избыток XP сохраняется. Исходный `setProperties` после rank award не
вызывает `updateEntityAttributes`, поэтому новые статистики применяются
при последующей перестройке/загрузке, а не мгновенно. Этот timing сохранён.
На смерти каждый компонент выпадает с вероятностью0.3+looting×0.15;
при успехе количество сокращается на случайное значение0..count-1.
Переносимые вещи отделены от этих вероятностных компонентов.

## Projectiles и визуальные функции

Существующие IDs `golem_dart`/`golem_orb` обслуживаются действующими
projectiles. Dart — исходный небольшой vanilla arrow subclass:
speed1.6/inaccuracy3, damage=attack/3+range+gaussian×0.25,
вертикальное прицеливание eyeY+range². Native arrow обеспечивает
collision/drag/owner-ignore; стрелу от golem-shooter нельзя подобрать.

Orb — ThrowableProjectile без gravity, lifetime160 или red240ticks.
Наведение прибавляет delta/distance²×0.2, с float clamp по каждой оси
[-0.25,0.25]; здесь намеренно не заменено distance² на длину вектора.
Entity impact причиняет indirect magic damage owner.attack×0.6
или red×1.0, любой impact удаляет orb. Удар направляет скорость
по взгляду нападавшего с коэффициентом0.9. Оригинал сохраняет в NBT
ни target, ни red: reload нейтрален и без наведения. Spawn sync
передаёт текущие target ID/red. Native particles заменяют старый FXDispatcher;
оригинальные модели/цвет/движения клиента подключает renderer модуля.
Колесо использует исходный расчёт поворота от движения. Levitator
производит native visual particles при движении/каждые5ticks на земле.

## Современные адаптации и границы проверки

- Server placement проверяет владение текущим стеком, range, edit/spawn
  protection, загруженность, build height и реальные collision. Платёж
  идёт после успешного Forge EntityJoinLevelEvent; canceled/reentrant
  размещение не расходует предмет. Некорректные props/XP/rank ограничены.
- Pickup резервирует отделённые переносимые стеки до принятия всех
  ItemEntities. Если Forge отменяет любой output, частично созданные
  outputs удаляются, действующий голем/руки остаются. Native
  spawnAtLocation игнорирует результат addFreshEntity, поэтому здесь
  используется явная компенсируемая операция.
- Навигация использует современные Ground/Climber/Flying адаптеры.
  До построения native PathNavigationRegion проверяется весь регион,
  чтобы AI не загружал отсутствующие chunks. Стандартный путь может
  отложиться у границы загрузки; это явная адаптация к современной
  гарантии loaded-only, а не изменение радиуса traits.
- Союзными признаются одинаковые owner UUID даже при offline владельце;
  friendly/PVP/creative/spectator checks не допускают запрещённые цели.
  DEFT через современный публичный API назначает реального владельца
  player-hit credit вместо записи чужого protected recentlyHit field.
- Стандартные звуки clack/tool/zap/scan/shock сохранены; shared IDs
  tool/zap/scan имеют единственного владельца регистраций.
- Колокольчик работает с настройкой/удалением физических seals и
  GOLEMLOGISTICS меню; их авторизация, рецепты, providers и проверка
  сетевых запросов описаны в аудите seal core. Изготовление поздних
  частей остаётся отдельным strict research/recipe вопросом, а валидная
  operational модель не является доказательством полного survival пути.

В `GolemEntityGameTests` добавлены13сценариев: действующее размещение/
debit, invalid/collision/Forge cancel, owner pickup/повтор/carry,
merge/tags/HAULER detach, persistence/VisualOnly, rank timing/cap,
bell/dye, traits/real damage/heal, melee kill/dart, фактическая home
навигация, фактическое следование за владельцем вокруг стены после
применения колокольчика, canceled pickup/reentry и real orb damage/steering/reload.
Исследования/материалы и arena явно заданы fixtures. Home fixture
изолирует home goal от соседних QA seals; реальные seal tasks проверяет
отдельный core suite. Здесь **не заявлен их успешный запуск**:
финальные серверные/клиентские результаты принадлежат VALIDATION.md.

Follow fixture добавляет FakePlayer в настоящий индекс ServerLevel,
проверяет разрешение UUID владельца и запускает follow через реальный
`mobInteract` с колокольчиком и начатым GOLEMDIRECT. Расстояние sqrt128
включает follow (>=10), но не допускает исходный fallback teleport (>=12).
Двухблочная стена перекрывает прямую диагональ. За160 обычных серверных
ticks проверяются активный native path, >=15ticks движения, проход через
отверстие и конечное расстояние<3. Каждый шаг<1 блока исключает teleport;
fixture не вызывает tick/moveTo/setPos голема после запуска. Player и
golem очищаются после проверки и при её ошибках.

## Независимая сверка renderer: невидимость и силуэт через стену

`RenderThaumcraftGolem.renderModel` прочитан в зеркале и проверен
`javap -c -p` по pinned JAR. Нормальный проход исполняется при
`!golem.isInvisible()`. Если голем невидим, но
`!golem.isInvisibleToPlayer(localPlayer)`, всё равно вызывается renderParts:
начальный цвет RGBA=(1,1,1,0.15), depth write выключен, depth test остаётся
включённым, SRC_ALPHA/ONE_MINUS_SRC_ALPHA, alpha threshold=1/255.
Это стандартная player/team visibility, а не отдельное разрешение
владельцу: EntityOwnedConstruct и EntityThaumcraftGolem не переопределяют
isInvisibleToPlayer.

Второй проход **независим** от первого и от невидимости: localPlayer
приседает, в любой руке держит ISealDisplayer и между серединой тела
игрока и серединой тела голема прерывается block raytrace. Тогда
renderParts вызывается с начальным RGBA=(0.25,0.25,0.25,0.25), depth test
и depth write выключены, blend/alpha threshold как выше. ItemGolemBell
и ItemSealPlacer оба реализуют ISealDisplayer. Проверки UUID/owner,
GOLEMDIRECT/GOLEMLOGISTICS и специального радиуса в renderer нет.
Raytrace: (player.x,player.y+player.height/2,player.z) ->
(golem.x,golem.y+golem.height/2,golem.z), stopOnLiquid=false,
ignoreBlockWithoutBoundingBox=true, returnLastUncollidableBlock=false.
После прохода восстанавливаются depth test/write и threshold0.1.
Исходные renderParts/part handlers могут локально менять RGBA, поэтому
перечисленные значения описывают состояние на входе прохода.

В современном renderer оба прохода подключены: team visibility использует
полупрозрачный NEW_ENTITY слой без depth write, а перекрытый block raytrace
при Shift/ISealDisplayer выбирает отдельный слой без depth test/write.
Цвет и alpha передаются в вершины вместо глобального GL state. В отличие
от случайных сбросов RGBA внутри старых part handlers, материал и флаг
сохраняют alpha данного прохода. Переносимые предметы используют современный
vanilla ItemRenderer; исходные OBJ/UV и их transforms сохраняются.
Новый renderer использует EntityRenderer, поэтому старые RenderLivingEvent
hooks не импортируются автоматически. Печати представлены плоским sprite
вместо старого extruded-item объёма0.1; исходные area/corner textures и
радиус/цвет/Shift visibility подключены отдельно в SealWorldRenderer.
