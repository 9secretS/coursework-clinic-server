package ru.mirea.clinic.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import ru.mirea.clinic.model.ErrorResponse

/**
 * Сериализация JSON, логирование запросов и обработка ошибок.
 */
fun Application.configurePlugins() {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Patient-Name")
    }

    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(cause.message ?: "Некорректный запрос")
            )
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled server error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Внутренняя ошибка сервера")
            )
        }
    }
}
