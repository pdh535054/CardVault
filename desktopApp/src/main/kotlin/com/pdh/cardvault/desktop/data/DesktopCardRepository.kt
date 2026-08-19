package com.pdh.cardvault.desktop.data

import com.pdh.cardvault.desktop.model.DesktopCard
import com.pdh.cardvault.desktop.model.DesktopAddress
import java.time.Clock

class DesktopCardRepository(
    private val vault: EncryptedDesktopVault,
    private val clock: Clock = Clock.systemUTC(),
) : AutoCloseable {
    private var snapshot: DesktopVaultSnapshot = vault.load()

    @Synchronized
    fun cards(): List<DesktopCard> = snapshot.cards.sortedBy(DesktopCard::sortOrder)

    @Synchronized
    fun addresses(): List<DesktopAddress> = snapshot.addresses.sortedBy(DesktopAddress::sortOrder)

    @Synchronized
    fun snapshot(): DesktopVaultSnapshot = snapshot.copy(
        cards = snapshot.cards.toList(),
        addresses = snapshot.addresses.toList(),
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
        val next = imported.copy(cards = imported.cards.reindexed(), revision = snapshot.revision + 1)
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

    private fun persist(
        cards: List<DesktopCard>,
        state: DesktopSyncState,
        addresses: List<DesktopAddress> = snapshot.addresses,
    ) {
        val next = DesktopVaultSnapshot(
            cards = cards,
            revision = snapshot.revision + 1,
            syncState = state,
            addresses = addresses,
        )
        vault.save(next)
        snapshot = next
    }

    private fun List<DesktopCard>.reindexed(): List<DesktopCard> =
        mapIndexed { index, card -> card.copy(sortOrder = index) }

    private fun List<DesktopAddress>.reindexedAddresses(): List<DesktopAddress> =
        mapIndexed { index, address -> address.copy(sortOrder = index) }

    @Synchronized
    override fun close() {
        snapshot.syncState.sharedSyncKey?.close()
    }
}
