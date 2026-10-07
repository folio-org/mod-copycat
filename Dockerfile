FROM docker.io/folioci/eclipse-temurin:25-alpine-dev AS native-build

USER root

WORKDIR /app

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

# Collect the JNI library and its transitive ELF dependencies, preserving paths.
# lddtree lists both SONAME symlinks and their targets; preserve the links.
RUN apk add --no-cache lddtree \
 && cp yaz4j/target/native/libyaz4j.so /usr/lib/ \
 && lddtree -l /usr/lib/libyaz4j.so > /tmp/native-libs \
 && while IFS= read -r lib; do \
      mkdir -p "/runtime$(dirname "$lib")"; \
      cp -P "$lib" "/runtime$lib" || exit 1; \
    done < /tmp/native-libs \
 && find /runtime -type f -exec strip --strip-unneeded {} +

FROM docker.io/folioci/eclipse-temurin:25-alpine

COPY --from=native-build /runtime/ /

COPY target/mod-copycat-fat.jar /usr/verticles/

EXPOSE 8081

CMD ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/usr/verticles/mod-copycat-fat.jar"]
