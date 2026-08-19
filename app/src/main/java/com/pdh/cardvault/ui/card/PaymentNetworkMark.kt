package com.pdh.cardvault.ui.card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.pdh.cardvault.R
import com.pdh.cardvault.domain.model.CardNetwork

/** Compact, locally drawn network marks. No remote or bundled raster artwork is used. */
@Composable
fun PaymentNetworkMark(
    network: CardNetwork,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clearAndSetSemantics { }) {
        when (network) {
            CardNetwork.Visa -> Text(
                text = stringResource(R.string.card_network_visa),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-0.5f).sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.72f),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 4f,
                    ),
                ),
            )

            CardNetwork.Mastercard -> Box(
                modifier = Modifier.size(width = 46.dp, height = 26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.height * 0.41f
                    drawCircle(
                        color = Color(0xFFEB001B),
                        radius = radius,
                        center = Offset(size.width * 0.39f, size.height * 0.5f),
                    )
                    drawCircle(
                        color = Color(0xFFF79E1B).copy(alpha = 0.92f),
                        radius = radius,
                        center = Offset(size.width * 0.61f, size.height * 0.5f),
                    )
                }
            }

            CardNetwork.UnionPay -> Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.graphicsLayer { rotationZ = -8f },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(
                        modifier = Modifier
                            .size(width = 6.dp, height = 16.dp)
                            .background(Color(0xFFD71920), RoundedCornerShape(2.dp)),
                    )
                    Spacer(
                        modifier = Modifier
                            .width(1.dp),
                    )
                    Spacer(
                        modifier = Modifier
                            .size(width = 6.dp, height = 16.dp)
                            .background(Color(0xFF0071BC), RoundedCornerShape(2.dp)),
                    )
                    Spacer(modifier = Modifier.width(1.dp))
                    Spacer(
                        modifier = Modifier
                            .size(width = 6.dp, height = 16.dp)
                            .background(Color(0xFF00A651), RoundedCornerShape(2.dp)),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.card_network_unionpay_short),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.78f),
                            offset = Offset(0f, 1.2f),
                            blurRadius = 3.5f,
                        ),
                    ),
                )
            }
        }
    }
}
