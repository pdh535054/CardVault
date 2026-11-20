package com.pdh.cardvault.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Gemini 4 Carbon 120Hz motion & surface optics vocabulary.
 *
 * Designed for zero idle GPU invalidations, zero Lookahead overhead, and immediate first-frame
 * tactile response on high-refresh-rate displays.
 */
object CardVaultMotion {
    val FastOutExtraSlowIn = CubicBezierEasing(0.08f, 0.82f, 0.17f, 1f)
    val FastOutLinearIn = CubicBezierEasing(0.32f, 0f, 0.85f, 0.18f)

    const val FAST_FADE_MILLIS = 135
    const val NAV_ENTER_MILLIS = 210
    const val NAV_POP_MILLIS = 185
    const val NAV_EXIT_MILLIS = 130

    /** Snappy critically-damped spring for stack reordering & layout settles (~170ms at 120Hz). */
    fun <T> snappySpring() = spring<T>(dampingRatio = 0.88f, stiffness = 620f)

    /** Tactile micro-interaction spring for press/release feedback. */
    fun <T> tactileSpring() = spring<T>(dampingRatio = 0.74f, stiffness = 680f)

    /** Physical 3D card-flip spring: crisp launch, smooth magnetic landing, zero sluggishness. */
    fun <T> flipSpring() = spring<T>(dampingRatio = 0.86f, stiffness = 460f)

    fun <T> navSlide() = tween<T>(durationMillis = NAV_ENTER_MILLIS, easing = FastOutExtraSlowIn)

    fun <T> navPopSlide() = tween<T>(durationMillis = NAV_POP_MILLIS, easing = FastOutExtraSlowIn)

    fun <T> navEnterFade() = tween<T>(durationMillis = FAST_FADE_MILLIS, easing = FastOutExtraSlowIn)

    fun <T> navExitFade() = tween<T>(durationMillis = NAV_EXIT_MILLIS, easing = FastOutLinearIn)
}

/**
 * Computes the normalized specular reflection intensity (`0f..1f`) as a card rotates in 3D space
 * (`0f..180f`). Returns exactly `0f` when resting flat (`0f` or `180f`) so zero extra overdraw
 * occurs at rest, and peaks smoothly around `45°` and `135°` during a flip.
 */
internal fun flipSpecularIntensity(rotationDegrees: Float): Float {
    val normalized = ((rotationDegrees % 180f) + 180f) % 180f
    if (normalized <= 0.5f || normalized >= 179.5f) return 0f
    return abs(sin(normalized / 90.0 * PI)).toFloat().coerceIn(0f, 1f)
}

/**
 * Zero-allocation `Modifier.Node` tactile press physics.
 *
 * Updates only the RenderNode layer scale during placement without recomposing or re-measuring
 * child composables.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.968f,
): Modifier = this then PressScaleElement(interactionSource, pressedScale)

private data class PressScaleElement(
    val interactionSource: InteractionSource,
    val pressedScale: Float,
) : ModifierNodeElement<PressScaleNode>() {
    override fun create(): PressScaleNode = PressScaleNode(interactionSource, pressedScale)

    override fun update(node: PressScaleNode) {
        node.update(interactionSource, pressedScale)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pressScale"
        properties["pressedScale"] = pressedScale
    }
}

private class PressScaleNode(
    private var interactionSource: InteractionSource,
    private var pressedScale: Float,
) : Modifier.Node(), LayoutModifierNode {
    private val scale = Animatable(1f)
    private var collectJob: Job? = null
    private var animationJob: Job? = null

    override val shouldAutoInvalidate: Boolean = false

    override fun onAttach() {
        observeInteractions()
    }

    override fun onDetach() {
        collectJob?.cancel()
        collectJob = null
        animationJob?.cancel()
        animationJob = null
    }

    fun update(newSource: InteractionSource, newPressedScale: Float) {
        pressedScale = newPressedScale
        if (interactionSource != newSource) {
            interactionSource = newSource
            if (isAttached) observeInteractions()
        }
    }

    private fun observeInteractions() {
        collectJob?.cancel()
        val activePresses = mutableSetOf<PressInteraction.Press>()
        collectJob = coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> activePresses.add(interaction)
                    is PressInteraction.Release -> activePresses.remove(interaction.press)
                    is PressInteraction.Cancel -> activePresses.remove(interaction.press)
                }
                val target = if (activePresses.isNotEmpty()) pressedScale else 1f
                if (scale.targetValue != target) {
                    animationJob?.cancel()
                    animationJob = coroutineScope.launch {
                        scale.animateTo(target, CardVaultMotion.tactileSpring()) {
                            invalidatePlacement()
                        }
                    }
                }
            }
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val current = scale.value
                scaleX = current
                scaleY = current
            }
        }
    }
}

/**
 * Cached 1dp luminous top-edge glass/anodized-metal specular rim border.
 *
 * Precomputes the gradient brush and stroke geometry once per layout size inside [drawWithCache],
 * adding crisp physical separation to dark surfaces and stacked cards at near-zero GPU cost.
 */
fun Modifier.specularRim(
    cornerRadius: Dp = 32.dp,
    topAlpha: Float = 0.20f,
    midAlpha: Float = 0.06f,
    bottomAlpha: Float = 0.02f,
    strokeWidth: Dp = 1.dp,
): Modifier = drawWithCache {
    val strokePx = strokeWidth.toPx()
    val inset = strokePx * 0.5f
    val radiusPx = (cornerRadius.toPx() - inset).coerceAtLeast(0f)
    val rimBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = topAlpha),
        0.48f to Color.White.copy(alpha = midAlpha),
        1.0f to Color.White.copy(alpha = bottomAlpha),
    )
    val stroke = Stroke(width = strokePx)
    onDrawWithContent {
        drawContent()
        drawRoundRect(
            brush = rimBrush,
            topLeft = Offset(inset, inset),
            size = Size(
                width = (size.width - strokePx).coerceAtLeast(0f),
                height = (size.height - strokePx).coerceAtLeast(0f),
            ),
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            style = stroke,
        )
    }
}
