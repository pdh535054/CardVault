package com.pdh.cardvault.data.room

import com.pdh.cardvault.domain.model.BankCardInput
import com.pdh.cardvault.domain.model.AddressInput
import com.pdh.cardvault.domain.model.CardNetwork
import com.pdh.cardvault.domain.validation.BankCardValidator
import com.pdh.cardvault.security.crypto.EncryptedDataAuthenticationException
import com.pdh.cardvault.security.crypto.KekManager
import com.pdh.cardvault.security.crypto.VaultKeyUnavailableException
import com.pdh.cardvault.sync.android.StoredVersionVectors
import com.pdh.cardvault.sync.android.AndroidVaultSyncCoordinator
import com.pdh.cardvault.sync.CardVaultSyncFiles
import com.pdh.cardvault.sync.PairingCode
import com.pdh.cardvault.sync.SecretBytes
import com.pdh.cardvault.ui.card.CardTemplateRegistry
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedRoomBankCardRepositoryTest {
    @Test
    fun encryptedCrudUsesOnlyCiphertextRowsAndContinuousFrontFirstOrder() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()

        val first = fixture.repository.add(validInput(nickname = "Synthetic alpha marker"))
        val second = fixture.repository.add(validInput(nickname = "Synthetic beta marker"))
        val rows = fixture.cardDao.getAll()

        assertEquals(listOf(second, first), fixture.repository.getList().map { it.id })
        assertEquals(listOf(0, 1), rows.map { it.sortOrder })
        assertTrue(rows.all { !StoredVersionVectors.decode(it.versionVector).isEmpty })
        assertFalse(rows.any { it.ciphertext.containsUtf8("Synthetic alpha marker") })
        assertFalse(rows.any { it.ciphertext.containsUtf8("Synthetic beta marker") })
        assertEquals("Synthetic alpha marker", fixture.repository.getMaskedDetail(first)?.nickname)
        assertNull(fixture.repository.getMaskedDetail(UUID.randomUUID()))

        assertTrue(fixture.repository.delete(second))
        assertFalse(fixture.repository.delete(second))
        assertEquals(
            listOf(second.toString()),
            fixture.cardDao.syncStateDao.tombstones.map(SyncTombstoneEntity::recordId),
        )
        assertEquals(listOf(first), fixture.repository.getList().map { it.id })
        assertEquals(listOf(0), fixture.cardDao.getAll().map { it.sortOrder })
    }

    @Test
    fun updatePreservesIdentityAndUsesFreshIvAndCiphertext() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        val original = fixture.repository.add(validInput(nickname = "Synthetic before marker"))
        val before = requireNotNull(fixture.cardDao.getById(original.toString()))

        assertTrue(
            fixture.repository.update(
                original,
                validInput(nickname = "Synthetic after marker", notes = "Edited synthetic note"),
            ),
        )
        val after = requireNotNull(fixture.cardDao.getById(original.toString()))

        assertEquals(before.id, after.id)
        assertEquals(before.createdAt, after.createdAt)
        assertEquals(before.sortOrder, after.sortOrder)
        assertTrue(after.updatedAt > before.updatedAt)
        val deviceId = requireNotNull(fixture.cardDao.syncStateDao.vaultState).deviceId
        assertTrue(
            StoredVersionVectors.decode(after.versionVector)[deviceId] >
                StoredVersionVectors.decode(before.versionVector)[deviceId],
        )
        assertNotEquals("A record edit must use a fresh IV", before.recordIv.toList(), after.recordIv.toList())
        assertNotEquals(before.ciphertext.toList(), after.ciphertext.toList())
        assertEquals("Synthetic after marker", fixture.repository.getMaskedDetail(original)?.nickname)
    }

    @Test
    fun addressUpdatePreservesMetadataAndReencryptsWithFreshIv() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        val id = fixture.repository.addAddress(validAddressInput(nickname = "Address before"))
        val before = requireNotNull(fixture.addressDao.getById(id.toString()))

        assertTrue(
            fixture.repository.updateAddress(
                id,
                validAddressInput(
                    nickname = "Address after",
                    detailedAddress = "Fictional updated road 9",
                    country = "Updated Country",
                ),
            ),
        )
        val after = requireNotNull(fixture.addressDao.getById(id.toString()))

        assertEquals(before.id, after.id)
        assertEquals(before.createdAt, after.createdAt)
        assertEquals(before.sortOrder, after.sortOrder)
        assertTrue(after.updatedAt > before.updatedAt)
        assertNotEquals(before.recordIv.toList(), after.recordIv.toList())
        assertNotEquals(before.ciphertext.toList(), after.ciphertext.toList())
        assertFalse(after.ciphertext.containsUtf8("Fictional updated road 9"))
        assertEquals("Address after", fixture.repository.getAddressDetail(id)?.nickname)
        assertEquals("Updated Country", fixture.repository.getAddressEditInput(id)?.country)
        assertFalse(fixture.repository.updateAddress(UUID.randomUUID(), validAddressInput()))
    }

    @Test
    fun disabledCvvIsRemovedBeforeEncryption() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        val discardedCvv = "8".repeat(4)

        val card = fixture.repository.add(
            validInput(saveCvv = false, cvv = discardedCvv),
        )
        val row = requireNotNull(fixture.cardDao.getById(card.toString()))

        assertFalse(requireNotNull(fixture.repository.getMaskedDetail(card)).cvvSaved)
        assertNull(fixture.repository.getSecretsForAuthenticatedUse(card)?.cvv)
        assertNull(fixture.repository.getEditInputForAuthenticatedUse(card)?.cvv)
        assertFalse(row.ciphertext.containsUtf8(discardedCvv))
    }

    @Test
    fun defaultProjectionsNeverContainFullCardNumberOrCvvFields() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        val fullNumber = "7".repeat(12)
        val cvv = "8".repeat(4)
        val id = fixture.repository.add(
            validInput(cardNumber = fullNumber, saveCvv = true, cvv = cvv),
        )

        val listItem = fixture.repository.getList().single()
        val detail = requireNotNull(fixture.repository.getMaskedDetail(id))
        val listFields = PersistentCardListItem::class.java.declaredFields
            .map { field -> field.name.lowercase() }
        val detailFields = PersistentCardDetail::class.java.declaredFields
            .map { field -> field.name.lowercase() }

        assertFalse(detail.maskedCardNumber.contains(fullNumber))
        assertFalse("cardnumber" in listFields)
        assertFalse("cvv" in listFields)
        assertFalse("expirymonth" in listFields)
        assertFalse("expiryyear" in listFields)
        assertFalse("expired" in listFields)
        assertFalse("cardnumber" in detailFields)
        assertFalse("cvv" in detailFields)
        assertFalse("expirymonth" in detailFields)
        assertFalse("expiryyear" in detailFields)
        assertFalse("expired" in detailFields)
        assertFalse(listItem.toString().contains(fullNumber))
        assertFalse(detail.toString().contains(cvv))
        val secrets = requireNotNull(fixture.repository.getSecretsForAuthenticatedUse(id))
        assertEquals(fullNumber, secrets.cardNumber)
        assertEquals(12, secrets.expiryMonth)
        assertEquals(2_099, secrets.expiryYear)
        assertEquals(cvv, secrets.cvv)
        assertFalse(secrets.toString().contains(fullNumber))
        assertFalse(secrets.toString().contains(cvv))
        assertNull(fixture.repository.getSecretsForAuthenticatedUse(UUID.randomUUID()))
    }

    @Test
    fun paymentNetworkIsDerivedAfterDecryptionAndIsNotPersistedAsAColumn() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        val syntheticVisaNumber = "4" + "0".repeat(15)

        val id = fixture.repository.add(validInput(cardNumber = syntheticVisaNumber))

        assertEquals(CardNetwork.Visa, fixture.repository.getList().single().cardNetwork)
        assertEquals(
            CardNetwork.Visa,
            requireNotNull(fixture.repository.getMaskedDetail(id)).cardNetwork,
        )
        val persistedFieldNames = CardEntity::class.java.declaredFields
            .map { field -> field.name.lowercase() }
        assertFalse(persistedFieldNames.any { field -> "network" in field || "bin" in field })
    }

    @Test
    fun reorderChangesNoRecordCountAndKeepsZeroBasedContinuousOrder() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        val first = fixture.repository.add(validInput(nickname = "Synthetic first"))
        val second = fixture.repository.add(validInput(nickname = "Synthetic second"))
        val third = fixture.repository.add(validInput(nickname = "Synthetic third"))
        val requested = listOf(first, third, second)
        val orderBefore = requireNotNull(fixture.cardDao.syncStateDao.orderState)

        fixture.repository.reorder(requested)

        val orderAfter = requireNotNull(fixture.cardDao.syncStateDao.orderState)
        val deviceId = requireNotNull(fixture.cardDao.syncStateDao.vaultState).deviceId
        assertEquals(requested, fixture.repository.getList().map { it.id })
        assertEquals(3, fixture.cardDao.count())
        assertEquals(listOf(0, 1, 2), fixture.cardDao.getAll().map { it.sortOrder })
        assertTrue(
            StoredVersionVectors.decode(orderAfter.versionVector)[deviceId] >
                StoredVersionVectors.decode(orderBefore.versionVector)[deviceId],
        )
    }

    @Test
    fun invalidReorderFailsBeforeMutatingAnyOrder() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        val first = fixture.repository.add(validInput(nickname = "Synthetic first"))
        val second = fixture.repository.add(validInput(nickname = "Synthetic second"))
        val before = fixture.repository.getList().map { it.id }

        val failure = runCatching {
            fixture.repository.reorder(listOf(first, UUID.randomUUID()))
        }.exceptionOrNull()

        assertTrue(failure is DatabaseInvariantException)
        assertEquals(before, fixture.repository.getList().map { it.id })
        assertEquals(listOf(0, 1), fixture.cardDao.getAll().map { it.sortOrder })
        assertEquals(setOf(first, second), fixture.repository.getList().map { it.id }.toSet())
    }

    @Test
    fun addressReorderIsAtomicContinuousAndAdvancesAddressOrderVersion() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        val first = fixture.repository.addAddress(validAddressInput(nickname = "Synthetic address first"))
        val second = fixture.repository.addAddress(validAddressInput(nickname = "Synthetic address second"))
        val third = fixture.repository.addAddress(validAddressInput(nickname = "Synthetic address third"))
        val requested = listOf(first, third, second)
        val orderBefore = requireNotNull(fixture.addressDao.syncStateDao.addressOrderState)

        fixture.repository.reorderAddresses(requested)

        val orderAfter = requireNotNull(fixture.addressDao.syncStateDao.addressOrderState)
        val deviceId = requireNotNull(fixture.addressDao.syncStateDao.vaultState).deviceId
        assertEquals(requested, fixture.repository.getAddressList().map { it.id })
        assertEquals(3, fixture.addressDao.count())
        assertEquals(listOf(0, 1, 2), fixture.addressDao.getAll().map { it.sortOrder })
        assertTrue(
            StoredVersionVectors.decode(orderAfter.versionVector)[deviceId] >
                StoredVersionVectors.decode(orderBefore.versionVector)[deviceId],
        )
    }

    @Test
    fun invalidAddressReorderFailsBeforeMutatingAnyOrder() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        val first = fixture.repository.addAddress(validAddressInput(nickname = "Synthetic address first"))
        val second = fixture.repository.addAddress(validAddressInput(nickname = "Synthetic address second"))
        val before = fixture.repository.getAddressList().map { it.id }

        val failure = runCatching {
            fixture.repository.reorderAddresses(listOf(first, UUID.randomUUID()))
        }.exceptionOrNull()

        assertTrue(failure is DatabaseInvariantException)
        assertEquals(before, fixture.repository.getAddressList().map { it.id })
        assertEquals(listOf(0, 1), fixture.addressDao.getAll().map { it.sortOrder })
        assertEquals(setOf(first, second), fixture.repository.getAddressList().map { it.id }.toSet())
    }

    @Test
    fun corruptedCiphertextFailsClosedWithoutReturningAPartialList() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        val intact = fixture.repository.add(validInput(nickname = "Synthetic intact marker"))
        val corrupted = fixture.repository.add(validInput(nickname = "Synthetic corrupt marker"))
        fixture.cardDao.corruptCiphertext(corrupted.toString())

        val failure = runCatching { fixture.repository.getList() }.exceptionOrNull()

        assertTrue(failure is EncryptedDataAuthenticationException)
        val diagnostic = failure.toString()
        assertFalse(diagnostic.contains("Synthetic intact marker"))
        assertFalse(diagnostic.contains("Synthetic corrupt marker"))
        assertFalse(diagnostic.contains(intact.toString()))
        assertFalse(diagnostic.contains(corrupted.toString()))
    }

    @Test
    fun reopeningExistingVaultUsesExistingKekAndNeverCreatesAReplacement() = runBlocking {
        val cardDao = FakeCardDao()
        val metadataDao = FakeVaultMetadataDao()
        val sharedKey = randomTestKey()
        val firstManager = FakeKekManager(sharedKey)
        val first = repository(cardDao, metadataDao, firstManager)
        first.unlockOrCreateVault()
        first.add(validInput())
        first.lock()

        val reopeningManager = FakeKekManager(sharedKey)
        val reopened = repository(cardDao, metadataDao, reopeningManager)
        reopened.unlockOrCreateVault()

        assertEquals(0, reopeningManager.createCalls)
        assertEquals(1, reopeningManager.existingCalls)
        assertEquals(1, reopened.getList().size)
        assertEquals(1, metadataDao.getAll().size)
    }

    @Test
    fun missingExistingKekFailsWithoutSilentResetOrMetadataReplacement() = runBlocking {
        val cardDao = FakeCardDao()
        val metadataDao = FakeVaultMetadataDao()
        val initial = repository(cardDao, metadataDao, FakeKekManager())
        initial.unlockOrCreateVault()
        initial.add(validInput())
        initial.lock()
        val metadataBefore = metadataDao.getAll().single()
        val missingManager = FakeKekManager(existingFailure = VaultKeyUnavailableException())
        val reopened = repository(cardDao, metadataDao, missingManager)

        val failure = runCatching { reopened.unlockOrCreateVault() }.exceptionOrNull()

        assertTrue(failure is VaultKeyUnavailableException)
        assertEquals(0, missingManager.createCalls)
        assertEquals(1, missingManager.existingCalls)
        assertSame(metadataBefore, metadataDao.getAll().single())
        assertFalse(reopened.isUnlocked())
    }

    @Test
    fun cardsWithoutMetadataFailBeforeCreatingAnyKek() = runBlocking {
        val cardDao = FakeCardDao().apply { addUnrecoverablePlaceholder() }
        val metadataDao = FakeVaultMetadataDao()
        val manager = FakeKekManager()
        val repository = repository(cardDao, metadataDao, manager)

        val failure = runCatching { repository.unlockOrCreateVault() }.exceptionOrNull()

        assertTrue(failure is VaultKeyUnavailableException)
        assertEquals(0, manager.createCalls)
        assertEquals(0, manager.existingCalls)
        assertTrue(metadataDao.getAll().isEmpty())
        assertFalse(repository.isUnlocked())
    }

    @Test
    fun lockingErasesRepositoryAccessUntilTheVaultIsUnlockedAgain() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        fixture.repository.add(validInput())

        fixture.repository.lock()
        val readFailure = runCatching { fixture.repository.getList() }.exceptionOrNull()
        val deleteFailure = runCatching {
            fixture.repository.delete(UUID.randomUUID())
        }.exceptionOrNull()

        assertTrue(readFailure is VaultKeyUnavailableException)
        assertTrue(deleteFailure is VaultKeyUnavailableException)
        assertFalse(fixture.repository.isUnlocked())
    }

    @Test
    fun repositoryAndStorageFailuresNeverPrintBusinessFields() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        val fullNumber = "7".repeat(12)
        val cvv = "6".repeat(3)
        fixture.repository.add(validInput(cardNumber = fullNumber, saveCvv = true, cvv = cvv))
        fixture.cardDao.failReads = true

        val failure = runCatching { fixture.repository.getList() }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure is VaultStorageException)
        listOf(fixture.repository.toString(), failure.toString()).forEach { output ->
            assertFalse(output.contains(fullNumber))
            assertFalse(output.contains(cvv))
            assertFalse(output.contains("Fictional encrypted nickname"))
        }
    }

    @Test
    fun encryptedPairingAndSyncRoundTripBetweenIndependentLocalDeks() = runBlocking {
        val phone = fixture()
        val desktop = fixture()
        phone.repository.unlockOrCreateVault()
        desktop.repository.unlockOrCreateVault()
        phone.repository.add(
            validInput(
                nickname = "Synthetic phone-only marker",
                notes = "Synthetic phone transfer note",
            ),
        )
        val phoneAddress = phone.repository.addAddress(
            validAddressInput(
                nickname = "Synthetic phone address",
                detailedAddress = "Imaginary phone avenue 12",
            ),
        )
        val phoneCoordinator = coordinator(phone)
        val desktopCoordinator = coordinator(desktop)

        val pairing = phoneCoordinator.createPairingExport()
        val pairingBytes = pairing.fileBytesCopy()
        assertFalse(pairingBytes.containsUtf8("Synthetic phone-only marker"))
        assertFalse(pairingBytes.containsUtf8("Synthetic phone transfer note"))
        assertFalse(pairingBytes.containsUtf8("Synthetic phone address"))
        assertFalse(pairingBytes.containsUtf8("Imaginary phone avenue 12"))
        desktopCoordinator.importPairing(pairingBytes, pairing.displayCode)
        assertEquals(
            listOf("Synthetic phone-only marker"),
            desktop.repository.getList().map(PersistentCardListItem::nickname),
        )
        assertEquals(
            listOf("Synthetic phone address"),
            desktop.repository.getAddressList().map(PersistentAddressListItem::nickname),
        )
        assertEquals(
            "Imaginary phone avenue 12",
            desktop.repository.getAddressDetail(phoneAddress)?.detailedAddress,
        )

        desktop.repository.add(validInput(nickname = "Synthetic desktop-only marker"))
        assertTrue(
            desktop.repository.updateAddress(
                phoneAddress,
                validAddressInput(
                    nickname = "Synthetic address edited on desktop",
                    detailedAddress = "Imaginary desktop avenue 27",
                    country = "Example Updated Country",
                ),
            ),
        )
        val desktopAddress = desktop.repository.addAddress(
            validAddressInput(
                nickname = "Synthetic desktop address",
                detailedAddress = "Imaginary desktop-only road 8",
            ),
        )
        val sync = desktopCoordinator.exportSync()
        val syncBytes = sync.fileBytesCopy()
        phoneCoordinator.importSync(syncBytes)

        assertEquals(
            setOf("Synthetic phone-only marker", "Synthetic desktop-only marker"),
            phone.repository.getList().map(PersistentCardListItem::nickname).toSet(),
        )
        assertEquals(
            setOf("Synthetic address edited on desktop", "Synthetic desktop address"),
            phone.repository.getAddressList().map(PersistentAddressListItem::nickname).toSet(),
        )
        assertEquals(
            "Example Updated Country",
            phone.repository.getAddressDetail(phoneAddress)?.country,
        )
        assertTrue(phone.repository.deleteAddress(desktopAddress))
        val deletionSync = phoneCoordinator.exportSync()
        desktopCoordinator.importSync(deletionSync.fileBytesCopy())
        assertEquals(
            listOf(phoneAddress),
            desktop.repository.getAddressList().map(PersistentAddressListItem::id),
        )
        assertTrue(
            desktop.cardDao.syncStateDao.addressTombstones.any {
                it.recordId == desktopAddress.toString()
            },
        )
        phone.cardDao.syncStateDao.clearRecentPackagesForTest()
        val replayAfterRecentHistoryTrim = runCatching {
            phoneCoordinator.importSync(syncBytes)
        }.exceptionOrNull()
        assertTrue(replayAfterRecentHistoryTrim is com.pdh.cardvault.sync.SyncProtocolException)
        assertEquals(
            com.pdh.cardvault.sync.SyncErrorCode.STALE_PACKAGE,
            (replayAfterRecentHistoryTrim as com.pdh.cardvault.sync.SyncProtocolException).code,
        )
        assertFalse(pairing.toString().contains(pairing.displayCode))
        assertEquals("AndroidVaultSyncCoordinator(contents=redacted)", phoneCoordinator.toString())
    }

    @Test
    fun authenticatedPairingReunifiesTwoIndependentlyPairedInstallations() = runBlocking {
        val phone = fixture()
        val desktop = fixture()
        phone.repository.unlockOrCreateVault()
        desktop.repository.unlockOrCreateVault()
        phone.repository.add(validInput(nickname = "Synthetic phone card"))
        desktop.repository.add(validInput(nickname = "Synthetic desktop card"))
        val phoneCoordinator = coordinator(phone)
        val desktopCoordinator = coordinator(desktop)
        val phonePairing = phoneCoordinator.createPairingExport()
        desktopCoordinator.createPairingExport()

        desktopCoordinator.importPairing(phonePairing.fileBytesCopy(), phonePairing.displayCode)

        assertEquals(
            setOf("Synthetic phone card", "Synthetic desktop card"),
            desktop.repository.getList().map(PersistentCardListItem::nickname).toSet(),
        )
        assertEquals(
            phone.cardDao.syncStateDao.vaultState?.vaultId,
            desktop.cardDao.syncStateDao.vaultState?.vaultId,
        )
        val sync = desktopCoordinator.exportSync()
        phoneCoordinator.importSync(sync.fileBytesCopy())
        assertEquals(2, phone.repository.getList().size)
    }

    @Test
    fun rotatingSyncKeyPreservesSavedCvvRejectsOldSyncAndAllowsNewPairing() = runBlocking {
        val phone = fixture()
        val desktop = fixture()
        phone.repository.unlockOrCreateVault()
        desktop.repository.unlockOrCreateVault()
        val syntheticCvv = "9".repeat(3)
        val cardId = phone.repository.add(
            validInput(
                nickname = "Synthetic rotation card",
                saveCvv = true,
                cvv = syntheticCvv,
            ),
        )
        val phoneCoordinator = coordinator(phone)
        val desktopCoordinator = coordinator(desktop)
        val initialPairing = phoneCoordinator.createPairingExport()
        desktopCoordinator.importPairing(initialPairing.fileBytesCopy(), initialPairing.displayCode)
        val oldSync = desktopCoordinator.exportSync().fileBytesCopy()

        val rotatedPairing = phoneCoordinator.rotateSyncKeyAndCreatePairingExport()

        assertEquals(2, phoneCoordinator.pairingStatus().keyEpoch)
        assertEquals(
            syntheticCvv,
            phone.repository.getSecretsForAuthenticatedUse(cardId)?.cvv,
        )
        val oldImportFailure = runCatching { phoneCoordinator.importSync(oldSync) }.exceptionOrNull()
        assertTrue(oldImportFailure is com.pdh.cardvault.sync.SyncProtocolException)
        assertEquals(
            com.pdh.cardvault.sync.SyncErrorCode.AUTHENTICATION_FAILED,
            (oldImportFailure as com.pdh.cardvault.sync.SyncProtocolException).code,
        )

        desktopCoordinator.importPairing(
            rotatedPairing.fileBytesCopy(),
            rotatedPairing.displayCode,
        )
        assertEquals(2, desktopCoordinator.pairingStatus().keyEpoch)
        assertEquals(
            syntheticCvv,
            desktop.repository.getSecretsForAuthenticatedUse(cardId)?.cvv,
        )
        oldSync.fill(0)
    }

    @Test
    fun legacyCardOnlyPairingFileDoesNotEraseLocalAddresses() = runBlocking {
        val source = fixture()
        val target = fixture()
        source.repository.unlockOrCreateVault()
        target.repository.unlockOrCreateVault()
        source.repository.add(validInput(nickname = "Synthetic legacy card"))
        val retainedAddress = target.repository.addAddress(
            validAddressInput(nickname = "Synthetic retained local address"),
        )
        val pairing = coordinator(source).createPairingExport()
        val pairingSecret = PairingCode.decode(pairing.displayCode)
        val decoded = CardVaultSyncFiles.decodePairing(pairing.fileBytesCopy(), pairingSecret)
        val sharedSyncSecret = decoded.syncSecret.copyBytes()
        val legacyPayload = decoded.copy(
            syncSecret = SecretBytes(sharedSyncSecret),
            snapshot = decoded.snapshot.copy(addresses = null, folders = null),
        )
        val legacyBytes = try {
            CardVaultSyncFiles.encodePairing(legacyPayload, pairingSecret)
        } finally {
            legacyPayload.syncSecret.close()
            decoded.syncSecret.close()
            sharedSyncSecret.fill(0)
            pairingSecret.fill(0)
        }

        coordinator(target).importPairing(legacyBytes, pairing.displayCode)

        assertEquals(
            listOf(retainedAddress),
            target.repository.getAddressList().map(PersistentAddressListItem::id),
        )
        assertEquals(
            "Synthetic retained local address",
            target.repository.getAddressDetail(retainedAddress)?.nickname,
        )
    }

    @Test
    fun importTransactionRejectsSeventeenthSourceWithoutMutatingWatermarks() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        val dao = fixture.cardDao.syncStateDao
        val state = requireNotNull(dao.vaultState)
        val order = requireNotNull(dao.orderState)
        val addressOrder = requireNotNull(dao.addressOrderState)

        repeat(16) { index ->
            dao.replaceSnapshotAtomically(
                cards = emptyList(),
                tombstones = emptyList(),
                order = order,
                addresses = emptyList(),
                addressTombstones = emptyList(),
                addressOrder = addressOrder,
                folders = emptyList(),
                folderTombstones = emptyList(),
                vaultState = state,
                importedPackage = ImportedSyncPackageEntity(
                    packageId = UUID.randomUUID().toString(),
                    sourceDeviceId = UUID.randomUUID().toString(),
                    exportSequence = index + 1L,
                    importedAt = index + 1L,
                ),
            )
        }
        val failure = runCatching {
            dao.replaceSnapshotAtomically(
                cards = emptyList(),
                tombstones = emptyList(),
                order = order,
                addresses = emptyList(),
                addressTombstones = emptyList(),
                addressOrder = addressOrder,
                folders = emptyList(),
                folderTombstones = emptyList(),
                vaultState = state,
                importedPackage = ImportedSyncPackageEntity(
                    packageId = UUID.randomUUID().toString(),
                    sourceDeviceId = UUID.randomUUID().toString(),
                    exportSequence = 1L,
                    importedAt = 17L,
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is SyncSourceLimitException)
        assertEquals(16, dao.sourceSequences.size)
    }

    @Test
    fun encryptedFoldersGroupCardsAndAddressesAndDeletionReturnsItemsToRoot() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        val cardId = fixture.repository.add(validInput())
        val addressId = fixture.repository.addAddress(validAddressInput())
        val cardFolderId = fixture.repository.createFolder(VaultFolderKind.CARDS, "Synthetic cards")
        val addressFolderId = fixture.repository.createFolder(VaultFolderKind.ADDRESSES, "Synthetic addresses")

        assertTrue(fixture.repository.moveCardToFolder(cardId, cardFolderId))
        assertTrue(fixture.repository.moveAddressToFolder(addressId, addressFolderId))
        assertEquals(cardFolderId, fixture.repository.getList().single().folderId)
        assertEquals(addressFolderId, fixture.repository.getAddressList().single().folderId)
        assertEquals("Synthetic cards", fixture.repository.getFolders(VaultFolderKind.CARDS).single().name)
        assertTrue(fixture.repository.renameFolder(cardFolderId, "Renamed cards"))
        assertEquals("Renamed cards", fixture.repository.getFolders(VaultFolderKind.CARDS).single().name)

        assertTrue(fixture.repository.deleteFolder(cardFolderId))
        assertTrue(fixture.repository.deleteFolder(addressFolderId))
        assertEquals(null, fixture.repository.getList().single().folderId)
        assertEquals(null, fixture.repository.getAddressList().single().folderId)
        assertTrue(fixture.repository.getFolders(VaultFolderKind.CARDS).isEmpty())
        assertTrue(fixture.repository.getFolders(VaultFolderKind.ADDRESSES).isEmpty())
    }

    @Test
    fun folderDisplayOrderPersistsAndIncludesMovableUnfiledEntry() = runBlocking {
        val fixture = fixture()
        fixture.repository.unlockOrCreateVault()
        val first = fixture.repository.createFolder(VaultFolderKind.CARDS, "First")
        val second = fixture.repository.createFolder(VaultFolderKind.CARDS, "Second")
        val third = fixture.repository.createFolder(VaultFolderKind.CARDS, "Third")

        fixture.repository.reorderFolders(
            VaultFolderKind.CARDS,
            listOf(third, null, first, second),
        )

        assertEquals(
            listOf(third, null, first, second),
            fixture.repository.getFolderOrder(VaultFolderKind.CARDS),
        )
        assertEquals(
            listOf(third, first, second),
            fixture.repository.getFolders(VaultFolderKind.CARDS).map(PersistentVaultFolder::id),
        )
    }

    private fun fixture(): RepositoryFixture {
        val cardDao = FakeCardDao()
        val addressDao = FakeAddressDao()
        val folderDao = FakeVaultFolderDao()
        val metadataDao = FakeVaultMetadataDao()
        val manager = FakeKekManager()
        folderDao.syncStateDao = cardDao.syncStateDao
        cardDao.syncStateDao.folderDao = folderDao
        return RepositoryFixture(
            cardDao = cardDao,
            addressDao = addressDao,
            folderDao = folderDao,
            metadataDao = metadataDao,
            repository = repository(cardDao, metadataDao, manager, addressDao, folderDao),
        )
    }

    private fun coordinator(
        fixture: RepositoryFixture,
    ): AndroidVaultSyncCoordinator = AndroidVaultSyncCoordinator(
        cardDao = fixture.cardDao,
        addressDao = fixture.addressDao,
        folderDao = fixture.folderDao,
        syncStateDao = fixture.cardDao.syncStateDao,
        repository = fixture.repository,
        validator = BankCardValidator { templateId ->
            CardTemplateRegistry.findById(templateId) != null
        },
        addressValidator = com.pdh.cardvault.domain.validation.AddressValidator { templateId ->
            CardTemplateRegistry.findById(templateId) != null
        },
    )

    private fun repository(
        cardDao: CardDao,
        metadataDao: VaultMetadataDao,
        kekManager: KekManager,
        addressDao: AddressDao = FakeAddressDao(),
        folderDao: VaultFolderDao = FakeVaultFolderDao(),
    ): EncryptedRoomBankCardRepository {
        val syncStateDao = (cardDao as FakeCardDao).syncStateDao
        (addressDao as FakeAddressDao).syncStateDao = syncStateDao
        syncStateDao.addressDao = addressDao
        (folderDao as FakeVaultFolderDao).syncStateDao = syncStateDao
        syncStateDao.folderDao = folderDao
        return EncryptedRoomBankCardRepository(
            cardDao = cardDao,
            addressDao = addressDao,
            folderDao = folderDao,
            metadataDao = metadataDao,
            syncStateDao = syncStateDao,
            validator = BankCardValidator { templateId ->
                CardTemplateRegistry.findById(templateId) != null
            },
            addressValidator = com.pdh.cardvault.domain.validation.AddressValidator { templateId ->
                CardTemplateRegistry.findById(templateId) != null
            },
            kekManager = kekManager,
            clock = IncrementingClock()::invoke,
        )
    }

    private fun validInput(
        nickname: String = "Fictional encrypted nickname",
        issuerName: String = "Fictional encrypted issuer",
        cardNumber: String = "0".repeat(12),
        expiryMonth: Int = 12,
        expiryYear: Int = 2_099,
        saveCvv: Boolean = false,
        cvv: String? = null,
        cardTemplateId: String = CardTemplateRegistry.DEFAULT_TEMPLATE_ID,
        notes: String = "Synthetic encrypted repository note",
    ): BankCardInput = BankCardInput(
        nickname = nickname,
        issuerName = issuerName,
        cardNumber = cardNumber,
        expiryMonth = expiryMonth,
        expiryYear = expiryYear,
        saveCvv = saveCvv,
        cvv = cvv,
        cardTemplateId = cardTemplateId,
        notes = notes,
    )

    private fun validAddressInput(
        nickname: String = "Synthetic address",
        detailedAddress: String = "Fictional road 1",
        country: String = "Example Country",
    ): AddressInput = AddressInput(
        nickname = nickname,
        detailedAddress = detailedAddress,
        city = "Example City",
        other = "Imaginary floor",
        postalCode = "TEST-100",
        country = country,
        cardTemplateId = CardTemplateRegistry.DEFAULT_TEMPLATE_ID,
    )
}

