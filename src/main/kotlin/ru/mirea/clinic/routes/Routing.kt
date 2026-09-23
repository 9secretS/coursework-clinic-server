package ru.mirea.clinic.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import ru.mirea.clinic.db.DatabaseFactory
import ru.mirea.clinic.model.CreateAppointmentRequest
import ru.mirea.clinic.model.CreateDoctorReviewRequest
import ru.mirea.clinic.model.ErrorResponse
import ru.mirea.clinic.model.HealthResponse
import ru.mirea.clinic.model.Patient
import ru.mirea.clinic.model.RegisterPatientRequest
import ru.mirea.clinic.repository.AppointmentRepository
import ru.mirea.clinic.repository.BookingResult
import ru.mirea.clinic.repository.DoctorRepository
import ru.mirea.clinic.repository.PatientRepository
import ru.mirea.clinic.repository.ReviewRepository
import ru.mirea.clinic.security.AuthenticatedUser
import ru.mirea.clinic.security.FirebaseTokenVerifier
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Основные маршруты REST API сервера (таблица 3.2).
 */
fun Application.configureRouting(
    databaseFactory: DatabaseFactory,
    verifier: FirebaseTokenVerifier,
) {
    val dataSource = databaseFactory.dataSource
    val doctors = DoctorRepository(dataSource)
    val patients = PatientRepository(dataSource)
    val appointments = AppointmentRepository(dataSource)
    val reviews = ReviewRepository(dataSource)

    routing {
        // Проверка работоспособности сервера
        get("/health") {
            call.respond(
                HealthResponse(
                    status = "UP",
                    database = if (databaseFactory.isAvailable()) "UP" else "DOWN",
                    firebase = if (verifier.isConfigured) "CONFIGURED" else "DEMO",
                )
            )
        }

        // Получение списка врачей
        get("/doctors") {
            call.respond(doctors.findAll())
        }

        // Поиск врачей по фамилии или специальности
        get("/doctors/search") {
            val query = call.request.queryParameters["q"].orEmpty()
            if (query.isBlank()) {
                call.respond(doctors.findAll())
            } else {
                call.respond(doctors.search(query))
            }
        }

        // Получение карточки врача
        get("/doctors/{id}") {
            val id = call.intParameter("id") ?: return@get call.badRequest("Некорректный идентификатор врача")
            val doctor = doctors.findById(id)
            if (doctor == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Врач не найден"))
            } else {
                call.respond(doctor)
            }
        }

        // Получение свободных слотов врача
        get("/doctors/{id}/slots") {
            val id = call.intParameter("id") ?: return@get call.badRequest("Некорректный идентификатор врача")
            call.respond(doctors.findFreeSlots(id))
        }

        // Отзывы о враче
        get("/doctors/{id}/reviews") {
            val id = call.intParameter("id") ?: return@get call.badRequest("Некорректный идентификатор врача")
            call.respond(reviews.findByDoctor(id))
        }

        post("/doctors/{id}/reviews") {
            val id = call.intParameter("id") ?: return@post call.badRequest("Некорректный идентификатор врача")
            val patient = call.requirePatient(verifier, patients) ?: return@post
            val request = call.receive<CreateDoctorReviewRequest>()
            if (request.rating !in 1..5) {
                return@post call.badRequest("Оценка должна быть от 1 до 5")
            }
            call.respond(HttpStatusCode.Created, reviews.create(id, patient.id, request.rating, request.text))
        }

        // Регистрация пациента в базе данных приложения
        post("/patients") {
            val user = call.authenticate(verifier) ?: return@post call.unauthorized()
            val request = call.receive<RegisterPatientRequest>()
            val patient = patients.findOrCreate(user.uid, request.fullName, request.phone)
            call.respond(HttpStatusCode.Created, patient)
        }

        // Профиль текущего пациента
        get("/patients/me") {
            val patient = call.requirePatient(verifier, patients) ?: return@get
            call.respond(patient)
        }

        // Создание записи на прием
        post("/appointments") {
            val patient = call.requirePatient(verifier, patients) ?: return@post
            val request = call.receive<CreateAppointmentRequest>()
            when (val result = appointments.book(patient.id, request.slotId)) {
                is BookingResult.Success ->
                    call.respond(HttpStatusCode.Created, result.appointment)

                BookingResult.SlotNotFound ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Выбранный слот не найден"))

                BookingResult.SlotAlreadyBooked ->
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("Выбранное время уже занято"))
            }
        }

        // Получение записей пациента
        get("/appointments/my") {
            val patient = call.requirePatient(verifier, patients) ?: return@get
            call.respond(appointments.findByPatient(patient.id))
        }

        // Отмена записи
        delete("/appointments/{id}") {
            val patient = call.requirePatient(verifier, patients) ?: return@delete
            val id = call.intParameter("id") ?: return@delete call.badRequest("Некорректный идентификатор записи")
            if (appointments.cancel(patient.id, id)) {
                call.respond(HttpStatusCode.OK, ErrorResponse("Запись отменена"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Активная запись не найдена"))
            }
        }
    }
}

private fun ApplicationCall.intParameter(name: String): Int? = parameters[name]?.toIntOrNull()

private suspend fun ApplicationCall.badRequest(message: String) =
    respond(HttpStatusCode.BadRequest, ErrorResponse(message))

private suspend fun ApplicationCall.unauthorized() =
    respond(HttpStatusCode.Unauthorized, ErrorResponse("Требуется авторизация"))

/**
 * Извлекает токен Firebase из заголовка Authorization и проверяет его.
 */
private fun ApplicationCall.authenticate(verifier: FirebaseTokenVerifier): AuthenticatedUser? {
    val header = request.headers["Authorization"] ?: return null
    val token = header.removePrefix("Bearer ").trim()
    return verifier.verify(token)
}

/**
 * Возвращает пациента, соответствующего авторизованному пользователю,
 * создавая его при первом обращении.
 */
private suspend fun ApplicationCall.requirePatient(
    verifier: FirebaseTokenVerifier,
    patients: PatientRepository,
): Patient? {
    val user = authenticate(verifier)
    if (user == null) {
        unauthorized()
        return null
    }
    val displayName = request.headers["X-Patient-Name"]?.let { header ->
        runCatching { URLDecoder.decode(header, StandardCharsets.UTF_8) }.getOrDefault(header)
    }
        ?: user.displayName
        ?: user.email
        ?: "Пациент"
    return patients.findOrCreate(user.uid, displayName)
}
