#!/bin/sh
# Entrada do Cloud Run Job de ingest (ADR-54): sobe a tela virtual para o Chromium que o
# manga-downloader abre nos sites com Cloudflare, e só então a JVM, que herda o DISPLAY.
set -e

Xvfb :99 -screen 0 1920x1080x24 -nolisten tcp -ac &
export DISPLAY=:99

# espera o socket do X, para o primeiro download não chegar antes da tela
i=0
while [ ! -S /tmp/.X11-unix/X99 ] && [ "$i" -lt 50 ]; do
    sleep 0.1
    i=$((i + 1))
done

exec java -jar /app/app.jar "$@"
