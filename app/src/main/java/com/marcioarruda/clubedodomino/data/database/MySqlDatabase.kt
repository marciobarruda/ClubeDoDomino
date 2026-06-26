package com.marcioarruda.clubedodomino.data.database

import com.marcioarruda.clubedodomino.BuildConfig
import java.sql.Connection
import java.sql.DriverManager

object MySqlDatabase {

    private val jdbcUrl = "jdbc:mariadb://${BuildConfig.DB_HOST}:${BuildConfig.DB_PORT}/${BuildConfig.DB_NAME}?" +
        "serverTimezone=America/Recife&" +
        "characterEncoding=UTF-8&" +
        "connectTimeout=15000&" +
        "socketTimeout=30000&" +
        "autoReconnect=true"

    fun connect(): Connection {
        Class.forName("org.mariadb.jdbc.Driver")
        return DriverManager.getConnection(jdbcUrl, BuildConfig.DB_USER, BuildConfig.DB_PASS)
    }
}