private data class RepositoryFixture(
    val cardDao: FakeCardDao,
    val addressDao: FakeAddressDao,
    val folderDao: FakeVaultFolderDao,
    val metadataDao: FakeVaultMetadataDao,
    val repository: EncryptedRoomBankCardRepository,
)

private class IncrementingClock {
    private var value = 1_000L

    operator fun invoke(): Long = value++
}

private class FakeKekManager(
    private val key: SecretKey = randomTestKey(),
    private val existingFailure: Exception? = null,
) : KekManager {
    var createCalls: Int = 0
        private set
    var existingCalls: Int = 0
        private set

    override fun createOrGetForNewVault(): SecretKey {
        createCalls += 1
        return key
    }

    override fun getExisting(): SecretKey {
        existingCalls += 1
        existingFailure?.let { throw it }
        return key
    }
}

private class FakeCardDao(
    val syncStateDao: FakeSyncStateDao = FakeSyncStateDao(),
) : CardDao() {
    private val rows = mutableListOf<CardEntity>()
    var failReads: Boolean = false

    init {
        syncStateDao.cardDao = this
    }

    override suspend fun getAll(): List<CardEntity> {
        if (failReads) throw IllegalStateException("Synthetic storage failure without data")
        return rows.sortedBy(CardEntity::sortOrder)
    }

    override suspend fun getById(id: String): CardEntity? = rows.firstOrNull { it.id == id }

    override suspend fun count(): Int = rows.size

    protected override suspend fun insert(entity: CardEntity) {
        if (rows.any { it.id == entity.id }) throw IllegalStateException("Synthetic insert conflict")
        rows += entity
    }

    protected override suspend fun shiftAllForFrontInsert() {
        rows.indices.forEach { index ->
            rows[index] = rows[index].copy(sortOrder = rows[index].sortOrder + 1)
        }
    }

    protected override suspend fun updateEncryptedPayload(
        id: String,
        ciphertext: ByteArray,
        recordIv: ByteArray,
        payloadSchemaVersion: Int,
        cryptoVersion: Int,
        updatedAt: Long,
        versionVector: ByteArray,
    ): Int {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) return 0
        rows[index] = rows[index].copy(
            ciphertext = ciphertext.copyOf(),
            recordIv = recordIv.copyOf(),
            payloadSchemaVersion = payloadSchemaVersion,
            cryptoVersion = cryptoVersion,
            updatedAt = updatedAt,
            versionVector = versionVector.copyOf(),
        )
        return 1
    }

    protected override suspend fun deleteById(id: String): Int {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) return 0
        rows.removeAt(index)
        return 1
    }

    protected override suspend fun compactAfterDelete(deletedOrder: Int) {
        rows.indices.forEach { index ->
            if (rows[index].sortOrder > deletedOrder) {
                rows[index] = rows[index].copy(sortOrder = rows[index].sortOrder - 1)
            }
        }
    }

    protected override suspend fun updateSortOrder(id: String, sortOrder: Int): Int {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) return 0
        rows[index] = rows[index].copy(sortOrder = sortOrder)
        return 1
    }

    protected override suspend fun getOrderState(): SyncOrderStateEntity? =
        syncStateDao.orderState

    protected override suspend fun updateOrderState(
        versionVector: ByteArray,
        updatedAt: Long,
    ): Int {
        val current = syncStateDao.orderState ?: return 0
        syncStateDao.orderState = current.copy(
            versionVector = versionVector.copyOf(),
            updatedAt = updatedAt,
        )
        return 1
    }

    protected override suspend fun insertTombstone(entity: SyncTombstoneEntity) {
        check(syncStateDao.tombstones.none { it.recordId == entity.recordId })
        syncStateDao.tombstones += entity
    }

    fun corruptCiphertext(id: String) {
        val index = rows.indexOfFirst { it.id == id }
        check(index >= 0)
        val corrupted = rows[index].ciphertext.copyOf()
        corrupted[corrupted.lastIndex] = (corrupted.last() + 1).toByte()
        rows[index] = rows[index].copy(ciphertext = corrupted)
    }

    fun addUnrecoverablePlaceholder() {
        rows += CardEntity(
            id = UUID.randomUUID().toString(),
            ciphertext = ByteArray(16),
            recordIv = ByteArray(12),
            payloadSchemaVersion = 1,
            cryptoVersion = 1,
            sortOrder = 0,
            createdAt = 1,
            updatedAt = 1,
        )
    }

    fun replaceAllForSync(entities: List<CardEntity>) {
        rows.clear()
        rows += entities
    }

    fun clearForSync() {
        rows.clear()
    }

    fun initializeMissingVersions(initialVersion: ByteArray): Int {
        var changed = 0
        rows.indices.forEach { index ->
            if (rows[index].versionVector.isEmpty()) {
                rows[index] = rows[index].copy(versionVector = initialVersion.copyOf())
                changed += 1
            }
        }
        return changed
    }
}

