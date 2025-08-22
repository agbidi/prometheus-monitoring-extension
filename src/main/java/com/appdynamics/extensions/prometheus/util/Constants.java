package com.appdynamics.extensions.prometheus.util;

public final class Constants {
    // Configuration: General constants
    public static final String MONITOR_NAME = "Prometheus Monitor";
    public static final String METRIC_PREFIX = "metricPrefix";
    public static final String DEFAULT_METRIC_PREFIX = "Custom Metrics|Prometheus";
    public static final String METRIC_SEPARATOR  = "|";
    public static final String SERVERS = "servers";
    public static final String PROMETHEUS_QUERY_PATH = "/api/v1/query?query=";
    

    // Configuration: Prometheus server constants
    public static final String URL = "url";
    public static final String AUTH_TOKEN = "authToken";
    public static final String METRICS = "metrics";

    // Configuration: Prometheus metric entry constants
    public static final String NAME = "name";
    public static final String VALUE  = "value";
    public static final String PROMQL = "promql";
    public static final String ALIAS = "alias";
    public static final String LABEL_FILTERS = "labelFilters";
    public static final String LABEL_FILTER_SEPARATOR  = "%";
    public static final String AGGREGATION_TYPE = "aggregationType";
    public static final String TIME_ROLL_UP_TYPE = "timeRollUpType";
    public static final String CLUSTER_ROLL_UP_TYPE = "clusterRollUpType";


    // Configuration: Metric writer constants
    public static final String AVERAGE = "AVERAGE";
    public static final String SUM = "SUM";
    public static final String OBSERVATION = "OBSERVATION";
    public static final String CURRENT = "CURRENT";
    public static final String INDIVIDUAL = "INDIVIDUAL";
    public static final String COLLECTIVE = "COLLECTIVE";
}
