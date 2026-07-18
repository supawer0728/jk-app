package com.jkapp.todo

import android.util.Log
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

    // 반복 부모 완료 시 자식 복제 다이얼로그 트리거.
    // null이면 다이얼로그 없음. non-null이면 (완료된 부모 ID, 복제 대상 자식 목록).
    private val _pendingSubClone = MutableStateFlow<Pair<String, List<TodoItem>>?>(null)
    val pendingSubClone: StateFlow<Pair<String, List<TodoItem>>?> = _pendingSubClone.asStateFlow()

    fun consumePendingSubClone() {
        _pendingSubClone.value = null
    }

    fun consumeSaveCompleted() {
        _saveCompleted.value = false
    }

    // 필터+정렬 결과를 캐시해 탭 전환으로 컴포지션이 재생성되어도 재계산하지 않는다.
    // SUB 항목은 visibleItems에 포함하지 않는다 — 부모 카드 펼침 영역에서만 노출.
    val visibleItems: StateFlow<List<TodoItem>> = combine(
        _uiState,
        _statusFilter,
        _assigneeFilter,
    ) { state, status, assignees ->
        val items = (state as? TodoUiState.Success)?.items.orEmpty()
        val mainItems = items.filter { it.type == TodoType.MAIN }
        filterAndSort(mainItems, status, assignees, Instant.now(), ZoneId.systemDefault())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 부모별 자식 목록은 TodoScreen이 getTodoItems() 결과를 mainTodoId로 그룹핑(subsByParent)해
    // 직접 처리하므로 여기서 별도 StateFlow를 노출하지 않는다.

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
                    _uiState.value = errorState("할 일을 불러오는 중 오류가 발생했습니다", e)
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

    // 목록의 상태 사이클 버튼(이슈 #71): 미진행 -> 진행중 -> 완료 -> 미진행.
    // 진행중 -> 완료 전이는 실제 완료 처리이므로 repository.completeTodoItem을 호출한다. 이 경로가
    // completeOccurrence(반복 다음 회차 전진/비반복 DONE)와 자식 DONE cascade(무알림)를 함께 강제한다.
    // 단순 전이(진행중 시작·완료 재개)는 상태만 바꾸면 되므로 updateTodoItem을 쓴다.
    // 완료/재개로 완료 여부가 바뀔 수 있으므로 이전 알림은 항상 취소하고, 결과가 미완료로 남을
    // 때만(다음 회차 포함) 재예약한다.
    //
    // 반복 부모가 다음 회차로 전진하고 자식이 있으면, 자식 복제 다이얼로그를 트리거한다.
    // (목록 화면 advanceStatus 경로에서만 — 리마인더 백그라운드 경로에서는 트리거 안 함)
    fun advanceStatus(item: TodoItem) {
        // IN_PROGRESS -> 완료 확정은 completeTodoItem(cascade·무알림)을 태운다. 나머지 단순 전이는 update.
        if (item.status == TodoStatus.IN_PROGRESS) {
            completeFromCycle(item)
            return
        }
        val updated = when (item.status) {
            TodoStatus.NOT_STARTED -> item.copy(status = TodoStatus.IN_PROGRESS)
            TodoStatus.DONE -> item.copy(status = TodoStatus.NOT_STARTED, completedAt = null)
            TodoStatus.IN_PROGRESS -> return // 위에서 처리
        }
        viewModelScope.launch {
            runCatching { repository.updateTodoItem(updated) }
                .onSuccess {
                    reminderScheduler.cancel(item)
                    if (shouldScheduleReminder(updated)) reminderScheduler.schedule(updated)
                }
                .onFailure { e ->
                    _uiState.value = errorState("할 일 상태 변경에 실패했습니다", e)
                }
        }
    }

    // 목록 사이클의 완료 확정(진행중 -> 완료) 처리. repository.completeTodoItem이 completeOccurrence와
    // 자식 DONE cascade(무알림)를 수행한다. 완료 후 예상 결과(반복이면 다음 회차 NOT_STARTED, 아니면 DONE)로
    // 리마인더를 재조정하고, 반복 부모가 다음 회차로 전진했고 자식이 있으면 복제 다이얼로그를 띄운다.
    private fun completeFromCycle(item: TodoItem) {
        val firestoreId = item.firestoreId ?: return
        val expected = item.completeOccurrence(Instant.now())
        viewModelScope.launch {
            runCatching { repository.completeTodoItem(firestoreId) }
                .onSuccess {
                    reminderScheduler.cancel(item)
                    if (shouldScheduleReminder(expected)) reminderScheduler.schedule(expected)

                    // 반복 부모가 다음 회차로 전진(NOT_STARTED로 리셋)했고 자식이 있으면 복제 다이얼로그 트리거.
                    val isRecurrenceAdvanced = item.recurrence != null &&
                        expected.status == TodoStatus.NOT_STARTED
                    if (isRecurrenceAdvanced) {
                        val subs = currentSubItems(firestoreId)
                        if (subs.isNotEmpty()) {
                            _pendingSubClone.value = firestoreId to subs
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.value = errorState("할 일 상태 변경에 실패했습니다", e)
                }
        }
    }

    // 자식(SUB) 상태 사이클. 자식을 IN_PROGRESS/DONE으로 바꾸면 NOT_STARTED 부모를 IN_PROGRESS로 전이.
    fun advanceSubStatus(sub: TodoItem) {
        val now = Instant.now()
        val updatedSub = when (sub.status) {
            TodoStatus.NOT_STARTED -> sub.copy(status = TodoStatus.IN_PROGRESS)
            TodoStatus.IN_PROGRESS -> sub.copy(status = TodoStatus.DONE, completedAt = now)
            TodoStatus.DONE -> sub.copy(status = TodoStatus.NOT_STARTED, completedAt = null)
        }
        viewModelScope.launch {
            runCatching { repository.updateTodoItem(updatedSub) }
                .onSuccess {
                    // 자식이 IN_PROGRESS 또는 DONE으로 전환됐고 부모가 NOT_STARTED이면 IN_PROGRESS로 전이.
                    val parentId = sub.mainTodoId
                    if (parentId != null &&
                        updatedSub.status != TodoStatus.NOT_STARTED &&
                        sub.status == TodoStatus.NOT_STARTED
                    ) {
                        val parent = currentMainItem(parentId)
                        if (parent != null && parent.status == TodoStatus.NOT_STARTED) {
                            // 부모 전이는 부수 작업. 실패해도 자식 변경(주 작업)은 성공했으므로 전체
                            // Error로 덮지 않고 로그만 남기고 삼킨다(push 실패 격리와 동일 관용).
                            runCatching {
                                val advanced = parent.copy(status = TodoStatus.IN_PROGRESS)
                                repository.updateTodoItem(advanced)
                            }.onFailure { e -> Log.w(LOG_TAG, "부모 상태 전이 실패(자식 변경은 완료됨)", e) }
                        }
                    }
                }
                .onFailure { e -> _uiState.value = errorState("할 일 상태 변경에 실패했습니다", e) }
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
                .onFailure { e -> _uiState.value = errorState("할 일 저장에 실패했습니다", e) }
        }
    }

    fun addSubTodoItem(parentId: String, item: TodoItem) {
        viewModelScope.launch {
            runCatching { repository.addSubTodoItem(parentId, item) }
                .onSuccess { _saveCompleted.value = true }
                .onFailure { e -> _uiState.value = errorState("자식 할 일 저장에 실패했습니다", e) }
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
                    _uiState.value = errorState("할 일 수정에 실패했습니다", e)
                }
        }
    }

    fun deleteTodoItem(item: TodoItem) {
        val firestoreId = item.firestoreId ?: return
        viewModelScope.launch {
            runCatching { repository.deleteTodoItem(firestoreId) }
                .onSuccess { reminderScheduler.cancel(item) }
                .onFailure { e ->
                    _uiState.value = errorState("할 일 삭제에 실패했습니다", e)
                }
        }
    }

    // 다중선택 삭제. 선택된 MAIN 항목 + 자식(cascade)을 삭제하고, 리마인더를 모두 취소한다.
    fun deleteTodoItems(items: List<TodoItem>) {
        val ids = items.mapNotNull { it.firestoreId }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.deleteTodoItems(ids) }
                .onSuccess { items.forEach { reminderScheduler.cancel(it) } }
                .onFailure { e ->
                    _uiState.value = errorState("할 일 삭제에 실패했습니다", e)
                }
        }
    }

    /**
     * 반복 부모 완료 시 자식을 NOT_STARTED로 복제해 새 회차 부모에 연결한다(무알림).
     * pendingSubClone 다이얼로그에서 "생성" 버튼을 탭하면 호출한다.
     * @param newParentId 새 회차 부모의 firestoreId (advanceStatus 후 실시간 스냅샷에서 업데이트된 ID)
     * @param subs 복제할 자식 목록 (다이얼로그 진입 시점의 스냅샷)
     */
    fun cloneSubsToNextOccurrence(newParentId: String, subs: List<TodoItem>) {
        viewModelScope.launch {
            subs.forEach { sub ->
                val clone = TodoItem(
                    type = TodoType.SUB,
                    mainTodoId = newParentId,
                    title = sub.title,
                    assignee = sub.assignee,
                    memo = sub.memo,
                    status = TodoStatus.NOT_STARTED,
                )
                // 복제 쓰기는 무알림(notify=false). 복제 자식마다 push가 나가면 ≤1 원칙을 깬다.
                runCatching { repository.addSubTodoItem(newParentId, clone, notify = false) }
                    .onFailure { e -> Log.w(LOG_TAG, "자식 복제 실패", e) }
            }
        }
    }

    // uiState에서 특정 MAIN 항목을 동기적으로 가져온다(advanceSubStatus 부모 전이용).
    private fun currentMainItem(parentId: String): TodoItem? {
        val items = (_uiState.value as? TodoUiState.Success)?.items ?: return null
        return items.firstOrNull { it.firestoreId == parentId && it.type == TodoType.MAIN }
    }

    // uiState에서 특정 부모의 자식 목록을 동기적으로 가져온다(복제 다이얼로그 트리거용).
    private fun currentSubItems(parentId: String): List<TodoItem> {
        val items = (_uiState.value as? TodoUiState.Success)?.items ?: return emptyList()
        return items.filter { it.type == TodoType.SUB && it.mainTodoId == parentId }
    }

    companion object {
        // 앱 전역 로그 태그(AGENT.md의 로그 필터 규칙과 통일). 부수 작업 실패 경고에 쓴다.
        private const val LOG_TAG = "jkapp"

        // 오류 상태 메시지 생성 헬퍼(반복되는 localizedMessage 폴백을 한 곳으로 모은다).
        private fun errorState(prefix: String, e: Throwable): TodoUiState.Error =
            TodoUiState.Error("$prefix: ${e.localizedMessage ?: "알 수 없는 오류"}")

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
