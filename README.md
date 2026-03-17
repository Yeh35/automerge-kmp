# automerge-kmp

Kotlin Multiplatform wrapper for [Automerge](https://automerge.org/) CRDT, powered by Rust + [UniFFI](https://mozilla.github.io/uniffi-rs/).

Build collaborative, offline-first applications with automatic conflict resolution across Android, iOS, and JVM.

## Supported Platforms

| Platform | Target |
|----------|--------|
| Android  | arm64, x86_64 |
| iOS      | arm64, x64, simulatorArm64 |
| JVM      | macOS, Linux, Windows |

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.yeh35:automerge-kmp:0.1.0")
}
```

## Quick Start

### Create and manipulate a document

```kotlin
import automerge.kmp.Document

// Create a new document
val doc = Document()

// Put values in the root map
doc.putInMap("title", "My Document")
doc.putIntInMap("version", 1)
doc.putBoolInMap("published", false)
doc.putDoubleInMap("score", 4.5)

// Read values
val title = doc.getStringFromMap("title")   // "My Document"
val version = doc.getIntFromMap("version")  // 1L
val keys = doc.keys()                       // ["published", "score", "title", "version"]

// Don't forget to close when done (or use .use {})
doc.close()
```

### Use `use` block for automatic cleanup

```kotlin
Document().use { doc ->
    doc.putInMap("key", "value")
    doc.getStringFromMap("key") // "value"
}
```

### Nested maps

```kotlin
val doc = Document()
val user = doc.putObjectInMap("user", AmObjType.MAP)
doc.putInNestedMap(user, "name", "Alice")
doc.putIntInNestedMap(user, "age", 30)

val name = doc.getStringFromNestedMap(user, "name") // "Alice"
```

### Lists

```kotlin
val doc = Document()
val items = doc.putListInMap("items")
doc.insertInList(items, 0uL, "first")
doc.insertInList(items, 1uL, "second")

val length = doc.listLength(items)              // 2uL
val first = doc.getStringFromList(items, 0uL)   // "first"
```

### Collaborative text editing

```kotlin
val doc = Document()
val textId = doc.putTextInMap("content")

doc.spliceText(textId, 0uL, 0, "Hello World")
// Replace "World" with "Automerge"
doc.spliceText(textId, 6uL, 5, "Automerge")

val text = doc.getText(textId) // "Hello Automerge"
```

### Save and load

```kotlin
// Save to bytes
val doc = Document()
doc.putInMap("key", "value")
val bytes: ByteArray = doc.save()

// Load from bytes
val restored = Document.load(bytes)
restored.getStringFromMap("key") // "value"
```

### Fork and merge

```kotlin
val doc1 = Document()
doc1.putInMap("base", "value")

// Fork creates an independent copy
val doc2 = doc1.fork()
doc1.putInMap("from_doc1", "hello")
doc2.putInMap("from_doc2", "world")

// Merge changes from doc2 into doc1
doc1.merge(doc2)
doc1.getStringFromMap("from_doc1") // "hello"
doc1.getStringFromMap("from_doc2") // "world"
```

## API Overview

### `Document`

| Method | Description |
|--------|-------------|
| `Document()` | Create a new empty document |
| `Document.load(bytes)` | Load a document from binary data |
| `save()` | Serialize the document to bytes |
| `fork()` | Create an independent copy |
| `merge(other)` | Merge another document into this one |
| `getActorId()` | Get the unique actor ID |
| `close()` | Release native resources |

### Root Map Operations

| Method | Description |
|--------|-------------|
| `putInMap(key, value)` | Put a string value |
| `putIntInMap(key, value)` | Put an integer value |
| `putDoubleInMap(key, value)` | Put a double value |
| `putBoolInMap(key, value)` | Put a boolean value |
| `putNullInMap(key)` | Put a null value |
| `getStringFromMap(key)` | Get a string (or null) |
| `getIntFromMap(key)` | Get an integer (or null) |
| `keys()` | Get all keys |

### Nested Objects

| Method | Description |
|--------|-------------|
| `putObjectInMap(key, type)` | Create a nested Map/List/Text, returns handle |
| `putListInMap(key)` | Create a list, returns handle |
| `putTextInMap(key)` | Create a text object, returns handle |

### List Operations (via handle)

| Method | Description |
|--------|-------------|
| `insertInList(handle, index, value)` | Insert a string |
| `insertIntInList(handle, index, value)` | Insert an integer |
| `listLength(handle)` | Get list length |
| `getStringFromList(handle, index)` | Get a string at index |

### Text Operations (via handle)

| Method | Description |
|--------|-------------|
| `spliceText(handle, index, delete, text)` | Insert/replace/delete text |
| `getText(handle)` | Get full text content |

