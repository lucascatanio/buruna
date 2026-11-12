FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# binário do manga-downloader (ADR-54), fixado pela tag; o Dependabot abre PR a cada release
FROM elboletaire/manga-downloader:25.1.0 AS manga-downloader

FROM eclipse-temurin:21-jre-alpine AS runtime
# bsdtar (libarchive) extrai os CBR (RAR4 e RAR5) dos capítulos enviados (ADR-48)
RUN apk add --no-cache libarchive-tools
# o serviço usa o binário só para listar uma série na tela de import, sem navegador
COPY --from=manga-downloader /usr/bin/manga-downloader /usr/local/bin/manga-downloader
ENV MANGA_DOWNLOADER_NO_UPDATE_CHECK=1
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# Cloud Run Job de ingest (ADR-48, ADR-54): baixa pelo manga-downloader, inclusive os sites que
# exigem navegador. Chromium com tela virtual, como a imagem :browser do manga-downloader: o
# Cloudflare detecta o modo headless, e o Chromium com janela no Xvfb passa pelos desafios.
FROM runtime AS ingest
RUN apk add --no-cache chromium xvfb font-noto font-noto-cjk
# o Job roda como root, e o Chromium só sobe assim sem o sandbox; a CLI só o desliga sozinha
# quando acha /.dockerenv, que o Cloud Run não tem
ENV BROWSER_NO_SANDBOX=1
COPY ingest-entrypoint.sh /usr/local/bin/ingest-entrypoint.sh
RUN chmod +x /usr/local/bin/ingest-entrypoint.sh
ENTRYPOINT ["/usr/local/bin/ingest-entrypoint.sh"]

# serviço web: o alvo padrão, por ser o último
FROM runtime AS service
ENTRYPOINT ["java", "-jar", "app.jar"]
