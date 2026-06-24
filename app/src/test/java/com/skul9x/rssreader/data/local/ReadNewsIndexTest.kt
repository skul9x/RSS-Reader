package com.skul9x.rssreader.data.local

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException

/**
 * Verify that ReadNewsItem entity has the required @Index on readAt.
 */
class ReadNewsIndexTest {

    private fun findProjectFile(relativePath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val fileInDir = File(dir, relativePath)
            if (fileInDir.exists()) {
                return fileInDir
            }
            val appFileInDir = File(dir, "app/$relativePath")
            if (appFileInDir.exists()) {
                return appFileInDir
            }
            dir = dir.parentFile
        }
        throw FileNotFoundException("Could not find file: $relativePath")
    }

    @Test
    fun `ReadNewsItem entity has index on readAt column`() {
        val file = findProjectFile("src/main/java/com/skul9x/rssreader/data/model/ReadNewsItem.kt")
        val content = file.readText()

        val entityRegex = """@Entity\s*\(([^)]+)\)""".toRegex()
        val matchResult = entityRegex.find(content)
        assertNotNull("ReadNewsItem must have @Entity annotation with parameters", matchResult)

        val entityParams = matchResult!!.groupValues[1]
        assertTrue(
            "ReadNewsItem @Entity must define indices containing 'readAt'",
            entityParams.contains("indices") && entityParams.contains("readAt")
        )
    }

    @Test
    fun `AppDatabase version is bumped to 9`() {
        val file = findProjectFile("src/main/java/com/skul9x/rssreader/data/local/AppDatabase.kt")
        val content = file.readText()

        val databaseRegex = """@Database\s*\(([^)]+)\)""".toRegex()
        val matchResult = databaseRegex.find(content)
        assertNotNull("AppDatabase must have @Database annotation with parameters", matchResult)

        val dbParams = matchResult!!.groupValues[1]
        val versionRegex = """version\s*=\s*(\d+)""".toRegex()
        val versionMatch = versionRegex.find(dbParams)
        assertNotNull("AppDatabase @Database must specify version", versionMatch)

        val version = versionMatch!!.groupValues[1].toInt()
        assertTrue("AppDatabase version must be >= 9, found $version", version >= 9)
    }
}
