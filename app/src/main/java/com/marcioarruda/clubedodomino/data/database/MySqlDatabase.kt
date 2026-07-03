package com.marcioarruda.clubedodomino.data.database

import com.marcioarruda.clubedodomino.BuildConfig
import com.mysql.jdbc.Driver
import java.sql.Connection
import java.util.Properties

object MySqlDatabase {

    private val jdbcUrl =
        "jdbc:mysql://${BuildConfig.DB_HOST}:${BuildConfig.DB_PORT}/${BuildConfig.DB_NAME}" +
        "?useSSL=false" +
        "&characterEncoding=UTF-8" +
        "&connectTimeout=10000" +
        "&socketTimeout=20000"

    private val driver by lazy { Driver() }

    fun connect(): Connection {
        val props = Properties().apply {
            setProperty("user", BuildConfig.DB_USER)
            setProperty("password", BuildConfig.DB_PASS)
        }
        val conn = driver.connect(jdbcUrl, props)
            ?: throw Exception("Driver retornou conexão nula para: $jdbcUrl")

        try {
            conn.createStatement().use { stmt ->
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS partidas_em_andamento (" +
                    "id VARCHAR(50) PRIMARY KEY, " +
                    "jogador1 VARCHAR(100) NOT NULL, " +
                    "jogador2 VARCHAR(100) NOT NULL, " +
                    "jogador3 VARCHAR(100) NOT NULL, " +
                    "jogador4 VARCHAR(100) NOT NULL, " +
                    "cadastrador VARCHAR(100) NOT NULL, " +
                    "data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            conn.createStatement().use { stmt ->
                stmt.execute(
                    "ALTER TABLE jogadores ADD COLUMN IF NOT EXISTS ativo TINYINT(1) NOT NULL DEFAULT 1"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            conn.createStatement().use { stmt ->
                stmt.execute(
                    "ALTER TABLE jogadores ADD COLUMN IF NOT EXISTS ferias TINYINT(1) NOT NULL DEFAULT 0"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return conn
    }
}
