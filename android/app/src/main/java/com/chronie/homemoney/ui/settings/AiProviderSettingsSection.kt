package com.chronie.homemoney.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.chat.AiProviderConfig
import com.chronie.homemoney.domain.model.chat.ProviderFormat
import com.chronie.homemoney.domain.model.chat.ProviderToolMode
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.components.ExpressiveSwitch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Settings block for AI chat model sources and tool permissions.
 *
 * Users can keep the built-in on-device model or add any number of
 * OpenAI-/Anthropic-compatible endpoints. API keys are written to encrypted
 * preferences by [com.chronie.homemoney.data.local.AiProviderConfigManager].
 */
@Composable
fun AiProviderSettingsSection(
    context: Context,
    viewModel: AiProviderSettingsViewModel = hiltViewModel()
) {
    val providers by viewModel.providers.collectAsState()
    val selectable by viewModel.selectableProviders.collectAsState()
    val activeId by viewModel.activeProviderId.collectAsState()
    val queryAuthorized by viewModel.queryAuthorized.collectAsState()
    val autoWrite by viewModel.autoWriteRecords.collectAsState()
    val onDeviceEnabled by viewModel.onDeviceEnabled.collectAsState()

    var editing by remember { mutableStateOf<AiProviderConfig?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var isNewProvider by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AiProviderConfig?>(null) }

    val names = selectable.map { provider ->
        if (provider.isBuiltIn) {
            context.getString(R.string.settings_ai_provider_builtin)
        } else {
            provider.name
        }
    }

    Column {
        Text(
            text = context.getString(R.string.settings_ai_provider_title),
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        WindowDropdownPreference(
            items = names,
            selectedIndex = selectable.indexOfFirst { it.id == activeId }.coerceAtLeast(0),
            title = context.getString(R.string.settings_ai_provider_active),
            onSelectedIndexChange = { index ->
                selectable.getOrNull(index)?.let { viewModel.setActive(it.id) }
            }
        )

        Text(
            text = context.getString(R.string.settings_ai_on_device_warning),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.settings_ai_on_device_enabled),
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            ExpressiveSwitch(
                checked = onDeviceEnabled,
                onCheckedChange = viewModel::setOnDeviceEnabled
            )
        }
        Text(
            text = context.getString(R.string.settings_ai_provider_builtin_desc),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(start = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        providers.filterNot { it.isBuiltIn }.forEach { provider ->
            ArrowPreference(
                title = provider.name,
                summary = "${provider.format.name} · ${provider.model}",
                onClick = {
                    editing = provider
                    isNewProvider = false
                    showEditor = true
                }
            )
        }

        Button(
            onClick = {
                editing = null
                isNewProvider = true
                viewModel.clearTestState()
                showEditor = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = context.getString(R.string.settings_ai_provider_add))
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // Explicit consent gate: query tools are not even exposed to the model
        // until this switch is enabled.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.settings_ai_query_permission),
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            ExpressiveSwitch(
                checked = queryAuthorized,
                onCheckedChange = viewModel::setQueryAuthorized
            )
        }
        Text(
            text = context.getString(R.string.settings_ai_query_permission_desc),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.settings_ai_auto_write),
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            ExpressiveSwitch(
                checked = autoWrite,
                onCheckedChange = viewModel::setAutoWriteRecords
            )
        }
        Text(
            text = context.getString(R.string.settings_ai_auto_write_desc),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(start = 16.dp)
        )
    }

    AiProviderEditorDialog(
        show = showEditor,
        isNew = isNewProvider,
        editing = editing,
        viewModel = viewModel,
        context = context,
        onOpenDelete = { target ->
            showEditor = false
            deleteTarget = target
        },
        onDismiss = {
            showEditor = false
            viewModel.clearTestState()
        }
    )

    WindowDialog(
        show = deleteTarget != null,
        title = context.getString(R.string.settings_ai_provider_delete_title),
        summary = deleteTarget?.name.orEmpty(),
        onDismissRequest = { deleteTarget = null }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularIconButton(onClick = { deleteTarget = null }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = context.getString(R.string.cancel),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            CircularIconButton(
                onClick = {
                    deleteTarget?.let { viewModel.delete(it.id) }
                    deleteTarget = null
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = context.getString(R.string.settings_ai_provider_delete),
                    tint = MiuixTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AiProviderEditorDialog(
    show: Boolean,
    isNew: Boolean,
    editing: AiProviderConfig?,
    viewModel: AiProviderSettingsViewModel,
    context: Context,
    onOpenDelete: (AiProviderConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(show, editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var baseUrl by remember(show, editing?.id) { mutableStateOf(editing?.baseUrl.orEmpty()) }
    var model by remember(show, editing?.id) { mutableStateOf(editing?.model.orEmpty()) }
    var apiKey by remember(show, editing?.id) { mutableStateOf("") }
    var formatIndex by remember(show, editing?.id) {
        mutableIntStateOf(if (editing?.format == ProviderFormat.ANTHROPIC) 1 else 0)
    }
    var toolModeIndex by remember(show, editing?.id) {
        mutableIntStateOf(
            when (editing?.resolvedToolMode ?: ProviderToolMode.AUTO) {
                ProviderToolMode.AUTO -> 0
                ProviderToolMode.TEXT -> 1
                ProviderToolMode.OFF -> 2
            }
        )
    }
    var localError by remember(show, editing?.id) { mutableStateOf<String?>(null) }
    val testState by viewModel.testState.collectAsState()

    val format = if (formatIndex == 1) ProviderFormat.ANTHROPIC else ProviderFormat.OPENAI
    val formatItems = listOf(
        context.getString(R.string.settings_ai_provider_format_openai),
        context.getString(R.string.settings_ai_provider_format_anthropic)
    )
    val toolMode = when (toolModeIndex) {
        1 -> ProviderToolMode.TEXT
        2 -> ProviderToolMode.OFF
        else -> ProviderToolMode.AUTO
    }
    val toolModeItems = listOf(
        context.getString(R.string.settings_ai_tool_mode_auto),
        context.getString(R.string.settings_ai_tool_mode_text),
        context.getString(R.string.settings_ai_tool_mode_off)
    )

    fun saveProvider() {
        val result = viewModel.addOrUpdate(
            id = editing?.id,
            name = name,
            format = format,
            baseUrl = baseUrl,
            model = model,
            apiKey = apiKey,
            toolMode = toolMode
        )
        if (result.isSuccess) {
            onDismiss()
        } else {
            localError = context.getString(R.string.settings_ai_provider_required) +
                " (${result.exceptionOrNull()?.message.orEmpty()})"
        }
    }

    WindowBottomSheet(
        show = show,
        title = context.getString(
            if (isNew) R.string.settings_ai_provider_add else R.string.settings_ai_provider_edit
        ),
        startAction = {
            CircularIconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = context.getString(R.string.cancel),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        endAction = {
            CircularIconButton(onClick = { saveProvider() }) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = context.getString(R.string.settings_ai_provider_save),
                    tint = MiuixTheme.colorScheme.primary
                )
            }
        },
        onDismissRequest = onDismiss,
        insideMargin = DpSize(16.dp, 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = name,
                onValueChange = { name = it; localError = null },
                modifier = Modifier.fillMaxWidth(),
                label = context.getString(R.string.settings_ai_provider_name),
                useLabelAsPlaceholder = true,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            WindowDropdownPreference(
                items = formatItems,
                selectedIndex = formatIndex,
                title = context.getString(R.string.settings_ai_provider_format),
                onSelectedIndexChange = { formatIndex = it }
            )
            Spacer(modifier = Modifier.height(8.dp))

            WindowDropdownPreference(
                items = toolModeItems,
                selectedIndex = toolModeIndex,
                title = context.getString(R.string.settings_ai_provider_tool_mode),
                onSelectedIndexChange = { toolModeIndex = it }
            )
            Text(
                text = context.getString(R.string.settings_ai_tool_mode_hint),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; localError = null },
                modifier = Modifier.fillMaxWidth(),
                label = context.getString(R.string.settings_ai_provider_base_url_hint),
                useLabelAsPlaceholder = true,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = model,
                onValueChange = { model = it; localError = null },
                modifier = Modifier.fillMaxWidth(),
                label = context.getString(R.string.settings_ai_provider_model),
                useLabelAsPlaceholder = true,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = apiKey,
                onValueChange = { apiKey = it; localError = null },
                modifier = Modifier.fillMaxWidth(),
                label = context.getString(R.string.settings_ai_provider_api_key_hint),
                useLabelAsPlaceholder = true,
                singleLine = true
            )
            if (!isNew && editing?.hasApiKey == true) {
                Text(
                    text = context.getString(R.string.settings_ai_provider_api_key_keep),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.testConnection(
                        editing?.id, name, format, baseUrl, model, apiKey, toolMode
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = testState !is ProviderTestState.Testing
            ) {
                Text(text = context.getString(R.string.settings_ai_provider_test))
            }
            when (val state = testState) {
                is ProviderTestState.Success -> Text(
                    text = context.getString(R.string.settings_ai_provider_test_ok, state.message),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
                is ProviderTestState.Failed -> Text(
                    text = state.message,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
                else -> Unit
            }

            localError?.let {
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (!isNew && editing != null) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    text = context.getString(R.string.settings_ai_provider_delete),
                    onClick = { onOpenDelete(editing) }
                )
            }
        }
    }
}
