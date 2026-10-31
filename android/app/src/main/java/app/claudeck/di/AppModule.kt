package app.claudeck.di

import android.content.Context
import android.content.SharedPreferences
import app.claudeck.LimitAlarms
import app.claudeck.data.SharedPrefsAccountRepository
import app.claudeck.data.SharedPrefsSettingsRepository
import app.claudeck.data.SharedPrefsSnippetRepository
import app.claudeck.domain.Clock
import app.claudeck.domain.SystemClock
import app.claudeck.domain.repository.AccountRepository
import app.claudeck.domain.repository.SettingsRepository
import app.claudeck.domain.repository.SnippetRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** El mismo archivo de siempre: así lo guardado antes sigue valiendo. */
    @Provides @Singleton
    fun prefs(@ApplicationContext ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("claudeck", Context.MODE_PRIVATE)

    @Provides @Singleton
    fun accountRepository(@ApplicationContext ctx: Context, prefs: SharedPreferences): AccountRepository =
        SharedPrefsAccountRepository(prefs) { LimitAlarms.reschedule(ctx) }

    @Provides @Singleton
    fun settingsRepository(prefs: SharedPreferences): SettingsRepository = SharedPrefsSettingsRepository(prefs)

    @Provides @Singleton
    fun snippetRepository(prefs: SharedPreferences): SnippetRepository = SharedPrefsSnippetRepository(prefs)

    @Provides @Singleton
    fun clock(): Clock = SystemClock
}
