package com.trace.workspace

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.trace.workspace.ui.TraceViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: TraceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TraceTheme {
                TraceApp(viewModel)
            }
        }
    }
}

private enum class Screen { Home, Scanner, Confirm, Ask, Compare, Resume, Settings }

@Composable
private fun TraceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF176B5D),
            secondary = Color(0xFF8B6F21),
            background = Color(0xFFFAF9F6),
            surface = Color.White,
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TraceApp(viewModel: TraceViewModel) {
    val state by viewModel.uiState.collectAsState()
    var screen by remember { mutableStateOf(Screen.Home) }

    if (!state.onboarded) {
        PrivacyOnboarding(onContinue = viewModel::completeOnboarding)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TRACE", fontWeight = FontWeight.Black)
                        Text("Physical workspace memory", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (screen) {
                Screen.Home -> HomeScreen(
                    message = state.message,
                    onScan = { screen = Screen.Scanner },
                    onAsk = { screen = Screen.Ask },
                    onCompare = {
                        viewModel.compareScans()
                        screen = Screen.Compare
                    },
                    onResume = {
                        viewModel.resumeWorkspace()
                        screen = Screen.Resume
                    },
                    onSettings = { screen = Screen.Settings },
                )
                Screen.Scanner -> ScannerScreen(
                    onImageCaptured = {
                        viewModel.analyzeCapturedImage(it)
                        screen = Screen.Confirm
                    },
                    onUseDemo = {
                        viewModel.useDemoScan()
                        screen = Screen.Confirm
                    },
                )
                Screen.Confirm -> ConfirmScreen(
                    imagePath = state.pendingImagePath,
                    objects = state.confirmedObjects,
                    onRename = viewModel::renameObject,
                    onRemove = viewModel::removeObject,
                    onAdd = viewModel::addManualObject,
                    onSave = {
                        viewModel.saveCurrentScan()
                        screen = Screen.Home
                    },
                    onRetake = { screen = Screen.Scanner },
                )
                Screen.Ask -> AskTraceScreen(
                    query = state.query,
                    answer = state.answer,
                    onQueryChanged = viewModel::updateQuery,
                    onAsk = viewModel::askTrace,
                    onBack = { screen = Screen.Home },
                )
                Screen.Compare -> AnswerScreen("Scan comparison", state.answer, onBack = { screen = Screen.Home })
                Screen.Resume -> AnswerScreen("Resume Workspace", state.answer, onBack = { screen = Screen.Home })
                Screen.Settings -> SettingsScreen(onClear = viewModel::clearData, onBack = { screen = Screen.Home })
            }
        }
    }
}

@Composable
private fun PrivacyOnboarding(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF9F6))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("TRACE", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
        Text("Your physical workspace, remembered.", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Text("TRACE stores scans and memories locally on this phone. The MVP only reports where objects were last observed and never claims live location certainty.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onContinue) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start")
        }
    }
}

@Composable
private fun HomeScreen(
    message: String?,
    onScan: () -> Unit,
    onAsk: () -> Unit,
    onCompare: () -> Unit,
    onResume: () -> Unit,
    onSettings: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("IoT Prototype", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Hackathon demo workspace: scan, remember, search, compare, resume.")
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        }
        item { ActionCard("Workspace scanner", "Capture a timestamped desk memory.", Icons.Default.CameraAlt, onScan) }
        item { ActionCard("Ask TRACE", "Find the last observed location of an object.", Icons.Default.QuestionAnswer, onAsk) }
        item { ActionCard("Compare scans", "Detect added, removed and retained objects.", Icons.Default.History, onCompare) }
        item { ActionCard("Resume workspace", "Reconstruct the latest project context.", Icons.Default.PlayArrow, onResume) }
        item {
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Settings and data deletion")
            }
        }
    }
}

@Composable
private fun ActionCard(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(body)
            }
        }
    }
}

@Composable
private fun ScannerScreen(onImageCaptured: (Uri) -> Unit, onUseDemo: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hasPermission) {
            CameraCapture(onImageCaptured = onImageCaptured, modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFEAE6DA)), contentAlignment = Alignment.Center) {
                Text("Camera permission is needed for real workspace scans.")
            }
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Allow camera")
            }
        }
        OutlinedButton(onClick = onUseDemo, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Use demo scan")
        }
    }
}

@Composable
private fun CameraCapture(onImageCaptured: (Uri) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )
        Button(
            onClick = {
                val dir = File(context.filesDir, "scans").apply { mkdirs() }
                val file = File(dir, "trace-${System.currentTimeMillis()}.jpg")
                val output = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture.takePicture(
                    output,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            onImageCaptured(Uri.fromFile(file))
                        }

                        override fun onError(exception: ImageCaptureException) = Unit
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Capture workspace")
        }
    }
}

@Composable
private fun ConfirmScreen(
    imagePath: String?,
    objects: List<com.trace.workspace.domain.ConfirmedWorkspaceObject>,
    onRename: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: (String) -> Unit,
    onSave: () -> Unit,
    onRetake: () -> Unit,
) {
    var manualName by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Confirm detections", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Rename generic labels into personal workspace objects before saving.")
        }
        item { EvidenceImage(imagePath) }
        itemsIndexed(objects) { index, item ->
            var name by remember(item.name) { mutableStateOf(item.name) }
            Card(shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            onRename(index, it)
                        },
                        label = { Text(item.label) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = manualName, onValueChange = { manualName = it }, label = { Text("Add missed object") }, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    onAdd(manualName)
                    manualName = ""
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text("Retake") }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun AskTraceScreen(
    query: String,
    answer: com.trace.workspace.domain.TraceAnswer?,
    onQueryChanged: (String) -> Unit,
    onAsk: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ask TRACE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = { Text("Where is my blue notebook?") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onAsk, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Search memory")
        }
        answer?.let { AnswerCard(it) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun AnswerScreen(title: String, answer: com.trace.workspace.domain.TraceAnswer?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        AnswerCard(answer ?: com.trace.workspace.domain.TraceAnswer("No result yet", "Run the action again after saving scans."))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun AnswerCard(answer: com.trace.workspace.domain.TraceAnswer) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(answer.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(answer.body)
            EvidenceImage(answer.evidenceImagePath)
        }
    }
}

@Composable
private fun EvidenceImage(path: String?) {
    if (path == null) return
    Image(
        painter = rememberAsyncImagePainter(path),
        contentDescription = "Supporting workspace image",
        modifier = Modifier.fillMaxWidth().height(180.dp).background(Color(0xFFEAE6DA)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun SettingsScreen(onClear: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("TRACE keeps hackathon MVP data locally on this phone.")
        Button(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Delete local memory")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
