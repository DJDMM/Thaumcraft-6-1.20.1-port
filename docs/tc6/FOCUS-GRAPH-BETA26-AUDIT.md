# Focus graph/package — официальный TC6 6.1.BETA26

Цель: Forge1.20.1/Java17, этап0.16. Эталон — закреплённый
`Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Читаемый источник соответствует commit `954022bb777b7546281fb36df8522f0ba6b43f81`;
необычные условия ниже проверены также `javap -c -p` по официальному JAR.
TC4/TC5 и addon-фокусы не используются.

## Что зарегистрировано и что работает

`ConfigItems.initItems` регистрирует **21 node definition**: ROOT, семь остальных
medium, десять effect и три mod. Порт сохраняет весь этот фиксированный реестр
в `FocusNodeRegistry` как справочные неизменяемые данные: исходные key, research,
аспект, тип, icon, цвет, диапазоны/list setting values, подписи, optional setting
research, supply contracts, exclusive и формулы complexity/power multiplier.

Только **ROOT → thaumcraft.TOUCH → thaumcraft.FIRE** объявлены runtime supported.
Компиляция отклоняет остальные узлы и другую форму графа. Наличие metadata,
картинки или описания позднего узла не означает работающий projectile/cloud/
mine/bolt/bat, mining/exchange/heal/curse/rift либо scatter/split.

| Node key | Research | Аспект | Complexity | Runtime0.16 |
| --- | --- | --- | --- | --- |
| ROOT | BASEAUROMANCY | — | 0 | да |
| thaumcraft.TOUCH | BASEAUROMANCY | aversio | 2 | да |
| thaumcraft.BOLT | FOCUSBOLT | potentia | 5 | нет |
| thaumcraft.PROJECTILE | FOCUSPROJECTILE@2 | motus | 4+(speed−1)/2 + option0/3/5/5 | нет |
| thaumcraft.CLOUD | FOCUSCLOUD | alkimia | 4+radius×2+duration/5 | нет |
| thaumcraft.MINE | FOCUSMINE | vinculum | 4 | нет |
| thaumcraft.PLAN | FOCUSPLAN | fabrico | 4 | нет |
| thaumcraft.SPELLBAT | FOCUSSPELLBAT | bestia | 8 | нет |
| thaumcraft.FIRE | BASEAUROMANCY | ignis | duration+power×2 | да |
| thaumcraft.FROST | FOCUSELEMENTAL | gelum | duration+power×2 | нет |
| thaumcraft.AIR | FOCUSELEMENTAL | aer | power×2 | нет |
| thaumcraft.EARTH | FOCUSELEMENTAL | terra | power×3 | нет |
| thaumcraft.FLUX | FOCUSFLUX | vitium | power×3 | нет |
| thaumcraft.BREAK | FOCUSBREAK | perditio | power×3+silk×4+(fortune==0?0:(fortune+1)×3) | нет |
| thaumcraft.RIFT | FOCUSRIFT | alienis | 3+duration/2+depth/4 | нет |
| thaumcraft.EXCHANGE | FOCUSEXCHANGE | permutatio | исходная precedence-ошибка, см. ниже | нет |
| thaumcraft.CURSE | FOCUSCURSE | mortuus | duration+power×3 | нет |
| thaumcraft.HEAL | FOCUSHEAL | victus | power×4 | нет |
| thaumcraft.SCATTER | FOCUSSCATTER | — | int(max(2,2×(forks−cone/45f))) | нет |
| thaumcraft.SPLITTARGET | FOCUSSPLIT | — | 4 | нет |
| thaumcraft.SPLITTRAJECTORY | FOCUSSPLIT | — | 5 | нет |

Деление целочисленное, кроме явно указанных float-формул. Cloud power multiplier
0.5f, SpellBat0.33f, Scatter `1f/(forks/2f)`, обе разновидности Split0.75f;
остальные1f. Для рабочего Touch/Fire нет уменьшающих множителей.

## Серверные требования, стоимость и исходные особенности

`TileFocalManipulator.startCraft` вызывает **knowsResearchStrict**, не обычный
isResearchKnown: bare keys требуют **завершённое исследование**; `KEY@N`
проверяет isResearchKnown соответствующей стадии. Это подтверждено байткодом
`ThaumcraftCapabilities.knowsResearchStrict` (`@` → isResearchKnown,
иначе → isResearchComplete). Поэтому caller `FocusCompiler.compile` должен
передавать `knowledge::isResearchCompleteStrict`. ROOT metadata тоже содержит
BASEAUROMANCY; порт пропускает его повторную проверку, поскольку TOUCH и FIRE
в обязательной единственной рабочей цепи каждый проверяют тот же gate.

Типы focus1/2/3 имеют capacity **15/25/50**. Для каждого key количество
повторений увеличивает multiplier `0.5f*(occurrence+1)`; complexity каждого
слагаемого отдельно приводится к int, затем суммы складываются. За каждый node
с ненулевым аспектом требуется один crystal этого аспекта, не complexity units.
ROOT не расходует crystal. Рабочая цепь требует aversio1 + ignis1.

- FIRE power1..5, default1; duration0..5, default0.
- complexity рабочей цепи: `2+duration+power*2` (4..17).
- vis изготовления: `complexity*10 + capacity/5`.
- серверный XP: `max(1, round(sqrt(complexity)))` уровней, не experience points.
- расход одного применения: `complexity/5f`.
- cooldown: `max(5, (complexity/5)*(complexity/4))`, обе дроби int.
- Референс FIRE color16734721; итоговый item color содержит непрозрачный alpha,
  как `java.awt.Color.getRGB`. Для нескольких исходных effects RGB усредняются;
  текущая единственная цепь содержит один effect.

Пример lesser Touch/Fire power1/duration0: complexity4/15,43vis изготовления,
два уровня XP, один aversio/ignis crystal,0.8vis применения и cooldown5ticks.
При power5/duration3 complexity15/15,153vis,4XP,3vis/cooldown9;
power5/duration5 имеет17complexity и не помещается в lesser focus.

Подтверждённые особенности оригинального релиза:

1. GUI `GuiFocalManipulator` ошибочно показывает XP через `min(1,round(sqrt))`,
   сервер реально использует **max**. Порт показывает/списывает серверную величину.
2. Сервер оригинала списывает XP перед проверкой наличия crystals и не проверяет
   capacity/shape так строго, как GUI. Порт проверяет весь запрос и резервирует
   ресурсы перед списанием; бесконечные/поддельные графы не воспроизводятся.
3. `FocusEffectExchange.getComplexity` действительно выполняет
   `(5+silk*4+fortune==0)?0:(fortune+1)*3`. Это **не** артефакт декомпиляции:
   официальный bytecode складывает5/silk/fortune перед `ifne`. При допустимых
   setting values первая ветка недостижима; default complexity3, silk не добавляет4.
   Справочная формула сохранена, сам Exchange runtime ещё не перенесён.
4. GUI name field ограничен50characters. Порт сохраняет эту длину и дополнительно
   убирает управляющие символы/format marker из серверного имени.

## Граф, package NBT и безопасные границы

`FocusGraph.Node` хранит неизменяемые копии original editor fields
`id/parent/children/x/y/key/setting.*`. `save()` также выдаёт рассчитанные
`target/trajectory/complexity` поля исходного FocusElementNode; они служат
описанием и **никогда не принимаются как серверная стоимость**.

`FocusCompiler` проверяет один ROOT/id0, согласованные parent/children,
уникальные ID/child references, достижимость, supply contracts, типы и допустимые
values settings, строгие research gates, runtime support и capacity. Обход
итеративный: attacker-controlled graph не вызывает рекурсивный stack overflow.
Рабочий runtime принимает ровно три последовательно связанные node. Вся
стоимость, crystals/color/settings и результат находятся в detached `FocusPlan`;
компилятор не меняет ItemStack, inventory, knowledge или world.

Явная современная адаптация: максимум32nodes/child references/depth, id0..31,
координаты−4096..4096, максимум16settings/key length32, node key length64.
Ограничения защищают сетевой/сохранённый граф; они не объявлены правилами BETA26.
Исходный сервер принимал непроверенные node data из клиентского packet.

`FocusPlan.packageNbt()` каждый раз создаёт новый tag с исходными полями
`index:int=0`, `power:float=1`, `complexity:int`, `nodes:List<Compound>`.
Node compounds имеют original `type` (MEDIUM/EFFECT), `key` и typed int
`setting.*`. UUID caster, dimension, execution index и temporary power не
переносятся из предмета в authoritative cast.

`FocusStacks.apply` создаёт копию stack и сохраняет unrelated NBT. Original
`package`, item `color`, `srt` и display name записываются в копию. Пересчёт
sorting cache при изменении — исправление stale cache исходного endCraft,
который удалял color, но не srt. `readPlan` повторно проверяет весь package,
item tier и settings; cached complexity/power/color/srt не определяют цену либо
damage. Wrong typed tags, unknown/malformed settings, nested PACKAGE/split
и extra nodes отвергаются. Отсутствующие настройки используют исходные
declared defaults. Неподдержанный late focus не превращается в базовый огненный.

Готовый focus можно передать другому игроку: исходный ItemCaster не проверяет
research при применении, только при изготовлении. `readPlan` поэтому компилирует
с predicate true; это не даёт без исследований изменить рецепт в manipulator.

## Проверки и API

`FocusGraphGameTests` содержит12 meaningful server checks: exactprices/package,
трёх tiers/cooldown, bare started-vs-completedresearch, giftedfocus/forged caches,
detachment/roundtrip, malformed cycles/extra graph,21reference/3runtime inventory,
invalid settings/supply, typed saved NBT/nesting/capacity, bounded parser,
single focus/unrelatedNBT/name, missing-settings defaults. Сам агент common
слоя не запускал Gradle/client: окончательный общий прогон фиксирует root-agent
в VALIDATION. Наличие тестового исходника ещё не означает успешный прогон.

Public API `thaumcraft.auromancy.focus`:

- `FocusCompiler.compile(graph,focus,knowsStrict)` → `Result.success()/plan()/error()`.
  Успех имеет nonnull plan/empty error; failure nullplan/stable error key.
- `FocusGraph.touchFire(power,duration)`, `nodes()`, `save()`, `read(tag)`;
  malformed bounded parser throws IllegalArgumentException.
- `FocusPlan.graph()/complexity()/maxComplexity()/craftVis()/xpLevels()/crystals()`;
  `castVis()/cooldownTicks()/color()/firePower()/fireDuration()/packageNbt()`.
- `FocusStacks.isFocus/maxComplexity/readPlan/apply`; `readPlan` → Optional,
  `apply` возвращает ItemStack copy. Невалидный/lower-tier apply бросает
  IllegalArgumentException.

Primary source classes: `api/casters/FocusNode`, `FocusMedium`, `FocusEffect`,
`FocusMod`, `FocusModSplit`, `FocusPackage`, `NodeSetting`, `FocusMediumRoot`;
`common/config/ConfigItems`, `common/tiles/crafting/FocusElementNode`,
`TileFocalManipulator`, `common/items/casters/ItemFocus`, все21 зарегистрированных
`foci` классов, `api/capabilities/ThaumcraftCapabilities`.
