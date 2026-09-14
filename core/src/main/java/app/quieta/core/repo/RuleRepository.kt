package app.quieta.core.repo

import android.content.Context
import app.quieta.core.model.Rule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local JSON rule store (no Room in MVP). File lives in filesDir.
 */
class RuleRepository(context: Context) {

    private val file = File(context.filesDir, "rules.json")
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
        if (!file.exists()) return emptyList()
        return runCatching { RuleJson.decode(file.readText()) }.getOrDefault(emptyList())
    }
}
