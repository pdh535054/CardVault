package com.pdh.cardvault.data.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardVaultMigration3To4Test {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private var migratedDatabase: CardVaultDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "cardvault-migration-3-4-${UUID.randomUUID()}.db"
    }

    @After
    fun tearDown() {
        migratedDatabase?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationPreservesEncryptedAddressAndAddsAddressSyncMetadata() {
        val recordId = UUID.randomUUID().toString()
        val ciphertext = byteArrayOf(0x43, 0x56, 0x34, 0x01, 0x7f, 0x00, 0x2a)
        val recordIv = byteArrayOf(0x0c, 0x0b, 0x0a, 0x09, 0x08, 0x07)
        val payloadSchemaVersion = 2
        val cryptoVersion = 1
        val sortOrder = 3
        val createdAt = 1_720_000_000_001L
        val updatedAt = 1_720_000_000_999L

        createVersionThreeDatabase(
            recordId = recordId,
            ciphertext = ciphertext,
            recordIv = recordIv,
            payloadSchemaVersion = payloadSchemaVersion,
            cryptoVersion = cryptoVersion,
            sortOrder = sortOrder,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        val roomDatabase = Room.databaseBuilder(
            context,
            CardVaultDatabase::class.java,
            databaseName,
        )
            .addMigrations(CardVaultDatabase.MIGRATION_3_4)
            .build()
            .also { migratedDatabase = it }
        val sqlite = roomDatabase.openHelper.writableDatabase

        sqlite.query(
            "SELECT id, ciphertext, recordIv, payloadSchemaVersion, cryptoVersion, " +
                "sortOrder, createdAt, updatedAt, versionVector FROM addresses WHERE id = ?",
            arrayOf(recordId),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(recordId, cursor.getString(0))
            assertArrayEquals(ciphertext, cursor.getBlob(1))
            assertArrayEquals(recordIv, cursor.getBlob(2))
            assertEquals(payloadSchemaVersion, cursor.getInt(3))
            assertEquals(cryptoVersion, cursor.getInt(4))
            assertEquals(sortOrder, cursor.getInt(5))
            assertEquals(createdAt, cursor.getLong(6))
            assertEquals(updatedAt, cursor.getLong(7))
            assertArrayEquals(ByteArray(0), cursor.getBlob(8))
        }

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
            tableColumns(sqlite, "addresses"),
        )
        assertEquals(
            setOf("singletonId", "versionVector", "updatedAt"),
            tableColumns(sqlite, "address_sync_order_state"),
        )
        assertEquals(
            setOf("recordId", "versionVector", "deletedAt"),
            tableColumns(sqlite, "address_sync_tombstones"),
        )
        assertEquals(0, rowCount(sqlite, "address_sync_order_state"))
        assertEquals(0, rowCount(sqlite, "address_sync_tombstones"))
    }

    private fun createVersionThreeDatabase(
        recordId: String,
        ciphertext: ByteArray,
        recordIv: ByteArray,
        payloadSchemaVersion: Int,
        cryptoVersion: Int,
        sortOrder: Int,
        createdAt: Long,
        updatedAt: Long,
    ) {
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.let { parent ->
            check(parent.isDirectory || parent.mkdirs())
        }
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { sqlite ->
            VERSION_THREE_CREATE_STATEMENTS.forEach(sqlite::execSQL)
            sqlite.execSQL(
                "INSERT INTO addresses " +
                    "(id, ciphertext, recordIv, payloadSchemaVersion, cryptoVersion, " +
                    "sortOrder, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    recordId,
                    ciphertext,
                    recordIv,
                    payloadSchemaVersion,
                    cryptoVersion,
                    sortOrder,
                    createdAt,
                    updatedAt,
                ),
            )
            sqlite.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            sqlite.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(VERSION_THREE_IDENTITY_HASH),
            )
            sqlite.version = 3
        }
    }

    private fun tableColumns(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
    ): Set<String> = buildSet {
        database.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) add(cursor.getString(nameIndex))
        }
    }

    private fun rowCount(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
    ): Int = database.query("SELECT COUNT(*) FROM `$tableName`").use { cursor ->
        check(cursor.moveToFirst())
        cursor.getInt(0)
    }

    private companion object {
        const val VERSION_THREE_IDENTITY_HASH = "f700b76c67c120f52c776faac898ba85"

        val VERSION_THREE_CREATE_STATEMENTS = listOf(
            """
            CREATE TABLE IF NOT EXISTS cards (
                id TEXT NOT NULL,
                ciphertext BLOB NOT NULL,
                recordIv BLOB NOT NULL,
                payloadSchemaVersion INTEGER NOT NULL,
                cryptoVersion INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                versionVector BLOB NOT NULL DEFAULT X'',
                PRIMARY KEY(id)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS addresses (
                id TEXT NOT NULL,
                ciphertext BLOB NOT NULL,
                recordIv BLOB NOT NULL,
                payloadSchemaVersion INTEGER NOT NULL,
                cryptoVersion INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS vault_metadata (
                kekAliasVersion INTEGER NOT NULL,
                wrappedDek BLOB NOT NULL,
                wrappingIv BLOB NOT NULL,
                wrappingFormatVersion INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(kekAliasVersion)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS sync_vault_state (
                singletonId INTEGER NOT NULL,
                vaultId TEXT NOT NULL,
                deviceId TEXT NOT NULL,
                keyEpoch INTEGER NOT NULL,
                encryptedSyncKey BLOB,
                syncKeyIv BLOB,
                syncKeyCryptoVersion INTEGER NOT NULL,
                exportSequence INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(singletonId)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS sync_order_state (
                singletonId INTEGER NOT NULL,
                versionVector BLOB NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(singletonId)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS sync_tombstones (
                recordId TEXT NOT NULL,
                versionVector BLOB NOT NULL,
                deletedAt INTEGER NOT NULL,
                PRIMARY KEY(recordId)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS imported_sync_packages (
                packageId TEXT NOT NULL,
                sourceDeviceId TEXT NOT NULL,
                exportSequence INTEGER NOT NULL,
                importedAt INTEGER NOT NULL,
                PRIMARY KEY(packageId)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS sync_source_sequences (
                sourceDeviceId TEXT NOT NULL,
                highestSequence INTEGER NOT NULL,
                PRIMARY KEY(sourceDeviceId)
            )
            """.trimIndent(),
        )
    }
}
