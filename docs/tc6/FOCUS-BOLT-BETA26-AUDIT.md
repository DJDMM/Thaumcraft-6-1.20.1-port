# Луч фокуса — официальный TC6 6.1.BETA26, этап 0.18

Основа — закреплённый `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Проверены `javap -c -p` FocusMediumBolt/FocusMediumTouch и читаемый
EntityUtils.getPointedEntityRay/PacketFXZap/FXBolt. Это TC6 BETA26, без TC4/5.

## Доставка и стоимость

BOLT наследует Touch, но range во всех supply/execute равен **16**, независимо
от player reach. Стоимость 5, аспект Potentia, supply TARGET+TRAJECTORY,
gate — строго завершённое FOCUSBOLT. Само исследование использует исходный
parent FOCUSPROJECTILE@2; полный Projectile для него не нужен.

Entity-first trace сохраняет: near exclusion .25, поиск от bounding box
заклинателя с расширением по direction*range и padding .25, collision border
минимум .8, eye LOS, ближайшее пересечение. Если видимого существа нет,
используется block ray; fluid NONE. Target существа использует точку
пересечения, следующая траектория/дуга заканчивается на расстоянии до
entity.position, а не до intersection. Miss передвигает trajectory ровно16
и не создаёт эффект на несуществующей цели. Продолжение после BOLT исполняет
уже оплаченный остаток; повторного vis/cooldown/sound нет. Та же последовательность
может находиться после PROJECTILE, чей оплаченный suffix возобновляется при hit.

Нормализация направления, finite validation и отказ читать незагруженные
чанки — явное усиление границ современного серверного исполнения. Ни один
визуальный клиентский пакет не является запросом кастинга или выбором цели.

## Дуга FXBolt

Оригинальный PacketFXZap достигает игроков в64 блоках от source. Цвет —
среднее цветов всех effect; пока граф линейный с единственным terminal effect,
это именно его исходный RGB. Width — package power*.66, в текущих линейных
заклинаниях power1. Сервер отправляет отдельный bounded S2C пакет, привязанный
к dimension; render state очищается при смене world.

Сохранены исходная `textures/misc/essentia.png`, lifetime3, seeded jitter,
length=distance*PI, integer steps, sinusoidal phase и age-dependent amplitude,
random width contraction, два пятигранных слоя radii width/10 и ещё /3,
additive SRC_ALPHA/ONE, depth test, no depth write, alpha clamp(.1..1).
Legacy CoreGLE заменён современными textured vertex quads. Join geometry
адаптирована к отдельным пятигранным кольцам сегментов; это явно не старый
OpenGL/GLE API. Paths с двумя точками не рисуются, как в исходном FXBolt.
Количество активных дуг ограничено512; world switch удаляет старые дуги.

## Проверки

FocusBoltGameTests проверяет дальность сверх Touch,16-block miss,
occlusion/endpoint, настоящий Bolt→Flux cast с однократным debit/cooldown,
оплаченный Bolt→Heal continuation без новой платы и malformed visual data.
FourFocusGraphGameTests проверяет server-derived costs/crystals/NBT/gates.
Клиентский сценарий создаёт Bolt→Heal через real menu/C2S за93vis/3XP,
физически устанавливает его и применяет к существу в12 блоках; отдельно
проверяет получение пакета и фактический render callback.
Только выполненные результаты публикуются в VALIDATION.md.
