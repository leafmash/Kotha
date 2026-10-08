package com.kotha.app.ui.screens.chat

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.kotha.app.R
import com.kotha.app.core.AppConfig
import com.kotha.app.data.app.AppForeground
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.chat.ChatActionsRepository
import com.kotha.app.data.chat.ChatListRepository
import com.kotha.app.data.chat.ChatRepository
import com.kotha.app.data.chat.DraftStore
import com.kotha.app.data.chat.MessageRules
import com.kotha.app.data.chat.ReplyRef
import com.kotha.app.data.media.MediaJob
import com.kotha.app.data.media.JobState
import com.kotha.app.data.media.MediaJobStore
import com.kotha.app.data.media.MediaSender
import com.kotha.app.data.media.MediaStager
import com.kotha.app.data.media.PickedMedia
import com.kotha.app.data.media.RecordingState
import com.kotha.app.data.media.VoicePlayer
import com.kotha.app.data.media.VoiceRecorder
import com.kotha.app.data.model.Chat
import com.kotha.app.data.model.Message
import com.kotha.app.data.model.UploadStage
import com.kotha.app.data.prefs.AppPreferences
import com.kotha.app.data.report.ReportRepository
import com.kotha.app.data.report.ReportTarget
import com.kotha.app.data.model.UploadUi
import com.kotha.app.data.model.UserProfile
import com.kotha.app.data.net.NetworkMonitor
import com.kotha.app.data.presence.PresenceInfo
import com.kotha.app.data.presence.PresenceRepository
import com.kotha.app.data.user.UserRepository
import com.kotha.app.ui.UiMessage
import com.kotha.app.util.Format
import com.kotha.app.util.ticker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface HeaderStatus {
    data object None : HeaderStatus
    data class Typing(val names: List<String>) : HeaderStatus
    data class Active(val info: PresenceInfo, val now: Long) : HeaderStatus
    data class Members(val count: Int) : HeaderStatus
}

sealed interface ComposerMode {
    data object Enabled : ComposerMode
    data class Blocked(val peerUid: String, val name: String) : ComposerMode
    data object ReadOnly : ComposerMode
}

data class ChatMenuUi(
    val group: Boolean = false,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val muted: Boolean = false,
    val peerUid: String = "",
    val peerName: String = "",
    val blocked: Boolean = false
)

