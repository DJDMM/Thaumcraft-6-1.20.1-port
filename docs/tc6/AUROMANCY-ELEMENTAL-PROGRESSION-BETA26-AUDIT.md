# Стихии и снаряды: исследования и наблюдения — BETA26, этап 0.17

Эталон — официальный `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Читаемый источник закреплён на commit `954022bb777b7546281fb36df8522f0ba6b43f81`.
Проверены оригинальные `assets/thaumcraft/research/auromancy.json`,
`ConfigResearch`, `EntityEvents.entityHurt`, `FocusMediumProjectile` и
`FocusEffectAir/Frost/Earth`. Для predicates использован также `javap -c -p`
закреплённого JAR. TC4/TC5, addon-исследования и головоломка с шестигранниками
не применяются.

## Две канонические записи

К 23 поддерживаемым записям этапа 0.16 добавлены `FOCUSELEMENTAL` и
`FOCUSPROJECTILE`: всего **25**. Оба исходных bare parent — `BASEAUROMANCY`;
требуется его полное завершение, а не начало или достижение стадии 2.
Исходные JSON этих двух записей сохранены без изменения требований.

| Запись | Оплата и подтверждение | Завершение |
| --- | --- | --- |
| FOCUSELEMENTAL | Стадия 1: 1 OBSERVATION AUROMANCY = 16 raw; обнаруженные `!aer`, `!gelum`, `!terra`. | Стадия 2 — пустое заключение; сохранённая завершённая стадия 3. |
| FOCUSPROJECTILE | Стадия 1: 1 THEORY AUROMANCY = 32 raw и обнаруженный `!motus`. Стадия 2: `f_arrow`, `f_fireball`, `f_spit`. | Стадия 3 — пустое заключение; сохранённая завершённая стадия 4. |

AIR/FROST/EARTH проверяют строгое полное `FOCUSELEMENTAL`.
PROJECTILE проверяет строгое `FOCUSPROJECTILE@2`: после первой оплаты
доступны исходные скорость 1–5 и option 0. Ненулевые options 1–3 дополнительно
требуют полностью завершённое `FOCUSPROJECTILE`. Регистрация 21 исходной
node definition не открывает остальные неподдерживаемые механики.
`FOCUSBOLT`, `FOCUSCLOUD`, `FOCUSFLUX`, `FOCUSBREAK`, `FOCUSADVANCED`,
`FOCUSGREATER`, `FORTRESSMASK` и прочие поздние descendants остаются закрыты.
Достижение parent `FOCUSPROJECTILE@2` не объявляет BOLT реализованным.

## Два разных способа получения projectile facts

Оригинальный hurt hook проверяет `FOCUSPROJECTILE@2` и **immediate/direct**
entity источника. Стрелок, owner, общий projectile damage tag и огненный
damage tag не заменяют непосредственную сущность. Соответствующий факт
сохраняется один раз с обычной синхронизацией знания и `got.projectile`.
Отдельная проверка `BASEAUROMANCY@2` для `f_onfire` продолжает работать:
новая projectile ветка не зависит от её успешности.

У оригинальных `ScanEntity`/`ScanItem` из `ConfigResearch` стадийного gate
**нет**. Поэтому настоящий скан может записать эти факты ещё до начала
исследования снарядов. Они не создают stage grant, не оплачивают теорию и
не завершают исследование без запроса книги.

| Факт | Исходный hurt predicate после `@2` | Исходный scan predicate без gate |
| --- | --- | --- |
| f_arrow | Сущность наследует EntityArrow. | Такая сущность либо обычный `Items.ARROW`, включая выброшенный ItemEntity. |
| f_fireball | Сущность наследует EntityFireball. | Такая сущность. |
| f_spit | Сущность наследует EntityLlamaSpit. | Такая сущность. |

Третье требование — **плевок ламы**, а не снежок. Снежок как предмет может
дать Gelum при обычном aspect scan, но не `f_spit`. Egg и trident не являются
исходными доказательствами. Обычная item-стрела допускает переименование,
размер стака и посторонний NBT; tipped/spectral **предметы** не подменяют
`Items.ARROW`. Spectral **сущность** в исходнике наследует EntityArrow и подходит.
Собственный TC6 `EntityFocusProjectile` наследовал EntityThrowable: рабочий
снаряд фокуса сам по себе не является arrow/fireball/spit scan proof.
Наличие модели или catalogue placeholder тоже не создаёт доказательство.

Уже отправленный `LivingHurtEvent` оригинал не фильтрует дополнительным
условием `amount > 0`. Тест с нулевым amount проверяет именно predicate этого
hook; он не утверждает, что нулевое событие уменьшает здоровье. Порт исключает
dead/spectator/off-thread player; action-bar доставка проверяет connection
для offline QA, сохраняя сам факт и SavedData.

## Сохранение исходного наследования на Minecraft 1.20.1

Современный `Fireball` уже не является общим предком всех исходных вариантов.
Это проверено отдельно по официальному vanilla Minecraft 1.12.2 client JAR
из [метаданных Mojang](https://piston-meta.mojang.com/v1/packages/832d95b9f40699d4961394dcf6cf549e65f15dc5/1.12.2.json):
SHA-1 `0f275bc1547d01fa5f56ba34bdc87d981ee12daf`, размер 10 180 113 bytes,
SHA-256 `8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f`.
Entity registry `vi` связывает large_fireball/aen, small_fireball/aes,
wither_skull/afb, dragon_fireball/aei. Заголовки всех четырёх классов через
`javap` подтверждают общий superclass `ael` — EntityFireball.

Поэтому modern predicate — `Fireball || WitherSkull || DragonFireball`,
а не только Small/LargeFireball и не произвольный AbstractHurtingProjectile.
Ранняя рабочая заметка, исключавшая wither skull из старого наследования,
была неточной; это уточнение подтверждено оригинальным vanilla binary.
Для стрел применяется `AbstractArrow` с исключением нового `ThrownTrident`.
Это сохраняет исходные семейства и не придумывает trident requirement.

## Настоящая транзакция таумометра

`ThaumometerItem` восстанавливает target из серверного inventory/луча:
тот же range 9, block occlusion, ближайший bounding-box hit, существующая
сущность и held scanner. Native fact-eligible projectiles допускаются в
выбор лучом даже при `!isPickable`. Predicate не включает все Projectile
и не добавляет особое разрешение для собственного снаряда TC6.

Нулевое число аспектов не блокирует оригинальный специальный scan fact.
Aspect discovery и `recordScannedFact` совершаются независимо: если старое
сохранение уже кредитовало аспекты обычной стрелы, повторный настоящий скан
может дать отсутствующий `f_arrow` без повторной observation награды.
Fact-only scan не создаёт выдуманные аспекты, scan key или знания. Уже
известные факты и композиции повторно не награждаются; стаки не расходуются.

HUD `ScanningNetwork.capture` допускает такой target с пустым aspect list.
`hasUnseenScanFact` — чистая проверка: наведение, snapshots и просмотр книги
не пишут SavedData. Объект для scan commit берётся из настоящей серверной
цели; нового client discovery packet нет. Cooldown и проверка физически
удерживаемого таумометра остаются действующими.

## Числа наполнения не изменились

У двух новых записей нет новых infusion recipe gates. Как в этапе 0.16,
достижимы **9 из 56** путей, **47** сохраняют поздние gates. Точные пути:

- `infusion/bootstraveller`;
- `infusion/elementalaxe`;
- `infusion/elementalsword`;
- `infusion/elementalpick`;
- `infusion/elementalshovel`;
- `infusion/elementalhoe`;
- `infusion/thaumiumfortresshelm`;
- `infusion/thaumiumfortresschest`;
- `infusion/thaumiumfortresslegs`.

Это достижимость исходных канонических research paths, а не автоматическая
выдача девяти рецептов новому персонажу. [Предыдущий аудит](AUROMANCY-PROGRESSION-BETA26-AUDIT.md)
сохраняет исходные bare known gates рецептов и требования ранней цепочки.
Полная ауромантия и поздние effects/media не заявляются готовыми.

## Проверки изменения

Добавлены **7** server GameTests в `ElementalProgressionGameTests`:

1. Строгий завершённый basic parent, ровно 25 канонических записей, no-book
   отказ и закрытые поздние descendants.
2. Цепочка двух новых записей через настоящий held-item таумометр, discovery
   Aer/Gelum/Terra/Motus, точные платежи 16/32 raw и Forge-dispatched native
   hurt events. В этой цепочке новые stages и projectile facts не выдаются
   вручную; missing/stale requests не меняют знания и XP частично.
3. Stage-2 hurt gate, direct против true source, zero-amount predicate,
   повтор и сохранение факта.
4. Small/LargeFireball/WitherSkull/DragonFireball и spectral entity,
   исключения trident/snowball/egg и настоящий зарегистрированный
   FocusProjectileEntity, не catalogue placeholder.
5. Настоящий ранний item-arrow scan после уже кредитованной идентичной
   aspect composition; extra NBT, без расхода стака, повторной награды
   или stage grant; отличие предметных spectral/tipped arrows.
6. Реальные ray-selected Arrow/SmallFireball/LlamaSpit, нулевые аспекты,
   read-only hover и actual scanner use без фиктивного aspect credit.
7. Строгие compiler gates: завершённое Elemental, Projectile `@2` и отдельное
   полное завершение для ненулевого option.

Upstream BASEAUROMANCY и кошельки знаний — явно подготовленные изолированные
fixtures. Boundary/consumer tests отдельно выставляют новые стадии для
проверки конкретных predicates; это не подмена интеграционной цепочки из
пункта 2. Hurt tests отправляют настоящий Forge event с native direct entity,
но не заявляют полный бой в survival. Реальный spell projectile/effects
проверяются также отдельными runtime тестами соответствующего модуля.

Агент этой части не запускал Gradle или клиент. Написанные проверки и
статическая сверка API не означают успешного общего прогона. Итоговые
server/client результаты, собранный JAR и ограничения фиксирует общий
`VALIDATION.md` после проверки интеграции.
