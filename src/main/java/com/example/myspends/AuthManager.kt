package com.example.myspends

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

// --- ESTOS SON LOS IMPORTS QUE FALTABAN ---
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
// ------------------------------------------

class AuthManager(private val context: Context) {

    // TU CLIENT ID WEB (Asegúrate de que sea este el correcto)
    private val WEB_CLIENT_ID = "607536287692-701vkrjpl02q319l0ngags96h0taip40.apps.googleusercontent.com"

    suspend fun iniciarSesionGoogle(): Boolean {
        val credentialManager = CredentialManager.create(context)

        Log.d("AuthManager", "Iniciando solicitud de Google ID...")

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            val googleIdToken = when (credential) {
                is GoogleIdTokenCredential -> credential.idToken
                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        try {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            googleIdTokenCredential.idToken
                        } catch (e: GoogleIdTokenParsingException) {
                            Log.e("AuthManager", "Error parseando token: ${e.message}")
                            return false
                        }
                    } else {
                        Log.e("AuthManager", "Tipo de credencial desconocida: ${credential.type}")
                        return false
                    }
                }
                else -> {
                    Log.e("AuthManager", "Credencial no reconocida")
                    return false
                }
            }

            Log.d("AuthManager", "Token obtenido, enviando a Supabase...")

            // Ahora sí reconocerá '.auth', 'idToken' y 'provider' gracias a los imports de arriba
            SupabaseClient.client.auth.signInWith(IDToken) {
                idToken = googleIdToken
                provider = Google
            }

            Log.d("AuthManager", "Login en Supabase exitoso!")
            return true

        } catch (e: GetCredentialException) {
            Log.e("AuthManager", "Error obteniendo credencial: ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e("AuthManager", "Error general: ${e.message}")
            return false
        }
    }

    suspend fun cerrarSesion() {
        try {
            SupabaseClient.client.auth.signOut()
        } catch (e: Exception) {
            Log.e("AuthManager", "Error cerrando sesión: ${e.message}")
        }
    }

    fun obtenerUsuarioActual() = SupabaseClient.client.auth.currentUserOrNull()
}