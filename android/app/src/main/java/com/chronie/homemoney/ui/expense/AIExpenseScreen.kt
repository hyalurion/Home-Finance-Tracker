package com.chronie.homemoney.ui.expense

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.chronie.homemoney.R
import com.chronie.homemoney.core.common.rememberCurrencySymbol
import com.chronie.homemoney.domain.model.AIExpenseRecord
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import com.chronie.homemoney.ui.components.MiuixDatePickerSheet
import com.chronie.homemoney.ui.components.OutlinedButton
import com.chronie.homemoney.ui.components.imageeditor.CropShape
import com.chronie.homemoney.ui.components.imageeditor.ImageEditorDialog
import com.chronie.homemoney.ui.components.imageeditor.compressBitmapToBytes
import com.chronie.homemoney.ui.scroll.RegisterScrollToTop
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * AI Expense Screen
 * Displays AI-generated expense records and allows user interaction
 */
@Composable
fun AIExpenseScreen(
    context: Context,
    onNavigateBack: () -> Unit,
    onRecordsSaved: () -> Unit,
    viewModel: AIExpenseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Image editor state (replaces uCrop)
    var editorUri by remember { mutableStateOf<Uri?>(null) }
    
    
    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        
        uris.forEach { uri ->
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {
            }
        }
        
        if (uris.size == 1) {
            editorUri = uris.first()
        } else {
            viewModel.addImages(uris.toList())
        }
    }

    // Camera temporary file URI
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) {
        if (it) {
            // Capture successful, open the image editor
            cameraImageUri?.let { uri -> editorUri = uri }
        }
    }
    
    // Track thumbnail positions for shared-element transitions
    val thumbnailBoundsMap = remember { mutableStateMapOf<Uri, Rect>() }
    var editorThumbnailBounds by remember { mutableStateOf<Rect?>(null) }

    // Handle existing image cropping request via the custom editor
    fun handleCropExistingImage(uri: Uri) {
        // Save thumbnail bounds for animation
        editorThumbnailBounds = thumbnailBoundsMap[uri]
        // Remove old image from selection list and open the editor
        viewModel.removeImage(uri)
        editorUri = uri
    }

    // Create temporary file for camera capture
    fun createImageFile(context: Context): Uri? {
        val tag = "AIExpenseScreen"
        return try {
            val timeStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            
            Log.d(tag, "Storage dir: $storageDir")
            
            // Ensure storage directory exists
            if (storageDir?.exists() != true) {
                Log.d(tag, "Creating storage dir: ${storageDir?.mkdirs()}")
            }
            
            // Create file
            val image = File(storageDir, "$imageFileName.jpg")
            
            Log.d(tag, "Image file path: ${image.absolutePath}")
            
            // If file already exists, delete it
            if (image.exists()) {
                Log.d(tag, "Deleting existing file: ${image.delete()}")
            }
            
            // Ensure file is created successfully
            if (image.createNewFile()) {
                Log.d(tag, "File created successfully")
                // Use FileProvider to create URI, avoiding FileUriExposedException
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    image
                )
                Log.d(tag, "Created URI: $uri")
                uri
            } else {
                Log.e(tag, "Failed to create file")
                null
            }
        } catch (ex: IOException) {
            Log.e(tag, "IOException in createImageFile: ${ex.message}", ex)
            null
        } catch (ex: Exception) {
            Log.e(tag, "Exception in createImageFile: ${ex.message}", ex)
            null
        }
    }

    // Camera permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        if (it) {
            // Permission granted, launch camera
            cameraImageUri = createImageFile(context)
            cameraImageUri?.let { uri ->
                cameraLauncher.launch(uri)
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = context.getString(R.string.ai_expense_title),
                navigationIcon = {
                    CircularIconButton(onClick = onNavigateBack, modifier = Modifier.padding(start = 8.dp, end = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.back))
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp))
                },
                color = MiuixTheme.colorScheme.surface
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Image selection section
            ImageSelectionSection(
                context = context,
                selectedImages = uiState.selectedImages,
                onAddImages = { source -> 
                    when (source) {
                        "camera" -> {
                            val hasCameraPermission = ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasCameraPermission) {
                                cameraImageUri = createImageFile(context)
                                cameraImageUri?.let { cameraLauncher.launch(it) }
                            } else {
                                permissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        }
                        "gallery" -> imagePickerLauncher.launch("image/*")
                    }
                },
                onRemoveImage = viewModel::removeImage,
                onCropImage = ::handleCropExistingImage,
                thumbnailBoundsMap = thumbnailBoundsMap,
                onEditorThumbnailBounds = { editorThumbnailBounds = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Text input section
            TextInputSection(
                context = context,
                textInput = uiState.textInput,
                onTextChange = viewModel::updateTextInput
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Recognition button
            Button(
                onClick = { viewModel.startRecognition() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading &&
                         (uiState.selectedImages.isNotEmpty() || uiState.textInput.isNotBlank()),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                if (uiState.isLoading) {
                    ExpressiveLoadingIndicator(containerVisible = false)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (uiState.isLoading)
                        context.getString(R.string.ai_expense_recognizing)
                    else
                        context.getString(R.string.ai_expense_start_recognition)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Recognized records list
            if (uiState.recognizedRecords.isNotEmpty()) {
                RecognizedRecordsSection(
                    context = context,
                    records = uiState.recognizedRecords,
                    onUpdateRecord = viewModel::updateRecord,
                    onDeleteRecord = viewModel::deleteRecord,
                    onSaveAll = { viewModel.saveAllRecords(onRecordsSaved) },
                    isSaving = uiState.isSaving
                )
            }
            
            // Error prompt
            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.footnote1
                )
            }
        }
    }

    // Save a cropped bitmap to a JPEG file (<= 2MB) and return its content Uri
    fun saveCroppedBitmap(bmp: Bitmap): Uri? {
        return try {
            val bytes = compressBitmapToBytes(bmp, 2 * 1024 * 1024, Bitmap.CompressFormat.JPEG, 90)
            val image = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "CROP_${System.currentTimeMillis()}.jpg")
            FileOutputStream(image).use { it.write(bytes) }
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
        } catch (e: Exception) {
            Log.e("AIExpenseScreen", "Failed to save cropped image", e)
            null
        }
    }

    ImageEditorDialog(
        uri = editorUri,
        cropShape = CropShape.SQUARE,
        enableEraser = true,
        maxResultSize = 1080,
        thumbnailBounds = editorThumbnailBounds,
        onDismiss = { 
            editorUri = null
            editorThumbnailBounds = null
        },
        onConfirm = { bmp ->
            saveCroppedBitmap(bmp)?.let { viewModel.addImages(listOf(it)) }
            editorUri = null
            editorThumbnailBounds = null
        }
    )
}

