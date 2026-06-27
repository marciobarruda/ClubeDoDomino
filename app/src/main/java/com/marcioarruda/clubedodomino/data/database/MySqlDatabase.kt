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
        return driver.connect(jdbcUrl, props)
            ?: throw Exception("Driver retornou conexão nula para: $jdbcUrl")
    }
}
