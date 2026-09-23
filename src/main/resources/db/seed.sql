-- Начальные данные поликлиники. Выполняются только если таблицы пустые.

INSERT INTO departments (name) VALUES
    ('Педиатрическое отделение'),
    ('Кардиологическое отделение'),
    ('Терапевтическое отделение'),
    ('Неврологическое отделение')
ON CONFLICT (name) DO NOTHING;

INSERT INTO specialties (name) VALUES
    ('Педиатр'),
    ('Кардиолог'),
    ('Терапевт'),
    ('Невролог')
ON CONFLICT (name) DO NOTHING;

INSERT INTO doctors (department_id, specialty_id, full_name, room, description, experience_years)
SELECT d.id, s.id, v.full_name, v.room, v.description, v.experience_years
FROM (VALUES
    ('Педиатрическое отделение',   'Педиатр',   'Кузнецов Андрей Павлович',  '№ 214, 2-й этаж', 'Плановые осмотры и консультации детей', 8),
    ('Кардиологическое отделение', 'Кардиолог', 'Орлова Мария Николаевна',   '№ 305, 3-й этаж', 'Диагностика и лечение заболеваний сердца', 15),
    ('Терапевтическое отделение',  'Терапевт',  'Смирнова Елена Викторовна', '№ 108, 1-й этаж', 'Первичный прием и ведение пациентов', 12),
    ('Неврологическое отделение',  'Невролог',  'Ефимов Игорь Сергеевич',    '№ 401, 4-й этаж', 'Консультации при головных болях и нарушениях сна', 6)
) AS v(department, specialty, full_name, room, description, experience_years)
JOIN departments d ON d.name = v.department
JOIN specialties s ON s.name = v.specialty
WHERE NOT EXISTS (SELECT 1 FROM doctors WHERE doctors.full_name = v.full_name);

-- Демонстрационный пациент и отзывы, чтобы у врачей появился рейтинг
INSERT INTO patients (firebase_uid, full_name)
VALUES ('demo-seed-patient', 'Иванова Мария Петровна')
ON CONFLICT (firebase_uid) DO NOTHING;

INSERT INTO doctor_reviews (doctor_id, patient_id, rating, review_text)
SELECT d.id, p.id, v.rating, v.text
FROM (VALUES
    ('Кузнецов Андрей Павлович',  5, 'Внимательный врач, всё подробно объяснил'),
    ('Кузнецов Андрей Павлович',  4, 'Приём прошёл вовремя'),
    ('Орлова Мария Николаевна',   5, 'Очень грамотный специалист'),
    ('Смирнова Елена Викторовна', 4, 'Хороший терапевт')
) AS v(full_name, rating, text)
JOIN doctors d ON d.full_name = v.full_name
JOIN patients p ON p.firebase_uid = 'demo-seed-patient'
WHERE NOT EXISTS (
    SELECT 1 FROM doctor_reviews r
    WHERE r.doctor_id = d.id AND r.patient_id = p.id AND r.rating = v.rating
);
