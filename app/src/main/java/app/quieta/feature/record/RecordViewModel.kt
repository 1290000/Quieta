package app.quieta.feature.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.engine.MuteLogStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordViewModel(application: Application) : AndroidViewModel(application) {

    private val store = MuteLogStore(application)

    val items: StateFlow<List<RecordItem>> = store.entries
        .map { list ->
            list.map {
                RecordItem(
                    title = it.label,
                    subtitle = it.detail,
                    time = it.time,
                    tag = it.tag,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearAll() {
        viewModelScope.launch { store.clear() }
    }
}
