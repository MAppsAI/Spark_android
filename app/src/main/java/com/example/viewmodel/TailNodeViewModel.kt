package com.example.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.ChatConversation
import com.example.data.model.ChatMessage
import com.example.data.model.DiscoveredLlmModel
import com.example.data.model.FileTransfer
import com.example.data.model.HermesHealthStatus
import com.example.data.model.HermesMcpTool
import com.example.data.model.HermesMessage
import com.example.data.model.HermesToolCall
import com.example.data.model.NodeStatus
import com.example.data.model.RemoteFileItem
import com.example.data.model.SystemTelemetry
import com.example.data.model.TailNode
import com.example.data.model.TerminalLine
import com.example.data.model.TerminalPreset
import com.example.data.repository.TailNodeRepository
import com.example.network.ConnectionTestResult
import com.example.network.DuckDuckGoSearchService
import com.example.network.HermesGatewayService
import com.example.network.LocalLlmService
import com.example.network.RemoteFileService
import com.example.network.RemoteTerminalService
import com.example.network.SearchResult
import com.example.network.SearchSnippet
import com.example.network.TailscalePingService
import com.example.network.TermuxLaunchResult
import com.example.voice.VoiceEngineManager
import com.example.voice.VoiceModelInfo
import com.example.voice.VoiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class TailNodeViewModel(
    application: Application,
    private val repository: TailNodeRepository,
    private val pingService: TailscalePingService = TailscalePingService(),
    private val llmService: LocalLlmService = LocalLlmService(),
    private val fileService: RemoteFileService = RemoteFileService(),
    private val terminalService: RemoteTerminalService = RemoteTerminalService(),
    private val hermesService: HermesGatewayService = HermesGatewayService(),
    private val duckDuckGoSearchService: DuckDuckGoSearchService = DuckDuckGoSearchService()
) : AndroidViewModel(application) {

    val allNodes: StateFlow<List<TailNode>> = repository.allNodes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Node status cache (keyed by nodeId)
    private val _nodeStatuses = MutableStateFlow<Map<Long, NodeStatus>>(emptyMap())
    val nodeStatuses: StateFlow<Map<Long, NodeStatus>> = _nodeStatuses.asStateFlow()

    // Live node telemetry
    private val _telemetryMap = MutableStateFlow<Map<Long, SystemTelemetry>>(emptyMap())
    val telemetryMap: StateFlow<Map<Long, SystemTelemetry>> = _telemetryMap.asStateFlow()

    private val _telemetry = MutableStateFlow<SystemTelemetry?>(null)
    val telemetry: StateFlow<SystemTelemetry?> = _telemetry.asStateFlow()

    // Currently active node
    private val _selectedNode = MutableStateFlow<TailNode?>(null)
    val selectedNode: StateFlow<TailNode?> = _selectedNode.asStateFlow()

    // Navigation Tab (0: Overview, 1: Terminal, 2: Files, 3: LLM Chat, 4: Hermes, 5: Desktop)
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Terminal State
    private val _terminalLines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

    private val _terminalInput = MutableStateFlow("")
    val terminalInput: StateFlow<String> = _terminalInput.asStateFlow()

    private val _isTerminalRunning = MutableStateFlow(false)
    val isTerminalRunning: StateFlow<Boolean> = _isTerminalRunning.asStateFlow()

    private val _isTerminalFocused = MutableStateFlow(false)
    val isTerminalFocused: StateFlow<Boolean> = _isTerminalFocused.asStateFlow()

    fun setTerminalFocused(focused: Boolean) {
        _isTerminalFocused.value = focused
    }

    val terminalPresets: List<TerminalPreset> = terminalService.getPresetCommands("LINUX")

    // File Explorer State
    private val _currentPath = MutableStateFlow(".")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _serverProtocol = MutableStateFlow("SFTP (Port 22)")
    val serverProtocol: StateFlow<String> = _serverProtocol.asStateFlow()

    private val _fileList = MutableStateFlow<List<RemoteFileItem>>(emptyList())
    val fileList: StateFlow<List<RemoteFileItem>> = _fileList.asStateFlow()

    private val _isFileLoading = MutableStateFlow(false)
    val isFileLoading: StateFlow<Boolean> = _isFileLoading.asStateFlow()

    private val _fileListingError = MutableStateFlow<String?>(null)
    val fileListingError: StateFlow<String?> = _fileListingError.asStateFlow()

    private val _selectedFilePreview = MutableStateFlow<Pair<RemoteFileItem, String>?>(null)
    val selectedFilePreview: StateFlow<Pair<RemoteFileItem, String>?> = _selectedFilePreview.asStateFlow()

    private val _recentTransfers = MutableStateFlow<List<FileTransfer>>(emptyList())
    val recentTransfers: StateFlow<List<FileTransfer>> = _recentTransfers.asStateFlow()

    private val _isSshAuthDialogOpen = MutableStateFlow(false)
    val isSshAuthDialogOpen: StateFlow<Boolean> = _isSshAuthDialogOpen.asStateFlow()

    private val _isDownloadingFile = MutableStateFlow<String?>(null)
    val isDownloadingFile: StateFlow<String?> = _isDownloadingFile.asStateFlow()

    private val _lastDownloadedFile = MutableStateFlow<DownloadedFileInfo?>(null)
    val lastDownloadedFile: StateFlow<DownloadedFileInfo?> = _lastDownloadedFile.asStateFlow()

    fun clearLastDownloadedFile() {
        _lastDownloadedFile.value = null
    }

    private val _isUploadingFile = MutableStateFlow<String?>(null)
    val isUploadingFile: StateFlow<String?> = _isUploadingFile.asStateFlow()

    // Multi-Thread Chat & History (Gemini style)
    private val _allConversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    val allConversations: StateFlow<List<ChatConversation>> = _allConversations.asStateFlow()

    private val _currentConversationId = MutableStateFlow("")
    val currentConversationId: StateFlow<String> = _currentConversationId.asStateFlow()

    private val _currentConversationTitle = MutableStateFlow("New Chat")
    val currentConversationTitle: StateFlow<String> = _currentConversationTitle.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // Cross-Machine Model Registry (All models across all nodes)
    private val _allDiscoveredModels = MutableStateFlow<List<DiscoveredLlmModel>>(emptyList())
    val allDiscoveredModels: StateFlow<List<DiscoveredLlmModel>> = _allDiscoveredModels.asStateFlow()

    private val _selectedDiscoveredModel = MutableStateFlow<DiscoveredLlmModel?>(null)
    val selectedDiscoveredModel: StateFlow<DiscoveredLlmModel?> = _selectedDiscoveredModel.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _selectedModel = MutableStateFlow("llama3.2:latest")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput.asStateFlow()

    private val _isLlmGenerating = MutableStateFlow(false)
    val isLlmGenerating: StateFlow<Boolean> = _isLlmGenerating.asStateFlow()

    // DuckDuckGo Web Search Tool State
    private val _isWebSearchEnabled = MutableStateFlow(false)
    val isWebSearchEnabled: StateFlow<Boolean> = _isWebSearchEnabled.asStateFlow()

    private val _isSearchingWeb = MutableStateFlow(false)
    val isSearchingWeb: StateFlow<Boolean> = _isSearchingWeb.asStateFlow()

    private val _lastSearchQuery = MutableStateFlow("")
    val lastSearchQuery: StateFlow<String> = _lastSearchQuery.asStateFlow()

    private val _lastSearchResults = MutableStateFlow<SearchResult?>(null)
    val lastSearchResults: StateFlow<SearchResult?> = _lastSearchResults.asStateFlow()

    private var llmGenerationJob: Job? = null

    // Ambient Voice Mode (On-Device STT/TTS & Cross-Machine LLM)
    val voiceEngineManager: VoiceEngineManager by lazy {
        VoiceEngineManager(getApplication<Application>().applicationContext, viewModelScope)
    }

    val voiceModels: StateFlow<List<VoiceModelInfo>> get() = voiceEngineManager.models
    val voiceState: StateFlow<VoiceState> get() = voiceEngineManager.voiceState
    val voiceRmsDecibels: StateFlow<Float> get() = voiceEngineManager.rmsDecibels
    val voiceLiveSpokenText: StateFlow<String> get() = voiceEngineManager.liveSpokenText
    val voiceLiveAiSpeechText: StateFlow<String> get() = voiceEngineManager.liveAiSpeechText

    fun getLanServerUrl(): String = voiceEngineManager.getLanServerUrl()
    fun setLanServerUrl(url: String) = voiceEngineManager.setLanServerUrl(url)

    fun getVoiceSystemPrompt(): String = voiceEngineManager.getVoiceSystemPrompt()
    fun setVoiceSystemPrompt(text: String) = voiceEngineManager.setVoiceSystemPrompt(text)
    fun voiceSystemPromptOrDefault(): String = voiceEngineManager.getVoiceSystemPrompt()
    val defaultVoiceSystemPrompt: String get() = com.example.voice.DEFAULT_VOICE_SYSTEM_PROMPT

    val selectedSttModel: VoiceModelInfo? get() = voiceEngineManager.selectedSttModel
    val selectedTtsModel: VoiceModelInfo? get() = voiceEngineManager.selectedTtsModel

    fun replayLastSpeech() {
        voiceEngineManager.replayLastSpeech()
    }

    fun clearLiveTranscripts() {
        voiceEngineManager.clearLiveTranscripts()
    }

    private val _isVoiceModeOpen = MutableStateFlow(false)
    val isVoiceModeOpen: StateFlow<Boolean> = _isVoiceModeOpen.asStateFlow()

    private val _isVoiceModelsSheetOpen = MutableStateFlow(false)
    val isVoiceModelsSheetOpen: StateFlow<Boolean> = _isVoiceModelsSheetOpen.asStateFlow()

    private val _llmTestResult = MutableStateFlow<ConnectionTestResult?>(null)
    val llmTestResult: StateFlow<ConnectionTestResult?> = _llmTestResult.asStateFlow()

    private val _isTestingLlm = MutableStateFlow(false)
    val isTestingLlm: StateFlow<Boolean> = _isTestingLlm.asStateFlow()

    // Hermes Agent Gateway State
    private val _hermesSessionId = MutableStateFlow("session_main")
    val hermesSessionId: StateFlow<String> = _hermesSessionId.asStateFlow()

    private val _hermesMessages = MutableStateFlow<List<HermesMessage>>(emptyList())
    val hermesMessages: StateFlow<List<HermesMessage>> = _hermesMessages.asStateFlow()

    private val _isHermesGenerating = MutableStateFlow(false)
    val isHermesGenerating: StateFlow<Boolean> = _isHermesGenerating.asStateFlow()

    private val _hermesHealth = MutableStateFlow<HermesHealthStatus?>(null)
    val hermesHealth: StateFlow<HermesHealthStatus?> = _hermesHealth.asStateFlow()

    private val _isCheckingHermesHealth = MutableStateFlow(false)
    val isCheckingHermesHealth: StateFlow<Boolean> = _isCheckingHermesHealth.asStateFlow()

    private val _hermesMcpTools = MutableStateFlow<List<HermesMcpTool>>(emptyList())
    val hermesMcpTools: StateFlow<List<HermesMcpTool>> = _hermesMcpTools.asStateFlow()

    private val _hermesLiveThinking = MutableStateFlow<String?>(null)
    val hermesLiveThinking: StateFlow<String?> = _hermesLiveThinking.asStateFlow()

    private val _hermesLiveTools = MutableStateFlow<List<HermesToolCall>>(emptyList())
    val hermesLiveTools: StateFlow<List<HermesToolCall>> = _hermesLiveTools.asStateFlow()

    private val _hermesInput = MutableStateFlow("")
    val hermesInput: StateFlow<String> = _hermesInput.asStateFlow()

    // Remote Desktop State
    private val _desktopConnected = MutableStateFlow(false)
    val desktopConnected: StateFlow<Boolean> = _desktopConnected.asStateFlow()

    private val _desktopMouseMode = MutableStateFlow("TOUCH") // TOUCH or CURSOR
    val desktopMouseMode: StateFlow<String> = _desktopMouseMode.asStateFlow()

    private val _desktopQuality = MutableStateFlow("High (1080p)")
    val desktopQuality: StateFlow<String> = _desktopQuality.asStateFlow()

    // Dialog states
    private val _isAddNodeDialogOpen = MutableStateFlow(false)
    val isAddNodeDialogOpen: StateFlow<Boolean> = _isAddNodeDialogOpen.asStateFlow()

    private val _isEditNodeDialogOpen = MutableStateFlow(false)
    val isEditNodeDialogOpen: StateFlow<Boolean> = _isEditNodeDialogOpen.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedInitialNodesIfEmpty()
            allNodes.collect { nodes ->
                if (nodes.isNotEmpty()) {
                    if (_selectedNode.value == null) {
                        selectNode(nodes.first())
                    } else {
                        val currentId = _selectedNode.value?.id
                        val updated = nodes.find { it.id == currentId }
                        if (updated != null && updated != _selectedNode.value) {
                            _selectedNode.value = updated
                        }
                    }
                    refreshAllNodeStatuses(nodes)
                    refreshAllDiscoveredModels()
                }
            }
        }

        // Initialize Chat Conversations
        viewModelScope.launch {
            repository.allConversations.collect { convs ->
                _allConversations.value = convs
                if (convs.isEmpty()) {
                    val initial = ChatConversation(
                        id = UUID.randomUUID().toString(),
                        title = "New Chat",
                        createdAt = System.currentTimeMillis()
                    )
                    repository.insertConversation(initial)
                    _currentConversationId.value = initial.id
                    _currentConversationTitle.value = initial.title
                } else if (_currentConversationId.value.isBlank() || convs.none { it.id == _currentConversationId.value }) {
                    _currentConversationId.value = convs.first().id
                    _currentConversationTitle.value = convs.first().title
                }
            }
        }

        // Collect messages whenever conversation changes using collectLatest to prevent leaks across chats
        viewModelScope.launch {
            _currentConversationId.collectLatest { convId ->
                if (convId.isNotBlank()) {
                    repository.getMessagesForConversation(convId).collect { msgs ->
                        if (_currentConversationId.value == convId) {
                            _chatMessages.value = msgs
                        }
                    }
                } else {
                    _chatMessages.value = emptyList()
                }
            }
        }
    }

    fun selectNode(node: TailNode) {
        _selectedNode.value = node
        _selectedModel.value = node.llmDefaultModel
        _currentPath.value = if (node.osType == "WINDOWS") "C:/" else "."
        _llmTestResult.value = null

        // Reset terminal session display with connection details
        _terminalLines.value = listOf(
            TerminalLine("Target Node: ${node.name} (${node.tailscaleIp})", isCommand = false),
            TerminalLine("SSH: ${node.sshUser}@${node.tailscaleIp}:${node.sshPort} | LLM Endpoint: ${node.getEffectiveLlmBaseUrl()}", isCommand = false)
        )

        refreshFiles()

        // Load file transfers from DB
        viewModelScope.launch {
            repository.getTransfersForNode(node.id).collect { transfers ->
                _recentTransfers.value = transfers
            }
        }

        // Load models for this node
        refreshAvailableModels(node)

        // Load Hermes Agent messages & probe health
        viewModelScope.launch {
            repository.getHermesMessages(node.id, _hermesSessionId.value)?.collect { msgs ->
                _hermesMessages.value = msgs
            }
        }
        checkHermesHealth(node)
        refreshHermesTools(node)
        checkNodeStatus(node)
    }

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    fun refreshAllNodeStatuses(nodes: List<TailNode> = allNodes.value) {
        viewModelScope.launch(Dispatchers.IO) {
            val statusMap = mutableMapOf<Long, NodeStatus>()
            nodes.forEach { node ->
                statusMap[node.id] = pingService.checkNodeReachability(node)
            }
            _nodeStatuses.value = statusMap
        }
    }

    fun checkNodeStatus(node: TailNode) {
        viewModelScope.launch(Dispatchers.IO) {
            val status = pingService.checkNodeReachability(node)
            val currentMap = _nodeStatuses.value.toMutableMap()
            currentMap[node.id] = status
            _nodeStatuses.value = currentMap

            if (status.isOnline) {
                val tele = SystemTelemetry(
                    nodeName = node.name,
                    tailscaleIp = node.tailscaleIp,
                    isOnline = true,
                    latencyMs = status.latencyMs,
                    sshPort = node.sshPort,
                    sshUp = status.sshReachable,
                    llmPort = node.llmPort,
                    llmUp = status.llmReachable,
                    llmBaseUrl = node.getEffectiveLlmBaseUrl(),
                    desktopPort = node.remoteDesktopPort,
                    desktopUp = status.desktopReachable,
                    filePort = node.fileServerPort,
                    fileUp = status.fileServerReachable,
                    lastCheckTime = System.currentTimeMillis()
                )
                _telemetry.value = tele
                val curTele = _telemetryMap.value.toMutableMap()
                curTele[node.id] = tele
                _telemetryMap.value = curTele
            }
        }
    }

    // Terminal actions
    fun setTerminalInput(input: String) {
        _terminalInput.value = input
    }

    fun executeTerminalCommand(customCmd: String? = null) {
        val node = _selectedNode.value ?: return
        val cmd = (customCmd ?: _terminalInput.value).trim()
        if (cmd.isEmpty()) return

        if (cmd.lowercase() == "clear") {
            _terminalLines.value = emptyList()
            _terminalInput.value = ""
            return
        }

        val userLine = TerminalLine(
            text = "${node.sshUser}@${node.name.lowercase().replace(" ", "-")}:~# $cmd",
            isCommand = true
        )
        _terminalLines.value = _terminalLines.value + userLine
        _terminalInput.value = ""
        _isTerminalRunning.value = true

        viewModelScope.launch {
            val liveBuffer = StringBuilder()
            var streamedLineIdx = -1

            val result = terminalService.executeCommand(
                node = node,
                command = cmd,
                onOutputChunk = { chunk ->
                    liveBuffer.append(chunk)
                    val textSoFar = liveBuffer.toString().trimEnd()
                    if (textSoFar.isNotEmpty()) {
                        val current = _terminalLines.value.toMutableList()
                        if (streamedLineIdx == -1) {
                            current.add(TerminalLine(text = textSoFar, isCommand = false))
                            streamedLineIdx = current.size - 1
                        } else if (streamedLineIdx < current.size) {
                            current[streamedLineIdx] = TerminalLine(text = textSoFar, isCommand = false)
                        }
                        _terminalLines.value = current
                    }
                }
            )

            // Finalize with exit status
            val finalLines = _terminalLines.value.toMutableList()
            if (streamedLineIdx != -1 && streamedLineIdx < finalLines.size) {
                finalLines[streamedLineIdx] = TerminalLine(
                    text = result.output,
                    isCommand = false,
                    isError = result.exitCode != 0
                )
                _terminalLines.value = finalLines
            } else {
                _terminalLines.value = _terminalLines.value + TerminalLine(
                    text = result.output,
                    isCommand = false,
                    isError = result.exitCode != 0
                )
            }
            _isTerminalRunning.value = false

            if (result.requiresPassword) {
                _isSshAuthDialogOpen.value = true
            }

            // Save to DB
            repository.insertTerminalHistory(
                com.example.data.model.TerminalHistory(
                    nodeId = node.id,
                    command = cmd,
                    output = result.output.take(500),
                    exitCode = result.exitCode
                )
            )
        }
    }

    fun openInTermux(context: Context): TermuxLaunchResult? {
        val node = _selectedNode.value ?: return null
        return terminalService.prepareTermuxLaunch(context, node)
    }

    fun openSshAuthDialog() {
        _isSshAuthDialogOpen.value = true
    }

    fun closeSshAuthDialog() {
        _isSshAuthDialogOpen.value = false
    }

    fun saveSshPassword(password: String) {
        val node = _selectedNode.value ?: return
        viewModelScope.launch {
            val updated = node.copy(sshPassword = password)
            repository.updateNode(updated)
            _selectedNode.value = updated
            closeSshAuthDialog()
            _terminalLines.value = _terminalLines.value + TerminalLine(
                text = "[SSH password saved for ${node.sshUser}@${node.tailscaleIp}]",
                isCommand = false
            )
        }
    }

    fun appendTerminalKey(key: String) {
        when (key) {
            "CLEAR" -> _terminalLines.value = emptyList()
            "TAB" -> _terminalInput.value += "    "
            "ESC" -> _terminalInput.value = ""
            else -> _terminalInput.value += key
        }
    }

    fun getSshCommand(): String {
        val node = _selectedNode.value ?: return ""
        return terminalService.getSshCommand(node)
    }

    fun getTailscaleSshCommand(): String {
        val node = _selectedNode.value ?: return ""
        return terminalService.getTailscaleSshCommand(node)
    }

    // Remote File actions (SFTP)
    fun refreshFiles() {
        val node = _selectedNode.value ?: return
        _isFileLoading.value = true
        _fileListingError.value = null
        viewModelScope.launch {
            val result = fileService.listFiles(node, _currentPath.value)
            _fileList.value = result.items
            _fileListingError.value = result.errorMessage
            if (result.serverType.isNotBlank()) {
                _serverProtocol.value = result.serverType
            }
            if (!result.resolvedPath.isNullOrBlank() && (_currentPath.value == "." || _currentPath.value == "/")) {
                _currentPath.value = result.resolvedPath
            }
            _isFileLoading.value = false
        }
    }

    fun navigateToFolder(item: RemoteFileItem) {
        if (!item.isDirectory) {
            previewFile(item)
            return
        }
        _currentPath.value = item.path
        refreshFiles()
    }

    fun navigateUp() {
        val current = _currentPath.value
        val parent = if (current.contains("/")) {
            current.substringBeforeLast("/").ifEmpty { "/" }
        } else {
            ".."
        }
        _currentPath.value = parent
        refreshFiles()
    }

    fun previewFile(item: RemoteFileItem) {
        val node = _selectedNode.value ?: return
        viewModelScope.launch {
            val content = fileService.getFileContent(node, item)
            _selectedFilePreview.value = Pair(item, content)
        }
    }

    fun dismissFilePreview() {
        _selectedFilePreview.value = null
    }

    fun logFileTransfer(fileName: String, sizeBytes: Long, direction: String, speed: String = "SFTP Native") {
        val node = _selectedNode.value ?: return
        viewModelScope.launch {
            val transfer = FileTransfer(
                nodeId = node.id,
                fileName = fileName,
                fileSize = sizeBytes,
                direction = direction,
                status = "COMPLETED",
                remotePath = "${_currentPath.value}/$fileName",
                transferSpeed = speed
            )
            repository.insertFileTransfer(transfer)
        }
    }

    fun downloadFileDirectly(
        item: RemoteFileItem,
        context: Context,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val node = _selectedNode.value ?: return
        _isDownloadingFile.value = item.name
        viewModelScope.launch {
            val res = fileService.downloadFileToDevice(node, item, context)
            _isDownloadingFile.value = null
            if (res.isSuccess) {
                logFileTransfer(item.name, res.bytesDownloaded, "DOWNLOAD (SFTP)", res.transferSpeed)
                val info = DownloadedFileInfo(
                    fileName = item.name,
                    savedPath = res.savedPath ?: "Downloads/${item.name}",
                    savedUri = res.savedUri,
                    bytes = res.bytesDownloaded
                )
                _lastDownloadedFile.value = info
                onComplete(true, "Saved to ${res.savedPath ?: "Downloads/${item.name}"}")
            } else {
                onComplete(false, res.errorMessage ?: "Download failed")
            }
        }
    }

    fun openFile(context: Context, uriString: String?, fileName: String = "") {
        if (uriString.isNullOrBlank()) {
            openDownloadsFolder(context)
            return
        }
        try {
            val uri = Uri.parse(uriString)
            val extension = fileName.substringAfterLast('.', "").lowercase()
            val mimeType = if (extension.isNotBlank()) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
            } else "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open $fileName").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            openDownloadsFolder(context)
        }
    }

    fun openDownloadsFolder(context: Context) {
        try {
            val downloadsIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(downloadsIntent)
        } catch (_: Exception) {
            try {
                val filesIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath), "resource/folder")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(filesIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "Saved to Downloads folder. Check your Files app.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun uploadFileDirectly(
        uri: Uri,
        context: Context,
        onComplete: (Boolean, String) -> Unit
    ) {
        val node = _selectedNode.value ?: return
        var fileName = "upload_${System.currentTimeMillis()}"
        var fileSize = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}

        _isUploadingFile.value = fileName
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _isUploadingFile.value = null
                    onComplete(false, "Could not open selected file")
                    return@launch
                }

                val res = fileService.uploadFile(
                    node = node,
                    remoteDirectory = _currentPath.value,
                    fileName = fileName,
                    inputStream = inputStream,
                    fileSize = fileSize
                )
                _isUploadingFile.value = null

                if (res.isSuccess) {
                    logFileTransfer(fileName, res.bytesUploaded, "UPLOAD (SFTP)", res.transferSpeed)
                    refreshFiles()
                    onComplete(true, "Uploaded '$fileName' (${res.transferSpeed})")
                } else {
                    onComplete(false, res.errorMessage ?: "Upload failed")
                }
            } catch (e: Exception) {
                _isUploadingFile.value = null
                onComplete(false, e.localizedMessage ?: "Upload error")
            }
        }
    }

    fun deleteRemoteFile(
        item: RemoteFileItem,
        onComplete: (Boolean, String) -> Unit
    ) {
        val node = _selectedNode.value ?: return
        viewModelScope.launch {
            val success = fileService.deleteFile(node, item)
            if (success) {
                refreshFiles()
                onComplete(true, "Deleted ${item.name}")
            } else {
                onComplete(false, "Failed to delete ${item.name} (check permissions)")
            }
        }
    }

    fun createRemoteFolder(
        folderName: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val node = _selectedNode.value ?: return
        val trimmed = folderName.trim()
        if (trimmed.isBlank()) {
            onComplete(false, "Folder name cannot be empty")
            return
        }
        viewModelScope.launch {
            val success = fileService.createDirectory(node, _currentPath.value, trimmed)
            if (success) {
                refreshFiles()
                onComplete(true, "Created folder '$trimmed'")
            } else {
                onComplete(false, "Failed to create folder '$trimmed'")
            }
        }
    }

    // Multi-Thread Chat Actions (Gemini style)
    fun setChatInput(input: String) {
        _chatInput.value = input
    }

    fun createNewChat() {
        viewModelScope.launch {
            val newConv = ChatConversation(
                id = UUID.randomUUID().toString(),
                title = "New Chat",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                lastModelUsed = _selectedDiscoveredModel.value?.modelName ?: _selectedModel.value
            )
            repository.insertConversation(newConv)
            _chatMessages.value = emptyList()
            _currentConversationId.value = newConv.id
            _currentConversationTitle.value = newConv.title
        }
    }

    fun selectConversation(convId: String) {
        if (_currentConversationId.value == convId) return
        _chatMessages.value = emptyList()
        _currentConversationId.value = convId
        val conv = _allConversations.value.find { it.id == convId }
        if (conv != null) {
            _currentConversationTitle.value = conv.title
            if (conv.lastModelUsed.isNotBlank() && conv.lastModelUsed != "default") {
                val matchingModel = _allDiscoveredModels.value.find { it.modelName == conv.lastModelUsed }
                if (matchingModel != null) {
                    _selectedDiscoveredModel.value = matchingModel
                }
                _selectedModel.value = conv.lastModelUsed
            }
        }
    }

    fun deleteConversation(convId: String) {
        viewModelScope.launch {
            repository.deleteConversation(convId)
            if (_currentConversationId.value == convId) {
                val remaining = _allConversations.value.filter { it.id != convId }
                if (remaining.isNotEmpty()) {
                    selectConversation(remaining.first().id)
                } else {
                    createNewChat()
                }
            }
        }
    }

    fun refreshAllDiscoveredModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val nodes = allNodes.value
            val modelList = mutableListOf<DiscoveredLlmModel>()
            nodes.forEach { node ->
                val models = try {
                    llmService.getAvailableModels(node)
                } catch (_: Exception) {
                    emptyList()
                }
                models.forEach { m ->
                    modelList.add(
                        DiscoveredLlmModel(
                            modelName = m,
                            nodeId = node.id,
                            nodeName = node.name,
                            nodeOs = node.osType,
                            nodeIp = node.tailscaleIp,
                            port = node.llmPort,
                            llmType = node.llmType
                        )
                    )
                }
            }
            _allDiscoveredModels.value = modelList
            if (_selectedDiscoveredModel.value == null && modelList.isNotEmpty()) {
                val currentNode = _selectedNode.value
                val defaultChoice = modelList.find { it.nodeId == currentNode?.id } ?: modelList.first()
                _selectedDiscoveredModel.value = defaultChoice
                _selectedModel.value = defaultChoice.modelName
            }
        }
    }

    fun selectDiscoveredModel(model: DiscoveredLlmModel) {
        _selectedDiscoveredModel.value = model
        _selectedModel.value = model.modelName
        // If this model belongs to another node, we keep node available
    }

    fun setSelectedModel(model: String) {
        _selectedModel.value = model
        val match = _allDiscoveredModels.value.find { it.modelName == model }
        if (match != null) {
            _selectedDiscoveredModel.value = match
        }
    }

    fun refreshAvailableModels(targetNode: TailNode? = null) {
        val node = targetNode ?: _selectedNode.value ?: return
        viewModelScope.launch {
            val models = llmService.getAvailableModels(node)
            _availableModels.value = models
            if (models.isNotEmpty() && !models.contains(_selectedModel.value)) {
                _selectedModel.value = models.first()
            }
            refreshAllDiscoveredModels()
        }
    }

    fun testLlmEndpoint() {
        val node = _selectedNode.value ?: return
        _isTestingLlm.value = true
        _llmTestResult.value = null
        viewModelScope.launch {
            val result = llmService.testConnection(node)
            _llmTestResult.value = result
            _isTestingLlm.value = false
            if (result.isSuccess) {
                refreshAvailableModels(node)
            }
        }
    }

    fun stopLlmGeneration() {
        llmService.cancelActiveCall()
        llmGenerationJob?.cancel()
        llmGenerationJob = null
        _isLlmGenerating.value = false
        _isSearchingWeb.value = false
        voiceEngineManager.setIdleState()
    }

    fun toggleWebSearch() {
        _isWebSearchEnabled.value = !_isWebSearchEnabled.value
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        _isWebSearchEnabled.value = enabled
    }

    fun sendChatMessage() {
        val rawPrompt = _chatInput.value.trim()
        if (rawPrompt.isEmpty() || _isLlmGenerating.value) return

        var currentConvId = _currentConversationId.value
        if (currentConvId.isBlank()) {
            val newId = UUID.randomUUID().toString()
            currentConvId = newId
            _currentConversationId.value = currentConvId
            val newConv = ChatConversation(
                id = newId,
                title = rawPrompt.take(35).let { if (rawPrompt.length > 35) "$it..." else it },
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                lastModelUsed = _selectedDiscoveredModel.value?.modelName ?: _selectedModel.value
            )
            _currentConversationTitle.value = newConv.title
            viewModelScope.launch {
                try {
                    repository.insertConversation(newConv)
                } catch (_: Exception) {}
            }
        }

        // Determine destination node: from selectedDiscoveredModel or current node with fallback
        val chosen = _selectedDiscoveredModel.value
        val targetNode = if (chosen != null) {
            allNodes.value.find { it.id == chosen.nodeId } ?: _selectedNode.value ?: allNodes.value.firstOrNull()
        } else {
            _selectedNode.value ?: allNodes.value.firstOrNull()
        }

        if (targetNode == null) {
            // Cannot send message without any configured or discovered nodes
            return
        }
        val activeModelName = chosen?.modelName ?: _selectedModel.value.ifBlank { targetNode.llmDefaultModel }

        val isSearchCommand = rawPrompt.startsWith("/search ", ignoreCase = true) || rawPrompt.startsWith("@web ", ignoreCase = true)
        val shouldWebSearch = _isWebSearchEnabled.value || isSearchCommand
        val cleanQuery = if (isSearchCommand) {
            rawPrompt.replaceFirst(Regex("^/(search|web)\\s+", RegexOption.IGNORE_CASE), "").trim()
        } else rawPrompt

        val userMessage = ChatMessage(
            conversationId = currentConvId,
            nodeId = targetNode.id,
            role = "user",
            content = rawPrompt,
            modelUsed = activeModelName
        )
        _chatInput.value = ""
        _isLlmGenerating.value = true

        llmGenerationJob = viewModelScope.launch {
            try {
                // Ensure conversation exists in DB
                val existingConv = repository.getConversationById(currentConvId)
                if (existingConv == null) {
                    val initialConv = ChatConversation(
                        id = currentConvId,
                        title = rawPrompt.take(35).let { if (rawPrompt.length > 35) "$it..." else it },
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        lastModelUsed = activeModelName
                    )
                    repository.insertConversation(initialConv)
                    _currentConversationTitle.value = initialConv.title
                } else if (_chatMessages.value.none { it.conversationId == currentConvId }) {
                    val title = rawPrompt.take(35).let { if (rawPrompt.length > 35) "$it..." else it }
                    _currentConversationTitle.value = title
                    repository.updateConversationTitle(currentConvId, title)
                }

                repository.insertChatMessage(userMessage)

                var finalPrompt = rawPrompt
                if (shouldWebSearch) {
                    _isSearchingWeb.value = true
                    _lastSearchQuery.value = cleanQuery
                    try {
                        val searchResult = duckDuckGoSearchService.search(cleanQuery)
                        _lastSearchResults.value = searchResult
                        if (searchResult.isSuccess && searchResult.snippets.isNotEmpty()) {
                            val formattedSnippets = searchResult.snippets.mapIndexed { idx, s ->
                                "[Result ${idx + 1}] ${s.title}\nURL: ${s.url}\nSnippet: ${s.snippet}"
                            }.joinToString("\n\n")

                            finalPrompt = """
                                User Question: $rawPrompt
                                
                                [Live DuckDuckGo Web Search Results for "$cleanQuery"]
                                $formattedSnippets
                                
                                Instructions: Incorporate the above fresh DuckDuckGo web search results to provide a comprehensive, accurate answer with sources and links where relevant.
                            """.trimIndent()
                        }
                    } catch (_: Exception) {}
                    _isSearchingWeb.value = false
                }

                val currentHistory = try {
                    _chatMessages.value.filter { it.conversationId == currentConvId }
                } catch (_: Exception) {
                    emptyList()
                }

                val response = llmService.sendChatMessage(
                    node = targetNode,
                    model = activeModelName,
                    prompt = finalPrompt,
                    history = currentHistory
                )

                val assistantMessage = ChatMessage(
                    conversationId = currentConvId,
                    nodeId = targetNode.id,
                    role = "assistant",
                    content = response.text,
                    modelUsed = activeModelName,
                    inferenceStats = response.stats
                )
                repository.insertChatMessage(assistantMessage)
                repository.updateConversationModel(currentConvId, activeModelName)
            } catch (t: Throwable) {
                // Prevent crash, report clean assistant bubble
                val errorBubble = ChatMessage(
                    conversationId = currentConvId,
                    nodeId = targetNode.id,
                    role = "assistant",
                    content = "⚠️ Unable to process request: ${t.localizedMessage ?: "Unknown network error"}",
                    modelUsed = activeModelName,
                    inferenceStats = "Failed"
                )
                try {
                    repository.insertChatMessage(errorBubble)
                } catch (_: Exception) {}
            } finally {
                _isLlmGenerating.value = false
                _isSearchingWeb.value = false
                llmGenerationJob = null
            }
        }
    }

    // Ambient Voice Mode Control Methods
    fun openVoiceMode() {
        _isVoiceModeOpen.value = true
        voiceEngineManager.startConversation(
            onAutoListen = { startVoiceListening() },
            onBarge = {
                // User talked over the assistant: kill playback + generation, listen.
                stopVoiceSpeaking()
                llmService.cancelActiveCall()
                llmGenerationJob?.cancel()
                llmGenerationJob = null
                _isLlmGenerating.value = false
                startVoiceListening()
            }
        )
        startVoiceListening()
    }

    fun closeVoiceMode() {
        _isVoiceModeOpen.value = false
        voiceEngineManager.endConversation()
        stopVoiceSpeaking()
        stopVoiceListening()
    }

    fun isConversationMode(): Boolean = voiceEngineManager.isConversationEnabled()
    fun setConversationMode(enabled: Boolean) = voiceEngineManager.setConversationEnabled(enabled)

    // TTS tuning exposed to the settings sheet
    fun getTtsSpeed(): Float = voiceEngineManager.ttsSpeed
    fun setTtsSpeed(v: Float) { voiceEngineManager.ttsSpeed = v }
    fun getTtsPitch(): Float = voiceEngineManager.ttsPitch
    fun setTtsPitch(v: Float) { voiceEngineManager.ttsPitch = v }
    fun getTtsSpeakerId(): Int = voiceEngineManager.ttsSpeakerId
    fun setTtsSpeakerId(v: Int) { voiceEngineManager.ttsSpeakerId = v }
    fun getBargeThreshold(): Float = voiceEngineManager.bargeThreshold
    fun setBargeThreshold(v: Float) { voiceEngineManager.bargeThreshold = v }
    fun getBargeMultiplier(): Float = voiceEngineManager.bargeMultiplier
    fun setBargeMultiplier(v: Float) { voiceEngineManager.bargeMultiplier = v }
    fun getBargeWindowMs(): Int = voiceEngineManager.bargeWindowMs
    fun setBargeWindowMs(v: Int) { voiceEngineManager.bargeWindowMs = v }
    fun getAutoListenDelayMs(): Int = voiceEngineManager.autoListenDelayMs
    fun setAutoListenDelayMs(v: Int) { voiceEngineManager.autoListenDelayMs = v }
    fun isBargeEnabled(): Boolean = voiceEngineManager.isBargeEnabled()
    fun setBargeEnabled(enabled: Boolean) = voiceEngineManager.setBargeEnabled(enabled)

    fun previewTts() {
        voiceEngineManager.speakText("This is how I will sound with your current settings.")
    }

    fun openVoiceModelsSheet() {
        _isVoiceModelsSheetOpen.value = true
    }

    private val _isTtsSettingsSheetOpen = MutableStateFlow(false)
    val isTtsSettingsSheetOpen: StateFlow<Boolean> = _isTtsSettingsSheetOpen.asStateFlow()
    fun openTtsSettingsSheet() { _isTtsSettingsSheetOpen.value = true }
    fun closeTtsSettingsSheet() { _isTtsSettingsSheetOpen.value = false }

    fun closeVoiceModelsSheet() {
        _isVoiceModelsSheetOpen.value = false
    }

    fun startVoiceListening() {
        voiceEngineManager.startListening { spokenText ->
            if (spokenText.isNotBlank()) {
                handleVoicePrompt(spokenText)
            }
        }
    }

    fun stopVoiceListening() {
        voiceEngineManager.stopListening()
    }

    fun stopVoiceSpeaking() {
        voiceEngineManager.stopSpeaking()
    }

    fun downloadVoiceModel(modelId: String) {
        voiceEngineManager.downloadModel(modelId)
    }

    fun deleteVoiceModel(modelId: String) {
        voiceEngineManager.deleteModel(modelId)
    }

    fun selectVoiceModel(modelId: String) {
        voiceEngineManager.selectModel(modelId)
    }

    fun handleVoicePrompt(spokenText: String) {
        val trimmed = spokenText.trim()
        if (trimmed.isEmpty()) return

        var currentConvId = _currentConversationId.value
        if (currentConvId.isBlank()) {
            val newId = UUID.randomUUID().toString()
            val newConv = ChatConversation(
                id = newId,
                title = "Voice: " + trimmed.take(25),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                lastModelUsed = _selectedDiscoveredModel.value?.modelName ?: _selectedModel.value
            )
            viewModelScope.launch {
                repository.insertConversation(newConv)
            }
            currentConvId = newId
            _currentConversationId.value = currentConvId
        }

        val chosen = _selectedDiscoveredModel.value
        val targetNode = if (chosen != null) {
            allNodes.value.find { it.id == chosen.nodeId } ?: _selectedNode.value
        } else {
            _selectedNode.value
        }
        if (targetNode == null) return
        val activeModelName = chosen?.modelName ?: _selectedModel.value.ifBlank { targetNode.llmDefaultModel }

        val userMessage = ChatMessage(
            conversationId = currentConvId,
            nodeId = targetNode.id,
            role = "user",
            content = trimmed,
            modelUsed = activeModelName
        )

        voiceEngineManager.setThinkingState()
        _isLlmGenerating.value = true

        llmGenerationJob = viewModelScope.launch(Dispatchers.IO) {
            repository.insertChatMessage(userMessage)

            // Overlap LLM generation with TTS: stream tokens and enqueue
            // completed sentences into a LAN Kokoro session as they arrive,
            // so audio starts ~1s after the model's first sentence.
            val lanSession = voiceEngineManager.openLanSpeechSession()
            val gate = if (lanSession != null) {
                com.example.voice.SentenceGate(minWords = 3) { s -> lanSession.enqueue(s) }
            } else null
            val speechCaption = StringBuilder()
            var playbackStarted = false

            if (lanSession != null) {
                Thread {
                    try {
                        val ok = lanSession.join()
                        when {
                            lanSession.isAborted -> { /* barge-in: stay quiet */ }
                            ok -> voiceEngineManager.notifyTurnComplete()
                            else -> voiceEngineManager.speakText(speechCaption.toString())
                        }
                    } catch (_: Throwable) {}
                }.also { it.isDaemon = true; it.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, _ -> }; it.start() }
            }

            val history = _chatMessages.value.filter { it.conversationId == currentConvId }
            val voiceSys = voiceEngineManager.getVoiceSystemPrompt()
            val response = if (lanSession != null) {
                llmService.sendChatMessageStreaming(
                    node = targetNode,
                    model = activeModelName,
                    prompt = trimmed,
                    history = history,
                    systemPrompt = voiceSys
                ) { token ->
                    gate?.feed(token)
                    speechCaption.append(token)
                    if (!playbackStarted) { playbackStarted = true; voiceEngineManager.setSpeakingState() }
                    if (speechCaption.length % 120 < token.length) {
                        voiceEngineManager.setLiveSpeechText(speechCaption.toString())
                    }
                }
            } else {
                llmService.sendChatMessage(
                    node = targetNode,
                    model = activeModelName,
                    prompt = trimmed,
                    history = history,
                    systemPrompt = voiceSys
                )
            }

            if (lanSession != null) {
                gate?.flush()
                lanSession.finish()
                voiceEngineManager.setLiveSpeechText(response.text)
            }

            val assistantMessage = ChatMessage(
                conversationId = currentConvId,
                nodeId = targetNode.id,
                role = "assistant",
                content = response.text,
                modelUsed = activeModelName,
                inferenceStats = response.stats
            )
            repository.insertChatMessage(assistantMessage)
            _isLlmGenerating.value = false
            llmGenerationJob = null

            // Non-LAN paths: speak after full response (system/on-device TTS)
            if (lanSession == null && !response.isError) {
                voiceEngineManager.speakText(response.text)
            }
        }
    }

    fun clearCurrentChat() {
        val convId = _currentConversationId.value
        if (convId.isNotBlank()) {
            viewModelScope.launch {
                repository.clearMessagesForConversation(convId)
            }
        }
    }

    // Hermes Agent Gateway Actions
    fun setHermesInput(text: String) {
        _hermesInput.value = text
    }

    fun checkHermesHealth(targetNode: TailNode? = null) {
        val node = targetNode ?: _selectedNode.value ?: return
        _isCheckingHermesHealth.value = true
        viewModelScope.launch {
            val status = hermesService.checkHealth(node)
            _hermesHealth.value = status
            _isCheckingHermesHealth.value = false
            if (status.isReachable) {
                refreshHermesTools(node)
            }
        }
    }

    fun refreshHermesTools(targetNode: TailNode? = null) {
        val node = targetNode ?: _selectedNode.value ?: return
        viewModelScope.launch {
            val tools = hermesService.getAvailableMcpTools(node)
            _hermesMcpTools.value = tools
        }
    }

    fun setHermesSession(sessionId: String) {
        _hermesSessionId.value = sessionId
        val node = _selectedNode.value ?: return
        viewModelScope.launch {
            repository.getHermesMessages(node.id, sessionId)?.collect { msgs ->
                _hermesMessages.value = msgs
            }
        }
    }

    fun createNewHermesSession() {
        val newSessionId = "session_" + System.currentTimeMillis()
        setHermesSession(newSessionId)
    }

    fun clearHermesSession() {
        val node = _selectedNode.value ?: return
        val currentSession = _hermesSessionId.value
        viewModelScope.launch {
            repository.clearHermesSession(node.id, currentSession)
            _hermesMessages.value = emptyList()
            _hermesLiveThinking.value = null
            _hermesLiveTools.value = emptyList()
        }
    }

    fun updateHermesConfig(
        port: Int,
        apiKey: String,
        dashboardPort: Int,
        baseUrl: String
    ) {
        val node = _selectedNode.value ?: return
        viewModelScope.launch {
            val updated = node.copy(
                hermesPort = port,
                hermesApiKey = apiKey.trim(),
                hermesDashboardPort = dashboardPort,
                hermesBaseUrl = baseUrl.trim()
            )
            repository.updateNode(updated)
            _selectedNode.value = updated
            checkHermesHealth(updated)
        }
    }

    fun sendHermesTask() {
        val node = _selectedNode.value ?: return
        val prompt = _hermesInput.value.trim()
        if (prompt.isEmpty() || _isHermesGenerating.value) return

        val sessionId = _hermesSessionId.value
        val userMsg = HermesMessage(
            nodeId = node.id,
            sessionId = sessionId,
            role = "user",
            content = prompt
        )

        _hermesInput.value = ""
        _isHermesGenerating.value = true
        _hermesLiveThinking.value = null
        _hermesLiveTools.value = emptyList()

        val liveAssistantId = UUID.randomUUID().toString()
        val streamingAssistant = HermesMessage(
            id = liveAssistantId,
            nodeId = node.id,
            sessionId = sessionId,
            role = "assistant",
            content = "",
            isStreaming = true
        )

        viewModelScope.launch {
            repository.insertHermesMessage(userMsg)
            repository.insertHermesMessage(streamingAssistant)

            val currentHistory = _hermesMessages.value

            // Voice: stream agent output into a LAN TTS session sentence-by-sentence
            val voiceOn = voiceEngineManager.canStreamViaLan()
            val lanSession = if (voiceOn) voiceEngineManager.openLanSpeechSession() else null
            val hermesGate = if (lanSession != null) {
                com.example.voice.SentenceGate(minWords = 3) { s -> lanSession.enqueue(s) }
            } else null
            var hermesPlaybackStarted = false
            if (lanSession != null) {
                Thread {
                    try { lanSession.join() } catch (_: Throwable) {}
                }.also { it.isDaemon = true; it.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, _ -> }; it.start() }
            }

            hermesService.streamAgentChat(
                node = node,
                prompt = prompt,
                history = currentHistory,
                onToken = { token ->
                    val cur = _hermesMessages.value.toMutableList()
                    val idx = cur.indexOfFirst { it.id == liveAssistantId }
                    if (idx != -1) {
                        cur[idx] = cur[idx].copy(content = cur[idx].content + token)
                        _hermesMessages.value = cur
                    }
                    if (hermesGate != null) {
                        hermesGate.feed(token)
                        if (!hermesPlaybackStarted) {
                            hermesPlaybackStarted = true
                            voiceEngineManager.setSpeakingState()
                        }
                    }
                },
                onThinking = { thinkToken ->
                    _hermesLiveThinking.value = (_hermesLiveThinking.value ?: "") + thinkToken
                },
                onToolCallDiscovered = { newTool ->
                    _hermesLiveTools.value = _hermesLiveTools.value + newTool
                },
                onToolCallUpdated = { updatedTool ->
                    val tools = _hermesLiveTools.value.toMutableList()
                    val idx = tools.indexOfFirst { it.id == updatedTool.id }
                    if (idx != -1) {
                        tools[idx] = updatedTool
                    } else {
                        tools.add(updatedTool)
                    }
                    _hermesLiveTools.value = tools
                },
                onError = { errorText ->
                    _isHermesGenerating.value = false
                    hermesGate?.flush()
                    lanSession?.finish()
                    val errorAssistant = HermesMessage(
                        id = liveAssistantId,
                        nodeId = node.id,
                        sessionId = sessionId,
                        role = "assistant",
                        content = "⚠️ Hermes Error: $errorText",
                        isStreaming = false
                    )
                    viewModelScope.launch {
                        repository.insertHermesMessage(errorAssistant)
                    }
                },
                onCompleted = { fullContent, thinking, finalTools ->
                    _isHermesGenerating.value = false
                    val toolCallsJson = if (finalTools.isNotEmpty()) {
                        JSONArray().apply {
                            finalTools.forEach { tc ->
                                put(JSONObject().apply {
                                    put("id", tc.id)
                                    put("name", tc.name)
                                    put("arguments", tc.arguments)
                                    put("result", tc.result ?: "")
                                    put("status", tc.status.name)
                                })
                            }
                        }.toString()
                    } else ""

                    val resolvedContent = fullContent.ifBlank {
                        if (finalTools.isNotEmpty()) {
                            "Executed ${finalTools.size} actions on ${node.name}."
                        } else {
                            "Hermes Agent processed request on ${node.name} with no text output."
                        }
                    }

                    val assistantMsg = HermesMessage(
                        id = liveAssistantId,
                        nodeId = node.id,
                        sessionId = sessionId,
                        role = "assistant",
                        content = resolvedContent,
                        thinkingContent = thinking,
                        toolCallsJson = toolCallsJson,
                        isStreaming = false
                    )
                    viewModelScope.launch {
                        repository.insertHermesMessage(assistantMsg)
                    }
                }
            )
        }
    }

    // Remote Desktop actions
    fun toggleDesktopConnection() {
        _desktopConnected.value = !_desktopConnected.value
    }

    fun setDesktopMouseMode(mode: String) {
        _desktopMouseMode.value = mode
    }

    // Node management actions
    fun openAddNodeDialog() {
        _isAddNodeDialogOpen.value = true
    }

    fun closeAddNodeDialog() {
        _isAddNodeDialogOpen.value = false
    }

    fun openEditNodeDialog() {
        _isEditNodeDialogOpen.value = true
    }

    fun closeEditNodeDialog() {
        _isEditNodeDialogOpen.value = false
    }

    fun addNode(node: TailNode) {
        viewModelScope.launch {
            val newId = repository.insertNode(node)
            val insertedNode = node.copy(id = newId)
            _selectedNode.value = insertedNode
            closeAddNodeDialog()
            refreshAllDiscoveredModels()
        }
    }

    fun updateSelectedNode(updated: TailNode) {
        viewModelScope.launch {
            repository.updateNode(updated)
            _selectedNode.value = updated
            closeEditNodeDialog()
            refreshAllDiscoveredModels()
        }
    }

    fun updateCurrentNode(updated: TailNode) = updateSelectedNode(updated)

    fun deleteCurrentNode() {
        _selectedNode.value?.let { deleteNode(it) }
    }

    fun saveNode(node: TailNode) = addNode(node)

    fun saveNode(
        name: String,
        ip: String,
        os: String,
        sshPort: Int,
        sshUser: String,
        llmPort: Int,
        llmType: String,
        llmBaseUrl: String,
        llmApiKey: String,
        deskPort: Int,
        deskType: String,
        deskUrl: String,
        tags: String
    ) {
        val node = TailNode(
            name = name,
            tailscaleIp = ip,
            osType = os,
            sshPort = sshPort,
            sshUser = sshUser,
            llmPort = llmPort,
            llmType = llmType,
            llmBaseUrl = llmBaseUrl,
            llmApiKey = llmApiKey,
            remoteDesktopPort = deskPort,
            remoteDesktopType = deskType,
            remoteDesktopUrl = deskUrl,
            tags = tags
        )
        addNode(node)
    }

    fun sendHermesMessage(customPrompt: String? = null) {
        if (customPrompt != null) {
            _hermesInput.value = customPrompt
        }
        sendHermesTask()
    }

    fun deleteNode(node: TailNode) {
        viewModelScope.launch {
            repository.deleteNode(node)
            val currentNodes = allNodes.value.filter { it.id != node.id }
            if (currentNodes.isNotEmpty()) {
                selectNode(currentNodes.first())
            } else {
                _selectedNode.value = null
            }
            refreshAllDiscoveredModels()
        }
    }

    fun toggleFavorite(nodeId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(nodeId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngineManager.cleanup()
    }
}

class TailNodeViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TailNodeViewModel::class.java)) {
            val db = AppDatabase.getDatabase(application)
            val repo = TailNodeRepository(
                tailNodeDao = db.tailNodeDao(),
                chatMessageDao = db.chatMessageDao(),
                fileTransferDao = db.fileTransferDao(),
                terminalHistoryDao = db.terminalHistoryDao(),
                hermesMessageDao = db.hermesMessageDao(),
                chatConversationDao = db.chatConversationDao()
            )
            return TailNodeViewModel(application, repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class DownloadedFileInfo(
    val fileName: String,
    val savedPath: String,
    val savedUri: String?,
    val bytes: Long
)
