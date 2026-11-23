package com.crispyburger.admin.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.crispyburger.admin.BuildConfig
import com.crispyburger.admin.data.Formats
import com.crispyburger.admin.data.ImageCompressor
import com.crispyburger.admin.data.SectionType
import com.crispyburger.admin.data.SiteConfig
import com.crispyburger.admin.data.SiteRules
import com.crispyburger.admin.data.SiteSection
import com.crispyburger.admin.data.SiteTheme
import com.crispyburger.admin.ui.common.ButtonProgress
import com.crispyburger.admin.ui.common.ConfirmDialog
import com.crispyburger.admin.ui.common.MessageCard
import com.crispyburger.admin.ui.common.SectionHeader
import com.crispyburger.admin.ui.common.StatusChip
import com.crispyburger.admin.ui.common.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SiteConfirm { PUBLISH, DISCARD, RESET }
private enum class ColorRole(val label: String) {
    BG("Page background"), INK("Text"), ACCENT("Buttons, prices & badges"), GOOD("“In stock” badge"), PAPER("Cards")
}

private fun themeColor(t: SiteTheme, role: ColorRole) = when (role) {
    ColorRole.BG -> t.bg
    ColorRole.INK -> t.ink
    ColorRole.ACCENT -> t.accent
    ColorRole.GOOD -> t.good
    ColorRole.PAPER -> t.paper
}

private fun withColor(t: SiteTheme, role: ColorRole, hex: String) = when (role) {
    ColorRole.BG -> t.copy(bg = hex)
    ColorRole.INK -> t.copy(ink = hex)
    ColorRole.ACCENT -> t.copy(accent = hex)
    ColorRole.GOOD -> t.copy(good = hex)
    ColorRole.PAPER -> t.copy(paper = hex)
}

