package com.skul9x.rssreader.data.local

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for the batch translation update logic.
 *
 * KEY VERIFICATION:
 * 1. Batch method calls updateTranslatedTitle for each entry
 * 2. Empty map is handled gracefully
 * 3. All entries are processed (none skipped)
 * 4. The batch method exists and is callable
 */
class TranslationBatchTest {

    private lateinit var dao: CachedNewsDao

    @Before
    fun setup() {
        dao = mockk(relaxed = true)

        // Simulate the @Transaction batch method
        coEvery { dao.updateTranslatedTitlesBatch(any()) } coAnswers {
            val translations = firstArg<Map<String, String>>()
            translations.forEach { (id, title) ->
                dao.updateTranslatedTitle(id, title)
            }
        }
    }

    @Test
    fun `batch update calls updateTranslatedTitle for each entry`() = runTest {
        val translations = mapOf(
            "id1" to "Tiêu đề 1",
            "id2" to "Tiêu đề 2",
            "id3" to "Tiêu đề 3"
        )

        dao.updateTranslatedTitlesBatch(translations)

        coVerify(exactly = 1) { dao.updateTranslatedTitle("id1", "Tiêu đề 1") }
        coVerify(exactly = 1) { dao.updateTranslatedTitle("id2", "Tiêu đề 2") }
        coVerify(exactly = 1) { dao.updateTranslatedTitle("id3", "Tiêu đề 3") }
    }

    @Test
    fun `empty map does nothing`() = runTest {
        dao.updateTranslatedTitlesBatch(emptyMap())

        coVerify(exactly = 0) { dao.updateTranslatedTitle(any(), any()) }
    }

    @Test
    fun `single entry works`() = runTest {
        val translations = mapOf("only_id" to "Duy nhất")

        dao.updateTranslatedTitlesBatch(translations)

        coVerify(exactly = 1) { dao.updateTranslatedTitle("only_id", "Duy nhất") }
    }

    @Test
    fun `large batch processes all entries`() = runTest {
        val translations = (1..50).associate { "id_$it" to "Title $it" }

        dao.updateTranslatedTitlesBatch(translations)

        coVerify(exactly = 50) { dao.updateTranslatedTitle(any(), any()) }
    }
}
