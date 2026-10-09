package dev.housepoints.app.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Uuids
import dev.housepoints.ledger.FamilyState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZoneId

/** Result of asking for something to be recorded. */
sealed interface Outcome {
    /** [entries] are the ledger entries just written, for undo. */
    data class Recorded(val entries: List<EntryRecorded>) : Outcome
    data class Refused(val reason: Refusal) : Outcome
    data object NoFamily : Outcome
}

/** Family-wide state and commands, shared by every screen of the parent's app. */
class FamilyViewModel(
    private val repository: FamilyRepository,
    private val keys: FamilyKeys,
    private val clock: Clock,
) : ViewModel() {
    val snapshot: StateFlow<FamilySnapshot> = repository.snapshot

    fun now(): InstantMs = clock.now()

    fun perform(action: Action, onOutcome: (Outcome) -> Unit = {}) {
        viewModelScope.launch { onOutcome(execute(action)) }
    }

    suspend fun execute(action: Action): Outcome = when (action) {
        is Action.Refused -> Outcome.Refused(action.reason)
        is Action.Record -> when (val result = repository.record(action.payloads)) {
            RecordResult.NoFamily -> Outcome.NoFamily
            is RecordResult.Recorded -> Outcome.Recorded(action.payloads.filterIsInstance<EntryRecorded>())
        }
    }

    /** SPEC FR-1, FR-3: a new family id and key; the key goes to the vault before any op is written. */
    fun createFamily(name: String, currency: CurrencyCode, zone: ZoneId, weekStart: DayOfWeek, phoneName: String) {
        viewModelScope.launch {
            val family = FamilyId(Uuids.random())
            keys.create(family)
            repository.createFamily(family, FamilyActions.createFamily(name, currency, zone, weekStart, repository.self, phoneName))
        }
    }

    fun state(): FamilyState? = (snapshot.value as? FamilySnapshot.Ready)?.state
}

/** Custody of the family key (SPEC FR-3), implemented by the platform vault. */
interface FamilyKeys {
    suspend fun create(family: FamilyId)
}