private fun parse(hex: String): Color =
    if (SiteRules.isHex(hex)) Color(android.graphics.Color.parseColor(hex)) else Color.Gray

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SiteScreen(vm: SiteViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<SiteConfirm?>(null) }
    var colorRole by remember { mutableStateOf<ColorRole?>(null) }
    var pictureFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.ensureLoaded() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = pictureFor
        pictureFor = null
        if (uri == null || id == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { ImageCompressor.toJpeg(context.contentResolver, uri) }.getOrNull()
            }
            if (bytes == null) {
                vm.pictureProblem("That picture could not be used. Try a different one.")
                return@launch
            }
            vm.uploadPicture(bytes) { url ->
                vm.edit { c ->
                    c.sections.firstOrNull { it.id == id }?.let { SiteRules.replace(c, it.copy(imageUrl = url)) } ?: c
                }
            }
        }
    }

    val c = state.config

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader("Customer website")
                    Text(
                        "Change how the ordering website looks. Nothing goes live until you press Publish, and you can always undo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.info?.let { msg -> item(key = "info") { MessageCard(msg, isError = false) } }
            state.error?.let { msg -> item(key = "error") { MessageCard(msg, isError = true) } }

            if (!state.loaded) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        if (state.isLoading) CircularProgressIndicator()
                        else OutlinedButton(onClick = { vm.refresh() }) { Text("Try again") }
                    }
                }
                return@LazyColumn
            }

            item(key = "status") {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when {
                                state.isSaving || state.dirty -> StatusChip("Saving…", Tone.Neutral)
                                state.hasUnpublished -> StatusChip("Not published yet", Tone.Warn)
                                else -> StatusChip("Live", Tone.Good)
                            }
                            Text(
                                if (state.hasUnpublished || state.dirty) "Customers still see the old website." else "Customers see this website.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { showPreview = true }, modifier = Modifier.weight(1f)) { Text("Preview") }
                            Button(
                                onClick = { confirm = SiteConfirm.PUBLISH },
                                enabled = state.canPublish,
                                modifier = Modifier.weight(1f)
                            ) { if (state.isPublishing) ButtonProgress() else Text("Publish") }
                        }
                        if (state.hasUnpublished || state.dirty) {
                            TextButton(onClick = { confirm = SiteConfirm.DISCARD }) { Text("Undo all unpublished changes") }
                        }
                    }
                }
            }

            if (state.problems.isNotEmpty()) {
                item(key = "problems") {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Fix before publishing", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                            state.problems.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            item(key = "layout-title") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Page layout", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Top to bottom, as customers scroll. Use the arrows to reorder and the switch to hide.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            c.sections.forEachIndexed { index, s ->
                item(key = "s-${s.id}") {
                    SectionCard(
                        section = s,
                        first = index == 0,
                        last = index == c.sections.lastIndex,
                        onToggle = { v -> vm.edit { SiteRules.setVisible(it, s.id, v) } },
                        onUp = { vm.edit { SiteRules.move(it, s.id, -1) } },
                        onDown = { vm.edit { SiteRules.move(it, s.id, 1) } },
                        onEdit = { editingId = s.id }
                    )
                }
            }

            item(key = "add") {
                OutlinedButton(
                    onClick = { showAdd = true },
                    enabled = SiteRules.canAddCustom(c),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ Add a text box, picture or button") }
            }

            item(key = "look") {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Colours & style", style = MaterialTheme.typography.titleMedium)
                        Text("Pick a ready-made look:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SiteRules.PRESETS.forEach { p ->
                                FilterChip(
                                    selected = c.theme.copy(font = p.theme.font) == p.theme,
                                    onClick = { vm.edit { it.copy(theme = p.theme.copy(font = it.theme.font)) } },
                                    label = { Text(p.name) },
                                    leadingIcon = { Dot(p.theme.bg, 18) }
                                )
                            }
                        }
                        HorizontalDivider()
                        Text("Or choose each colour yourself:", style = MaterialTheme.typography.labelMedium)
                        ColorRole.entries.forEach { role ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { colorRole = role }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Dot(themeColor(c.theme, role), 30)
                                Text(role.label, modifier = Modifier.weight(1f))
                                Text(themeColor(c.theme, role), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        HorizontalDivider()
                        Text("Lettering:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SiteRules.FONTS.forEach { (key, label) ->
                                FilterChip(
                                    selected = c.theme.font == key,
                                    onClick = { vm.edit { it.copy(theme = it.theme.copy(font = key)) } },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "brand") {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Shop name & footer", style = MaterialTheme.typography.titleMedium)
                        Field("Shop name at the top (leave empty for the usual one)", c.brandName, 30) { v -> vm.edit { it.copy(brandName = v) } }
                        Field("Small note at the bottom of the page", c.footerNote, 160) { v -> vm.edit { it.copy(footerNote = v) } }
                    }
                }
            }

            item(key = "banner") {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Announcement bar", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "A message across the top of every page, e.g. “Closed for exams this Friday”.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = c.banner.enabled, onCheckedChange = { v -> vm.edit { it.copy(banner = it.banner.copy(enabled = v)) } })
                        }
                        if (c.banner.enabled) {
                            Field("Message", c.banner.text, 120) { v -> vm.edit { it.copy(banner = it.banner.copy(text = v)) } }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("info" to "Plain", "promo" to "Highlight", "warn" to "Warning").forEach { (key, label) ->
                                    FilterChip(
                                        selected = c.banner.tone == key,
                                        onClick = { vm.edit { it.copy(banner = it.banner.copy(tone = key)) } },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Field("Link text (optional)", c.banner.linkLabel, 30) { v -> vm.edit { it.copy(banner = it.banner.copy(linkLabel = v)) } }
                            if (c.banner.linkLabel.isNotBlank()) {
                                LinkPicker(c.banner.linkTo) { v -> vm.edit { it.copy(banner = it.banner.copy(linkTo = v)) } }
                            }
                        }
                    }
                }
            }

            item(key = "more") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.loadHistory(); showHistory = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Earlier published versions") }
                    TextButton(onClick = { confirm = SiteConfirm.RESET }, modifier = Modifier.fillMaxWidth()) {
                        Text("Start over with the original design")
                    }
                }
            }
        }
    }

    editingId?.let { id ->
        val section = c.sections.firstOrNull { it.id == id }
        if (section == null) editingId = null
        else SectionEditor(
            section = section,
            uploading = state.isUploading,
            onChange = { updated -> vm.edit { SiteRules.replace(it, updated) } },
            onPickPicture = { pictureFor = id; picker.launch("image/*") },
            onRemove = if (section.type in SectionType.custom) {
                { vm.edit { SiteRules.removeCustom(it, id) }; editingId = null }
            } else null,
            onClose = { editingId = null }
        )
    }

    colorRole?.let { role ->
        ColorDialog(
            title = role.label,
            current = themeColor(c.theme, role),
            onPick = { hex -> vm.edit { it.copy(theme = withColor(it.theme, role, hex)) }; colorRole = null },
            onDismiss = { colorRole = null }
        )
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add to the page") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SectionType.TEXT to "Text box: a title and a few lines",
                        SectionType.IMAGE to "Picture: a photo from your phone",
                        SectionType.BUTTON to "Button: links to a page"
                    ).forEach { (type, label) ->
                        OutlinedButton(
                            onClick = {
                                val suffix = java.util.UUID.randomUUID().toString()
                                val next = SiteRules.addCustom(c, type, suffix)
                                val newId = next.sections.firstOrNull { it.id !in c.sections.map { s -> s.id } }?.id
                                vm.edit { SiteRules.addCustom(it, type, suffix) }
                                showAdd = false
                                editingId = newId
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Earlier versions") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.history.isEmpty()) {
                        Text("No earlier versions yet. Each time you publish, the previous version is kept here.")
                    }
                    state.history.forEach { v ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Version ${v.version}\n${Formats.malaysiaDateTime(v.publishedAt)}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedButton(onClick = { vm.restore(v.id); showHistory = false }) { Text("Use this") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Close") } }
        )
    }

    confirm?.let { which ->
        when (which) {
            SiteConfirm.PUBLISH -> ConfirmDialog(
                title = "Publish to the website",
                message = "Customers will see these changes right away. You can go back to an earlier version later.",
                confirmLabel = "Publish",
                onConfirm = { confirm = null; vm.publish() },
                onDismiss = { confirm = null }
            )
            SiteConfirm.DISCARD -> ConfirmDialog(
                title = "Undo changes",
                message = "Throw away everything you changed since the last publish?",
                confirmLabel = "Undo changes",
                onConfirm = { confirm = null; vm.discard() },
                onDismiss = { confirm = null }
            )
            SiteConfirm.RESET -> ConfirmDialog(
                title = "Start over",
                message = "Put the original colours, text and layout back in the editor? Nothing changes for customers until you publish.",
                confirmLabel = "Start over",
                onConfirm = { confirm = null; vm.resetToOriginal() },
                onDismiss = { confirm = null }
            )
        }
    }

    if (showPreview) {
        PreviewDialog(
            json = state.previewJson,
            canPublish = state.canPublish,
            onPublish = { showPreview = false; confirm = SiteConfirm.PUBLISH },
            onClose = { showPreview = false }
        )
    }
}

@Composable
private fun Dot(hex: String, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(parse(hex))
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    )
}

