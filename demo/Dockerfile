# Cargador del conjunto DEMO: genera el SQL con fechas relativas a hoy y lo aplica en sofia_demo.
FROM python:3.12-alpine
RUN apk add --no-cache postgresql16-client
WORKDIR /demo
COPY demo_seed.py seed.sh ./
ENTRYPOINT ["sh","/demo/seed.sh"]
