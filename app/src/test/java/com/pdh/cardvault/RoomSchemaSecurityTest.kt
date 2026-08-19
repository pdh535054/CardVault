package com.pdh.cardvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSchemaSecurityTest {
    private val schema by lazy {
        TestProjectFiles.resolve(
            "schemas/com.pdh.cardvault.data.room.CardVaultDatabase/4.json",
        ).readText()
    }
    private val versionThreeSchema by lazy {
        TestProjectFiles.resolve(
            "schemas/com.pdh.cardvault.data.room.CardVaultDatabase/3.json",
        ).readText()
    }

    @Test
    fun cardTableContainsOnlyApprovedCiphertextAndManagementColumns() {
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
            columnsFor("cards"),
        )
    }

    @Test
    fun addressTableContainsOnlyCiphertextAndManagementColumns() {
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
            columnsFor("addresses"),
        )
    }

    @Test
    fun synchronizationTablesContainOnlyEncryptedKeyAndNonBusinessMetadata() {
        assertEquals(
            setOf(
                "singletonId",
                "vaultId",
                "deviceId",
                "keyEpoch",
                "encryptedSyncKey",
                "syncKeyIv",
                "syncKeyCryptoVersion",
                "exportSequence",
                "createdAt",
                "updatedAt",
            ),
            columnsFor("sync_vault_state"),
        )
        assertEquals(
            setOf("singletonId", "versionVector", "updatedAt"),
            columnsFor("sync_order_state"),
        )
        assertEquals(
            setOf("recordId", "versionVector", "deletedAt"),
            columnsFor("sync_tombstones"),
        )
        assertEquals(
            setOf("singletonId", "versionVector", "updatedAt"),
            columnsFor("address_sync_order_state"),
        )
        assertEquals(
            setOf("recordId", "versionVector", "deletedAt"),
            columnsFor("address_sync_tombstones"),
        )
        assertEquals(
            setOf("packageId", "sourceDeviceId", "exportSequence", "importedAt"),
            columnsFor("imported_sync_packages"),
        )
        assertEquals(
            setOf("sourceDeviceId", "highestSequence"),
            columnsFor("sync_source_sequences"),
        )
    }

    @Test
    fun vaultMetadataContainsOnlyWrappedKeyMaterialVersionsAndTimestamps() {
        assertEquals(
            setOf(
                "kekAliasVersion",
                "wrappedDek",
                "wrappingIv",
                "wrappingFormatVersion",
                "createdAt",
                "updatedAt",
            ),
            columnsFor("vault_metadata"),
        )
    }

    @Test
    fun exportedSchemaHasNoPlaintextBusinessFieldColumns() {
        val forbiddenColumns = setOf(
            "nickname",
            "issuerName",
            "cardNumber",
            "expiryMonth",
            "expiryYear",
            "saveCvv",
            "cvv",
            "cardTemplateId",
            "notes",
            "cardNetwork",
            "bin",
            "iin",
            "panPrefix",
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
            listOf("card", "hold", "er").joinToString(separator = ""),
            listOf("hold", "er", "Name").joinToString(separator = ""),
        )
        val allColumns = Regex("\\\"columnName\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .findAll(schema)
            .map { match -> match.groupValues[1] }
            .toSet()

        assertTrue(forbiddenColumns.intersect(allColumns).isEmpty())
        forbiddenColumns.forEach { forbidden ->
            assertFalse(allColumns.any { column -> column.equals(forbidden, ignoreCase = true) })
        }
    }

    @Test
    fun schemaVersionIsFourAndExplicitNonDestructiveMigrationsAreConfigured() {
        assertTrue(Regex("\\\"version\\\"\\s*:\\s*4").containsMatchIn(schema))
        assertTrue(
            TestProjectFiles.resolve(
                "schemas/com.pdh.cardvault.data.room.CardVaultDatabase/1.json",
            ).isFile,
        )
        val databaseSource = TestProjectFiles.resolve(
            "src/main/java/com/pdh/cardvault/data/room/CardVaultDatabase.kt",
        ).readText()
        val forbiddenFallback = listOf("fallback", "To", "Destructive", "Migration")
            .joinToString(separator = "")

        assertFalse(databaseSource.contains(forbiddenFallback, ignoreCase = true))
        assertTrue(databaseSource.contains("MIGRATION_1_2"))
        assertTrue(databaseSource.contains("MIGRATION_2_3"))
        assertTrue(databaseSource.contains("MIGRATION_3_4"))
        assertTrue(
            databaseSource.contains(
                ".addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)",
            ),
        )
        assertTrue(databaseSource.contains("ALTER TABLE cards ADD COLUMN versionVector"))
        assertTrue(databaseSource.contains("ALTER TABLE addresses ADD COLUMN versionVector"))
        assertTrue(databaseSource.contains("CREATE TABLE IF NOT EXISTS sync_source_sequences"))
        assertTrue(databaseSource.contains("CREATE TABLE IF NOT EXISTS addresses"))
        assertTrue(databaseSource.contains("CREATE TABLE IF NOT EXISTS address_sync_order_state"))
        assertTrue(databaseSource.contains("CREATE TABLE IF NOT EXISTS address_sync_tombstones"))
    }

    @Test
    fun versionThreeToFourAddressSchemaChangeIsStrictlyAdditive() {
        val versionThreeAddressColumns = columnsFor("addresses", versionThreeSchema)
        val versionFourAddressColumns = columnsFor("addresses", schema)

        assertEquals(versionThreeAddressColumns + "versionVector", versionFourAddressColumns)
        assertFalse(versionThreeSchema.contains("\"tableName\": \"address_sync_order_state\""))
        assertFalse(versionThreeSchema.contains("\"tableName\": \"address_sync_tombstones\""))
        assertTrue(schema.contains("\"tableName\": \"address_sync_order_state\""))
        assertTrue(schema.contains("\"tableName\": \"address_sync_tombstones\""))
    }

    private fun columnsFor(tableName: String, source: String = schema): Set<String> {
        val tableStart = source.indexOf("\"tableName\": \"$tableName\"")
        require(tableStart >= 0) { "Required Room table is absent from the exported schema." }
        val fieldsStart = source.indexOf("\"fields\": [", startIndex = tableStart)
        require(fieldsStart >= 0) { "Required Room fields block is absent." }
        val fieldsEnd = source.indexOf("\n        ],", startIndex = fieldsStart)
        require(fieldsEnd > fieldsStart) { "Room fields block is malformed." }
        return Regex("\\\"columnName\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .findAll(source.substring(fieldsStart, fieldsEnd))
            .map { match -> match.groupValues[1] }
            .toSet()
    }
}
