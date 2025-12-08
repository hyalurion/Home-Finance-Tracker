package com.chronie.homemoney.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.core.common.Language
import com.chronie.homemoney.ui.theme.LocalThemeSettings
import com.chronie.homemoney.ui.theme.ThemeSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    context: Context,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToDatabaseTest: () -> Unit = {},
    onNavigateToMembership: () -> Unit = {},
    onLogout: () -> Unit = {},
    onRequireLogin: () -> Unit = {},
    onRequireMembership: () -> Unit = {}
) {
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val scrollState = androidx.compose.foundation.rememberScrollState()
    
    // 会员验证
    LaunchedEffect(Unit) {
        val isLoggedIn = viewModel.checkLoginStatusUseCase()
        val isMember = viewModel.checkMembershipUseCase()
        
        when {
            !isLoggedIn -> onRequireLogin()
            !isMember -> onRequireMembership()
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部标题栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.settings),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
        
        // 可滚动内容
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // 账户信息部分
            AccountSection(
                viewModel = viewModel, 
                context = context, 
                onLogout = onLogout,
                onNavigateToMembership = onNavigateToMembership
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Divider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = context.getString(R.string.select_language),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Language.values().forEach { language ->
                LanguageItem(
                    language = language,
                    isSelected = language == currentLanguage,
                    onClick = { viewModel.setLanguage(language) }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // AI 设置部分
            Divider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            AISettingsSection(viewModel = viewModel, context = context)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 预算管理部分
            Divider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            BudgetSettingsSection(context = context)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 数据同步部分
            Divider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SyncSection(viewModel = viewModel, context = context)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 数据导入导出部分
            Divider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            DataImportExportSection(viewModel = viewModel, context = context)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 主题颜色设置部分
            Divider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = context.getString(R.string.theme_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 动态颜色开关
            val useDynamicColor by viewModel.useDynamicColor.collectAsState()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = context.getString(R.string.dynamic_color),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = context.getString(R.string.dynamic_color_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 获取当前主题设置
                    val themeSettings = LocalThemeSettings.current
                    
                    Switch(
                        checked = useDynamicColor,
                        onCheckedChange = { enabled ->
                            // 同时更新本地状态和ViewModel
                            themeSettings.value = ThemeSettings(
                                useDynamicColor = enabled,
                                primaryColor = themeSettings.value.primaryColor
                            )
                            viewModel.toggleDynamicColor(enabled)
                        }
                    )
                }
            }
            
            // 手动颜色选择器（仅当动态颜色关闭时显示）
            if (!useDynamicColor) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = context.getString(R.string.manual_color_selection),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                ColorPickerSection(viewModel = viewModel, context = context)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 开发者模式开关
            val isDeveloperMode by viewModel.isDeveloperMode.collectAsState(initial = false)
            
            Divider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = context.getString(R.string.developer_options),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = context.getString(R.string.developer_mode),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = context.getString(R.string.developer_mode_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isDeveloperMode,
                        onCheckedChange = { viewModel.toggleDeveloperMode() }
                    )
                }
            }
            
            // 开发者工具（仅在开发者模式下显示）
            if (isDeveloperMode) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = context.getString(R.string.developer_tools),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // 数据库测试按钮
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToDatabaseTest),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.database_test),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = ">",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 意见反馈
            Divider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = context.getString(R.string.feedback_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://wj.qq.com/s2/24109109/3572/")
                            )
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                "Brower Open Failed: ${e.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = context.getString(R.string.feedback_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = context.getString(R.string.feedback_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun LanguageItem(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
            if (isSelected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun AISettingsSection(
    viewModel: SettingsViewModel,
    context: Context
) {
    val apiKey by viewModel.aiApiKey.collectAsState()
    var showApiKeyDialog by remember { mutableStateOf(false) }
    
    Column {
        Text(
            text = context.getString(R.string.settings_ai_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showApiKeyDialog = true },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.settings_ai_api_key),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = context.getString(R.string.settings_ai_api_key_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (apiKey.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "已设置: ${apiKey.take(8)}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
    
    // API Key 输入对话框
    if (showApiKeyDialog) {
        var inputApiKey by remember { mutableStateOf(apiKey) }
        
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text(context.getString(R.string.settings_ai_api_key)) },
            text = {
                Column {
                    Text(
                        text = context.getString(R.string.settings_ai_api_key_description),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = inputApiKey,
                        onValueChange = { inputApiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(context.getString(R.string.settings_ai_api_key_hint)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setAIApiKey(inputApiKey)
                        showApiKeyDialog = false
                    }
                ) {
                    Text(context.getString(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun BudgetSettingsSection(
    context: Context,
    budgetViewModel: com.chronie.homemoney.ui.budget.BudgetViewModel = hiltViewModel()
) {
    val uiState by budgetViewModel.uiState.collectAsState()
    var showBudgetDialog by remember { mutableStateOf(false) }
    
    Column {
        Text(
            text = context.getString(R.string.budget_settings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showBudgetDialog = true },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.budget_monthly_limit),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = context.getString(R.string.budget_enable_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 显示当前预算状态
                        if (uiState.budget?.isEnabled == true) {
                            Text(
                                text = "${context.getString(R.string.budget_enable_feature)}: ¥${String.format(java.util.Locale.getDefault(), "%.2f", uiState.budget?.monthlyLimit ?: 0.0)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = context.getString(R.string.budget_enable_title),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
    
    // 预算设置对话框
    if (showBudgetDialog) {
        com.chronie.homemoney.ui.budget.BudgetSettingsDialog(
            context = context,
            currentBudget = uiState.budget,
            onDismiss = { showBudgetDialog = false },
            onSave = { limit, threshold, enabled ->
                budgetViewModel.saveBudget(limit, threshold, enabled)
                showBudgetDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataImportExportSection(
    viewModel: SettingsViewModel,
    context: Context
) {
    val exportInProgress by viewModel.exportInProgress.collectAsState()
    val importInProgress by viewModel.importInProgress.collectAsState()
    var showExportDialog by remember { mutableStateOf(false) }
    var showDateRangeDialog by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var endDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    
    // 权限请求
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.permission_storage_required),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // 检查并请求权限
    fun checkAndRequestPermissions(onGranted: () -> Unit) {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        
        val allGranted = permissions.all { permission ->
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        
        if (allGranted) {
            onGranted()
        } else {
            permissionLauncher.launch(permissions)
        }
    }
    
    // 文件选择器
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.importExpenses(it) }
    }
    
    Column {
        Text(
            text = context.getString(R.string.data_import_export),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = context.getString(R.string.data_import_export_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // 导出按钮
        Button(
            onClick = { 
                checkAndRequestPermissions {
                    showExportDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !exportInProgress && !importInProgress
        ) {
            if (exportInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (exportInProgress) {
                    context.getString(R.string.export_in_progress)
                } else {
                    context.getString(R.string.export_data)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 导入按钮
        Button(
            onClick = { 
                checkAndRequestPermissions {
                    filePickerLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !exportInProgress && !importInProgress
        ) {
            if (importInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (importInProgress) {
                    context.getString(R.string.import_in_progress)
                } else {
                    context.getString(R.string.import_data)
                }
            )
        }
    }
    
    // 导出选项对话框
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(context.getString(R.string.export_data)) },
            text = {
                Column {
                    Text(
                        text = context.getString(R.string.export_select_range),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // 导出全部数据按钮
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportDialog = false
                                viewModel.exportExpenses(null, null)
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = context.getString(R.string.export_all_data),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 导出日期范围按钮
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportDialog = false
                                showDateRangeDialog = true
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = context.getString(R.string.export_date_range),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
    
    // 日期范围选择对话框
    if (showDateRangeDialog) {
        var showStartDatePicker by remember { mutableStateOf(false) }
        var showEndDatePicker by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { showDateRangeDialog = false },
            title = { Text(context.getString(R.string.export_select_range)) },
            text = {
                Column {
                    // 开始日期
                    Text(
                        text = context.getString(R.string.export_start_date),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showStartDatePicker = true },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = startDate?.toString() ?: context.getString(R.string.export_start_date),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 结束日期
                    Text(
                        text = context.getString(R.string.export_end_date),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showEndDatePicker = true },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = endDate?.toString() ?: context.getString(R.string.export_end_date),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                
                // 日期选择器
                if (showStartDatePicker) {
                    val datePickerState = androidx.compose.material3.rememberDatePickerState()
                    androidx.compose.material3.DatePickerDialog(
                        onDismissRequest = { showStartDatePicker = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    datePickerState.selectedDateMillis?.let { millis ->
                                        startDate = java.time.Instant.ofEpochMilli(millis)
                                            .atZone(java.time.ZoneId.systemDefault())
                                            .toLocalDate()
                                    }
                                    showStartDatePicker = false
                                }
                            ) {
                                Text(context.getString(R.string.confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showStartDatePicker = false }) {
                                Text(context.getString(R.string.cancel))
                            }
                        }
                    ) {
                        androidx.compose.material3.DatePicker(state = datePickerState)
                    }
                }
                
                if (showEndDatePicker) {
                    val datePickerState = androidx.compose.material3.rememberDatePickerState()
                    androidx.compose.material3.DatePickerDialog(
                        onDismissRequest = { showEndDatePicker = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    datePickerState.selectedDateMillis?.let { millis ->
                                        endDate = java.time.Instant.ofEpochMilli(millis)
                                            .atZone(java.time.ZoneId.systemDefault())
                                            .toLocalDate()
                                    }
                                    showEndDatePicker = false
                                }
                            ) {
                                Text(context.getString(R.string.confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEndDatePicker = false }) {
                                Text(context.getString(R.string.cancel))
                            }
                        }
                    ) {
                        androidx.compose.material3.DatePicker(state = datePickerState)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDateRangeDialog = false
                        viewModel.exportExpenses(startDate, endDate)
                    },
                    enabled = startDate != null && endDate != null
                ) {
                    Text(context.getString(R.string.export_data))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangeDialog = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun SyncSection(
    viewModel: SettingsViewModel,
    context: Context
) {
    val syncStatus by viewModel.syncStatus.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    
    // 显示同步消息
    syncMessage?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSyncMessage()
        }
        
        Snackbar(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = message)
        }
    }
    
    Column {
        Text(
            text = context.getString(R.string.sync_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 同步状态
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.sync_status),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = when (syncStatus) {
                            com.chronie.homemoney.domain.model.SyncStatus.IDLE -> 
                                context.getString(R.string.sync_status_idle)
                            com.chronie.homemoney.domain.model.SyncStatus.SYNCING -> 
                                context.getString(R.string.sync_status_syncing)
                            com.chronie.homemoney.domain.model.SyncStatus.SUCCESS -> 
                                context.getString(R.string.sync_status_success)
                            com.chronie.homemoney.domain.model.SyncStatus.FAILED -> 
                                context.getString(R.string.sync_status_failed)
                            com.chronie.homemoney.domain.model.SyncStatus.CONFLICT -> 
                                context.getString(R.string.sync_status_conflict)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (syncStatus) {
                            com.chronie.homemoney.domain.model.SyncStatus.SUCCESS -> 
                                MaterialTheme.colorScheme.primary
                            com.chronie.homemoney.domain.model.SyncStatus.FAILED,
                            com.chronie.homemoney.domain.model.SyncStatus.CONFLICT -> 
                                MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 最后同步时间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.sync_last_time),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = lastSyncTime ?: context.getString(R.string.sync_never),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 待同步项数量
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.sync_pending_count),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = pendingSyncCount.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (pendingSyncCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 手动同步按钮
                Button(
                    onClick = { viewModel.manualSync() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = syncStatus != com.chronie.homemoney.domain.model.SyncStatus.SYNCING
                ) {
                    if (syncStatus == com.chronie.homemoney.domain.model.SyncStatus.SYNCING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (syncStatus == com.chronie.homemoney.domain.model.SyncStatus.SYNCING) {
                            context.getString(R.string.sync_syncing)
                        } else {
                            context.getString(R.string.sync_manual_trigger)
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun AccountSection(
    viewModel: SettingsViewModel,
    context: Context,
    onLogout: () -> Unit,
    onNavigateToMembership: () -> Unit = {}
) {
    val currentUsername by viewModel.currentUsername.collectAsState()
    val membershipStatus by viewModel.membershipStatus.collectAsState()
    val membershipLoading by viewModel.membershipLoading.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    
    // 监听退出登录事件
    LaunchedEffect(Unit) {
        viewModel.logoutEvent.collect {
            onLogout()
        }
    }
    
    Column {
        Text(
            text = context.getString(R.string.auth_account_info),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 当前用户
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.auth_current_user),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = currentUsername ?: context.getString(R.string.auth_not_logged_in),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // 会员状态
                if (currentUsername != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.membership_status),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        if (membershipLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (membershipStatus?.isActive == true) {
                                    context.getString(R.string.membership_active)
                                } else {
                                    context.getString(R.string.membership_inactive)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (membershipStatus?.isActive == true) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                    
                    // 会员计划
                    if (membershipStatus?.planName != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = context.getString(R.string.membership_plan),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = membershipStatus?.planName ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 到期时间
                    if (membershipStatus?.endDate != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = context.getString(R.string.membership_expires_on),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatTimestamp(membershipStatus?.endDate ?: 0L),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 会员管理/续费按钮
                    Button(
                        onClick = {
                            android.util.Log.d("SettingsScreen", "管理订阅按钮被点击")
                            android.util.Log.d("SettingsScreen", "会员状态: ${membershipStatus?.isActive}")
                            android.util.Log.d("SettingsScreen", "调用 onNavigateToMembership")
                            onNavigateToMembership()
                            android.util.Log.d("SettingsScreen", "onNavigateToMembership 调用完成")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (membershipStatus?.isActive == true) {
                                context.getString(R.string.membership_manage)
                            } else {
                                context.getString(R.string.membership_subscribe_now)
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 刷新会员状态按钮
                    OutlinedButton(
                        onClick = { viewModel.refreshMembershipStatus() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !membershipLoading
                    ) {
                        if (membershipLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(context.getString(R.string.membership_refresh))
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 退出登录按钮
                    OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(context.getString(R.string.auth_logout_button))
                    }
                }
            }
        }
    }
    
    // 退出登录确认对话框
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(context.getString(R.string.auth_logout_confirm_title)) },
            text = { Text(context.getString(R.string.auth_logout_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.logout()
                        showLogoutDialog = false
                    }
                ) {
                    Text(
                        text = context.getString(R.string.confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ColorPickerSection(viewModel: SettingsViewModel, context: Context) {
    val primaryColor by viewModel.primaryColor.collectAsState()
    
    val colorOptions = listOf(
        0xFF6750A4.toInt(), // 紫色
        0xFF5C6BC0.toInt(), // 蓝色
        0xFF42A5F5.toInt(), // 浅蓝色
        0xFF26A69A.toInt(), // 青色
        0xFF66BB6A.toInt(), // 绿色
        0xFF9CCC65.toInt(), // 浅绿色
        0xFFFFA726.toInt(), // 橙色
        0xFFEF5350.toInt(), // 红色
        0xFFAB47BC.toInt(), // 粉色
        0xFF8D6E63.toInt(), // 棕色
        0xFF757575.toInt()  // 灰色
    )
    
    Column {
        Text(
            text = context.getString(R.string.primary_color),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 获取当前主题设置
            val themeSettings = LocalThemeSettings.current
            
            colorOptions.forEach { color ->
                Box(
                    modifier = Modifier
                        .clickable {
                            // 同时更新本地状态和ViewModel
                            themeSettings.value = ThemeSettings(
                                useDynamicColor = themeSettings.value.useDynamicColor,
                                primaryColor = color
                            )
                            viewModel.setPrimaryColor(color)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 选中边框
                    if (color == primaryColor) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(46.dp)
                        ) {}
                    }
                    
                    // 颜色圆
                    Surface(
                        shape = CircleShape,
                        color = Color(color),
                        modifier = Modifier.size(40.dp)
                    ) {}
                    
                    // 选中标记
                    if (color == primaryColor) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "选中",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