/**
 * Image selection section
 */
@Composable
private fun ImageSelectionSection(
    context: Context,
    selectedImages: List<Uri>,
    onAddImages: (String) -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onCropImage: (Uri) -> Unit,
    thumbnailBoundsMap: MutableMap<Uri, Rect>,
    onEditorThumbnailBounds: (Rect?) -> Unit
) {
    // Dropdown menu for image source selection
    val imageSourceEntry = remember {
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = context.getString(R.string.ai_expense_take_photo),
                    icon = { modifier -> Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = modifier.size(24.dp), tint = MiuixTheme.colorScheme.primary) },
                    onClick = { onAddImages("camera") }
                ),
                DropdownItem(
                    text = context.getString(R.string.ai_expense_choose_from_gallery),
                    icon = { modifier -> Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = modifier.size(24.dp), tint = MiuixTheme.colorScheme.primary) },
                    onClick = { onAddImages("gallery") }
                )
            )
        )
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.ai_expense_select_images),
                style = MiuixTheme.textStyles.body1
            )
            WindowIconDropdownMenu(entry = imageSourceEntry) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MiuixTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = context.getString(R.string.ai_expense_add_images),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }
        }
        
        if (selectedImages.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(selectedImages.size) { index ->
                    ImagePreviewCard(
                        imageUri = selectedImages[index],
                        onRemove = { onRemoveImage(selectedImages[index]) },
                        onCrop = { 
                            onEditorThumbnailBounds(thumbnailBoundsMap[selectedImages[index]])
                            onCropImage(selectedImages[index])
                        },
                        onPositioned = { bounds ->
                            thumbnailBoundsMap[selectedImages[index]] = bounds
                        }
                    )
                }
            }
        } else {
            WindowIconDropdownMenu(entry = imageSourceEntry) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MiuixTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MiuixTheme.colorScheme.outline)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        Text(
                            context.getString(R.string.ai_expense_click_to_add),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * Image preview card
 */
@Composable
private fun ImagePreviewCard(
    imageUri: Uri,
    onRemove: () -> Unit,
    onCrop: () -> Unit,
    onPositioned: (Rect) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .size(100.dp)
            .onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInWindow()
                val size = coordinates.size.toSize()
                onPositioned(Rect(pos, size))
            }
    ) {
        Box {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        onCrop()
                    },
                contentScale = ContentScale.Crop
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MiuixTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Text input section
 */
@Composable
private fun TextInputSection(
    context: Context,
    textInput: String,
    onTextChange: (String) -> Unit
) {
    Column {
        Text(
            text = context.getString(R.string.ai_expense_or_input_text),
            style = MiuixTheme.textStyles.body1
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = textInput,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            label = context.getString(R.string.ai_expense_text_hint),
            useLabelAsPlaceholder = true,
            maxLines = 5
        )
    }
}

/**
 * Recognized records section
 */
@Composable
private fun RecognizedRecordsSection(
    context: Context,
    records: List<AIExpenseRecord>,
    onUpdateRecord: (Int, AIExpenseRecord) -> Unit,
    onDeleteRecord: (Int) -> Unit,
    onSaveAll: () -> Unit,
    isSaving: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.ai_expense_records_count, records.size),
                style = MiuixTheme.textStyles.body1
            )
            Button(
                onClick = onSaveAll,
                enabled = !isSaving && records.any { it.isValid },
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                if (isSaving) {
                    ExpressiveLoadingIndicator(containerVisible = false)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (isSaving) 
                        context.getString(R.string.ai_expense_saving) 
                    else 
                        context.getString(R.string.ai_expense_save_all)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        val listState = rememberLazyListState()
        RegisterScrollToTop(listState)
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(records) { index, record ->
                RecordEditCard(
                    context = context,
                    record = record,
                    onUpdate = { updated -> onUpdateRecord(index, updated) },
                    onDelete = { onDeleteRecord(index) }
                )
            }
        }
    }
}


