package com.example.notetaker.ui.main

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.notetaker.feature.workspace.WorkspaceScreen
import com.example.notetaker.feature.workspace.WorkspaceViewModel

@Composable
fun MainNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "workspace") {
        composable("workspace") {
            val viewModel: WorkspaceViewModel = hiltViewModel()
            WorkspaceScreen(
                viewModel = viewModel,
                onNoteClick = { noteId ->
                    // Navigation to editor will be added later
                }
            )
        }
    }
}