data class HeaderUi(
    val name: String,
    val photo: String,
    val online: Boolean,
    val status: HeaderStatus
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val chatListRepository: ChatListRepository,
    private val actions: ChatActionsRepository,
    private val presenceRepository: PresenceRepository,
    private val draftStore: DraftStore,
    private val preferences: AppPreferences,
    private val reportRepository: ReportRepository,
    private val mediaSender: MediaSender,
    private val jobStore: MediaJobStore,
    private val stager: MediaStager,
    private val recorder: VoiceRecorder,
    val voice: VoicePlayer,
    authRepository: AuthRepository,
    userRepository: UserRepository,
    appForeground: AppForeground,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val uid: String = authRepository.user?.uid.orEmpty()

    private val directPeerId: String =
        if (chatId.contains("_")) chatId.split("_").firstOrNull { it != uid }.orEmpty() else ""

    private val limit = MutableStateFlow(AppConfig.PAGE)

    val chat: StateFlow<Chat?> = chatRepository.observeChat(chatId).stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        chatListRepository.chats.value.firstOrNull { it.id == chatId }
    )

    private val messages: StateFlow<List<Message>> = limit
        .flatMapLatest { chatRepository.observeMessages(chatId, it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val users: StateFlow<Map<String, UserProfile>> = userRepository.users

    private val localMessages: StateFlow<List<Message>> = combine(jobStore.jobs, jobStore.progress) { jobs, progress ->
        jobs.filter { it.chatId == chatId && it.uid == uid }.map { it.toMessage(progress[it.id] ?: 0f) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val items: StateFlow<List<ChatItem>> = combine(
        messages,
        localMessages,
        chatListRepository.blocked,
        chatListRepository.cleared,
        chat
    ) { list, local, blocked, cleared, current ->
        buildChatItems(mergeMessages(list, local), uid, current?.group == true, blocked, cleared[chatId] ?: 0L)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hasMore: StateFlow<Boolean> = combine(messages, limit) { list, max -> list.size >= max }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val header: StateFlow<HeaderUi> = combine(
        chat,
        userRepository.users,
        presenceRepository.peers,
        ticker(AppConfig.PRESENCE_TICK_MS)
    ) { current, all, rt, now ->
        if (current?.group == true) {
            val typers = current.typing
                .filter { it.value && it.key != uid }
                .keys
                .mapNotNull { all[it]?.name }
            HeaderUi(
                name = current.name,
                photo = current.photo,
                online = false,
                status = if (typers.isNotEmpty()) HeaderStatus.Typing(typers) else HeaderStatus.Members(current.members.size)
            )
        } else {
            val peerId = current?.peerId(uid)?.ifEmpty { directPeerId } ?: directPeerId
            val peer = all[peerId]
            val info = presenceRepository.presenceOf(peer, rt[peerId])
            val typing = current?.typing?.get(peerId) == true
            HeaderUi(
                name = peer?.name.orEmpty(),
                photo = peer?.photo.orEmpty(),
                online = info.online,
                status = when {
                    peer == null -> HeaderStatus.None
                    typing -> HeaderStatus.Typing(emptyList())
                    else -> HeaderStatus.Active(info, presenceRepository.serverNow())
                }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HeaderUi("", "", false, HeaderStatus.None))

    val composerMode: StateFlow<ComposerMode> = combine(
        chat,
        chatListRepository.blocked,
        userRepository.users
    ) { current, blocked, all ->
        when {
            current?.group == true ->
                if (current.adminOnly && !current.isAdmin(uid)) ComposerMode.ReadOnly else ComposerMode.Enabled
            else -> {
                val peerId = current?.peerId(uid)?.ifEmpty { directPeerId } ?: directPeerId
                if (peerId.isNotEmpty() && blocked.contains(peerId)) {
                    ComposerMode.Blocked(peerId, all[peerId]?.name.orEmpty())
                } else {
                    ComposerMode.Enabled
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ComposerMode.Enabled)

    val online: StateFlow<Boolean> = networkMonitor.online

    val replyTo = MutableStateFlow<ReplyRef?>(null)

    val recording: StateFlow<RecordingState?> = recorder.state

    val pendingPicks = MutableStateFlow<List<PickedMedia>>(emptyList())

    private val mutableEvents = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val events: SharedFlow<UiMessage> = mutableEvents.asSharedFlow()

    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    val selectedMessages: StateFlow<List<Message>> = combine(selectedIds, items) { ids, list ->
        list.filterIsInstance<ChatItem.Bubble>().map { it.message }.filter { it.id in ids }.reversed()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val menu: StateFlow<ChatMenuUi> = combine(
        chat,
        chatListRepository.prefs,
        chatListRepository.blocked,
        userRepository.users,
        ticker(AppConfig.PRESENCE_TICK_MS)
    ) { current, prefs, blocked, all, now ->
        val peerId = if (current?.group == true) "" else current?.peerId(uid)?.ifEmpty { directPeerId } ?: directPeerId
        ChatMenuUi(
            group = current?.group == true,
            pinned = prefs.pinned.containsKey(chatId),
            archived = prefs.archived.containsKey(chatId),
            muted = (prefs.muted[chatId] ?: 0L) > now,
            peerUid = peerId,
            peerName = all[peerId]?.name.orEmpty(),
            blocked = peerId.isNotEmpty() && blocked.contains(peerId)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatMenuUi())

    private var typing = false
    private var typingJob: Job? = null
    private var lastReadKey = ""

    init {
        chatListRepository.setActive(chatId)
        viewModelScope.launch {
            appForeground.foreground.filter { !it }.collect { cancelRecording() }
        }
        viewModelScope.launch {
            combine(chat, appForeground.foreground) { current, foreground -> current to foreground }
                .collect { (current, foreground) ->
                    val pending = current?.unread?.get(uid) ?: 0L
                    if (foreground && current != null && pending > 0) {
                        val key = "$pending:${current.lastAtMs}"
                        if (key != lastReadKey) {
                            lastReadKey = key
                            chatRepository.markRead(chatId)
                        }
                    }
                }
        }
        viewModelScope.launch {
            combine(messages, appForeground.foreground, preferences.readReceipts) { list, foreground, receipts ->
                if (foreground && receipts) {
                    list.filter { it.from != uid && it.type != "system" && it.status != "seen" }.map { it.id }
                } else {
                    emptyList()
                }
            }
                .filter { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { ids -> chatRepository.markSeen(chatId, ids) }
        }
    }

    fun initialDraft(): String = draftStore.get(chatId)

    fun isBlocked(id: String): Boolean = chatListRepository.blocked.value.contains(id)

    fun report(message: Message, reason: String, note: String, alsoBlock: Boolean) {
        val media = message.type != "text"
        val target = ReportTarget(
            type = "message",
            chatId = chatId,
            reportedUid = message.from,
            messageId = message.id,
            content = if (media) message.url else message.text,
            contentType = message.type
        )
        viewModelScope.launch {
            val sent = reportRepository.submit(target, reason, note, alsoBlock)
            mutableEvents.emit(UiMessage(if (sent) R.string.report_sent else R.string.report_fail))
        }
    }

    fun loadOlder() {
        if (hasMore.value) limit.update { it + AppConfig.PAGE }
    }

    fun onInput(text: String) {
        draftStore.set(chatId, text)
        if (text.isBlank()) {
            stopTyping()
            return
        }
        if (!typing) {
            typing = true
            chatRepository.setTyping(chatId, true)
        }
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(AppConfig.TYPING_IDLE_MS)
            stopTyping()
        }
    }

    fun setReply(message: Message, previewText: String) {
        replyTo.value = ReplyRef(message.id, previewText)
    }

    fun clearReply() {
        replyTo.value = null
    }

    fun send(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        val members = chat.value?.members ?: listOf(uid, directPeerId)
        val reply = replyTo.value
        replyTo.value = null
        stopTyping()
        draftStore.set(chatId, "")
        viewModelScope.launch {
            try {
                chatRepository.send(chatId, members, mapOf("type" to "text", "text" to value), reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val denied = (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                mutableEvents.emit(UiMessage(if (denied) R.string.chat_cannot_send else R.string.chat_send_fail))
            }
        }
    }

    fun onPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val picks = withContext(Dispatchers.IO) { uris.take(AppConfig.ATTACH_MAX).map { stager.describe(it) } }
            pendingPicks.update { (it + picks).take(AppConfig.ATTACH_MAX) }
        }
    }

    fun removePick(index: Int) {
        pendingPicks.update { list -> list.filterIndexed { position, _ -> position != index } }
    }

    fun clearPicks() {
        pendingPicks.value = emptyList()
    }

    fun newCameraUri(video: Boolean): Uri = stager.newCameraUri(video)

    fun previewFrame(uri: Uri): Bitmap? = stager.previewFrame(uri)

    fun sendPicks() {
        val picks = pendingPicks.value
        if (picks.isEmpty()) return
        pendingPicks.value = emptyList()
        val members = chat.value?.members ?: listOf(uid, directPeerId)
        val reply = replyTo.value
        replyTo.value = null
        stopTyping()
        viewModelScope.launch {
            val failed = mediaSender.enqueue(chatId, members, picks, reply)
            if (failed > 0) mutableEvents.emit(UiMessage(R.string.attach_prepare_fail))
        }
    }

    fun startRecording() {
        voice.stop()
        stopTyping()
        if (!recorder.start()) mutableEvents.tryEmit(UiMessage(R.string.chat_record_fail))
    }

    fun finishRecording() {
        val result = recorder.stop() ?: return
        val members = chat.value?.members ?: listOf(uid, directPeerId)
        val reply = replyTo.value
        replyTo.value = null
        viewModelScope.launch {
            if (!mediaSender.enqueueVoice(chatId, members, result, reply)) {
                mutableEvents.emit(UiMessage(R.string.chat_upload_fail))
            }
        }
    }

    fun cancelRecording() {
        recorder.cancel()
    }

    fun retryUpload(id: String) {
        mediaSender.retry(id)
    }

    fun cancelUpload(id: String) {
        mediaSender.cancel(id)
    }

    fun startSelect(id: String) {
        selectedIds.value = setOf(id)
    }

    fun toggleSelect(id: String) {
        selectedIds.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun deleteMessages(targets: List<Message>, forAll: Boolean) {
        val newestId = messages.value.firstOrNull()?.id
        val local = targets.filter { it.upload != null }
        local.forEach { mediaSender.cancel(it.id) }
        val remote = targets.filter { it.upload == null }
        if (remote.isEmpty()) {
            clearSelection()
            return
        }
        viewModelScope.launch {
            try {
                chatRepository.deleteMessages(chatId, remote, forAll, newestId)
                clearSelection()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableEvents.emit(UiMessage(R.string.msg_delete_fail))
            }
        }
    }

    fun editMessage(message: Message, text: String) {
        val value = text.trim()
        val minutes = Format.number(AppConfig.EDIT_WINDOW_MS / 60_000L)
        if (value.isEmpty() || value == message.text) return
        if (MessageRules.editLeftMs(message, System.currentTimeMillis()) <= 0) {
            mutableEvents.tryEmit(UiMessage(R.string.msg_edit_expired, listOf(minutes)))
            return
        }
        val wasLast = messages.value.firstOrNull()?.id == message.id
        viewModelScope.launch {
            try {
                chatRepository.editMessage(chatId, message.id, value, wasLast)
                clearSelection()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val denied = (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                mutableEvents.emit(
                    if (denied) UiMessage(R.string.msg_edit_expired, listOf(minutes)) else UiMessage(R.string.msg_edit_fail)
                )
            }
        }
    }

    fun togglePin() {
        viewModelScope.launch { mutableEvents.emit(UiMessage(actions.togglePin(chatId))) }
    }

    fun toggleArchive() {
        viewModelScope.launch { mutableEvents.emit(UiMessage(actions.toggleArchive(chatId))) }
    }

    fun mute(until: Long) {
        viewModelScope.launch { mutableEvents.emit(UiMessage(actions.mute(chatId, until))) }
    }

    fun deleteConversation(onDone: () -> Unit) {
        viewModelScope.launch {
            val result = actions.clearChats(listOf(chatId))
            mutableEvents.emit(UiMessage(result))
            if (result == R.string.chat_conv_deleted) onDone()
        }
    }

    fun block(peerUid: String, name: String) {
        val task = actions.block(peerUid) ?: return
        task.addOnFailureListener { mutableEvents.tryEmit(UiMessage(R.string.block_fail)) }
        mutableEvents.tryEmit(UiMessage(R.string.block_done, listOf(name)))
    }

    fun react(message: Message, emoji: String) {
        if (message.upload != null) return
        chatRepository.react(chatId, message.id, emoji, message.reactions[uid])
    }

    fun unblock(peerUid: String) {
        viewModelScope.launch {
            try {
                chatRepository.unblock(peerUid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableEvents.emit(UiMessage(R.string.block_unblock_fail))
            }
        }
    }

    fun notifyCopied() {
        mutableEvents.tryEmit(UiMessage(R.string.chat_copied))
    }

    override fun onCleared() {
        if (chatListRepository.activeChatId.value == chatId) chatListRepository.setActive(null)
        stopTyping()
        recorder.cancel()
        voice.stop()
        super.onCleared()
    }

    private fun mergeMessages(server: List<Message>, local: List<Message>): List<Message> {
        if (local.isEmpty()) return server
        val known = server.mapTo(HashSet()) { it.id }
        val extra = local.filter { it.id !in known }
        if (extra.isEmpty()) return server
        return (server + extra).sortedByDescending { it.atMs }
    }

    private fun MediaJob.toMessage(progress: Float): Message = Message(
        id = id,
        from = uid,
        type = kind,
        text = "",
        atMs = createdAtMs,
        pending = true,
        status = "sent",
        replyText = replyText.ifEmpty { null },
        replyId = replyId.ifEmpty { null },
        reactions = emptyMap(),
        hiddenFor = emptyList(),
        deleted = false,
        edited = false,
        forwarded = false,
        url = url,
        name = name,
        size = size,
        duration = durationMs / 1000.0,
        wave = wave.map { it.toFloat() },
        callLog = null,
        sys = null,
        thumb = thumbUrl,
        width = width,
        height = height,
        upload = UploadUi(
            stage = when (state) {
                JobState.QUEUED -> UploadStage.Queued
                JobState.UPLOADING -> UploadStage.Uploading
                JobState.FAILED -> UploadStage.Failed
            },
            progress = progress,
            localPath = path,
            thumbPath = thumbPath
        )
    )

    private fun stopTyping() {
        typingJob?.cancel()
        if (typing) {
            typing = false
            chatRepository.setTyping(chatId, false)
        }
    }
}
