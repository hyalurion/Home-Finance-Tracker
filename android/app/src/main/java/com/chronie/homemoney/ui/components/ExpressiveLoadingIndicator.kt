package com.chronie.homemoney.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    containerVisible: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    // 1. 定义旋转动画
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    // 2. 定义变形进度 (0f 到 6f，对应 7 个状态的转换)
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f, 
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morph"
    )

    // 3. 预生成的形状（关键点：设置 rounding 弧度让它看起来柔和）
    val shapes = remember {
        listOf(10, 9, 5, 4, 5, 9, 10).map { sides ->
            if (sides == 4) {
                // 用 4 个顶点 + 大圆角制造胶囊（椭圆）效果
                RoundedPolygon(
                    numVertices = 4,
                    radius = 1f,
                    rounding = androidx.graphics.shapes.CornerRounding(0.8f)   // 非常大的圆角形成长胶囊
                )
            } else {
                RoundedPolygon(
                    numVertices = sides,
                    radius = 1f,
                    // 这里是灵魂：圆角比例，M3 的风格通常很圆润
                    rounding = androidx.graphics.shapes.CornerRounding(0.3f)
                )
            }
        }
    }

    // 4. 创建 Morph 序列
    val morphs = remember(shapes) {
        List(shapes.size - 1) { i ->
            Morph(shapes[i], shapes[i + 1])
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val radius = size.toPx() / 2f
        val centerOffset = center

        // 绘制背景容器
        if (containerVisible) {
            drawCircle(
                color = colorScheme.secondaryContainer,
                radius = radius
            )
        }

        val indicatorColor = if (containerVisible) colorScheme.onPrimaryContainer else colorScheme.primary

        // 计算当前处于哪两个形状之间
        val index = morphProgress.toInt().coerceIn(0, morphs.size - 1)
        val progress = morphProgress - index
        
        // 核心：获取当前变形状态的 Path
        val currentPath = morphs[index].toPath(progress).asComposePath()

        rotate(rotation) {
            // 将 Path 缩放并平移到 Canvas 中心
            val scale = radius * 0.6f
            drawContext.canvas.save()
            drawContext.canvas.translate(centerOffset.x, centerOffset.y)
            drawContext.canvas.scale(scale, scale)
            
            drawPath(path = currentPath, color = indicatorColor)
            
            drawContext.canvas.restore()
        }
    }
}