/**
 * Record edit card
 */
@Composable
private fun RecordEditCard(
    context: Context,
    record: AIExpenseRecord,
    onUpdate: (AIExpenseRecord) -> Unit,
    onDelete: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    val currencySymbol = rememberCurrencySymbol(context)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (record.isValid)
                MiuixTheme.colorScheme.surface
            else
                MiuixTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ExpenseTypeLocalizer.getLocalizedName(context, record.type),
                        style = MiuixTheme.textStyles.body1
                    )
                    Text(
                        text = context.getString(R.string.currency_format, currencySymbol, record.amount),
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.primary
                    )
                    Text(
                        text = record.date,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    if (record.remark.isNotBlank()) {
                        Text(
                            text = record.remark,
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (record.isEdited) {
                        Text(
                            text = context.getString(R.string.ai_expense_edited),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                
                Column {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = context.getString(R.string.ai_expense_edit_record))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = context.getString(R.string.ai_expense_delete_record),
                            tint = MiuixTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
    
    RecordEditDialog(
        show = showEditDialog,
        context = context,
        record = record,
        onDismiss = { showEditDialog = false },
        onConfirm = { updated ->
            onUpdate(updated)
            showEditDialog = false
        }
    )
}

/**
 * Record edit dialog
 */
@Composable
private fun RecordEditDialog(
    show: Boolean,
    context: Context,
    record: AIExpenseRecord,
    onDismiss: () -> Unit,
    onConfirm: (AIExpenseRecord) -> Unit
) {
    var selectedType by remember(show) { mutableStateOf(record.type) }
    var amount by remember(show) { mutableStateOf(record.amount.toString()) }
    var remark by remember(show) { mutableStateOf(record.remark) }
    var selectedDate by remember(show) { mutableStateOf(java.time.LocalDate.parse(record.date)) }
    var showTypePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    WindowDialog(
        show = show,
        title = context.getString(R.string.ai_expense_edit_record),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top header with X (cancel) and Check (confirm) icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularIconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = context.getString(R.string.cancel),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
                CircularIconButton(
                    onClick = {
                        val updatedRecord = record.copy(
                            type = selectedType,
                            amount = amount.toDoubleOrNull() ?: record.amount,
                            date = selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                            remark = remark,
                            isEdited = true
                        )
                        onConfirm(updatedRecord)
                    }
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = context.getString(R.string.confirm),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
            }

            // Type selection
            OutlinedButton(
                onClick = { showTypePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(ExpenseTypeLocalizer.getLocalizedName(context, selectedType))
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }

            // Amount input
            TextField(
                value = amount,
                onValueChange = { amount = it },
                label = context.getString(R.string.ai_expense_amount),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Date picker
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.DateRange, contentDescription = null)
            }

            // Remark input
            TextField(
                value = remark,
                onValueChange = { remark = it },
                label = context.getString(R.string.ai_expense_remark),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
        }
    }

    ExpenseTypePickerDialog(
        show = showTypePicker,
        context = context,
        selectedType = selectedType,
        onDismiss = { showTypePicker = false },
        onTypeSelected = { type ->
            selectedType = type
            showTypePicker = false
        }
    )

    MiuixDatePickerSheet(
        context = context,
        show = showDatePicker,
        initialDate = selectedDate,
        onDismiss = { showDatePicker = false },
        onDateSelected = { date -> selectedDate = date },
        title = context.getString(R.string.add_expense_date_label)
    )
}

/**
 * Expense type picker dialog - with search capability
 */
@Composable
private fun ExpenseTypePickerDialog(
    show: Boolean,
    context: Context,
    selectedType: ExpenseType,
    onDismiss: () -> Unit,
    onTypeSelected: (ExpenseType) -> Unit
) {
    var searchQuery by remember(show) { mutableStateOf("") }

    // Filter types based on search query
    val filteredTypes = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            ExpenseType.entries
        } else {
            ExpenseType.entries.filter { type ->
                val displayName = ExpenseTypeLocalizer.getLocalizedName(context, type)
                displayName.contains(searchQuery, ignoreCase = true) ||
                    type.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    WindowDialog(
        show = show,
        title = context.getString(R.string.ai_expense_select_type),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (filteredTypes.size != ExpenseType.entries.size) {
                Text(
                    text = context.getString(R.string.search_results_count, filteredTypes.size),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Search field
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = context.getString(R.string.search_category),
                useLabelAsPlaceholder = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = context.getString(R.string.clear))
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Category list
            if (filteredTypes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.getString(R.string.no_results_found),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            } else {
                val listState = rememberLazyListState()
                RegisterScrollToTop(listState)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(filteredTypes.size) { index ->
                        val type = filteredTypes[index]
                        TextButton(
                            text = ExpenseTypeLocalizer.getLocalizedName(context, type),
                            onClick = { onTypeSelected(type) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                textColor = if (type == selectedType)
                                    MiuixTheme.colorScheme.primary
                                else
                                    MiuixTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    }
}

