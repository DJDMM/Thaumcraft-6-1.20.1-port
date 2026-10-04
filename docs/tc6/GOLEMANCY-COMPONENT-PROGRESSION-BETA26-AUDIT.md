# Вход в големоведение и clockwork mind — BETA26

Эталон: официальный `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
JSON сверены непосредственно с архивом; зеркало исходников закреплено на
`954022bb777b7546281fb36df8522f0ba6b43f81`.

## Исходные стадии и платежи

| Запись | Завершённые родители | Переход |
|---|---|---|
| UNLOCKGOLEMANCY | UNLOCKARTIFICE, UNLOCKAUROMANCY, UNLOCKINFUSION | Начало →1; затем факт f_golem, 16 raw Observation Golemancy и16 Basics →3, с пропуском пустого заключения |
| BASEGOLEMANCY | UNLOCKGOLEMANCY | Одна пустая стадия →2; автоматически как sibling открытой категории |
| MINDCLOCKWORK | BASEGOLEMANCY, ESSENTIASMELTER, HEDGEALCHEMY | Начало →1; Mind/Life scan facts и16 raw Observation Golemancy →2 |
| MATSTUDWOOD | Нет | Одна пустая стадия →2; исходный sibling при начале MINDCLOCKWORK |

В оригинале следующий переход MINDCLOCKWORK требует 32 raw Theory Artifice
и32 Theory Golemancy, завершает запись на4 и открывает пресс/управление.
В срезе0.21 **поддержаны только начало и переход к2**. Физический рецепт
`MINDCLOCKWORK@2` уже доступен; сервер возвращает UNSUPPORTED на попытку
платежа следующей стадии, не списывает теории и не открывает CONTROLSEALS,
SEALCOLLECT или SEALSTORE. Книга позволяет открыть и пройти текущую стадию,
а после2 отключает продолжение с отдельной подсказкой. Справочный архив
сохраняет неизменённое содержание исходных страниц.

Полный набор прогрессии содержит **50 ключей**: 46 из0.20 плюс HEDGEALCHEMY,
UNLOCKGOLEMANCY, BASEGOLEMANCY, MATSTUDWOOD. Частичный MINDCLOCKWORK указан
отдельно и не включён в50. MATSTUDWOOD — открытая исходная запись знания
материала, а не утверждение о работающем изготовлении деревянного голема.

## Точный скан f_golem

`ConfigResearch.java:172–173`; байткод pinned ConfigResearch, offsets377–408,
регистрирует `ScanEntity("f_golem", EntityGolem.class, true)` и такой же скан
для `EntityOwnedConstruct.class`. `ScanEntity.checkThing` вызывает
`Class.isInstance` (offset34); дополнительных gates по стадии, владельцу
или NBT нет. В vanilla1.12.2 Iron Golem, Snow Golem и **Shulker** наследуют
EntityGolem: `javap` классов aak/aai/adi показывает общий superclass zz,
имена подтверждены `joined.srg` в локальном аудите vanilla.

В1.20.1 это `AbstractGolem`; для пока плоского каталога TC явно перечислены
четыре исходных наследника OwnedConstruct: `golem`, `turret_basic`,
`turret_advanced`, `arcane_bore`. Это адаптация иерархии, не перенос AI.
Предметы/яйца спавна и другие мобы не заменяют настоящий скан сущности.
Серверный Thaumometer использует уже проверяемые ray/range/hand/world
условия. HUD/hover только читает цель; commit записывает факт независимо
от открытия големоведения. Повторный факт не оплачивает наблюдения повторно.

`!cognitio` и `!victus` уже следуют из обнаруженных аспектов. Новые
синтетические маркеры не добавлены: в проверках сканируются настоящие
бумага и яблоко. Категория наблюдений начисляется существующим scanner
всем исходным категориям, поэтому её можно накопить перед разблокировкой.

## Соседние записи и совместимость

Pinned `ResearchManager.progressResearch` offsets905–964 обрабатывает
siblings после каждого перехода. Существующий механизм прогрессии теперь
открывает пустой MATSTUDWOOD при начале MINDCLOCKWORK и начисляет штатные5XP
за каждый новый sibling. BASEGOLEMANCY открывается после завершения
UNLOCKGOLEMANCY. Платежи проверяются атомарно на сервере с expectedStage;
старый повтор запроса возвращает STALE. Сохранение сохраняет частичную2,
не делает её завершённой и не открывает следующую ветвь.

HEDGEALCHEMY переносится отдельно:
[преобразования и свечи](HEDGE-ALCHEMY-BETA26-AUDIT.md).
Его несуществующий `thaumcraft:leather` required_craft отбрасывается как в
BETA26. Vanilla `minecraft:dye` metadata0 отображается в `ink_sac`,
`minecraft:web` — в `cobweb`, одинаково для craft proofs и книги.
Новые профили больше не получают временный PORT_TALLOW. Уже сохранённый
alias сохраняет первый рецепт, не удовлетворяя завершённого родителя
HEDGEALCHEMY и не открывая поздние стадии.

Физическая цепочка simple mechanism → clockwork mind → Brain Box стоит
10+25+50vis и свои кристаллы: [контракт рецепта](CLOCKWORK-COMPONENTS-BETA26-AUDIT.md).
Основной Тауматорий работает без улучшения. Получение компонентов теперь
имеет исходный путь, но полный пресс, изготовление/AI големов, печати,
независимый multiplayer и полное ручное survival-прохождение остаются
отдельной работой. Фактические результаты проверок публикуются в
[VALIDATION.md](../../VALIDATION.md) после выполнения.
