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

    /** Decode without writing; UI previews merge/replace before commit. */
    suspend fun decodeRules(raw: String): List<Rule> = withContext(Dispatchers.IO) {
        RuleJson.decode(raw)
    }

    /**
     * MERGE: append ids not already present (default recommended path).
     * REPLACE: wipe local rules and load the incoming list only.
     */
    suspend fun importFrom(raw: String, mode: RuleImportMode = RuleImportMode.REPLACE): List<Rule> =
        withContext(Dispatchers.IO) {
            val incoming = RuleJson.decode(raw)
            when (mode) {
                RuleImportMode.REPLACE -> {
                    replaceAll(incoming)
                    incoming
                }
                RuleImportMode.MERGE -> {
                    val before = current()
                    val merged = RuleImportPlanner.merge(before, incoming)
                    replaceAll(merged)
                    // Newly appended rules (present after merge, not before).
                    val existing = before.map { it.id }.toSet()
                    merged.filterNot { it.id in existing }
                }
            }
        }

    suspend fun importMerge(incoming: List<Rule>): Int {
        val before = current()
        val merged = RuleImportPlanner.merge(before, incoming)
        replaceAll(merged)
        return merged.size - before.size
    }

    suspend fun importReplace(incoming: List<Rule>) {
        replaceAll(incoming)
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
