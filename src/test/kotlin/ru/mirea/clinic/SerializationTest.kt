package ru.mirea.clinic

import kotlinx.serialization.json.Json
import ru.mirea.clinic.model.CreateDoctorReviewRequest
import ru.mirea.clinic.model.Doctor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Тестовые сценарии 6-7 (таблица Б.1): сериализация моделей сервера.
 */
class SerializationTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `doctor includes dynamic rating fields in json`() {
        val doctor = Doctor(
            id = 1,
            fullName = "Кузнецов Андрей Павлович",
            specialty = "Педиатр",
            department = "Педиатрическое отделение",
            room = "№ 214, 2-й этаж",
            description = "Плановые осмотры и консультации детей",
            experienceYears = 12,
            rating = 4.5,
            reviewCount = 2,
        )

        val encoded = json.encodeToString(Doctor.serializer(), doctor)
        val decoded = json.decodeFromString(Doctor.serializer(), encoded)

        assertEquals(12, decoded.experienceYears)
        assertEquals(4.5, decoded.rating)
        assertEquals(2, decoded.reviewCount)
        assertEquals(doctor, decoded)
    }

    @Test
    fun `create review request keeps rating and text`() {
        val request = CreateDoctorReviewRequest(rating = 5, text = "Отличный специалист")

        val encoded = json.encodeToString(CreateDoctorReviewRequest.serializer(), request)
        val decoded = json.decodeFromString(CreateDoctorReviewRequest.serializer(), encoded)

        assertEquals(5, decoded.rating)
        assertEquals("Отличный специалист", decoded.text)
    }
}
