package com.jkapp.finance.asset

import android.content.Intent
import com.jkapp.auth.FakeAuthRepository
import com.jkapp.common.todayDate
import io.mockk.mockk
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyAssetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAssetFirestoreRepository
    private lateinit var fakeSheetRepository: FakeAssetSheetRepository
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var viewModel: DailyAssetViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAssetFirestoreRepository()
        fakeSheetRepository = FakeAssetSheetRepository()
        fakeAuthRepository = FakeAuthRepository()
        viewModel = DailyAssetViewModel(
            repository = fakeRepository,
            sheetRepository = fakeSheetRepository,
            authRepository = fakeAuthRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeAsset(
        name: String,
        owner: String = "전지훈",
        amount: BigDecimal? = null,
    ) = AssetItem(name = name, owner = owner, amount = amount)

    @Test
    fun `초기에는 uiState가 Loading이다가 데이터 수집 후 Success가 된다`() = runTest {
        val dailyAssets = listOf(DailyAsset(date = "2026-07-01", assets = listOf(makeAsset("현금"))))
        fakeRepository.setDailyAssets(dailyAssets)
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        assertEquals(dailyAssets, state.dailyAssets)
    }

    @Test
    fun `addAsset은 새 날짜에 대해 자산 목록을 새로 생성한다`() = runTest {
        advanceUntilIdle()

        viewModel.addAsset("2026-07-04", makeAsset("현금", amount = BigDecimal("1000")))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        assertEquals(listOf(makeAsset("현금", amount = BigDecimal("1000"))), dailyAsset?.assets)
    }

    @Test
    fun `addAsset은 기존 날짜의 자산 목록에 항목을 추가한다`() = runTest {
        fakeRepository.setDailyAssets(listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금")))))
        advanceUntilIdle()

        viewModel.addAsset("2026-07-04", makeAsset("주식"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        assertEquals(listOf(makeAsset("현금"), makeAsset("주식")), dailyAsset?.assets)
    }

    private val sheetHeader = listOf("이름", "명의", "계좌", "계좌번호", "카드", "금액")

    @Test
    fun `importFromSheet 성공 시 미리보기 상태가 되고 시트 행을 파싱해 담는다`() = runTest {
        fakeSheetRepository.rows = listOf(
            sheetHeader,
            listOf("현금", "J", "-", "-", "-", "₩ 1,000"),
            listOf("주식", "K", "삼성증권", "-", "-", "₩ 2,000"),
        )

        viewModel.importFromSheet()
        advanceUntilIdle()

        val preview = viewModel.sheetImport.value as AssetSheetImportState.Preview
        assertEquals(listOf("현금", "주식"), preview.rows.mapNotNull { it.item?.name })
        assertEquals(listOf("전지훈", "권유경"), preview.rows.mapNotNull { it.item?.owner })
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun `confirmSheetImport는 실행 시점 날짜에 자산을 저장하고 미리보기를 닫는다`() = runTest {
        advanceUntilIdle()
        fakeSheetRepository.rows = listOf(
            sheetHeader,
            listOf("현금", "J", "-", "-", "-", "₩ 1,000"),
        )
        viewModel.importFromSheet()
        advanceUntilIdle()

        val preview = viewModel.sheetImport.value as AssetSheetImportState.Preview
        val today = todayDate()
        viewModel.confirmSheetImport(today, preview.rows.mapNotNull { it.item })
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.single { it.date == today }
        assertEquals("현금", dailyAsset.assets.single().name)
        assertEquals(BigDecimal("1000"), dailyAsset.assets.single().amount)
        assertEquals(AssetSheetImportState.Idle, viewModel.sheetImport.value)
    }

    @Test
    fun `confirmSheetImport는 기존 날짜 문서가 있으면 항목을 병합한다`() = runTest {
        val today = todayDate()
        fakeRepository.setDailyAssets(
            listOf(DailyAsset(date = today, assets = listOf(makeAsset("현금", amount = BigDecimal("100")))))
        )
        advanceUntilIdle()

        viewModel.confirmSheetImport(
            today,
            listOf(
                AssetItem(name = "현금", owner = "전지훈", amount = BigDecimal("500")),
                AssetItem(name = "주식", owner = "권유경", amount = BigDecimal("2000")),
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val assets = state.dailyAssets.single { it.date == today }.assets
        assertEquals(BigDecimal("500"), assets.single { it.name == "현금" }.amount)
        assertEquals(BigDecimal("2000"), assets.single { it.name == "주식" }.amount)
    }

    @Test
    fun `importFromSheet 인증 예외 시 복구 인텐트가 설정되고 미리보기는 열리지 않는다`() = runTest {
        fakeSheetRepository.error = AssetSheetAuthException(mockk<Intent>(relaxed = true))

        viewModel.importFromSheet()
        advanceUntilIdle()

        assertNotNull(viewModel.sheetAuthRecoveryIntent.value)
        assertEquals(AssetSheetImportState.Idle, viewModel.sheetImport.value)
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun `importFromSheet 일반 오류 시 actionError가 설정된다`() = runTest {
        fakeSheetRepository.error = RuntimeException("네트워크 오류")

        viewModel.importFromSheet()
        advanceUntilIdle()

        assertTrue(viewModel.actionError.value!!.contains("구글시트를 불러오지 못했습니다"))
        assertEquals(AssetSheetImportState.Idle, viewModel.sheetImport.value)
    }

    @Test
    fun `로딩 중 dismissSheetImport로 취소하면 Idle로 돌아가고 오류를 남기지 않는다`() = runTest {
        fakeSheetRepository.suspendIndefinitely = true

        viewModel.importFromSheet()
        // 시계를 진행시키지 않고 현재 시점 작업만 실행한다(타임아웃 30초는 아직 발화하지 않음).
        runCurrent()
        assertEquals(AssetSheetImportState.Loading, viewModel.sheetImport.value)

        viewModel.dismissSheetImport()
        advanceUntilIdle()

        assertEquals(AssetSheetImportState.Idle, viewModel.sheetImport.value)
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun `응답이 타임아웃되면 actionError가 설정되고 Idle로 돌아간다`() = runTest {
        fakeSheetRepository.suspendIndefinitely = true

        viewModel.importFromSheet()
        advanceUntilIdle() // 가상 시계가 타임아웃(30초)을 지나 withTimeoutOrNull이 null을 반환한다.

        assertTrue(viewModel.actionError.value!!.contains("시간이 초과"))
        assertEquals(AssetSheetImportState.Idle, viewModel.sheetImport.value)
    }

    @Test
    fun `importAssets는 이름과 명의가 같은 항목을 교체하고 없는 항목은 추가한다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금", amount = BigDecimal("100")))))
        )
        advanceUntilIdle()

        viewModel.importAssets(
            "2026-07-04",
            listOf(
                makeAsset("현금", amount = BigDecimal("200")),
                makeAsset("주식"),
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        assertEquals(
            listOf(makeAsset("현금", amount = BigDecimal("200")), makeAsset("주식")),
            dailyAsset?.assets,
        )
    }

    @Test
    fun `importAssets는 이름이 같아도 명의가 다르면 별도 항목으로 유지한다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(
                DailyAsset(
                    date = "2026-07-04",
                    assets = listOf(makeAsset("적금", owner = "전지훈", amount = BigDecimal("100"))),
                )
            )
        )
        advanceUntilIdle()

        viewModel.importAssets("2026-07-04", listOf(makeAsset("적금", owner = "권유경", amount = BigDecimal("200"))))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        assertEquals(
            listOf(
                makeAsset("적금", owner = "전지훈", amount = BigDecimal("100")),
                makeAsset("적금", owner = "권유경", amount = BigDecimal("200")),
            ),
            dailyAsset?.assets,
        )
    }

    @Test
    fun `importAssets는 기존 항목의 card처럼 붙여넣기에 없는 필드는 보존한다`() = runTest {
        val existing = AssetItem(name = "현금", owner = "전지훈", card = "체크카드", amount = BigDecimal("100"))
        fakeRepository.setDailyAssets(listOf(DailyAsset(date = "2026-07-04", assets = listOf(existing))))
        advanceUntilIdle()

        viewModel.importAssets("2026-07-04", listOf(makeAsset("현금", amount = BigDecimal("200"))))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        val updated = dailyAsset?.assets?.single { it.name == "현금" }
        assertEquals("체크카드", updated?.card)
        assertEquals(BigDecimal("200"), updated?.amount)
    }

    @Test
    fun `importAssets는 자산이 없는 날짜에도 새 문서를 생성한다`() = runTest {
        advanceUntilIdle()

        viewModel.importAssets("2026-07-04", listOf(makeAsset("현금"), makeAsset("주식")))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        assertEquals(listOf(makeAsset("현금"), makeAsset("주식")), dailyAsset?.assets)
    }

    @Test
    fun `updateAsset은 대상과 일치하는 항목만 교체한다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금"), makeAsset("주식"))))
        )
        advanceUntilIdle()

        viewModel.updateAsset("2026-07-04", makeAsset("주식"), makeAsset("주식", amount = BigDecimal("500")))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        assertEquals(
            listOf(makeAsset("현금"), makeAsset("주식", amount = BigDecimal("500"))),
            dailyAsset?.assets,
        )
    }

    @Test
    fun `updateAsset은 대상 항목을 찾지 못하면 uiState가 Error가 된다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금"))))
        )
        advanceUntilIdle()

        viewModel.updateAsset("2026-07-04", makeAsset("존재하지않음"), makeAsset("수정됨"))
        advanceUntilIdle()

        val error = viewModel.uiState.value as DailyAssetUiState.Error
        assertTrue(error.message.contains("찾을 수 없습니다"))
    }

    @Test
    fun `deleteAsset은 항목이 남아있으면 나머지 목록으로 upsert한다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금"), makeAsset("주식"))))
        )
        advanceUntilIdle()

        viewModel.deleteAsset("2026-07-04", makeAsset("현금"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        assertEquals(listOf(makeAsset("주식")), dailyAsset?.assets)
    }

    @Test
    fun `deleteAsset은 마지막 항목을 삭제하면 문서 자체를 삭제한다`() = runTest {
        fakeRepository.setDailyAssets(listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금")))))
        advanceUntilIdle()

        viewModel.deleteAsset("2026-07-04", makeAsset("현금"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        assertNull(state.dailyAssets.find { it.date == "2026-07-04" })
    }

    @Test
    fun `deleteAsset은 대상 항목을 찾지 못하면 uiState가 Error가 된다`() = runTest {
        fakeRepository.setDailyAssets(listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금")))))
        advanceUntilIdle()

        viewModel.deleteAsset("2026-07-04", makeAsset("존재하지않음"))
        advanceUntilIdle()

        val error = viewModel.uiState.value as DailyAssetUiState.Error
        assertTrue(error.message.contains("찾을 수 없습니다"))
    }

    @Test
    fun `동시에 발생한 자산 변경 요청은 직렬화되어 서로의 변경을 덮어쓰지 않는다`() = runTest {
        advanceUntilIdle()

        var activeCount = 0
        var maxActiveCount = 0
        fakeRepository.onUpsertDailyAsset = {
            activeCount++
            maxActiveCount = maxOf(maxActiveCount, activeCount)
            yield()
            activeCount--
        }

        viewModel.addAsset("2026-07-04", makeAsset("현금"))
        viewModel.addAsset("2026-07-04", makeAsset("주식"))
        advanceUntilIdle()

        assertEquals(1, maxActiveCount)
        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val dailyAsset = state.dailyAssets.find { it.date == "2026-07-04" }
        assertEquals(setOf("현금", "주식"), dailyAsset?.assets?.map { it.name }?.toSet())
    }

    @Test
    fun `netWorth는 가장 최신 날짜의 숨김되지 않은 자산 합계다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(
                DailyAsset(
                    date = "2026-07-01",
                    assets = listOf(makeAsset("과거현금", amount = BigDecimal("999999"))),
                ),
                DailyAsset(
                    date = "2026-07-04",
                    assets = listOf(
                        makeAsset("현금", amount = BigDecimal("1000")),
                        makeAsset("주식", amount = BigDecimal("2000")),
                    ),
                ),
            )
        )
        advanceUntilIdle()

        assertEquals(BigDecimal("3000"), viewModel.netWorth.value)
    }

    @Test
    fun `netWorth는 숨김 처리된 자산을 제외한다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(
                DailyAsset(
                    date = "2026-07-04",
                    assets = listOf(
                        AssetItem(name = "공용 계좌", owner = "공동", amount = BigDecimal("5000"), hidden = true),
                        makeAsset("현금", amount = BigDecimal("1000")),
                    ),
                )
            )
        )
        advanceUntilIdle()

        assertEquals(BigDecimal("1000"), viewModel.netWorth.value)
    }

    @Test
    fun `netWorth는 금액이 없는 자산을 0으로 계산한다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금", amount = null))))
        )
        advanceUntilIdle()

        assertEquals(BigDecimal.ZERO, viewModel.netWorth.value)
    }

    @Test
    fun `netWorth는 모든 자산이 숨김이면 0이 아니라 null이다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(
                DailyAsset(
                    date = "2026-07-04",
                    assets = listOf(AssetItem(name = "공용 계좌", owner = "공동", amount = BigDecimal("5000"), hidden = true)),
                )
            )
        )
        advanceUntilIdle()

        assertNull(viewModel.netWorth.value)
    }

    @Test
    fun `netWorth는 자산 데이터가 없으면 null이다`() = runTest {
        advanceUntilIdle()

        assertNull(viewModel.netWorth.value)
    }

    @Test
    fun `netWorth는 미래 날짜 자산을 제외하고 오늘 이전 최신 자산을 사용한다`() = runTest {
        val today = todayDate()
        val tomorrow = LocalDate.parse(today, DateTimeFormatter.ISO_LOCAL_DATE).plusDays(1)
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        fakeRepository.setDailyAssets(
            listOf(
                DailyAsset(date = today, assets = listOf(makeAsset("현금", amount = BigDecimal("1000")))),
                DailyAsset(date = tomorrow, assets = listOf(makeAsset("현금", amount = BigDecimal("999999")))),
            )
        )
        advanceUntilIdle()

        assertEquals(BigDecimal("1000"), viewModel.netWorth.value)
    }

    @Test
    fun `importAssets는 기존 항목의 hidden 값을 보존한다`() = runTest {
        val existing = AssetItem(name = "현금", owner = "전지훈", hidden = true, amount = BigDecimal("100"))
        fakeRepository.setDailyAssets(listOf(DailyAsset(date = "2026-07-04", assets = listOf(existing))))
        advanceUntilIdle()

        viewModel.importAssets("2026-07-04", listOf(makeAsset("현금", amount = BigDecimal("200"))))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DailyAssetUiState.Success
        val updated = state.dailyAssets.find { it.date == "2026-07-04" }?.assets?.single { it.name == "현금" }
        assertEquals(true, updated?.hidden)
        assertEquals(BigDecimal("200"), updated?.amount)
    }

    @Test
    fun `자산 저장 실패 시 uiState가 Error가 된다`() = runTest {
        advanceUntilIdle()

        fakeRepository.upsertDailyAssetError = RuntimeException("저장 실패")
        viewModel.addAsset("2026-07-04", makeAsset("현금"))
        advanceUntilIdle()

        val error = viewModel.uiState.value as DailyAssetUiState.Error
        assertTrue(error.message.contains("자산 저장에 실패했습니다"))
    }

    @Test
    fun `자산 삭제 실패 시 uiState가 Error가 된다`() = runTest {
        fakeRepository.setDailyAssets(listOf(DailyAsset(date = "2026-07-04", assets = listOf(makeAsset("현금")))))
        advanceUntilIdle()

        fakeRepository.deleteDailyAssetError = RuntimeException("삭제 실패")
        viewModel.deleteAsset("2026-07-04", makeAsset("현금"))
        advanceUntilIdle()

        val error = viewModel.uiState.value as DailyAssetUiState.Error
        assertTrue(error.message.contains("자산 삭제에 실패했습니다"))
    }

    // --- availableDates / selectedDate / currentDailyAsset (이슈 #37) ---

    @Test
    fun `availableDates는 날짜를 최신순으로 정렬한다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(
                DailyAsset(date = "2026-07-01", assets = listOf(makeAsset("현금"))),
                DailyAsset(date = "2026-07-03", assets = listOf(makeAsset("주식"))),
            )
        )
        advanceUntilIdle()

        assertEquals(listOf("2026-07-03", "2026-07-01"), viewModel.availableDates.value)
    }

    @Test
    fun `selectedDate는 초기에 가장 최신 날짜로 채워진다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(
                DailyAsset(date = "2026-07-01", assets = listOf(makeAsset("현금"))),
                DailyAsset(date = "2026-07-03", assets = listOf(makeAsset("주식"))),
            )
        )
        advanceUntilIdle()

        assertEquals("2026-07-03", viewModel.selectedDate.value)
    }

    @Test
    fun `selectDate 호출 시 selectedDate가 변경된다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(
                DailyAsset(date = "2026-07-01", assets = listOf(makeAsset("현금"))),
                DailyAsset(date = "2026-07-03", assets = listOf(makeAsset("주식"))),
            )
        )
        advanceUntilIdle()

        viewModel.selectDate("2026-07-01")
        advanceUntilIdle()

        assertEquals("2026-07-01", viewModel.selectedDate.value)
    }

    @Test
    fun `selectedDate는 선택한 날짜가 삭제되면 최신 날짜로 대체된다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(
                DailyAsset(date = "2026-07-01", assets = listOf(makeAsset("현금"))),
                DailyAsset(date = "2026-07-02", assets = listOf(makeAsset("주식"))),
            )
        )
        advanceUntilIdle()
        viewModel.selectDate("2026-07-01")
        advanceUntilIdle()
        assertEquals("2026-07-01", viewModel.selectedDate.value)

        viewModel.deleteAsset("2026-07-01", makeAsset("현금"))
        advanceUntilIdle()

        assertEquals("2026-07-02", viewModel.selectedDate.value)
    }

    @Test
    fun `selectDate로 아직 자산이 없는 새 날짜를 고르면 최신 날짜로 되돌아가지 않는다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(
                DailyAsset(date = "2026-07-01", assets = listOf(makeAsset("현금"))),
                DailyAsset(date = "2026-07-03", assets = listOf(makeAsset("주식"))),
            )
        )
        advanceUntilIdle()

        viewModel.selectDate("2026-07-10")
        advanceUntilIdle()

        assertEquals("2026-07-10", viewModel.selectedDate.value)
        assertNull(viewModel.currentDailyAsset.value)
    }

    @Test
    fun `currentDailyAsset는 selectedDate에 해당하는 자산을 반환한다`() = runTest {
        val dailyAssets = listOf(
            DailyAsset(date = "2026-07-01", assets = listOf(makeAsset("현금"))),
            DailyAsset(date = "2026-07-03", assets = listOf(makeAsset("주식"))),
        )
        fakeRepository.setDailyAssets(dailyAssets)
        advanceUntilIdle()

        viewModel.selectDate("2026-07-01")
        advanceUntilIdle()

        assertEquals(dailyAssets[0], viewModel.currentDailyAsset.value)
    }

    // --- ownerFilterOptions / groupedAssets (이슈 #37) ---

    @Test
    fun `ownerFilterOptions는 고정 명의에 없는 다른 명의도 포함한다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(DailyAsset(date = "2026-07-01", assets = listOf(makeAsset("현금", owner = "기타명의"))))
        )
        advanceUntilIdle()

        assertEquals(ASSET_OWNERS + listOf("기타명의"), viewModel.ownerFilterOptions.value)
    }

    @Test
    fun `toggleOwnerFilter는 선택된 명의를 추가하고 다시 호출하면 제거한다`() = runTest {
        advanceUntilIdle()

        viewModel.toggleOwnerFilter("전지훈")
        assertEquals(setOf("전지훈"), viewModel.selectedOwners.value)

        viewModel.toggleOwnerFilter("전지훈")
        assertEquals(emptySet<String>(), viewModel.selectedOwners.value)
    }

    @Test
    fun `clearOwnerFilter는 선택된 명의를 모두 비운다`() = runTest {
        advanceUntilIdle()
        viewModel.toggleOwnerFilter("전지훈")
        viewModel.toggleOwnerFilter("권유경")

        viewModel.clearOwnerFilter()

        assertEquals(emptySet<String>(), viewModel.selectedOwners.value)
    }

    @Test
    fun `groupedAssets는 선택된 명의로 필터링해 명의별로 그룹핑한다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(
                DailyAsset(
                    date = "2026-07-01",
                    assets = listOf(makeAsset("현금", owner = "전지훈"), makeAsset("주식", owner = "권유경")),
                )
            )
        )
        advanceUntilIdle()

        viewModel.toggleOwnerFilter("전지훈")
        advanceUntilIdle()

        val grouped = viewModel.groupedAssets.value
        assertEquals(setOf("전지훈"), grouped.keys)
        assertEquals(listOf("현금"), grouped.getValue("전지훈").map { it.value.name })
    }

    @Test
    fun `groupedAssets는 showHidden이 false면 숨김 자산을 제외한다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(
                DailyAsset(
                    date = "2026-07-01",
                    assets = listOf(
                        AssetItem(name = "숨김자산", owner = "전지훈", hidden = true),
                        makeAsset("현금", owner = "전지훈"),
                    ),
                )
            )
        )
        advanceUntilIdle()

        val grouped = viewModel.groupedAssets.value
        assertEquals(listOf("현금"), grouped.getValue("전지훈").map { it.value.name })
    }

    @Test
    fun `toggleShowHidden 호출 시 숨김 자산도 포함된다`() = runTest {
        fakeRepository.setDailyAssets(
            listOf(
                DailyAsset(
                    date = "2026-07-01",
                    assets = listOf(
                        AssetItem(name = "숨김자산", owner = "전지훈", hidden = true),
                        makeAsset("현금", owner = "전지훈"),
                    ),
                )
            )
        )
        advanceUntilIdle()

        viewModel.toggleShowHidden()
        advanceUntilIdle()

        val grouped = viewModel.groupedAssets.value
        assertEquals(setOf("숨김자산", "현금"), grouped.getValue("전지훈").map { it.value.name }.toSet())
    }
}
