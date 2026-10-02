# Таумометр и HUD: контракт TC6 BETA26

Аудит 2 октября 2026. Эталон — **Thaumcraft 1.12.2, 6.1.BETA26**, без TC4/TC5 и аддонов. Этот документ описывает оригинал, а не заявляет готовность интерфейса порта.

Восстановленный Java-код закреплён на commit `954022bb777b7546281fb36df8522f0ba6b43f81` TheDarkTower314. Ветви проверены `javap -c -p` в официальном [файле BETA26 №2629023](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2629023): локально `work/Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256 `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`. Изучены ItemThaumometer, HudHandler, RenderEventHandler, ScanningManager, AspectHelper, ScanGeneric, FXDispatcher, FXBlockRunes, PacketAuraToClient, ItemTCBase, а также исходные OBJ/MTL/PNG/sounds assets из этого JAR.

## Быстрый контракт

- Шкала ауры появляется при таумометре **в основной или второй руке**, без удержания ПКМ. Положение — слева сверху. Shift показывает точные vis/flux в небольшом тексте рядом со шкалой.
- ПКМ запускает сканирование немедленно: на клиенте 10 фиолетовых рун и `thaumcraft:scan`, на сервере вызов ScanningManager. Нет use-duration, зарядки, кольца прогресса или отдельного GUI-screen.
- `renderLast` показывает аспекты сущности над целью **без проверки, была ли она изучена**. `isThingStillScannable` ограничивает подсветку новых целей, а не раскрытие аспектов.
- Инвентарные аспекты также **не ограничены знаниями**: по умолчанию появляются с Shift в tooltip контейнерного экрана. Держать таумометр для этого не требуется. Нет замены неизвестного аспекта на `?` в этих HUD-путях.
- Ветка аспектов обычного блока через `blockTags` в BETA26 **неактивна**: список инициализируется, но ни один класс JAR его не заполняет. Возрождение этой ветви в порте — явная адаптация. Не выдавать её за доказанное правило оригинала «видны только уже изученные блоки».
- Видимая модель таумометра — оригинальный `scanner.obj`, включая две поверхности линзы. Отдельного ItemThaumometerRenderer или принудительного подъёма линзы к центру экрана в BETA26 нет.
- Аура нормируется на **525**, а не на текущий base. Vis/flux суммируются в одном вертикальном резервуаре; при сумме нормализованных значений >1 сжимаются вместе. Исследование FLUX не является разрешением увидеть flux.

## 1. Когда работает интерфейс

HudHandler.renderHuds вызывается из RenderTickEvent.END, если render-view entity — EntityPlayer. Aura HUD внутри renderHuds дополнительно требует `mc.inGameHasFocus && Minecraft.isGuiEnabled()`: штатно он исчезает в меню/при отключённом HUD.

Проверяются main hand, затем offhand. Для ItemThaumometer renderThaumometerHud вызывается один раз за кадр: `rT` предотвращает двойную шкалу при двух таумометрах. `start` сначала 0; уже отрисованный кастер в main hand добавляет 33, если `dialBottom=false`, sanity checker добавляет 75. Поэтому таумометр в offhand может получить вертикальный `shifty`; собственная шкала после отрисовки добавляет 80 для следующего устройства. `dialBottom` управляет dial кастера; код таумометра сам не переносит шкалу вниз.

Ни active hand, ни `isHandActive`, ни число тиков использования не входят в условия. ItemThaumometer не переопределяет getMaxItemUseDuration/getItemUseAction и не начинает длительное использование. Сам предмет stack size 1, rarity UNCOMMON.

Отдельная исходная особенность onUpdate: `held = isSelected || itemSlot == 0`. Серверный updateAura каждые 20 тиков и клиентская подсветка каждые 5 тиков используют именно этот флаг. Это также допускает необранный hotbar slot 0; способ присвоения itemSlot для offhand относится к внешнему vanilla lifecycle. В порте предпочтительно явно описать современную main/offhand привязку, а не переносить неоднозначное число слота как новое правило HUD.

## 2. Точная шкала ауры и UV

Текстура JAR: `assets/thaumcraft/textures/gui/hud.png`, **256×256 RGBA**. UtilsFX.drawTexturedQuad делит U/V на 256. Все размеры/позиции ниже — **масштабированные GUI-координаты**, не физические пиксели окна. Начальная трансляция `(2, shifty, 0)`, z=-90.

Определения:

```text
b = clamp(base / 525, 0, 1)
v = clamp(vis  / 525, 0, 1)
f = clamp(flux / 525, 0, 1)
if v + f > 1:
    m = 1 / (v + f)
    b *= m; v *= m; f *= m

