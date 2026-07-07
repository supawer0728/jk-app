package com.jkapp.todo

sealed interface TodoUiState {
    data object Loading : TodoUiState
    data class Success(
        val items: List<TodoItem>,
        val categories: List<TodoCategory>,
    ) : TodoUiState
    data class Error(val message: String) : TodoUiState
}
