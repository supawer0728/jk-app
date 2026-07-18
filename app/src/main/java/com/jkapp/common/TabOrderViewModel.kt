package com.jkapp.common

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class TabOrderViewModel(
    private val repository: TabOrderRepository = TabOrderRepositoryImpl(),
) : ViewModel() {

    private val _tabOrder = MutableStateFlow(MainTab.entries.toList())
    val tabOrder: StateFlow<List<MainTab>> = _tabOrder.asStateFlow()

    // null이면 편집 비활성. beginEdit()로 현재 tabOrder를 복사해 초기화된다.
    private val _editTabOrder = MutableStateFlow<List<MainTab>?>(null)
    val editTabOrder: StateFlow<List<MainTab>?> = _editTabOrder.asStateFlow()

    private var observeJob: Job? = null
    private var uid: String? = null

    // 이미 같은 사용자를 구독 중이면 무시한다. MainScreen이 recomposition될 때마다
    // 다시 호출돼도 Firestore 리스너를 중복으로 붙이지 않기 위함이다.
    fun loadTabOrder(uid: String) {
        if (this.uid == uid) return
        this.uid = uid
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeTabOrder(uid)
                .catch { e -> Log.w(TAG, "탭 순서를 불러오는 중 오류가 발생했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}") }
                .collect { saved ->
                    // 편집 중 도착한 스냅샷(느린 초기 로드, 다른 기기의 변경 등)이
                    // 사용자가 재배치한 순서를 덮어쓰지 않도록 무시한다.
                    if (_editTabOrder.value != null) return@collect
                    _tabOrder.value = mergeTabOrder(saved, MainTab.entries)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
    }

    // 편집 시작: 현재 tabOrder를 복사해 editTabOrder를 초기화한다.
    fun beginEdit() {
        _editTabOrder.value = _tabOrder.value.toList()
    }

    // editTabOrder 내에서 탭 위치를 이동한다. 편집 중일 때만 동작한다.
    fun moveTab(from: Int, to: Int) {
        val current = _editTabOrder.value ?: return
        if (from !in current.indices || to !in current.indices || from == to) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        _editTabOrder.value = mutable
    }

    // 편집 적용: editTabOrder가 tabOrder와 다를 때만 저장하고, 편집을 종료한다.
    fun applyEdit() {
        val editOrder = _editTabOrder.value ?: return
        if (editOrder != _tabOrder.value) {
            _tabOrder.value = editOrder
            saveOrder(editOrder)
        }
        _editTabOrder.value = null
    }

    // 편집 취소: 변경을 버리고 편집을 종료한다.
    fun cancelEdit() {
        _editTabOrder.value = null
    }

    private fun saveOrder(order: List<MainTab>) {
        val currentUid = uid ?: return
        viewModelScope.launch {
            runCatching { repository.saveTabOrder(currentUid, order.map { it.name }) }
                .onFailure { e ->
                    Log.w(TAG, "탭 순서를 저장하는 중 오류가 발생했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}")
                }
        }
    }

    companion object {
        // saved에 없는 탭(신규 추가된 탭)은 MainTab.entries 순서 그대로 맨 뒤에 붙이고,
        // saved에 있지만 더 이상 존재하지 않는 탭 이름은 자동으로 걸러진다.
        fun mergeTabOrder(saved: List<String>?, all: List<MainTab>): List<MainTab> {
            if (saved == null) return all
            val savedTabs = saved.mapNotNull { name -> all.find { it.name == name } }
            val missing = all.filterNot { it in savedTabs }
            return savedTabs + missing
        }

        fun factory(): ViewModelProvider.Factory =
            viewModelFactory { initializer { TabOrderViewModel() } }

        private const val TAG = "TabOrderViewModel"
    }
}
