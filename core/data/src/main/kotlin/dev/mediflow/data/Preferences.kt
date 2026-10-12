package dev.mediflow.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mediflow.model.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.preferencesStore by preferencesDataStore("mediflow_preferences")

@Singleton
class DataStorePreferences
@Inject
constructor(@param:ApplicationContext private val context: Context) : PreferenceRepository {
    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val sample = booleanPreferencesKey("sample_data")
        val reminders = booleanPreferencesKey("reminders")
        val theme = stringPreferencesKey("theme")
        val discreet = booleanPreferencesKey("discreet_notifications")
    }

    override val preferences =
        context.preferencesStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map {
                AppPreferences(
                    it[Keys.onboarded] ?: false,
                    it[Keys.sample] ?: false,
                    it[Keys.reminders] ?: false,
                    runCatching { ThemeMode.valueOf(it[Keys.theme] ?: "LIGHT") }
                        .getOrDefault(ThemeMode.LIGHT),
                    it[Keys.discreet] ?: true,
                )
            }

    override suspend fun setOnboarded(sample: Boolean) {
        context.preferencesStore.edit {
            it[Keys.onboarded] = true
            it[Keys.sample] = sample
        }
    }

    override suspend fun setReminders(enabled: Boolean) {
        context.preferencesStore.edit { it[Keys.reminders] = enabled }
    }

    override suspend fun setTheme(theme: ThemeMode) {
        context.preferencesStore.edit { it[Keys.theme] = theme.name }
    }

    override suspend fun setDiscreet(enabled: Boolean) {
        context.preferencesStore.edit { it[Keys.discreet] = enabled }
    }
}