private class FakeAddressDao : AddressDao() {
    private val rows = mutableListOf<AddressEntity>()
    lateinit var syncStateDao: FakeSyncStateDao

    override suspend fun getAll(): List<AddressEntity> = rows.sortedBy(AddressEntity::sortOrder)

    override suspend fun getById(id: String): AddressEntity? = rows.firstOrNull { it.id == id }

    override suspend fun count(): Int = rows.size

    protected override suspend fun insert(entity: AddressEntity) {
        rows += entity
    }

    protected override suspend fun shiftAllForFrontInsert() {
        rows.indices.forEach { index ->
            rows[index] = rows[index].copy(sortOrder = rows[index].sortOrder + 1)
        }
    }

    protected override suspend fun updateEncryptedPayload(
        id: String,
        ciphertext: ByteArray,
        recordIv: ByteArray,
        payloadSchemaVersion: Int,
        cryptoVersion: Int,
        updatedAt: Long,
        versionVector: ByteArray,
    ): Int {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) return 0
        rows[index] = rows[index].copy(
            ciphertext = ciphertext.copyOf(),
            recordIv = recordIv.copyOf(),
            payloadSchemaVersion = payloadSchemaVersion,
            cryptoVersion = cryptoVersion,
            updatedAt = updatedAt,
            versionVector = versionVector.copyOf(),
        )
        return 1
    }

    protected override suspend fun deleteById(id: String): Int {
        val removed = rows.removeAll { it.id == id }
        return if (removed) 1 else 0
    }

    protected override suspend fun compactAfterDelete(deletedOrder: Int) {
        rows.indices.forEach { index ->
            if (rows[index].sortOrder > deletedOrder) {
                rows[index] = rows[index].copy(sortOrder = rows[index].sortOrder - 1)
            }
        }
    }

    protected override suspend fun updateSortOrder(id: String, sortOrder: Int): Int {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) return 0
        rows[index] = rows[index].copy(sortOrder = sortOrder)
        return 1
    }

    protected override suspend fun getAddressOrderState(): AddressSyncOrderStateEntity? =
        syncStateDao.addressOrderState

    protected override suspend fun updateAddressOrderState(
        versionVector: ByteArray,
        updatedAt: Long,
    ): Int {
        val current = syncStateDao.addressOrderState ?: return 0
        syncStateDao.addressOrderState = current.copy(
            versionVector = versionVector.copyOf(),
            updatedAt = updatedAt,
        )
        return 1
    }

    protected override suspend fun insertAddressTombstone(entity: AddressSyncTombstoneEntity) {
        check(syncStateDao.addressTombstones.none { it.recordId == entity.recordId })
        syncStateDao.addressTombstones += entity
    }

    fun replaceAllForSync(entities: List<AddressEntity>) {
        rows.clear()
        rows += entities
    }

    fun clearForSync() = rows.clear()

    fun initializeMissingVersions(initialVersion: ByteArray): Int {
        var changed = 0
        rows.indices.forEach { index ->
            if (rows[index].versionVector.isEmpty()) {
                rows[index] = rows[index].copy(versionVector = initialVersion.copyOf())
                changed += 1
            }
        }
        return changed
    }
}

