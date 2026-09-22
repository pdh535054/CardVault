package com.pdh.cardvault.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pdh.cardvault.domain.model.BankCardInput
import com.pdh.cardvault.domain.validation.BankCardValidator
import com.pdh.cardvault.security.crypto.EncryptedDataAuthenticationException
import com.pdh.cardvault.security.crypto.KekManager
import com.pdh.cardvault.security.crypto.VaultKeyUnavailableException
import com.pdh.cardvault.ui.card.CardTemplateRegistry
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedRoomDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: CardVaultDatabase
    private lateinit var repository: EncryptedRoomBankCardRepository
    private lateinit var kekManager: TestKekManager
    private lateinit var databaseName: String

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "cardvault-instrumentation-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, CardVaultDatabase::class.java, databaseName)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        kekManager = TestKekManager()
        repository = createRepository(kekManager)
        repository.unlockOrCreateVault()
    }

    @After
    fun tearDown() {
        if (::repository.isInitialized) repository.lock()
        if (::database.isInitialized) database.close()
        if (::context.isInitialized && ::databaseName.isInitialized) {
            context.deleteDatabase(databaseName)
        }
        if (::kekManager.isInitialized) kekManager.clear()
    }

    @Test
    fun realRoomCrudEncryptsBusinessFieldsAndUsesFreshIvOnEdit() = runBlocking {
        val first = repository.add(syntheticInput(nickname = marker("FirstNick")))
        val second = repository.add(syntheticInput(nickname = marker("SecondNick")))
        val beforeEdit = requireNotNull(database.cardDao().getById(first.toString()))

        assertEquals(listOf(second, first), repository.getList().map { it.id })
        assertEquals(listOf(0, 1), database.cardDao().getAll().map { it.sortOrder })

        assertTrue(repository.update(first, syntheticInput(nickname = marker("EditedNick"))))
        val afterEdit = requireNotNull(database.cardDao().getById(first.toString()))
        assertNotEquals(beforeEdit.recordIv.toList(), afterEdit.recordIv.toList())
        assertNotEquals(beforeEdit.ciphertext.toList(), afterEdit.ciphertext.toList())

        repository.reorder(listOf(first, second))
        assertEquals(listOf(first, second), repository.getList().map { it.id })
        assertEquals(listOf(0, 1), database.cardDao().getAll().map { it.sortOrder })

        assertTrue(repository.delete(first))
        assertEquals(listOf(second), repository.getList().map { it.id })
        assertEquals(listOf(0), database.cardDao().getAll().map { it.sortOrder })
    }

    @Test
    fun failedFrontInsertRollsBackOrderShiftAtomically() = runBlocking {
        val card = repository.add(syntheticInput())
        val existing = requireNotNull(database.cardDao().getById(card.toString()))
        val localDeviceId = requireNotNull(database.syncStateDao().getVaultState()).deviceId

        val failure = runCatching {
            database.cardDao().insertAtFront(
                entity = existing.copy(sortOrder = 0),
                localDeviceId = localDeviceId,
                proposedUpdatedAt = System.currentTimeMillis(),
            )
        }.exceptionOrNull()

        assertTrue(failure is RuntimeException)
        assertEquals(1, database.cardDao().count())
        assertEquals(listOf(0), database.cardDao().getAll().map { it.sortOrder })
        assertEquals(card.toString(), database.cardDao().getAll().single().id)
    }

    @Test
    fun invalidReorderRollsBackWithoutCreatingOrDeletingRecords() = runBlocking {
        val first = repository.add(syntheticInput(nickname = marker("First")))
        val second = repository.add(syntheticInput(nickname = marker("Second")))
        val before = repository.getList().map { it.id }

        val failure = runCatching {
            repository.reorder(listOf(first, UUID.randomUUID()))
        }.exceptionOrNull()

        assertTrue(failure is DatabaseInvariantException)
        assertEquals(before, repository.getList().map { it.id })
        assertEquals(setOf(first, second), repository.getList().map { it.id }.toSet())
        assertEquals(listOf(0, 1), database.cardDao().getAll().map { it.sortOrder })
    }

    @Test
    fun physicalSchemaContainsOnlyApprovedColumns() {
        assertEquals(
            setOf(
                "id",
                "ciphertext",
                "recordIv",
                "payloadSchemaVersion",
                "cryptoVersion",
                "sortOrder",
                "createdAt",
                "updatedAt",
                "versionVector",
            ),
            tableColumns("cards"),
        )
        assertEquals(
            setOf(
                "id",
                "ciphertext",
                "recordIv",
                "payloadSchemaVersion",
                "cryptoVersion",
                "sortOrder",
                "createdAt",
                "updatedAt",
                "versionVector",
            ),
            tableColumns("addresses"),
        )
        assertEquals(
            setOf(
                "kekAliasVersion",
                "wrappedDek",
                "wrappingIv",
                "wrappingFormatVersion",
                "createdAt",
                "updatedAt",
            ),
            tableColumns("vault_metadata"),
        )
        assertEquals(
            setOf("singletonId", "versionVector", "updatedAt"),
            tableColumns("address_sync_order_state"),
        )
        assertEquals(
            setOf("recordId", "versionVector", "deletedAt"),
            tableColumns("address_sync_tombstones"),
        )
    }

    @Test
    fun physicalSchemaContainsNoPlaintextAddressColumns() {
        val forbiddenColumns = setOf(
            "nickname",
            "detailedAddress",
            "addressLine",
            "street",
            "city",
            "district",
            "province",
            "state",
            "region",
            "other",
            "postalCode",
            "zipCode",
            "country",
            "cardTemplateId",
            "notes",
        ).map(String::lowercase).toSet()
        val persistedColumns = persistedTableNames()
            .flatMap(::tableColumns)
            .map(String::lowercase)
            .toSet()

        assertTrue(forbiddenColumns.intersect(persistedColumns).isEmpty())
    }

    @Test
    fun databaseWalAndShmContainNoUtf8OrUtf16BusinessMarkers() = runBlocking {
        val nickname = marker("Nickname")
        val issuer = marker("Issuer")
        val cardNumber = fictionalCardNumber(19)
        val cvv = randomDigits(4)
        val notes = marker("Notes")
        val expiryYear = 2_090 + UUID.randomUUID().hashCode().mod(10)
        repository.add(
            syntheticInput(
                nickname = nickname,
                issuerName = issuer,
                cardNumber = cardNumber,
                expiryMonth = 11,
                expiryYear = expiryYear,
                saveCvv = true,
                cvv = cvv,
                cardTemplateId = "generic_crypto",
                notes = notes,
            ),
        )
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM cards").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        val databaseFile = context.getDatabasePath(databaseName)
        val files = listOf(
            databaseFile,
            File(databaseFile.path + "-wal"),
            File(databaseFile.path + "-shm"),
        )
        files.forEach { file -> assertTrue("Missing SQLite file: ${file.name}", file.isFile) }
        val markers = listOf(
            nickname,
            issuer,
            cardNumber,
            expiryYear.toString(),
            cvv,
            "generic_crypto",
            notes,
        )

        files.forEach { file ->
            val bytes = file.readBytes()
            markers.forEach { value ->
                assertFalse("Plaintext marker found in ${file.name}", bytes.containsEncoded(value))
            }
        }
    }

    @Test
    fun tamperedCiphertextFailsAuthenticationWithoutReturningPartialData() = runBlocking {
        val intact = repository.add(syntheticInput(nickname = marker("Intact")))
        val target = repository.add(syntheticInput(nickname = marker("Tamper")))
        val entity = requireNotNull(database.cardDao().getById(target.toString()))
        val corrupted = entity.ciphertext.copyOf().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()
        }
        database.openHelper.writableDatabase.execSQL(
            "UPDATE cards SET ciphertext = ? WHERE id = ?",
            arrayOf(corrupted, target.toString()),
        )

        val failure = runCatching { repository.getList() }.exceptionOrNull()

        assertTrue(failure is EncryptedDataAuthenticationException)
        assertFalse(failure.toString().contains(intact.toString()))
        assertFalse(failure.toString().contains(target.toString()))
    }

    @Test
    fun missingExistingKekNeverCreatesAReplacement() = runBlocking {
        repository.add(syntheticInput())
        repository.lock()
        val missingManager = TestKekManager(failExisting = true)
        val reopened = createRepository(missingManager)

        val failure = runCatching { reopened.unlockOrCreateVault() }.exceptionOrNull()

        assertTrue(failure is VaultKeyUnavailableException)
        assertEquals(0, missingManager.createCalls)
        assertEquals(1, missingManager.existingCalls)
        assertEquals(1, database.vaultMetadataDao().getAll().size)
        assertFalse(reopened.isUnlocked())
        missingManager.clear()
    }

    @Test
    fun missingMetadataWithExistingCardsFailsBeforeKekCreation() = runBlocking {
        repository.add(syntheticInput())
        repository.lock()
        database.openHelper.writableDatabase.execSQL("DELETE FROM vault_metadata")
        val manager = TestKekManager()
        val reopened = createRepository(manager)

        val failure = runCatching { reopened.unlockOrCreateVault() }.exceptionOrNull()

        assertTrue(failure is VaultKeyUnavailableException)
        assertEquals(0, manager.createCalls)
        assertEquals(0, manager.existingCalls)
        assertEquals(1, database.cardDao().count())
        assertFalse(reopened.isUnlocked())
        manager.clear()
    }

    private fun createRepository(manager: KekManager): EncryptedRoomBankCardRepository =
        EncryptedRoomBankCardRepository(
            cardDao = database.cardDao(),
            addressDao = database.addressDao(),
            folderDao = database.vaultFolderDao(),
            metadataDao = database.vaultMetadataDao(),
            syncStateDao = database.syncStateDao(),
            validator = BankCardValidator { templateId ->
                CardTemplateRegistry.findById(templateId) != null
            },
            addressValidator = com.pdh.cardvault.domain.validation.AddressValidator { templateId ->
                CardTemplateRegistry.findById(templateId) != null
            },
            kekManager = manager,
        )

    private fun tableColumns(tableName: String): Set<String> = buildSet {
        database.openHelper.writableDatabase.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) add(cursor.getString(nameIndex))
        }
    }

    private fun persistedTableNames(): Set<String> = buildSet {
        database.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master " +
                "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table'",
        ).use { cursor ->
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    private fun syntheticInput(
        nickname: String = marker("Nickname"),
        issuerName: String = marker("Issuer"),
        cardNumber: String = fictionalCardNumber(12),
        expiryMonth: Int = 12,
        expiryYear: Int = 2_099,
        saveCvv: Boolean = false,
        cvv: String? = null,
        cardTemplateId: String = CardTemplateRegistry.DEFAULT_TEMPLATE_ID,
        notes: String = marker("Notes"),
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

    private fun marker(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    private fun fictionalCardNumber(length: Int): String {
        require(length in 12..19)
        return "0".repeat(10) + randomDigits(length - 10)
    }

    private fun randomDigits(length: Int): String {
        val digits = StringBuilder(length)
        while (digits.length < length) {
            UUID.randomUUID().toString().forEach { character ->
                if (character.isDigit() && digits.length < length) digits.append(character)
            }
        }
        return digits.toString()
    }
}

private class TestKekManager(
    private val failExisting: Boolean = false,
) : KekManager {
    private val rawKey = ByteArray(32).also(SecureRandom()::nextBytes)
    private val key = SecretKeySpec(rawKey, "AES")
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
        if (failExisting) throw VaultKeyUnavailableException()
        return key
    }

    fun clear() {
        rawKey.fill(0)
    }
}

private fun ByteArray.containsEncoded(value: String): Boolean = listOf(
    value.toByteArray(StandardCharsets.UTF_8),
    value.toByteArray(StandardCharsets.UTF_16LE),
    value.toByteArray(StandardCharsets.UTF_16BE),
).any(::containsBytes)

private fun ByteArray.containsBytes(target: ByteArray): Boolean {
    if (target.isEmpty() || target.size > size) return false
    return (0..size - target.size).any { offset ->
        target.indices.all { index -> this[offset + index] == target[index] }
    }
}
