# Воздушная эссенция и transfuser: контракт handler для 0.25

Проверены исходный `EssentiaHandler.java` из закреплённого TC6 и
`javap -c -p` официального `Thaumcraft-1.12.2-6.1.BETA26.jar`.
Этот документ расширяет `ESSENTIA-AIRBORNE-BETA26-AUDIT.md`; прежние правила
матрицы, кэша, расстояния и повторных попыток остаются действующими.
Зеркала, переход между измерениями и их API пока не реализованы.

JAR SHA-256:
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.

| Класс | SHA-256 официального class |
| --- | --- |
| EssentiaHandler | `6eeec4d53fae4fe18b8d16306e61e942f1f3b1e27f6ce4b5de8cdd02ba362614` |
| TileEssentiaInput | `cfcd37139ef07b908c4fce2bb6e79be8bd803fb71fcab35d6819dfcc1d5abae5` |
| TileEssentiaOutput | `15edf117a82e3c33cbfbee707d6dbd170a3bd1f6c03135baa9234f75b09745cb` |

## Исходные операции

`addEssentia` использует общий список `IAspectSource` возле потребителя.
Первый проход пропускает `isBlocked()`, проверяет `doesContainerAccept` и
откладывает совместимые пустые источники во второй проход. Непустые совместимые
источники получают первую попытку даже при большем расстоянии. Внутри обоих
проходов сохранён исходный стабильный порядок квадратов расстояния и порядок
`aa / bb / cc` при равенстве. Пустота означает `getAspects()==null` или
`visSize()==0`; это не проверка конкретного класса банки или количества
требуемого аспекта.

Успех — `addToContainer(aspect,1)<=0`. Полная обычная банка отказывает;
полная совместимая пустотная банка уничтожает переполнение и сообщает успех.
Метка ограничивает добавление, скоба ограничивает воздушный доступ. При drain
метка не проверяется: извлекается реальное содержимое, включая содержимое,
не совпадающее с меткой после low-level/NBT вызова.

Исчезнувшая cached-координата прерывает текущий проход. Уже найденные до неё
пустые банки всё ещё получают второй проход. Если ни одна попытка не удалась,
handler удаляет положительный список и ставит общие десять секунд реального
времени. Этот отказ задерживает drain, add, find и confirmed discovery данной
координаты. `refreshSources` снимает список, но не снимает задержку. Успешный
список не имеет TTL, а его ключ не включает аспект, тип операции, направление
или range. Изменённые параметры используют уже построенный список.

Confirmed discovery проверяет наличие единицы, не извлекая её. В оригинале
единственный static-набор `las/lasp/lat/lext` перезаписывается каждым успешным
query; `confirmDrain()` реально извлекает единицу, отправляет FX при успехе
и обнуляет ссылки. Это не постоянный буфер и не NBT машины.

`TileEssentiaInput` раз в пять тиков проверяет задний connectable peer,
его output-face, положительный остаток, suction строго меньше 128 и minimum
suction не больше 128. Направление поиска — front, range16, ext5. Оригинал
сначала добавляет единицу в воздушную банку, а затем вызывает peer.take,
не проверяя результат списания; отказ буфера после добавления мог создать
лишнюю единицу.

`TileEssentiaOutput` раз в пять тиков проверяет задний peer input-face,
положительный suction и ненулевой требуемый аспект. Направление front,
range16, ext5. Он делает read-only confirmed query, пытается вставить единицу
в peer и лишь после успеха подтверждает извлечение из банки. Глобальное
confirmation и отказ позднего source debit потенциально связывали разные
потребители или создавали лишнюю единицу.

## Публичный API порта

Все результаты — `Optional<Transfer>`; пустой результат не разрешает
потребителю учитывать оплату или отправлять FX. `Transfer` содержит
`source/target/aspect/ext` с неизменяемыми копиями координат.

| API AirborneEssentiaManager | Назначение |
| --- | --- |
| `drain(consumer,aspect,direction,range,ext)` | Старое извлечение матрицы: source → consumer.below() |
| `find(consumer,aspect,direction,range)` | Read-only presence; отказ не удаляет положительный список |
| `add(consumer,aspect,direction,range,ext)` | Вставляет одну уже предоставленную единицу: consumer → jar |
| `prepareDrain(consumer,aspect,direction,range,ext)` | Возвращает отдельный непрозрачный DrainConfirmation без списания |
| `confirmDrain(consumer,confirmation)` | Один реальный debit обещанной source tile, source → consumer |
| `transferFromTransport(consumer,peer,front,range,ext)` | API ess_input, peer.take → airborne jar.add |
| `transferToTransport(consumer,peer,front,range,ext)` | API ess_output, airborne jar.take → peer.add |
| `transferToSources(consumer,source,aspect,sourceFace,direction,range,ext)` | Нижний уровень для exact unit transfer из физического peer |
| `transferFromSources(consumer,destination,aspect,destinationFace,direction,range,ext)` | Нижний уровень exact unit transfer в физический peer |
| `confirmDrainTo(consumer,confirmation,destination,destinationFace)` | Коммит конкретного preview в физический peer |

