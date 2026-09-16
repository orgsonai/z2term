package com.zerotoship.z2term.edge

import android.content.Context
import com.zerotoship.z2term.R
import com.zerotoship.z2term.proot.z2TranslationMacro
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.widget.WidgetStore
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

/** Adds only our editable wrapper and ordinary form items. Never downloads a translator. */
internal object EdgeTranslationTemplate {
    fun add(context: Context, store: EdgeStore, panel: String) {
        require(store.panel(panel).items.size <= 59) { "At most 64 items" }
        installWrapper(context)
        store.addTranslationForm(panel, listOf(R.string.edge_translation_text, R.string.edge_translation_run,
            R.string.edge_type_result, R.string.edge_translation_target, R.string.edge_translation_source)
            .map { context.getString(it) })
    }

    fun installWrapper(context: Context) {
        val dir = WidgetStore.macroDir(context).apply { mkdirs() }
        val target = File(dir, "translate.sh")
        if (target.exists()) return // User-owned macros are never silently updated.
        val temp = File.createTempFile(".translate-", ".tmp", dir)
        try {
            temp.writeText(z2TranslationMacro(LocaleHelper.language(context)))
            try { Files.move(temp.toPath(), target.toPath()) }
            catch (_: FileAlreadyExistsException) { /* Another editor won; keep its file. */ }
        } finally { temp.delete() }
    }
}
