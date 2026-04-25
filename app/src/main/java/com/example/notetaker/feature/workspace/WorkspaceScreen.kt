package com.example.notetaker.feature.workspace

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
    val gridState = rememberLazyGridState()
    val navigateToNoteEditor by viewModel.navigateToNoteEditor.collectAsState(initial = null)
    LaunchedEffect(navigateToNoteEditor) {
        navigateToNoteEditor?.let { noteId ->
            if (noteId.isNotEmpty()) {
                onNoteClick(noteId)
            }
        }
    }

    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("NoteTaker Workspace") })
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pointerInput(uiState.gridElements) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                gridState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { item ->
                                        offset.x.toInt() in item.offset.x..(item.offset.x + item.size.width) &&
                                                offset.y.toInt() in item.offset.y..(item.offset.y + item.size.height)
                                    }
                                    ?.let { draggedItemIndex = it.index }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                            },
                            onDragEnd = {
                                val fromIndex = draggedItemIndex
                                if (fromIndex != null) {
                                    val draggedItemInfo = gridState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == fromIndex }

                                    if (draggedItemInfo != null) {
                                        val finalPos = Offset(
                                            draggedItemInfo.offset.x.toFloat() + dragOffset.x + (draggedItemInfo.size.width / 2),
                                            draggedItemInfo.offset.y.toFloat() + dragOffset.y + (draggedItemInfo.size.height / 2)
                                        )

                                        val targetItem =
                                            gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                                finalPos.x.toInt() in item.offset.x..(item.offset.x + item.size.width) &&
                                                        finalPos.y.toInt() in item.offset.y..(item.offset.y + item.size.height)
                                            }

                                        if (targetItem != null && targetItem.index != fromIndex) {
                                            val elements = uiState.gridElements
                                            val targetIdx = targetItem.index

                                            val newIndex = when {
                                                targetIdx == 0 -> {
                                                    elements[0].element.orderIndex / 2.0
                                                }

                                                targetIdx == elements.lastIndex -> {
                                                    elements.last().element.orderIndex + 1.0
                                                }

                                                else -> {
                                                    val prevIdx =
                                                        if (targetIdx > fromIndex) targetIdx else targetIdx - 1
                                                    val nextIdx =
                                                        if (targetIdx > fromIndex) targetIdx + 1 else targetIdx

                                                    val prev = elements[prevIdx].element.orderIndex
                                                    val next =
                                                        elements.getOrNull(nextIdx)?.element?.orderIndex
                                                            ?: (prev + 1.0)
                                                    (prev + next) / 2.0
                                                }
                                            }
                                            viewModel.onEvent(
                                                WorkspaceEvent.OnReorder(
                                                    elements[fromIndex].element.id,
                                                    newIndex
                                                )
                                            )
                                        }
                                    }
                                }
                                draggedItemIndex = null
                                dragOffset = Offset.Zero
                            },
                            onDragCancel = {
                                draggedItemIndex = null
                                dragOffset = Offset.Zero
                            }
                        )
                    },
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    uiState.gridElements,
                    key = { _, it -> it.element.id }) { index, item ->
                    val isDragging = index == draggedItemIndex
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 2.dp)

                    WorkspaceTile(
                        elementWithContent = item,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationX = dragOffset.x
                                    translationY = dragOffset.y
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                }
                            }
                            .shadow(elevation, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp)),
                        onClick = {
                            if (item.element.type == GridElementType.NOTE) {
                                item.element.noteId?.let { onNoteClick(it) }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WorkspaceTile(
    elementWithContent: GridElementWithContent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val element = elementWithContent.element

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
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
