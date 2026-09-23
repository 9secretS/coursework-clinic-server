// Плагин автоматически скачивает нужную версию JDK, если ее нет на машине.
// Благодаря этому проект собирается на любом компьютере без ручной установки Java 17.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "coursework-clinic-server"