private class FakeVaultFolderDao : VaultFolderDao() {
    private val rows = mutableListOf<VaultFolderEntity>()
    private val displayOrders = mutableMapOf<String, VaultFolderDisplayOrderEntity>()
    lateinit var syncStateDao: FakeSyncStateDao

    override suspend fun getAll(): List<VaultFolderEntity> = rows.sortedWith(
        compareBy<VaultFolderEntity> { it.createdAt }.thenBy { it.id },
    )

    override suspend fun getById(id: String): VaultFolderEntity? = rows.firstOrNull { it.id == id }

    override suspend fun getDisplayOrder(collection: String): VaultFolderDisplayOrderEntity? =
        displayOrders[collection]

    override suspend fun upsertDisplayOrder(entity: VaultFolderDisplayOrderEntity) {
        displayOrders[entity.collection] = entity
    }

    protected override suspend fun insert(entity: VaultFolderEntity) {
        check(rows.none { it.id == entity.id })
        rows += entity
    }

    protected override suspend fun updateEncryptedPayload(
        id: String,
        ciphertext: ByteArray,
        recordIv: ByteArray,
        payloadSchemaVersion: Int,
        cryptoVersion: Int,
        updatedAt: Long,
        versionVector: ByteArray,
    ): Int {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) return 0
        rows[index] = rows[index].copy(
            ciphertext = ciphertext.copyOf(),
            recordIv = recordIv.copyOf(),
            payloadSchemaVersion = payloadSchemaVersion,
            cryptoVersion = cryptoVersion,
            updatedAt = updatedAt,
            versionVector = versionVector.copyOf(),
        )
        return 1
    }

    protected override suspend fun deleteById(id: String): Int {
        val removed = rows.removeAll { it.id == id }
        return if (removed) 1 else 0
    }

    protected override suspend fun insertTombstone(entity: FolderSyncTombstoneEntity) {
        check(syncStateDao.folderTombstones.none { it.recordId == entity.recordId })
        syncStateDao.folderTombstones += entity
    }

    fun replaceAllForSync(entities: List<VaultFolderEntity>) {
        rows.clear()
        rows += entities
    }

    fun clearForSync() = rows.clear()
}

