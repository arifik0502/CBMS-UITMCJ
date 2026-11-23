package com.crispyburger.admin.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crispyburger.admin.data.AdminRealtimeManager
import com.crispyburger.admin.data.DeveloperProfile
import com.crispyburger.admin.notifications.AdminNotificationHelper
import com.crispyburger.admin.updater.AppUpdateControls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

private enum class DashboardTab(val label: String, val glyph: String) {
    ORDERS("Orders", "🧾"),
    MENU("Products", "🍔"),
    STOCK("Stock", "📦"),
    TICKETS("Tickets", "🎫"),
    REPORTS("Reports", "📊"),
    SITE("Website", "🎨")
}

@Composable
fun AdminDashboardScreen(
    profile: DeveloperProfile,
    openOrderId: String?,
    onOrderOpenHandled: () -> Unit,
    openTab: String?,
    onTabOpenHandled: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val ordersVm: OrdersViewModel = viewModel()
    val catalogVm: CatalogViewModel = viewModel()
    val inventoryVm: InventoryViewModel = viewModel()
    val reportVm: ReportViewModel = viewModel()
    val ticketsVm: TicketsViewModel = viewModel()
    val siteVm: SiteViewModel = viewModel()
    val deliveryVm: DeliverySettingsViewModel = viewModel()
    val ticketsState by ticketsVm.state.collectAsStateWithLifecycle()

    val availableTabs = remember(profile.role) {
        if (profile.role == "OWNER") DashboardTab.entries
        else listOf(DashboardTab.MENU, DashboardTab.SITE)
    }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var realtimeStatus by remember { mutableStateOf("Connecting") }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        AdminNotificationHelper.createChannel(context.applicationContext)
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                AdminRealtimeManager.start(context.applicationContext)
                realtimeStatus = "Connected"
                awaitCancellation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                realtimeStatus = "Unavailable — pull down to refresh"
            } finally {
                withContext(NonCancellable) {
                    runCatching { AdminRealtimeManager.stop() }
                }
            }
        }
    }

    LaunchedEffect(profile.role) {
        if (profile.role == "OWNER") ticketsVm.ensureLoaded()
    }

    LaunchedEffect(openTab) {
        if (openTab == AdminNotificationHelper.TAB_TICKETS) {
            val ticketIndex = availableTabs.indexOf(DashboardTab.TICKETS)
            if (ticketIndex >= 0) {
                tabIndex = ticketIndex
                ticketsVm.refresh(silent = true)
            }
            onTabOpenHandled()
        }
    }

    // A tapped notification jumps straight to that order.
    LaunchedEffect(openOrderId) {
        if (openOrderId != null) {
            val orderIndex = availableTabs.indexOf(DashboardTab.ORDERS)
            if (orderIndex >= 0) {
                tabIndex = orderIndex
                ordersVm.requestOpen(openOrderId)
            }
            onOrderOpenHandled()
        }
    }

    val tab = availableTabs[tabIndex.coerceIn(0, availableTabs.lastIndex)]

    Scaffold(
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("CBMS ADMIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Welcome, ${profile.fullName.ifBlank { "Admin" }} · Live: $realtimeStatus",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (realtimeStatus == "Connected") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    }
                    TextButton(onClick = {
                        ordersVm.reset()
                        catalogVm.reset()
                        inventoryVm.reset()
                        reportVm.reset()
                        ticketsVm.reset()
                        siteVm.reset()
                        deliveryVm.reset()
                        onSignOut()
                    }) { Text("Sign out") }
                }
                HorizontalDivider()
            }
        },
        bottomBar = {
            NavigationBar {
                availableTabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = index == tabIndex,
                        onClick = { tabIndex = index },
                        icon = {
                            if (item == DashboardTab.TICKETS && ticketsState.openCount > 0) {
                                BadgedBox(badge = { Badge { Text(ticketsState.openCount.toString()) } }) {
                                    Text(item.glyph)
                                }
                            } else Text(item.glyph)
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                DashboardTab.ORDERS -> OrdersScreen(ordersVm)
                DashboardTab.MENU -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { CatalogScreen(catalogVm) }
                }
                DashboardTab.STOCK -> InventoryScreen(inventoryVm)
                DashboardTab.TICKETS -> TicketsScreen(ticketsVm)
                DashboardTab.SITE -> WebsiteToolsScreen(siteVm, deliveryVm)
                DashboardTab.REPORTS -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { ReportScreen(reportVm) }
                    HorizontalDivider()
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) { AppUpdateControls() }
                }
            }
        }
    }
}
