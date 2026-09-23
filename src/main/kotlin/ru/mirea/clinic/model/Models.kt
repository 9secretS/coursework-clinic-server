package ru.mirea.clinic.model

import kotlinx.serialization.Serializable

/**
 * Пациент приложения. Связывается с учетной записью Firebase по uid.
 */
@Serializable
data class Patient(
    val id: Int,
    val firebaseUid: String,
    val fullName: String,
    val phone: String? = null,
)

/**
 * Врач поликлиники. Стаж, рейтинг и количество отзывов вычисляются
 * сервером и передаются клиенту вместе с карточкой врача.
 */
@Serializable
data class Doctor(
    val id: Int,
    val fullName: String,
    val specialty: String,
    val department: String,
    val room: String? = null,
    val description: String? = null,
    val experienceYears: Int = 0,
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
)

/**
 * Временной слот расписания врача.
 */
@Serializable
data class Slot(
    val id: Int,
    val doctorId: Int,
    val startsAt: String,
    val endsAt: String,
    val status: String,
)

/**
 * Запись пациента на прием с вложенными данными врача и слота.
 */
@Serializable
data class Appointment(
    val id: Int,
    val doctor: Doctor,
    val slot: Slot,
    val status: String,
    val createdAt: String,
    val cancelledAt: String? = null,
)

/**
 * Отзыв пациента о враче.
 */
@Serializable
data class DoctorReview(
    val id: Int,
    val doctorId: Int,
    val patientName: String,
    val rating: Int,
    val text: String? = null,
    val createdAt: String,
)

/** Запрос на создание записи на прием. */
@Serializable
data class CreateAppointmentRequest(
    val slotId: Int,
)

/** Запрос на создание отзыва о враче. */
@Serializable
data class CreateDoctorReviewRequest(
    val rating: Int,
    val text: String? = null,
)

/** Запрос на регистрацию пациента в базе данных приложения. */
@Serializable
data class RegisterPatientRequest(
    val fullName: String,
    val phone: String? = null,
)

/** Ответ проверки работоспособности сервера. */
@Serializable
data class HealthResponse(
    val status: String,
    val database: String,
    val firebase: String,
)

/** Стандартный ответ с ошибкой. */
@Serializable
data class ErrorResponse(
    val message: String,
)
