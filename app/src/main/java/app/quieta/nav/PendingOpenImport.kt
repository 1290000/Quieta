package app.quieta.nav

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One-shot open-with payload from ACTION_VIEW (channel snapshot / rules JSON). */
object PendingOpenImport {
    data class Payload(val uri: Uri)

    private val _pending = MutableStateFlow<Payload?>(null)
    val pending: StateFlow<Payload?> = _pending.asStateFlow()

    fun offer(uri: Uri) {
        _pending.value = Payload(uri)
    }

    fun consume() {
        _pending.value = null
    }
}
