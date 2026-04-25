package com.example.notetaker.feature.editor

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.notetaker.core.domain.model.Note
import com.example.notetaker.core.domain.model.NoteImage

private const val TAG = "NoteEditorScreen"
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onEvent(NoteEditorEvent.OnAddImage(it.toString())) }
    }

    Log.e(TAG, "NoteEditorScreen: ${uiState.note?.content}", )

    Scaffold(
        topBar = {
            TopAppBar(
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
