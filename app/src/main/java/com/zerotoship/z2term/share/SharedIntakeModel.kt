package com.zerotoship.z2term.share

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerotoship.z2term.R
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.service.WhenManager
import com.zerotoship.z2term.snippets.Snippet
import com.zerotoship.z2term.snippets.SnippetStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A rotation does not cancel the import or fire its rules again. */
class SharedIntakeModel(application: Application) : AndroidViewModel(application) {
    data class Choice(val intake: SharedIntake.Intake, val actions: List<Snippet>)
    var pending by mutableStateOf<List<Choice>>(emptyList())
        private set
    private val imports = Mutex()

    fun accept(intent: Intent) {
        val snapshot = Intent(intent)
        val context = getApplication<Application>()
        viewModelScope.launch {
            imports.withLock {
                try {
                    val choice = withContext(Dispatchers.IO) {
                        val intake = requireNotNull(SharedIntake.intakeFrom(context, snapshot))
                        // Exactly once per receipt, regardless of the later manual choice.
                        runCatching { WhenManager.onShare(context, intake.kind, intake.text, intake.fileNames, intake.manifest, intake.body) }
                        Choice(intake, SnippetStore(context).snippets.first().filter { it.shareAction })
                    }
                    if (choice.actions.isEmpty()) insert(choice.intake.text)
                    else pending = pending + choice
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.toast_share_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun dismiss(choice: Choice) { pending = pending.filterNot { it.intake.manifest == choice.intake.manifest } }
    fun insert(text: String) {
        val ok = SessionManager.insertText(text)
        Toast.makeText(getApplication(), if (ok) R.string.toast_share_inserted else R.string.toast_share_failed, Toast.LENGTH_SHORT).show()
    }
}
