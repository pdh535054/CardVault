package com.pdh.cardvault.desktop.data

import com.pdh.cardvault.desktop.model.DesktopCard
import com.pdh.cardvault.desktop.model.DesktopAddress
import com.pdh.cardvault.desktop.model.DesktopFolder
import com.pdh.cardvault.desktop.model.DesktopFolderKind
import java.time.Clock

class DesktopCardRepository(
    private val vault: EncryptedDesktopVault,
    private val clock: Clock = Clock.systemUTC(),
) : AutoCloseable {
    private var snapshot: DesktopVaultSnapshot = vault.load().normalizedFolderOrders()

    @Synchronized
    fun cards(): List<DesktopCard> = snapshot.cards.sortedBy(DesktopCard::sortOrder)

    @Synchronized
    fun addresses(): List<DesktopAddress> = snapshot.addresses.sortedBy(DesktopAddress::sortOrder)

    @Synchronized
    fun folders(kind: DesktopFolderKind): List<DesktopFolder> {
        val byId = snapshot.folders.filter { it.kind == kind }.associateBy(DesktopFolder::id)
        return folderOrder(kind).mapNotNull(byId::get)
    }

    @Synchronized
    fun folderOrder(kind: DesktopFolderKind): List<String?> = when (kind) {
        DesktopFolderKind.CARDS -> snapshot.cardFolderOrder
        DesktopFolderKind.ADDRESSES -> snapshot.addressFolderOrder
    }.toList()

    @Synchronized
    fun snapshot(): DesktopVaultSnapshot = snapshot.copy(
        cards = snapshot.cards.toList(),
        addresses = snapshot.addresses.toList(),
        folders = snapshot.folders.toList(),
        cardFolderOrder = snapshot.cardFolderOrder.toList(),
        addressFolderOrder = snapshot.addressFolderOrder.toList(),
    )

    @Synchronized
    fun replaceAll(cards: List<DesktopCard>) {
        val state = snapshot.syncState.copy(
            recordVectors = cards.associate { card ->
                card.id to (snapshot.syncState.recordVectors[card.id] ?: VersionVector())
                    .increment(snapshot.syncState.deviceId)
            },
            tombstones = emptyMap(),
        )
        persist(cards.reindexed(), state)
    }

    @Synchronized
    fun replaceSnapshot(imported: DesktopVaultSnapshot) {
        val next = imported.copy(
            cards = imported.cards.reindexed(),
            addresses = imported.addresses.reindexedAddresses(),
            revision = snapshot.revision + 1,
            cardFolderOrder = normalizeFolderOrder(
                currentOrder = snapshot.cardFolderOrder,
                importedOrder = imported.cardFolderOrder,
                folderIds = imported.folders.filter { it.kind == DesktopFolderKind.CARDS }.map(DesktopFolder::id),
            ),
            addressFolderOrder = normalizeFolderOrder(
                currentOrder = snapshot.addressFolderOrder,
                importedOrder = imported.addressFolderOrder,
                folderIds = imported.folders.filter { it.kind == DesktopFolderKind.ADDRESSES }.map(DesktopFolder::id),
            ),
        )
        val previousKey = snapshot.syncState.sharedSyncKey
        val nextKey = next.syncState.sharedSyncKey
        try {
            vault.save(next)
        } catch (error: Throwable) {
            if (nextKey !== previousKey) nextKey?.close()
            throw error
        }
        snapshot = next
        if (previousKey !== nextKey) previousKey?.close()
    }

    @Synchronized
    fun add(card: DesktopCard) {
        val state = snapshot.syncState.copy(
            recordVectors = snapshot.syncState.recordVectors +
                (card.id to VersionVector().increment(snapshot.syncState.deviceId)),
            tombstones = snapshot.syncState.tombstones - card.id,
            orderVector = snapshot.syncState.orderVector.increment(snapshot.syncState.deviceId),
        )
        persist((listOf(card) + snapshot.cards).reindexed(), state)
    }

    @Synchronized
    fun update(card: DesktopCard): Boolean {
        if (snapshot.cards.none { it.id == card.id }) return false
        val vector = (snapshot.syncState.recordVectors[card.id] ?: VersionVector())
            .increment(snapshot.syncState.deviceId)
        persist(
            snapshot.cards.map { existing -> if (existing.id == card.id) card else existing }.reindexed(),
            snapshot.syncState.copy(recordVectors = snapshot.syncState.recordVectors + (card.id to vector)),
        )
        return true
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val filtered = snapshot.cards.filterNot { it.id == id }
        if (filtered.size == snapshot.cards.size) return false
        val vector = (snapshot.syncState.recordVectors[id] ?: VersionVector())
            .increment(snapshot.syncState.deviceId)
        val tombstone = DesktopTombstone(id, clock.millis(), vector)
        persist(
            filtered.reindexed(),
            snapshot.syncState.copy(
                recordVectors = snapshot.syncState.recordVectors - id,
                tombstones = snapshot.syncState.tombstones + (id to tombstone),
                orderVector = snapshot.syncState.orderVector.increment(snapshot.syncState.deviceId),
            ),
        )
        return true
    }

    @Synchronized
    fun move(id: String, targetIndex: Int): Boolean {
        val ordered = snapshot.cards.sortedBy(DesktopCard::sortOrder).toMutableList()
        val from = ordered.indexOfFirst { it.id == id }
        if (from == -1 || targetIndex !in ordered.indices || from == targetIndex) return false
        val moved = ordered.removeAt(from)
        ordered.add(targetIndex, moved)
        persist(
            ordered.reindexed(),
            snapshot.syncState.copy(
                orderVector = snapshot.syncState.orderVector.increment(snapshot.syncState.deviceId),
            ),
        )
        return true
    }

    @Synchronized
    fun addAddress(address: DesktopAddress) {
        val state = snapshot.syncState.copy(
            addressRecordVectors = snapshot.syncState.addressRecordVectors +
                (address.id to VersionVector().increment(snapshot.syncState.deviceId)),
            addressTombstones = snapshot.syncState.addressTombstones - address.id,
            addressOrderVector = snapshot.syncState.addressOrderVector.increment(snapshot.syncState.deviceId),
        )
        persist(
            cards = snapshot.cards,
            state = state,
            addresses = (listOf(address) + snapshot.addresses).reindexedAddresses(),
        )
    }

    @Synchronized
    fun updateAddress(address: DesktopAddress): Boolean {
        if (snapshot.addresses.none { it.id == address.id }) return false
        val vector = (snapshot.syncState.addressRecordVectors[address.id] ?: VersionVector())
            .increment(snapshot.syncState.deviceId)
        persist(
            cards = snapshot.cards,
            state = snapshot.syncState.copy(
                addressRecordVectors = snapshot.syncState.addressRecordVectors + (address.id to vector),
            ),
            addresses = snapshot.addresses
                .map { existing -> if (existing.id == address.id) address else existing }
                .reindexedAddresses(),
        )
        return true
    }

    @Synchronized
    fun deleteAddress(id: String): Boolean {
        val filtered = snapshot.addresses.filterNot { it.id == id }
        if (filtered.size == snapshot.addresses.size) return false
        val vector = (snapshot.syncState.addressRecordVectors[id] ?: VersionVector())
            .increment(snapshot.syncState.deviceId)
        val tombstone = DesktopTombstone(id, clock.millis(), vector)
        persist(
            cards = snapshot.cards,
            state = snapshot.syncState.copy(
                addressRecordVectors = snapshot.syncState.addressRecordVectors - id,
                addressTombstones = snapshot.syncState.addressTombstones + (id to tombstone),
                addressOrderVector = snapshot.syncState.addressOrderVector.increment(snapshot.syncState.deviceId),
            ),
            addresses = filtered.reindexedAddresses(),
        )
        return true
    }

    @Synchronized
    fun moveAddress(id: String, targetIndex: Int): Boolean {
        val ordered = snapshot.addresses.sortedBy(DesktopAddress::sortOrder).toMutableList()
        val from = ordered.indexOfFirst { it.id == id }
        if (from == -1 || targetIndex !in ordered.indices || from == targetIndex) return false
        val moved = ordered.removeAt(from)
        ordered.add(targetIndex, moved)
        persist(
            cards = snapshot.cards,
            state = snapshot.syncState.copy(
                addressOrderVector = snapshot.syncState.addressOrderVector.increment(snapshot.syncState.deviceId),
            ),
            addresses = ordered.reindexedAddresses(),
        )
        return true
    }

    @Synchronized
    fun createFolder(name: String, kind: DesktopFolderKind): DesktopFolder {
        val folder = DesktopFolder.create(name, kind, clock)
        val vector = VersionVector().increment(snapshot.syncState.deviceId)
        persist(
            cards = snapshot.cards,
            state = snapshot.syncState.copy(
                folderRecordVectors = snapshot.syncState.folderRecordVectors + (folder.id to vector),
                folderTombstones = snapshot.syncState.folderTombstones - folder.id,
            ),
            folders = snapshot.folders + folder,
            cardFolderOrder = if (kind == DesktopFolderKind.CARDS) snapshot.cardFolderOrder + folder.id else snapshot.cardFolderOrder,
            addressFolderOrder = if (kind == DesktopFolderKind.ADDRESSES) snapshot.addressFolderOrder + folder.id else snapshot.addressFolderOrder,
        )
        return folder
    }

    @Synchronized
    fun renameFolder(id: String, name: String): Boolean {
        val normalized = name.trim()
        require(normalized.codePointCount(0, normalized.length) in 1..50) { "文件夹名称无效。" }
        val existing = snapshot.folders.firstOrNull { it.id == id } ?: return false
        val vector = (snapshot.syncState.folderRecordVectors[id] ?: VersionVector())
            .increment(snapshot.syncState.deviceId)
        val now = maxOf(clock.millis(), existing.updatedAtEpochMillis + 1)
        persist(
            cards = snapshot.cards,
            state = snapshot.syncState.copy(
                folderRecordVectors = snapshot.syncState.folderRecordVectors + (id to vector),
            ),
            folders = snapshot.folders.map { if (it.id == id) it.copy(name = normalized, updatedAtEpochMillis = now) else it },
        )
        return true
    }

    @Synchronized
    fun deleteFolder(id: String): Boolean {
        val existing = snapshot.folders.firstOrNull { it.id == id } ?: return false
        val vector = (snapshot.syncState.folderRecordVectors[id] ?: VersionVector())
            .increment(snapshot.syncState.deviceId)
        val tombstone = DesktopTombstone(id, maxOf(clock.millis(), existing.updatedAtEpochMillis), vector)
        val affectedCards = snapshot.cards.filter { it.folderId == id }
        val affectedAddresses = snapshot.addresses.filter { it.folderId == id }
        val cardVectors = snapshot.syncState.recordVectors + affectedCards.associate { card ->
            card.id to (snapshot.syncState.recordVectors[card.id] ?: VersionVector())
                .increment(snapshot.syncState.deviceId)
        }
        val addressVectors = snapshot.syncState.addressRecordVectors + affectedAddresses.associate { address ->
            address.id to (snapshot.syncState.addressRecordVectors[address.id] ?: VersionVector())
                .increment(snapshot.syncState.deviceId)
        }
        persist(
            cards = snapshot.cards.map { if (it.folderId == id) it.copy(folderId = null) else it },
            state = snapshot.syncState.copy(
                folderRecordVectors = snapshot.syncState.folderRecordVectors - id,
                folderTombstones = snapshot.syncState.folderTombstones + (id to tombstone),
                recordVectors = cardVectors,
                addressRecordVectors = addressVectors,
            ),
            addresses = snapshot.addresses.map { if (it.folderId == id) it.copy(folderId = null) else it },
            folders = snapshot.folders.filterNot { it.id == id },
            cardFolderOrder = snapshot.cardFolderOrder.filterNot { it == id },
            addressFolderOrder = snapshot.addressFolderOrder.filterNot { it == id },
        )
        return true
    }

    @Synchronized
    fun reorderFolders(kind: DesktopFolderKind, orderedIds: List<String?>): Boolean {
        val expectedIds = snapshot.folders.filter { it.kind == kind }.map(DesktopFolder::id)
        if (!isValidFolderOrder(orderedIds, expectedIds)) return false
        val current = folderOrder(kind)
        if (current == orderedIds) return true
        persist(
            cards = snapshot.cards,
            state = snapshot.syncState,
            cardFolderOrder = if (kind == DesktopFolderKind.CARDS) orderedIds else snapshot.cardFolderOrder,
            addressFolderOrder = if (kind == DesktopFolderKind.ADDRESSES) orderedIds else snapshot.addressFolderOrder,
        )
        return true
    }

    @Synchronized
    fun moveCardToFolder(cardId: String, folderId: String?): Boolean {
        if (folderId != null && snapshot.folders.none { it.id == folderId && it.kind == DesktopFolderKind.CARDS }) return false
        val card = snapshot.cards.firstOrNull { it.id == cardId } ?: return false
        if (card.folderId == folderId) return true
        val vector = (snapshot.syncState.recordVectors[cardId] ?: VersionVector())
            .increment(snapshot.syncState.deviceId)
        persist(
            snapshot.cards.map { if (it.id == cardId) it.copy(folderId = folderId, updatedAtEpochMillis = maxOf(clock.millis(), it.updatedAtEpochMillis + 1)) else it },
            snapshot.syncState.copy(recordVectors = snapshot.syncState.recordVectors + (cardId to vector)),
        )
        return true
    }

    @Synchronized
    fun moveAddressToFolder(addressId: String, folderId: String?): Boolean {
        if (folderId != null && snapshot.folders.none { it.id == folderId && it.kind == DesktopFolderKind.ADDRESSES }) return false
        val address = snapshot.addresses.firstOrNull { it.id == addressId } ?: return false
        if (address.folderId == folderId) return true
        val vector = (snapshot.syncState.addressRecordVectors[addressId] ?: VersionVector())
            .increment(snapshot.syncState.deviceId)
        persist(
            cards = snapshot.cards,
            state = snapshot.syncState.copy(addressRecordVectors = snapshot.syncState.addressRecordVectors + (addressId to vector)),
            addresses = snapshot.addresses.map { if (it.id == addressId) it.copy(folderId = folderId, updatedAtEpochMillis = maxOf(clock.millis(), it.updatedAtEpochMillis + 1)) else it },
        )
        return true
    }

    private fun persist(
        cards: List<DesktopCard>,
        state: DesktopSyncState,
        addresses: List<DesktopAddress> = snapshot.addresses,
        folders: List<DesktopFolder> = snapshot.folders,
        cardFolderOrder: List<String?> = snapshot.cardFolderOrder,
        addressFolderOrder: List<String?> = snapshot.addressFolderOrder,
    ) {
        val cardFolderIds = folders.filter { it.kind == DesktopFolderKind.CARDS }.map(DesktopFolder::id)
        val addressFolderIds = folders.filter { it.kind == DesktopFolderKind.ADDRESSES }.map(DesktopFolder::id)
        val next = DesktopVaultSnapshot(
            cards = cards,
            revision = snapshot.revision + 1,
            syncState = state,
            addresses = addresses,
            folders = folders,
            cardFolderOrder = normalizeFolderOrder(cardFolderOrder, emptyList(), cardFolderIds),
            addressFolderOrder = normalizeFolderOrder(addressFolderOrder, emptyList(), addressFolderIds),
        )
        vault.save(next)
        snapshot = next
    }

    private fun List<DesktopCard>.reindexed(): List<DesktopCard> =
        mapIndexed { index, card -> card.copy(sortOrder = index) }

    private fun List<DesktopAddress>.reindexedAddresses(): List<DesktopAddress> =
        mapIndexed { index, address -> address.copy(sortOrder = index) }

    private fun DesktopVaultSnapshot.normalizedFolderOrders(): DesktopVaultSnapshot = copy(
        cardFolderOrder = normalizeFolderOrder(
            currentOrder = cardFolderOrder,
            importedOrder = emptyList(),
            folderIds = folders.filter { it.kind == DesktopFolderKind.CARDS }.map(DesktopFolder::id),
        ),
        addressFolderOrder = normalizeFolderOrder(
            currentOrder = addressFolderOrder,
            importedOrder = emptyList(),
            folderIds = folders.filter { it.kind == DesktopFolderKind.ADDRESSES }.map(DesktopFolder::id),
        ),
    )

    private fun normalizeFolderOrder(
        currentOrder: List<String?>,
        importedOrder: List<String?>,
        folderIds: List<String>,
    ): List<String?> {
        val available = folderIds.toSet()
        val result = mutableListOf<String?>()
        (currentOrder + importedOrder).forEach { id ->
            if ((id == null || id in available) && id !in result) result += id
        }
        if (null !in result) result += null
        folderIds.forEach { id -> if (id !in result) result += id }
        return result
    }

    private fun isValidFolderOrder(order: List<String?>, folderIds: List<String>): Boolean =
        order.size == folderIds.size + 1 &&
            order.count { it == null } == 1 &&
            order.filterNotNull().distinct().size == folderIds.size &&
            order.filterNotNull().toSet() == folderIds.toSet()

    @Synchronized
    override fun close() {
        snapshot.syncState.sharedSyncKey?.close()
    }
}
