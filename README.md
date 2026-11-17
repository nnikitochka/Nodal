# Nodal

> [English](README_EN.md) | **Русский**

Высокопроизводительная иерархическая система событий (Event Tree) на чистом Kotlin для JVM, архитектурно вдохновлённая решением из [Minestom](https://github.com/Minestom/Minestom).

В отличие от классических плоских шин событий (Bukkit, Guava EventBus), **Nodal** организует слушатели в виде **дерева узлов (`EventNode`)**. Это позволяет изолировать логику отдельных модулей, миров или игровых сессий, отсекать ненужные вызовы в корне и подключать/отключать целые ветки слушателей за одну операцию.

---

## ⚡ Особенности

* **Древовидная архитектура (`EventNode`):** возможность строить иерархии узлов (например: `Корень -> Сервер -> Мир -> Арена`). Отключение ветки удаляет всех её слушателей мгновенно.
* **$O(1)$ производительность:** быстрый поиск слушателей по хэшмапе классов с кэшированием вызовов (`ListenerHandle`).
* **Ранняя отсечка спам-ивентов:** проверка условий на уровне узлов позволяет не спамить вызовами слушателей там, где они не нужны (например, движение игроков в лобби не доходит до узлов мини-игры).
* **Идиоматичный Kotlin DSL:** лаконичная регистрация слушателей через `reified` дженерики без boilerplate-кода.
* **Атомарная отмена (`callCancellable`):** встроенная обработка отмены событий через лямбды.
* **Срок жизни и фильтры:** возможность задать лимит срабатываний (`expireCount(1)`) или условие завершения прямо в билдере слушателя.
* **Чистый Kotlin:** без корутин, без рефлексии в горячих точках, без тяжелых сторонних библиотек и без зависимостей на Minecraft.

---

## 📦 Подключение

### Gradle (Kotlin DSL)
```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("su.nnedition.nodal:nodal:1.0")
}
```

---

## 🚀 Быстрый старт

### 1. Создание событий
Любое событие реализует интерфейс `Event`. Для отменяемых событий используйте `CancellableEvent`:

```kotlin
import su.nnedition.nodal.Event
import su.nnedition.nodal.trait.CancellableEvent

// Обычное событие
class ServerMessageEvent(val message: String) : Event

// Отменяемое событие
class PlayerDamageEvent(
    val playerId: String,
    val damage: Double,
    override var isCancelled: Boolean = false
) : CancellableEvent
```

---

### 2. Подписка на события

```kotlin
import su.nnedition.nodal.EventDispatcher
import su.nnedition.nodal.EventListener

val root = EventDispatcher.globalHandler

// Простой слушатель через reified generics
root.addListener(
    EventListener.of<ServerMessageEvent> { event ->
        println("Сообщение: ${event.message}")
    }
)

// Продвинутый слушатель с фильтром и сроком жизни
val damageListener = EventListener.builder<PlayerDamageEvent>()
    .filter { it.damage > 10.0 }
    .expireCount(1) // выполнится ровно 1 раз и сам удалится
    .handler { event ->
        println("Критический урон: ${event.damage}")
    }
    .build()

root.addListener(damageListener)
```

---

### 3. Вызов событий

```kotlin
// Обычный вызов через синглтон
EventDispatcher.call(ServerMessageEvent("Сервер запущен!"))

// Атомарный вызов с отменой: действие выполнится ТОЛЬКО если событие НЕ отменено другим слушателем
EventDispatcher.callCancellable(PlayerDamageEvent("player1", 15.0)) {
    println("Урон успешно нанесён игроку!")
}
```

---

### 4. Иерархия узлов (Дерево)

Вы можете группировать слушатели по модулям или игровым аренам:

```kotlin
import su.nnedition.nodal.EventNode

// Создаём дочерний узел для конкретной арены
val arenaNode = EventNode.all("arena-1")

arenaNode.addListener(
    EventListener.of<PlayerDamageEvent> {
        println("Урон на арене 1")
    }
)

// Прикрепляем узел арены к глобальному дереву
root.addChild(arenaNode)

// Когда арена завершилась — отключаем её в одну строчку:
root.removeChild(arenaNode) 
// Все слушатели арены мгновенно перестают вызываться и очищаются GC
```

---

### 5. Быстрый вызов без мап (`ListenerHandle`)

Если событие вызывается сотни раз в секунду, используйте `ListenerHandle` для прямого вызова:

```kotlin
val handle = EventDispatcher.getHandle<ServerMessageEvent>()

// Проверка: есть ли вообще слушатели?
if (handle.hasListener()) {
    handle.call(ServerMessageEvent("Быстрый вызов"))
}
```

---

## 📄 Лицензия и благодарности

Проект распространяется под свободной лицензией **[Apache License 2.0](LICENSE)**.

Архитектура узлового дерева событий вдохновлена и адаптирована из проекта [Minestom](https://github.com/Minestom/Minestom) (c) The Minestom Authors.
