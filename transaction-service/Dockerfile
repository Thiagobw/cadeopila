# ══════════════════════════════════════════════════════════════════════
# Stage 1 — Build: Maven + JDK 25
# ══════════════════════════════════════════════════════════════════════
FROM maven:3.9-eclipse-temurin-25-alpine AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

COPY src ./src
RUN mvn package -DskipTests -B --no-transfer-progress

# ══════════════════════════════════════════════════════════════════════
# Stage 2 — Runtime: JRE 25 Alpine (imagem mínima, usuário não-root)
# ══════════════════════════════════════════════════════════════════════
FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT [ \
  "java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar" \
]
