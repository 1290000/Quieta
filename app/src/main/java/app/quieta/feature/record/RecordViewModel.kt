package app.quieta.feature.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.quieta.core.engine.MuteLogStore
import app.quieta.core.engine.NotificationTimelineStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordViewModel(application: Application) : AndroidViewModel(application) {

    private val store = MuteLogStore.getInstance(application)
    private val timelineStore = NotificationTimelineStore.getInstance(application)

    val items: StateFlow<List<RecordItem>> = store.entries
        .map { list ->
            list.map {
                RecordItem(
                    id = it.id,
                    title = it.label,
                    subtitle = it.detail,
                    time = it.time,
                    tag = it.tag,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val timeline: StateFlow<List<TimelineItem>> = timelineStore.entries
        .map { entries ->
            entries.map {
                TimelineItem(
                    id = it.id,
                    packageName = it.packageName,
                    channelId = it.channelId,
                    time = formatTime(it.timestamp),
                    count = it.count,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearAll() {
        viewModelScope.launch {
            store.clear()
            timelineStore.clear()
        }
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(timestamp))
}

data class TimelineItem(
    val id: String,
    val packageName: String,
    val channelId: String,
    val time: String,
    val count: Int,
)
