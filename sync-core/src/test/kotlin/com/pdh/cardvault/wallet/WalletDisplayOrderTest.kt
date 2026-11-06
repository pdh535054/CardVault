package com.pdh.cardvault.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletDisplayOrderTest {
    @Test fun mixedOrderPreservesHiddenLevels() {
        val all = listOf("f:parent", "r:one", "f:child", "r:two", "r:nested")
        assertEquals(listOf("r:two", "f:parent", "f:child", "r:one", "r:nested"),
            WalletDisplayOrder.mergeVisible(all, listOf("r:two", "f:parent", "r:one")))
    }
    @Test fun normalizationKeepsInterleavingAndAddsOnlyNewItems() {
        assertEquals(listOf("r:new", "r:one", "f:folder", "r:two"),
            WalletDisplayOrder.normalize(listOf("r:one", "f:folder", "r:two", "r:deleted"),
                listOf("f:folder", "r:new", "r:two", "r:one")))
    }
}
