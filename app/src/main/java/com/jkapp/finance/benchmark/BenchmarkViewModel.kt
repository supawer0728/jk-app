package com.jkapp.finance.benchmark

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jkapp.auth.AuthRepository
import com.jkapp.auth.FirebaseAuthRepository
import com.jkapp.common.latestNotFuture
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class BenchmarkViewModel(
    private val repository: BenchmarkFirestoreRepository = BenchmarkFirestoreRepositoryImpl(),
    private val sheetRepository: BenchmarkSheetRepository = BenchmarkSheetRepository.NoOp,
    private val authRepository: AuthRepository = FirebaseAuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<BenchmarkUiState>(BenchmarkUiState.Loading)
    val uiState: StateFlow<BenchmarkUiState> = _uiState.asStateFlow()

    // 저장/삭제 실패는 _uiState를 덮어쓰지 않는다. Firestore 스냅샷 리스너는 데이터가 실제로
    // 바뀔 때만 재발행되므로, 쓰기 실패(데이터 변화 없음) 시 Error로 덮으면 표가 사라진 채 고착된다.
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    // 구글시트에서 가져오기 흐름 상태(로딩/미리보기).
    private val _sheetImport = MutableStateFlow<BenchmarkSheetImportState>(BenchmarkSheetImportState.Idle)
    val sheetImport: StateFlow<BenchmarkSheetImportState> = _sheetImport.asStateFlow()

    // 시트 접근 동의가 필요할 때 UI가 실행할 복구 인텐트. Drive 패턴과 동일하게 다룬다.
    private val _sheetAuthRecoveryIntent = MutableStateFlow<Intent?>(null)
    val sheetAuthRecoveryIntent: StateFlow<Intent?> = _sheetAuthRecoveryIntent.asStateFlow()

    // 오늘 혹은 그보다 가까운 과거 날짜 중 가장 최신인 벤치마크의 현재금액(투자자산). 데이터가 없으면 null.
    val latestCurrentAmount: StateFlow<BigDecimal?> = uiState
        .map { state ->
            (state as? BenchmarkUiState.Success)?.benchmarks
                ?.let { latestNotFuture(it) { benchmark -> benchmark.date } }
                ?.currentAmount
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 표에 필요한 20개 열의 파생 지표(원금 누적, 수익률, 상승률, MDD 등)를 데이터가 실제로
    // 바뀔 때만 계산해 캐시한다. 컴포저블의 remember에 두면 탭을 오갈 때마다 화면이 새로
    // 컴포지션되면서 매번 다시 계산되므로, 뷰모델 레벨에서 한 번만 계산하도록 여기에 둔다.
    val rowMetrics: StateFlow<List<BenchmarkRowMetrics>> = uiState
        .map { state ->
            (state as? BenchmarkUiState.Success)?.benchmarks
                ?.withRowMetrics()
                ?.reversed() // 최신 날짜부터 표시
                ?: emptyList()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var dataJob: Job? = null
    private var sheetImportJob: Job? = null

    init {
        dataJob = viewModelScope.launch {
            repository.getBenchmarks()
                .map { BenchmarkUiState.Success(it) as BenchmarkUiState }
                .catch { e ->
                    emit(BenchmarkUiState.Error("벤치마크 목록을 불러오는 중 오류가 발생했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"))
                }
                .collect { state -> _uiState.value = state }
        }
        // 로그인된 구글 계정을 시트 저장소에 전달해, 이미 동의한 사용자는 계정 선택 없이 바로 읽는다.
        viewModelScope.launch {
            authRepository.observeCurrentUserEmail().collect { email ->
                email?.let { sheetRepository.setAccount(it) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dataJob?.cancel()
    }

    fun saveBenchmark(benchmark: Benchmark) {
        viewModelScope.launch {
            runCatching { repository.upsertBenchmark(benchmark) }
                .onFailure { e ->
                    _actionError.value = "벤치마크 저장에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                }
        }
    }

    fun deleteBenchmark(date: String) {
        viewModelScope.launch {
            runCatching { repository.deleteBenchmark(date) }
                .onFailure { e ->
                    _actionError.value = "벤치마크 삭제에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                }
        }
    }

    fun consumeActionError() {
        _actionError.value = null
    }

    // 여러 날짜를 하나의 배치로 한 번에 삭제한다(선택 삭제). 배치는 원자적이라
    // 일부만 삭제된 상태가 되지 않고, 실패하면 아무것도 삭제되지 않는다.
    fun deleteBenchmarks(dates: List<String>) {
        if (dates.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.deleteBenchmarks(dates) }
                .onFailure { e ->
                    _actionError.value = "벤치마크 삭제에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                }
        }
    }

    // 화면에 표시된(파싱에 성공한) 날짜 목록이 아니라 컬렉션 전체를 대상으로 삭제하므로,
    // 역직렬화에 실패해 표에 나타나지 않는 손상된 문서도 함께 삭제된다.
    fun deleteAllBenchmarks() {
        viewModelScope.launch {
            runCatching { repository.deleteAllBenchmarks() }
                .onFailure { e ->
                    _actionError.value = "벤치마크 삭제에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                }
        }
    }

    // 고정된 원본 구글시트에서 벤치마크 행을 읽어 파싱한 뒤 미리보기 상태로 만든다.
    // 접근 동의가 필요하면 복구 인텐트를 노출하고, 그 외 오류는 actionError로 안내한다.
    fun importFromSheet() {
        if (_sheetImport.value == BenchmarkSheetImportState.Loading) return
        sheetImportJob = viewModelScope.launch {
            _sheetImport.value = BenchmarkSheetImportState.Loading
            // 응답이 지나치게 늦으면(네트워크/토큰 지연) 로딩에 갇히지 않도록 타임아웃을 둔다.
            // withTimeoutOrNull은 초과 시 예외 대신 null을 돌려주므로 취소(CancellationException)와 구분된다.
            runCatching { withTimeoutOrNull(SHEET_IMPORT_TIMEOUT_MS) { sheetRepository.readBenchmarkRows() } }
                .onSuccess { rows ->
                    if (rows == null) {
                        _sheetImport.value = BenchmarkSheetImportState.Idle
                        _actionError.value = "구글시트를 불러오지 못했습니다: 응답 시간이 초과되었습니다"
                        return@onSuccess
                    }
                    val parsed = parseBenchmarkRows(rows)
                    parsed.filter { it.error != null }.forEach { row ->
                        Log.w(TAG, "벤치마크 시트 파싱 실패: error=${row.error}, input=\"${row.rawLine}\"")
                    }
                    _sheetImport.value = BenchmarkSheetImportState.Preview(parsed)
                }
                .onFailure { e ->
                    // 사용자가 로딩을 취소하면 조용히 종료한다(상태는 dismissSheetImport가 이미 정리).
                    if (e is CancellationException) return@onFailure
                    _sheetImport.value = BenchmarkSheetImportState.Idle
                    when (e) {
                        is BenchmarkSheetAuthException -> _sheetAuthRecoveryIntent.value = e.recoveryIntent
                        else -> _actionError.value =
                            "구글시트를 불러오지 못했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                    }
                }
        }
    }

    // 계정 선택/동의 화면에서 돌아온 계정 이름을 시트 저장소에 반영한다.
    fun onSheetAccountSelected(accountName: String) {
        sheetRepository.setAccount(accountName)
    }

    fun clearSheetAuthRecoveryIntent() {
        _sheetAuthRecoveryIntent.value = null
    }

    // 미리보기에서 확인한 벤치마크들을 저장하고 미리보기를 닫는다.
    fun confirmSheetImport(benchmarks: List<Benchmark>) {
        importBenchmarks(benchmarks)
        _sheetImport.value = BenchmarkSheetImportState.Idle
    }

    fun dismissSheetImport() {
        sheetImportJob?.cancel()
        _sheetImport.value = BenchmarkSheetImportState.Idle
    }

    // 붙여넣기로 여러 날짜의 벤치마크를 하나의 배치로 한 번에 저장한다. 배치는 원자적이라
    // 일부만 저장된 상태가 되지 않고, 실패하면 아무것도 저장되지 않는다.
    fun importBenchmarks(benchmarks: List<Benchmark>) {
        if (benchmarks.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.upsertBenchmarks(benchmarks) }
                .onFailure { e ->
                    _actionError.value = "벤치마크 저장에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                }
        }
    }

    companion object {
        private const val TAG = "BenchmarkViewModel"
        private const val SHEET_IMPORT_TIMEOUT_MS = 30_000L

        fun factory(
            sheetRepository: BenchmarkSheetRepository = BenchmarkSheetRepository.NoOp,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { BenchmarkViewModel(sheetRepository = sheetRepository) } }
    }
}
