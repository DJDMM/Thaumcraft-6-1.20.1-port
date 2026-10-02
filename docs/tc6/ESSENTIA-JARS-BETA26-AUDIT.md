# Банки, фиалы и метки: контракт TC6 BETA26

Аудит для этапа 0.9, 1 октября 2026. Эталон — **Thaumcraft 1.12.2, 6.1.BETA26**; TC4/TC5 и аддоны не использованы. Прочитан [общий конспект алхимии/инфузии/Artifice](ALCHEMY-INFUSION-ARTIFICE.md). Этот документ описывает оригинал, а не заявляет готовность механик порта.

Исходники закреплены на commit `954022bb777b7546281fb36df8522f0ba6b43f81` TheDarkTower314. Важные ветви сверены `javap -c -p` с официальным [файлом BETA26 №2629023](https://www.curseforge.com/minecraft/mc-mods/thaumcraft/files/2629023), локально `work/Thaumcraft-1.12.2-6.1.BETA26.jar`. Повторно проверенный SHA-256: `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.

## Краткий контракт реализации

- Normal и Void Jar хранят **250 одного аспекта**. Обычная банка возвращает непринятый остаток; void принимает совместимый избыток, оставляет 250 и уничтожает остальное.
- Suction normal: **32**, с меткой **64**, полная **0**. Void: **32**, с меткой и количеством <250 **48**, полная снова **32**. Minimum suction normal 32/64, void 32/48 — независимо от заполнения.
- Трубные соединения, ввод и вывод — **только UP**. Brass Lid Brace блокирует `IAspectSource` для воздушного переноса, не верхний трубный интерфейс и не ручные фиалы.
- Фиала забирает/вливает ровно **10**. Вручную в обе банки вливают только при `amount <= 240`; заполненную void-банку фиалой переполнять нельзя.
- Метка на наполненной банке берёт её текущий аспект. Typed label на пустой задаёт тип без добавления содержимого. Фильтр переживает извлечение последней эссенции и перенос банки.
- Shift-клик по стороне метки снимает метку; пустая рука для этого не требуется. Shift-клик пустой рукой по иной стороне опустошает содержимое и передаёт всё количество в flux.
- Дроп банки сохраняет содержимое и `AspectFilter`; brace выпадает отдельно. Разрушение банки подавляет обычный spill содержимого в flux. Не объединять перенос и намеренное выливание.
- Низкоуровневые `setAspects`, `addToContainer`, `addEssentia` сами не проверяют фильтр. `doesContainerAccept` — отдельный запрос, который проверяют корректные вызывающие операции. Это особенность оригинала, а не ошибка декомпиляции.

## 1. Состояние и ёмкость

`TileJarFillable` содержит `aspect`, `aspectFilter`, `amount`, `facing`, `blocked` и несохраняемый счётчик. Начальные значения: null/null/0/2/false/0. `facing=2` — NORTH, это сторона метки, а не направление верхнего входа.

В исходном восстановленном файле `CAPACITY` объявлено изменяемым `static int`; официальный JAR показывает **`public static final int CAPACITY = 250`**. Во всех проверенных операциях встречается именно константа 250. `TileJarFillableVoid` наследует те же поля и NBT, но имеет собственный скрывающий счётчик `count` для update.

Для корректного игрового состояния достаточно одного аспекта и количества 0…250. Два разных аспекта никогда не смешиваются. Метка и текущий тип хранения — разные поля: пустая банка может иметь фильтр при `aspect == null`.

Основание: [TileJarFillable][fillable], [TileJarFillableVoid][void], официальный constructor и `javap -constants`.

## 2. Точная семантика IAspectContainer

| Метод | Поведение BETA26 |
|---|---|
| `getAspects()` | Всегда новый AspectList, даже пустой. Добавляет единственный аспект только при `aspect != null && amount > 0`. Это отличается от item getter, который возвращает null для пустого списка. |
| `setAspects(list)` | При непустом списке выбирает `getAspectsSortedByAmount()[0]`, то есть первый аспект с наибольшим положительным количеством, и присваивает его количество. null/пустой список — **ничего не меняет**, это не команда очистки. Не ограничивает 250, не проверяет фильтр, не вызывает sync/dirty. |
| Normal `addToContainer(type,n)` | `n==0` немедленно возвращает 0. Принимает, если `(amount < 250 && type == aspect) || amount == 0`. Присваивает тип; добавляет `min(n,250-amount)`; возвращает **остаток**. Для любого ненулевого вызова делает sync(false)/dirty, даже при отказе. |
| `takeFromContainer(type,n)` | Успех только при `amount >= n && type == aspect`; частичного извлечения нет. Вычитает n; при количестве <=0 ставит amount=0 и aspect=null. Фильтр остаётся. Успешная операция делает sync(false)/dirty. Возвращает boolean. |
| `takeFromContainer(AspectList)` | Всегда false. Нельзя подменять вызов покомпонентным извлечением и считать это оригинальным поведением. |
| `doesContainerContainAmount(type,n)` | Проверяет количество и идентичность текущего типа. |
| `doesContainerContain(list)` | True, если **хотя бы один** тип из списка совпадает и amount>0. Количества запрошенного списка и полное покрытие всех его типов не проверяются. |
| `containerContains(type)` | amount при совпадении, иначе 0. |
| `doesContainerAccept(type)` | Только `aspectFilter == null || type.equals(aspectFilter)`. Не проверяет текущий аспект, свободное место или blocked. True не гарантирует, что add примет. |

Сравнения хранения используют идентичность зарегистрированных Aspect (`==`); запрос допуска использует equals. `AspectList` сортирует по убыванию положительных количеств, с сохранением порядка при равенстве. При некорректных количествах исходный алгоритм не является универсальной сортировкой.

И normal, и void прямым add допускают смену типа в пустой банке, даже если он противоречит фильтру. Нельзя молча перенести проверку фильтра внутрь add и утверждать побайтовое соответствие API. Ручная фиала, воздушный handler и другие корректные вызывающие методы отдельно соблюдают фильтр.

В исходных API нет проверки `n>0`, null-аспектов и общего clamp в setter/NBT. Отрицательные аргументы, неизвестные теги и вручную созданный многотипный/переполненный NBT относятся к некорректным входам, а не к обычному геймплею. Если порт их нормализует, это отдельное документируемое изменение на границе ввода, а не «исправленный исходник».

Основание: [TileJarFillable][fillable], [AspectList][aspect-list]; методы add/take/set/contain подтверждены JAR.

## 3. Void Jar: допуск переполнения и flux

Void add принимает при `type == aspect || amount == 0`, включая уже полную банку того же типа. При успешном приёме он прибавляет **всё** n и возвращает 0. Если сумма >250, выполняет один `world.rand.nextInt(250)`; при результате 0 вызывает `AuraHelper.polluteAura(world,pos,1.0f,true)`, затем оставляет amount=250.

Вероятность **1/250 на вызов, который действительно переполнил**, не на каждую уничтоженную единицу. Случай 249+1 не переполняет и не запускает RNG; 249+2 запускает один RNG; 250+100 также один. Несовместимый тип возвращает исходное n и не уничтожается. Отсутствующая метка не превращает полную void-банку в универсальный уничтожитель разных типов.

Перед add сохраняется `up = oldAmount < 250`. Sync/dirty вызывается только если up=true, даже при несовместимом ненулевом add. Переполнение уже полной банки не делает sync/dirty самой банки: её видимое количество не меняется. Aura pollution — отдельный side effect. Это подтверждено байткодом и не должно превращаться в обязательный flux за каждую потерю.

`ItemPhial` проверяет свободные 10 **до** вызова void add, поэтому ручной overflow не происходит. `BlockJarItem` при заборе из alembic тоже ограничивается 250 для normal и void item. Переполнение доступно через API тайла, трубный или воздушный транспорт; сами сети относятся к следующему этапу.

Основание: [Void Jar][void], [ItemPhial][phial], [BlockJarItem][jar-item].

## 4. IEssentiaTransport: верх, suction и вывод

| Состояние | Suction normal | Minimum normal | Suction void | Minimum void |
|---|---:|---:|---:|---:|
| Без метки, <250 | 32 | 32 | 32 | 32 |
| С меткой, <250 | 64 | 64 | 48 | 48 |
| Без метки, >=250 | 0 | 32 | 32 | 32 |
| С меткой, >=250 | 0 | 64 | 32 | 48 |

`getSuctionType` возвращает фильтр, если он есть, иначе aspect. `getEssentiaType` возвращает aspect, `getEssentiaAmount` — amount. Эти getters игнорируют аргумент стороны; проверка доступности — через `isConnectable`, `canInputFrom`, `canOutputTo`, каждый true только для **UP**. `setSuction` ничего не делает.

`takeEssentia(type,n,face)` при UP и успешном all-or-nothing take возвращает n, иначе 0. Он **не сравнивает suction** сам. `addEssentia` при UP возвращает `n-addToContainer(type,n)`, то есть фактически принятые единицы, а не остаток; на других гранях 0. Blocked нигде в этих методах не проверяется.

Normal update на сервере каждые 5 тиков вызывает fillJar только при amount<250. Void каждые 5 тиков вызывает fillJar и в полном состоянии. Каждый проход запрашивает сверху только **1 единицу**.

Точный fillJar:

1. `ThaumcraftApiHelper.getConnectableTile(pos,UP)` находит соседний IEssentiaTransport, у которого DOWN connectable; дополнительно требуется `canOutputTo(DOWN)`.
2. Выбор типа: сначала filter; затем непустой собственный aspect; иначе тип источника, если у него amount>0, его suction(DOWN) меньше своего suction(UP), а свой suction **>= minimum источника**.
3. Если выбранный тип не null и suction источника строго меньше своего, вызов `source.takeEssentia(type,1,DOWN)` и передача результата в addToContainer.

Важная исходная асимметрия: сравнение с minimum источника выполняется только в третьей ветви, когда пустая непомеченная банка выбирает новый тип. Для уже заданного filter/содержимого финальная ветвь minimum повторно не проверяет. В API-комментарии minimum описано словом «exceeds», но эта ветвь BETA26 использует **>=**, тогда как сравнение с текущим suction соседнего источника строгое. Не добавлять общий uniform guard в fillJar без объявления изменения.

Основание: [IEssentiaTransport][transport], [TileJarFillable][fillable], [ThaumcraftApiHelper][helper]; fillJar JAR offsets 39–133 и 133–173.

## 5. Brass Lid Brace и воздушный доступ

Brace устанавливается ПКМ предметом `jar_brace` на незаблокированную TileJarFillable, включая void; количество и наличие label не важны. `blocked=true`, исходный метод вызывает shrink(1); на клиенте звук key, на сервере markDirty. Ветка brace имеет приоритет над снятием label/выливанием.

`isBlocked()` возвращает поле blocked. [EssentiaHandler][air] в drain/find/add пропускает blocked IAspectSource: brace препятствует как воздушному извлечению для инфузии, так и воздушному заполнению. Он не меняет suction, doesContainerAccept, top IEssentiaTransport или ручные операции фиалой/банкой. Это не универсальный «запрет доступа» и не redstone valve.

В jar-классах нет отдельной операции ПКМ для снятия brace. При нормальном переносе он возвращается отдельным дропом; вновь поставленная банка по штатному item restore не blocked. Не сохранять brace в item NBT банки и не придумывать toggle пустой рукой.

Воздушный handler проверяет filter отдельно через doesContainerAccept, сначала пытается добавить к непустым источникам, а пустые складывает во второй проход. Drain пользуется takeFromContainer одного аспекта/единицы. Suction при воздушном доступе не используется. Реализация полного handler/труб в 0.9 не требуется, но контракт банки должен позволить эту интеграцию без изменения её правил.

## 6. Применение и снятие label

`ItemLabel` имеет legacy meta 0 blank / 1 filled, base=1; в creative выдаётся только blank. Filled label содержит один аспект с количеством 1 как описание фильтра, **не расходуемую эссенцию** (`ignoreContainedAspects=true`). Empty phial имеет meta 0, filled meta 1, base=10; современные идентификаторы/варианты должны сохранять эти различия по смыслу.

`BlockJar.applyLabel` возвращает false, если tile не fillable или filter уже есть. При amount==0 blank label (getAspects==null) не применяется. Typed label при amount==0 задаёт aspect из первого типа label, оставляет количество 0 и затем записывает этот aspect в filter. При amount>0 тип typed label **игнорируется**: фильтром становится уже находящийся в банке аспект. Содержимое при этом не заменяется и не добавляется.

Положение label определяется yaw игрока через вызов onBlockPlacedBy, а **не clicked side**: `floor(yaw*4/360+.5)&3`, 0→NORTH(2), 1→EAST(5), 2→SOUTH(3), 3→WEST(4). Эта же ориентация назначается при размещении банки. Применение label делает notify flags=3, dirty и звук jar.

ItemLabel onItemUseFirst выполняется на сервере; при успешном apply уменьшает label stack на 1 и обновляет inventory container. Если блок/тайл реализует ILabelable, item возвращает SUCCESS **даже когда apply вернул false**, но label при отказе не расходуется.

После проверки brace `BlockJar.onBlockActivated` проверяет Shift+filter!=null+clickedSide.ordinal()==facing. При совпадении снимает filter и выбрасывает **blank** label. Пустая рука не нужна; содержимое и aspect не очищаются. У пустой банки после снятия label может остаться aspect при amount=0; будущий add всё равно сможет выбрать новый тип.

Исходная ветвь снятия метки не вызывает markDirty или notify: байткод после spawn EntityItem на offset 508 сразу переходит к return (512→711). Это реальная особенность JAR, не дефект декомпиляции. Требования надёжного сохранения/синхронизации порта следует описывать отдельно, если порт явно устраняет этот дефект; нельзя заявлять, что исходник уже это делает.

Основание: [BlockJar][jar-block], [ItemLabel][label], [ConfigRecipes][recipes], официальный applyLabel и ItemLabel.onItemUseFirst.

## 7. Намеренное опустошение и загрязнение

Следующая ветвь после снятия label: Shift+пустой held item на fillable tile. Если filter==null, aspect=null; если filter есть, aspect сохраняется. На сервере до очистки вызывается `polluteAura(world,pos,(float)oldAmount,true)`, затем amount=0 и dirty. На клиенте звуки jar/bottle fill. Это передаёт **всё** старое содержимое в flux, включая void-банку; не вероятность 1/250 и не безопасное удаление.

Если игрок Shift-кликает пустой рукой именно по стороне label, снимается только метка — приоритет выше. Для выливания с сохранением filter нужно кликнуть другую сторону. После извлечения последней фиалой aspect=null, filter остаётся; после выливания помеченной банки amount=0, aspect может остаться. `getAspects()` в обоих случаях пустой, suction type продолжает быть filter.

Обычное разрушение — иной путь. `BlockJar.breakBlock` временно ставит общий `spillEssentia=false`, вызывает super, затем возвращает true. Super BlockTCTile иначе загрязнил бы ауру количеством IEssentiaTransport; для банки эта потеря подавлена. После сборки дропа содержимое переезжает в item, а не выбрасывается в ауру.

## 8. Phial: точные инвентарные операции

- Empty phial + normal/void jar или alembic с amount>=10: извлечь ровно 10, уменьшить held empty stack на 1, создать один filled phial с этим аспектом и 10. Остальные пустые фиалы не меняются. В jar-ветви сначала сохраняется aspect, затем извлекается: последняя порция корректно переживает очистку tile.aspect.
- Filled phial с одним типом + normal/void jar: нужны meta!=0, `amount<=240`, `doesContainerAccept(type)` и `addToContainer(type,10)==0`. После успеха held filled stack уменьшается на 1, игрок получает одну новую empty phial. Несовместимое уже имеющееся содержимое приводит к отказу add, даже если filter разрешает тип.
- Возврат результата — в inventory; если места нет, EntityItem. В операциях с банкой fallback появляется у центра банки; при заполнении из alembic — у игрока. InventoryContainer обновляется, звук bottle fill .25/1.0. Перенос не требует выбранной стороны UP; это ручная операция с блоком, отдельная от труб.
- Фиалой нельзя вливать обратно в alembic: BETA26 реализует там только извлечение. Failed use возвращает PASS. Клиент при потенциальном успехе делает swing и PASS, сервер выполняет мутацию и SUCCESS.
- `doesSneakBypassUse=true`: ручное использование phial не должно исчезать из-за обычного sneak suppression. Учитывать также приоритет item-use-before-block: пустой hand и phial — разные ветви.

Quirk: операция вливает фиксированный base=10 и не читает `AspectList.getAmount` фиалы. `makePhial(aspect,amt)` может программно записать иное amt без ограничения; ручной путь всё равно переносит 10. Нормальная creative/добытая filled phial всегда содержит 10. Поведение для испорченных/phial-with-9 NBT не следует считать новым способом частичного дозирования.

`onUpdate`/`onCreated` phial при отсутствии любого NBT и meta==1 меняют meta на 0. Непустой compound с отсутствующим Aspects автоматически этим условием не исправляется. ItemLabel переопределяет эти callbacks пустыми; общий ItemGenericEssentiaContainer не должен случайно назначать blank labels/phials случайный аспект.

Основание: [ItemPhial][phial], [ItemGenericEssentiaContainer][generic-item]; branch JAR offsets 244–341 и 435–593.

## 9. Jar item: сбор из alembic, размещение, перенос

`BlockJarItem.onItemUseFirst` на сервере позволяет held jar item забрать из alembic сразу всё доступное либо оставшуюся ёмкость. Он проверяет item filter и текущий первый содержимый аспект на совпадение с alembic. Непустая банка ограничивает взятое до `abs(itemVisSize-250)`, когда сумма превосходит 250. При amt<=0 — FAIL. Для корректного NBT это свободное место; для переполненного item NBT исходный abs ведёт себя иначе, не добавлять его как игровой режим.

Если held count>1, создаётся copy c новым суммарным содержимым и count=1, исходный stack уменьшается на 1; результат идёт в inventory либо EntityItem у игрока. Если count==1, mutate происходит в held stack. Copy сохраняет item filter/прочие NBT; содержимое обновляется setAspects. Факт наличия void item не включает overflow: здесь общий код BlockJarItem.

`placeBlockAt` сначала вызывает обычное размещение ItemBlock; при успехе на сервере `jar.setAspects(item.getAspects())`, затем отдельно восстанавливает строку `AspectFilter`, если ключ присутствует. Tile dirty и notify flags=3. **Blocked и старый facing из item не восстанавливаются**; facing назначается yaw при размещении. В частности, filter-only empty jar восстанавливает amount=0/aspect=null плюс filter — допустимое состояние.

`harvestBlock` и `dropBlockAsItemWithChance` для fillable вызывают `spawnFilledJar`: новый item jar, при amount>0 содержимое в Aspects, при filter!=null строка AspectFilter, при blocked отдельный jar_brace. Empty labeled jar сохраняет фильтр без необходимости добавлять фальшивую эссенцию. Исходный fillable путь не учитывает chance/fortune в собственном helper. Не вызывать оба пути одновременно в новом lifecycle и не добавлять второй дроп обычным loot table.

Normal→Void crafting через `ShapedArcaneVoidJar` переносит **весь compound** первого normal jar ingredient в result, в отличие от narrow field selection при block drop. Байткод вызывает setTagCompound напрямую с исходным compound; в порте можно использовать семантически независимую copy, сохранив данные, а не потерять Aspects/AspectFilter.

Creative важно разделять по путям. В проверенных mod methods phial/label/brace/jar-item взаимодействий **нет проверок creative**; исходные методы вызывают shrink/add как обычно. Но это не доказательство финального количества предмета после внешних Forge/Minecraft item-use wrappers: их creative restoration отдельно не аудитировался. BlockJar также не содержит собственного «drop on creative break»/special pick-block override; его helper вызывается из lifecycle harvest/drop. Не вводить без основания обязательный заполненный дроп на любое creative destroy, поскольку это изменяет стандартный caller lifecycle. Отдельно проверить серверные creative и survival операции и отсутствие двойного дропа.

Основание: [BlockJarItem][jar-item], [BlockJar][jar-block], [BlockTCTile][tile-block], [ShapedArcaneVoidJar][void-recipe].

## 10. Сохранение: точные NBT keys

| Где | Ключ/тип | Значение |
|---|---|---|
| Tile normal/void | `Aspect`: String | Текущий аспект, записывается только если не null. |
| Tile normal/void | `AspectFilter`: String | Фильтр, записывается только если не null. |
| Tile normal/void | `Amount`: **Short** | Содержимое; чтение getShort без clamp. |
| Tile normal/void | `facing`: **Byte** | Legacy EnumFacing ordinal стороны метки. |
| Tile normal/void | `blocked`: Boolean | Установленный brace. |
| Jar/phial/typed-label item | `Aspects`: List<Compound> | Каждый элемент `key`: String aspect tag, `amount`: **Int**. Заглавное Aspects и строчные поля — существенны. |
| Jar item | `AspectFilter`: String | Фильтр независимо от Aspects. |

TileThaumcraft использует readSyncNBT/writeSyncNBT и для persistence, и для update packet/tag. Счётчики count не сохраняются. Getter item возвращает null при отсутствии compound или пустом AspectList. Setter item создаёт compound при необходимости и переписывает Aspects list, сохраняя прочие ключи; передать null list нельзя — исходный setter его разыменовывает.

При missing tile keys constructor defaults не гарантированы: readSyncNBT присваивает getByte/getShort результат, например отсутствующий facing станет 0 (DOWN). Запись null полей не удаляет старые ключи из произвольного reused compound, она просто их не добавляет; штатная save/update создаёт свежий tag. Unknown aspect tag превращается в null при Aspect.getAspect; tooltip исходного jar item при имеющемся невалидном filter способен разыменовать null.

Item durability: `1-totalAspects/250`; bar показывается, если getAspects!=null. Filter-only empty jar не показывает бар содержимого, но показывает filter tooltip. Fill model property: пусто→0, 1…62→1, 63…125→2, 126…187→3, 188…250→4. Это следствие сравнения durability с .75/.5/.25, а не ровно четыре интервальных отсечки количества, придуманные при переносе.

Comparator normal/void: `floor(amount/250.0*14)+(amount>0?1:0)`: 0→0, 1→1, 250→15. Он зависит от количества, не filter/blocked. Brain jar — другой tile и другой xp-контракт; в scope хранения эссенции не входит.

## 11. Рецепты label/phial/brace и интеграция

ConfigRecipes BETA26: 4 blank labels из black dye+slimeball+4 paper; 8 empty phials из clay ball и 3 glass blocks; 2 braces из 4 brass nuggets и 4 sticks. Typed label shapeless: blank label + filled phial конкретного аспекта → label meta1 с одним описательным аспектом. Filled label→blank label — отдельный shapeless рецепт.

Содержимое filled phial при создании typed label **не расходуется**: CraftingEvents для label output с NBT проходит 9 слотов matrix, увеличивает ItemPhial stack на 1 и возвращает его в matrix перед обычным расходом ингредиентов. Это не превращает фиалу в empty и не изымает 1/10 эссенции. При переносе на recipe remainder сохранить семантику «та же полная фиала возвращается», без grow-based дюпа в новом callback lifecycle. Сам момент grow подтверждён официальным JAR offsets 93–117.

Пока нет operational alembic/tubes/transfusers/infusion, нужны инвентарные действия и tile API; наличие визуального catalogue блока не означает, что надо запускать неготовую сеть. Brace/filter/top-only интерфейс и state persistence являются контрактом следующего этапа.

## 12. Проверенные особенности JAR и координаты

| Проверка | Метод/offsets официального JAR |
|---|---|
| Final capacity 250 | `javap -constants TileJarFillable`: CAPACITY=250. |
| setAspects не очищает/null/no-clamp | `TileJarFillable.setAspects`: 0–35. |
| add не проверяет filter | `TileJarFillable.addToContainer`: 6–63; отдельный doesContainerAccept 0–19. |
| Take последней порции оставляет filter | `takeFromContainer(Aspect,int)`: 26–43 очищают только aspect/amount. |
| void RNG per-call и dirty только old<250 | `TileJarFillableVoid.addToContainer`: 0–15 old flag, 54–99 overflow, 102–115 sync gate. |
| minimum только в выборе нового типа | `TileJarFillable.fillJar`: 39–133 выбор; 133–173 последний pull. |
| label apply сохраняет content type и определяет yaw | `BlockJar.applyLabel`: 34–131, 131–163. |
| Снятие label без dirty/notify | `BlockJar.func_180639_a`: 378–384 clear, 437–508 drop, 512→711 return. |
| Dump всё количество в flux | `BlockJar.func_180639_a`: 549–566 aspect gate, 682–706 pollution/amount=0. |
| Brace отдельным дропом | `BlockJar.spawnFilledJar`: 103–130. |
| Ручная phial не переполняет void | `ItemPhial.onItemUseFirst`: 506–563. |
| Phial переносит base, не своё NBT amount | `ItemPhial.onItemUseFirst`: 552–563; getAmount не вызывается. |
| Jar item alembic capacity использует abs | `BlockJarItem.onItemUseFirst`: 128–162. |
| Void upgrade сохраняет compound | `ShapedArcaneVoidJar.func_77572_b`: 29–37, 53–58. |
| Typed label оставляет filled phial | `CraftingEvents.onCrafting`: 93–117. |

Повторяемая команда: `javap -classpath work/Thaumcraft-1.12.2-6.1.BETA26.jar -c -p <fully.qualified.Class>`. Префиксы SRG func_* относятся к тому же mapped source method, а не другой версии мода. Проверены mod methods; не выполнен эталонный gameplay в оригинальном 1.12.2 и не аудитировались его Forge/Minecraft creative wrapper callbacks.

## 13. Минимальные приёмочные сценарии 0.9

1. Normal add до 250 и остаток; несовместимый тип; zero add; take целиком/недостаточно; filter остаётся после последней единицы.
2. Void 249+1 без RNG, 249+2 с одним RNG, 250+n с одним RNG; несовместимый тип не уничтожается; full suction=32 даже с filter, minimum при этом 48.
3. Метка blank на empty не расходуется; blank на filled работает; typed label на empty задаёт filter без содержимого; typed-other на filled берёт тип банки. Снятие label не выливает эссенцию.
4. Shift empty-hand по другой стороне выливает amount в flux и сохраняет filter; по стороне label снимает его. Установка brace не сбрасывает filter/content.
5. Phial: 9 единиц не извлекаются, 10→одна полная; 240+10→250; 241+10 отвергается normal и void; тип/фильтр не совпадает — без расхода. Held stack и full inventory fallback сохраняют число контейнеров.
6. Перенос partial/full/filter-only банки, выгрузка чанка и server restart: quantity/type/filter сохраняются; brace дропается отдельно, нет pollution на обычный перенос и нет двойного jar loot.
7. API top-only input/output; side-aware transport против side-independent getters; blocked подавляет воздушный доступ, но не phial/top transport.
8. Normal→Void result сохраняет Aspects+AspectFilter; typed label crafting возвращает полную phial без дюпа; clearing label recipe не производит эссенцию.
9. Отдельные survival/creative действия с учётом внешнего item-use lifecycle; состояние сервера authoritative, prediction не создаёт второй предмет/flux.
10. Клиентские label, brace, уровень/цвет жидкости, tooltip/bar и comparator отражают синхронизированное состояние. Уже проверенные прозрачные модели 0.8 должны использовать данные tile, а не подменять механику одним item NBT preview.

[fillable]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/essentia/TileJarFillable.java
[void]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/tiles/essentia/TileJarFillableVoid.java
[jar-block]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/blocks/essentia/BlockJar.java
[jar-item]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/blocks/essentia/BlockJarItem.java
[phial]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/items/consumables/ItemPhial.java
[label]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/items/consumables/ItemLabel.java
[transport]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/aspects/IEssentiaTransport.java
[helper]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/ThaumcraftApiHelper.java
[aspect-list]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/aspects/AspectList.java
[generic-item]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/api/items/ItemGenericEssentiaContainer.java
[tile-block]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/blocks/BlockTCTile.java
[void-recipe]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/crafting/ShapedArcaneVoidJar.java
[air]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/lib/events/EssentiaHandler.java
[recipes]: https://github.com/TheDarkTower314/Thaumcraft-6-Source-Code/blob/954022bb777b7546281fb36df8522f0ba6b43f81/src/main/java/thaumcraft/common/config/ConfigRecipes.java
