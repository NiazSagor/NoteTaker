package com.example.notetaker.feature.editor

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.notetaker.core.domain.model.Note
import com.example.notetaker.core.domain.model.NoteImage
import com.example.notetaker.core.domain.model.ResolutionStrategy
import com.example.notetaker.feature.conflict.ConflictEvent
import com.example.notetaker.feature.conflict.ConflictViewModel
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAG = "NoteEditorScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    val touchState by viewModel.touchState.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onEvent(NoteEditorEvent.OnAddImage(it.toString())) }
    }

    Log.e(TAG, "NoteEditorScreen: ${uiState.note?.content}")

    Scaffold(
        topBar = {
            TopAppBar(
                actions = {
                    // Conditionally show conflict indicator icon if conflictDetails is not null
                    if (uiState.hasConflict == true) {
                        IconButton(onClick = {
                            showSheet = true
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = "Conflict detected, resolve manually",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                title = { Text("Edit Note") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(onClick = { launcher.launch("image/*") }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Image"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Dedicated Image Section
            if (uiState.images.isNotEmpty()) {
                NoteImageGallery(
                    touchState,
                    images = uiState.images,
                    onImageClick = { viewModel.onEvent(NoteEditorEvent.OnImageSelected(it.id)) },
                    onPointerEvent = { imageId, bounds, event, currentRotation ->
                        viewModel.onImagePointerEvent(
                            imageId, bounds, event, currentRotation
                        )
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Title Field
            TextField(
                value = uiState.draftTitle,
                onValueChange = { viewModel.onEvent(NoteEditorEvent.OnTitleChange(it)) },
                placeholder = { Text("Title", style = MaterialTheme.typography.headlineSmall) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Content Field
            TextField(
                value = uiState.draftContent,
                onValueChange = { viewModel.onEvent(NoteEditorEvent.OnContentChange(it)) },
                placeholder = { Text("Start typing...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 200.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState()
        ConflictBottomSheet(
            sheetState = sheetState,
            onDismiss = { showSheet = false }
        )
    }
}

@Composable
fun NoteImageGallery(
    touchState: ImageTouchState,
    images: List<NoteImage>,
    onImageClick: (NoteImage) -> Unit,
    onPointerEvent: (String, Rect, PointerEvent, Float) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        items(images, key = { it.id }) { image ->

            NoteImage(
                image = image,
                touchState = touchState,
                onPointerEvent = onPointerEvent
            )
        }
    }
}

@Composable
fun NoteImage(
    image: NoteImage,
    touchState: ImageTouchState,
    onPointerEvent: (imageId: String, bounds: Rect, event: PointerEvent, currentRotation: Float) -> Unit
) {
    var imageBounds by remember { mutableStateOf(Rect.Zero) }

    // Derive visual state from touchState
    val isSelected = touchState.let {
        it is ImageTouchState.Selected && it.imageId == image.id ||
                it is ImageTouchState.RotationReady && it.imageId == image.id ||
                it is ImageTouchState.Rotating && it.imageId == image.id
    }

    val isRotating = touchState is ImageTouchState.Rotating
            && (touchState as ImageTouchState.Rotating).imageId == image.id

    val displayRotation = if (isRotating) {
        (touchState as ImageTouchState.Rotating).currentDegrees
    } else {
        image.rotationDegrees
    }

    Box(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                imageBounds = coordinates.boundsInWindow()
            }
            .graphicsLayer {
                rotationZ = displayRotation
            }
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color.Blue else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .pointerInput(image.id) {
                awaitPointerEventScope {
                    while (true) {
                        // Collect EVERY pointer event — press, move, release
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        onPointerEvent(
                            image.id,
                            imageBounds,
                            event,
                            image.rotationDegrees
                        )
                    }
                }
            }
    ) {
        AsyncImage(
            model = image.remoteImageUrl ?: image.localImageUri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        // HUD — only visible during ROTATING state for this image
        if (isRotating) {
            RotationHud(
                degrees = (touchState as ImageTouchState.Rotating).currentDegrees
            )
        }
    }
}

@Composable
fun RotationHud(degrees: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f - 16.dp.toPx()

        // Draw the arc from 0 to current rotation
        drawArc(
            color = Color.White.copy(alpha = 0.85f),
            startAngle = -90f,               // start from top
            sweepAngle = degrees % 360f,     // sweep to current rotation
            useCenter = false,
            topLeft = Offset(
                center.x - radius,
                center.y - radius
            ),
            size = Size(radius * 2, radius * 2),
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        // Draw degree text in center
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 28.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
            }
            drawText(
                "${degrees.roundToInt()}°",
                center.x,
                center.y + (paint.textSize / 3),   // vertically center text
                paint
            )
        }

        // Draw a small direction indicator line
        val angleRad = Math.toRadians((degrees - 90.0))
        val lineEnd = Offset(
            center.x + (radius * cos(angleRad)).toFloat(),
            center.y + (radius * sin(angleRad)).toFloat()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = center,
            end = lineEnd,
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictBottomSheet(
    viewmodel: ConflictViewModel = hiltViewModel(),
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    val conflict by viewmodel.uiState.collectAsState()
    val id = conflict.conflicts.firstOrNull()?.noteId ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = "Conflict detected",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Choose which version to keep",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewmodel.onEvent(
                            ConflictEvent.Resolve(
                                conflictId = id,
                                strategy = ResolutionStrategy.KEEP_LOCAL
                            )
                        )
                    }
                ) { Text("Keep Local") }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewmodel.onEvent(
                            ConflictEvent.Resolve(
                                conflictId = id,
                                strategy = ResolutionStrategy.KEEP_REMOTE
                            )
                        )
                    }
                ) { Text("Keep Remote") }
            }

            Spacer(Modifier.height(16.dp))

            // Content (scrollable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                VersionCard(
                    label = "Local",
                    note = conflict.conflicts.first().localNote
                        ?: conflict.conflicts.first().remoteNote!!,
                )

                VersionCard(
                    label = "Remote",
                    note = conflict.conflicts.first().remoteNote
                        ?: conflict.conflicts.first().localNote!!,
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun VersionCard(
    label: String,
    note: Note,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = note.title.ifBlank { "(No title)" },
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = note.content.ifBlank { "(No content)" },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