private class FakeSyncStateDao : SyncStateDao() {
    lateinit var cardDao: FakeCardDao
    lateinit var addressDao: FakeAddressDao
    lateinit var folderDao: FakeVaultFolderDao
    var vaultState: SyncVaultStateEntity? = null
    var orderState: SyncOrderStateEntity? = null
    var addressOrderState: AddressSyncOrderStateEntity? = null
    val tombstones = mutableListOf<SyncTombstoneEntity>()
    val addressTombstones = mutableListOf<AddressSyncTombstoneEntity>()
    val folderTombstones = mutableListOf<FolderSyncTombstoneEntity>()
    private val importedPackages = mutableListOf<ImportedSyncPackageEntity>()
    val sourceSequences = linkedMapOf<String, Long>()

    override suspend fun getAllVaultStates(): List<SyncVaultStateEntity> =
        listOfNotNull(vaultState)

    override suspend fun getVaultState(): SyncVaultStateEntity? = vaultState

    override suspend fun getOrderState(): SyncOrderStateEntity? = orderState

    override suspend fun getTombstones(): List<SyncTombstoneEntity> =
        tombstones.sortedBy(SyncTombstoneEntity::recordId)

    override suspend fun getAddressOrderState(): AddressSyncOrderStateEntity? = addressOrderState

