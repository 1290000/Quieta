package app.quieta

import android.app.Instrumentation
import android.os.Bundle
import app.quieta.core.privilege.root.RootBackend
import kotlinx.coroutines.runBlocking

/** Explicit device test; only mutates a disposable channel owned by the test APK. */
class RootBackendInstrumentation : Instrumentation() {
    private var checkStartup = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        checkStartup = arguments?.getString("startup") == "true"
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            if (checkStartup) {
                checkRefreshState()
                result.putString("result", "PASS: refresh retains displayed status and blocks writes while probing")
                finish(RESULT_OK, result)
                return
            }
            runBlocking {
                val backend = RootBackend(targetContext)
                val capability = backend.probeCapabilities()
                result.putString("identity", capability.identity.toString())
                check(capability.readable && capability.writeSupported) {
                    "Root not ready: $capability"
                }
                check(context.applicationInfo.uid != targetContext.applicationInfo.uid)
                val id = "quieta.root.test.${System.nanoTime()}"
                fixture(backend, id, delete = false)
                try {
                    for (importance in listOf(2, 0, 3)) {
                        backend.setImportance(context.packageName, id, importance)
                        val channel = backend.listChannels(context.packageName).single { it.id == id }
                        check(channel.importance.ordinal == importance) { "Read-back mismatch: $channel" }
                    }
                    result.putString("result", "PASS: root read/write/read-back/restore")
                } finally {
                    fixture(backend, id, delete = true)
                }
            }
            finish(RESULT_OK, result)
        } catch (error: Exception) {
            result.putString("error", error.stackTraceToString())
            finish(RESULT_CANCELED, result)
        }
    }

    private fun checkRefreshState() {
        val store = androidx.lifecycle.ViewModelStore()
        lateinit var model: app.quieta.feature.home.HomeViewModel
        runOnMainSync {
            model = app.quieta.feature.home.HomeViewModel(targetContext.applicationContext as android.app.Application)
            store.put("home", model)
        }
        try {
            runBlocking {
                kotlinx.coroutines.withTimeout(30_000) {
                    while (model.state.value.checkingPrivilege) kotlinx.coroutines.delay(25)
                }
                val before = model.state.value
                check(before.gate == app.quieta.feature.home.PrivilegeGate.READY)
                runOnMainSync {
                    model.refresh()
                    val during = model.state.value
                    check(during.checkingPrivilege)
                    check(during.privilege == before.privilege && during.gate == before.gate)
                    model.applyBatchMute()
                    check(model.state.value.muteResult == null)
                }
                kotlinx.coroutines.withTimeout(30_000) {
                    while (model.state.value.checkingPrivilege) kotlinx.coroutines.delay(25)
                }
                check(model.state.value.privilege == before.privilege)
            }
        } finally {
            runOnMainSync { store.clear() }
        }
    }

    private suspend fun fixture(backend: RootBackend, id: String, delete: Boolean) {
        uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/app.quieta.TestChannelActivity --es channel $id --ez delete $delete").use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
        kotlinx.coroutines.withTimeout(15_000) {
            while (backend.listChannels(context.packageName).any { it.id == id } == delete) {
                kotlinx.coroutines.delay(250)
            }
        }
    }

    private companion object {
        const val RESULT_OK = -1
        const val RESULT_CANCELED = 0
    }
}
