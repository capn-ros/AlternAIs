package app.claudeck.ui.snippets

import app.claudeck.domain.repository.SnippetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnippetsViewModelTest {
    private class FakeRepo(initial: List<String>) : SnippetRepository {
        val flow = MutableStateFlow(initial)
        override fun current() = flow.value
        override fun observe(): Flow<List<String>> = flow
        override fun save(list: List<String>) { flow.value = list }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `arranca con lo guardado`() =
        assertEquals(listOf("a", "b"), SnippetsViewModel(FakeRepo(listOf("a", "b"))).snippets.value)

    @Test fun `agregar editar subir y borrar se reflejan en el estado`() {
        val vm = SnippetsViewModel(FakeRepo(listOf("a", "b")))
        vm.add("c");            assertEquals(listOf("a", "b", "c"), vm.snippets.value)
        vm.replace(0, "A");     assertEquals(listOf("A", "b", "c"), vm.snippets.value)
        vm.moveUp(2);           assertEquals(listOf("A", "c", "b"), vm.snippets.value)
        vm.remove(1);           assertEquals(listOf("A", "b"), vm.snippets.value)
    }

    @Test fun `un cambio hecho desde afuera llega al estado`() {
        val repo = FakeRepo(listOf("a"))
        val vm = SnippetsViewModel(repo)
        repo.save(listOf("a", "nuevo"))
        assertEquals(listOf("a", "nuevo"), vm.snippets.value)
    }

    @Test fun `agregar un texto en blanco no guarda nada nuevo`() {
        val vm = SnippetsViewModel(FakeRepo(listOf("a")))
        vm.add("   ")
        assertEquals(listOf("a"), vm.snippets.value)
    }
}
