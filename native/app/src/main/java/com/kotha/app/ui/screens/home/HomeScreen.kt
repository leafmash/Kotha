package com.kotha.app.ui.screens.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.R
import com.kotha.app.ui.components.OfflineBar
import com.kotha.app.ui.components.TermsGateDialog
import com.kotha.app.ui.components.VerifyEmailBar
import com.kotha.app.ui.screens.calls.CallHistoryContent
import com.kotha.app.ui.screens.chats.ChatListContent
import com.kotha.app.ui.screens.chats.ChatListViewModel
import com.kotha.app.ui.screens.chats.FindContactSheet
import com.kotha.app.ui.screens.group.GroupPickerSheet
import com.kotha.app.ui.screens.group.PickerMode
import com.kotha.app.ui.theme.CovaTheme
import com.kotha.app.util.Format

private enum class HomeTab(
    @StringRes val label: Int,
    @StringRes val empty: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    Chats(R.string.common_chats, R.string.list_no_chats, Icons.Outlined.Chat, Icons.Filled.Chat),
    Groups(R.string.common_groups, R.string.list_no_chats, Icons.Outlined.Groups, Icons.Filled.Groups),
    Calls(R.string.common_calls, R.string.calls_no_calls, Icons.Outlined.Call, Icons.Filled.Call)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenChat: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    listViewModel: ChatListViewModel = hiltViewModel()
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var showNewGroup by rememberSaveable { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val tabs = HomeTab.entries
    val needsTerms by viewModel.needsTerms.collectAsStateWithLifecycle()
    val unverifiedEmail by viewModel.unverifiedEmail.collectAsStateWithLifecycle()
    val resendLocked by viewModel.resendLocked.collectAsStateWithLifecycle()
    val listBase by listViewModel.base.collectAsStateWithLifecycle()
    val online by listViewModel.online.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) }
    }
    LaunchedEffect(Unit) {
        listViewModel.events.collect { snackbar.showSnackbar(it.resolve(context)) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.checkVerified(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { showFind = true }) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = stringResource(R.string.common_add_contact))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.common_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_new_group)) },
                            leadingIcon = { Icon(Icons.Outlined.GroupAdd, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                showNewGroup = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_profile)) },
                            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onOpenProfile()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_settings)) },
                            leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onOpenSettings()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = {
                            if (selected != index) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selected = index
                            }
                        },
                        icon = {
                            val unread = if (tab == HomeTab.Chats) listBase.totalUnread else 0
                            BadgedBox(
                                badge = {
                                    if (unread > 0) {
                                        Badge(
                                            containerColor = CovaTheme.colors.lantern,
                                            contentColor = CovaTheme.colors.onLantern
                                        ) { Text(if (unread > 99) "99+" else Format.number(unread)) }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selected == index) tab.selectedIcon else tab.icon,
                                    contentDescription = null
                                )
                            }
                        },
                        label = { Text(stringResource(tab.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        )
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            unverifiedEmail?.let { email ->
                VerifyEmailBar(
                    email = email,
                    resendLocked = resendLocked,
                    onResend = viewModel::resendVerification,
                    onVerified = { viewModel.checkVerified(true) }
                )
            }
            OfflineBar(offline = !online)
            when (tabs[selected]) {
                HomeTab.Chats -> ChatListContent(
                    groupsOnly = false,
                    viewModel = listViewModel,
                    onOpenChat = onOpenChat,
                    modifier = Modifier.weight(1f)
                )
                HomeTab.Groups -> ChatListContent(
                    groupsOnly = true,
                    viewModel = listViewModel,
                    onOpenChat = onOpenChat,
                    modifier = Modifier.weight(1f)
                )
                HomeTab.Calls -> CallHistoryContent(modifier = Modifier.weight(1f))
            }
        }
    }

    if (showFind) {
        FindContactSheet(
            onDismiss = { showFind = false },
            onOpenChat = {
                showFind = false
                onOpenChat(it)
            }
        )
    }

    if (showNewGroup) {
        GroupPickerSheet(
            mode = PickerMode.Create,
            chatId = "",
            onDismiss = { showNewGroup = false },
            onCreated = {
                showNewGroup = false
                onOpenChat(it)
            },
            onAdded = {}
        )
    }

    if (needsTerms) {
        TermsGateDialog(onAgree = viewModel::acceptTerms, onDecline = viewModel::declineTerms)
    }
}
