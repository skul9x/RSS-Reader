package com.skul9x.rssreader.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.Close
import androidx.activity.compose.BackHandler
import com.skul9x.rssreader.data.network.ArticleContentFetcher
import com.skul9x.rssreader.data.network.GeminiApiClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HtmlAnalyzerScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    
    var urlInput by remember { mutableStateOf("") }
    var resultTitle by remember { mutableStateOf("") }
    var resultContent by remember { mutableStateOf("") }
    var rawHtml by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisStatus by remember { mutableStateOf("") }
    
    // FIX: Debounce tracking to prevent double submission
    var lastAnalyzeClickTime by remember { mutableLongStateOf(0L) }

    val contentFetcher = remember { ArticleContentFetcher() }
    var candidates by remember { mutableStateOf<List<ArticleContentFetcher.ContentCandidate>>(emptyList()) }
    var previewCandidate by remember { mutableStateOf<ArticleContentFetcher.ContentCandidate?>(null) }
    
    // AI Suggest states
    val geminiClient = remember { GeminiApiClient(context) }
    var aiSuggestResult by remember { mutableStateOf("") }
    var aiSuggestModel by remember { mutableStateOf("") }
    var isAiSuggesting by remember { mutableStateOf(false) }
    var aiSuggestError by remember { mutableStateOf("") }
    val customSelectorManager = remember { com.skul9x.rssreader.data.local.CustomSelectorManager.getInstance(context) }
    
    // FIX: Track selector changes to trigger recomposition in Custom Selector tab
    var customSelectorRefreshKey by remember { mutableIntStateOf(0) }
    
    // FIX: Cleanup resources when Composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            contentFetcher.cleanup()
        }
    }
    
    // Configure content fetcher with the manager
    LaunchedEffect(Unit) {
        contentFetcher.setCustomSelectorManager(customSelectorManager)
    }
    
    // FIX: SnackbarHostState for undo functionality
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("HTML Analyzer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Input Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Article URL",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = {
                            val clipText = clipboardManager.getText()?.toString()
                            if (!clipText.isNullOrBlank()) {
                                urlInput = clipText
                                Toast.makeText(context, "Pasted URL", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, "Paste")
                        }
                    }
                    
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter article URL here...") },
                        maxLines = 3
                    )
                    
                    // Show current saved selector if any
                    var selectorRefreshKey by remember { mutableIntStateOf(0) }
                    val currentSelector = remember(urlInput, selectorRefreshKey) { customSelectorManager.getSelectorForUrl(urlInput) }
                    if (currentSelector != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.BugReport, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Using: $currentSelector",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = { 
                                        // FIX: Store selector before deleting for undo
                                        val deletedSelector = currentSelector
                                        val domain = customSelectorManager.getDomain(urlInput)
                                        
                                        customSelectorManager.removeSelector(urlInput)
                                        selectorRefreshKey++ // Trigger recomposition
                                        
                                        // Show Snackbar with Undo option
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Đã xóa selector cho $domain",
                                                actionLabel = "Hoàn tác",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                // Undo: restore the deleted selector
                                                customSelectorManager.addSelector(urlInput, deletedSelector)
                                                selectorRefreshKey++
                                                Toast.makeText(context, "Đã khôi phục selector", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Xóa", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            // FIX: Debounce to prevent double submission
                            val now = System.currentTimeMillis()
                            if (now - lastAnalyzeClickTime < 1000) return@Button
                            lastAnalyzeClickTime = now
                            
                            if (urlInput.isBlank()) {
                                Toast.makeText(context, "Please enter URL", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            // FIX: Capture current URL value to prevent closure capturing mutable state
                            val targetUrl = urlInput
                            
                            isAnalyzing = true
                            analysisStatus = "Đang tải... (0s)"
                            val startTime = System.currentTimeMillis()
                            resultTitle = ""
                            resultContent = ""
                            candidates = emptyList()
                            
                                scope.launch {
                                    try {
                                        // FIX: Timeout feedback - update status periodically
                                        val timeoutJob = scope.launch {
                                            var elapsed = 0
                                            while (true) {
                                                kotlinx.coroutines.delay(1000)
                                                elapsed++
                                                analysisStatus = "Đang tải... (${elapsed}s)"
                                                if (elapsed >= 30) {
                                                    analysisStatus = "Đang tải... (${elapsed}s) - Có thể bị timeout"
                                                }
                                            }
                                        }
                                        
                                        try {
                                            // FIX: Use Dispatchers.IO for network operations
                                            val (result, html) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                val r = contentFetcher.fetchArticleWithTitle(targetUrl)
                                                val h = contentFetcher.fetchRawHtml(targetUrl)
                                                Pair(r, h)
                                            }
                                            
                                            timeoutJob.cancel()
                                    
                                    rawHtml = html ?: "Failed to fetch HTML"
                                    
                                    // Scan for candidates from raw HTML
                                    if (html != null) {
                                        candidates = contentFetcher.scanForPotentialContainers(html)
                                    }
                                    
                                    if (result != null) {
                                        resultTitle = result.title
                                        resultContent = result.content
                                        analysisStatus = "Success! Content extracted."
                                    } else {
                                        analysisStatus = "Extraction Failed. Try the 'Smart Select' tab."
                                        resultTitle = "N/A"
                                        resultContent = "Fetcher returned null."
                                    }
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            timeoutJob.cancel()
                                            // FIX: Re-throw CancellationException to respect structured concurrency
                                            throw e
                                        } catch (e: java.io.IOException) {
                                            timeoutJob.cancel()
                                            // FIX: Handle network errors specifically
                                            val elapsed = (System.currentTimeMillis() - startTime) / 1000
                                            analysisStatus = "Network Error (${elapsed}s): ${e.message ?: "Connection failed"}"
                                            rawHtml = ""
                                        } catch (e: Exception) {
                                            timeoutJob.cancel()
                                            // FIX: Don't expose stacktrace in production
                                            val elapsed = (System.currentTimeMillis() - startTime) / 1000
                                            analysisStatus = "Error (${elapsed}s): ${e.message ?: "Unknown error"}"
                                            rawHtml = ""
                                        } finally {
                                            isAnalyzing = false
                                        }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } finally {
                                        isAnalyzing = false
                                    }
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isAnalyzing
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isAnalyzing) "Fetching..." else "Analyze URL")
                    }
                    
                    if (analysisStatus.isNotEmpty()) {
                        Text(
                            text = analysisStatus,
                            color = if (analysisStatus.startsWith("Success")) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // Result Section
            if (resultTitle.isNotEmpty() || resultContent.isNotEmpty() || rawHtml.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Analysis Result",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        var selectedTab by remember { mutableIntStateOf(0) }
                        
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Extracted") })
                            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Custom Selector") })
                            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Raw HTML") })
                            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("AI Suggest") })
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        when (selectedTab) {
                            0 -> {
                                Text("Title:", fontWeight = FontWeight.Bold)
                                Text(resultTitle.ifEmpty { "(No title found)" })
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text("Extracted Content (${resultContent.length} chars):", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                OutlinedTextField(
                                    value = resultContent.ifEmpty { "(No content extracted)" },
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp),
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                            }
                            1 -> {
                                // Custom Selector Tab
                                Text("Nhập Custom Selector:", fontWeight = FontWeight.Bold)
                                Text(
                                    "Nhập CSS selector để lấy content (ví dụ: .article-content, #main-body)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Custom selector input state
                                var customSelectorInput by remember { mutableStateOf("") }
                                
                                // Text field with paste button
                                OutlinedTextField(
                                    value = customSelectorInput,
                                    onValueChange = { customSelectorInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Nhập selector tại đây...") },
                                    singleLine = true,
                                    trailingIcon = {
                                        Row {
                                            // Paste button
                                            IconButton(onClick = {
                                                val clipText = clipboardManager.getText()?.toString()
                                                if (!clipText.isNullOrBlank()) {
                                                    customSelectorInput = clipText.trim()
                                                    Toast.makeText(context, "Đã paste selector", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Clipboard trống", Toast.LENGTH_SHORT).show()
                                                }
                                            }) {
                                                Icon(Icons.Default.ContentPaste, "Paste")
                                            }
                                            // Clear button
                                            if (customSelectorInput.isNotEmpty()) {
                                                IconButton(onClick = { customSelectorInput = "" }) {
                                                    Icon(Icons.Default.Close, "Clear")
                                                }
                                            }
                                        }
                                    }
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Save button
                                val currentUrl = urlInput
                                Button(
                                    onClick = {
                                        if (customSelectorInput.isBlank()) {
                                            Toast.makeText(context, "Vui lòng nhập selector", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (currentUrl.isBlank()) {
                                            Toast.makeText(context, "Vui lòng nhập URL trước", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        
                                        customSelectorManager.addSelector(currentUrl, customSelectorInput.trim())
                                        customSelectorRefreshKey++ // FIX: Trigger recomposition
                                        Toast.makeText(
                                            context,
                                            "Đã lưu: ${customSelectorInput.trim()} cho ${customSelectorManager.getDomain(currentUrl)}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        analysisStatus = "Selector đã lưu. Tap Analyze để verify."
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = customSelectorInput.isNotBlank() && urlInput.isNotBlank()
                                ) {
                                    Icon(Icons.Default.BugReport, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Lưu Selector")
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // FIX: Show current saved selector for this domain with refresh key
                                val savedSelector = remember(urlInput, customSelectorRefreshKey) { 
                                    customSelectorManager.getSelectorForUrl(urlInput) 
                                }
                                if (savedSelector != null) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                "Selector đang dùng cho domain này:",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                savedSelector,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                } else if (urlInput.isNotBlank()) {
                                    Text(
                                        "Chưa có selector nào được lưu cho domain này.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            2 -> {
                                Text("Raw HTML Preview (First 10KB):", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // FIX: Cache truncated HTML to avoid recalculation on every recomposition
                                val rawHtmlPreview = remember(rawHtml) { rawHtml.take(10000) }
                                
                                OutlinedTextField(
                                    value = rawHtmlPreview,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 250.dp, max = 400.dp),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    placeholder = { Text("Raw HTML will appear here") }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Total Length: ${rawHtml.length} bytes",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Copy button with prompt
                                Button(
                                    onClick = {
                                        if (rawHtml.isBlank()) {
                                            Toast.makeText(context, "Chưa có HTML để copy", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        
                                        // Use cleaned HTML to reduce size by ~70-80%
                                        val cleanedHtml = contentFetcher.cleanHtmlForAi(rawHtml)
                                        val promptWithHtml = """Bạn là chuyên gia phân tích HTML. Nhiệm vụ của bạn là phân tích HTML sau và tìm CSS class hoặc selector tốt nhất để lấy nội dung chính của bài viết (article content).
YÊU CẦU BẮT BUỘC:
1. Chỉ trả về ĐÚNG 1 CSS selector duy nhất (ví dụ: .article-content, #main-body, div.post-body, article.content).
2. TUYỆT ĐỐI KHÔNG trả về giải thích, chỉ trả về selector duy nhất.
3. Ưu tiên class chứa nội dung bài viết chính, bỏ qua sidebar, menu, header, footer, quảng cáo, comments.
4. Selector phải tồn tại trong HTML được cung cấp.
5. Nếu không tìm được class phù hợp, chỉ trả về: NOT_FOUND
Nội dung đoạn html là:
$cleanedHtml"""
                                        
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(promptWithHtml))
                                        Toast.makeText(context, "Đã copy HTML kèm prompt (${promptWithHtml.length} chars)", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = rawHtml.isNotBlank()
                                ) {
                                    Icon(Icons.Default.ContentCopy, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Copy HTML + Prompt",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Dùng khi AI Suggest hết quota - paste vào Grok, ChatGPT...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            3 -> {
                                // AI Suggest Tab
                                Text("AI Content Class Suggestion", fontWeight = FontWeight.Bold)
                                Text(
                                    "Sử dụng Gemini AI để phân tích HTML và đề xuất CSS selector tốt nhất.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Ask AI Button
                                Button(
                                    onClick = {
                                        if (rawHtml.isBlank()) {
                                            Toast.makeText(context, "Vui lòng Analyze URL trước", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        
                                        isAiSuggesting = true
                                        aiSuggestResult = ""
                                        aiSuggestError = ""
                                        aiSuggestModel = ""
                                        
                                        // Use cleaned HTML to reduce token usage by ~70-80%
                                        val htmlToAnalyze = contentFetcher.cleanHtmlForAi(rawHtml)
                                        val currentUrlForSave = urlInput
                                        
                                        scope.launch {
                                            try {
                                                val result = geminiClient.suggestContentClass(htmlToAnalyze)
                                                
                                                when (result) {
                                                    is GeminiApiClient.SuggestClassResult.Success -> {
                                                        aiSuggestResult = result.selector
                                                        aiSuggestModel = result.model
                                                        aiSuggestError = ""
                                                    }
                                                    else -> {
                                                        aiSuggestResult = ""
                                                        aiSuggestError = result.getErrorMessage()
                                                    }
                                                }
                                            } catch (e: kotlinx.coroutines.CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                aiSuggestError = "Lỗi: ${e.message ?: "Unknown error"}"
                                            } finally {
                                                isAiSuggesting = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isAiSuggesting && rawHtml.isNotBlank()
                                ) {
                                    if (isAiSuggesting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Đang hỏi AI...")
                                    } else {
                                        Icon(Icons.Default.Search, null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Hỏi AI đề xuất selector")
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Result Display
                                if (aiSuggestResult.isNotBlank()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (aiSuggestResult == "NOT_FOUND") 
                                                MaterialTheme.colorScheme.errorContainer 
                                            else MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                if (aiSuggestResult == "NOT_FOUND") "Không tìm thấy" else "AI đề xuất:",
                                                fontWeight = FontWeight.Bold,
                                                color = if (aiSuggestResult == "NOT_FOUND")
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                else MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            if (aiSuggestResult != "NOT_FOUND") {
                                                Text(
                                                    aiSuggestResult,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                
                                                if (aiSuggestModel.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        "Model: ${aiSuggestModel.substringAfterLast("/")}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                    )
                                                }
                                                
                                                Spacer(modifier = Modifier.height(12.dp))
                                                
                                                // Button to save the suggested selector
                                                val currentUrl = urlInput
                                                FilledTonalButton(
                                                    onClick = {
                                                        customSelectorManager.addSelector(currentUrl, aiSuggestResult)
                                                        Toast.makeText(
                                                            context,
                                                            "Đã lưu: $aiSuggestResult cho ${customSelectorManager.getDomain(currentUrl)}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        analysisStatus = "Selector từ AI đã lưu. Tap Analyze để verify."
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Lưu selector này")
                                                }
                                            } else {
                                                Text(
                                                    "AI không thể tìm được class phù hợp trong HTML này. Hãy thử nhập selector thủ công ở tab Custom Selector hoặc copy HTML để hỏi AI khác.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                // Error Display
                                if (aiSuggestError.isNotBlank()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                "Lỗi",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                aiSuggestError,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                                
                                // Info text when no result yet
                                if (aiSuggestResult.isBlank() && aiSuggestError.isBlank() && !isAiSuggesting) {
                                    if (rawHtml.isBlank()) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Text(
                                                "Vui lòng nhập URL và click \"Analyze URL\" trước khi sử dụng tính năng này.",
                                                modifier = Modifier.padding(16.dp),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    } else {
                                        Text(
                                            "HTML đã sẵn sàng (${rawHtml.length} bytes). Click button ở trên để hỏi AI.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Fullscreen Preview Dialog
    previewCandidate?.let { candidate ->
        FullScreenPreview(
            candidate = candidate,
            onDismiss = { previewCandidate = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenPreview(
    candidate: ArticleContentFetcher.ContentCandidate,
    onDismiss: () -> Unit
) {
    // Full screen modal using a Surface overlay
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // FIX: Handle system back button to dismiss preview instead of navigating away
        BackHandler(onBack = onDismiss)
        
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Content Preview") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Selector:", fontWeight = FontWeight.Bold)
                        Text(candidate.selector, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Length: ${candidate.textLength} characters", style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Full Content:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = candidate.fullText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

