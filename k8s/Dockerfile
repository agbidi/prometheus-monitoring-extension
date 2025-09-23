FROM openjdk:8u332-jdk AS installer

WORKDIR /opt/appdynamics

RUN apt-get update && apt-get install -y --no-install-recommends git maven zip

RUN git clone https://github.com/agbidi/prometheus-monitoring-extension.git

RUN cd prometheus-monitoring-extension && mvn clean install && unzip target/PrometheusMonitor-*.zip -d /opt/appdynamics/PrometheusMonitor

FROM appdynamics/machine-agent:25.7.0-alpine

LABEL name="AppDynamics Machine Agent + Prometheus Monitoring Extension" \
      vendor="AppDynamics" \
      version="25.7.0" \
      release="1" \
      url="https://www.appdynamics.com" \
      summary="AppDynamics solution for monitoring Prometheus in containerized environment" \
      description="AppDynamics solution for monitoring Prometheus in containerized environment"

COPY --from=installer /opt/appdynamics/PrometheusMonitor /opt/appdynamics/monitors

RUN ln -s $JAVA_HOME /opt/appdynamics/java