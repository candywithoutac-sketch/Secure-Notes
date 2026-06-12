package com.example.securenotes // Change this to your package name!

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity // Needed for Biometrics
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*

// We use AppCompatActivity instead of ComponentActivity to support BiometricPrompt
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // App starts locked.
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var isAuthenticated by remember { mutableStateOf(false) }

                    if (!isAuthenticated) {
                        SecurityLockScreen(onAuthenticated = { isAuthenticated = true })
                    } else {
                        SecureNoteScreen()
                    }
                }
            }
        }
    }

    @Composable
    fun SecurityLockScreen(onAuthenticated: () -> Unit) {
        val context = LocalContext.current
        val executor = ContextCompat.getMainExecutor(context)

        LaunchedEffect(Unit) {
            val biometricPrompt = BiometricPrompt(this@MainActivity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onAuthenticated()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Toast.makeText(context, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Secure Notes Login")
                .setSubtitle("Use your fingerprint or face to unlock your notes")
                .setNegativeButtonText("Cancel")
                .build()

            biometricPrompt.authenticate(promptInfo)
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Waiting for authentication...", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun SecureNoteScreen() {
    val context = LocalContext.current
    var paths by remember { mutableStateOf(listOf<Path>()) }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    
    // ML Kit Ink Builder for Handwriting recognition
    var inkBuilder by remember { mutableStateOf(Ink.builder()) }
    var strokeBuilder: Ink.Stroke.Builder? by remember { mutableStateOf(null) }
    
    var recognizedText by remember { mutableStateOf("Draw below, then press 'Recognize Text'") }
    var isModelDownloaded by remember { mutableStateOf(false) }

    // Initialize ML Kit Model
    LaunchedEffect(Unit) {
        val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")!!
        val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
        val remoteModelManager = RemoteModelManager.getInstance()

        remoteModelManager.download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener {
                isModelDownloaded = true
                Toast.makeText(context, "Handwriting Engine Ready!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                recognizedText = "Failed to download handwriting model."
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Toolbar
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                enabled = isModelDownloaded,
                onClick = {
                    if (paths.isEmpty()) {
                        recognizedText = "Nothing to recognize!"
                        return@Button
                    }
                    
                    recognizedText = "Thinking..."
                    val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")!!
                    val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
                    val recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
                    
                    recognizer.recognize(inkBuilder.build())
                        .addOnSuccessListener { result ->
                            recognizedText = if (result.candidates.isNotEmpty()) {
                                result.candidates[0].text
                            } else {
                                "Could not read text."
                            }
                        }
                        .addOnFailureListener { e -> recognizedText = "Error: ${e.message}" }
                }
            ) { Text("Recognize Text") }

            Button(onClick = { 
                paths = emptyList()
                inkBuilder = Ink.builder()
                recognizedText = "Canvas Cleared"
            }) { Text("Clear") }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Result: $recognizedText", style = MaterialTheme.typography.titleMedium, color = Color.Blue)

        Spacer(modifier = Modifier.height(16.dp))

        // Drawing Canvas with Palm Rejection
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF9C4)) // Note-like yellow background
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val downEvent = awaitPointerEvent()
                        val pointer = downEvent.changes.first()
                        
                        // PALM REJECTION LOGIC: 
                        // Only draw if the user is using a Stylus. 
                        // To test on an emulator without a stylus, change PointerType.Stylus to PointerType.Touch
                        if (pointer.type == PointerType.Stylus || pointer.type == PointerType.Touch) {
                            pointer.consume()
                            val newPath = Path().apply { moveTo(pointer.position.x, pointer.position.y) }
                            currentPath = newPath
                            
                            // Start ML Kit Stroke
                            strokeBuilder = Ink.Stroke.builder()
                            strokeBuilder?.addPoint(Ink.Point.create(pointer.position.x, pointer.position.y, System.currentTimeMillis()))

                            var pointerEvent: PointerEvent
                            do {
                                pointerEvent = awaitPointerEvent()
                                val movePointer = pointerEvent.changes.first()
                                
                                if (movePointer.type == PointerType.Stylus || movePointer.type == PointerType.Touch) {
                                    movePointer.consume()
                                    currentPath?.lineTo(movePointer.position.x, movePointer.position.y)
                                    strokeBuilder?.addPoint(Ink.Point.create(movePointer.position.x, movePointer.position.y, System.currentTimeMillis()))
                                    
                                    // Trigger recomposition to draw
                                    val tempPath = currentPath
                                    currentPath = null
                                    currentPath = tempPath
                                }
                            } while (pointerEvent.changes.any { it.pressed })

                            // Finish stroke
                            if (currentPath != null) {
                                paths = paths + currentPath!!
                                currentPath = null
                            }
                            strokeBuilder?.build()?.let { inkBuilder.addStroke(it) }
                        }
                    }
                }
        ) {
            // Draw all completed paths
            paths.forEach { path ->
                drawPath(
                    path = path,
                    color = Color.Black,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            // Draw current path being drawn
            currentPath?.let { path ->
                drawPath(
                    path = path,
                    color = Color.Black,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