@Composable
private fun Field(label: String, value: String, max: Int, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(max)) },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun summary(s: SiteSection): String = when (s.type) {
    SectionType.HERO -> s.headline.ifBlank { "No headline" }
    SectionType.MENU -> "Always on the page so customers can order"
    SectionType.STEPS -> "${s.items.size} steps"
    SectionType.TRIPS -> s.title.ifBlank { "Delivery trips" }
    SectionType.BUILDINGS -> "Lists your delivery buildings"
    SectionType.SUPPORT -> s.label.ifBlank { "Report link" }
    SectionType.TEXT -> s.title.ifBlank { s.body.ifBlank { "Empty" } }
    SectionType.IMAGE -> if (s.imageUrl.isNullOrBlank()) "No picture yet" else "Picture added"
    SectionType.BUTTON -> s.label.ifBlank { "No label yet" }
    else -> ""
}

@Composable
private fun SectionCard(
    section: SiteSection,
    first: Boolean,
    last: Boolean,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit
) {
    val isMenu = section.type == SectionType.MENU
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(SectionType.glyph(section.type), style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f)) {
                    Text(SectionType.label(section.type), fontWeight = FontWeight.SemiBold)
                    Text(
                        summary(section),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Switch(checked = section.visible, onCheckedChange = onToggle, enabled = !isMenu)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onUp, enabled = !first) { Text("▲ Up") }
                TextButton(onClick = onDown, enabled = !last) { Text("▼ Down") }
                Box(Modifier.weight(1f))
                OutlinedButton(onClick = onEdit) { Text("Edit") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkPicker(value: String, onChange: (String) -> Unit) {
    val known = SiteRules.LINKS.any { it.path == value }
    var custom by remember { mutableStateOf(value.isNotBlank() && !known) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Where should it go?", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SiteRules.LINKS.forEach { l ->
                FilterChip(
                    selected = !custom && value == l.path,
                    onClick = { custom = false; onChange(l.path) },
                    label = { Text(l.label) }
                )
            }
            FilterChip(selected = custom, onClick = { custom = true }, label = { Text("Another website") })
        }
        if (custom) {
            OutlinedTextField(
                value = value,
                onValueChange = { onChange(it.trim().take(200)) },
                label = { Text("Web address (starts with https://)") },
                singleLine = true,
                isError = value.isNotBlank() && !SiteRules.isValidLink(value),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SectionEditor(
    section: SiteSection,
    uploading: Boolean,
    onChange: (SiteSection) -> Unit,
    onPickPicture: () -> Unit,
    onRemove: (() -> Unit)?,
    onClose: () -> Unit
) {
    var confirmRemove by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(SectionType.label(section.type), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClose) { Text("Done") }
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (section.type) {
                        SectionType.HERO -> {
                            Field("Headline", section.headline, 120, singleLine = false) { onChange(section.copy(headline = it)) }
                            PictureControls(section.imageUrl, uploading, onPickPicture, "The burger drawing is used when no picture is added.") {
                                onChange(section.copy(imageUrl = null))
                            }
                        }
                        SectionType.MENU -> {
                            Field("Heading above the menu", section.title, 40) { onChange(section.copy(title = it)) }
                            Text(
                                "The product name, price and photo come from the Menu tab.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SectionType.STEPS -> {
                            Field("Heading (optional)", section.title, 40) { onChange(section.copy(title = it)) }
                            section.items.forEachIndexed { i, text ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.weight(1f)) {
                                        Field("Step ${i + 1}", text, 160, singleLine = false) { v ->
                                            onChange(section.copy(items = section.items.toMutableList().also { it[i] = v }))
                                        }
                                    }
                                    TextButton(onClick = { onChange(section.copy(items = section.items.filterIndexed { j, _ -> j != i })) }) { Text("✕") }
                                }
                            }
                            OutlinedButton(
                                onClick = { onChange(section.copy(items = section.items + "")) },
                                enabled = section.items.size < SiteRules.MAX_STEPS,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("+ Add a step") }
                        }
                        SectionType.TRIPS -> {
                            Field("Heading", section.title, 40) { onChange(section.copy(title = it)) }
                            Field("Short explanation", section.body, 200, singleLine = false) { onChange(section.copy(body = it)) }
                            Text(
                                "The trip names and times come from your delivery trips setup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SectionType.BUILDINGS -> Field("Sentence before the building list", section.body, 200) { onChange(section.copy(body = it)) }
                        SectionType.SUPPORT -> {
                            Field("Text before the link", section.body, 120) { onChange(section.copy(body = it)) }
                            Field("Link text", section.label, 30) { onChange(section.copy(label = it)) }
                        }
                        SectionType.TEXT -> {
                            Field("Title", section.title, 60) { onChange(section.copy(title = it)) }
                            Field("Text", section.body, 600, singleLine = false) { onChange(section.copy(body = it)) }
                        }
                        SectionType.IMAGE -> {
                            PictureControls(section.imageUrl, uploading, onPickPicture, null) { onChange(section.copy(imageUrl = null)) }
                            Field("Caption (optional)", section.caption, 120) { onChange(section.copy(caption = it)) }
                        }
                        SectionType.BUTTON -> {
                            Field("Button label", section.label, 30) { onChange(section.copy(label = it)) }
                            LinkPicker(section.to) { onChange(section.copy(to = it)) }
                        }
                    }

                    if (onRemove != null) {
                        HorizontalDivider()
                        TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Remove this from the page", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (confirmRemove && onRemove != null) {
        ConfirmDialog(
            title = "Remove",
            message = "Remove this ${SectionType.label(section.type).lowercase()} from the page?",
            confirmLabel = "Remove",
            onConfirm = { confirmRemove = false; onRemove() },
            onDismiss = { confirmRemove = false }
        )
    }
}

@Composable
private fun PictureControls(url: String?, uploading: Boolean, onPick: () -> Unit, hint: String?, onClear: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (url.isNullOrBlank()) "No picture yet" else "Picture added ✓", fontWeight = FontWeight.SemiBold)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPick, enabled = !uploading) {
                    if (uploading) ButtonProgress() else Text(if (url.isNullOrBlank()) "Choose picture" else "Change picture")
                }
                if (!url.isNullOrBlank()) TextButton(onClick = onClear, enabled = !uploading) { Text("Remove") }
            }
            Text("Pictures are shrunk automatically. Use Preview to see how it looks.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorDialog(title: String, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var hex by remember { mutableStateOf(current) }
    val normalized = SiteRules.normalizeHex(hex)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SiteRules.SWATCHES.forEach { s ->
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(parse(s))
                                .border(
                                    BorderStroke(if (s.equals(normalized, true)) 3.dp else 1.dp, MaterialTheme.colorScheme.outline),
                                    CircleShape
                                )
                                .clickable { hex = s }
                        )
                    }
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { hex = it.take(7) },
                    label = { Text("Or type a colour code, like #FFC93C") },
                    singleLine = true,
                    isError = normalized == null,
                    leadingIcon = { Dot(normalized ?: "#888888", 22) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = { normalized?.let(onPick) }, enabled = normalized != null) { Text("Use this colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** The real website in a WebView, fed the unsaved draft so changes show up instantly. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PreviewDialog(json: String, canPublish: Boolean, onPublish: () -> Unit, onClose: () -> Unit) {
    val base = BuildConfig.SITE_URL.trim().trimEnd('/')
    var web by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(json, ready, web) {
        val view = web ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        val js = "(function t(n){if(window.__cbmsPreview){window.__cbmsPreview($json)}else if(n<60){setTimeout(function(){t(n+1)},100)}})(0);"
        view.evaluateJavascript(js, null)
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Not live yet. Orders placed here are real.", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onPublish, enabled = canPublish) { Text("Publish") }
                    TextButton(onClick = onClose) { Text("Close") }
                }
                HorizontalDivider()
                if (!base.startsWith("https://")) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Add site.url=https://your-site to android-admin/local.properties and rebuild the app to enable the preview.")
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) { ready = true }
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        // Stay on the customer website; never navigate the preview elsewhere.
                                        val host = request?.url?.host ?: return true
                                        return host != android.net.Uri.parse(base).host
                                    }
                                }
                                web = this
                                loadUrl("$base/?preview=app")
                            }
                        },
                        onRelease = { it.destroy(); web = null; ready = false }
                    )
                }
            }
        }
    }
}
