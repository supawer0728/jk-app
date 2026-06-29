package com.jkapp.ui

import com.jkapp.auth.FakeAuthRepository
import com.jkapp.data.firestore.FakeFirestoreRepository
import com.jkapp.data.model.CatRecord
import com.jkapp.data.model.CatRecordType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeFirestoreRepository
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var viewModel: DiaryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeFirestoreRepository()
        fakeAuth = FakeAuthRepository(initialLoggedIn = false)
        viewModel = DiaryViewModel(repository = fakeRepository, authRepository = fakeAuth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- 인증 상태 ---

    @Test
    fun `로그아웃 상태에서 uiState는 Loading이다`() = runTest {
        advanceUntilIdle()
        assertEquals(DiaryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `로그인하면 데이터 수집이 시작되어 Success 상태가 된다`() = runTest {
        val types = listOf(makeType("DAILY_NOTE", "일상"))
        val records = listOf(makeRecord("2024-01-01"))
        fakeRepository.setRecordTypes(types)
        fakeRepository.setRecords(records)

        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as DiaryUiState.Success
        assertEquals(types, state.recordTypes)
        assertEquals(records, state.records)
    }

    @Test
    fun `로그인 후 로그아웃하면 uiState가 Loading으로 돌아온다`() = runTest {
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        fakeAuth.setLoggedIn(false)
        advanceUntilIdle()

        assertEquals(DiaryUiState.Loading, viewModel.uiState.value)
    }

    // --- addRecord ---

    @Test
    fun `addRecord 실패 시 uiState가 Error가 된다`() = runTest {
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        fakeRepository.addRecordError = RuntimeException("저장 실패")
        viewModel.addRecord(makeRecord("2024-01-01"))
        advanceUntilIdle()

        val error = viewModel.uiState.value as DiaryUiState.Error
        assertTrue(error.message.contains("새 기록 저장에 실패했습니다"))
    }

    // --- updateRecord ---

    @Test
    fun `updateRecord 실패 시 uiState가 Error가 된다`() = runTest {
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        fakeRepository.updateRecordError = RuntimeException("수정 실패")
        val record = makeRecord("2024-01-01", firestoreId = "id1")
        viewModel.updateRecord(original = record, updated = record)
        advanceUntilIdle()

        val error = viewModel.uiState.value as DiaryUiState.Error
        assertTrue(error.message.contains("기록 수정에 실패했습니다"))
    }

    // --- deleteRecord ---

    @Test
    fun `deleteRecord 실패 시 uiState가 Error가 된다`() = runTest {
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        fakeRepository.deleteRecordError = RuntimeException("삭제 실패")
        viewModel.deleteRecord("nonexistent-id")
        advanceUntilIdle()

        val error = viewModel.uiState.value as DiaryUiState.Error
        assertTrue(error.message.contains("기록 삭제에 실패했습니다"))
    }

    // --- addRecordType ---

    @Test
    fun `addRecordType에서 시스템 ID 사용 시 Error 상태가 된다`() = runTest {
        fakeAuth.setLoggedIn(true)
        fakeRepository.setRecordTypes(emptyList())
        advanceUntilIdle()

        viewModel.addRecordType(makeType("DAILY_NOTE", "일상"))
        advanceUntilIdle()

        val error = viewModel.uiState.value as DiaryUiState.Error
        assertTrue(error.message.contains("시스템 필수 유형 ID"))
    }

    @Test
    fun `addRecordType에서 중복 ID 사용 시 Error 상태가 된다`() = runTest {
        fakeAuth.setLoggedIn(true)
        fakeRepository.setRecordTypes(listOf(makeType("CUSTOM_TYPE", "커스텀")))
        advanceUntilIdle()

        viewModel.addRecordType(makeType("CUSTOM_TYPE", "다른 이름"))
        advanceUntilIdle()

        val error = viewModel.uiState.value as DiaryUiState.Error
        assertTrue(error.message.contains("이미 존재하는 기록유형 ID"))
    }

    @Test
    fun `addRecordType 저장 실패 시 uiState가 Error가 된다`() = runTest {
        fakeAuth.setLoggedIn(true)
        fakeRepository.setRecordTypes(emptyList())
        advanceUntilIdle()

        fakeRepository.addRecordTypeError = RuntimeException("저장 실패")
        viewModel.addRecordType(makeType("NEW_TYPE", "새 유형"))
        advanceUntilIdle()

        val error = viewModel.uiState.value as DiaryUiState.Error
        assertTrue(error.message.contains("기록유형 저장에 실패했습니다"))
    }

    // --- deleteRecordType ---

    @Test
    fun `deleteRecordType에서 시스템 유형 삭제 시도 시 Error 상태가 된다`() = runTest {
        val systemType = makeType("DAILY_NOTE", "일상", docId = "doc-daily")
        fakeAuth.setLoggedIn(true)
        fakeRepository.setRecordTypes(listOf(systemType))
        advanceUntilIdle()

        viewModel.deleteRecordType("doc-daily")
        advanceUntilIdle()

        val error = viewModel.uiState.value as DiaryUiState.Error
        assertTrue(error.message.contains("시스템 필수 유형은 삭제할 수 없습니다"))
    }

    @Test
    fun `deleteRecordType 저장 실패 시 uiState가 Error가 된다`() = runTest {
        val customType = makeType("CUSTOM", "커스텀", docId = "doc-custom")
        fakeAuth.setLoggedIn(true)
        fakeRepository.setRecordTypes(listOf(customType))
        advanceUntilIdle()

        fakeRepository.deleteRecordTypeError = RuntimeException("삭제 실패")
        viewModel.deleteRecordType("doc-custom")
        advanceUntilIdle()

        val error = viewModel.uiState.value as DiaryUiState.Error
        assertTrue(error.message.contains("기록유형 삭제에 실패했습니다"))
    }

    @Test
    fun `deleteRecordType이 Success 상태가 아닐 때 아무 동작도 하지 않는다`() = runTest {
        // 로그인하지 않아 Loading 상태 유지
        viewModel.deleteRecordType("doc-custom")
        advanceUntilIdle()

        assertEquals(DiaryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `deleteRecordType 호출 시 docId가 존재하지 않으면 에러 없이 처리된다`() = runTest {
        val customType = makeType("CUSTOM", "커스텀", docId = "doc-custom")
        fakeAuth.setLoggedIn(true)
        fakeRepository.setRecordTypes(listOf(customType))
        advanceUntilIdle()

        viewModel.deleteRecordType("doc-nonexistent")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is DiaryUiState.Success)
    }

    @Test
    fun `deleteRecordType 시 연관 레코드가 없어도 타입이 정상 삭제된다`() = runTest {
        val customType = makeType("CUSTOM", "커스텀", docId = "doc-custom")
        val unrelated = CatRecord(firestoreId = "rec-1", date = "2024-01-01", recordType = "DAILY_NOTE", record = "기록")
        fakeAuth.setLoggedIn(true)
        fakeRepository.setRecordTypes(listOf(customType))
        fakeRepository.setRecords(listOf(unrelated))
        advanceUntilIdle()

        viewModel.deleteRecordType("doc-custom")
        advanceUntilIdle()

        val state = viewModel.uiState.value as DiaryUiState.Success
        assertTrue(state.recordTypes.none { it.id == "CUSTOM" })
        assertEquals(1, state.records.size)
    }

    @Test
    fun `deleteRecordType 시 해당 타입의 레코드가 FALLBACK 타입으로 재배정된다`() = runTest {
        val customType = makeType("CUSTOM", "커스텀", docId = "doc-custom")
        val affected1 = CatRecord(firestoreId = "rec-1", date = "2024-01-01", recordType = "CUSTOM", record = "기록1")
        val affected2 = CatRecord(firestoreId = "rec-2", date = "2024-01-02", recordType = "CUSTOM", record = "기록2")
        val unaffected = CatRecord(firestoreId = "rec-3", date = "2024-01-03", recordType = "DAILY_NOTE", record = "기록3")
        fakeAuth.setLoggedIn(true)
        fakeRepository.setRecordTypes(listOf(customType))
        fakeRepository.setRecords(listOf(affected1, affected2, unaffected))
        advanceUntilIdle()

        viewModel.deleteRecordType("doc-custom")
        advanceUntilIdle()

        val state = viewModel.uiState.value as DiaryUiState.Success
        assertTrue(state.recordTypes.none { it.id == "CUSTOM" })
        assertTrue(state.records.filter { it.firestoreId in listOf("rec-1", "rec-2") }
            .all { it.recordType == DiaryViewModel.FALLBACK_RECORD_TYPE_ID })
        assertEquals("DAILY_NOTE", state.records.find { it.firestoreId == "rec-3" }?.recordType)
    }

    // --- toggleTypeFilter ---

    @Test
    fun `toggleTypeFilter 호출 시 selectedTypeIds에 ID가 추가된다`() = runTest {
        viewModel.toggleTypeFilter("DAILY_NOTE")
        advanceUntilIdle()

        assertTrue("DAILY_NOTE" in viewModel.selectedTypeIds.value)
    }

    @Test
    fun `toggleTypeFilter를 같은 ID로 두 번 호출하면 selectedTypeIds에서 제거된다`() = runTest {
        viewModel.toggleTypeFilter("DAILY_NOTE")
        viewModel.toggleTypeFilter("DAILY_NOTE")
        advanceUntilIdle()

        assertTrue("DAILY_NOTE" !in viewModel.selectedTypeIds.value)
    }

    // --- clearTypeFilter ---

    @Test
    fun `clearTypeFilter 호출 시 selectedTypeIds가 비워진다`() = runTest {
        viewModel.toggleTypeFilter("DAILY_NOTE")
        viewModel.toggleTypeFilter("HOSPITAL_VISIT")
        advanceUntilIdle()

        viewModel.clearTypeFilter()
        advanceUntilIdle()

        assertTrue(viewModel.selectedTypeIds.value.isEmpty())
    }

    // --- selectedYearMonth ---

    @Test
    fun `로그인하면 selectedYearMonth가 최신 달로 초기화된다`() = runTest {
        fakeRepository.setRecords(listOf(
            makeRecord("2024-03-15"),
            makeRecord("2024-01-10"),
            makeRecord("2024-02-20"),
        ))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        assertEquals(YearMonth.of(2024, 3), viewModel.selectedYearMonth.value)
    }

    @Test
    fun `로그아웃하면 selectedYearMonth가 null로 초기화된다`() = runTest {
        fakeRepository.setRecords(listOf(makeRecord("2024-03-15")))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        fakeAuth.setLoggedIn(false)
        advanceUntilIdle()

        assertNull(viewModel.selectedYearMonth.value)
    }

    @Test
    fun `selectYearMonth 호출 시 selectedYearMonth가 변경된다`() = runTest {
        fakeRepository.setRecords(listOf(makeRecord("2024-03-15"), makeRecord("2024-01-10")))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.selectYearMonth(YearMonth.of(2024, 1))

        assertEquals(YearMonth.of(2024, 1), viewModel.selectedYearMonth.value)
    }

    @Test
    fun `moveToPreviousMonth 호출 시 이전 달이 있으면 이동한다`() = runTest {
        fakeRepository.setRecords(listOf(makeRecord("2024-03-15"), makeRecord("2024-01-10")))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()
        // selectedYearMonth가 2024-03으로 초기화됨

        viewModel.moveToPreviousMonth()

        assertEquals(YearMonth.of(2024, 1), viewModel.selectedYearMonth.value)
    }

    @Test
    fun `moveToNextMonth 호출 시 다음 달이 있으면 이동한다`() = runTest {
        fakeRepository.setRecords(listOf(makeRecord("2024-03-15"), makeRecord("2024-01-10")))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()
        viewModel.selectYearMonth(YearMonth.of(2024, 1))

        viewModel.moveToNextMonth()

        assertEquals(YearMonth.of(2024, 3), viewModel.selectedYearMonth.value)
    }

    @Test
    fun `moveToPreviousMonth 호출 시 이전 달이 없으면 변경되지 않는다`() = runTest {
        fakeRepository.setRecords(listOf(makeRecord("2024-03-15")))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.moveToPreviousMonth()

        assertEquals(YearMonth.of(2024, 3), viewModel.selectedYearMonth.value)
    }

    @Test
    fun `moveToNextMonth 호출 시 다음 달이 없으면 변경되지 않는다`() = runTest {
        fakeRepository.setRecords(listOf(makeRecord("2024-03-15")))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.moveToNextMonth()

        assertEquals(YearMonth.of(2024, 3), viewModel.selectedYearMonth.value)
    }

    @Test
    fun `선택된 달의 레코드가 모두 삭제되면 selectedYearMonth가 다른 달로 이동한다`() = runTest {
        fakeRepository.setRecords(listOf(makeRecord("2024-03-15"), makeRecord("2024-01-10")))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()
        // 초기: 2024-03

        fakeRepository.setRecords(listOf(makeRecord("2024-01-10")))
        advanceUntilIdle()

        assertEquals(YearMonth.of(2024, 1), viewModel.selectedYearMonth.value)
    }

    @Test
    fun `모든 레코드가 삭제되면 selectedYearMonth가 null이 된다`() = runTest {
        fakeRepository.setRecords(listOf(makeRecord("2024-03-15")))
        fakeAuth.setLoggedIn(true)
        advanceUntilIdle()

        fakeRepository.setRecords(emptyList())
        advanceUntilIdle()

        assertNull(viewModel.selectedYearMonth.value)
    }

    // --- filterRecordsByMonth (companion) ---

    @Test
    fun `filterRecordsByMonth는 yearMonth가 null이면 전체를 반환한다`() {
        val records = listOf(makeRecord("2024-01-10"), makeRecord("2024-02-15"))
        assertEquals(records, DiaryViewModel.filterRecordsByMonth(records, null))
    }

    @Test
    fun `filterRecordsByMonth는 해당 월의 레코드만 반환한다`() {
        val jan = makeRecord("2024-01-10")
        val feb = makeRecord("2024-02-15")
        val result = DiaryViewModel.filterRecordsByMonth(listOf(jan, feb), YearMonth.of(2024, 1))
        assertEquals(listOf(jan), result)
    }

    // --- helpers ---

    private fun makeRecord(date: String, firestoreId: String? = null) = CatRecord(
        firestoreId = firestoreId,
        date = date,
        recordType = "DAILY_NOTE",
        record = "테스트 기록",
    )

    private fun makeType(id: String, name: String, docId: String = "") = CatRecordType(
        id = id,
        name = name,
        emoji = "📝",
        fontColor = "#000000",
        backgroundColor = "#FFFFFF",
        docId = docId,
    )
}
