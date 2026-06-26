package com.marcioarruda.clubedodomino.data.database

import java.sql.Connection
import java.sql.DriverManager

object MySqlDatabase {
    private const val HOST = "easypanel.devlogconsultoria.com.br"
    private const val PORT = 3248
    private const val DB   = "domino"
    private const val USER = "root"
    private const val PASS = "973574GaB*@"

    private val jdbcUrl = "jdbc:mariadb://$HOST:$PORT/$DB?" +
        "serverTimezone=America/Recife&" +
        "characterEncoding=UTF-8&" +
        "connectTimeout=15000&" +
        "socketTimeout=30000&" +
        "autoReconnect=true"

    fun connect(): Connection {
        Class.forName("org.mariadb.jdbc.Driver")
        return DriverManager.getConnection(jdbcUrl, USER, PASS)
    }
}
