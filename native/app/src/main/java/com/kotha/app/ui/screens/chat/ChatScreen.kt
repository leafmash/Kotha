package com.kotha.app.ui.screens.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.R
import com.kotha.app.data.chat.MessageRules
import com.kotha.app.data.model.Message
import com.kotha.app.ui.components.ConfirmDialog
import com.kotha.app.ui.screens.call.rememberCallStarter
import com.kotha.app.ui.components.OfflineBar
import com.kotha.app.ui.components.ReportSheet
import com.kotha.app.ui.screens.chat.media.AttachSheet
import com.kotha.app.ui.screens.chat.media.AttachmentPreview
import com.kotha.app.ui.screens.chat.media.MediaCallbacks
import com.kotha.app.ui.screens.chat.media.MediaViewer
import com.kotha.app.ui.screens.chat.media.ViewerItem
import com.kotha.app.ui.theme.CovaTheme
import com.kotha.app.util.MessageText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenUser: (String, String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val startCall = rememberCallStarter()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val header by viewModel.header.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val chat by viewModel.chat.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val mode by viewModel.composerMode.collectAsStateWithLifecycle()
    val replyTo by viewModel.replyTo.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val selected by viewModel.selectedMessages.collectAsStateWithLifecycle()
    val menu by viewModel.menu.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val picks by viewModel.pendingPicks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var highlightedId by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<Message?>(null) }
    var deleteTargets by remember { mutableStateOf<List<Message>?>(null) }
    var forwardTargets by remember { mutableStateOf<List<Message>?>(null) }
    var showMute by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmDeleteChat by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Message?>(null) }
    var showAttach by rememberSaveable { mutableStateOf(false) }
    var viewerId by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val group = chat?.group == true
    val selecting = selected.isNotEmpty()
    val selectedIds = remember(selected) { selected.map { it.id }.toSet() }

    BackHandler(enabled = selecting) { viewModel.clearSelection() }
    BackHandler(enabled = recording != null) { viewModel.cancelRecording() }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        viewModel.onPicked(it)
    }
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        viewModel.onPicked(it)
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { done ->
        val target = cameraTarget
        cameraTarget = null
        if (done && target != null) viewModel.onPicked(listOf(Uri.parse(target)))
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { done ->
        val target = cameraTarget
        cameraTarget = null
        if (done && target != null) viewModel.onPicked(listOf(Uri.parse(target)))
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.startRecording()
        } else {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.chat_mic_permission)) }
        }
    }
    val launchCamera: (Boolean) -> Unit = { video ->
        val target = viewModel.newCameraUri(video)
        cameraTarget = target.toString()
        val started = runCatching {
            if (video) videoLauncher.launch(target) else photoLauncher.launch(target)
        }
        if (started.isFailure) {
            cameraTarget = null
            scope.launch { snackbar.showSnackbar(context.getString(R.string.camera_unavailable)) }
        }
    }
    val mediaCallbacks = remember(viewModel) {
        MediaCallbacks(
            voice = viewModel.voice,
            onOpen = { id ->
                val message = viewModel.items.value
                    .filterIsInstance<ChatItem.Bubble>()
                    .map { it.message }
                    .firstOrNull { it.id == id }
                if (message != null) {
                    if (message.type == "file") {
                        if (message.url.isNotEmpty()) {
                            val open = Intent(Intent.ACTION_VIEW, Uri.parse(message.url))
                            val result = runCatching { context.startActivity(open) }
                            if (result.isFailure) {
                                scope.launch { snackbar.showSnackbar(context.getString(R.string.file_open_fail)) }
                            }
                        }
                    } else if (message.type == "image" || message.type == "video") {
                        viewerId = id
                    }
                }
            },
            onRetry = viewModel::retryUpload,
            onCancel = viewModel::cancelUpload
        )
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbar.showSnackbar(it.resolve(context)) }
    }
    LaunchedEffect(highlightedId) {
        if (highlightedId != null) {
            delay(1_300)
            highlightedId = null
        }
    }
    val newest = items.firstOrNull()
    LaunchedEffect(newest?.key) {
        val mine = (newest as? ChatItem.Bubble)?.mine == true
        if (mine || listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    val nearTop by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearTop, hasMore, items.size) {
        if (nearTop && hasMore) viewModel.loadOlder()
    }
    val showScrollDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CovaTheme.colors.chatBackground)
    ) {
        if (selecting) {
            val single = selected.singleOrNull()
            val alive = selected.all { !it.deleted }
            SelectionBar(
                count = selected.size,
                canReply = single != null && alive,
                canCopy = alive && selected.all { MessageText.copyable(it) != null },
                canForward = selected.all { MessageRules.canForward(it) },
                canEdit = single != null && single.from == viewModel.uid &&
                    MessageRules.canEdit(single, System.currentTimeMillis()),
                canReport = single != null && alive && single.from != viewModel.uid && single.upload == null &&
                    single.type != "system" && single.type != "call",
                onClose = viewModel::clearSelection,
                onReply = {
                    single?.let { viewModel.setReply(it, MessageText.preview(context, it)) }
                    viewModel.clearSelection()
                },
                onCopy = {
                    clipboard.setText(AnnotatedString(selected.joinToString("\n") { MessageText.copyable(it).orEmpty() }))
                    viewModel.notifyCopied()
                    viewModel.clearSelection()
                },
                onForward = {
                    forwardTargets = selected
                    viewModel.clearSelection()
                },
                onEdit = { editTarget = single },
                onReport = {
                    reportTarget = single
                    viewModel.clearSelection()
                },
                onDelete = {
                    val list = selected
                    if (list.all { it.deleted }) viewModel.deleteMessages(list, false) else deleteTargets = list
                }
            )
            if (single != null && !single.deleted && single.upload == null) {
                ReactionRow(
                    current = single.reactions[viewModel.uid],
                    onReact = {
                        viewModel.react(single, it)
                        viewModel.clearSelection()
                    }
                )
            }
        } else {
            ChatHeader(
                header = header,
                menu = menu,
                onBack = onBack,
                onVoiceCall = { startCall(viewModel.chatId, menu.peerUid, false) },
                onVideoCall = { startCall(viewModel.chatId, menu.peerUid, true) },
                onOpenInfo = {
                    if (menu.group) {
                        onOpenGroup(viewModel.chatId)
                    } else if (menu.peerUid.isNotEmpty()) {
                        onOpenUser(menu.peerUid, viewModel.chatId)
                    }
                },
                onTogglePin = viewModel::togglePin,
                onToggleArchive = viewModel::toggleArchive,
                onToggleMute = { if (menu.muted) viewModel.mute(0L) else showMute = true },
                onToggleBlock = {
                    if (menu.blocked) viewModel.unblock(menu.peerUid) else confirmBlock = true
                },
                onDeleteConversation = { confirmDeleteChat = true }
            )
        }
        OfflineBar(offline = !online)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                items(items, key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.Day -> DayChip(item.ms)
                        is ChatItem.HiddenNote -> HiddenNote()
                        is ChatItem.System -> item.message.sys?.let {
                            SystemEventRow(it, item.message.from, viewModel.uid, users)
                        }
                        is ChatItem.Call -> item.message.callLog?.let {
                            CallEventRow(it, item.mine, item.message.atMs)
                        }
                        is ChatItem.Bubble -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (item.message.id in selectedIds) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                        ) {
                            MessageBubble(
                                item = item,
                                group = group,
                                senderName = users[item.message.from]?.name.orEmpty(),
                                highlighted = highlightedId == item.message.id,
                                selecting = selecting,
                                media = mediaCallbacks,
                                onClick = { if (selecting) viewModel.toggleSelect(item.message.id) },
                                onLongPress = {
                                    if (selecting) viewModel.toggleSelect(item.message.id)
                                    else viewModel.startSelect(item.message.id)
                                },
                                onReply = {
                                    viewModel.setReply(item.message, MessageText.preview(context, item.message))
                                },
                                onQuoteClick = { id ->
                                    val index = items.indexOfFirst { it.key == id }
                                    if (index >= 0) {
                                        highlightedId = id
                                        scope.launch { listState.animateScrollToItem(index) }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            ScrollDownButton(
                visible = showScrollDown,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        }
        when (val current = mode) {
            ComposerMode.Enabled -> Composer(
                chatId = viewModel.chatId,
                initialText = viewModel.initialDraft(),
                replyTo = replyTo,
                onCancelReply = viewModel::clearReply,
                recording = recording,
                onTextChange = viewModel::onInput,
                onSend = viewModel::send,
                onAttach = { showAttach = true },
                onStartRecording = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.startRecording()
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopRecording = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.finishRecording()
                },
                onCancelRecording = viewModel::cancelRecording
            )
            ComposerMode.ReadOnly -> InfoBar(text = stringResource(R.string.ginfo_ro_bar))
            is ComposerMode.Blocked -> InfoBar(
                text = stringResource(R.string.block_bar, current.name.ifEmpty { stringResource(R.string.common_user) }),
                actionLabel = stringResource(R.string.block_unblock),
                onAction = { viewModel.unblock(current.peerUid) }
            )
        }
    }

    if (showAttach) {
        AttachSheet(
            onDismiss = { showAttach = false },
            onGallery = {
                showAttach = false
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onCamera = {
                showAttach = false
                launchCamera(false)
            },
            onVideo = {
                showAttach = false
                launchCamera(true)
            },
            onDocument = {
                showAttach = false
                documentLauncher.launch(arrayOf("*/*"))
            }
        )
    }

    if (picks.isNotEmpty()) {
        AttachmentPreview(
            picks = picks,
            frameOf = viewModel::previewFrame,
            onRemove = viewModel::removePick,
            onSend = viewModel::sendPicks,
            onDismiss = viewModel::clearPicks
        )
    }

    val openViewerId = viewerId
    if (openViewerId != null) {
        val you = stringResource(R.string.common_you)
        val viewerItems = remember(openViewerId) {
            viewModel.items.value
                .asReversed()
                .mapNotNull { (it as? ChatItem.Bubble)?.message }
                .filter {
                    (it.type == "image" || it.type == "video") && !it.deleted &&
                        (it.url.isNotEmpty() || it.upload?.localPath.orEmpty().isNotEmpty())
                }
                .map {
                    ViewerItem(
                        id = it.id,
                        type = it.type,
                        url = it.url,
                        localPath = it.upload?.localPath.orEmpty(),
                        name = it.name,
                        sender = if (it.from == viewModel.uid) you else users[it.from]?.name.orEmpty(),
                        atMs = it.atMs
                    )
                }
        }
        if (viewerItems.any { it.id == openViewerId }) {
            MediaViewer(
                items = viewerItems,
                startId = openViewerId,
                voice = viewModel.voice,
                onDismiss = { viewerId = null }
            )
        } else {
            LaunchedEffect(openViewerId) { viewerId = null }
        }
    }

    reportTarget?.let { target ->
        ReportSheet(
            title = stringResource(R.string.report_title_message),
            showBlock = !viewModel.isBlocked(target.from),
            onSubmit = { reason, note, alsoBlock ->
                reportTarget = null
                viewModel.report(target, reason, note, alsoBlock)
            },
            onDismiss = { reportTarget = null }
        )
    }

    editTarget?.let { target ->
        EditMessageDialog(
            initial = target.text,
            onSave = {
                viewModel.editMessage(target, it)
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    deleteTargets?.let { list ->
        DeleteMessagesDialog(
            count = list.size,
            everyoneAllowed = list.any { !it.deleted } && list.all { it.from == viewModel.uid },
            onDeleteForAll = {
                viewModel.deleteMessages(list, true)
                deleteTargets = null
            },
            onDeleteForMe = {
                viewModel.deleteMessages(list, false)
                deleteTargets = null
            },
            onDismiss = { deleteTargets = null }
        )
    }

    forwardTargets?.let { list ->
        ForwardSheet(
            messages = list,
            onDismiss = { forwardTargets = null },
            onResult = { scope.launch { snackbar.showSnackbar(it.resolve(context)) } }
        )
    }

    if (showMute) {
        MuteDialog(
            onPick = {
                showMute = false
                viewModel.mute(it)
            },
            onDismiss = { showMute = false }
        )
    }

    if (confirmBlock) {
        ConfirmDialog(
            title = stringResource(R.string.block_confirm_title, menu.peerName.ifEmpty { stringResource(R.string.common_user) }),
            text = stringResource(R.string.block_confirm_text),
            confirmLabel = stringResource(R.string.block_action),
            destructive = true,
            onConfirm = {
                confirmBlock = false
                viewModel.block(menu.peerUid, menu.peerName)
            },
            onDismiss = { confirmBlock = false }
        )
    }

    if (confirmDeleteChat) {
        ConfirmDialog(
            title = stringResource(R.string.chat_delete_title),
            text = stringResource(R.string.chat_delete_text),
            confirmLabel = stringResource(R.string.chat_delete),
            destructive = true,
            onConfirm = {
                confirmDeleteChat = false
                viewModel.deleteConversation(onBack)
            },
            onDismiss = { confirmDeleteChat = false }
        )
    }
}

@Composable
private fun ScrollDownButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        SmallFloatingActionButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.chat_scroll_down)
            )
        }
    }
}
