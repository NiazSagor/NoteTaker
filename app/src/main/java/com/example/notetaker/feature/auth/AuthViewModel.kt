package com.example.notetaker.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notetaker.core.domain.base.Result
import com.example.notetaker.core.domain.usecase.auth.ObserveUserIdUseCase
import com.example.notetaker.core.domain.usecase.auth.SignInAnonymouslyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val userId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class AuthEvent {
    object SignInAnonymously : AuthEvent()
    // Potentially other events like LinkGoogleAccount, SignOut, etc.
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val observeUserIdUseCase: ObserveUserIdUseCase,
    private val signInAnonymouslyUseCase: SignInAnonymouslyUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        observeAuthenticationState()
    }

    private fun observeAuthenticationState() {
        observeUserIdUseCase(Unit)
            .onEach { result ->
                when (result) {
                    is Result.Success -> {
                        _uiState.update { it.copy(userId = result.data, isLoading = false) }
                    }
                    is Result.Error -> {
                        _uiState.update { it.copy(error = result.exception.message, isLoading = false) }
                    }
                    Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: AuthEvent) {
        when (event) {
            AuthEvent.SignInAnonymously -> signInAnonymously()
        }
    }

    private fun signInAnonymously() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = signInAnonymouslyUseCase(Unit)
            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(userId = result.data, isLoading = false) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(error = result.exception.message, isLoading = false) }
                }
                Result.Loading -> { /* Should not happen for a one-shot use case */ }
            }
        }
    }
}
