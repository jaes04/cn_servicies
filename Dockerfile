# --- Stage 1: dependency cache ---
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q

# --- Stage 2: build ---
FROM deps AS build
COPY src ./src
RUN mvn package -DskipTests -q

# --- Stage 3: runtime ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

# El volumen de imagenes se monta aqui. Si el directorio no existe en la imagen,
# Docker lo crea como root y appuser no puede escribir: toda subida da 500.
# Creandolo con su dueno, un volumen nuevo hereda ese dueno al montarse.
RUN mkdir -p /app/uploads && chown appuser:appgroup /app/uploads

COPY --from=build /build/target/*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "app.jar"]