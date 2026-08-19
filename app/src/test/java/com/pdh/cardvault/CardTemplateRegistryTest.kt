package com.pdh.cardvault

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pdh.cardvault.ui.card.CardTemplateRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTemplateRegistryTest {
    @Test
    fun registryOffersExpandedUniqueCoverAndNicknameChoices() {
        val colors = CardTemplateRegistry.colors
        val patterns = CardTemplateRegistry.patterns
        val nicknameStyles = CardTemplateRegistry.nicknameStyles
        val nicknameColors = CardTemplateRegistry.nicknameColors

        assertEquals(16, colors.size)
        assertEquals(colors.size, colors.map { it.id }.distinct().size)
        assertEquals(18, patterns.size)
        assertEquals(patterns.size, patterns.map { it.id }.distinct().size)
        assertEquals(9, nicknameStyles.size)
        assertEquals(nicknameStyles.size, nicknameStyles.map { it.id }.distinct().size)
        assertEquals(10, nicknameColors.size)
        assertEquals(nicknameColors.size, nicknameColors.map { it.id }.distinct().size)
    }

    @Test
    fun everyColorAndPatternCombinationHasAStableValidatedId() {
        val combinations = CardTemplateRegistry.colors.flatMap { color ->
            CardTemplateRegistry.patterns.map { pattern ->
                CardTemplateRegistry.composeTemplateId(color.id, pattern.id)
            }
        }

        assertEquals(288, combinations.size)
        assertEquals(combinations.size, combinations.distinct().size)
        combinations.forEach { id ->
            val template = requireNotNull(CardTemplateRegistry.findById(id))
            val parts = id.split(':')
            assertEquals(id, template.id)
            assertEquals("v2", parts[1])
            assertEquals(parts[2], template.colorId)
            assertEquals(parts[3], template.patternId)
            assertEquals(CardTemplateRegistry.DEFAULT_NICKNAME_STYLE_ID, template.nicknameStyle.id)
            assertEquals(CardTemplateRegistry.DEFAULT_NICKNAME_COLOR_ID, template.nicknameColor.id)
            assertTrue(template.issuerStorageLabel.isNotBlank())
        }
    }

    @Test
    fun everyNicknameStyleAndColorCombinationResolves() {
        CardTemplateRegistry.nicknameStyles.forEach { style ->
            CardTemplateRegistry.nicknameColors.forEach { color ->
                val id = CardTemplateRegistry.composeTemplateId(
                    colorId = "obsidian",
                    patternId = "contours",
                    nicknameStyleId = style.id,
                    nicknameColorId = color.id,
                )
                val template = requireNotNull(CardTemplateRegistry.findById(id))
                assertEquals(style.id, template.nicknameStyle.id)
                assertEquals(color.id, template.nicknameColor.id)
            }
        }
    }

    @Test
    fun customRgbColorUsesVersionThreeAndRoundTripsWithoutAlpha() {
        val colorId = CardTemplateRegistry.customColorId(Color(0x4012ABEF))
        val id = CardTemplateRegistry.composeTemplateId(
            colorId = colorId,
            patternId = "contours",
            nicknameStyleId = "monogram",
            nicknameColorId = "gold",
        )
        val template = requireNotNull(CardTemplateRegistry.findById(id))

        assertEquals("rgb-12ABEF", colorId)
        assertEquals("custom:v3:rgb-12ABEF:contours:monogram:gold", id)
        assertEquals(id, template.id)
        assertEquals(0xFF12ABEF.toInt(), template.gradientStart.toArgb())
        assertEquals(0xFF4BBFF3.toInt(), template.gradientEnd.toArgb())
        assertEquals(0xFF0C719E.toInt(), template.accent.toArgb())
        assertEquals(Color.Black.toArgb(), template.foreground.toArgb())
        assertEquals("monogram", template.nicknameStyle.id)
        assertEquals("gold", template.nicknameColor.id)
        assertEquals(colorId, CardTemplateRegistry.selectionFor(id).colorId)
    }

    @Test
    fun customNicknameRgbWithPresetCoverUsesVersionThreeAndRoundTrips() {
        val nicknameColorId = CardTemplateRegistry.customColorId(Color(0x4012ABEF))
        val id = CardTemplateRegistry.composeTemplateId(
            colorId = "obsidian",
            patternId = "contours",
            nicknameStyleId = "editorial",
            nicknameColorId = nicknameColorId,
        )
        val template = requireNotNull(CardTemplateRegistry.findById(id))
        val selection = CardTemplateRegistry.selectionFor(id)

        assertEquals("rgb-12ABEF", nicknameColorId)
        assertEquals("custom:v3:obsidian:contours:editorial:rgb-12ABEF", id)
        assertEquals(id, template.id)
        assertEquals("obsidian", template.colorId)
        assertEquals(nicknameColorId, template.nicknameColor.id)
        assertEquals(0xFF12ABEF.toInt(), requireNotNull(template.nicknameColor.color).toArgb())
        assertEquals("obsidian", selection.colorId)
        assertEquals(nicknameColorId, selection.nicknameColorId)
    }

    @Test
    fun customCoverAndNicknameRgbRoundTripIndependently() {
        val coverColorId = CardTemplateRegistry.customColorId(Color(0xFF102030))
        val nicknameColorId = CardTemplateRegistry.customColorId(Color(0xFFA1B2C3))
        val id = CardTemplateRegistry.composeTemplateId(
            colorId = coverColorId,
            patternId = "continuous",
            nicknameStyleId = "editorial",
            nicknameColorId = nicknameColorId,
        )
        val template = requireNotNull(CardTemplateRegistry.findById(id))
        val selection = CardTemplateRegistry.selectionFor(id)

        assertEquals(
            "custom:v3:rgb-102030:continuous:editorial:rgb-A1B2C3",
            id,
        )
        assertEquals(id, template.id)
        assertEquals(0xFF102030.toInt(), template.gradientStart.toArgb())
        assertEquals(0xFFA1B2C3.toInt(), requireNotNull(template.nicknameColor.color).toArgb())
        assertEquals(coverColorId, selection.colorId)
        assertEquals(nicknameColorId, selection.nicknameColorId)
    }

    @Test
    fun malformedNicknameRgbWrongVersionAndAllPresetV3IdsAreRejected() {
        listOf(
            "custom:v3:obsidian:contours:modern:rgb-12abef",
            "custom:v3:obsidian:contours:modern:rgb-12ABE",
            "custom:v3:obsidian:contours:modern:rgb-FF12ABEF",
            "custom:v3:obsidian:contours:modern:%2312ABEF",
            "custom:v3:rgb-123456:contours:modern:rgb-12abef",
            "custom:v2:obsidian:contours:modern:rgb-12ABEF",
            "custom:v3:obsidian:contours:modern:gold",
        ).forEach { id -> assertNull(CardTemplateRegistry.findById(id)) }
    }

    @Test
    fun composeTemplateIdRejectsMalformedNicknameRgb() {
        listOf(
            "rgb-12abef",
            "rgb-12ABE",
            "rgb-FF12ABEF",
            "#12ABEF",
        ).forEach { nicknameColorId ->
            assertThrows(IllegalArgumentException::class.java) {
                CardTemplateRegistry.composeTemplateId(
                    colorId = "obsidian",
                    patternId = "contours",
                    nicknameColorId = nicknameColorId,
                )
            }
        }
    }

    @Test
    fun malformedOrWrongVersionCustomRgbColorsAreRejected() {
        listOf(
            "custom:v3:rgb-12abef:contours:modern:auto",
            "custom:v3:rgb-12ABE:contours:modern:auto",
            "custom:v3:rgb-FF12ABEF:contours:modern:auto",
            "custom:v3:%2312ABEF:contours:modern:auto",
            "custom:v3:obsidian:contours:modern:auto",
            "custom:v2:rgb-12ABEF:contours:modern:auto",
        ).forEach { id -> assertNull(CardTemplateRegistry.findById(id)) }
    }

    @Test
    fun versionThreeRenderingKeepsBrightAndDarkBoundaryColorsStable() {
        val saturated = requireNotNull(
            CardTemplateRegistry.findById(
                "custom:v3:rgb-00B432:contours:modern:auto",
            ),
        )
        val black = requireNotNull(
            CardTemplateRegistry.findById(
                "custom:v3:rgb-000000:contours:modern:auto",
            ),
        )
        val white = requireNotNull(
            CardTemplateRegistry.findById(
                "custom:v3:rgb-FFFFFF:contours:modern:auto",
            ),
        )

        assertEquals(0xFF3DC663.toInt(), saturated.gradientEnd.toArgb())
        assertEquals(Color.Black.toArgb(), saturated.foreground.toArgb())
        assertEquals(0xFF000000.toInt(), black.gradientEnd.toArgb())
        assertEquals(0xFF3D3D3D.toInt(), black.accent.toArgb())
        assertEquals(Color.White.toArgb(), black.foreground.toArgb())
        assertEquals(0xFFA8A8A8.toInt(), white.gradientEnd.toArgb())
        assertEquals(Color.Black.toArgb(), white.foreground.toArgb())
    }

    @Test
    fun composeTemplateIdRejectsSelectionsTheRegistryCannotResolve() {
        listOf(
            { CardTemplateRegistry.composeTemplateId("rgb-12abef", "contours") },
            { CardTemplateRegistry.composeTemplateId("obsidian", "unknown") },
            {
                CardTemplateRegistry.composeTemplateId(
                    "obsidian",
                    "contours",
                    nicknameStyleId = "unknown",
                )
            },
            {
                CardTemplateRegistry.composeTemplateId(
                    "obsidian",
                    "contours",
                    nicknameColorId = "unknown",
                )
            },
        ).forEach { invalidSelection ->
            assertThrows(IllegalArgumentException::class.java) { invalidSelection() }
        }
    }

    @Test
    fun unknownOrMalformedCustomIdsAreRejected() {
        assertNull(CardTemplateRegistry.findById("custom:unknown:plain"))
        assertNull(CardTemplateRegistry.findById("custom:obsidian:unknown"))
        assertNull(CardTemplateRegistry.findById("custom:obsidian"))
        assertNull(CardTemplateRegistry.findById("custom:v2:obsidian:plain:unknown:auto"))
        assertNull(CardTemplateRegistry.findById("custom:v2:obsidian:plain:modern:unknown"))
        assertNull(CardTemplateRegistry.findById("not-a-template"))
    }

    @Test
    fun previousThreePartCustomIdsRemainCompatible() {
        val previous = requireNotNull(
            CardTemplateRegistry.findById("custom:midnight:orbits"),
        )

        assertEquals("midnight", previous.colorId)
        assertEquals("orbits", previous.patternId)
        assertEquals(CardTemplateRegistry.DEFAULT_NICKNAME_STYLE_ID, previous.nicknameStyle.id)
        assertEquals(CardTemplateRegistry.DEFAULT_NICKNAME_COLOR_ID, previous.nicknameColor.id)
    }

    @Test
    fun legacyPersistedIdsStillResolveToLocalColorAndPatternStyles() {
        assertEquals(47, CardTemplateRegistry.legacyTemplateIds.size)
        CardTemplateRegistry.legacyTemplateIds.forEach { legacyId ->
            val template = requireNotNull(CardTemplateRegistry.findById(legacyId))
            assertNotNull(template)
            assertEquals(legacyId, template.id)
            assertTrue(template.colorId in CardTemplateRegistry.colors.map { it.id })
            assertTrue(template.patternId in CardTemplateRegistry.patterns.map { it.id })
        }
        val fixedLegacyStyle = requireNotNull(
            CardTemplateRegistry.findById("redotpay_original"),
        )
        assertEquals("obsidian", fixedLegacyStyle.colorId)
        assertEquals("contours", fixedLegacyStyle.patternId)
    }

    @Test
    fun defaultAndCuratedStylesAlwaysUseTheCustomLocalFormat() {
        assertEquals(
            "custom:v2:obsidian:contours:modern:auto",
            CardTemplateRegistry.DEFAULT_TEMPLATE_ID,
        )
        assertEquals(
            CardTemplateRegistry.DEFAULT_TEMPLATE_ID,
            CardTemplateRegistry.defaultTemplate.id,
        )
        assertEquals(12, CardTemplateRegistry.templates.size)
        assertTrue(CardTemplateRegistry.templates.all { it.id.startsWith("custom:v2:") })
    }
}
