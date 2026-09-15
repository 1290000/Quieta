package app.quieta.core.repo

import android.content.Context
import app.quieta.core.model.Rule
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Local JSON rule store (no Room in MVP). File lives in filesDir.
 *
 * Process-wide singleton — multiple ViewModels and the notification listener
 * MUST share one instance or toggles will not propagate across pages.
 */
class RuleRepository private constructor(context: Context) {

    private val file = File(context.applicationContext.filesDir, "rules.json")
    private val _rules = MutableStateFlow(loadFromDisk())

    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    suspend fun replaceAll(rules: List<Rule>) = withContext(Dispatchers.IO) {
        _rules.value = rules
        file.writeText(RuleJson.encode(rules))
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
