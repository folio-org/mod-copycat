FROM folioci/alpine-jre-openjdk11:latest

USER root

RUN apk add --no-cache swig openjdk11 maven \
	bison gnutls-dev libxslt-dev libxml2-dev make build-base git

# Compile yaz (there's no apk package for it)
USER folio
RUN curl -s http://ftp.indexdata.dk/pub/yaz/yaz-5.30.3.tar.gz |tar xzf -
RUN cd yaz-5.30.3 && ./configure --prefix=/usr --disable-static --enable-shared && make

# Install yaz
USER root
RUN cd yaz-5.30.3 && make install

# Compile yaz4j
USER folio
RUN git clone https://github.com/indexdata/yaz4j.git
RUN cd yaz4j && git checkout v1.6.0 && mvn compile

# install yaz4j
USER root
RUN cp yaz4j/target/native/libyaz4j.so /usr/lib/

ENV VERTICLE_FILE mod-copycat-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

# Copy your fat jar to the container
COPY target/${VERTICLE_FILE} ${VERTICLE_HOME}/${VERTICLE_FILE}

# Expose this port locally in the container.
EXPOSE 8081
