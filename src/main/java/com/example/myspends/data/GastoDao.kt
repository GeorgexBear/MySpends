package com.example.myspends.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GastoDao {
    // Inserta o actualiza si ya existe
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarGasto(gasto: Gasto)

    // Obtener todos ordenados por fecha (del más reciente al más viejo)
    @Query("SELECT * FROM gastos ORDER BY fecha DESC")
    fun obtenerGastos(): Flow<List<Gasto>>

    @Delete
    suspend fun eliminarGasto(gasto: Gasto)

    // Para calcular el total gastado [cite: 35]
    @Query("SELECT SUM(monto) FROM gastos")
    fun obtenerTotalGastado(): Flow<Double?>

    @Query("DELETE FROM gastos")
    suspend fun borrarTodo()
}