package com.example.jkapp.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.example.jkapp.auth.AuthViewModel
import com.jkapp.R

private enum class MainTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Default.Home),
    ASSET(R.string.tab_asset, Icons.Default.AccountBalance),
    DIARY(R.string.tab_diary, Icons.AutoMirrored.Filled.MenuBook)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AuthViewModel,
    diaryViewModel: DiaryViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdd: () -> Unit
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val currentUser = user ?: return

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var showProfileMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(selectedTab.labelRes)) },
                actions = {
                    Box {
                        IconButton(onClick = { showProfileMenu = true }) {
                            SubcomposeAsyncImage(
                                model = currentUser.photoUrl,
                                contentDescription = stringResource(R.string.profile),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                error = {
                                    ProfileInitial(
                                        displayName = currentUser.displayName,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = showProfileMenu,
                            onDismissRequest = { showProfileMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sign_out)) },
                                onClick = {
                                    showProfileMenu = false
                                    viewModel.signOut()
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                MainTab.HOME -> HomeTabScreen()
                MainTab.ASSET -> AssetScreen()
                MainTab.DIARY -> DiaryScreen(
                    viewModel = diaryViewModel,
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToAdd = onNavigateToAdd
                )
            }
        }
    }
}

@Composable
private fun ProfileInitial(displayName: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
