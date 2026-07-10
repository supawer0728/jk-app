package com.jkapp.todo

sealed interface TodoUiState {
    data object Loading : TodoUiState
    data class Success(
        val items: List<TodoItem>,
    ) : TodoUiState
    data class Error(val message: String) : TodoUiState
}
