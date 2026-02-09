package com.readassistant.feature.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readassistant.core.data.datastore.UserPreferences
import com.readassistant.core.ui.components.FontSettings
import com.readassistant.core.ui.components.ThemePicker
import com.readassistant.core.ui.theme.ReadingThemeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {
    val themeType = userPreferences.themeType
    val fontSize = userPreferences.fontSize
    val lineHeight = userPreferences.lineHeight
    val sourceLanguage = userPreferences.sourceLanguage
    val targetLanguage = userPreferences.targetLanguage
    val autoSyncEnabled = userPreferences.autoSyncEnabled

    fun setTheme(type: String) {
        viewModelScope.launch { userPreferences.setThemeType(type) }
    }

    fun setFontSize(size: Float) {
        viewModelScope.launch { userPreferences.setFontSize(size) }
    }

    fun setLineHeight(height: Float) {
        viewModelScope.launch { userPreferences.setLineHeight(height) }
    }

    fun setSourceLanguage(lang: String) {
        viewModelScope.launch { userPreferences.setSourceLanguage(lang) }
    }

    fun setTargetLanguage(lang: String) {
        viewModelScope.launch { userPreferences.setTargetLanguage(lang) }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setAutoSyncEnabled(enabled) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLlmConfigClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeType by viewModel.themeType.collectAsState(initial = "LIGHT")
    val fontSize by viewModel.fontSize.collectAsState(initial = 16f)
    val lineHeight by viewModel.lineHeight.collectAsState(initial = 1.75f)
    val sourceLang by viewModel.sourceLanguage.collectAsState(initial = "en")
    val targetLang by viewModel.targetLanguage.collectAsState(initial = "zh")
    val autoSync by viewModel.autoSyncEnabled.collectAsState(initial = true)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Theme section
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            ThemePicker(
                currentTheme = try {
                    ReadingThemeType.valueOf(themeType)
                } catch (_: Exception) {
                    ReadingThemeType.LIGHT
                },
                onThemeSelected = { viewModel.setTheme(it.name) }
            )

            HorizontalDivider()

            // Font settings
            Text("Reading", style = MaterialTheme.typography.titleMedium)
            FontSettings(
                fontSize = fontSize,
                lineHeight = lineHeight,
                onFontSizeChange = { viewModel.setFontSize(it) },
                onLineHeightChange = { viewModel.setLineHeight(it) }
            )

            HorizontalDivider()

            // Language settings
            Text("Translation", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LanguageDropdown(
                    label = "Source",
                    selectedLang = sourceLang,
                    onSelect = { viewModel.setSourceLanguage(it) },
                    modifier = Modifier.weight(1f)
                )
                LanguageDropdown(
                    label = "Target",
                    selectedLang = targetLang,
                    onSelect = { viewModel.setTargetLanguage(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            // LLM Config
            ListItem(
                headlineContent = { Text("LLM Provider") },
                supportingContent = { Text("Configure AI provider for translation and Q&A") },
                leadingContent = {
                    Icon(Icons.Default.SmartToy, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onLlmConfigClick)
            )

            HorizontalDivider()

            // Sync settings
            Text("Data", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-sync feeds", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Refresh RSS feeds every 2 hours",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoSync,
                    onCheckedChange = { viewModel.setAutoSync(it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    label: String,
    selectedLang: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val languages = mapOf(
        "en" to "English",
        "zh" to "Chinese",
        "ja" to "Japanese",
        "ko" to "Korean",
        "fr" to "French",
        "de" to "German",
        "es" to "Spanish",
        "ru" to "Russian"
    )
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = languages[selectedLang] ?: selectedLang,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languages.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    }
                )
            }
        }
    }
}
