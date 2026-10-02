package com.gamepadlayout.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Único DataStore do app (persistência de configurações e biblioteca). */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "gamepad_layout")
