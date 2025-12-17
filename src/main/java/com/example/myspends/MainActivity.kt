package com.example.myspends

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.myspends.AppNavigation
import com.example.myspends.GastoViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializamos el ViewModel que vivirá mientras la app esté abierta
        val viewModel: GastoViewModel by viewModels()

        setContent {
            MaterialTheme {
                // Contenedor principal
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Aquí llamamos a la navegación que creamos en Pantallas.kt
                    // en lugar de llamar a una sola pantalla.
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}