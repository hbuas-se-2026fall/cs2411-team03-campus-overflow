# 多阶段构建：第一阶段编译（利用依赖缓存层），第二阶段只保留 JRE 与 jar
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml ./pom.xml
COPY backend/ ./
# 产物名由 co-bootstrap/pom.xml 的 <finalName> 决定（当前为 campus-overflow.jar）。
# 这里显式复制成固定名 app.jar：若将来 finalName 改名，构建会立刻失败在 cp 这一步，
# 而不是像之前那样镜像构建"成功"、容器启动才报 Unable to access jarfile。
RUN mvn -B -DskipTests clean package \
 && cp /src/co-bootstrap/target/campus-overflow.jar /src/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && apk add --no-cache wget
WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -Duser.timezone=UTC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
