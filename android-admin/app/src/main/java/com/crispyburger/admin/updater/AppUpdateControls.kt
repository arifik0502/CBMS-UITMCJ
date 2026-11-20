package com.crispyburger.admin.updater

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AppUpdateControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var dialogVisible by remember { mutableStateOf(false) }

    fun checkUpdate(showNoUpdate: Boolean) {
        scope.launch {
            checking = true
            message = null
            try {
                release = AppUpdater.check(context)
                if (release != null) dialogVisible = true
                else if (showNoUpdate) android.widget.Toast.makeText(context, "CBMS Admin is up to date.", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (showNoUpdate) android.widget.Toast.makeText(context, e.message ?: "Update check failed.", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                checking = false
            }
        }
    }

    LaunchedEffect(Unit) { checkUpdate(false) }

    OutlinedButton(onClick = { checkUpdate(true) }, modifier = Modifier.fillMaxWidth(), enabled = !checking && !downloading) {
        if (checking) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
        Text(if (checking) "Checking updates…" else "Check app updates")
    }

    if (dialogVisible && release != null) {
        val latest = release!!
        AlertDialog(
            onDismissRequest = { if (!downloading) dialogVisible = false },
            title = { Text("CBMS Admin update available") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version ${latest.version} is available.")
                    if (latest.notes.isNotBlank()) Text(latest.notes, style = MaterialTheme.typography.bodySmall)
                    if (downloading) Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Downloading update…")
                    }
                    if (message != null) Text(message!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        downloading = true
                        message = null
                        try {
                            val apk = AppUpdater.downloadAndInstall(context, latest)
                            AppUpdater.install(context, apk)
                            dialogVisible = false
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: InstallPermissionRequired) {
                            message = e.message
                        } catch (e: Exception) {
                            message = e.message ?: "Unable to download or install update."
                        } finally {
                            downloading = false
                        }
                    }
                }, enabled = !downloading) { Text("Update now") }
            },
            dismissButton = { TextButton(onClick = { dialogVisible = false }, enabled = !downloading) { Text("Later") } }
        )
    }
}
