package com.pdh.cardvault.data.room

import android.content.Context
import com.pdh.cardvault.domain.validation.BankCardValidator
import com.pdh.cardvault.domain.validation.AddressValidator
import com.pdh.cardvault.sync.android.AndroidVaultSyncCoordinator

internal class CardVaultPersistentGraph(
    context: Context,
    templateIdLookup: (String) -> Boolean,
) : AutoCloseable {
    private val database = CardVaultDatabase.create(context.applicationContext)
    val validator = BankCardValidator(templateIdLookup)
    val addressValidator = AddressValidator(templateIdLookup)

    val repository = EncryptedRoomBankCardRepository.create(
        database = database,
        validator = validator,
        addressValidator = addressValidator,
        context = context.applicationContext,
    )

    val syncCoordinator = AndroidVaultSyncCoordinator(
        cardDao = database.cardDao(),
        addressDao = database.addressDao(),
        folderDao = database.vaultFolderDao(),
        syncStateDao = database.syncStateDao(),
        repository = repository,
        validator = validator,
        addressValidator = addressValidator,
    )

    override fun close() {
        repository.lock()
        database.close()
    }
}
