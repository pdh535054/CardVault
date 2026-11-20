package com.pdh.cardvault.desktop.model

import androidx.compose.runtime.Immutable
import java.time.Clock
import java.time.YearMonth
import java.util.UUID

@Immutable
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

    val lastFour: String by lazy(LazyThreadSafetyMode.NONE) { cardNumber.takeLast(4) }
    val maskedNumber: String by lazy(LazyThreadSafetyMode.NONE) { "•••• •••• •••• $lastFour" }
    val groupedNumber: String by lazy(LazyThreadSafetyMode.NONE) { cardNumber.chunked(4).joinToString(" ") }
    val expiryText: String by lazy(LazyThreadSafetyMode.NONE) { "%02d/%02d".format(expiryMonth, expiryYear % 100) }
    val network: PaymentNetwork? by lazy(LazyThreadSafetyMode.NONE) { PaymentNetwork.detect(cardNumber) }
    val style: CardCoverStyle by lazy(LazyThreadSafetyMode.NONE) { AndroidTemplateStyleCodec.decode(cardTemplateId) }

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

@Immutable
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
    Shards("斜向切片"),
    Grid("细格"),
    Orbits("星轨"),
    Waves("流光波纹"),
    Ribbons("飘带"),
    Stars("星点"),
    Circuits("回路"),
    Halos("光环"),
    Dots("点阵"),
    Arches("拱门"),
    Facets("几何切面"),
    Frame("内框"),
    Loops("环线"),
    Tiles("菱格"),
    Arcs("弧线"),
    Continuous("连续波"),
    Prism("棱镜折光"),
    Sunburst("曜光射线"),
    Mountains("远山层峦"),
    Lotus("几何莲华"),
    Constellation("星图连线"),
    Compass("星盘罗盘"),
    Scales("青海波纹"),
    Honeycomb("蜂巢晶格"),
    Chevrons("流矢斜纹"),
    Silk("丝绸流韵"),
    Beams("极光光幕"),
    Monolith("黑曜石碑"),
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
        Palette("曜石黑", 0xFF292A2E.toInt(), 0xFF090A0C.toInt(), 0xFFD7D9DF.toInt(), 0xFFF7F7FA.toInt()),
        Palette("午夜蓝", 0xFF244B78.toInt(), 0xFF071526.toInt(), 0xFF87C7FF.toInt(), 0xFFF3F8FF.toInt()),
        Palette("翡翠绿", 0xFF247A68.toInt(), 0xFF082C29.toInt(), 0xFF92E5C5.toInt(), 0xFFF2FFF9.toInt()),
        Palette("石榴红", 0xFF8F3948.toInt(), 0xFF2A0710.toInt(), 0xFFFFA7B2.toInt(), 0xFFFFF5F6.toInt()),
        Palette("香槟金", 0xFFE4C98E.toInt(), 0xFFA67D3F.toInt(), 0xFFFFE7B7.toInt(), 0xFF21170B.toInt()),
        Palette("雾银", 0xFFF2F4F7.toInt(), 0xFF9DA5B0.toInt(), 0xFFFFFFFF.toInt(), 0xFF17191D.toInt()),
        Palette("暮紫", 0xFF8066B8.toInt(), 0xFF24143F.toInt(), 0xFFD7BDFF.toInt(), 0xFFF9F4FF.toInt()),
        Palette("暖陶", 0xFFC46F55.toInt(), 0xFF54261D.toInt(), 0xFFFFC6A8.toInt(), 0xFFFFF7F2.toInt()),
        Palette("碳灰", 0xFF555C68.toInt(), 0xFF1B1F26.toInt(), 0xFFAAB2C0.toInt(), 0xFFF7F8FA.toInt()),
        Palette("钴蓝", 0xFF3568D4.toInt(), 0xFF10245E.toInt(), 0xFFA9C6FF.toInt(), 0xFFF7F9FF.toInt()),
        Palette("冰川蓝", 0xFFC1EDF2.toInt(), 0xFF69A8BC.toInt(), 0xFFE8FCFF.toInt(), 0xFF102D38.toInt()),
        Palette("极光青", 0xFF24A49E.toInt(), 0xFF073C49.toInt(), 0xFF8AF0DD.toInt(), 0xFFF1FFFD.toInt()),
        Palette("松柏绿", 0xFF527A5E.toInt(), 0xFF173A2C.toInt(), 0xFFAAD8AF.toInt(), 0xFFF4FFF5.toInt()),
        Palette("珊瑚橙", 0xFFF18A75.toInt(), 0xFF87352F.toInt(), 0xFFFFC1AE.toInt(), 0xFFFFF7F4.toInt()),
        Palette("珍珠白", 0xFFFFFAF3.toInt(), 0xFFD9CCC2.toInt(), 0xFFFFFFFF.toInt(), 0xFF29221F.toInt()),
        Palette("洋红", 0xFFB34D87.toInt(), 0xFF43162F.toInt(), 0xFFFFA7D0.toInt(), 0xFFFFF5FA.toInt()),
        Palette("钛金灰", 0xFF8A8E96.toInt(), 0xFF343840.toInt(), 0xFFE4E7ED.toInt(), 0xFFF8FAFC.toInt()),
        Palette("玫瑰金", 0xFFE8B4AC.toInt(), 0xFF9C5E58.toInt(), 0xFFFFDFD9.toInt(), 0xFF261210.toInt()),
        Palette("古铜金", 0xFF8C6239.toInt(), 0xFF2E1C0E.toInt(), 0xFFE8C090.toInt(), 0xFFFFF7ED.toInt()),
        Palette("祖母绿", 0xFF0F8A5F.toInt(), 0xFF042E20.toInt(), 0xFF7CF2C2.toInt(), 0xFFF0FFF8.toInt()),
        Palette("皇家蓝", 0xFF1E4F9C.toInt(), 0xFF061638.toInt(), 0xFF82B8FF.toInt(), 0xFFF2F7FF.toInt()),
        Palette("绯绒红", 0xFFB8293D.toInt(), 0xFF3B0812.toInt(), 0xFFFF9CA8.toInt(), 0xFFFFF5F6.toInt()),
        Palette("琥珀金", 0xFFD98A2B.toInt(), 0xFF572B08.toInt(), 0xFFFFD996.toInt(), 0xFFFFFAF0.toInt()),
        Palette("星夜靛", 0xFF3B358C.toInt(), 0xFF0E0B2E.toInt(), 0xFFB3AEFF.toInt(), 0xFFF5F4FF.toInt()),
        Palette("赛博青", 0xFF143A4A.toInt(), 0xFF060E17.toInt(), 0xFF00F0FF.toInt(), 0xFFEFFFFF.toInt()),
        Palette("樱雾粉", 0xFFFAD4DF.toInt(), 0xFFC7859A.toInt(), 0xFFFFF0F5.toInt(), 0xFF2B121B.toInt()),
        Palette("抹茶绿", 0xFFA8C99B.toInt(), 0xFF56784C.toInt(), 0xFFE5F7DC.toInt(), 0xFF152412.toInt()),
        Palette("暖砂岩", 0xFFD9C2A7.toInt(), 0xFF8C6F52.toInt(), 0xFFFFF2E2.toInt(), 0xFF261B11.toInt()),
    )

    val nicknameSwatches: List<Int> = listOf(
        0xFFFFF8E8.toInt(),
        0xFF111318.toInt(),
        0xFFFFD166.toInt(),
        0xFFE3EAF2.toInt(),
        0xFF87D9FF.toInt(),
        0xFF8CE8C5.toInt(),
        0xFFFF9DB6.toInt(),
        0xFFD7B7FF.toInt(),
        0xFFFFA080.toInt(),
        0xFFFFB347.toInt(),
        0xFF5CEEFF.toInt(),
        0xFFFF6B7A.toInt(),
        0xFFB8C2CC.toInt(),
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

object DesktopCardPresets {
    @Immutable
    data class Preset(
        val id: String,
        val title: String,
        val subtitle: String,
        val templateId: String,
        val style: CardCoverStyle,
    )

    private fun item(id: String, title: String, subtitle: String, templateId: String): Preset = Preset(
        id = id,
        title = title,
        subtitle = subtitle,
        templateId = templateId,
        style = AndroidTemplateStyleCodec.decode(templateId),
    )

    val all: List<Preset> = listOf(
        item("obsidian_prism", "黑金棱镜", "尊享金属", "custom:v2:obsidian:prism:editorial:gold"),
        item("sapphire_constellation", "星际深海", "浩瀚星图", "custom:v2:sapphire:constellation:wide:ice"),
        item("forest_mountains", "远山松烟", "东方意境", "custom:v2:forest:mountains:editorial:ivory"),
        item("emerald_scales", "翡翠青海", "传统纹样", "custom:v2:emerald:scales:split:mint"),
        item("cyber_circuit", "赛博脉冲", "霓虹科技", "custom:v2:cyber:circuit:mono:cyan"),
        item("titanium_frame", "钛金极简", "拉丝金属", "custom:v2:titanium:frame:modern:platinum"),
        item("champagne_lotus", "香槟莲华", "浮雕奢雅", "custom:v2:champagne:lotus:elegant:ink"),
        item("indigo_compass", "星夜罗盘", "秘境星盘", "custom:v2:indigo:compass:wide:gold"),
        item("aurora_beams", "极光光幕", "幻彩流光", "custom:v2:aurora:beams:orbit:mint"),
        item("sakura_silk", "樱雾丝绸", "柔雾轻奢", "custom:v2:sakura:silk:elegant:ink"),
        item("crimson_sunburst", "绯绒曜光", "炽烈锋芒", "custom:v2:crimson:sunburst:monogram:gold"),
        item("rosegold_ribbons", "玫瑰流韵", "丝缎金属", "custom:v2:rosegold:ribbons:editorial:ink"),
        item("carbon_monolith", "黑曜石碑", "建筑几何", "custom:v2:carbon:monolith:mono:silver"),
        item("amber_mountains", "琥珀层峦", "暮色山河", "custom:v2:amber:mountains:modern:ivory"),
        item("glacier_honeycomb", "冰川晶格", "纯净冷冽", "custom:v2:glacier:honeycomb:mono:ink"),
        item("violet_orbits", "紫晶星轨", "梦幻暗夜", "custom:v2:violet:orbits:orbit:violet"),
        item("bronze_arches", "古铜拱门", "典藏金库", "custom:v2:bronze:arches:editorial:gold"),
        item("matcha_waves", "抹茶微澜", "自然清新", "custom:v2:matcha:waves:light:ink"),
        item("pearl_tiles", "珍珠菱格", "温润白瓷", "custom:v2:pearl:tiles:wide:ink"),
        item("cobalt_chevrons", "钴蓝流矢", "动感竞速", "custom:v2:cobalt:chevrons:monogram:ice"),
    )
}
