package com.pdh.cardvault.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.max

internal const val DESKTOP_STACK_STEP_DP = 74f
private const val DESKTOP_STACK_BOTTOM_SPACE_DP = 54f

internal fun desktopStackContentHeightDp(
    viewportHeightDp: Float,
    viewportWidthDp: Float,
    itemCount: Int,
): Float {
    if (itemCount <= 0) return viewportHeightDp.coerceAtLeast(0f)
    val cardHeightDp = viewportWidthDp.coerceAtLeast(0f) / STANDARD_CARD_ASPECT_RATIO
    val requiredHeight = cardHeightDp + (itemCount - 1) * DESKTOP_STACK_STEP_DP +
        DESKTOP_STACK_BOTTOM_SPACE_DP
    return max(viewportHeightDp.coerceAtLeast(0f), requiredHeight)
}

@Composable
internal fun DesktopStackViewport(
    itemCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier) {
        val contentHeight = desktopStackContentHeightDp(
            viewportHeightDp = maxHeight.value,
            viewportWidthDp = maxWidth.value,
            itemCount = itemCount,
        ).dp
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(contentHeight),
                    content = content,
                )
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}
