package me.xizzhu.android.joshua.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.xizzhu.android.joshua.domain.models.Book
import me.xizzhu.android.joshua.domain.models.Translation
import me.xizzhu.android.joshua.domain.models.Verse
import me.xizzhu.android.joshua.domain.usecases.GetBooksUseCase
import me.xizzhu.android.joshua.domain.usecases.GetTranslationsUseCase
import me.xizzhu.android.joshua.domain.usecases.GetVersesUseCase
import me.xizzhu.android.joshua.domain.usecases.SearchVersesUseCase
import javax.inject.Inject

@HiltViewModel
class BibleViewModel @Inject constructor(
    private val getTranslationsUseCase: GetTranslationsUseCase,
    private val getBooksUseCase: GetBooksUseCase,
    private val getVersesUseCase: GetVersesUseCase,
    private val searchVersesUseCase: SearchVersesUseCase
) : ViewModel() {

    private val _translations = MutableStateFlow<List<Translation>>(emptyList())
    val translations: StateFlow<List<Translation>> = _translations

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books

    private val _verses = MutableStateFlow<List<Verse>>(emptyList())
    val verses: StateFlow<List<Verse>> = _verses

    private val _searchResults = MutableStateFlow<List<Verse>>(emptyList())
    val searchResults: StateFlow<List<Verse>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadTranslations()
    }

    fun loadTranslations() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val translations = getTranslationsUseCase()
                _translations.value = translations
            } catch (e: Exception) {
                _error.value = "Failed to load translations: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadBooks(translation: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val books = getBooksUseCase(translation)
                _books.value = books
            } catch (e: Exception) {
                _error.value = "Failed to load books: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadVerses(bookId: Int, chapter: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val verses = getVersesUseCase(bookId, chapter)
                _verses.value = verses
            } catch (e: Exception) {
                _error.value = "Failed to load verses: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchVerses(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val results = searchVersesUseCase(query)
                _searchResults.value = results
            } catch (e: Exception) {
                _error.value = "Failed to search verses: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}