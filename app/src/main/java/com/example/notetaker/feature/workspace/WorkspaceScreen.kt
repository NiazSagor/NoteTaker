package com.example.notetaker.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.notetaker.core.data.db.entity.GridElementWithContent
import com.example.notetaker.core.domain.model.GridElementType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    onNoteClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NoteTaker Workspace") }
            )
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
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp
            ) {
                items(uiState.gridElements, key = { it.element.id }) { elementWithContent ->
                    WorkspaceTile(
                        elementWithContent = elementWithContent,
                        onClick = {
                            if (elementWithContent.element.type == GridElementType.NOTE) {
                                elementWithContent.element.noteId?.let { onNoteClick(it) }
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
    onClick: () -> Unit
) {
    val element = elementWithContent.element
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ImageTileContent(imageUrl: String?) {
    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.FillWidth
    )
}
