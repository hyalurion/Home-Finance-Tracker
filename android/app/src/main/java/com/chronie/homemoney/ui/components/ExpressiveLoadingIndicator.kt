package com.chronie.homemoney.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 3.dp,
    containerVisible: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading_animation")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morph_progress"
    )
    
    val colorScheme = MaterialTheme.colorScheme
    
    Canvas(
        modifier = modifier.size(size)
    ) {
        val center = Offset(size.toPx() / 2, size.toPx() / 2)
        val radius = (size.toPx() - strokeWidth.toPx()) / 2
        
        if (containerVisible) {
            drawCircle(
                color = colorScheme.secondaryContainer,
                radius = radius,
                center = center
            )
        }
        
        val indicatorColor = if (containerVisible) {
            colorScheme.onPrimaryContainer
        } else {
            colorScheme.primary
        }
        
        val indicatorRadius = radius * 0.6f
        
        drawExpressiveShape(
            progress = morphProgress,
            radius = indicatorRadius,
            color = indicatorColor,
            rotation = rotation,
            center = center
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawExpressiveShape(
    progress: Float,
    radius: Float,
    color: Color,
    rotation: Float,
    center: Offset
) {
    val sides = when {
        progress < 0.33f -> 9
        progress < 0.66f -> 4
        progress < 0.9f -> 2
        else -> 1
    }
    
    val path = Path()
    
    if (sides == 1) {
        val ellipseScale = 1f - ((progress - 0.9f) / 0.1f) * 0.3f
        drawOval(
            color = color,
            topLeft = Offset(center.x - radius * ellipseScale, center.y - radius),
            size = Size(radius * 2 * ellipseScale, radius * 2)
        )
    } else {
        val angleStep = (2 * PI / sides).toFloat()
        
        for (i in 0 until sides) {
            val angle = angleStep * i - (PI / 2).toFloat()
            val x = center.x + cos(angle).toFloat() * radius
            val y = center.y + sin(angle).toFloat() * radius
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        path.close()
        
        rotate(rotation, pivot = center) {
            drawPath(
                path = path,
                color = color
            )
        }
    }
}
