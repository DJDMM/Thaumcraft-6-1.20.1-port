# Hungry Chest, Levitator, Jar Brain и Flyer — исходная прогрессия BETA26

Эталон: закреплённый `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Исходные JSON берутся из JAR; зеркало исходников —
`954022bb777b7546281fb36df8522f0ba6b43f81`. ConfigResearch проверен через
`javap -p -c`; сырые данные остаются вне распространяемого проекта.

Срез 0.24 добавляет четыре канонических ключа к прежним 74. Таблица
описывает серверные требования; успешные прогоны фиксируются отдельно
в [VALIDATION.md](../../VALIDATION.md).

| Исследование | Родители | Физический платёж и факты на стадии 1 |
| --- | --- | --- |
| HUNGRYCHEST | Завершённый BASEARTIFICE | 16 raw Observation Golemancy, один `oredict:chest`, один Hopper |
| LEVITATOR | Завершённый HUNGRYCHEST, entered METALLURGY@3 | 16 raw Observation Artifice, реальный факт аспекта `!volatus` |
| JARBRAIN | Завершённые BASEGOLEMANCY, WARDEDJARS, INFUSION | 16 raw Observation Golemancy, 32 raw Theory Golemancy, `f_BRAIN`, один Zombie Brain |
| GOLEMFLYER | Завершённые GOLEMCLIMBER и LEVITATOR | 32 raw Theory Golemancy, `f_FLY` |

У всех четырёх две исходные стадии. Начало записывает 1; оплата первой
стадии пропускает пустое заключение и записывает завершение 3, даёт обычные
5 XP. У JARBRAIN заключение имеет warp 3: исходная формула ResearchManager
даёт permanent +2 и normal +1 при завершении. Ни один переход не раскрывает
MATSTUDVOID или BASEELDRITCH.

Проверки требований и main-inventory reservations предшествуют списанию.
Недостающий факт, предмет или один raw knowledge не может частично оплатить
другой ресурс. Offhand и надетая броня не используются для required_item.
Обычный bare рецепт сохраняет исходное started gate; пресс требует строго
завершённого ключа **каждой** выбранной детали. Начатый Flyer не позволяет
изготавливать летающие ноги.

## Исходная группа chest

Forge 1.12 регистрирует в `chest` обычный, trapped и Ender chest. Поэтому
Ender chest является допустимой исходной оплатой HUNGRYCHEST.
[Официальный OreDictionary 1.12](https://github.com/MinecraftForge/MinecraftForge/blob/1.12.x/src/main/java/net/minecraftforge/oredict/OreDictionary.java)
показывает эти три регистрации в `initVanillaEntries`; `chestWood`,
`chestEnder`, `chestTrapped` — отдельные группы.

Порт адаптирует общую группу к `forge:chests` с явными тремя vanilla
вариантами. Barrel не считается chest только из-за наличия инвентаря.
Серверный Obtain и строки книги используют один физический предикат,
чтобы допустимый предмет не отображался как неоплаченный. Это замена
удалённого OreDictionary тегами, а не требование только деревянного сундука.

## Сканы полёта

ConfigResearch регистрирует ровно семь `ScanEntity("f_FLY", Class, true)`:
Bat, Parrot, FireBat, TaintSwarm, Wisp, Ghast и Blaze. Современные Phantom,
Allay и spawn eggs не добавлены к этому списку. Реальный Bat также даёт
прежний `f_BAT`; FireBat сохраняет `!Firebat` и `f_BAT`. Факты объединяются,
а не подменяют старые фокусные сканы. Просмотр HUD остаётся read-only;
запись выполняется существующим серверным commit таумометра. Для LEVITATOR
нужен факт аспекта Flight, а для GOLEMFLYER — факт подходящей сущности;
это разные исходные требования.

## Рецепты и границы

Исходные арканные рецепты Hungry Chest и Levitator расходуют соответственно
15 vis + Terra/Aqua по одному и 35 vis + Aer один. Jar Brain использует
уже зарегистрированное исходное наполнение: normal jar, Zombie Brain,
два Spider Eye, Water Bucket, instability 4, Cognitio/Sensus/Exanimis по 25.
С поддержанным JARBRAIN доступны 16 из 56 путей наполнения; поздние 40
сохраняют gates. Обычные источники установлены для 14; Advanced/Greater
по-прежнему требуют естественных Firebat/Pech/Primordial Pearl источников.

В состав Flyer входят физический Levitator, четыре латунных пластины,
Slime Ball и механизм вместе с компонентами остальных выбранных частей.
Цена компонентов и Machina вычисляется существующим GolemDesign.
Void остаётся за исходной Eldritch-цепью; наличие его текстуры или
сохранённой подставленной стадии не открывает изготовление.
