package com.example.notetaker.feature.editor

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.example.notetaker.core.domain.model.Note
import com.example.notetaker.core.domain.model.NoteImage
import com.example.notetaker.core.domain.model.ResolutionStrategy
import com.example.notetaker.feature.conflict.ConflictEvent
import com.example.notetaker.feature.conflict.ConflictViewModel

private const val TAG = "NoteEditorScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSheet by remember { mutableStateOf(false) }

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
                            // TODO: Trigger ViewModel event to show the conflict resolution dialog/sheet.
                            // This event might look like: viewModel.onEvent(NoteEditorEvent.OnShowConflictDialog(conflict.id))
                            // You'll need to define this event and its handler in the ViewModel and Screen.
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
                    images = uiState.images,
                    onImageClick = { viewModel.onEvent(NoteEditorEvent.OnImageSelected(it.id)) }
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
    images: List<NoteImage>,
    onImageClick: (NoteImage) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        items(images, key = { it.id }) { image ->
            AsyncImage(
                model = image.localImageUri ?: image.remoteImageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clickable { onImageClick(image) },
                contentScale = ContentScale.Crop
            )
        }
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
