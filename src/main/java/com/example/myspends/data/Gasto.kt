package com.example.myspends.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

@Serializable
@Entity(tableName = "gastos")
data class Gasto(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val monto: Double,
    val descripcion: String,
    val fecha: Long = System.currentTimeMillis(),

    @Transient val rutaFotoLocal: String? = null,

    @SerialName("foto_url") val foto_url: String? = null,
    @SerialName("email_compartido") val emailCompartido: String? = null,
    @SerialName("user_id") val usuarioId: String? = null,
    @SerialName("email_creador") val email_creador: String? = null,

    // 👇 NUEVO CAMPO: NOMBRE REAL DE GOOGLE 👇
    @SerialName("nombre_creador") val nombreCreador: String? = null,

    @Transient val sincronizado: Boolean = false
)