Два bridge API повторно проверяют исходные sided/suction условия. Нижний
уровень проверяет сторону, реальную соседнюю координату peer и loaded identity;
частоту пять тиков задаёт сама машина. Ни vis, ни дополнительная эссенция,
ни новая задержка успешного действия не добавлены. Матрица продолжает
использовать прежний drain с визуальной целью на блок ниже; transfuser
использует координату самой машины, как исходный packet handler.

## Явные современные адаптации

- Confirmation принадлежит конкретному SourceCache, consumer instance,
  ServerLevel и source instance. Он не заменяет другой preview, не является
  резервом хранимой эссенции и используется не более одного раза. Чужой consumer
  и вызов вне серверного потока не расходуют валидный promise. Два preview одной
  единицы не обеспечивают два debit: каждый commit проверяет фактический остаток.
- Commit заново проверяет скобу, loaded source/consumer identity, removed-state
  и наличие единицы. Поставленная на старую координату новая банка не заменяет
  source обещания. Consumer/world/server cleanup инвалидирует pending promises
  через transient lifecycle epoch. Refresh положительного списка этого не делает.
- Переход source → destination выполняется в одном серверном вызове под reentry
  guard. Вход сначала реально списывает единицу physical source, затем добавляет
  её native jar. Выход сначала списывает native jar, затем пробует physical peer.
  При обычном отказе принимающей стороны точный snapshot источника восстанавливается;
  повторный preview/commit не может оплатить ту же операцию.
- Paid transfer допускает native final jars и точные, проверенные классы
  Tube/TubeBuffer/TubeFilter, Alembic, Centrifuge, Thaumatorium/Core/Top и GolemPress.
  Произвольные addon/subclass callbacks не имеют rollback контракта и не входят
  в этот путь. Generic direct drain/add/preview продолжают принимать IAspectSource,
  как оригинал; generic query сам по себе не обещает двухсторонней атомарности.
- Source buffer veto не вставляет единицу до оплаты и не создаёт новую задержку
  поиска destination. Отказ самого handler найти принимающую банку по-прежнему
  создаёт исходные десять секунд. Refusal physical output не создаёт такой delay:
  успешный read-only source discovery сохраняется для следующей попытки.
- Second empty pass дополнительно проверяет скобу и идентичность отложенной tile.
  В исходном бинарнике скоба повторно не проверяется. Это защищает от изменения
  банки через callback во время первого прохода и явно меняет только такой случай.
- Все платёжные операции проверяют серверный поток. Discovery/commit используют
  только уже присутствующий FULL chunk через getChunkNow: чанки не загружаются,
  не создаются и не повышаются по статусу ради перелёта эссенции. Rollback никогда
  не воссоздаёт удалённую машину и не заменяет новую tile на старой координате.
  Гарантия native единичной передачи относится к обычным операциям этих endpoints;
  разрушение мира произвольным внешним callback не является поддержанным контрактом.
- Reentry во время mutation возвращает отказ без дебита и без изменения кэша.
  Исключение native endpoint после списания восстанавливает живой source snapshot
  и пробрасывается; оно не превращается в фиктивный успешный Transfer.

Cold `canAcceptEssentia` с его исходным вызовом `findEssentia` и зеркало-specific
`ignoreMirror` не добавлялись: оба устройства используют непосредственно add
или confirmed drain. Нельзя считать этот этап реализацией зеркал.

## Определённые проверки

`AirborneEssentiaGameTests` сохраняет прежние 19 определений и добавляет 19:
приоритет nonempty/empty, distance tie, полная normal банка, тип/метка/скоба,
void overflow, общий cache/delay и точное равенство deadline, отсутствие TTL,
stale break с ранее deferred empty, consumer ownership и конкуренция одной
единицы, preview без списания, brace/replacement/removal/lifecycle, source buffer
veto без duplication/delay, настоящая оплата native source, отказ native output
с полной сохранностью source NBT, replay, неподдержанный callback transport,
reentry, deferred brace и серверные/off-thread guards.

Это определения тестов, не утверждение успешного прогона. Финальный Gradle,
полный GameTest server и hidden client запускает ведущий агент; фактические
результаты фиксируются в VALIDATION.md после их завершения.
