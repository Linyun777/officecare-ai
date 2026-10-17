FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY . .

RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 officecare \
    && useradd --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin officecare \
    && mkdir -p /app /data/documents /data/ruoyi-profile /home/ruoyi/logs \
    && chown -R officecare:officecare /app /data/documents /data/ruoyi-profile /home/ruoyi/logs

WORKDIR /app

COPY --from=builder --chown=officecare:officecare \
    /workspace/ruoyi-admin/target/ruoyi-admin.jar /app/officecare-server.jar

USER officecare

ENV JAVA_OPTS="-Xms256m -Xmx768m -Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/officecare-server.jar"]
