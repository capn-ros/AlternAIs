package app.claudeck.domain.repository

import kotlinx.coroutines.flow.Flow

interface SnippetRepository {
    fun current(): List<String>
    fun observe(): Flow<List<String>>
    fun save(list: List<String>)
}
