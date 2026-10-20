package com.victorious.browser.fuzz

import android.content.Context
import java.io.File

/**
 * Loads bundled + user-imported [FuzzTemplate]s. Bundled templates live under
 * assets/fuzz/templates/ (a SMALL set of safe exposed-file / info-disclosure
 * checks); imported ones live in filesDir/templates. Parsing is delegated to
 * [TemplateParser].
 */
class TemplateLibrary(context: Context) {
    private val appContext = context.applicationContext
    private val dir: File = File(appContext.filesDir, "templates").apply { mkdirs() }
    private val assets = appContext.assets

    data class Loaded(val name: String, val bundled: Boolean, val template: FuzzTemplate?, val error: String?)

    fun bundledNames(): List<String> =
        runCatching { assets.list(ASSET_DIR)?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.endsWith(".yaml") || it.endsWith(".yml") }
            .sorted()

    fun importedFiles(): List<File> =
        dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()

    fun readRaw(name: String, bundled: Boolean): String =
        if (bundled) {
            assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
        } else {
            File(dir, name).readText()
        }

    fun loadAll(): List<Loaded> {
        val out = ArrayList<Loaded>()
        for (n in bundledNames()) out.add(loadOne(n, true))
        for (f in importedFiles()) out.add(loadOne(f.name, false))
        return out
    }

    private fun loadOne(name: String, bundled: Boolean): Loaded =
        try {
            val tpl = TemplateParser.parse(readRaw(name, bundled))
            Loaded(name, bundled, tpl, null)
        } catch (e: Exception) {
            Loaded(name, bundled, null, e.message ?: "parse error")
        }

    /** Valid, parsed templates only. */
    fun validTemplates(): List<FuzzTemplate> = loadAll().mapNotNull { it.template }

    fun importText(name: String, content: String): String {
        val stem = WordlistParser.safeName(name)
        val fileName = if (stem.endsWith(".yaml") || stem.endsWith(".yml")) stem else "$stem.yaml"
        File(dir, fileName).writeText(content)
        return fileName
    }

    fun delete(name: String): Boolean {
        val f = File(dir, name)
        return if (f.exists() && f.isFile) f.delete() else false
    }

    companion object {
        private const val ASSET_DIR = "fuzz/templates"
    }
}
