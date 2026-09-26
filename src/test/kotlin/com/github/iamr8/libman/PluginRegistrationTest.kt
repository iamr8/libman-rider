package com.github.iamr8.libman

import com.intellij.json.JsonFileType
import com.intellij.json.JsonLanguage
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.testFramework.ApplicationRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.ClassRule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads the plugin in the Rider test IDE (application only - Rider project services need a real
 * solution). The platform logs a bad plugin.xml registration with LOG.error, and the test logger
 * turns that into a test failure.
 */
class PluginRegistrationTest {

    companion object {
        init {
            // The test IDE caches Marketplace plugin ids in its sandbox and downloads the file in
            // the background. A run that ends during the download leaves a cut file, and the next
            // run then fails when it registers our actions. Start each run without that file.
            Files.deleteIfExists(Path.of(PathManager.getPluginTempPath(), "pluginsXMLIds.json"))
        }

        @JvmField
        @ClassRule
        val application = ApplicationRule()
    }

    @Test fun `libman json is a secondary JSON file type`() {
        val type = FileTypeManager.getInstance().getFileTypeByFileName("libman.json")
        assertSame(LibManJsonFileType, type)
        assertEquals("JSON", (type as LanguageFileType).language.id)
        // The <fileType> bean has no `language`, so the platform needs a secondary type.
        assertTrue(type.isSecondary)
        // JSON keeps its own primary file type.
        assertSame(JsonFileType.INSTANCE, JsonLanguage.INSTANCE.associatedFileType)
    }

    @Test fun `LibMan group is in the Solution Explorer context menu`() {
        val manager = ActionManager.getInstance()
        val menu = manager.getAction("SolutionExplorerPopupMenu") as? DefaultActionGroup
        assertNotNull("Rider's Solution Explorer menu is not registered", menu)
        val childIds = menu!!.getChildActionsOrStubs().map { manager.getId(it) }
        assertTrue(childIds.contains("com.github.iamr8.libman.LibmanGroup"))
    }
}
