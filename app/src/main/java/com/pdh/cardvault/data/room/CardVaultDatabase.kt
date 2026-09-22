package com.pdh.cardvault.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CardEntity::class,
        AddressEntity::class,
        AddressSyncOrderStateEntity::class,
        AddressSyncTombstoneEntity::class,
        VaultFolderEntity::class,
        VaultFolderDisplayOrderEntity::class,
        FolderSyncTombstoneEntity::class,
        VaultMetadataEntity::class,
        SyncVaultStateEntity::class,
        SyncOrderStateEntity::class,
        SyncTombstoneEntity::class,
        ImportedSyncPackageEntity::class,
        SyncSourceSequenceEntity::class,
    ],
    version = CardVaultDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
internal abstract class CardVaultDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    abstract fun addressDao(): AddressDao

    abstract fun vaultFolderDao(): VaultFolderDao

    abstract fun vaultMetadataDao(): VaultMetadataDao

    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val SCHEMA_VERSION = 6
        const val DATABASE_NAME = "cardvault.db"

        fun create(context: Context): CardVaultDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CardVaultDatabase::class.java,
                DATABASE_NAME,
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                )
                .build()

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE cards ADD COLUMN versionVector BLOB NOT NULL DEFAULT X''",
                )
                db.execSQL(
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
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_order_state (
                        singletonId INTEGER NOT NULL,
                        versionVector BLOB NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(singletonId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_tombstones (
                        recordId TEXT NOT NULL,
                        versionVector BLOB NOT NULL,
                        deletedAt INTEGER NOT NULL,
                        PRIMARY KEY(recordId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS imported_sync_packages (
                        packageId TEXT NOT NULL,
                        sourceDeviceId TEXT NOT NULL,
                        exportSequence INTEGER NOT NULL,
                        importedAt INTEGER NOT NULL,
                        PRIMARY KEY(packageId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
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

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
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
                )
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE addresses ADD COLUMN versionVector BLOB NOT NULL DEFAULT X''",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS address_sync_order_state (
                        singletonId INTEGER NOT NULL,
                        versionVector BLOB NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(singletonId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS address_sync_tombstones (
                        recordId TEXT NOT NULL,
                        versionVector BLOB NOT NULL,
                        deletedAt INTEGER NOT NULL,
                        PRIMARY KEY(recordId)
                    )
                    """.trimIndent(),
                )
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vault_folders (
                        id TEXT NOT NULL,
                        ciphertext BLOB NOT NULL,
                        recordIv BLOB NOT NULL,
                        payloadSchemaVersion INTEGER NOT NULL,
                        cryptoVersion INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        versionVector BLOB NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS folder_sync_tombstones (
                        recordId TEXT NOT NULL,
                        versionVector BLOB NOT NULL,
                        deletedAt INTEGER NOT NULL,
                        PRIMARY KEY(recordId)
                    )
                    """.trimIndent(),
                )
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vault_folder_display_order (
                        collection TEXT NOT NULL,
                        orderedEntries TEXT NOT NULL,
                        PRIMARY KEY(collection)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
