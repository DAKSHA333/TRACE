package com.trace.workspace

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
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

private enum class Screen { Home, Scanner, Confirm, Ask }

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

    fun goBack() {
        screen = when (screen) {
            Screen.Home -> Screen.Home
            Screen.Scanner -> Screen.Home
            Screen.Confirm -> Screen.Scanner
            Screen.Ask -> Screen.Home
        }
    }

    BackHandler(enabled = true) {
        goBack()
    }

    if (!state.onboarded) {
        PrivacyOnboarding(onContinue = viewModel::completeOnboarding)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.trace_logo),
                            contentDescription = "TRACE logo",
                            modifier = Modifier.size(48.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("TRACE", fontWeight = FontWeight.Black)
                            Text("Workspace memory", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                navigationIcon = {
                    if (screen != Screen.Home) {
                        IconButton(onClick = { goBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
                    onAsk = {
                        viewModel.prepareAsk()
                        screen = Screen.Ask
                    },
                    onClear = viewModel::clearData,
                )
                Screen.Scanner -> ScannerScreen(
                    onImageCaptured = {
                        viewModel.analyzeCapturedImage(it)
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
                        viewModel.prepareAsk()
                        screen = Screen.Ask
                    },
                    onRetake = { screen = Screen.Scanner },
                )
                Screen.Ask -> AskTraceScreen(
                    query = state.query,
                    answer = state.answer,
                    onQueryChanged = viewModel::updateQuery,
                    onAsk = viewModel::askTrace,
                    onNewScan = { screen = Screen.Scanner },
                )
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
        Image(
            painter = painterResource(R.drawable.trace_logo),
            contentDescription = "TRACE logo",
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(20.dp))
        Text("Your physical workspace, remembered.", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Text("TRACE saves workspace memories on this phone and answers only from what it has observed.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onContinue, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}

@Composable
private fun HomeScreen(
    message: String?,
    onScan: () -> Unit,
    onAsk: () -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Remember your desk", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("Scan your workspace once, then ask where important objects were last seen.")
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        }
        item { ActionCard("Scan workspace", "Capture your desk and save the objects TRACE should remember.", Icons.Default.CameraAlt, onScan) }
        item { ActionCard("Ask TRACE", "Search your saved workspace memory with a question.", Icons.Default.Search, onAsk) }
        item {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3EF)),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Use simple names like laptop, sticky note, charger, calculator.")
                }
            }
        }
        item {
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clear saved memory")
            }
        }
    }
}

@Composable
private fun ActionCard(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFEAF3EF), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ScannerScreen(onImageCaptured: (Uri) -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Scan workspace", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Text("Place the phone above your desk and capture one clear frame.")
        if (hasPermission) {
            CameraCapture(onImageCaptured = onImageCaptured, modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFEAE6DA)), contentAlignment = Alignment.Center) {
                Text("Camera permission is needed for real workspace scans.")
            }
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Allow camera")
            }
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
            shape = RoundedCornerShape(8.dp),
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
            Text("Objects remembered", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text("Keep only real objects from the photo. Rename anything TRACE should remember.")
        }
        item { EvidenceImage(imagePath, 210) }
        itemsIndexed(objects) { index, item ->
            var name by remember(item.name) { mutableStateOf(item.name) }
            Card(shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            onRename(index, it.trim())
                        },
                        label = { Text("Object name") },
                        singleLine = true,
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
                OutlinedTextField(value = manualName, onValueChange = { manualName = it }, label = { Text("Add missed object") }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    onAdd(manualName.trim())
                    manualName = ""
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRetake, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Text("Retake") }
                Button(onClick = onSave, enabled = objects.isNotEmpty(), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                    Text("Remember")
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
    onNewScan: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Ask TRACE", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text("Ask where an object was last observed in your saved workspace.")
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                label = { Text("Example: Where is my laptop?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(onClick = onAsk, enabled = query.isNotBlank(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Search memory")
            }
        }
        item {
            answer?.let { AnswerCard(it) } ?: EmptyAnswerCard()
        }
        item {
            OutlinedButton(onClick = onNewScan, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan another workspace")
            }
        }
    }
}

@Composable
private fun EmptyAnswerCard() {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3EF)),
    ) {
        Text(
            "Your answer will appear here with the previous workspace image as evidence.",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun AnswerCard(answer: com.trace.workspace.domain.TraceAnswer) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(answer.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(answer.body)
            EvidenceImage(answer.evidenceImagePath, 220)
        }
    }
}

@Composable
private fun EvidenceImage(path: String?, height: Int = 180) {
    if (path == null) return
    Image(
        painter = rememberAsyncImagePainter(path),
        contentDescription = "Saved workspace image",
        modifier = Modifier.fillMaxWidth().height(height.dp).background(Color(0xFFEAE6DA), RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop,
    )
}
