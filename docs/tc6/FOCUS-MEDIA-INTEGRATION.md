# Cloud / Mine / Plan / SpellBat: интегрированный контракт 0.19

Поведение и исходные quirks зафиксированы в
[FOCUS-COMPLETE-BETA26-AUDIT.md](FOCUS-COMPLETE-BETA26-AUDIT.md).
Runtime поддерживает все 21 исходное определение; эти классы выполняют
четыре оставшихся medium и их оплаченные продолжения. Фокусный срез дал
42 канонических ключа; после интеграции CENTRIFUGE общий реестр содержит
43. Срез устройств описан в [CENTRIFUGE-INTEGRATION-NOTE.md](CENTRIFUGE-INTEGRATION-NOTE.md).

## Регистрация и совместимость

`FocusMediaModule.register(IEventBus)` вызывается один раз при создании мода.
Рабочие EntityType имеют отдельные IDs: `thaumcraft:focus_cloud_spell`,
`thaumcraft:focus_mine_spell`, `thaumcraft:spell_bat_spell`.
Прежние harmless catalogue `focus_cloud`, `focus_mine`, `spell_bat`
сохраняются; их наличие не означает исполняемое заклинание. Renderer
registration принадлежит `FocusMediaRenderers`, атрибуты SpellBat —
самому модулю. Новый Exchange drop имеет отдельный `focus_exchange_item`.

## Runtime API

`FocusMedia.execute(ServerPlayer, FocusPlan, int nextIndex, FocusGraph.Node,
HitResult, Vec3 source, Vec3 direction, float power, int ordinal)`
возвращает true для Cloud/Mine/Bat, когда medium принимает управление продолжением.
**FocusExecution уже умножил power на .5/.33 текущего узла**;
media не применяет множитель второй раз. Plan обрабатывается отдельно:
`FocusMedia.planTargets` возвращает цели отдельной входной траектории,
`FocusExecution` собирает общий TARGET массив всех траекторий до запуска
веток. Plan и SpellBat supply только TARGET; выходной TRAJECTORY у них нет.
Cloud/Mine/Bat используют detached `PaidFocusContinuation` и END callbacks;
повторного FocusCasting, vis, cooldown или onCast нет.

Родитель суффикса определяется по actual child.parent и его children,
не по соседнему array index. Paid NBT хранит Graph, Capacity, Next,
Power, Ordinal, Owner и Execution UUID. Load повторно компилирует graph,
проверяет medium/settings/finite bounds; неподходящий владелец не может
bind или выполнить чужой суффикс. Сохранённая execution UUID сохраняет
same-cast hurt-window поведение при нескольких попаданиях. Effect ordinal
плотно нумерует реальные targets, исключая промахи Touch/Scatter. Plan
нумерует общий массив. Каждый отдельный Projectile impact имеет ordinal0;
старое сохранённое значение fork ordinal не добавляет задержку этому target.

Callbacks ограничены 8192 pending и detached batch512 на END HIGH.
Removed/dead target, invalid/spectator/dead/different-world owner,
unloaded source/target отменяют continuation. Unload/stop очищают pending
и общий Cloud cooldown. Отсутствующий владелец завершает media entity.

## Plan и управление

`FocusPlanArea` использует original caster NBT `areax/areay/areaz/aread`,
default/max радиусы 3. `cycle(player,0)` меняет радиусы выбранных осей,
`cycle(player,1)` меняет all/X/Z/Y. `FocusAreaNetwork` получает только
action0/1 по новой отдельной C2S channel; server сам выбирает физический
кастер с установленным Plan. `FocusAreaClient` реализует G / Ctrl+G.
Уникальные subscriber `AreaRegistration.setupArea` /
`AreaKeys.registerArea` избегают известной Forge ASM-коллизии.

Full включает непустые блоки сдвинутого внутрь объёма максимум343;
surface выбирает связанную exposed-plane того же полного BlockState,
с исходным сопоставлением осей. Trace16 и расстояние определяют targets;
preview/HUD только читают выбор и не платят/не изменяют мир.

## Подтверждённые особенности и визуальные адаптации

В BETA26 Cloud ищет living entity IDs как Integer, но записывает как Long:
living cooldown не действует, target pulses идут каждые5ticks.
У блоков Long с обеих сторон и реальные общие2000ms. Порт сохраняет этот
release quirk, разделяет map по измерениям и чистит stale entries/unload.
Исходный EntityUtils выбирает кубический AABB, не сферический фильтр.
Cloud может поражать владельца.

Mine имеет zero shoot velocity, gravity.01, arm counter40 и target cube1.
Движение/drag/gravity продолжаются после armed impact по исходному
EntityThrowable. Рабочая мина наследует Projectile, учитывает Forge impact
veto; modern moveTowardsClosestSpace сохраняет исходное quarter damping
при вытеснении из блоков. Reload armed mine делает counter0 — исходный quirk.
Если impact listener удалил мину, tick сразу завершается и не добавляет
paid deliveries; отдельный removed guard перед countdown сохраняет
original isEntityAlive behavior после arming impact.
SpellBat — 5health, target cube12, attack40, health−1, strength.33, lifetime600;
ignore block triggers сохраняет отсутствие активации нажимных плит.
Возраст Mine/Bat дополнительно сохраняется как защита от продления reload.

Модели/текстуры Mine и SpellBat перенесены, flight flapping и пульс mine
работают. Cloud и частицы media используют native dust API вместо старого
ParticleEngine/индивидуальных effect FX. Plan uses modern outline/HUD.
Визуальные адаптации не объявляются попиксельным равенством оригинала.

## Проверка

Текущий полный `validation/focus-019-gametest8.log` завершил
**577/577**, BUILD SUCCESSFUL, включая Mine movement/Forge impact veto и
callback-removal guard, SpellBat block triggers, target ordinal и CENTRIFUGE.
Отдельный итоговый `full-019-client8` прошёл **33/33** сцены, JVM exit 0;
все **34** снимка с отдельным Bolt кадром просмотрены. Input desktop
Default не изменён, максимум 16 окон JVM находились на скрытом desktop.
Media tests проверяют paid owner/strength/reload, Cloud pulse, Mine40/
одно срабатывание/reload0/motion/impact veto, атакующую SpellBat/pressure plate,
full face-offset/axis controls и forged saves. Graph/execution/ordinal tests
проверяют branch packages, reduced strength и порядок actual targets.
Новые isolated client scenes используют реальные use-item/C2S packets.
Для media-кадров очищены старые сущности/стол, cow вынесена из ракурса и
задана footing камеры. Bat-flight и Mine-armed просмотрены полноразмерно;
missing textures и перекрытий GUI не обнаружено. Для маленькой серой Mine
подтверждены armed state, ресурсы, пульс и renderer binding; подробное
совпадение mesh не установлено. Native particles остаются адаптацией.
Полные результаты, включая отдельные 12 сцен EssentiaProduction и 46
просмотренных снимков, — в [VALIDATION.md](../../VALIDATION.md) и
[отчёте 0.19](../../validation/artifact-report-0.19.json).
Полное survival и отдельные независимые
multiplayer-клиенты этим QA не подтверждаются.
