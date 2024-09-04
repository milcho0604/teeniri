# 멀티 스테이지 빌드 방법 사용
# 첫번째 스테이지##
FROM openjdk:11 as stage1
WORKDIR /app

# /app/gradelw 파일로 생성
COPY gradlew .
# /app/gradle 폴더로 생성
COPY gradle gradle
COPY src src
COPY build.gradle .
COPY settings.gradle .

RUN chmod 777 gradlew
RUN ./gradlew bootJar

# 두번째 스테이지
FROM openjdk:11
WORKDIR /app
# stage1에 있는 jar를 stage2의 app.jar라는 이름으로 copy
COPY --from=stage1 /app/build/libs/*.jar app.jar

# CMD 또는 ENTRYPOINT를 통해 컨테이너를 실행
ENTRYPOINT ["java", "-jar", "app.jar"]


# docker build -t ordersystem:latest .

# docker 컨테이너 내에서 밖의 전체 host를 지칭하는 도메인: host.docker.internal
# docker run -d -p 8080:8080 -e SPRING_DATASOURCE_URL=jdbc:mariadb://host.docker.internal:3307/ordersystem ordersystem:latest

# docker run -d -p 8080:8080 -e SPRING_DATASOURCE_URL=jdbc:mariadb://host.docker.internal:3307/order_system hannah/ordersystem:latest

# docker컨테이너 실행시에 볼륨을 설정할 때에는 -v 옵션 사용
# docker run -d -p 8081:8080 -e SPRING_DATASOURCE_URL=jdbc:mariadb://host.docker.internal:3307/board -v aaaa:bbbb spring_test:latest
# docker run -d -p 8081:8080 -e SPRING_DATASOURCE_URL=jdbc:mariadb://host.docker.internal:3307/board -v /Users/hannah/etc:/tmp/logs spring_test:latest