    override suspend fun getAddressTombstones(): List<AddressSyncTombstoneEntity> =
        addressTombstones.sortedBy(AddressSyncTombstoneEntity::recordId)

    override suspend fun getFolderTombstones(): List<FolderSyncTombstoneEntity> =
        folderTombstones.sortedBy(FolderSyncTombstoneEntity::recordId)

    override suspend fun getRecentImportedPackages(): List<ImportedSyncPackageEntity> =
        importedPackages
            .sortedWith(
                compareByDescending<ImportedSyncPackageEntity> { it.importedAt }
                    .thenByDescending { it.packageId },
            )
            .take(1_024)

    override suspend fun hasImportedPackage(packageId: String): Boolean =
        importedPackages.any { it.packageId == packageId }

    override suspend fun maxImportedSequence(sourceDeviceId: String): Long? =
        sourceSequences[sourceDeviceId]

    protected override suspend fun sourceSequenceCount(): Int = sourceSequences.size

    protected override suspend fun insertVaultState(entity: SyncVaultStateEntity) {
        check(vaultState == null)
        vaultState = entity
    }

    protected override suspend fun insertOrderState(entity: SyncOrderStateEntity) {
        check(orderState == null)
        orderState = entity
    }

    protected override suspend fun insertAddressOrderState(entity: AddressSyncOrderStateEntity) {
        check(addressOrderState == null)
        addressOrderState = entity
    }

