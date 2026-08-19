package com.pdh.cardvault.desktop.model

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DesktopAddressTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `create normalizes fields and copy text uses postal and country`() {
        val address = DesktopAddress.create(
            nickname = "  虚构住所  ",
            detailedAddress = "  虚构大道 99 号  ",
            city = "  虚构城  ",
            other = "  虚构楼层  ",
            postalCode = "  000000  ",
            country = "  虚构国  ",
            style = CardCoverStyle.Default,
            sortOrder = 0,
            clock = clock,
        )

        assertEquals("虚构住所", address.nickname)
        assertEquals("虚构大道 99 号\n虚构楼层\n虚构城\n000000\n虚构国", address.copyText())
    }

    @Test
    fun `address and validation failures do not disclose address fields`() {
        val secretMarker = "SENSITIVE-ADDRESS-MARKER"
        val failure = assertFailsWith<IllegalArgumentException> {
            DesktopAddress.create(
                nickname = "",
                detailedAddress = secretMarker,
                city = "虚构城",
                other = "",
                postalCode = "000000",
                country = "虚构国",
                style = CardCoverStyle.Default,
                sortOrder = 0,
                clock = clock,
            )
        }
        assertFalse(failure.message.orEmpty().contains(secretMarker))

        val address = DesktopAddress.create(
            nickname = "虚构住所",
            detailedAddress = secretMarker,
            city = "虚构城",
            other = "",
            postalCode = "000000",
            country = "虚构国",
            style = CardCoverStyle.Default,
            sortOrder = 0,
            clock = clock,
        )
        assertFalse(address.toString().contains(secretMarker))
    }
}
