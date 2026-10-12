package dev.mediflow.designsystem

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
fun MediMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.size(36.dp)) {
        val w = size.width
        val h = size.height
        rotate(-18f) {
            drawRoundRect(
                color,
                Offset(w * .12f, h * .36f),
                Size(w * .76f, h * .28f),
                CornerRadius(w * .14f),
            )
            drawRoundRect(
                color,
                Offset(w * .36f, h * .12f),
                Size(w * .28f, h * .76f),
                CornerRadius(w * .14f),
            )
        }
        drawCircle(Color.White.copy(alpha = .45f), w * .075f, Offset(w * .44f, h * .44f))
    }
}

@Composable
fun MedicineGlyph(colorIndex: Int, modifier: Modifier = Modifier) {
    val color = MediColors.accent(colorIndex)
    val soft = MediColors.soft(colorIndex)
    Box(
        modifier.size(54.dp).background(soft, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp)) {
            rotate(-35f) {
                drawRoundRect(
                    color,
                    Offset(size.width * .22f, 0f),
                    Size(size.width * .56f, size.height),
                    CornerRadius(size.width * .28f),
                    style = Stroke(2.dp.toPx()),
                )
                clipRect(0f, size.height * .5f, size.width, size.height) {
                    drawRoundRect(
                        color.copy(alpha = .3f),
                        Offset(size.width * .22f, 0f),
                        Size(size.width * .56f, size.height),
                        CornerRadius(size.width * .28f),
                    )
                }
                drawLine(
                    color,
                    Offset(size.width * .23f, size.height * .5f),
                    Offset(size.width * .77f, size.height * .5f),
                    1.6.dp.toPx(),
                )
            }
        }
    }
}

@Composable
fun RoutineFlower(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension
        val center = Offset(size.width * .5f, size.height * .48f)
        drawCircle(Color(0xFFF6F2FA), unit * .48f, center)
        repeat(5) { index ->
            rotate(index * 72f, center) {
                drawOval(
                    Color(0xFFB4A1CC).copy(alpha = .70f),
                    Offset(center.x - unit * .15f, center.y - unit * .39f),
                    Size(unit * .30f, unit * .48f),
                )
                drawOval(
                    Color(0xFFD9CCE8).copy(alpha = .8f),
                    Offset(center.x - unit * .10f, center.y - unit * .35f),
                    Size(unit * .20f, unit * .37f),
                )
            }
        }
        drawCircle(Color(0xFFFAF4E7), unit * .125f, center)
        drawCircle(Color(0xFFC1A779), unit * .055f, center)
        drawCircle(
            Color.White.copy(alpha = .8f),
            unit * .02f,
            Offset(center.x - unit * .02f, center.y - unit * .025f),
        )
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(24.dp),
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    background: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Surface(shape = RoundedCornerShape(50), color = background) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun SectionLabel(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null)
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
        }
        trailing?.invoke()
    }
}
