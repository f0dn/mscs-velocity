plugins {
    application
}

repositories {
    maven(url = "https://repo.papermc.io/repository/maven-public/") {
      name = "papermc"
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:4.2.1-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:4.2.1-SNAPSHOT")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.flintmueller.Main"
}
