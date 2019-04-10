package com.example

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.html.*
import kotlinx.html.*
import kotlinx.css.*
import freemarker.cache.*
import io.ktor.freemarker.*
import io.ktor.content.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.sessions.*
import io.ktor.features.*
import org.slf4j.event.*
import io.ktor.util.date.*
import io.ktor.webjars.*
import java.time.*
import io.ktor.server.engine.*
import io.ktor.websocket.*
import io.ktor.http.cio.websocket.*
import io.ktor.auth.*
import io.ktor.client.features.auth.basic.*
import io.ktor.util.*
import io.ktor.gson.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.jackson.*
import java.io.*
import java.util.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.util.*
import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import io.ktor.network.tls.*
import kotlinx.io.core.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.engine.jetty.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import java.net.URL
import io.ktor.client.engine.cio.*
import io.ktor.client.features.websocket.*
import io.ktor.client.features.websocket.WebSockets
import io.ktor.http.cio.websocket.Frame
import kotlinx.coroutines.channels.*
import io.ktor.client.features.logging.*
import io.ktor.client.features.UserAgent
import io.ktor.client.features.BrowserUserAgent

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    val googleOauthProvider = OAuthServerSettings.OAuth2ServerSettings(
        name = "google",
        authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
        accessTokenUrl = "https://oauth2.googleapis.com/token",
        requestMethod = HttpMethod.Post,

        clientId = "231213861237-f9lo08tb62ssia32sbvblcm3uvobjb5t.apps.googleusercontent.com",
        clientSecret = "QKVftLfCopzb2kT7QMmLCryl",
        defaultScopes = listOf("profile") // no email, but gives full name, picture, and id
    )

    install(Locations) {
    }

    install(Authentication) {
        fun ApplicationCall.redirectUrl(path: String): String {
            val defaultPort = if (request.origin.scheme == "http") 80 else 443
            val hostPort = request.host()!! + request.port().let { port -> if (port == defaultPort) "" else ":$port" }
            val protocol = request.origin.scheme
            return "$protocol://$hostPort$path"
        }

        oauth("google-oauth") {
            client = HttpClient(Apache)
            providerLookup = { googleOauthProvider }
            urlProvider = { redirectUrl("/login") }
        }


    }

    class MySession(val userId: String)

    install(Sessions) {
        cookie<MySession>("oauthSampleSessionId") {
            val secretSignKey = hex("000102030405060708090a0b0c0d0e0f") // @TODO: Remember to change this!
            transform(SessionTransportTransformerMessageAuthentication(secretSignKey))
        }
    }

    routing {
        get("/") {
            val session = call.sessions.get<MySession>()
            call.respondText("HI ${session?.userId}")
        }

        authenticate("google-oauth") {
            route("/login") {
                handle {
                    val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()
                        ?: error("No principal")

                    val json = HttpClient(Apache).get<String>("https://www.googleapis.com/userinfo/v2/me") {
                        header("Authorization", "Bearer ${principal.accessToken}")
                    }

                    val data = ObjectMapper().readValue<Map<String, Any?>>(json)
                    val id = data["id"] as String?

                    if (id != null) {
                        call.sessions.set(MySession(id))
                    }
                    call.respondRedirect("/")
                }
            }
        }
    }


}

