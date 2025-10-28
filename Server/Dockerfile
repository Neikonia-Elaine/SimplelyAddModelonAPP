FROM eclipse-temurin:21-jre

WORKDIR /app

# 复制构建好的 jar 文件
COPY build/libs/*.jar app.jar

# 暴露端口
EXPOSE 8080

# 运行应用
ENTRYPOINT ["java", "-jar", "app.jar"]
