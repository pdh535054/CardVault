package com.pdh.cardvault.desktop.model

import java.time.Clock
import java.time.YearMonth
import java.util.UUID

data class DesktopCard(
    val id: String,
    val nickname: String,
    val issuerName: String,
    val cardNumber: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val cvv: String?,
    val notes: String,
    val cardTemplateId: String,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val folderId: String? = null,
) {
    init {
        require(runCatching { UUID.fromString(id) }.isSuccess) { "银行卡记录无效。" }
        require(nickname == nickname.trim() && nickname.codePointCount(0, nickname.length) in 1..50) {
            "银行卡记录无效。"
        }
        require(issuerName == issuerName.trim() && issuerName.codePointCount(0, issuerName.length) in 1..80) {
            "银行卡记录无效。"
        }
        require(cardNumber.length in 12..19 && cardNumber.all { it in '0'..'9' }) {
            "银行卡记录无效。"
        }
        require(expiryMonth in 1..12 && expiryYear in 1000..9999) { "银行卡记录无效。" }
        require(cvv == null || cvv.length in 3..4 && cvv.all { it in '0'..'9' }) {
            "银行卡记录无效。"
        }
        require(notes.codePointCount(0, notes.length) <= 1000) { "银行卡记录无效。" }
        require(cardTemplateId.isNotBlank() && cardTemplateId.length <= 100) { "银行卡记录无效。" }
        require(sortOrder >= 0 && createdAtEpochMillis >= 0 && updatedAtEpochMillis >= createdAtEpochMillis) {
            "银行卡记录无效。"
        }
        require(folderId == null || runCatching { UUID.fromString(folderId) }.isSuccess) { "银行卡记录无效。" }
    }

    val lastFour: String get() = cardNumber.takeLast(4)
    val maskedNumber: String get() = "•••• •••• •••• $lastFour"
    val groupedNumber: String get() = cardNumber.chunked(4).joinToString(" ")
    val expiryText: String get() = "%02d/%02d".format(expiryMonth, expiryYear % 100)
    val network: PaymentNetwork? get() = PaymentNetwork.detect(cardNumber)
    val style: CardCoverStyle get() = AndroidTemplateStyleCodec.decode(cardTemplateId)

    fun isExpired(clock: Clock = Clock.systemDefaultZone()): Boolean {
        val now = YearMonth.now(clock)
        return YearMonth.of(expiryYear, expiryMonth).isBefore(now)
    }

    override fun toString(): String = "DesktopCard(id=$id, sensitiveFields=redacted)"

    companion object {
        fun create(
            nickname: String,
            issuerName: String,
            cardNumber: String,
            expiryMonth: Int,
            expiryYear: Int,
            cvv: String?,
            notes: String,
            style: CardCoverStyle,
            sortOrder: Int,
            clock: Clock = Clock.systemUTC(),
        ): DesktopCard {
            val now = clock.millis()
            return DesktopCard(
                id = UUID.randomUUID().toString(),
                nickname = nickname.trim(),
                issuerName = issuerName.trim().ifBlank { "CardVault" },
                cardNumber = normalizeCardNumber(cardNumber)
                    ?: throw IllegalArgumentException("卡号格式无效。"),
                expiryMonth = expiryMonth,
                expiryYear = expiryYear,
                cvv = cvv?.takeIf(String::isNotBlank),
                notes = notes,
                cardTemplateId = AndroidTemplateStyleCodec.encode(style),
                sortOrder = sortOrder,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
        }

        fun normalizeCardNumber(value: String): String? {
            if (value.any { it !in '0'..'9' && it != ' ' && it != '-' }) return null
            return value.filter { it in '0'..'9' }.takeIf { it.length in 12..19 }
        }
    }
}

enum class PaymentNetwork(val label: String) {
    Visa("VISA"),
    Mastercard("mastercard"),
    UnionPay("银联"),
    ;

    companion object {
        fun detect(number: String): PaymentNetwork? = when {
            number.startsWith('4') -> Visa
            number.length == 16 && isMastercard(number) -> Mastercard
            number.startsWith("62") || number.startsWith("81") -> UnionPay
            else -> null
        }

        private fun isMastercard(number: String): Boolean {
            val prefix = number.take(6).toIntOrNull() ?: return false
            return prefix in 510_000..559_999 || prefix in 222_100..272_099
        }
    }
}

data class CardCoverStyle(
    val startArgb: Int,
    val endArgb: Int,
    val accentArgb: Int,
    val pattern: CardPattern,
    val typography: NicknameTypography,
    val nicknameArgb: Int,
    /** Preserves an imported Android template identifier until the user changes the style. */
    val sourceTemplateId: String? = null,
) {
    override fun toString(): String = "CardCoverStyle(pattern=$pattern, typography=$typography)"

    companion object {
        val Default = CardCoverStyle(
            startArgb = 0xFF292A2E.toInt(),
            endArgb = 0xFF090A0C.toInt(),
            accentArgb = 0xFFD7D9DF.toInt(),
            pattern = CardPattern.Contours,
            typography = NicknameTypography.Modern,
            nicknameArgb = 0xFFF7F7FA.toInt(),
        )
    }
}

enum class CardPattern(val displayName: String) {
    Plain("纯净"),
    Contours("等高线"),
    Shards("几何切面"),
    Grid("细格"),
    Orbits("环轨"),
    Waves("流线"),
    Ribbons("光带"),
    Stars("星尘"),
    Circuits("电路"),
    Halos("光晕"),
    Dots("点阵"),
    Arches("拱门"),
    Facets("晶面"),
    Frame("银框"),
    Loops("回环"),
    Tiles("砂砾砖纹"),
    Arcs("弧线"),
    Continuous("连续波"),
}

enum class NicknameTypography(val displayName: String) {
    Modern("现代"),
    Editorial("编辑体"),
    Signature("签名字"),
    Technical("技术体"),
    Wide("宽体"),
    Compact("紧凑"),
    Monogram("字标"),
    Split("分栏"),
    Orbit("倾斜"),
}

object CoverPalettes {
    data class Palette(
        val name: String,
        val startArgb: Int,
        val endArgb: Int,
        val accentArgb: Int,
        val foregroundArgb: Int,
    )

    val all = listOf(
        Palette("曜石", 0xFF292A2E.toInt(), 0xFF090A0C.toInt(), 0xFFD7D9DF.toInt(), 0xFFF7F7FA.toInt()),
        Palette("深海", 0xFF244B78.toInt(), 0xFF071526.toInt(), 0xFF87C7FF.toInt(), 0xFFF3F8FF.toInt()),
        Palette("翡翠", 0xFF247A68.toInt(), 0xFF082C29.toInt(), 0xFF92E5C5.toInt(), 0xFFF2FFF9.toInt()),
        Palette("石榴", 0xFF8F3948.toInt(), 0xFF2A0710.toInt(), 0xFFFFA7B2.toInt(), 0xFFFFF5F6.toInt()),
        Palette("香槟", 0xFFE4C98E.toInt(), 0xFFA67D3F.toInt(), 0xFFFFE7B7.toInt(), 0xFF21170B.toInt()),
        Palette("银灰", 0xFFF2F4F7.toInt(), 0xFF9DA5B0.toInt(), 0xFFFFFFFF.toInt(), 0xFF17191D.toInt()),
        Palette("紫晶", 0xFF8066B8.toInt(), 0xFF24143F.toInt(), 0xFFD7BDFF.toInt(), 0xFFF9F4FF.toInt()),
        Palette("陶土", 0xFFC46F55.toInt(), 0xFF54261D.toInt(), 0xFFFFC6A8.toInt(), 0xFFFFF7F2.toInt()),
        Palette("碳灰", 0xFF555C68.toInt(), 0xFF1B1F26.toInt(), 0xFFAAB2C0.toInt(), 0xFFF7F8FA.toInt()),
        Palette("钴蓝", 0xFF3568D4.toInt(), 0xFF10245E.toInt(), 0xFFA9C6FF.toInt(), 0xFFF7F9FF.toInt()),
        Palette("冰川", 0xFFC1EDF2.toInt(), 0xFF69A8BC.toInt(), 0xFFE8FCFF.toInt(), 0xFF102D38.toInt()),
        Palette("极光", 0xFF24A49E.toInt(), 0xFF073C49.toInt(), 0xFF8AF0DD.toInt(), 0xFFF1FFFD.toInt()),
        Palette("森林", 0xFF527A5E.toInt(), 0xFF173A2C.toInt(), 0xFFAAD8AF.toInt(), 0xFFF4FFF5.toInt()),
        Palette("珊瑚", 0xFFF18A75.toInt(), 0xFF87352F.toInt(), 0xFFFFC1AE.toInt(), 0xFFFFF7F4.toInt()),
        Palette("珍珠", 0xFFFFFAF3.toInt(), 0xFFD9CCC2.toInt(), 0xFFFFFFFF.toInt(), 0xFF29221F.toInt()),
        Palette("玫红", 0xFFB34D87.toInt(), 0xFF43162F.toInt(), 0xFFFFA7D0.toInt(), 0xFFFFF5FA.toInt()),
    )

    fun style(index: Int, pattern: CardPattern = CardPattern.Contours): CardCoverStyle {
        val palette = all[index.coerceIn(all.indices)]
        return CardCoverStyle(
            startArgb = palette.startArgb,
            endArgb = palette.endArgb,
            accentArgb = palette.accentArgb,
            pattern = pattern,
            typography = NicknameTypography.Modern,
            nicknameArgb = palette.foregroundArgb,
        )
    }
}
