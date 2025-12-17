package com.example.myspends

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.myspends.data.AppDatabase
import com.example.myspends.data.Gasto

// ✅ CORRECCIÓN DE IMPORTACIONES (Ya no usamos 'gotrue')
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class GastoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "gastos-db"
    ).build()
    private val dao = db.gastoDao()

    private val _listaGastos = MutableStateFlow<List<Gasto>>(emptyList())
    val listaGastos = _listaGastos.asStateFlow()

    private val _totalGastado = MutableStateFlow(0.0)
    val totalGastado = _totalGastado.asStateFlow()

    private val _nombreUsuario = MutableStateFlow("Invitado")
    val nombreUsuario = _nombreUsuario.asStateFlow()

    // Variable para el email (Necesaria para Pantallas.kt)
    private val _emailUsuario = MutableStateFlow("")
    val emailUsuario = _emailUsuario.asStateFlow()

    private val authManager = AuthManager(application)

    init {
        // 1. Base de datos local
        viewModelScope.launch {
            dao.obtenerGastos().collect { gastos ->
                _listaGastos.value = gastos
                _totalGastado.value = gastos.sumOf { it.monto }
            }
        }

        // 2. Sesión de Google/Supabase
        viewModelScope.launch {
            SupabaseClient.client.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    val user = status.session.user
                    val nombreGoogle = user?.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                    val nombreCorto = user?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }

                    _nombreUsuario.value = nombreGoogle ?: nombreCorto ?: "Usuario"
                    _emailUsuario.value = user?.email ?: ""

                    Log.d("ViewModel", "Usuario detectado: ${_emailUsuario.value}, descargando nube...")
                    descargarDatosDeNube()
                } else {
                    _nombreUsuario.value = "Invitado"
                    _emailUsuario.value = ""
                }
            }
        }
    }

    fun agregarGasto(monto: Double, descripcion: String, uriFoto: String?) {
        viewModelScope.launch {
            val user = SupabaseClient.client.auth.currentUserOrNull()
            val userId = user?.id
            val userEmail = user?.email

            // 👇 AQUÍ TOMAMOS EL NOMBRE REAL DE LA VARIABLE QUE YA TIENES 👇
            val nombreReal = _nombreUsuario.value

            val nuevoGasto = Gasto(
                monto = monto,
                descripcion = descripcion,
                rutaFotoLocal = uriFoto,
                usuarioId = userId,
                email_creador = userEmail,
                nombreCreador = nombreReal, // <--- GUARDAMOS EL NOMBRE
                sincronizado = false
            )

            // 1. Guardar en local
            dao.insertarGasto(nuevoGasto)

            // 2. Subir a la nube
            if (userId != null && uriFoto != null) {
                try {
                    val urlNube = subirFotoYObtenerUrl(uriFoto)
                    if (urlNube != null) {
                        // Al actualizar, mantenemos el nombreCreador
                        val gastoParaNube = nuevoGasto.copy(foto_url = urlNube, sincronizado = true)
                        dao.insertarGasto(gastoParaNube)
                        SupabaseClient.client.from("gastos").insert(gastoParaNube)
                        Log.d("ViewModel", "Gasto subido con éxito!")
                    }
                } catch (e: Exception) {
                    Log.e("ViewModel", "Error subiendo gasto: ${e.message}")
                }
            }
        }
    }

    private suspend fun subirFotoYObtenerUrl(uriLocal: String): String? {
        return try {
            val uri = Uri.parse(uriLocal)
            val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.readBytes()

            if (bytes == null) return null

            val fileName = "${UUID.randomUUID()}.jpg"
            val bucket = SupabaseClient.client.storage.from("fotos_gastos")

            bucket.upload(fileName, bytes) { upsert = true }
            bucket.publicUrl(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- FUNCIÓN PARA EL BOTÓN DE REFRESCAR Y FILTRO ---
    fun descargarDatosDeNube() {
        viewModelScope.launch {
            try {
                val emailActual = _emailUsuario.value
                if (emailActual.isEmpty()) return@launch

                // Filtro: Mis gastos O los que me compartieron
                val gastosNube = SupabaseClient.client.from("gastos").select {
                    filter {
                        or {
                            eq("email_creador", emailActual)
                            eq("email_compartido", emailActual)
                        }
                    }
                }.decodeList<Gasto>()

                gastosNube.forEach { gasto ->
                    dao.insertarGasto(gasto.copy(sincronizado = true))
                }

                Log.d("ViewModel", "Sincronizado: ${gastosNube.size} gastos")
                Toast.makeText(getApplication(), "Datos actualizados", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("ViewModel", "Error descargando: ${e.message}")
            }
        }
    }

    fun compartirGasto(gasto: Gasto, emailAmigo: String) {
        viewModelScope.launch {
            val gastoActualizado = gasto.copy(emailCompartido = emailAmigo, sincronizado = true)
            dao.insertarGasto(gastoActualizado)

            try {
                SupabaseClient.client.from("gastos").update(
                    { set("email_compartido", emailAmigo) }
                ) {
                    filter { eq("id", gasto.id) }
                }
                Toast.makeText(getApplication(), "Compartido con $emailAmigo", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("ViewModel", "Error compartiendo: ${e.message}")
            }
        }
    }

    fun eliminarGasto(gasto: Gasto) {
        viewModelScope.launch {
            dao.eliminarGasto(gasto)

            try {
                // 1. Borrar registro de la base de datos
                SupabaseClient.client.from("gastos").delete {
                    filter { eq("id", gasto.id) }
                }

                // 2. Borrar FOTO del bucket
                if (gasto.foto_url != null) {
                    val nombreArchivo = gasto.foto_url.substringAfterLast("/")
                    val bucket = SupabaseClient.client.storage.from("fotos_gastos")
                    bucket.delete(nombreArchivo)
                    Log.d("ViewModel", "Foto eliminada del bucket: $nombreArchivo")
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Error eliminando: ${e.message}")
            }
        }
    }

    fun iniciarSesionGoogle() {
        viewModelScope.launch { authManager.iniciarSesionGoogle() }
    }

    fun cerrarSesion() {
        viewModelScope.launch {
            authManager.cerrarSesion()
            _nombreUsuario.value = "Invitado"
            _emailUsuario.value = ""
            dao.borrarTodo() // Borra datos locales al salir para privacidad
        }
    }
}