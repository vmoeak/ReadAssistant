package com.readassistant.feature.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readassistant.core.data.db.dao.LlmProviderDao
import com.readassistant.core.data.db.entity.LlmProviderEntity
import com.readassistant.core.llm.api.LlmConfig
import com.readassistant.core.llm.api.ProviderType
import com.readassistant.core.llm.service.LlmService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LlmConfigViewModel @Inject constructor(
    private val llmProviderDao: LlmProviderDao,
    private val llmService: LlmService
) : ViewModel() {
    val providers: StateFlow<List<LlmProviderEntity>> = llmProviderDao.getAllProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult

    fun addProvider(entity: LlmProviderEntity) {
        viewModelScope.launch {
            if (entity.isDefault) {
                llmProviderDao.clearAllDefaults()
            }
            llmProviderDao.insert(entity)
        }
    }

    fun deleteProvider(entity: LlmProviderEntity) {
        viewModelScope.launch { llmProviderDao.delete(entity) }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch {
            llmProviderDao.clearAllDefaults()
            llmProviderDao.setDefault(id)
        }
    }

    fun testConnection(entity: LlmProviderEntity) {
        viewModelScope.launch {
            _testResult.value = "Testing..."
            val config = LlmConfig(
                providerType = ProviderType.valueOf(entity.providerType),
                apiKey = entity.apiKey,
                baseUrl = entity.baseUrl,
                modelName = entity.modelName
            )
            val success = llmService.testConnection(config)
            _testResult.value = if (success) "Connection successful!" else "Connection failed"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmConfigScreen(
    onBack: () -> Unit,
    viewModel: LlmConfigViewModel = hiltViewModel()
) {
    val providers by viewModel.providers.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(testResult) {
        testResult?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LLM Providers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Provider")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (providers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No providers configured", style = MaterialTheme.typography.titleMedium)
                    Text("Add an LLM provider for translation and AI Q&A",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(providers, key = { it.id }) { provider ->
                    ProviderItem(
                        provider = provider,
                        onSetDefault = { viewModel.setDefault(provider.id) },
                        onTest = { viewModel.testConnection(provider) },
                        onDelete = { viewModel.deleteProvider(provider) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddProviderDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { entity ->
                viewModel.addProvider(entity)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ProviderItem(
    provider: LlmProviderEntity,
    onSetDefault: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(provider.providerType)
        },
        supportingContent = {
            Text("${provider.providerType} - ${provider.modelName}")
        },
        leadingContent = {
            if (provider.isDefault) {
                Icon(Icons.Default.Star, contentDescription = "Default",
                    tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Default.SmartToy, contentDescription = null)
            }
        },
        trailingContent = {
            Row {
                if (!provider.isDefault) {
                    IconButton(onClick = onSetDefault) {
                        Icon(Icons.Default.StarBorder, contentDescription = "Set Default")
                    }
                }
                IconButton(onClick = onTest) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = "Test")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (LlmProviderEntity) -> Unit
) {
    var providerType by remember { mutableStateOf(ProviderType.OPENAI) }
    var name by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add LLM Provider") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Provider type
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = providerType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        ProviderType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    providerType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                    Text("Set as default")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(LlmProviderEntity(
                        providerType = providerType.name,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        modelName = modelName,
                        isDefault = isDefault
                    ))
                },
                enabled = apiKey.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
