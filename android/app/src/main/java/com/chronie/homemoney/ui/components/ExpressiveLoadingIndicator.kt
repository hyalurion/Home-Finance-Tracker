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
import androidx.compose.ui.graphics.drawscope.Stroke
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
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val shapeProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shape_progress"
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
                center = center,
                style = Stroke(width = strokeWidth.toPx())
            )
        }
        
        translate(center.x, center.y) {
            val indicatorColor = if (containerVisible) {
                colorScheme.onPrimaryContainer
            } else {
                colorScheme.primary
            }
            
            val indicatorRadius = radius * 0.6f
            val indicatorStrokeWidth = strokeWidth.toPx()
            
            drawExpressiveShape(
                progress = shapeProgress,
                radius = indicatorRadius,
                strokeWidth = indicatorStrokeWidth,
                color = indicatorColor,
                rotation = rotation
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawExpressiveShape(
    progress: Float,
    radius: Float,
    strokeWidth: Float,
    color: Color,
    rotation: Float
) {
    val sides = when {
        progress < 0.2f -> 7
        progress < 0.4f -> 4
        progress < 0.6f -> 3
        progress < 0.8f -> 2
        else -> 1
    }
    
    if (sides == 1) {
        drawCircle(
            color = color,
            radius = strokeWidth * 2
        )
    } else {
        val path = Path()
        val angleStep = (2 * PI / sides).toFloat()
        
        for (i in 0 until sides) {
            val angle = angleStep * i - (PI / 2).toFloat()
            val x = cos(angle).toFloat() * radius
            val y = sin(angle).toFloat() * radius
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        path.close()
        
        val rotationRad = (rotation * PI / 180).toFloat()
        val cosRot = cos(rotationRad).toFloat()
        val sinRot = sin(rotationRad).toFloat()
        
        val rotatedPath = Path()
        
        for (i in 0 until sides) {
            val angle = angleStep * i - (PI / 2).toFloat()
            val x = cos(angle).toFloat() * radius
            val y = sin(angle).toFloat() * radius
            
            val rotatedX = x * cosRot - y * sinRot
            val rotatedY = x * sinRot + y * cosRot
            
            if (i == 0) {
                rotatedPath.moveTo(rotatedX, rotatedY)
            } else {
                rotatedPath.lineTo(rotatedX, rotatedY)
            }
        }
        rotatedPath.close()
        
        drawPath(
            path = rotatedPath,
            color = color,
            style = Stroke(width = strokeWidth)
        )
    }
}
