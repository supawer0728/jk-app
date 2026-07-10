package com.jkapp.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jkapp.auth.AuthRepository
import com.jkapp.auth.FirebaseAuthRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 목록 필터(이슈 #71). 오늘/전체/반복/완료 4종.
enum class TodoStatusFilter { TODAY, ALL, RECURRING, COMPLETED }

class TodoViewModel(
    private val repository: TodoFirestoreRepository = TodoFirestoreRepositoryImpl(),
    private val reminderScheduler: TodoReminderScheduler = TodoReminderScheduler.NoOp,
    authRepository: AuthRepository = FirebaseAuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<TodoUiState>(TodoUiState.Loading)
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    private val _statusFilter = MutableStateFlow(TodoStatusFilter.TODAY)
    val statusFilter: StateFlow<TodoStatusFilter> = _statusFilter.asStateFlow()

    private val _assigneeFilter = MutableStateFlow<Set<TodoAssignee>>(emptySet())
    val assigneeFilter: StateFlow<Set<TodoAssignee>> = _assigneeFilter.asStateFlow()

    private val _saveCompleted = MutableStateFlow(false)
    val saveCompleted: StateFlow<Boolean> = _saveCompleted.asStateFlow()

    fun consumeSaveCompleted() {
        _saveCompleted.value = false
    }

    // 필터+정렬 결과를 캐시해 탭 전환으로 컴포지션이 재생성되어도 재계산하지 않는다.
    // (DiaryViewModel.recordsByMonth와 동일한 목적, 이슈 #37 참고)
    // "오늘" 필터는 현재 시각에 의존하므로, 필터/상태가 바뀔 때마다 Instant.now()를 새로 읽어 판정한다.
    val visibleItems: StateFlow<List<TodoItem>> = combine(
        _uiState,
        _statusFilter,
        _assigneeFilter,
    ) { state, status, assignees ->
        val items = (state as? TodoUiState.Success)?.items.orEmpty()
        filterAndSort(items, status, assignees, Instant.now(), ZoneId.systemDefault())
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
            repository.getTodoItems()
                .catch { e ->
                    emit(emptyList())
                    _uiState.value =
                        TodoUiState.Error("할 일을 불러오는 중 오류가 발생했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}")
                }
                .collect { items -> _uiState.value = TodoUiState.Success(items = items) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dataJob?.cancel()
    }

    fun setStatusFilter(filter: TodoStatusFilter) {
        _statusFilter.value = filter
    }

    fun toggleAssigneeFilter(assignee: TodoAssignee) {
        _assigneeFilter.update { if (assignee in it) it - assignee else it + assignee }
    }

    fun clearAssigneeFilter() {
        _assigneeFilter.value = emptySet()
    }

    // 진행 중인 반복 항목(recurrence != null && !isCompleted)은 completeOccurrence로 다음 회차로
    // in-place 전진시킨다. 이미 종료된 반복 항목(반복이 endAt을 지나 status=DONE으로 고정된 경우)과
    // 비반복 항목은 완료 상태를 단순 토글한다 — 종료된 반복 항목도 이 분기를 타지 않으면 매 탭마다
    // completeOccurrence가 다시 실행되어 completionHistory에 같은 회차가 중복 누적된다.
    // 이전 회차(또는 이전 완료 상태)의 알림은 항상 취소하고, 결과가 미완료로 남을 때만(다음 회차 포함) 재예약한다.
    fun toggleCompleted(item: TodoItem) {
        val updated = if (item.recurrence != null && !item.isCompleted) {
            item.completeOccurrence(Instant.now())
        } else if (item.isCompleted) {
            item.copy(status = TodoStatus.NOT_STARTED, completedAt = null)
        } else {
            item.copy(status = TodoStatus.DONE, completedAt = Instant.now())
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

    // 목록에서 재생 아이콘으로 미진행 <-> 진행중을 전환한다. 완료(DONE) 항목에는 노출하지 않으므로
    // DONE이 들어오면 무시한다. 상태만 바뀌고 dueAt/리마인더 오프셋은 그대로라 리마인더 재예약은 불필요하다.
    fun toggleInProgress(item: TodoItem) {
        val nextStatus = when (item.status) {
            TodoStatus.NOT_STARTED -> TodoStatus.IN_PROGRESS
            TodoStatus.IN_PROGRESS -> TodoStatus.NOT_STARTED
            TodoStatus.DONE -> return
        }
        viewModelScope.launch {
            runCatching { repository.updateTodoItem(item.copy(status = nextStatus)) }
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

    companion object {
        fun factory(
            reminderScheduler: TodoReminderScheduler = TodoReminderScheduler.NoOp,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { TodoViewModel(reminderScheduler = reminderScheduler) } }

        // 마감일시와 리마인더 오프셋이 모두 있어야 "몇 분 전에 알린다"는 리마인더가 의미를 가진다.
        fun shouldScheduleReminder(item: TodoItem): Boolean =
            !item.isCompleted && item.dueAt != null && item.reminderOffsetMinutes != null

        // 오늘: 마감일시가 미설정이거나 오늘(zone 기준)이고, 상태가 미진행 또는 진행중인 것(= 완료 아님).
        // 전체: 완료 처리되지 않은 것.
        // 반복: 반복 설정된 것.
        // 완료: 반복 설정되지 않은 완료된 TODO(반복 항목은 완료 시 다음 회차로 리셋되므로 제외).
        fun filterByStatus(
            items: List<TodoItem>,
            filter: TodoStatusFilter,
            now: Instant,
            zone: ZoneId,
        ): List<TodoItem> = when (filter) {
            TodoStatusFilter.TODAY -> {
                val today = now.atZone(zone).toLocalDate()
                items.filter { item ->
                    item.status != TodoStatus.DONE &&
                        (item.dueAt == null || item.dueAt.atZone(zone).toLocalDate() == today)
                }
            }
            TodoStatusFilter.ALL -> items.filter { it.status != TodoStatus.DONE }
            TodoStatusFilter.RECURRING -> items.filter { it.recurrence != null }
            TodoStatusFilter.COMPLETED -> items.filter { it.recurrence == null && it.status == TodoStatus.DONE }
        }

        fun filterByAssignee(items: List<TodoItem>, assignees: Set<TodoAssignee>): List<TodoItem> {
            if (assignees.isEmpty()) return items
            return items.filter { it.assignee in assignees }
        }

        // 단일 고정 정렬(이슈 #71): 1) 마감시간 가까운 순, 2) 상태 순(미진행->진행중->완료),
        // 3) 마감시간 없음은 마지막(nullsLast).
        fun sortItems(items: List<TodoItem>): List<TodoItem> {
            val byDueAt = compareBy<TodoItem, Instant?>(nullsLast()) { it.dueAt }
            return items.sortedWith(byDueAt.thenBy { it.status.ordinal })
        }

        fun filterAndSort(
            items: List<TodoItem>,
            statusFilter: TodoStatusFilter,
            assigneeFilter: Set<TodoAssignee>,
            now: Instant,
            zone: ZoneId,
        ): List<TodoItem> {
            val filtered = filterByAssignee(filterByStatus(items, statusFilter, now, zone), assigneeFilter)
            return sortItems(filtered)
        }
    }
}
