package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.example.audio.AudioEngine
import com.example.data.AppDatabase
import com.example.data.SettingsManager
import com.example.ui.screens.FileAnalysisScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.LiveAnalysisScreen
import com.example.ui.screens.RecordingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.GalKolTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var audioEngine: AudioEngine
    private lateinit var settingsManager: SettingsManager
    private lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        audioEngine = AudioEngine(this)
        settingsManager = SettingsManager(this)
        appDatabase = AppDatabase.getInstance(this)

        setContent {
            val settings by settingsManager.settings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = settings.isDarkMode ?: systemDark

            // Sync settings to AudioEngine
            LaunchedEffect(settings) {
                audioEngine.sampleRate = 44100
                audioEngine.fftSize = settings.fftSize
                audioEngine.windowType = settings.windowType
                audioEngine.calibrationOffsetDb = if (settings.isCalibrated) settings.calibrationOffsetDb else null
            }

            GalKolTheme(darkTheme = isDark) {
                // Enforce RTL layout for entire Hebrew UI
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainAppScaffold(
                        audioEngine = audioEngine,
                        database = appDatabase,
                        settingsManager = settingsManager
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        audioEngine.stopCapture()
    }
}

@Composable
fun MainAppScaffold(
    audioEngine: AudioEngine,
    database: AppDatabase,
    settingsManager: SettingsManager
) {
    var selectedScreenIndex by remember { mutableIntStateOf(0) }
    var fileToAnalyze by remember { mutableStateOf<File?>(null) }
    val settings by settingsManager.settings.collectAsState()

    val navItems = listOf(
        Triple("ניתוח חי", Icons.Default.GraphicEq, "nav_live"),
        Triple("ניתוח קובץ", Icons.Default.AudioFile, "nav_file"),
        Triple("הקלטות", Icons.Default.Mic, "nav_recordings"),
        Triple("היסטוריה", Icons.Default.History, "nav_history"),
        Triple("הגדרות", Icons.Default.Settings, "nav_settings")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                navItems.forEachIndexed { index, (label, icon, tag) ->
                    NavigationBarItem(
                        selected = selectedScreenIndex == index,
                        onClick = { selectedScreenIndex = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.testTag(tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedScreenIndex) {
                0 -> LiveAnalysisScreen(
                    audioEngine = audioEngine,
                    settings = settings,
                    onSaveMeasurement = { entity ->
                        database.measurementDao().insert(entity)
                    }
                )
                1 -> FileAnalysisScreen(
                    initialFile = fileToAnalyze
                )
                2 -> RecordingsScreen(
                    audioEngine = audioEngine,
                    recordingDao = database.recordingDao(),
                    onAnalyzeRecording = { file ->
                        fileToAnalyze = file
                        selectedScreenIndex = 1 // Switch to File Analysis tab
                    }
                )
                3 -> HistoryScreen(
                    measurementDao = database.measurementDao()
                )
                4 -> SettingsScreen(
                    settings = settings,
                    onSettingsChanged = { newSettings ->
                        settingsManager.updateSettings(newSettings)
                    }
                )
            }
        }
    }
}
