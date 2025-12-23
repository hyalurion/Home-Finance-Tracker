package com.chronie.homemoney.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronie.homemoney.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    var showSplash by remember { mutableStateOf(true) }
    var countdown by remember { mutableStateOf(5) }
    var shouldClose by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        // 倒计时逻辑
        while (countdown > 0 && showSplash) {
            delay(1000)
            countdown--
        }
        shouldClose = true
    }
    
    // 处理关闭逻辑
    LaunchedEffect(shouldClose) {
        if (shouldClose && showSplash) {
            showSplash = false
            delay(300) // 等待淡出动画完成
            onSplashComplete()
        }
    }
    
    AnimatedVisibility(
        visible = showSplash,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Image(
                    // 开屏图片，先在res/drawable目录下放一张图片，再修改此处的引用，之后应该修改从远程加载和缓存
                    painter = painterResource(id = R.drawable.shengdan),
                    contentDescription = "开屏图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(600.dp),
                    contentScale = ContentScale.Fit
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "$countdown",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        shouldClose = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.size(200.dp, 50.dp)
                ) {
                    Text(
                        text = "跳过",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}