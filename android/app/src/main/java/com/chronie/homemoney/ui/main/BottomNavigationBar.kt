package com.chronie.homemoney.ui.main

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.chronie.homemoney.R

// 定义导航项数据类
data class NavigationItem(
    val icon: @Composable () -> Unit,
    val label: String,
    val index: Int
)

@Composable
fun BottomNavigationBar(
    context: Context,
    selectedTab: Int,
    onTabChange: (Int) -> Unit
) {
    // 获取当前主题模式
    val isDarkTheme = isSystemInDarkTheme()

    // 导航栏背景色：透明，因为背景已经存在
    val backgroundColor = Color.Transparent
    
    // 未选中项颜色：浅色模式为黑色，深色模式为白色
    val unselectedColor = if (isDarkTheme) Color.White else Color.Black
    
    // 选中项颜色：从主题中获取primary颜色
    val selectedColor = MaterialTheme.colorScheme.primary
    
    // 定义导航项
    val navigationItems = listOf(
        NavigationItem(
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = context.getString(R.string.expense_list_title),
            index = 0
        ),
        NavigationItem(
            icon = { Icon(Icons.Default.InsertChart, contentDescription = null) },
            label = context.getString(R.string.charts_title),
            index = 1
        ),
        NavigationItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = context.getString(R.string.settings),
            index = 2
        )
    )
    
    // 自定义IOS风格导航栏，使用透明背景容器包裹以覆盖Scaffold默认背景
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(backgroundColor),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
        navigationItems.forEach { item ->
            val isSelected = selectedTab == item.index
            
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .padding(4.dp)
                    .background(
                        color = if (isSelected) Color.Transparent else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        onTabChange(item.index)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标
                Icon(
                    modifier = Modifier
                        .size(30.dp) // 放大1.25倍
                        .padding(bottom = 4.dp),
                    imageVector = when (item.index) {
                        0 -> Icons.Default.Home
                        1 -> Icons.Default.InsertChart
                        2 -> Icons.Default.Settings
                        else -> Icons.Default.Home
                    },
                    contentDescription = item.label,
                    tint = if (isSelected) selectedColor else unselectedColor
                )
                
                // 文字
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) selectedColor else unselectedColor
                )
            }
        }
    }
    }
}