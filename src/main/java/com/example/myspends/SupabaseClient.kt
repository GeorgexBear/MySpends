package com.example.myspends

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

object SupabaseClient {
    private const val SUPABASE_URL = "https://kdbtehfqhcnykdlrrjuy.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtkYnRlaGZxaGNueWtkbHJyanV5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjU3NzgxODMsImV4cCI6MjA4MTM1NDE4M30.x9Fy1ghwXRFrYAbRmoHub1hkrwcYpNfp7xzQVfWUT9k"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Auth)
        install(Storage)
        install(Postgrest) {
            // --- ESTO ES LO NUEVO Y VITAL ---
            serializer = KotlinXSerializer(Json {
                ignoreUnknownKeys = true // Si hay columnas extra, no explotes
                encodeDefaults = true    // Usa valores por defecto si falta algo
            })
        }
    }
}