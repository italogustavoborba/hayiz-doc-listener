FROM openjdk:8-jdk-alpine

ARG JAR_FILE
COPY ./build/libs/* ./app.jar

RUN apk add -X https://dl-cdn.alpinelinux.org/alpine/latest-stable/main -u alpine-keys --allow-untrusted

RUN echo "https://dl-cdn.alpinelinux.org/alpine/latest-stable/main" > /etc/apk/repositories \
    && echo "https://dl-cdn.alpinelinux.org/alpine/latest-stable/community" >> /etc/apk/repositories \
    && echo "https://dl-cdn.alpinelinux.org/alpine/latest-stable/testing" >> /etc/apk/repositories \
    && echo "https://dl-cdn.alpinelinux.org/alpine/latest-stable/main" >> /etc/apk/repositories \
    && apk upgrade -U -a \
    && apk add --no-cache --allow-untrusted \
    chromium \
    && rm -rf /var/cache/* \
    && mkdir /var/cache/apk

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]