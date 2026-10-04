# Curse / Exchange / Rift: интегрированный контракт 0.19

Полный BETA26 контракт — в
[FOCUS-COMPLETE-BETA26-AUDIT.md](FOCUS-COMPLETE-BETA26-AUDIT.md).
Рабочая реализация находится в `thaumcraft.auromancy.remaining`;
все три эффекта включены в compiler/runtime/исследования/манипулятор.
42 канонических ключа фокусного среза дополнены CENTRIFUGE следующего пункта;
текущий общий реестр содержит 43, см. [контракт устройства](CENTRIFUGE-INTEGRATION-NOTE.md).

## Вызовы и регистрация

`RemainingFocusEffects.apply(level, caster, node, target, direction,
finalPower, ordinal)` выполняет эффект после серверной проверки.
`playCastSound(level,caster,key)` вызывается при первоначальном cast,
а не повторно на delivery. Curse возвращает false даже после damage/
ailments/sap; это исходное поведение, не отмена оплаты.

`RemainingEffectsModule.register(modBus)` регистрирует working hole BE,
оригинальный jacobs sound и **отдельный** working Exchange ItemEntity
`thaumcraft:focus_exchange_item`. Catalogue `special_item` сохраняется
в `VisualEntitiesModule`: **не пропускать и не регистрировать его второй
раз**. RiftHoleRenderer отдельно регистрирует BE и ItemEntityRenderer
через уникальный `registerRiftHoleRenderer` subscriber.

В `CatalogBlocks.create` исходные IDs `hole` и `effect_sap` используют
`RemainingEffectsModule.handlesBlock/createBlock`; новых duplicate blocks
нет. `jacobs.ogg` скопирован из pinned original JAR, sounds.json/register
согласованы; число original OGG увеличено с31 до32.

## Физический Exchange picker и swap

На sneaking block use после рабочих device/ritual consumers кастер вызывает
`FocusBlockPicker.pick(serverPlayer,hand,pos)`. Успех означает free sample
selection и SUCCESS, без cast. `isPicker(held)` подавляет sneaking cast,
`picked(held)` возвращает original top-level `picked` ItemStack NBT только
при установленном Exchange. Client не пишет выбранный NBT. Sample count1
получается через modern state-sensitive silk loot; block entities/
контейнеры и unloaded/out-of-range позиции исключены.

Обычная delivery ставит detached swap в END queue: source BlockState,
копия выбранного item, физический owner, Silk/Fortune и target cost
`.25+Silk*.25+Fortune*.1`. Survival платит один exact-NBT sample из main
inventory и vis, собирает real loot; creative всё равно требует достаточную
ауру для проверки, но этот дополнительный debit/sample/loot пропускает.
Caster cast-vis оплачивается отдельно даже creative.
Same-block item swap ничего не меняет. Replacement использует default
state; nonblock item становится working SpecialItem с compensated gravity
и explosion immunity. Истинная precedence complexity Exchange сохранена:
при штатных settings `3*(Fortune+1)`, Silk не добавляет complexity.

Queue максимум4096, transient detached/reentry guarded. Loaded/owner/
permission/source state проверяются снова после Forge placement/loot
callbacks. Placement veto или исчерпание target aura не оставляют partial
payment; failure установки возвращает sample/vis. Unload/stop чистят work.

## Curse и сап

Curse damage `(1+power)*finalPower`, Poison и probabilistic
Slow/Weakness/Fatigue/Hunger/Unluck с threshold.85, уменьшающимся на.15
только после успешного предыдущего effect. Terrain sphere проверяет
центр нижнего solid block относительно hitVec, затем размещает sap в
воздухе над ним; радиус `min(8,1.5*power*finalPower)`.

`effect_sap` invisible/light7/no loot; contact END даёт Wither40/amp0,
Slow40/amp1,Hunger40/amp1,ambient. Existing Wither не обновляется,
original Eldritch roster исключён. Random tick удаляет sap. Цветные
частицы modern native, jacobs ambient звук оригинальный.

## Rift passage

RIFT создаёт original temporary3×3 portable passage. Depth8/16/24/32
умножается на finalPower, исходный byte distance+1 включает terminal air;
duration2–10seconds. Первый center BE tick создаёт perpendicular plane
и следующий center. При countdown восстанавливается полный registry
BlockState; сохранение/загрузка сохраняют свойства/остаток времени.

Container/BE/bedrock/hole/unbreakable и original blacklist исключены.
Water/replaceable plants не становятся проходом, terminal air допускается.
Loaded-only/owner/Forge protection — явная адаптация. Старые числовые
block IDs/metadata заменены modern full-state NBT. Звёздные faces используют
End Portal shader, edge sparkles — native particles.

Это эффект временного прохода. Мировая сущность Flux Rift и её consumers
остаются другой подсистемой; новый Outer Lands dimension не создаётся.

## Проверка и границы

Текущий полный `validation/focus-019-gametest8.log` завершил
**577/577**, BUILD SUCCESSFUL, включая исправления ordinals/media,
центрифугу и последнюю Mine callback-removal регрессию.
Отдельный итоговый `full-019-client8` прошёл **33/33** сцены, JVM exit 0;
все **34** снимка с отдельным Bolt кадром просмотрены, включая полноразмерный
Rift passage. Missing textures и перекрытий GUI не обнаружено.
RemainingFocusEffectsGameTests проверяют реальные damage/debuff/terrain/
sap, физический sample и inventory payment, loot/enchantments/aura/
creative, Forge veto/callback state change, detached work/unload,
three-axis passage/blacklist/container/full-state reload/world restoration.
Канонические research tests проверяют реальный scan Pech Wand, строгие
parents/exact payments и normal/permanent warp без replay.

Новые client scenes проверяют настоящее use-item, Shift picker,
physical swap и проход; итоговые full-019-client8 log/captures/visual review,
отдельные 12 сцен EssentiaProduction, 46 просмотренных снимков и прошедшие
build/resource проверки — в [VALIDATION.md](../../VALIDATION.md) и
[отчёте 0.19](../../validation/artifact-report-0.19.json). Не объявлять полный
survival, Pech/FireBat world sources, Flux Rift или старые FX завершёнными
только потому, что эти effects/runtime tests работают.
