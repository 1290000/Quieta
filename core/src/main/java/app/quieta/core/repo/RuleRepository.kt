package app.quieta.core.repo

import android.content.Context
import app.quieta.core.model.Rule
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Local JSON rule store (no Room in MVP). File lives in filesDir.
 *
 * Process-wide singleton — multiple ViewModels and the notification listener
 * MUST share one instance or toggles will not propagate across pages.
 */
class RuleRepository private constructor(context: Context) {

    private val file by lazy { File(context.applicationContext.filesDir, "rules.json") }
    private val storage = AsyncLocalState(emptyList(), ::loadFromDisk, write = {
        file.writeText(RuleJson.encode(it))
    })

    val rules: StateFlow<List<Rule>> = storage.state

    suspend fun current(): List<Rule> = storage.current()

    suspend fun replaceAll(rules: List<Rule>) = update { rules }

    suspend fun update(transform: (List<Rule>) -> List<Rule>) = storage.update(transform)

    suspend fun importFrom(raw: String): List<Rule> = withContext(Dispatchers.IO) {
        val rules = RuleJson.decode(raw)
        replaceAll(rules)
        rules
    }

    suspend fun exportTo(): String = withContext(Dispatchers.IO) {
        RuleJson.encode(current())
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
