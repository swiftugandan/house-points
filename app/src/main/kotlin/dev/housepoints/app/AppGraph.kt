package dev.housepoints.app

import android.content.Context
import dev.housepoints.app.family.Clock
import dev.housepoints.app.family.FamilyKeys
import dev.housepoints.app.family.FamilyRepository
import dev.housepoints.app.family.FamilySnapshot
import dev.housepoints.app.family.FamilyViewModel
import dev.housepoints.app.platform.PaydayReminder
import dev.housepoints.app.sync.LinkFactory
import dev.housepoints.app.sync.NearbyPeerLink
import dev.housepoints.app.sync.SyncController
import dev.housepoints.app.widget.BalancesWidget
import androidx.glance.appwidget.updateAll
import dev.housepoints.lan.LanLink
import dev.housepoints.nearby.NearbyLink
import dev.housepoints.contracts.FamilyId
import dev.housepoints.data.DeviceIdentityStore
import dev.housepoints.data.FamilyKeyVault
import dev.housepoints.data.SqliteOpLog
import dev.housepoints.data.SyncHistoryEntry
import dev.housepoints.data.SyncHistoryStore
import dev.housepoints.sync.FamilyKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The family key held by this phone, once loaded from the vault. */
sealed interface KeyState {
    data object Loading : KeyState
    data object None : KeyState
    data class Held(val family: FamilyId, val key: FamilyKey) : KeyState
}

/**
 * The whole object graph, built once per process (SAD ADR-3: constructor injection, no framework).
 */
class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: Clock = Clock.SYSTEM
    val deviceId = DeviceIdentityStore(appContext).deviceId()
    val opLog = SqliteOpLog(appContext)
    val vault = FamilyKeyVault(appContext)
    val prefs = AppPrefs(appContext)
    val repository = FamilyRepository(opLog, deviceId, clock, Dispatchers.Default).also { it.start(scope) }

    private val historyStore = SyncHistoryStore(appContext)
    private val _history = MutableStateFlow<List<SyncHistoryEntry>>(emptyList())
    val history: StateFlow<List<SyncHistoryEntry>> = _history.asStateFlow()

    private val _key = MutableStateFlow<KeyState>(KeyState.Loading)
    val key: StateFlow<KeyState> = _key.asStateFlow()

    val sync = SyncController(scope, opLog, repository, historyStore, deviceId, clock) { refreshHistory() }

    /** Same Wi-Fi: the default, used automatically while the app is open (SAD ADR-9). */
    val localNetwork = LinkFactory { family, name -> LanLink(appContext, family, deviceId, name) }

    /** Bluetooth and Wi-Fi Direct through Nearby Connections, for when the phones don't share a network. */
    val bluetooth = LinkFactory { family, name -> NearbyPeerLink(NearbyLink(appContext, family, deviceId, name)) }

    private val foreground = MutableStateFlow(false)

    val familyKeys = object : FamilyKeys {
        override suspend fun create(family: FamilyId) = store(family, FamilyKey.generate())
    }

    init {
        scope.launch {
            _key.value = withContext(Dispatchers.IO) { vault.load() }?.let { (family, key) -> KeyState.Held(family, key) } ?: KeyState.None
            refreshHistory()
        }
        scope.launch {
            // Restart when this phone's name arrives, so other phones see it by name rather than "A phone".
            val ownName = repository.snapshot.map { phoneName() }.distinctUntilChanged()
            combine(foreground, key, ownName) { visible, held, _ -> if (visible) held as? KeyState.Held else null }
                .collect { held -> if (held != null) startAutomatic(held) else sync.stop() }
        }
        scope.launch {
            // SPEC FR-44: refresh the widget whenever a displayed balance or name changes.
            repository.snapshot
                .map { snapshot -> (snapshot as? FamilySnapshot.Ready)?.state?.let { s -> s.children.map { it.name to s.account(it.id)?.displayed } } }
                .distinctUntilChanged()
                .collect { BalancesWidget().updateAll(appContext) }
        }
        scope.launch {
            repository.snapshot
                .map { (it as? FamilySnapshot.Ready)?.state?.accounts?.values?.firstOrNull()?.current?.end }
                .distinctUntilChanged()
                .collect { payday -> if (payday != null) PaydayReminder.schedule(appContext, payday) }
        }
    }

    /** Called by the activity: automatic sync runs only while House Points is on screen. */
    fun setForeground(visible: Boolean) {
        foreground.value = visible
    }

    /** Back to automatic sync after a manual sync on the Sync screen. */
    fun resumeAutomatic() {
        (key.value as? KeyState.Held)?.takeIf { foreground.value }?.let(::startAutomatic)
    }

    fun phoneName(): String {
        val state = (repository.snapshot.value as? FamilySnapshot.Ready)?.state
        return state?.devices?.firstOrNull { it.id == deviceId }?.name?.ifBlank { null } ?: prefs.pendingPhoneName ?: "A phone"
    }

    private fun startAutomatic(held: KeyState.Held) = sync.startAutomatic(localNetwork, held.family, held.key, phoneName())

    /** Saves a family key (new family, joining, or after removing a lost phone). */
    suspend fun store(family: FamilyId, key: FamilyKey) {
        withContext(Dispatchers.IO) { vault.save(family, key) }
        _key.value = KeyState.Held(family, key)
    }

    suspend fun refreshHistory() {
        _history.value = historyStore.entries()
    }

    fun familyViewModel(): FamilyViewModel = FamilyViewModel(repository, familyKeys, clock)
}

/** Small per-phone settings that are not family data and never sync. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("house_points", Context.MODE_PRIVATE)

    var pendingPhoneName: String?
        get() = prefs.getString(KEY_PENDING_NAME, null)
        set(value) = prefs.edit().putString(KEY_PENDING_NAME, value).apply()

    /** Set once the parent has finished adding children during setup (or joined an existing family). */
    var childrenStepDone: Boolean
        get() = prefs.getBoolean(KEY_CHILDREN_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_CHILDREN_DONE, value).apply()

    var starterJobsOffered: Boolean
        get() = prefs.getBoolean(KEY_STARTER, false)
        set(value) = prefs.edit().putBoolean(KEY_STARTER, value).apply()

    private companion object {
        const val KEY_PENDING_NAME = "pending_phone_name"
        const val KEY_STARTER = "starter_jobs_offered"
        const val KEY_CHILDREN_DONE = "children_step_done"
    }
}
