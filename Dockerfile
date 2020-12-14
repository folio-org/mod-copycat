FROM folioci/alpine-jre-openjdk11:latest

USER root

RUN apk add --no-cache swig openjdk11 maven \
	bison gnutls-dev libxslt-dev libxml2-dev make build-base git

USER folio
RUN curl -s http://ftp.indexdata.dk/pub/yaz/yaz-5.30.3.tar.gz |tar xzf -
RUN cd yaz-5.30.3 && ./configure --disable-static --enable-shared && make

USER root
RUN cd yaz-5.30.3 && make install

USER folio
RUN git clone https://github.com/indexdata/yaz4j.git
RUN cd yaz4j && git checkout d7cd6967d297c92c179d9896b0150f7509f789f8 && mvn compile

USER root
RUN cp yaz4j/unix/target/libyaz4j.so /usr/lib/

ENV VERTICLE_FILE mod-copycat-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

# Copy your fat jar to the container
COPY target/${VERTICLE_FILE} ${VERTICLE_HOME}/${VERTICLE_FILE}

# Expose this port locally in the container.
EXPOSE 8081
