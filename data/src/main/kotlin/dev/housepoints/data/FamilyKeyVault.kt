package dev.housepoints.data

import android.content.Context
import dev.housepoints.contracts.FamilyId
import dev.housepoints.sync.FamilyKey

/** Keeps the family key wrapped by an Android Keystore key, outside backups (SAD §3.4). */
public class FamilyKeyVault(context: Context) {
    public fun save(familyId: FamilyId, key: FamilyKey): Unit = TODO("red")
    public fun load(): Pair<FamilyId, FamilyKey>? = TODO("red")
    public fun clear(): Unit = TODO("red")
}
