package com.marcioarruda.clubedodomino.data.database

import com.marcioarruda.clubedodomino.BuildConfig
import java.sql.Connection
import java.sql.DriverManager

object MySqlDatabase {

    private val jdbcUrl =
        "jdbc:mariadb://${BuildConfig.DB_HOST}:${BuildConfig.DB_PORT}/${BuildConfig.DB_NAME}" +
        "?useSSL=false" +
        "&characterEncoding=UTF-8" +
        "&serverTimezone=America/Recife" +
        "&connectTimeout=10000" +
        "&socketTimeout=20000"

    fun connect(): Connection {
        Class.forName("org.mariadb.jdbc.Driver")
        return DriverManager.getConnection(jdbcUrl, BuildConfig.DB_USER, BuildConfig.DB_PASS)
    }
}
