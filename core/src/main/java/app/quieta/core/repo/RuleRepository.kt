package app.quieta.core.repo

import android.content.Context
import app.quieta.core.model.Rule
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local JSON rule store (no Room in MVP). File lives in filesDir.
 *
 * Process-wide singleton — multiple ViewModels and the notification listener
 * MUST share one instance or toggles will not propagate across pages.
 */
class RuleRepository private constructor(context: Context) {

    private val file = File(context.applicationContext.filesDir, "rules.json")
    private val _rules = MutableStateFlow(loadFromDisk())
    private val writeMutex = Mutex()

    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    suspend fun replaceAll(rules: List<Rule>) = update { rules }

    suspend fun update(transform: (List<Rule>) -> List<Rule>) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val previous = _rules.value
            val next = transform(previous)
            _rules.value = next
            try {
                file.writeText(RuleJson.encode(next))
            } catch (error: Exception) {
                _rules.value = previous
                throw error
            }
        }
    }

    suspend fun importFrom(raw: String): List<Rule> = withContext(Dispatchers.IO) {
        val rules = RuleJson.decode(raw)
        replaceAll(rules)
        rules
    }

    suspend fun exportTo(): String = withContext(Dispatchers.IO) {
        RuleJson.encode(_rules.value)
    }

    private fun loadFromDisk(): List<Rule> {
        if (!file.exists()) {
            val starter = DefaultRules.starter()
            runCatching { file.writeText(RuleJson.encode(starter)) }
            return starter
        }
        return runCatching { RuleJson.decode(file.readText()) }.getOrDefault(emptyList())
    }

    companion object {
        @Volatile
        private var instance: RuleRepository? = null

        fun getInstance(context: Context): RuleRepository {
            return instance ?: synchronized(this) {
                instance ?: RuleRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
