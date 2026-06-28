package com.jkapp.ui

import com.jkapp.auth.FakeAuthRepository
import com.jkapp.data.drive.FakeDriveRepository
import com.jkapp.data.firestore.FakeFirestoreRepository
import com.jkapp.data.model.Attachment
import com.jkapp.data.model.CatRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModelAttachmentTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeFirestoreRepository
    private lateinit var fakeDriveRepository: FakeDriveRepository
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var viewModel: DiaryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeFirestoreRepository()
        fakeDriveRepository = FakeDriveRepository()
        fakeAuth = FakeAuthRepository(initialLoggedIn = true)
        viewModel = DiaryViewModel(
            repository = fakeRepository,
            driveRepository = fakeDriveRepository,
            authRepository = fakeAuth,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- uploadAttachment ---

    @Test
    fun `uploadAttachment 성공 시 pendingAttachments에 추가된다`() = runTest {
        advanceUntilIdle()

        viewModel.uploadAttachment("test".byteInputStream(), "test.jpg", "image/jpeg")
        advanceUntilIdle()

        assertEquals(1, viewModel.pendingAttachments.value.size)
        assertEquals("test.jpg", viewModel.pendingAttachments.value[0].name)
    }

    @Test
    fun `uploadAttachment 성공 시 isUploadingAttachment가 false로 복귀한다`() = runTest {
        advanceUntilIdle()

        viewModel.uploadAttachment("test".byteInputStream(), "test.jpg", "image/jpeg")
        advanceUntilIdle()

        assertFalse(viewModel.isUploadingAttachment.value)
    }

    @Test
    fun `uploadAttachment 실패 시 uiState가 Error가 된다`() = runTest {
        advanceUntilIdle()

        fakeDriveRepository.uploadError = RuntimeException("업로드 실패")
        viewModel.uploadAttachment("test".byteInputStream(), "test.jpg", "image/jpeg")
        advanceUntilIdle()

        val error = viewModel.uiState.value as DiaryUiState.Error
        assertTrue(error.message.contains("파일 업로드에 실패했습니다"))
    }

    @Test
    fun `uploadAttachment 실패 시 pendingAttachments가 비어있다`() = runTest {
        advanceUntilIdle()

        fakeDriveRepository.uploadError = RuntimeException("업로드 실패")
        viewModel.uploadAttachment("test".byteInputStream(), "test.jpg", "image/jpeg")
        advanceUntilIdle()

        assertTrue(viewModel.pendingAttachments.value.isEmpty())
    }

    // --- removePendingAttachment ---

    @Test
    fun `removePendingAttachment 호출 시 pendingAttachments에서 제거된다`() = runTest {
        advanceUntilIdle()
        viewModel.uploadAttachment("test".byteInputStream(), "test.jpg", "image/jpeg")
        advanceUntilIdle()

        val fileId = viewModel.pendingAttachments.value[0].fileId
        viewModel.removePendingAttachment(fileId)
        advanceUntilIdle()

        assertTrue(viewModel.pendingAttachments.value.isEmpty())
    }

    @Test
    fun `removePendingAttachment 호출 시 Drive에서 파일이 삭제된다`() = runTest {
        advanceUntilIdle()
        viewModel.uploadAttachment("test".byteInputStream(), "test.jpg", "image/jpeg")
        advanceUntilIdle()

        val fileId = viewModel.pendingAttachments.value[0].fileId
        viewModel.removePendingAttachment(fileId)
        advanceUntilIdle()

        assertTrue(fileId in fakeDriveRepository.deletedFileIds)
    }

    @Test
    fun `removePendingAttachment는 대상 파일만 제거하고 나머지는 유지한다`() = runTest {
        advanceUntilIdle()
        viewModel.uploadAttachment("a".byteInputStream(), "a.jpg", "image/jpeg")
        viewModel.uploadAttachment("b".byteInputStream(), "b.jpg", "image/jpeg")
        advanceUntilIdle()

        val firstId = viewModel.pendingAttachments.value[0].fileId
        viewModel.removePendingAttachment(firstId)
        advanceUntilIdle()

        assertEquals(1, viewModel.pendingAttachments.value.size)
        assertEquals("b.jpg", viewModel.pendingAttachments.value[0].name)
    }

    // --- cancelPendingAttachments ---

    @Test
    fun `cancelPendingAttachments 호출 시 pendingAttachments가 비워진다`() = runTest {
        advanceUntilIdle()
        viewModel.uploadAttachment("a".byteInputStream(), "a.jpg", "image/jpeg")
        viewModel.uploadAttachment("b".byteInputStream(), "b.jpg", "image/jpeg")
        advanceUntilIdle()

        viewModel.cancelPendingAttachments()
        advanceUntilIdle()

        assertTrue(viewModel.pendingAttachments.value.isEmpty())
    }

    @Test
    fun `cancelPendingAttachments 호출 시 모든 pending 파일이 Drive에서 삭제된다`() = runTest {
        advanceUntilIdle()
        viewModel.uploadAttachment("a".byteInputStream(), "a.jpg", "image/jpeg")
        viewModel.uploadAttachment("b".byteInputStream(), "b.jpg", "image/jpeg")
        advanceUntilIdle()

        val uploadedIds = viewModel.pendingAttachments.value.map { it.fileId }
        viewModel.cancelPendingAttachments()
        advanceUntilIdle()

        assertTrue(fakeDriveRepository.deletedFileIds.containsAll(uploadedIds))
    }

    // --- addRecord with pending attachments ---

    @Test
    fun `addRecord 시 pendingAttachments가 record에 포함된다`() = runTest {
        advanceUntilIdle()
        viewModel.uploadAttachment("test".byteInputStream(), "test.jpg", "image/jpeg")
        advanceUntilIdle()

        viewModel.addRecord(makeRecord("2024-01-01"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as DiaryUiState.Success
        assertEquals(1, state.records.size)
        assertEquals(1, state.records[0].attachments.size)
        assertEquals("test.jpg", state.records[0].attachments[0].name)
    }

    @Test
    fun `addRecord 후 pendingAttachments가 비워진다`() = runTest {
        advanceUntilIdle()
        viewModel.uploadAttachment("test".byteInputStream(), "test.jpg", "image/jpeg")
        advanceUntilIdle()

        viewModel.addRecord(makeRecord("2024-01-01"))
        advanceUntilIdle()

        assertTrue(viewModel.pendingAttachments.value.isEmpty())
    }

    // --- updateRecord GC ---

    @Test
    fun `updateRecord 시 제거된 attachment가 Drive에서 삭제된다`() = runTest {
        advanceUntilIdle()

        val oldAttachment = Attachment("old-id", "old.jpg", "image/jpeg", 1024L)
        val keptAttachment = Attachment("kept-id", "kept.jpg", "image/jpeg", 2048L)
        val original = makeRecord("2024-01-01", attachments = listOf(oldAttachment, keptAttachment))
        val updated = makeRecord("2024-01-01", attachments = listOf(keptAttachment))

        viewModel.updateRecord(original, updated)
        advanceUntilIdle()

        assertTrue("old-id" in fakeDriveRepository.deletedFileIds)
        assertFalse("kept-id" in fakeDriveRepository.deletedFileIds)
    }

    @Test
    fun `updateRecord 시 pendingAttachments가 updated record에 추가된다`() = runTest {
        advanceUntilIdle()
        viewModel.uploadAttachment("new".byteInputStream(), "new.jpg", "image/jpeg")
        advanceUntilIdle()

        val original = makeRecord("2024-01-01")
        val updated = makeRecord("2024-01-01")
        viewModel.updateRecord(original, updated)
        advanceUntilIdle()

        val saved = fakeRepository.lastUpdatedRecord
        assertNotNull(saved)
        assertEquals(1, saved!!.attachments.size)
        assertEquals("new.jpg", saved.attachments[0].name)
    }

    @Test
    fun `updateRecord 후 pendingAttachments가 비워진다`() = runTest {
        advanceUntilIdle()
        viewModel.uploadAttachment("test".byteInputStream(), "test.jpg", "image/jpeg")
        advanceUntilIdle()

        viewModel.updateRecord(makeRecord("2024-01-01"), makeRecord("2024-01-01"))
        advanceUntilIdle()

        assertTrue(viewModel.pendingAttachments.value.isEmpty())
    }

    @Test
    fun `updateRecord 실패 시 Drive GC는 실행되지 않는다`() = runTest {
        advanceUntilIdle()

        val oldAttachment = Attachment("old-id", "old.jpg", "image/jpeg", 1024L)
        val original = makeRecord("2024-01-01", attachments = listOf(oldAttachment))
        val updated = makeRecord("2024-01-01", attachments = emptyList())

        fakeRepository.updateRecordError = RuntimeException("수정 실패")
        viewModel.updateRecord(original, updated)
        advanceUntilIdle()

        assertFalse("old-id" in fakeDriveRepository.deletedFileIds)
    }

    // --- deleteRecord GC ---

    @Test
    fun `deleteRecord 시 record의 모든 attachment가 Drive에서 삭제된다`() = runTest {
        val attachment1 = Attachment("file-id-1", "a.jpg", "image/jpeg", 1024L)
        val attachment2 = Attachment("file-id-2", "b.pdf", "application/pdf", 2048L)
        val record = makeRecord("2024-01-01", firestoreId = "rec-1", attachments = listOf(attachment1, attachment2))
        fakeRepository.setRecords(listOf(record))
        advanceUntilIdle()

        viewModel.deleteRecord("rec-1")
        advanceUntilIdle()

        assertTrue("file-id-1" in fakeDriveRepository.deletedFileIds)
        assertTrue("file-id-2" in fakeDriveRepository.deletedFileIds)
    }

    @Test
    fun `deleteRecord 실패 시 Drive GC는 실행되지 않는다`() = runTest {
        val attachment = Attachment("file-id", "test.jpg", "image/jpeg", 1024L)
        val record = makeRecord("2024-01-01", firestoreId = "rec-1", attachments = listOf(attachment))
        fakeRepository.setRecords(listOf(record))
        advanceUntilIdle()

        fakeRepository.deleteRecordError = RuntimeException("삭제 실패")
        viewModel.deleteRecord("rec-1")
        advanceUntilIdle()

        assertFalse("file-id" in fakeDriveRepository.deletedFileIds)
    }

    // --- helpers ---

    private fun makeRecord(
        date: String,
        firestoreId: String? = null,
        attachments: List<Attachment> = emptyList(),
    ) = CatRecord(
        firestoreId = firestoreId,
        date = date,
        recordType = "DAILY_NOTE",
        record = "테스트 기록",
        attachments = attachments,
    )
}
