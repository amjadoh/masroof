package com.baraa.masroof.lab.beta.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baraa.masroof.lab.beta.application.BankSelectionState
import com.baraa.masroof.lab.beta.application.BetaBankSelectionStore
import com.baraa.masroof.lab.beta.application.UserBankSelection
import com.baraa.masroof.lab.finance.value.BankId

/** Independent from beta_asset: disable/resume never deletes financial history or relationships. */
@Entity(tableName = "beta_bank_selection", primaryKeys = ["bankId"])
data class BetaBankSelectionEntity(val bankId: String, val state: String)
@Dao
interface BetaBankSelectionDao {
    @Query("SELECT * FROM beta_bank_selection ORDER BY bankId")
    suspend fun all(): List<BetaBankSelectionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(selection: BetaBankSelectionEntity)
}
internal class RoomBetaBankSelectionStore(private val database: BetaDatabase) : BetaBankSelectionStore {
    override suspend fun all() = database.bankSelectionDao().all().map {
        UserBankSelection(BankId(it.bankId), BankSelectionState.valueOf(it.state))
    }
    override suspend fun save(selection: UserBankSelection) = database.bankSelectionDao().upsert(
        BetaBankSelectionEntity(selection.bankId.value, selection.state.name))
}
