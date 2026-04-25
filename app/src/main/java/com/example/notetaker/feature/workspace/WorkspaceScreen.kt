package com.example.notetaker.feature.workspace

import android.util.Log
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.notetaker.core.domain.model.GridElementType
import com.example.notetaker.core.domain.model.GridElementWithContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    onNoteClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigateToNoteEditor by viewModel.navigateToNoteEditor.collectAsState(initial = null)
    LaunchedEffect(navigateToNoteEditor) {
        navigateToNoteEditor?.let { noteId ->
            if (noteId.isNotEmpty()) {
                onNoteClick(noteId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Workspace") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onEvent(WorkspaceEvent.OnCreateNote) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Note")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            WorkspaceGrid(
                viewModel = viewModel,
                onElementClick = onNoteClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}


@Composable
fun WorkspaceGrid(
    viewModel: WorkspaceViewModel,
    modifier: Modifier,
    onElementClick: (String) -> Unit // Handle the click here
) {
    val state by viewModel.uiState.collectAsState()
    var draggedItemId by remember { mutableStateOf<String?>(null) }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp
    ) {
        items(
            items = state.gridElements,
            key = { it.element.id }
        ) { wrapper ->
            val isDragging = draggedItemId == wrapper.element.id

            WorkspaceTile(
                elementWithContent = wrapper,
                isDragging = isDragging,
                onClick = { onElementClick(wrapper.note?.id!!) }, // Pass the missing parameter
                onBoundsChanged = { rect ->
                    viewModel.updateItemBounds(wrapper.element.id, rect)
                },
                modifier = Modifier
                    .animateItem()
                    .pointerInput(wrapper.element.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggedItemId = wrapper.element.id },
                            onDragEnd = {
                                draggedItemId = null
                                viewModel.onDragEnd()
                            },
                            onDragCancel = { draggedItemId = null },
                            onDrag = { change, _ ->
                                // Safe access to bounds for coordinate transformation
                                viewModel.getItemBounds(wrapper.element.id)?.let { rect ->
                                    viewModel.onDragMove(
                                        draggedId = wrapper.element.id,
                                        fingerPosition = change.position + rect.topLeft
                                    )
                                }
                            }
                        )
                    }
            )
        }
    }
}

@Composable
fun WorkspaceTile(
    elementWithContent: GridElementWithContent,
    modifier: Modifier = Modifier,
    isDragging: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    onClick: () -> Unit
) {
    val element = elementWithContent.element
    val note = elementWithContent.note
    val shape = RoundedCornerShape(12.dp)

    Card(
        onClick = onClick,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                onBoundsChanged(coords.boundsInWindow())
            }
            .graphicsLayer {
                val scale = if (isDragging) 1.05f else 1f
                scaleX = scale
                scaleY = scale
                alpha = if (isDragging) 0.8f else 1f
                shadowElevation = if (isDragging) 12f else 2f
                this.shape = shape
                this.clip = true
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        when (element.type) {
            GridElementType.NOTE -> {
                NoteTileContent(
                    title = elementWithContent.note?.title ?: "Untitled",
                    content = elementWithContent.note?.content ?: ""
                )
            }

            GridElementType.STANDALONE_IMAGE -> {
                ImageTileContent(
                    imageUrl = element.remoteImageUrl ?: element.localImageUri
                )
            }
        }
    }
}

@Composable
fun NoteTileContent(title: String, content: String) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ImageTileContent(imageUrl: String?) {
    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}
