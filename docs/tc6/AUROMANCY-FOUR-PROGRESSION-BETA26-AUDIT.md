# Четыре канонических исследования фокусов: BETA26 → 0.18

Эта часть реализует только `FOCUSBOLT`, `FOCUSFLUX`, `FOCUSHEAL` и
`FOCUSBREAK`. После интеграции четырёх runtime узлов число реализованных
канонических записей — **29 из 148**, работающих определений узлов —
**11 из 21**. Остальные десять типов не считаются завершёнными.

## Закреплённый эталон

Оригинальный `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
JSON ниже прочитан непосредственно из
`assets/thaumcraft/research/auromancy.json` внутри этого JAR; в проекте его
неизменённая копия — `data/thaumcraft/legacy_research/auromancy.json`.

Дополнительно проверен `javap -c -p` официальных классов
`thaumcraft.common.lib.utils.InventoryUtils` и
`thaumcraft.common.lib.network.playerdata.PacketSyncProgressToServer`.
Читаемый исходник используется для навигации по `ConfigResearch`,
`ScanGeneric`, `ResearchManager` и четырём focus node definitions;
при расхождении поведение задаёт релизный binary.

## Точные родители, стадии и расходы

| Запись | Строгий родитель | Требования первой стадии | Итоговая сохранённая стадия |
| --- | --- | --- | --- |
| FOCUSBOLT | FOCUSPROJECTILE@2 | `!potentia`, 1 Observation Auromancy + 1 Theory Auromancy | 3; вторая стадия — пустое заключение |
| FOCUSFLUX | завершённое FOCUSELEMENTAL | `!vitium`, 1 Theory Auromancy | 2; единственная стадия |
| FOCUSHEAL | завершённое FOCUSFLUX | `!victus`, 1 Theory Auromancy | 2; единственная стадия |
| FOCUSBREAK | завершённое FOCUSFLUX | `!perditio`, 1 Theory Auromancy, предмет Silk Touch I и предмет Fortune I | 3; вторая стадия — пустое заключение |

Исходная единица Observation равна **16 raw**, Theory — **32 raw**.
`FOCUSBOLT` доступен после входа в stage 2 Projectile: полное завершение
Projectile и все три native projectile proofs не являются его родителем.
В остальных строках bare canonical parent требует завершения, а не
просто открытого исследования. Начало записи не снимает её stage payment.

В четырёх JSON записях нет новых `f_*` фактов, требований получить удар,
сканировать моба, набрать vanilla statistics или изготовить предмет.
Эти условия не добавлены. Старый stage-2 direct-source hook
`FOCUSPROJECTILE` и исходные семейства стрел/fireballs/llama spit сохранены
без изменений. Advanced/Greater focus research не нужен этим четырём
родителям и остаётся закрытым.

## Настоящее исследование аспектов

Сканирование используют уже работающие `ThaumometerItem` и серверный
`KnowledgeStore.recordScan`; отдельный client packet или новое событие
для этих четырёх записей не требуется. Исходные аспектные составы дают
достижимые примеры:

- redstone — Potentia;
- nether wart — Vitium;
- wheat seeds — Victus;
- cobblestone — Perditio.

Это пример источников аспектов, а не новый закрытый whitelist предметов.
Любой настоящий успешный scan с тем же аспектом подходит, как и раньше.
`ScanningNetwork.capture` и hover только показывают состояние; они не
выдают proof, знания или стадии. В новых интеграционных тестах эти четыре
аспекта получены реальным held-item использованием таумометра, без
прямого `discoverAspect` или вручную заданного `!aspect`.

## Enchanted placeholder — расход двух физических предметов

Исходный FOCUSBREAK содержит:

```text
thaumcraft:enchanted_placeholder;1;0;{ench:[{id:33s,lvl:1s}]}
thaumcraft:enchanted_placeholder;1;0;{ench:[{id:35s,lvl:1s}]}
```

Это **Silk Touch I** и **Fortune I**. `InventoryUtils.checkEnchantedPlaceholder`
проверяет зачарования найденного предмета, а не его базовый Item ID или
износ. Уровень не ниже требуемого подходит; имя, повреждение и другие
зачарования не препятствуют совпадению. Как в исходном EnchantmentHelper,
подходит и stored enchantment у enchanted book. Ограничения «только
кирка», «только свежий инструмент» и «ровно Fortune I» не добавлены.

Официальный `PacketSyncProgressToServer.checkRequisites` вызывает
`consumePlayerItem`, а тот уменьшает найденный stack. Поэтому два предмета
**расходуются**, а не просто предъявляются как скан или наличие в руке.
Поиск идёт по `mainInventory`; offhand/armor не являются платёжными слотами.

В порт добавлен этот предикат в authoritative `ResearchProgression.Obtain`.
Имеющаяся в книге проверка Enchanted Placeholder показывает те же пороги.
Все costs продолжают использовать общий detached reservation plan:
отсутствие аспекта, знаний или второго предмета не списывает первый
предмет/знания частично. Один stack с одновременно Silk/Fortune не может
оплатить две единицы предметов. Два отдельных таких предмета подходят.
Это сохранение уже принятой port adaptation — shared reservations и
atomic payment — вместо исходного unchecked sequential consume loophole.
Неправильный `minecraft:thaumcraft:enchanted_placeholder` в других старых
записях по-прежнему отбрасывается; он не превращён в новый расход.

## Границы

Ровно четыре записи добавлены в `ResearchProgression.isImplemented`.
Compiler и manufacture используют прежний `knowsResearchStrict`:
лишь открытая запись не разрешает её runtime node. Все четыре default
узла помещаются в базовый focus при соответствующей комбинации, поэтому
закрытые Advanced/Greater не открываются как обходной путь.

Это изменение не открывает `FOCUSCLOUD`, `FOCUSMINE`, `FOCUSPLAN`,
`FOCUSSPELLBAT`, `FOCUSSCATTER`, `FOCUSSPLIT`, `FOCUSCURSE`,
`FOCUSEXCHANGE`, `FOCUSRIFT`, FORTRESSMASK или другие поздние исследования.
Новые infusion recipes не зарегистрированы: достижимость остаётся
**9 из 56**, а **47** исходных путей сохраняют поздние gates.

## Добавленные проверки

`FourFocusProgressionGameTests` содержит **7** server GameTests:

1. Точные четыре родителя, stage-2 Bolt gate, bare completion для остальных,
   29 реализованных записей, no-book отказ и закрытые поздние descendants.
2. Четыре реальных vanilla aspect scans, read-only hover, отсутствие
   автоматической выдачи research stages и сохранение Vitium.
3. Полная цепочка четырёх исследований с настоящими scans, точными
   платежами 16/32 raw, расходом обоих enchantment предметов, пропуском
   пустых заключений, reload и stale-request отказом.
4. Main inventory против offhand, настоящий stored enchanted book,
   именованный повреждённый Fortune III tool, совпадение book preview
   с сервером и физическое потребление.
5. Общие reservations: один double-enchanted stack не оплачивает два
   предмета; отсутствующий второй payment не списывает первый.
6. Отсутствующий аспект и обычные/нужное зачарование не имеющие
   инструменты не списывают знания, XP или inventory частично.
7. Все четыре manufacturing gates требуют завершения собственной
   записи; начатая запись недостаточна, Advanced/Greater не выдаются.

Upstream parent stages и кошельки знаний — явно подготовленные fixtures;
enchanted предметы в тестах также fixtures. Новые четыре стадии и
aspect proofs в тесте 3 проходят настоящий scanner/прогрессию, но это
не является доказательством полного ручного survival прохождения.
Агент этой части не запускал Gradle или клиент; общие результаты server,
изолированного client QA и сборки фиксируются после интеграции в
`VALIDATION.md`.
