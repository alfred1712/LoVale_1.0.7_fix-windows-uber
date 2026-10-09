package com.example.lovale2.data.settings

import android.content.Context
import com.example.lovale2.domain.DriverOptions
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DriverOptionsStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("driver_options", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mutable = MutableStateFlow(try { gson.fromJson(prefs.getString("options", null), DriverOptions::class.java)?.validate() ?: DriverOptions() } catch (_: Exception) { DriverOptions() })
    val state = mutable.asStateFlow()
    fun save(value: DriverOptions) { value.validate(); prefs.edit().putString("options", gson.toJson(value)).apply(); mutable.value = value }
    companion object {
        @Volatile private var instance: DriverOptionsStore? = null
        fun get(context: Context): DriverOptionsStore = instance ?: synchronized(this) { instance ?: DriverOptionsStore(context.applicationContext).also { instance = it } }
    }
}
