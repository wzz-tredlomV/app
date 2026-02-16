package com.example.model3dviewer.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.model3dviewer.model.RecentModel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_models")

class RecentModelsManager(private val context: Context) {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val KEY_MODELS = stringPreferencesKey("models_list")
    private val MAX_RECENT = 20

    suspend fun getRecentModels(): List<RecentModel> {
        return try {
            val prefs = context.dataStore.data.first()
            val jsonString = prefs[KEY_MODELS] ?: "[]"
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addRecentModel(model: RecentModel) {
        val current = getRecentModels().toMutableList()
        current.removeAll { it.path == model.path }
        current.add(0, model)
        
        if (current.size > MAX_RECENT) {
            current.subList(MAX_RECENT, current.size).clear()
        }
        
        context.dataStore.edit { prefs ->
            prefs[KEY_MODELS] = json.encodeToString(current)
        }
    }

    suspend fun removeRecentModel(model: RecentModel) {
        val current = getRecentModels().toMutableList()
        current.removeAll { it.id == model.id }
        
        context.dataStore.edit { prefs ->
            prefs[KEY_MODELS] = json.encodeToString(current)
        }
    }
}

