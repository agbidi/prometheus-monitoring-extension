package com.appdynamics.extensions.prometheus;

import java.util.Map;

public class PrometheusMetric {
        private final String name;
        private final Map<String, String> labels;
        private final String value;

        public PrometheusMetric(String name, Map<String, String> labels, String value) {
            this.name = name;
            this.labels = labels;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "Metric{name='" + name + "', labels=" + labels + ", value='" + value + "'}";
        }
}
