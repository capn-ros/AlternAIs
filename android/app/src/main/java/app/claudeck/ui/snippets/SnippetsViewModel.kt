package app.claudeck.ui.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.claudeck.domain.model.Snippets
import app.claudeck.domain.repository.SnippetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SnippetsViewModel @Inject constructor(private val repo: SnippetRepository) : ViewModel() {
    val snippets: StateFlow<List<String>> =
        repo.observe().stateIn(viewModelScope, SharingStarted.Eagerly, repo.current())

    // Siempre se parte de lo guardado (no de la copia en pantalla) para no pisar cambios hechos desde otro lado.
    fun add(text: String) = repo.save(Snippets.add(repo.current(), text))
    fun replace(index: Int, text: String) = repo.save(Snippets.replace(repo.current(), index, text))
    fun moveUp(index: Int) = repo.save(Snippets.moveUp(repo.current(), index))
    fun remove(index: Int) = repo.save(Snippets.remove(repo.current(), index))
}
