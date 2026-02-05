package com.chronie.homemoney.ui.membership

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.SubscriptionPlan
import com.chronie.homemoney.domain.model.SubscriptionStatus
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.expense.formatDateByLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembershipPurchaseScreen(
    context: Context,
    onNavigateToMain: () -> Unit,
    onNavigateToWelcome: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: MembershipViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 显示错误提示
    LaunchedEffect(uiState) {
        if (uiState is MembershipUiState.Error) {
            snackbarHostState.showSnackbar(
                message = (uiState as MembershipUiState.Error).message,
                duration = SnackbarDuration.Long
            )
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.membership_title)) },
                navigationIcon = {
                    CircularIconButton(onClick = onNavigateToMain, modifier = Modifier.padding(start = 8.dp, end = 4.dp)) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = context.getString(R.string.back)
                        )
                    }
                },
                actions = {
                    CircularIconButton(onClick = onNavigateToHistory, modifier = Modifier.padding(start = 4.dp, end = 4.dp)) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = context.getString(R.string.subscription_history)
                        )
                    }
                    CircularIconButton(onClick = { viewModel.logout(onNavigateToWelcome) }, modifier = Modifier.padding(start = 4.dp, end = 8.dp)) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = context.getString(R.string.auth_logout_button)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when (val state = uiState) {
            is MembershipUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveLoadingIndicator()
                }
            }
            
            is MembershipUiState.Success -> {
                Box(modifier = Modifier.padding(paddingValues)) {
                    MembershipContent(
                        context = context,
                        plans = state.plans,
                        currentStatus = state.currentStatus,
                        onPurchase = { plan ->
                            viewModel.purchaseMembership(plan, onNavigateToMain)
                        },
                        onRefresh = { viewModel.refreshMembershipStatus() },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // 购买中的加载遮罩
                    if (state.isPurchasing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Card {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    ExpressiveLoadingIndicator(size = 48.dp)
                                    Text(
                                        text = context.getString(R.string.purchasing),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            is MembershipUiState.Error -> {
                ErrorContent(
                    context = context,
                    message = state.message,
                    onRetry = { viewModel.loadMembershipData() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembershipContent(
    context: Context,
    plans: List<SubscriptionPlan>,
    currentStatus: SubscriptionStatus?,
    onPurchase: (SubscriptionPlan) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showExpiredBottomSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    
    LaunchedEffect(currentStatus) {
        if (currentStatus != null && !currentStatus.isActive) {
            showExpiredBottomSheet = true
        }
    }
    
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 当前会员状态
            if (currentStatus != null && currentStatus.isActive) {
                item {
                    CurrentMembershipCard(context, currentStatus)
                }
            }
            
            // 会员套餐列表
            item {
                Text(
                    text = context.getString(R.string.membership_plans_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(plans) { plan ->
                MembershipPlanCard(
                    context = context,
                    plan = plan,
                    onPurchase = { onPurchase(plan) }
                )
            }
            
            // 刷新按钮
            item {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(context.getString(R.string.membership_refresh))
                }
            }
        }
    }
    
    // 会员过期提示BottomSheet
    if (showExpiredBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExpiredBottomSheet = false },
            sheetState = bottomSheetState,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = context.getString(R.string.membership_expired),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = context.getString(R.string.membership_expired_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Button(
                    onClick = { showExpiredBottomSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(context.getString(R.string.confirm))
                }
            }
        }
    }
}

@Composable
private fun CurrentMembershipCard(
    context: Context,
    status: SubscriptionStatus
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = context.getString(R.string.current_membership),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "${context.getString(R.string.membership_plan)}: ${status.planName ?: "Unknown"}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            status.endDate?.let { endDate ->
                val dateString = java.time.Instant.ofEpochMilli(endDate)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .toString()
                val formattedDate = formatDateByLocale(dateString, context.resources.configuration.locale.toLanguageTag())
                Text(
                    text = "${context.getString(R.string.membership_expires_on)}: $formattedDate",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun MembershipPlanCard(
    context: Context,
    plan: SubscriptionPlan,
    onPurchase: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), plan.price),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            plan.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "${context.getString(R.string.duration)}: ${plan.duration} ${context.getString(R.string.days)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(context.getString(R.string.purchase))
            }
        }
    }
    
    // 购买确认对话框
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(context.getString(R.string.confirm_purchase)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = context.getString(R.string.confirm_purchase_message, plan.name, plan.price),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = context.getString(R.string.purchase_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onPurchase()
                    }
                ) {
                    Text(context.getString(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ErrorContent(
    context: Context,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = onRetry) {
            Text(context.getString(R.string.retry))
        }
    }
}
