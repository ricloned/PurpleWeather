package com.purpleweather.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.purpleweather.app.ui.WeatherApp

class MainActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bar = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
        setContent { WeatherApp(vm) }
    }

    override fun onResume() {
        super.onResume()
        // свежий прогноз при возвращении (если старше 20 минут)
        vm.cities.forEach { vm.refresh(it, force = false) }
    }
}
