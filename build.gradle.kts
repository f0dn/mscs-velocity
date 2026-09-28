repositories {
  maven(url = "https://repo.papermc.io/repository/maven-public/") {
    name = "papermc"
  }
}

dependencies {
  compileOnly("com.velocitypowered:velocity-api:4.2.1-SNAPSHOT")
  // If you want your velocity-plugin.json file to be generated based on plugin annotations
  annotationProcessor("com.velocitypowered:velocity-api:4.2.1-SNAPSHOT")
}
