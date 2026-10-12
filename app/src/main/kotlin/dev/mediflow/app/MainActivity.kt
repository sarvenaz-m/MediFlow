package dev.mediflow.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.*
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.mediflow.designsystem.MediFlowTheme
import dev.mediflow.model.*
import dev.mediflow.routine.*
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.*

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var repository: RoutineRepository
    @Inject lateinit var preferences: PreferenceRepository
    @Inject lateinit var reminders: ReminderScheduler
    private val routineViewModel by viewModels<RoutineViewModel>()
    private var pendingImport by mutableStateOf<String?>(null)
    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            routineViewModel.refresh()
        }
    private val exportDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri
            ->
            if (uri != null)
                lifecycleScope.launch {
                    try {
                        val backup = repository.exportBackup()
                        withContext(Dispatchers.IO) {
                            requireNotNull(contentResolver.openOutputStream(uri))
                                .bufferedWriter()
                                .use { it.write(backup) }
                        }
                        routineViewModel.showMessage("Backup saved")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        routineViewModel.showMessage(
                            "Backup could not be saved. Please try another location."
                        )
                    }
                }
        }
    private val importDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                lifecycleScope.launch {
                    try {
                        val content =
                            withContext(Dispatchers.IO) {
                                requireNotNull(contentResolver.openInputStream(uri)).use { input ->
                                    val output = java.io.ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (output.size() <= 2_097_152) {
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        output.write(buffer, 0, read)
                                    }
                                    val bytes = output.toByteArray()
                                    require(bytes.size <= 2_097_152) {
                                        "Backup must be smaller than 2 MB."
                                    }
                                    bytes.toString(Charsets.UTF_8)
                                }
                            }
                        pendingImport = content
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        routineViewModel.showMessage(e.message ?: "Backup could not be opened.")
                    }
                }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by routineViewModel.state.collectAsStateWithLifecycle()
            val dark =
                when (state.preferences.theme) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle =
                        SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        ) {
                            dark
                        },
                    navigationBarStyle =
                        SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        ) {
                            dark
                        },
                )
            }
            MediFlowTheme(dark) {
                RoutineApp(
                    PlatformActions(
                        notifications = {
                            if (
                                Build.VERSION.SDK_INT >= 33 &&
                                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                        android.content.pm.PackageManager.PERMISSION_GRANTED
                            )
                                permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else
                                startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                )
                        },
                        exactAlarms = {
                            if (Build.VERSION.SDK_INT >= 31)
                                startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        "package:$packageName".toUri(),
                                    )
                                )
                        },
                        export = {
                            exportDocument.launch("MediFlow-backup-${LocalDate.now()}.json")
                        },
                        restore = {
                            importDocument.launch(arrayOf("application/json", "text/plain"))
                        },
                    ),
                    routineViewModel,
                )
                pendingImport?.let { backup ->
                    AlertDialog(
                        onDismissRequest = { pendingImport = null },
                        title = { Text("Replace your current routine?") },
                        text = {
                            Text(
                                "This restores medications and history from the selected backup. Export your current routine first if you want to keep a copy."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    pendingImport = null
                                    lifecycleScope.launch {
                                        try {
                                            repository.restoreBackup(backup)
                                            preferences.setOnboarded(
                                                repository.snapshot().medications.any {
                                                    it.id.startsWith("demo-")
                                                }
                                            )
                                            try {
                                                reminders.reconcile()
                                                routineViewModel.showMessage("Routine restored")
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (_: Exception) {
                                                routineViewModel.showMessage(
                                                    "Routine restored. Check reminder settings; scheduling will retry."
                                                )
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            routineViewModel.showMessage(
                                                "Backup was not restored. ${e.message?:"Check the file format."}"
                                            )
                                        }
                                    }
                                }
                            ) {
                                Text("Restore")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
                        },
                    )
                }
            }
        }
    }
}
