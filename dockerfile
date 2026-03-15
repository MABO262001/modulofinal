# Usamos una imagen base ligera de Java 17 (o la versión que uses)
FROM eclipse-temurin:17-jre-focal

# Directorio de trabajo
WORKDIR /app

# Copiamos el archivo JAR generado por Maven desde la carpeta target
COPY target/*.jar app.jar

# Exponemos el puerto 8080
EXPOSE 8080

# Comando para iniciar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]

#verficacion del deploy
# se me olvido guardar 
