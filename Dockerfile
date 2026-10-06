FROM docker.io/folioci/eclipse-temurin:25-alpine-dev AS native-build

USER root
# Replace the development image's JRE with the JDK for javac and JNI headers.
RUN apk upgrade \
 && apk del eclipse-temurin-25-jre-oci-config eclipse-temurin-25-jre \
 && apk add \
      swig eclipse-temurin-25-jdk maven \
      bison gnutls-dev libxslt-dev libxml2-dev make build-base git \
 && rm -rf /var/cache/apk/*

# Compile yaz (there's no apk package for it)
RUN wget -O- https://ftp.indexdata.com/pub/yaz/yaz-5.37.0.tar.gz |tar xzf -
RUN cd yaz-5.37.0 && ./configure --prefix=/usr --disable-static --enable-shared && make

# Install yaz
RUN cd yaz-5.37.0 && make install

# Compile yaz4j
RUN git clone https://github.com/indexdata/yaz4j.git
RUN cd yaz4j && git checkout v1.6.0 && mvn compile

# Stage only the native libraries and their runtime dependencies.
# BusyBox supplies the startup shell and wget for the CI health check.
RUN apk --no-cache --root /runtime --initdb --no-scripts \
      --repositories-file /etc/apk/repositories --keys-dir /etc/apk/keys add \
      busybox gnutls libxslt libstdc++ \
 && cp -a /usr/lib/libyaz.so* /runtime/usr/lib/ \
 && cp yaz4j/target/native/libyaz4j.so /runtime/usr/lib/ \
 && ln -s busybox /runtime/bin/sh \
 && ln -s /bin/busybox /runtime/usr/bin/wget

FROM docker.io/folioci/eclipse-temurin:25-alpine

COPY --from=native-build /runtime/lib/ /lib/
COPY --from=native-build /runtime/usr/lib/ /usr/lib/
COPY --from=native-build /runtime/bin/ /bin/
COPY --from=native-build /runtime/usr/bin/wget /usr/bin/wget

ENV VERTICLE_FILE=mod-copycat-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME=/usr/verticles

# Copy your fat jar to the container
COPY target/${VERTICLE_FILE} ${VERTICLE_HOME}/${VERTICLE_FILE}

# Expose this port locally in the container.
EXPOSE 8081

ENTRYPOINT ["/bin/sh", "-c", "exec java --enable-native-access=ALL-UNNAMED $JAVA_OPTIONS -jar ${VERTICLE_HOME}/${VERTICLE_FILE} \"$@\"", "--"]
CMD []
