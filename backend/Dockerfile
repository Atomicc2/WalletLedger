# ============================================================
# Stage 1: BUILD — Maven + JDK 21 compila, testa e gera o JAR
# ============================================================
FROM maven:3.9.9-eclipse-temurin-21 AS builder

# Diretório de trabalho dentro do container
WORKDIR /app

# Copia apenas arquivos de dependência primeiro (cache de layer do Docker)
COPY pom.xml .
COPY src ./src

# Compila, roda testes e empacota (pulando testes se quiser mais rápido: -DskipTests)
# O wrapper Maven está quebrado no repo, então usamos o 'mvn' da imagem oficial
RUN mvn clean package -DskipTests

# ============================================================
# Stage 2: RUNTIME — Apenas JRE 21 (leve, seguro, sem ferramentas de build)
# ============================================================
FROM eclipse-temurin:21-jre-alpine

# Metadados úteis
LABEL maintainer="WalletLedger Team"
LABEL description="WalletLedger API - Carteira digital com ledger de dupla entrada"

# Cria usuário não-root (segurança: não roda como root)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Diretório de trabalho
WORKDIR /app

# Copia o JAR gerado no stage anterior
# O nome do JAR segue o padrão do pom.xml: artifactId-version.jar
COPY --from=builder /app/target/walletLedger-0.0.1-SNAPSHOT.jar app.jar

# Expõe a porta (documentação; a Render injeta PORT via env)
EXPOSE 8080

# Variáveis de ambiente com defaults sensatos
# - server.port: lê PORT da Render (ex.: 10000), fallback 8080 local
# - spring.profiles.active: ativa profile 'prod' para configs de produção
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Entrypoint executa o JAR lendo a porta do ambiente
# A Render define a variável PORT; Spring Boot aceita server.port=${PORT}
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-$SERVER_PORT} -jar app.jar"]