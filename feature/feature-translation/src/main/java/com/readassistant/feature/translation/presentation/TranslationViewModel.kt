package com.readassistant.feature.translation.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readassistant.core.data.datastore.UserPreferences
import com.readassistant.feature.translation.data.TranslationRepositoryImpl
import com.readassistant.feature.translation.domain.TranslationPair
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject

@HiltViewModel
class TranslationViewModel @Inject constructor(private val repo: TranslationRepositoryImpl, private val prefs: UserPreferences) : ViewModel() {
    private val _isBilingual = MutableStateFlow(false); val isBilingualMode: StateFlow<Boolean> = _isBilingual
    private val _translations = MutableStateFlow<Map<Int, TranslationPair>>(emptyMap()); val translations: StateFlow<Map<Int, TranslationPair>> = _translations
    private val sem = Semaphore(3); private val jobs = mutableMapOf<Int, Job>()
    val sourceLang = prefs.sourceLanguage; val targetLang = prefs.targetLanguage

    fun toggleBilingualMode() { _isBilingual.value = !_isBilingual.value }
    fun translateParagraphs(paragraphs: List<Pair<Int, String>>) { if (!_isBilingual.value) return; viewModelScope.launch { val s = sourceLang.first(); val t = targetLang.first()
        paragraphs.forEach { (i, txt) -> if (_translations.value.containsKey(i) || jobs.containsKey(i) || txt.isBlank()) return@forEach
            jobs[i] = viewModelScope.launch { sem.acquire(); try { repo.getTranslation(i, txt, s, t).collect { p -> _translations.update { it + (i to p) } } } finally { sem.release() } }
        }
    }}
    fun clearTranslations() { jobs.values.forEach { it.cancel() }; jobs.clear(); _translations.value = emptyMap() }
}