    protected override suspend fun replaceVaultState(entity: SyncVaultStateEntity) {
        vaultState = entity
    }

    protected override suspend fun replaceOrderState(entity: SyncOrderStateEntity) {
        orderState = entity
    }

    protected override suspend fun replaceAddressOrderState(entity: AddressSyncOrderStateEntity) {
        addressOrderState = entity
    }

    protected override suspend fun insertImportedPackage(entity: ImportedSyncPackageEntity) {
        check(importedPackages.none { it.packageId == entity.packageId })
        importedPackages += entity
    }

    protected override suspend fun upsertSourceSequence(entity: SyncSourceSequenceEntity) {
        sourceSequences[entity.sourceDeviceId] = entity.highestSequence
    }

    protected override suspend fun insertCards(entities: List<CardEntity>) {
        cardDao.replaceAllForSync(entities)
    }

    protected override suspend fun insertTombstones(entities: List<SyncTombstoneEntity>) {
        tombstones += entities
    }

    protected override suspend fun insertAddresses(entities: List<AddressEntity>) {
        addressDao.replaceAllForSync(entities)
    }

    protected override suspend fun insertAddressTombstones(
        entities: List<AddressSyncTombstoneEntity>,
    ) {
        addressTombstones += entities
    }

    protected override suspend fun insertFolders(entities: List<VaultFolderEntity>) {
        folderDao.replaceAllForSync(entities)
    }

