package com.papsign.ktor.openapigen.examples

import com.papsign.ktor.openapigen.auth.JwtTestApplication.testServerWithJwtAuth
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

object JwtExample {
    @JvmStatic
    fun main(
        args: Array<String>,
    ) {
        embeddedServer(Netty, 8080, "localhost") { testServerWithJwtAuth() }.start(true)
    }
}
