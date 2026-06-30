package com.jkapp.ui

import android.content.Intent
import com.jkapp.auth.FakeAuthRepository
import com.jkapp.data.drive.DriveAuthRequiredException
import com.jkapp.data.drive.FakeDriveRepository
import com.jkapp.data.firestore.FakeFirestoreRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModelDownloadTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeFirestoreRepository
    private lateinit var fakeDriveRepository: FakeDriveRepository
    private lateinit var viewModel: DiaryViewModel
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeFirestoreRepository()
        fakeDriveRepository = FakeDriveRepository()
        viewModel = DiaryViewModel(
            repository = fakeRepository,
            driveRepository = fakeDriveRepository,
            authRepository = FakeAuthRepository(initialLoggedIn = true),
        )
        tempDir = Files.createTempDirectory("download_test").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    // --- downloadAttachment 성공 ---

    @Test
    fun `downloadAttachment 성공 시 true를 반환한다`() = runTest {
        advanceUntilIdle()
        val destFile = File(tempDir, "test.jpg")

        val result = viewModel.downloadAttachment("file-1", destFile)

        assertTrue(result)
    }

    @Test
    fun `downloadAttachment 성공 시 destFile에 내용이 저장된다`() = runTest {
        advanceUntilIdle()
        val destFile = File(tempDir, "test.jpg")

        viewModel.downloadAttachment("file-1", destFile)

        assertTrue(destFile.exists())
        assertTrue(destFile.length() > 0)
    }

    @Test
    fun `downloadAttachment 완료 후 downloadingAttachmentIds에서 제거된다`() = runTest {
        advanceUntilIdle()
        val destFile = File(tempDir, "test.jpg")

        viewModel.downloadAttachment("file-1", destFile)

        assertFalse("file-1" in viewModel.downloadingAttachmentIds.value)
    }

    // --- downloadAttachment 일반 실패 ---

    @Test
    fun `downloadAttachment 실패 시 false를 반환한다`() = runTest {
        advanceUntilIdle()
        fakeDriveRepository.downloadError = RuntimeException("네트워크 오류")
        val destFile = File(tempDir, "test.jpg")
        destFile.writeText("부분 데이터")

        val result = viewModel.downloadAttachment("file-1", destFile)

        assertFalse(result)
    }

    @Test
    fun `downloadAttachment 실패 시 불완전한 destFile을 삭제한다`() = runTest {
        advanceUntilIdle()
        fakeDriveRepository.downloadError = RuntimeException("네트워크 오류")
        val destFile = File(tempDir, "test.jpg")
        destFile.writeText("부분 데이터")

        viewModel.downloadAttachment("file-1", destFile)

        assertFalse(destFile.exists())
    }

    @Test
    fun `downloadAttachment 실패 시 downloadingAttachmentIds에서 제거된다`() = runTest {
        advanceUntilIdle()
        fakeDriveRepository.downloadError = RuntimeException("오류")
        val destFile = File(tempDir, "test.jpg")

        viewModel.downloadAttachment("file-1", destFile)

        assertFalse("file-1" in viewModel.downloadingAttachmentIds.value)
    }

    @Test
    fun `downloadAttachment 실패 시 driveAuthRecoveryIntent는 설정되지 않는다`() = runTest {
        advanceUntilIdle()
        fakeDriveRepository.downloadError = RuntimeException("일반 오류")
        val destFile = File(tempDir, "test.jpg")

        viewModel.downloadAttachment("file-1", destFile)

        assertNull(viewModel.driveAuthRecoveryIntent.value)
    }

    // --- downloadAttachment 인증 실패 ---

    @Test
    fun `downloadAttachment 인증 실패 시 false를 반환한다`() = runTest {
        advanceUntilIdle()
        val fakeIntent = mockk<Intent>(relaxed = true)
        fakeDriveRepository.downloadError = DriveAuthRequiredException(fakeIntent)
        val destFile = File(tempDir, "test.jpg")

        val result = viewModel.downloadAttachment("file-1", destFile)

        assertFalse(result)
    }

    @Test
    fun `downloadAttachment 인증 실패 시 driveAuthRecoveryIntent가 설정된다`() = runTest {
        advanceUntilIdle()
        val fakeIntent = mockk<Intent>(relaxed = true)
        fakeDriveRepository.downloadError = DriveAuthRequiredException(fakeIntent)
        val destFile = File(tempDir, "test.jpg")

        viewModel.downloadAttachment("file-1", destFile)

        assertNotNull(viewModel.driveAuthRecoveryIntent.value)
    }

    @Test
    fun `downloadAttachment 인증 실패 시 downloadingAttachmentIds에서 제거된다`() = runTest {
        advanceUntilIdle()
        val fakeIntent = mockk<Intent>(relaxed = true)
        fakeDriveRepository.downloadError = DriveAuthRequiredException(fakeIntent)
        val destFile = File(tempDir, "test.jpg")

        viewModel.downloadAttachment("file-1", destFile)

        assertFalse("file-1" in viewModel.downloadingAttachmentIds.value)
    }

    // --- setSharedRootFolderId ---

    @Test
    fun `setSharedRootFolderId 호출 시 driveRepository에 전달된다`() = runTest {
        advanceUntilIdle()

        viewModel.setSharedRootFolderId("shared-folder-id")
        advanceUntilIdle()

        assertTrue(fakeDriveRepository.sharedRootFolderIdSet == "shared-folder-id")
    }

    @Test
    fun `setSharedRootFolderId에 null 전달 시 driveRepository에 null이 전달된다`() = runTest {
        advanceUntilIdle()

        viewModel.setSharedRootFolderId(null)
        advanceUntilIdle()

        assertNull(fakeDriveRepository.sharedRootFolderIdSet)
    }
}
