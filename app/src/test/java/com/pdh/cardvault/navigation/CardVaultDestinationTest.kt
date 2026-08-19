package com.pdh.cardvault.navigation

import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardVaultDestinationTest {
    @Test
    fun detailNavigationCarriesOnlyTheRecordId() {
        val recordId = UUID.randomUUID()
        val route = CardVaultDestination.cardDetailRoute(recordId)

        assertTrue(route == "card_detail/$recordId")
        assertTrue(route.substringAfterLast('/') == recordId.toString())
        assertFalse(route.contains('?'))
        assertFalse(route.contains('&'))
        assertTrue(
            listOf("cardNumber", "cvv", "nickname", "issuer", "notes").none { field ->
                route.contains(field, ignoreCase = true)
            },
        )
    }

    @Test
    fun detailRouteDeclaresExactlyOneIdArgument() {
        val argumentMarker = "{${CardVaultDestination.RECORD_ID_ARGUMENT}}"
        val routePattern = CardVaultDestination.CardDetail.route

        assertTrue(routePattern == "card_detail/$argumentMarker")
        assertTrue(routePattern.count { character -> character == '{' } == 1)
        assertTrue(routePattern.count { character -> character == '}' } == 1)
        assertFalse(routePattern.contains('?'))
    }

    @Test
    fun addressDetailNavigationCarriesOnlyRecordId() {
        val recordId = UUID.randomUUID()
        val route = CardVaultDestination.addressDetailRoute(recordId)

        assertTrue(route == "address_detail/$recordId")
        assertFalse(route.contains('?'))
        assertTrue(
            listOf("detailedAddress", "city", "postalCode", "nickname").none { field ->
                route.contains(field, ignoreCase = true)
            },
        )
    }

    @Test
    fun addressEditNavigationCarriesOnlyRecordId() {
        val recordId = UUID.randomUUID()
        val route = CardVaultDestination.editAddressRoute(recordId)

        assertTrue(route == "edit_address/$recordId")
        assertFalse(route.contains('?'))
        assertTrue(
            listOf("detailedAddress", "city", "postalCode", "country", "nickname").none { field ->
                route.contains(field, ignoreCase = true)
            },
        )
    }
}
