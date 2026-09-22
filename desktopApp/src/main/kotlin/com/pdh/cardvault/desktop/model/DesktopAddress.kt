package com.pdh.cardvault.desktop.model

import java.time.Clock
import java.util.UUID

enum class DesktopAddressCopyPart {
    Complete,
    DetailedAddress,
    City,
    Other,
    PostalCode,
    Country,
}

data class DesktopAddress(
    val id: String,
    val nickname: String,
    val detailedAddress: String,
    val city: String,
    val other: String,
    val postalCode: String,
    val country: String,
    val cardTemplateId: String,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val folderId: String? = null,
) {
    init {
        require(runCatching { UUID.fromString(id) }.isSuccess) { "地址记录无效。" }
        require(nickname.isTrimmedAndLengthIn(1..50)) { "地址记录无效。" }
        require(detailedAddress.isTrimmedAndLengthIn(1..500)) { "地址记录无效。" }
        require(city.isTrimmedAndLengthIn(1..100)) { "地址记录无效。" }
        require(other == other.trim() && other.codePointLength() <= 200) { "地址记录无效。" }
        require(postalCode.isTrimmedAndLengthIn(1..20)) { "地址记录无效。" }
        // Empty is accepted only for records imported from the pre-country Android schema.
        require(country == country.trim() && country.codePointLength() <= 100) { "地址记录无效。" }
        require(cardTemplateId.isNotBlank() && cardTemplateId.length <= 400) { "地址记录无效。" }
        require(sortOrder >= 0 && createdAtEpochMillis >= 0 && updatedAtEpochMillis >= createdAtEpochMillis) {
            "地址记录无效。"
        }
        require(folderId == null || runCatching { UUID.fromString(folderId) }.isSuccess) { "地址记录无效。" }
    }

    val style: CardCoverStyle get() = AndroidTemplateStyleCodec.decode(cardTemplateId)

    fun copyText(part: DesktopAddressCopyPart = DesktopAddressCopyPart.Complete): String = when (part) {
        DesktopAddressCopyPart.Complete -> listOf(detailedAddress, other, city, postalCode, country)
            .filter(String::isNotBlank)
            .joinToString("\n")
        DesktopAddressCopyPart.DetailedAddress -> detailedAddress
        DesktopAddressCopyPart.City -> city
        DesktopAddressCopyPart.Other -> other
        DesktopAddressCopyPart.PostalCode -> postalCode
        DesktopAddressCopyPart.Country -> country
    }

    override fun toString(): String = "DesktopAddress(id=$id, sensitiveFields=redacted)"

    companion object {
        fun create(
            nickname: String,
            detailedAddress: String,
            city: String,
            other: String,
            postalCode: String,
            country: String,
            style: CardCoverStyle,
            sortOrder: Int,
            clock: Clock = Clock.systemUTC(),
        ): DesktopAddress {
            val now = clock.millis()
            return DesktopAddress(
                id = UUID.randomUUID().toString(),
                nickname = nickname.trim(),
                detailedAddress = detailedAddress.trim(),
                city = city.trim(),
                other = other.trim(),
                postalCode = postalCode.trim(),
                country = country.trim(),
                cardTemplateId = AndroidTemplateStyleCodec.encode(style),
                sortOrder = sortOrder,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
        }
    }
}

private fun String.codePointLength(): Int = codePointCount(0, length)

private fun String.isTrimmedAndLengthIn(range: IntRange): Boolean =
    this == trim() && codePointLength() in range
