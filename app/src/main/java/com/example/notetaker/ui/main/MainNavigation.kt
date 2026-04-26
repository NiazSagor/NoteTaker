package com.example.notetaker.ui.main

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.notetaker.feature.auth.AuthScreen
import com.example.notetaker.feature.auth.AuthViewModel
import com.example.notetaker.feature.editor.NoteEditorScreen
import com.example.notetaker.feature.editor.NoteEditorViewModel
import com.example.notetaker.feature.workspace.WorkspaceScreen
import com.example.notetaker.feature.workspace.WorkspaceViewModel

@Composable
fun MainNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "auth") {

        composable("auth") {
            val viewModel: AuthViewModel = hiltViewModel()

            AuthScreen(
                viewModel = viewModel,
                onSignedIn = {
                    navController.navigate("workspace") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        composable("workspace") {
            val viewModel: WorkspaceViewModel = hiltViewModel()
            WorkspaceScreen(
                viewModel = viewModel,
                onNoteClick = { noteId ->
                    navController.navigate("editor/$noteId")
                }
            )
        }
        composable(
            route = "editor/{noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) {
            val viewModel: NoteEditorViewModel = hiltViewModel()
            NoteEditorScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}