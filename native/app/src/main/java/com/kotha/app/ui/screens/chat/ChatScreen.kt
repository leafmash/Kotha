package com.kotha.app.ui.screens.chat

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kotha.app.R
import com.kotha.app.ui.components.OfflineBar
import com.kotha.app.ui.theme.CovaTheme
import com.kotha.app.util.MessageText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(onBack: () -> Unit, viewModel: ChatViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val header by viewModel.header.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val chat by viewModel.chat.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val mode by viewModel.composerMode.collectAsStateWithLifecycle()
    val replyTo by viewModel.replyTo.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var actionTarget by remember { mutableStateOf<ChatItem.Bubble?>(null) }
    var highlightedId by remember { mutableStateOf<String?>(null) }
    val group = chat?.group == true

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbar.showSnackbar(context.getString(it)) }
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
        ChatHeader(header = header, onBack = onBack)
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
                        is ChatItem.Bubble -> MessageBubble(
                            item = item,
                            group = group,
                            senderName = users[item.message.from]?.name.orEmpty(),
                            highlighted = highlightedId == item.message.id,
                            onLongPress = { if (!item.message.deleted) actionTarget = item },
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
                onTextChange = viewModel::onInput,
                onSend = viewModel::send
            )
            ComposerMode.ReadOnly -> InfoBar(text = stringResource(R.string.ginfo_ro_bar))
            is ComposerMode.Blocked -> InfoBar(
                text = stringResource(R.string.block_bar, current.name.ifEmpty { stringResource(R.string.common_user) }),
                actionLabel = stringResource(R.string.block_unblock),
                onAction = { viewModel.unblock(current.peerUid) }
            )
        }
    }

    actionTarget?.let { target ->
        val message = target.message
        MessageActionsSheet(
            currentReaction = message.reactions[viewModel.uid],
            canCopy = MessageText.copyable(message) != null,
            onReact = {
                viewModel.react(message, it)
                actionTarget = null
            },
            onReply = {
                viewModel.setReply(message, MessageText.preview(context, message))
                actionTarget = null
            },
            onCopy = {
                MessageText.copyable(message)?.let { clipboard.setText(AnnotatedString(it)) }
                viewModel.notifyCopied()
                actionTarget = null
            },
            onDismiss = { actionTarget = null }
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
