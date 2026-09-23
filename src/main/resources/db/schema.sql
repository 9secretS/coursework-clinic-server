-- Логическая модель базы данных приложения (рисунок 2.5, таблица 2.4)

CREATE TABLE IF NOT EXISTS departments (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS specialties (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS patients (
    id           SERIAL PRIMARY KEY,
    firebase_uid VARCHAR(128) NOT NULL UNIQUE,
    full_name    VARCHAR(255) NOT NULL,
    phone        VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS doctors (
    id               SERIAL PRIMARY KEY,
    department_id    INTEGER NOT NULL REFERENCES departments (id),
    specialty_id     INTEGER NOT NULL REFERENCES specialties (id),
    full_name        VARCHAR(255) NOT NULL,
    room             VARCHAR(64),
    description      TEXT,
    experience_years INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS schedule_slots (
    id        SERIAL PRIMARY KEY,
    doctor_id INTEGER NOT NULL REFERENCES doctors (id) ON DELETE CASCADE,
    starts_at TIMESTAMP NOT NULL,
    ends_at   TIMESTAMP NOT NULL,
    status    VARCHAR(16) NOT NULL DEFAULT 'FREE',
    CONSTRAINT schedule_slots_unique UNIQUE (doctor_id, starts_at)
);

CREATE TABLE IF NOT EXISTS appointments (
    id           SERIAL PRIMARY KEY,
    patient_id   INTEGER NOT NULL REFERENCES patients (id) ON DELETE CASCADE,
    slot_id      INTEGER NOT NULL REFERENCES schedule_slots (id) ON DELETE CASCADE,
    status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    cancelled_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS doctor_reviews (
    id          SERIAL PRIMARY KEY,
    doctor_id   INTEGER NOT NULL REFERENCES doctors (id) ON DELETE CASCADE,
    patient_id  INTEGER NOT NULL REFERENCES patients (id) ON DELETE CASCADE,
    rating      INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_text TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_doctors_department ON doctors (department_id);
CREATE INDEX IF NOT EXISTS idx_doctors_specialty ON doctors (specialty_id);
CREATE INDEX IF NOT EXISTS idx_slots_doctor ON schedule_slots (doctor_id);
CREATE INDEX IF NOT EXISTS idx_appointments_patient ON appointments (patient_id);
CREATE INDEX IF NOT EXISTS idx_reviews_doctor ON doctor_reviews (doctor_id);