yVis  = 10 + (1 - v) * 64
yFlux = 10 + (1 - f - v) * 64
yBase =  8 + (1 - b) * 64
count  = renderViewEntity.ticksExisted + partialTicks
count2 = renderViewEntity.ticksExisted / 3.0 + partialTicks
```

| Элемент | GUI-положение с учётом root | Размер GUI | Source UV в hud.png | Цвет / blend |
|---|---|---|---|---|
| Основная vis заливка, v>0 | x=7, y=shifty+yVis | 8 × 64v | (88,56), исходный прямоугольник 8×64 | (0.7,0.4,0.9,1), SRC_ALPHA/ONE_MINUS_SRC_ALPHA |
| Блик vis | x=7, y=shifty+yVis | 8 × 64v | (96,56+count%64), UV высота 64v | (1,1,1,0.5), SRC_ALPHA/ONE |
| Основная flux заливка, f>0 | x=7, y=shifty+yFlux | 8 × 64f | (88,56), исходный прямоугольник 8×64 | (0.25,0.1,0.3,1), обычный alpha |
| Блик flux | x=7, y=shifty+yFlux | 8 × 64f | (104,120-count2%64), UV высота 64f | (0.7,0.4,1,0.5), additive |
| Рамка, поверх заливок | x=3, y=shifty+1 | 16 × 80 | (72,48), 16×80 | белый, обычный alpha |
| Отметка base, после рамки | x=4, y=shifty+yBase | 14 × 5 | (117,61), 14×5 | белый, обычный alpha |

Обычные заливки используют scaleY=v/f на полном UV-прямоугольнике высотой 64: это **растягивание полного участка**, не обрезка UV сверху. Блики, напротив, меняют UV start и фактическую UV-высоту; они прокручиваются с противоположным направлением и разной скоростью. После additive возвращается стандартный alpha blend. Рамка и base marker рисуются последними.

Vis находится снизу, flux непосредственно над ним. 525 — константа визуальной шкалы; `base` только позиционирует отдельную отметку. Значения сначала ограничиваются по отдельности, затем общий overflow масштабирует **включая base**. Поэтому нельзя заменять код на `vis/base` или сделать две независимые полосы.

При Shift и соответствующем v/f>0:

- Vis: x=18, y=shifty+yVis; scale=.5; фактическое `currentAura.getVis()`, цвет **0xEEAAFF**.
- Flux: x=18, y=shifty+yFlux−4; scale=.5; фактическое `currentAura.getFlux()`, цвет **0xAA11BB**.

Формат `DecimalFormat("#######.#")`: до одного знака после десятичного разделителя, без обязательного trailing zero. Числа остаются **фактическими**, даже если шкала сжалась из-за overflow. Нулевые значения не подписываются, base числом не подписывается. Исследование FLUX, discovered aspects и режим creative не проверяются этим рендерером.

## 3. Серверное состояние ауры

ItemThaumometer.updateAura читает AuraChunk **чанка игрока**, x>>4,z>>4 в его dimension. Это не измерение чанка блока под прицелом. При существующем AuraChunk отправляется PacketAuraToClient с `base: short`, `vis: float`, `flux: float` — 10 байт payload. Position/dimension/sequence в исходном payload отсутствуют. Клиентский scheduled task заменяет HudHandler.currentAura на новый AuraChunk(null,base,vis,flux). Начальное значение — нули.

Отправка происходит каждые 20 тиков по исходному held-флагу, **не только после успешного скана**. Это пассивный индикатор; RPC на каждый render tick не нужен. Если AuraChunk отсутствует, метод не отправляет пакет и не очищает старое client state. Очистка при смене мира/отсутствии измерения в современном порте — допустимая документируемая адаптация состояния.

В updateAura есть отдельный trigger исследования FLUX: `(flux > vis || flux > base / 3) && !knowsResearch("FLUX")` запускает исследование и dark-purple actionbar `research.FLUX.warn`. Деление `base / 3` **целочисленное**, что подтверждено `idiv` в JAR. После этого пакет отправляется в любом случае. Нельзя использовать эту проверку как запрет на отображение flux до открытия исследования.

## 4. ПКМ, цель и результат исследования

ItemThaumometer.onItemRightClick:

1. На клиенте drawFX и звук `SoundsTC.scan` у игрока, category PLAYERS, volume .5, pitch 1.0.
2. На сервере doScan.
3. Возвращает SUCCESS с held stack. Нет charge timer, use animation, собственного cooldown или GUI-screen.

doScan и drawFX сначала ищут entity через `EntityUtils.getPointedEntity(world,player,1.0,9.0,0.0,true)`. Это включает non-collidable entities, например выпавшие предметы. Helper проверяет line of sight, увеличивает bounding box как минимум на .8, выбирает ближайшее пересечение; это отдельный поиск, не простой ванильный crosshair EntityHitResult.

Если entity найден, он имеет приоритет. Иначе вызывается inherited `rayTrace(world,player,true)` для блока, включая жидкости. Радиус **9 относится к entity lookup**; ItemThaumometer не задаёт радиус inherited block rayTrace в своём коде. При отсутствии цели scanTheThing получает null — среди зарегистрированных scan things есть и sky-путь.

ScanningManager перебирает IScanThing, проверяет checkThing и progressResearch соответствующего ключа, вызывает onSuccess только при успехе. Для ScanGeneric ключ entity `!`+entity name; для item `!`+registry name+metadata, если предмет не damageable. ScanGeneric исключает EntityItem из entity-тегов: его **сканирование** использует аспекты лежащего ItemStack. BlockPos преобразуется в item/getPickBlock; вода и лава получают bucket fallback.

Надписи actionbar оригинала имеют названия, которые легко неверно истолковать:

- `tc.knownobject`: **“You have learned something new.”**, зелёный italic — исследование продвинулось.
- `tc.unknownobject`: **“Nothing new can be learned from this.”**, фиолетовый italic — нового прогресса нет.

Это не названия двух HUD-состояний «аспекты уже известны / аспекты скрыты». Если scan thing возвращает пустой/null research key, он может подавить этот status text. Скан BlockPos дополнительно исследует содержимое доступного сверху item-handler inventory, максимум 100 непустых слотов; показ аспектов самого блока этим не включается.

## 5. Пассивная подсветка и эффект сканирования

Каждые **5 тиков клиента** при held-флаге ищется entity с `minrange=1, range=16, padding=5, nonCollide=true`. Если `isThingStillScannable` true, вызывается scanHighlight(entity). **thaumTarget присваивается найденной entity независимо от результата isThingStillScannable**. Затем выполняется block ray trace длиной 16 с жидкостями и случайной прибавкой `rand.nextInt(25)-rand.nextInt(25)` к каждому yaw/pitch; найденный ещё исследуемый блок тоже получает scanHighlight. Это окружающие голубые искры, а не статичный дополнительный reticle.

scanHighlight получает мировой AABB блока/сущности. `num=ceil(averageEdgeLength*2)`; на каждой из 6 граней создаёт num×2 искр. Цвета: red 16…32/255, green 132…165/255, blue 223…239/255; исходное движение нулевое, scale=.4+gaussian*.1. Аргумент random(10) в drawSimpleSparkle — **задержка 0…9 тиков**, не frame; последний аргумент 4 — **baseAge**, не render layer. Helper создаёт FXGeneric maxAge=16…19, выбирает base frame 320 с вероятностью .2, иначе 512, проигрывает loop из 16 кадров, задаёт layer=0, scale от s до 2s, slowdown=1, gravity=0. Alpha — случайный промежуточный массив с нулями на концах; есть небольшое random movement/wind. Точки случайны и ограничены размерами AABB. IsThingStillScannable проверяет **research key**, не наличие открытых отдельных аспектов.

drawFX при ПКМ создаёт **10 FXBlockRunes**:

- Entity: входная позиция `(entity.x−.5, entity.y+eyeHeight/2, entity.z−.5)`, длительность argument `(int)(height*15)`, gravity=.03.
- Block: `(block.x, block.y+.25, block.z)`, длительность argument 15, gravity=.03.
- В обоих случаях r и b `.3+rand*.7`, g=0. FXDispatcher.blockRunes дополнительно прибавляет .5 к каждой координате.

FXBlockRunes выбирает random horizontal quarter turn; maxAge=**3×duration argument**, поэтому стандартный block scan живёт 45 тиков. Rune frame из 224…239: реальный рендер берёт `u=(runeIndex%16)/64`, `v=6/64`, размер UV 1/64×1/64 в `textures/misc/particles.png`; world quad side `.3*particleScale`. Alpha растёт в первые maxAge/5, затем уменьшается, в draw делится на 2. Смещения ofx=random*.2, ofy=−.3+random*.6, плоскость на local z=−.51. Свет в старом vertex path передаётся парой (0,240).

В JAR нет класса **FXScan**. `PacketFXScanSource` существует, но ToolEvents использует его для enchantment **SOUNDING**, sneak-click и поиска руд. Он не вызывается ItemThaumometer и не должен становиться эффектом обычного сканирования по совпадению слова scan. Шейдеры рифта, research popups и эффекты Sounding не являются scanner reticle.

Assets эффекта: `textures/misc/particles.png`, `sounds/scan.ogg`; `sounds.json` событие `scan` содержит `{name:"thaumcraft:scan",stream:false}`, category master. Runtime вызов явно задаёт PLAYERS, volume=.5, pitch=1.

## 6. Аспекты сущностей и выпавших предметов: реальные ограничения

RenderEventHandler.renderLast при ненулевом thaumTarget вызывает **AspectHelper.getEntityAspects(thaumTarget)**. Если список не пуст, плавно увеличивает tagscale и рисует аспекты над entity. Здесь нет knowsResearch, discovered-aspect lookup или isThingStillScannable. Это подтверждено официальным bytecode offsets 233…390. AspectHelper.getEntityAspects тоже не проверяет PlayerKnowledge: берёт регистрацию EntityTags и NBT; у EntityPlayer дополнительно воспроизводимые аспекты из хеша имени.

Важное различие: renderLast **не** вызывает ScanGeneric/getItemFromParms для EntityItem. В штатной регистрации BETA26 нет EntityTag для обычной EntityItem, поэтому находящийся на земле ItemStack можно изучить, но его содержимые аспекты этим world-overlay путём автоматически не появляются. Не приписывать renderer отсутствующий item fallback. Если порт покажет их, это явное расширение полезности, а не доказанное совпадение BETA26.

Исходный renderLast не перепроверяет, что таумометр всё ещё в руке: thaumTarget обновляется ItemThaumometer.onUpdate, но после убирания предмета отдельной очистки не найдено. Это может оставить stale target. Надёжная очистка transient HUD state при смене руки/мира — адаптация lifecycle; сам stale state не стоит воспроизводить как намеренный режим прибора. У renderLast также нет такого же GUI focus/GUI-enabled guard, как у двухмерной шкалы ауры.

### Геометрия world aspect tags

- Якорь entity `(interpolatedX, interpolatedY+height, interpolatedZ)`; drawTagsOnContainer при dir=null вычитает .5 из x,z, затем прибавляет .5 обратно. Итоговый центр первой строки расположен **на .5 выше верхней точки entity**.
- Порядок `tags.getAspects()`, **не сортировка по числу**; максимум **5 на строку**, следующая строка выше на `tagscale*1.05`.
- Горизонтальный сдвиг `(column-rowCount/2+.5)*4*tagscale²`; при s=.25 шаг=.25 блока.
- Quad шириной s блока; yaw billboard вокруг вертикальной оси к игроку, без camera pitch billboard. Старые 90° Z-повороты компенсируют UV-orientation renderQuadCentered; современный рисунок должен быть вертикальным, а не лежать боком.
- Используются aspect.getImage(), aspect.getColor(), opacity **.75**, brightness argument **220**; depth test отключён на tags, затем включён. Не путать 220 с обычным packed full-bright integer.
- `tagscale` каждый world render frame уменьшается на .005, при цели ниже .5 увеличивается на `.031−s/10`. Это frame-based приближение и не таймер сканирования; с одним entity-проходом устойчивое значение около .26, порог .5 не означает постоянный размер .5.
- Если количество >=0, рисуется его целое число. После дополнительных transform scale=.04 и translate `(0,6,−.1)`: shadow draw `(14−textWidth,1)` цвет **0x111111**, foreground `(13−textWidth,0)` белый, между ними ещё z−.1. Это маленькое число у края символа, не отдельная текстовая панель названий.

## 7. Обычные блоки, containers и dormant blockTags

DrawBlockHighlightEvent сначала читает public list `blockTags`, если size>0. Ожидаемый layout `[x,y,z,AspectList,EnumFacing.ordinal]`; если координаты совпали с block target, теги рисуются с яркостью 220 у стороны блока, используя тот же world renderer.

**Проверка полноты:** сканированы constant pools всех `.class` официального JAR на строку `blockTags`. Единственный класс с ссылкой — RenderEventHandler. Его `javap` показывает единственную `putstatic blockTags`, в static initializer: `new ArrayList()`. Другие обращения — чтение size/get; нет add/set/addAll и нет сетевого packet handler, наполняющего список. В pinned source rg на всё дерево даёт то же. Значит эта ветвь в штатном BETA26 не получает object aspects обычных блоков. Нельзя вывести из неё ни «known-only», ни полную поддержку generic block aspect HUD.

Другая **активная** ветвь DrawBlockHighlightEvent показывает содержимое IAspectContainer при `EntityUtils.hasGoggles(player)` и special goggles displays. Это интерфейс очков, не функция держания таумометра. Если над контейнером воздух, якорь выше и dir=UP; иначе используется clicked face; tagscale threshold=.3. Он показывает **эссенцию контейнера**, а не аспекты самого jar-item как ингредиента. Перенос scanner HUD не должен случайно требовать goggles для entity tags или смешивать эти два источника данных.

## 8. Tooltip аспектов в инвентаре

События ItemTooltipEvent и RenderTooltipEvent.PostBackground требуют:

```text
currentScreen instanceof GuiContainer
GuiScreen.isShiftKeyDown() != CONFIG_GRAPHICS.showTags
!Mouse.isGrabbed()
```

showTags по умолчанию **false**, значит Shift раскрывает; true меняет поведение на «показывать всегда, Shift скрывает». Нет условия держания таумометра, known object, знание каждого аспекта, research status, creative либо completed scan. Берутся ThaumcraftCraftingManager.getObjectTags(ItemStack).

HudHandler.renderAspectsInGui сортирует `getAspectsSortedByAmount()`. Один горизонтальный ряд, quad **16×16**, шаг **18**; x=`tooltipX+index*18`, y=`tooltipY+sd−16`. Tooltip handler резервирует дополнительную высоту (ceil(18/fontHeight)) и ширину под ряд. Это часть tooltip, не отдельная карточка в центре экрана.

UtilsFX.drawTag использует обычный цвет aspect и image. Положительное количество форматируется `DecimalFormat("#######.##")` и рисуется внутри нижнего правого угла; по умолчанию scale text=.5 (`largeTagText=false`), четыре чёрных пиксельных offset дают outline, поверх белое число. При `largeTagText=true` текст вдвое крупнее. Нулевые amounts здесь не печатаются. World-tag числа из предыдущего раздела — иной renderer и иной layout.

`textures/aspects/_unknown.png` присутствует в JAR, но его буквальная ссылка найдена лишь в **GuiResearchPage и GuiResearchTable**. Scanner Item/HudHandler/RenderEventHandler её не используют, и ветки “?” на основе неизвестного аспекта отсутствуют. Доступность этих PNG сама по себе не доказывает необходимость unknown-режима таумометра TC6.

## 9. Модель в руках и линза

Официальный JAR содержит `blockstates/thaumometer.json` (Forge marker 1) со defaults:

```text
model: thaumcraft:scanner.obj
textures #body: thaumcraft:items/scanner
textures #pane: thaumcraft:items/scanscreen
custom flip-v: true
```

Original assets: `models/item/scanner.obj`, `scanner.mtl`, `textures/items/scanner.png` **256×256**, `textures/items/scanscreen.png` **64×64**. OBJ: 36 vertices, 70 vt, 10 normals, 56 faces; groups **scanner/body**, **screen/pane**, **screen_back/pane**. Bounds x=−1.4…1.4, y=−.1….1, z=−1.2124…1.2124. Две pane-поверхности принадлежат самой модели; отдельный lens world renderer не нужен.

JAR scanscreen имеет alpha 0…192, поэтому линза полупрозрачная. **scanscreen.png.mcmeta содержит только `texture.blur=true`, без animation**. Следовательно, анимированная современная texture с иным именем не доказана этим BETA26 asset. Не заменять полупрозрачную синюю линзу непрозрачной заливкой или придумать live camera framebuffer внутри модели.

Source transform blockstate, в исходных Forge-единицах (translation ещё не JSON model pixels):

| View | Translation | Scale | Rotation sequence |
|---|---|---|---|
| firstperson | (−.06,.006,−.4) | не задан, default | X +90 |
| firstperson_lefthand | (−1.06,.006,−.4) | не задан, default | X +90 |
| thirdperson | (0,0,−.1) | .2 | Y +90, затем X +90 |
| thirdperson_lefthand | (0,−.1,0) | .2 | Y +90, затем X +90 |
| gui | (.175,−.175,0) | .35 | X +90 |
| ground | (.1,.1,.1) | .2 | X +90 |
| fixed | (.2,.2,−.17) | .4 | X −90 |

В JAR нет ItemThaumometerRenderer; class references ItemThaumometer найдены только в ConfigItems, самом ItemThaumometer и HudHandler. ItemTCBase.getCustomModelResourceLocation выбирает model resource location, не hand event renderer. Проверка ItemThaumometer не обнаружила setActiveHand, use-duration override, ItemPropertyGetter, zoom, изменение FOV или hand-use animation. Современные Forge transform semantics могут потребовать явной адаптации; исходные left/right translation нельзя слепо умножить на 16 и одновременно зеркалить повторно без проверки в обоих руках.

Наблюдение текущего порта на момент аудита: model `models/item/thaumometer.json` с `thaumometer.obj` и `thaumometer_screen.png.mcmeta` использует переименованный/иной набор ассетов; оригинальные `scanner.obj`/`scanscreen.png` из этого JAR должны оставаться эталоном сверки. Этот аудит не меняет текущие assets и не утверждает, что нынешняя first-person поза уже проверена по BETA26.

## 10. Подтверждение JAR и пределы аудита

| Проверка | Класс / метод / bytecode evidence |
|---|---|
| Мгновенный ПКМ, client FX/server scan | ItemThaumometer.func_77659_a offsets 0…66 |
| held=isSelected или slot0, 20/5 тиков | ItemThaumometer.func_77663_a 0…179 |
| entity target присваивается вне scannable condition | тот же метод 98…125 |
| entity scan range9, inherited block rayTrace | ItemThaumometer.doScan 0…70 |
| Flux trigger не gate, base/3 integer | ItemThaumometer.updateAura 35…144, `idiv` offset60 |
| Обе руки, один HUD | HudHandler.renderHuds 156…304 |
| 525, clamp, сумма и base marker | HudHandler.renderThaumometerHud 7…128, marker 658…693 |
| Vis/flux UV и Shift text | тот же метод 170…625; frame 625…655 |
| Entity tags ungated | RenderEventHandler.renderLast 233…390; нет knowsResearch |
| Rowsize5, .75 alpha, numeric amount | RenderEventHandler.drawTagsOnContainer 157…672 |
| Tooltip sorted18-pixel row, ungated | HudHandler.renderAspectsInGui 0…124; event handlers 0…203/196 |
| blockTags никогда не заполняется | Единственный class reference RenderEventHandler; единственная putstatic в static init |
| Highlight research gate | ScanningManager.isThingStillScannable 0…70 |
| Cyan highlight dispatch | FXDispatcher.scanHighlight(AABB) 0…435 |
| Рунные частицы и fade | FXBlockRunes.func_180434_a 0…507, func_189213_a 0…156 |
| Неизвестные аспекты не masked в HUD | Отсутствие knowledge/mask branches в audited methods; _unknown references только research GUI classes |
| Нет отдельного item renderer | Полная инвентаризация JAR class names/references и item methods |

Повторяемая команда: `javap -classpath work/Thaumcraft-1.12.2-6.1.BETA26.jar -c -p <fully.qualified.Class>`. Документ сверяет исходные ветви и assets; оригинальный клиент 1.12.2 для нового gameplay-сравнения не запускался. Dormant blockTags, EntityItem overlay gap, slot0 и stale target описаны как реальные quirks, а не намеренные новые ограничения дизайна. Правки lifecycle, дополнительные useful item/block overlay и новые network protections в порте следует помечать как адаптацию, сохраняя визуальный язык TC6.

## 11. Приёмка современного HUD

1. Main/offhand дают одну ауру без ПКМ; смена руки/предмета/мира очищает transient state; GUI scale не меняет пропорций оригинального 16×80 dial.
2. Шкала 525, общий vis+flux overflow и base marker воспроизводятся при обычных и превышающих 525 значениях; Shift показывает реальные числа, не проценты.
3. Нулевой flux скрывает только flux segment; исследование FLUX не блокирует видимость положительного flux. Измеряется чанк игрока.
4. ПКМ выполняет одну server-owned попытку скана, client эффекты не означают успешного исследования; нет новой progress bar или обязательного hold-use.
5. Мобы с аспектами до и после скана показывают одинаковые цветные world tags; scanHighlight исчезает после изучения research key. Unknown-aspect '?' не появляется без явно выбранной адаптации.
6. Tooltip в контейнерном screen показывает исходные 16px иконки/числа при Shift и не требует таумометра/изучения предмета; world labels не превращены в неподвижную текстовую карточку.
7. Решения по generic block и ItemEntity world overlay явно отмечены как восстановление dormant ветви/расширение, а не скрытая подмена контракта BETA26.
8. Полупрозрачная линза, обе pane стороны, OBJ pose в правой и левой руке и фактические body textures проверяются клиентскими кадрами; отсутствие отдельного renderer не мешает сохранить оригинальную форму.

## Источники pinned Java

- [ItemThaumometer](https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/items/tools/ItemThaumometer.java)
- [HudHandler](https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/client/lib/events/HudHandler.java)
- [RenderEventHandler](https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/client/lib/events/RenderEventHandler.java)
- [ScanningManager](https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/research/ScanningManager.java)
- [AspectHelper](https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/aspects/AspectHelper.java)
- [ScanGeneric](https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/research/ScanGeneric.java)
- [FXDispatcher](https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/client/fx/FXDispatcher.java)
- [FXBlockRunes](https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/client/fx/particles/FXBlockRunes.java)
- [UtilsFX](https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/client/lib/UtilsFX.java)
- [PacketAuraToClient](https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/network/misc/PacketAuraToClient.java)
