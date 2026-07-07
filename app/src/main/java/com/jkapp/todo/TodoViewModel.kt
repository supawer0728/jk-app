package com.jkapp.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jkapp.auth.AuthRepository
import com.jkapp.auth.FirebaseAuthRepository
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TodoStatusFilter { ALL, ACTIVE, COMPLETED }
enum class TodoSortOption { DUE_DATE, PRIORITY, CREATED_AT }

class TodoViewModel(
    private val repository: TodoFirestoreRepository = TodoFirestoreRepositoryImpl(),
    private val reminderScheduler: TodoReminderScheduler = TodoReminderScheduler.NoOp,
    authRepository: AuthRepository = FirebaseAuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<TodoUiState>(TodoUiState.Loading)
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    private val _statusFilter = MutableStateFlow(TodoStatusFilter.ACTIVE)
    val statusFilter: StateFlow<TodoStatusFilter> = _statusFilter.asStateFlow()

    private val _categoryFilter = MutableStateFlow<Set<String>>(emptySet())
    val categoryFilter: StateFlow<Set<String>> = _categoryFilter.asStateFlow()

    private val _tagFilter = MutableStateFlow<Set<String>>(emptySet())
    val tagFilter: StateFlow<Set<String>> = _tagFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(TodoSortOption.DUE_DATE)
    val sortOption: StateFlow<TodoSortOption> = _sortOption.asStateFlow()

    private val _saveCompleted = MutableStateFlow(false)
    val saveCompleted: StateFlow<Boolean> = _saveCompleted.asStateFlow()

    fun consumeSaveCompleted() {
        _saveCompleted.value = false
    }

    // 필터+정렬 결과를 캐시해 탭 전환으로 컴포지션이 재생성되어도 재계산하지 않는다.
    // (DiaryViewModel.recordsByMonth와 동일한 목적, 이슈 #37 참고)
    val visibleItems: StateFlow<List<TodoItem>> = combine(
        _uiState,
        _statusFilter,
        _categoryFilter,
        _tagFilter,
        _sortOption,
    ) { state, status, categories, tags, sort ->
        val items = (state as? TodoUiState.Success)?.items.orEmpty()
        filterAndSort(items, status, categories, tags, sort)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var dataJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { isLoggedIn ->
                if (isLoggedIn) {
                    startDataCollection()
                } else {
                    dataJob?.cancel()
                    _uiState.value = TodoUiState.Loading
                }
            }
        }
    }

    private fun startDataCollection() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            combine(
                repository.getTodoItems(),
                repository.getCategories().onStart { emit(emptyList()) },
            ) { items, categories ->
                TodoUiState.Success(items = items, categories = categories) as TodoUiState
            }
                .catch { e ->
                    emit(TodoUiState.Error("할 일을 불러오는 중 오류가 발생했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"))
                }
                .collect { state -> _uiState.value = state }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dataJob?.cancel()
    }

    fun setStatusFilter(filter: TodoStatusFilter) {
        _statusFilter.value = filter
    }

    fun toggleCategoryFilter(categoryId: String) {
        _categoryFilter.update { toggleInSet(categoryId, it) }
    }

    fun clearCategoryFilter() {
        _categoryFilter.value = emptySet()
    }

    fun toggleTagFilter(tag: String) {
        _tagFilter.update { toggleInSet(tag, it) }
    }

    fun clearTagFilter() {
        _tagFilter.value = emptySet()
    }

    fun setSortOption(option: TodoSortOption) {
        _sortOption.value = option
    }

    // 진행 중인 반복 항목(recurrence != null && !isCompleted)은 completeOccurrence로 다음 회차로
    // in-place 전진시킨다. 이미 종료된 반복 항목(반복이 endAt을 지나 isCompleted=true로 고정된 경우)과
    // 비반복 항목은 완료 상태를 단순 토글한다 — 종료된 반복 항목도 이 분기를 타지 않으면 매 탭마다
    // completeOccurrence가 다시 실행되어 completionHistory에 같은 회차가 중복 누적된다.
    // 이전 회차(또는 이전 완료 상태)의 알림은 항상 취소하고, 결과가 미완료로 남을 때만(다음 회차 포함) 재예약한다.
    fun toggleCompleted(item: TodoItem) {
        val updated = if (item.recurrence != null && !item.isCompleted) {
            item.completeOccurrence(Instant.now())
        } else {
            val nowCompleted = !item.isCompleted
            item.copy(isCompleted = nowCompleted, completedAt = if (nowCompleted) Instant.now() else null)
        }
        viewModelScope.launch {
            runCatching { repository.updateTodoItem(updated) }
                .onSuccess {
                    reminderScheduler.cancel(item)
                    if (shouldScheduleReminder(updated)) reminderScheduler.schedule(updated)
                }
                .onFailure { e ->
                    _uiState.value = TodoUiState.Error("할 일 상태 변경에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}")
                }
        }
    }

    fun addTodoItem(item: TodoItem) {
        viewModelScope.launch {
            runCatching { repository.addTodoItem(item) }
                .onSuccess { id ->
                    val added = item.copy(firestoreId = id)
                    if (shouldScheduleReminder(added)) reminderScheduler.schedule(added)
                    _saveCompleted.value = true
                }
                .onFailure { e ->
                    _uiState.value = TodoUiState.Error("할 일 저장에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}")
                }
        }
    }

    fun updateTodoItem(item: TodoItem) {
        viewModelScope.launch {
            runCatching { repository.updateTodoItem(item) }
                .onSuccess {
                    reminderScheduler.cancel(item)
                    if (shouldScheduleReminder(item)) reminderScheduler.schedule(item)
                    _saveCompleted.value = true
                }
                .onFailure { e ->
                    _uiState.value = TodoUiState.Error("할 일 수정에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}")
                }
        }
    }

    fun deleteTodoItem(item: TodoItem) {
        val firestoreId = item.firestoreId ?: return
        viewModelScope.launch {
            runCatching { repository.deleteTodoItem(firestoreId) }
                .onSuccess { reminderScheduler.cancel(item) }
                .onFailure { e ->
                    _uiState.value = TodoUiState.Error("할 일 삭제에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}")
                }
        }
    }

    fun addCategory(category: TodoCategory) {
        viewModelScope.launch {
            runCatching { repository.addCategory(category) }
                .onFailure { e ->
                    _uiState.value = TodoUiState.Error("카테고리 저장에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}")
                }
        }
    }

    fun updateCategory(category: TodoCategory) {
        viewModelScope.launch {
            runCatching { repository.updateCategory(category) }
                .onFailure { e ->
                    _uiState.value = TodoUiState.Error("카테고리 수정에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}")
                }
        }
    }

    // 시스템 카테고리 개념이 없으므로(이슈 #54) 모든 카테고리를 제한 없이 삭제할 수 있다.
    // 삭제 시 해당 카테고리를 참조하던 항목들의 categoryId를 null로 재배정한다.
    fun deleteCategory(docId: String) {
        val state = uiState.value as? TodoUiState.Success ?: return
        val affectedItemIds = state.items.filter { it.categoryId == docId }.mapNotNull { it.firestoreId }
        viewModelScope.launch {
            runCatching { repository.deleteCategoryAndUnassignItems(docId, affectedItemIds) }
                .onFailure { e ->
                    _uiState.value = TodoUiState.Error("카테고리 삭제에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}")
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory =
            viewModelFactory { initializer { TodoViewModel() } }

        // 마감일시와 리마인더 오프셋이 모두 있어야 "몇 분 전에 알린다"는 리마인더가 의미를 가진다.
        fun shouldScheduleReminder(item: TodoItem): Boolean =
            !item.isCompleted && item.dueAt != null && item.reminderOffsetMinutes != null

        fun toggleInSet(id: String, current: Set<String>): Set<String> =
            if (id in current) current - id else current + id

        fun filterByStatus(items: List<TodoItem>, filter: TodoStatusFilter): List<TodoItem> = when (filter) {
            TodoStatusFilter.ALL -> items
            TodoStatusFilter.ACTIVE -> items.filter { !it.isCompleted }
            TodoStatusFilter.COMPLETED -> items.filter { it.isCompleted }
        }

        fun filterByCategory(items: List<TodoItem>, categoryIds: Set<String>): List<TodoItem> {
            if (categoryIds.isEmpty()) return items
            return items.filter { it.categoryId in categoryIds }
        }

        fun filterByTag(items: List<TodoItem>, tags: Set<String>): List<TodoItem> {
            if (tags.isEmpty()) return items
            return items.filter { item -> item.tags.any { it in tags } }
        }

        fun sortItems(items: List<TodoItem>, sortOption: TodoSortOption): List<TodoItem> = when (sortOption) {
            TodoSortOption.DUE_DATE -> items.sortedWith(compareBy(nullsLast()) { it.dueAt })
            TodoSortOption.PRIORITY -> items.sortedByDescending { it.priority.ordinal }
            TodoSortOption.CREATED_AT -> items.sortedByDescending { it.createdAt }
        }

        fun filterAndSort(
            items: List<TodoItem>,
            statusFilter: TodoStatusFilter,
            categoryFilter: Set<String>,
            tagFilter: Set<String>,
            sortOption: TodoSortOption,
        ): List<TodoItem> {
            val filtered = filterByTag(filterByCategory(filterByStatus(items, statusFilter), categoryFilter), tagFilter)
            return sortItems(filtered, sortOption)
        }
    }
}
