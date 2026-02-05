package com.chronie.homemoney.ui.membership

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.SubscriptionStatus
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.expense.formatDateByLocale
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionHistoryScreen(
    context: Context,
    onNavigateBack: () -> Unit,
    viewModel: MembershipViewModel = hiltViewModel()
) {
    val historyState by viewModel.historyState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadSubscriptionHistory()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.subscription_history)) },
                navigationIcon = {
                    CircularIconButton(onClick = onNavigateBack, modifier = Modifier.padding(start = 8.dp, end = 4.dp)) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = context.getString(R.string.back)
                        )
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        when (val state = historyState) {
            is HistoryUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveLoadingIndicator()
                }
            }
            
            is HistoryUiState.Success -> {
                if (state.history.isEmpty()) {
                    EmptyHistoryContent(
                        context = context,
                        modifier = Modifier.padding(paddingValues)
                    )
                } else {
                    HistoryListContent(
                        context = context,
                        history = state.history,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
            
            is HistoryUiState.Error -> {
                ErrorContent(
                    context = context,
                    message = state.message,
                    onRetry = { viewModel.loadSubscriptionHistory() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun HistoryListContent(
    context: Context,
    history: List<SubscriptionStatus>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(history) { subscription ->
            SubscriptionHistoryCard(context, subscription)
        }
    }
}

@Composable
private fun SubscriptionHistoryCard(
    context: Context,
    subscription: SubscriptionStatus
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subscription.planName ?: context.getString(R.string.unknown_plan),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                SubscriptionStatusChip(context, subscription.status ?: "unknown")
            }
            
            subscription.startDate?.let { startDate ->
                val dateTime = java.time.Instant.ofEpochMilli(startDate)
                    .atZone(java.time.ZoneId.systemDefault())
                val localDate = dateTime.toLocalDate()
                val localTime = dateTime.toLocalTime()
                val dateString = localDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                val timeString = localTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                val formattedDate = formatDateByLocale(dateString, context.resources.configuration.locale.toLanguageTag())
                Text(
                    text = "${context.getString(R.string.start_date)}: $formattedDate $timeString",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            subscription.endDate?.let { endDate ->
                val dateTime = java.time.Instant.ofEpochMilli(endDate)
                    .atZone(java.time.ZoneId.systemDefault())
                val localDate = dateTime.toLocalDate()
                val localTime = dateTime.toLocalTime()
                val dateString = localDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                val timeString = localTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                val formattedDate = formatDateByLocale(dateString, context.resources.configuration.locale.toLanguageTag())
                Text(
                    text = "${context.getString(R.string.end_date)}: $formattedDate $timeString",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SubscriptionStatusChip(
    context: Context,
    status: String
) {
    val (text, color) = when (status.lowercase()) {
        "active" -> context.getString(R.string.status_active) to MaterialTheme.colorScheme.primary
        "expired" -> context.getString(R.string.status_expired) to MaterialTheme.colorScheme.error
        "cancelled" -> context.getString(R.string.status_cancelled) to MaterialTheme.colorScheme.tertiary
        else -> status to MaterialTheme.colorScheme.onSurface
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun EmptyHistoryContent(
    context: Context,
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
            text = context.getString(R.string.no_subscription_history),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
