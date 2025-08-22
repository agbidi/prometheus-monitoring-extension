package com.appdynamics.extensions.prometheus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.prometheus.util.Constants;
import com.appdynamics.extensions.util.MetricPathUtils;



public class PrometheusMonitorTask implements AMonitorTaskRunnable {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(PrometheusMonitorTask.class);
    private final Map<String, ?> config;
    private final Map<String, ?> metricConfig;
    private final MetricWriteHelper metricWriteHelper;
    private final String metricName;
    private final PrometheusClient prometheusClient;

     PrometheusMonitorTask (TasksExecutionServiceProvider serviceProvider, Map<String, ?> config, Map<String, ?> serverConfig, Map<String, ?> metricConfig) {
        this.prometheusClient = new PrometheusClient((String)serverConfig.get(Constants.URL), (String)serverConfig.get(Constants.AUTH_TOKEN));
        this.metricConfig = metricConfig;
        this.metricWriteHelper = serviceProvider.getMetricWriteHelper();
        this.metricName = (String)this.metricConfig.get(Constants.NAME);
        this.config = config;
    }

    @Override
    public void run() {
        String promql = null;
        try {
            PrometheusClient promClient = getPrometheusClient();
            promql = (String) metricConfig.get(Constants.PROMQL);
            String metricPrefix = (String) config.get(Constants.METRIC_PREFIX);
            String alias = (String) metricConfig.get(Constants.ALIAS);
            String aggregationType = (String) metricConfig.get(Constants.AGGREGATION_TYPE);
            String timeRollUpType = (String) metricConfig.get(Constants.TIME_ROLL_UP_TYPE);
            String clusterRollUpType = (String) metricConfig.get(Constants.CLUSTER_ROLL_UP_TYPE);
            List<Map<String, String>> labelFilters = (List<Map<String, String>>) metricConfig.get(Constants.LABEL_FILTERS);
            List<PrometheusMetric> results;
            List<Metric> metrics = new ArrayList<>();

            promql = filterReplace(promql, labelFilters);
            results = promClient.query(promql);
            
            for (PrometheusMetric result : results) {
                List<String> metricPathTokens = getMetricPathTokens(result, labelFilters);
                String metricPath = MetricPathUtils.buildMetricPath(metricPrefix, metricPathTokens.toArray(new String[0]));
                Metric metric = new Metric(alias, result.getValue(), metricPath + Constants.METRIC_SEPARATOR + alias, aggregationType, timeRollUpType, clusterRollUpType);
                metrics.add(metric);
            }
            if (!metrics.isEmpty()) {
                metricWriteHelper.printMetric(metrics);
            }
        } catch (Exception e) {
            // Log or handle exceptions as needed
            logger.error(String.format("Exception caught for metric %s: %s: %s", this.metricName, promql, e));
        }
    }

    @Override
    public void onTaskComplete() {
        logger.info("All tasks for metric {} finished", this.metricName);
    }

    
    protected PrometheusClient getPrometheusClient() {
        return prometheusClient;
    }

    protected String filterReplace(String query, List<Map<String, String>> filters) {

        if (filters == null) {
            return query;
        }

        String res = query;
        String filterVar;
        for (Map<String, String> filter : filters) {
            filterVar = Constants.LABEL_FILTER_SEPARATOR + filter.get(Constants.NAME) + Constants.LABEL_FILTER_SEPARATOR; //%label%
            if (query.contains(filterVar)) {
                res = res.replace(filterVar, filter.get(Constants.VALUE));
            }
        }
        return res;
    }

    protected List<String> getMetricPathTokens(PrometheusMetric metric, List<Map<String, String>> filters) throws  IllegalArgumentException {

        if (filters == null) {
            return new ArrayList<>();
        }
        ArrayList<String> res = new ArrayList<>();
        String labelName;
        String labelValue;
        for (Map<String, String> filter : filters) {
            labelName = filter.get(Constants.NAME);
            labelValue = metric.getLabels().get(labelName);
            if (labelValue != null) {
                res.add(labelName);
                res.add(labelValue);
            } else {
                throw new IllegalArgumentException(String.format("Label filter %s defined but not found in labels for metric %s", labelName, metric.getName()));
            }
        }
        return res;
    }

}