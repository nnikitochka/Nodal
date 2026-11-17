# Nodal

> **English** | [Русский](README.md)

A high-performance, tree-based event system (Event Tree) written in pure Kotlin for the JVM, architecturally inspired by [Minestom](https://github.com/Minestom/Minestom).

Unlike traditional flat event buses (Bukkit, Guava EventBus), **Nodal** organizes event listeners into a **tree of nodes (`EventNode`)**. This enables isolating individual modules, game instances, or worlds, filtering out unnecessary calls early at the root, and mounting/unmounting entire branches of listeners with a single operation.

---

## ⚡ Features

* **Hierarchical Tree Architecture (`EventNode`):** Build complex node hierarchies (e.g. `Root -> Server -> World -> Arena`). Detaching a branch instantly deactivates all of its listeners.
* **$O(1)$ Dispatch Performance:** Fast listener resolution via cached class handles (`ListenerHandle`).
* **Early Filtering for High-Frequency Events:** Condition checks at the node level prevent spamming listeners with irrelevant events (e.g. lobby player movement never reaches minigame listeners).
* **Idiomatic Kotlin API:** Clean listener registration using `reified` generics without unnecessary boilerplate.
* **Atomic Cancellation (`callCancellable`):** Built-in cancellation handling with lambda callbacks.
* **Expiration & Filtering:** Set iteration limits (`expireCount(1)`) or predicates directly inside the listener builder.
* **Zero Coroutines, Zero Bloat:** Pure synchronous JVM execution without heavy third-party libraries or Minecraft dependencies.

---

## 📦 Installation

### Gradle (Kotlin DSL)
```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("su.nnedition.nodal:nodal:1.0")
}
```

---

## 🚀 Quick Start

### 1. Defining Events
Any event implements the `Event` interface. For cancellable events, implement `CancellableEvent`:

```kotlin
import su.nnedition.nodal.Event
import su.nnedition.nodal.trait.CancellableEvent

// Standard event
class ServerMessageEvent(val message: String) : Event

// Cancellable event
class PlayerDamageEvent(
    val playerId: String,
    val damage: Double,
    override var isCancelled: Boolean = false
) : CancellableEvent
```

---

### 2. Registering Listeners

```kotlin
import su.nnedition.nodal.EventDispatcher
import su.nnedition.nodal.EventListener

val root = EventDispatcher.globalHandler

// Simple listener with reified generics
root.addListener(
    EventListener.of<ServerMessageEvent> { event ->
        println("Message: ${event.message}")
    }
)

// Advanced listener with filters and expiration
val damageListener = EventListener.builder<PlayerDamageEvent>()
    .filter { it.damage > 10.0 }
    .expireCount(1) // runs exactly once, then automatically unregisters
    .handler { event ->
        println("Critical damage: ${event.damage}")
    }
    .build()

root.addListener(damageListener)
```

---

### 3. Dispatching Events

```kotlin
// Standard call
EventDispatcher.call(ServerMessageEvent("Server started!"))

// Atomic cancellable dispatch: the callback runs ONLY if the event was NOT cancelled by another listener
EventDispatcher.callCancellable(PlayerDamageEvent("player1", 15.0)) {
    println("Damage applied successfully!")
}
```

---

### 4. Node Hierarchy (Tree)

Group listeners by features, worlds, or match sessions:

```kotlin
import su.nnedition.nodal.EventNode

// Create a child node for a specific arena
val arenaNode = EventNode.all("arena-1")

arenaNode.addListener(
    EventListener.of<PlayerDamageEvent> {
        println("Damage inside Arena 1")
    }
)

// Attach the arena node to the global tree
root.addChild(arenaNode)

// When the match ends — detach it in one call:
root.removeChild(arenaNode)
// All arena listeners immediately stop firing and are eligible for GC
```

---

### 5. High-Performance Direct Calls (`ListenerHandle`)

For hot paths called hundreds of times per second, use `ListenerHandle` to bypass map lookups:

```kotlin
val handle = EventDispatcher.getHandle<ServerMessageEvent>()

// Check if any listeners are registered before allocating objects
if (handle.hasListener()) {
    handle.call(ServerMessageEvent("Fast dispatch"))
}
```

---

## 📄 License & Acknowledgements

This project is licensed under the **[Apache License 2.0](LICENSE)**.

The hierarchical event tree architecture is inspired by and adapted from [Minestom](https://github.com/Minestom/Minestom) (c) The Minestom Authors.
