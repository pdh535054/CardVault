package com.pdh.cardvault.wallet

/** Opaque local display keys only; never card numbers or decrypted contents. */
object WalletDisplayOrder {
    fun folder(id: String): String = "f:$id"
    fun record(id: String): String = "r:$id"

    fun normalize(stored: List<String>, available: List<String>): List<String> {
        val valid = available.toSet()
        val retained = stored.filter { it in valid }.distinct()
        return available.filterNot { it in retained }.distinct() + retained
    }

    fun mergeVisible(all: List<String>, reordered: List<String>): List<String> {
        require(reordered.distinct().size == reordered.size && all.containsAll(reordered)) {
            "Invalid wallet display order."
        }
        val selected = reordered.toSet()
        val replacements = reordered.iterator()
        return all.map { if (it in selected) replacements.next() else it }
    }
}