    protected override suspend fun insertFolderTombstones(entities: List<FolderSyncTombstoneEntity>) {
        folderTombstones += entities
    }

    protected override suspend fun deleteAllCards() {
        cardDao.clearForSync()
    }

    protected override suspend fun deleteAllTombstones() {
        tombstones.clear()
    }

    protected override suspend fun deleteAllAddresses() {
        addressDao.clearForSync()
    }

    protected override suspend fun deleteAllAddressTombstones() {
        addressTombstones.clear()
    }

    protected override suspend fun deleteAllFolders() {
        folderDao.clearForSync()
    }

    protected override suspend fun deleteAllFolderTombstones() {
        folderTombstones.clear()
    }

    protected override suspend fun deleteAllImportedPackages() {
        importedPackages.clear()
    }

    protected override suspend fun deleteAllSourceSequences() {
        sourceSequences.clear()
    }

    protected override suspend fun trimImportedPackages() {
        if (importedPackages.size > 1_024) {
            val retained = importedPackages
                .sortedWith(
                    compareByDescending<ImportedSyncPackageEntity> { it.importedAt }
                        .thenByDescending { it.packageId },
                )
                .take(1_024)
            importedPackages.clear()
            importedPackages += retained
        }
    }

    protected override suspend fun initializeMissingCardVersions(initialVersion: ByteArray): Int =
        cardDao.initializeMissingVersions(initialVersion)

    protected override suspend fun initializeMissingAddressVersions(initialVersion: ByteArray): Int =
        addressDao.initializeMissingVersions(initialVersion)

    fun clearRecentPackagesForTest() {
        importedPackages.clear()
    }
}

private class FakeVaultMetadataDao : VaultMetadataDao() {
    private val rows = mutableListOf<VaultMetadataEntity>()

    override suspend fun getAll(): List<VaultMetadataEntity> = rows.toList()

    protected override suspend fun insert(metadata: VaultMetadataEntity) {
        if (rows.any { it.kekAliasVersion == metadata.kekAliasVersion }) {
            throw IllegalStateException("Synthetic metadata conflict")
        }
        rows += metadata
    }
}

private fun ByteArray.containsUtf8(value: String): Boolean {
    val target = value.toByteArray(StandardCharsets.UTF_8)
    if (target.isEmpty() || target.size > size) return false
    return (0..size - target.size).any { offset ->
        target.indices.all { index -> this[offset + index] == target[index] }
    }
}

private fun randomTestKey(): SecretKey {
    val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
    return try {
        SecretKeySpec(bytes, "AES")
    } finally {
        bytes.fill(0)
    }
}
