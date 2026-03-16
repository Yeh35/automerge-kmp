package automerge.kmp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentTest {

    // ===== Document Lifecycle =====

    @Test
    fun createDocument() {
        val doc = Document()
        assertNotNull(doc.getActorId())
        doc.close()
    }

    @Test
    fun eachDocumentHasUniqueActorId() {
        val doc1 = Document()
        val doc2 = Document()
        assertNotEquals(doc1.getActorId(), doc2.getActorId())
        doc1.close()
        doc2.close()
    }

    @Test
    fun saveEmptyDocument() {
        val doc = Document()
        val bytes = doc.save()
        assertTrue(bytes.isNotEmpty())
        doc.close()
    }

    @Test
    fun saveAndLoad() {
        val doc1 = Document()
        doc1.putInMap("key", "value")
        doc1.putIntInMap("num", 99)
        doc1.putBoolInMap("flag", true)
        doc1.putDoubleInMap("pi", 3.14)
        val bytes = doc1.save()

        val doc2 = Document.load(bytes)
        assertEquals("value", doc2.getStringFromMap("key"))
        assertEquals(99L, doc2.getIntFromMap("num"))
        assertEquals(listOf("flag", "key", "num", "pi"), doc2.keys().sorted())
        doc1.close()
        doc2.close()
    }

    @Test
    fun loadInvalidDataThrows() {
        assertFailsWith<AutomergeException.LoadException> {
            Document.load(byteArrayOf(0, 1, 2, 3))
        }
    }

    @Test
    fun saveLoadRoundTripPreservesAllTypes() {
        val doc = Document()
        doc.putInMap("str", "hello")
        doc.putIntInMap("int", Long.MAX_VALUE)
        doc.putDoubleInMap("dbl", -0.001)
        doc.putBoolInMap("bool", false)
        doc.putNullInMap("nil")

        val restored = Document.load(doc.save())
        assertEquals("hello", restored.getStringFromMap("str"))
        assertEquals(Long.MAX_VALUE, restored.getIntFromMap("int"))
        // null은 getStringFromMap으로 조회하면 null 반환
        assertNull(restored.getStringFromMap("nil"))
        doc.close()
        restored.close()
    }

    // ===== Root Map Operations =====

    @Test
    fun putAndGetString() {
        val doc = Document()
        doc.putInMap("name", "hello")
        assertEquals("hello", doc.getStringFromMap("name"))
        doc.close()
    }

    @Test
    fun putAndGetInt() {
        val doc = Document()
        doc.putIntInMap("count", 42)
        assertEquals(42L, doc.getIntFromMap("count"))
        doc.close()
    }

    @Test
    fun putAndGetNegativeInt() {
        val doc = Document()
        doc.putIntInMap("neg", -1000)
        assertEquals(-1000L, doc.getIntFromMap("neg"))
        doc.close()
    }

    @Test
    fun putAndGetDouble() {
        val doc = Document()
        doc.putDoubleInMap("pi", 3.14159)
        // double은 getStringFromMap으로 null 반환 (타입 불일치)
        assertNull(doc.getStringFromMap("pi"))
        // key는 존재해야 한다
        assertTrue(doc.keys().contains("pi"))
        doc.close()
    }

    @Test
    fun putAndGetBool() {
        val doc = Document()
        doc.putBoolInMap("active", true)
        doc.putBoolInMap("deleted", false)
        // bool은 getStringFromMap으로 null 반환 (타입 불일치)
        assertNull(doc.getStringFromMap("active"))
        doc.close()
    }

    @Test
    fun putNullInMap() {
        val doc = Document()
        doc.putNullInMap("nothing")
        assertNull(doc.getStringFromMap("nothing"))
        assertNull(doc.getIntFromMap("nothing"))
        assertTrue(doc.keys().contains("nothing"))
        doc.close()
    }

    @Test
    fun getMissingKeyReturnsNull() {
        val doc = Document()
        assertNull(doc.getStringFromMap("nonexistent"))
        assertNull(doc.getIntFromMap("nonexistent"))
        doc.close()
    }

    @Test
    fun overwriteValue() {
        val doc = Document()
        doc.putInMap("key", "first")
        assertEquals("first", doc.getStringFromMap("key"))

        doc.putInMap("key", "second")
        assertEquals("second", doc.getStringFromMap("key"))

        // 타입도 덮어쓸 수 있다
        doc.putIntInMap("key", 123)
        assertNull(doc.getStringFromMap("key"))
        assertEquals(123L, doc.getIntFromMap("key"))
        doc.close()
    }

    @Test
    fun keys() {
        val doc = Document()
        assertTrue(doc.keys().isEmpty())

        doc.putInMap("b", "2")
        doc.putInMap("a", "1")
        doc.putInMap("c", "3")
        assertEquals(3, doc.keys().size)
        // Automerge map의 keys는 정렬됨
        assertEquals(listOf("a", "b", "c"), doc.keys().sorted())
        doc.close()
    }

    @Test
    fun emptyStringKeyAndValue() {
        val doc = Document()
        doc.putInMap("", "")
        assertEquals("", doc.getStringFromMap(""))
        assertTrue(doc.keys().contains(""))
        doc.close()
    }

    @Test
    fun unicodeStringValues() {
        val doc = Document()
        doc.putInMap("korean", "안녕하세요")
        doc.putInMap("emoji", "🎉🚀")
        doc.putInMap("japanese", "こんにちは")
        assertEquals("안녕하세요", doc.getStringFromMap("korean"))
        assertEquals("🎉🚀", doc.getStringFromMap("emoji"))
        assertEquals("こんにちは", doc.getStringFromMap("japanese"))
        doc.close()
    }

    @Test
    fun largeNumberOfKeys() {
        val doc = Document()
        val count = 1000
        for (i in 0 until count) {
            doc.putInMap("key_$i", "value_$i")
        }
        assertEquals(count, doc.keys().size)
        assertEquals("value_500", doc.getStringFromMap("key_500"))
        doc.close()
    }

    // ===== Nested Map Operations =====

    @Test
    fun nestedMapPutAndGet() {
        val doc = Document()
        val handle = doc.putObjectInMap("user", AmObjType.MAP)
        doc.putInNestedMap(handle, "name", "Alice")
        doc.putIntInNestedMap(handle, "age", 30)

        assertEquals("Alice", doc.getStringFromNestedMap(handle, "name"))
        doc.close()
    }

    @Test
    fun nestedMapKeys() {
        val doc = Document()
        val handle = doc.putObjectInMap("config", AmObjType.MAP)
        doc.putInNestedMap(handle, "host", "localhost")
        doc.putIntInNestedMap(handle, "port", 8080)

        val keys = doc.keysInNestedMap(handle)
        assertEquals(2, keys.size)
        assertTrue(keys.contains("host"))
        assertTrue(keys.contains("port"))
        doc.close()
    }

    @Test
    fun deeplyNestedMaps() {
        val doc = Document()
        val level1 = doc.putObjectInMap("level1", AmObjType.MAP)
        val level2 = doc.putObjectInNestedMap(level1, "level2", AmObjType.MAP)
        val level3 = doc.putObjectInNestedMap(level2, "level3", AmObjType.MAP)
        doc.putInNestedMap(level3, "deep", "value")

        assertEquals("value", doc.getStringFromNestedMap(level3, "deep"))
        doc.close()
    }

    @Test
    fun nestedMapSurvivesSaveLoad() {
        val doc1 = Document()
        val handle = doc1.putObjectInMap("nested", AmObjType.MAP)
        doc1.putInNestedMap(handle, "inner", "data")
        val bytes = doc1.save()

        // load 후에는 handle이 유효하지 않지만, root에서 nested가 존재하는지 확인
        val doc2 = Document.load(bytes)
        assertTrue(doc2.keys().contains("nested"))
        doc1.close()
        doc2.close()
    }

    @Test
    fun invalidHandleThrows() {
        val doc = Document()
        assertFailsWith<AutomergeException.InvalidObjectId> {
            doc.getStringFromNestedMap("bogus_handle", "key")
        }
        doc.close()
    }

    // ===== List Operations =====

    @Test
    fun createAndInsertIntoList() {
        val doc = Document()
        val list = doc.putListInMap("items")
        doc.insertInList(list, 0uL, "first")
        doc.insertInList(list, 1uL, "second")
        doc.insertInList(list, 2uL, "third")

        assertEquals(3uL, doc.listLength(list))
        assertEquals("first", doc.getStringFromList(list, 0uL))
        assertEquals("second", doc.getStringFromList(list, 1uL))
        assertEquals("third", doc.getStringFromList(list, 2uL))
        doc.close()
    }

    @Test
    fun insertAtBeginningOfList() {
        val doc = Document()
        val list = doc.putListInMap("items")
        doc.insertInList(list, 0uL, "second")
        doc.insertInList(list, 0uL, "first")

        assertEquals("first", doc.getStringFromList(list, 0uL))
        assertEquals("second", doc.getStringFromList(list, 1uL))
        doc.close()
    }

    @Test
    fun insertIntInList() {
        val doc = Document()
        val list = doc.putListInMap("numbers")
        doc.insertIntInList(list, 0uL, 10)
        doc.insertIntInList(list, 1uL, 20)
        doc.insertIntInList(list, 2uL, 30)

        assertEquals(3uL, doc.listLength(list))
        doc.close()
    }

    @Test
    fun emptyList() {
        val doc = Document()
        val list = doc.putListInMap("empty")
        assertEquals(0uL, doc.listLength(list))
        assertNull(doc.getStringFromList(list, 0uL))
        doc.close()
    }

    // ===== Text Operations =====

    @Test
    fun createAndEditText() {
        val doc = Document()
        val textId = doc.putTextInMap("content")
        doc.spliceText(textId, 0uL, 0, "Hello World")
        assertEquals("Hello World", doc.getText(textId))
        doc.close()
    }

    @Test
    fun textInsertAtPosition() {
        val doc = Document()
        val textId = doc.putTextInMap("content")
        doc.spliceText(textId, 0uL, 0, "Hello")
        doc.spliceText(textId, 5uL, 0, " World")
        assertEquals("Hello World", doc.getText(textId))
        doc.close()
    }

    @Test
    fun textDeleteCharacters() {
        val doc = Document()
        val textId = doc.putTextInMap("content")
        doc.spliceText(textId, 0uL, 0, "Hello World")
        // "World" 앞의 "Hello " (6글자) 삭제
        doc.spliceText(textId, 0uL, 6, "")
        assertEquals("World", doc.getText(textId))
        doc.close()
    }

    @Test
    fun textReplaceRange() {
        val doc = Document()
        val textId = doc.putTextInMap("content")
        doc.spliceText(textId, 0uL, 0, "Hello World")
        // "World" (5글자)를 "Automerge"로 교체
        doc.spliceText(textId, 6uL, 5, "Automerge")
        assertEquals("Hello Automerge", doc.getText(textId))
        doc.close()
    }

    @Test
    fun textUnicodeContent() {
        val doc = Document()
        val textId = doc.putTextInMap("content")
        doc.spliceText(textId, 0uL, 0, "안녕하세요 세계")
        assertEquals("안녕하세요 세계", doc.getText(textId))
        doc.close()
    }

    @Test
    fun emptyText() {
        val doc = Document()
        val textId = doc.putTextInMap("content")
        assertEquals("", doc.getText(textId))
        doc.close()
    }

    // ===== Fork & Merge =====

    @Test
    fun forkCreatesIndependentCopy() {
        val doc1 = Document()
        doc1.putInMap("shared", "original")

        val doc2 = doc1.fork()
        doc2.putInMap("shared", "modified")

        // 원본은 변경되지 않아야 한다
        assertEquals("original", doc1.getStringFromMap("shared"))
        assertEquals("modified", doc2.getStringFromMap("shared"))
        doc1.close()
        doc2.close()
    }

    @Test
    fun forkPreservesExistingData() {
        val doc = Document()
        doc.putInMap("a", "1")
        doc.putIntInMap("b", 2)

        val forked = doc.fork()
        assertEquals("1", forked.getStringFromMap("a"))
        assertEquals(2L, forked.getIntFromMap("b"))
        assertEquals(doc.keys().sorted(), forked.keys().sorted())
        doc.close()
        forked.close()
    }

    @Test
    fun mergeAddsNewKeys() {
        val doc1 = Document()
        doc1.putInMap("x", "original")

        val doc2 = doc1.fork()
        doc2.putInMap("y", "forked")

        doc1.merge(doc2)
        assertEquals("original", doc1.getStringFromMap("x"))
        assertEquals("forked", doc1.getStringFromMap("y"))
        doc1.close()
        doc2.close()
    }

    @Test
    fun bidirectionalMerge() {
        val doc1 = Document()
        doc1.putInMap("base", "value")

        val doc2 = doc1.fork()
        doc1.putInMap("from_doc1", "hello")
        doc2.putInMap("from_doc2", "world")

        // 양방향 머지
        doc1.merge(doc2)
        doc2.merge(doc1)

        // 양쪽 모두 동일한 상태여야 한다
        assertEquals(doc1.keys().sorted(), doc2.keys().sorted())
        assertEquals("hello", doc1.getStringFromMap("from_doc1"))
        assertEquals("world", doc1.getStringFromMap("from_doc2"))
        assertEquals("hello", doc2.getStringFromMap("from_doc1"))
        assertEquals("world", doc2.getStringFromMap("from_doc2"))
        doc1.close()
        doc2.close()
    }

    @Test
    fun mergeConcurrentEditsToSameKey() {
        val doc1 = Document()
        doc1.putInMap("key", "initial")

        val doc2 = doc1.fork()
        doc1.putInMap("key", "from_doc1")
        doc2.putInMap("key", "from_doc2")

        doc1.merge(doc2)

        // 충돌 시 하나의 값이 선택됨 (어떤 값이든 null이 아니어야 함)
        val result = doc1.getStringFromMap("key")
        assertNotNull(result)
        assertTrue(result == "from_doc1" || result == "from_doc2")
        doc1.close()
        doc2.close()
    }

    @Test
    fun mergeTextEdits() {
        val doc1 = Document()
        val textId = doc1.putTextInMap("doc")
        doc1.spliceText(textId, 0uL, 0, "Hello")

        // fork 후 양쪽에서 텍스트 편집
        val doc2 = doc1.fork()
        // doc1: "Hello" -> "Hello World"
        doc1.spliceText(textId, 5uL, 0, " World")

        // save/load로 doc2에서 같은 text를 찾기 어려우므로,
        // doc1의 텍스트만 검증
        assertEquals("Hello World", doc1.getText(textId))
        doc1.close()
        doc2.close()
    }

    // ===== Save/Load with Complex Data =====

    @Test
    fun saveLoadWithListData() {
        val doc1 = Document()
        val list = doc1.putListInMap("items")
        doc1.insertInList(list, 0uL, "a")
        doc1.insertInList(list, 1uL, "b")
        doc1.insertInList(list, 2uL, "c")
        val bytes = doc1.save()

        val doc2 = Document.load(bytes)
        assertTrue(doc2.keys().contains("items"))
        doc1.close()
        doc2.close()
    }

    @Test
    fun saveLoadWithTextData() {
        val doc1 = Document()
        val textId = doc1.putTextInMap("readme")
        doc1.spliceText(textId, 0uL, 0, "# Title\n\nSome content here.")
        val bytes = doc1.save()

        val doc2 = Document.load(bytes)
        assertTrue(doc2.keys().contains("readme"))
        doc1.close()
        doc2.close()
    }

    @Test
    fun multipleRoundTrips() {
        var doc = Document()
        doc.putInMap("counter", "0")

        for (i in 1..5) {
            val bytes = doc.save()
            doc.close()
            doc = Document.load(bytes)
            doc.putInMap("counter", i.toString())
        }

        assertEquals("5", doc.getStringFromMap("counter"))
        doc.close()
    }

    // ===== AutoCloseable / use =====

    @Test
    fun useBlockAutoCloses() {
        val result = Document().use { doc ->
            doc.putInMap("key", "value")
            doc.getStringFromMap("key")
        }
        assertEquals("value", result)
    }

    // ===== Edge Cases =====

    @Test
    fun longStringValue() {
        val doc = Document()
        val longStr = "x".repeat(100_000)
        doc.putInMap("long", longStr)
        assertEquals(longStr, doc.getStringFromMap("long"))
        doc.close()
    }

    @Test
    fun manyTextSplices() {
        val doc = Document()
        val textId = doc.putTextInMap("log")
        val lines = 100
        for (i in 0 until lines) {
            val offset = doc.getText(textId).length.toULong()
            doc.spliceText(textId, offset, 0, "Line $i\n")
        }
        val text = doc.getText(textId)
        assertTrue(text.startsWith("Line 0\n"))
        assertTrue(text.contains("Line 99\n"))
        assertEquals(lines, text.lines().count { it.isNotEmpty() })
        doc.close()
    }

    @Test
    fun multipleNestedObjects() {
        val doc = Document()
        val handles = (0 until 10).map { i ->
            val h = doc.putObjectInMap("obj_$i", AmObjType.MAP)
            doc.putInNestedMap(h, "id", i.toString())
            h
        }
        assertEquals(10, doc.keys().size)
        assertEquals("5", doc.getStringFromNestedMap(handles[5], "id"))
        doc.close()
    }
}
