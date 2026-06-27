package com.example.jkapp.ui

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jkapp.data.model.HealthRecord
import com.example.jkapp.data.model.RecordType
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.jkapp.R

@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdd: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = checkNotNull(LocalActivity.current)

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                val authResult = Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(result.data!!)
                viewModel.onDriveAuthSuccess(authResult.accessToken ?: "")
            } catch (e: Exception) {
                viewModel.onDriveAuthFailed(e.message)
            }
        } else {
            viewModel.onDriveAuthFailed(activity.getString(R.string.drive_auth_denied))
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.isAuthorized) return@LaunchedEffect
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_FILE)))
            .build()
        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    authLauncher.launch(
                        IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build()
                    )
                } else {
                    viewModel.onDriveAuthSuccess(result.accessToken ?: "")
                }
            }
            .addOnFailureListener { e -> viewModel.onDriveAuthFailed(e.message) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is DiaryUiState.NeedsAuth, is DiaryUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is DiaryUiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.loadRecords() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            is DiaryUiState.Success -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = state.data.petName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, bottom = 80.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.data.records, key = { it.date }) { record ->
                            DiaryListItem(
                                record = record,
                                onClick = { onNavigateToDetail(record.date) }
                            )
                        }
                    }
                }
                FloatingActionButton(
                    onClick = onNavigateToAdd,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_record))
                }
            }
        }
    }
}

@Composable
private fun DiaryListItem(record: HealthRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${record.date} (${record.dayOfWeek})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RecordTypeBadge(record.recordType)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when (record.recordType) {
                    RecordType.HOSPITAL_VISIT -> record.hospitalVisit?.category ?: ""
                    RecordType.DAILY_NOTE -> record.dailyNotes?.firstOrNull() ?: ""
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecordTypeBadge(type: RecordType) {
    val (icon, label, containerColor) = when (type) {
        RecordType.HOSPITAL_VISIT -> Triple(
            Icons.Default.LocalHospital,
            "병원 방문",
            MaterialTheme.colorScheme.errorContainer
        )
        RecordType.DAILY_NOTE -> Triple(
            Icons.AutoMirrored.Filled.Notes,
            "일일 기록",
            MaterialTheme.colorScheme.secondaryContainer
        )
    }
    SuggestionChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) },
        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = containerColor)
    )
}
