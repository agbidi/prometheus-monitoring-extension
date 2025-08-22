package com.appdynamics.extensions.prometheus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.prometheus.util.Constants;

public class PrometheusMonitor extends ABaseMonitor {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(PrometheusMonitor.class);
    private MonitorContextConfiguration monitorContextConfiguration;
    public Map<String, ?> configYml = new HashMap<>();

     //used by Initialize as part of execute
    @Override
    protected void initializeMoreStuff(Map<String, String> args) {
        this.monitorContextConfiguration = getContextConfiguration();
        this.configYml = monitorContextConfiguration.getConfigYml();
    }

    @Override
    public String getDefaultMetricPrefix() {
        return Constants.DEFAULT_METRIC_PREFIX;
    }

    @Override
    public String getMonitorName() {
        return Constants.MONITOR_NAME;
    }

    @Override
    protected void doRun(TasksExecutionServiceProvider tasksExecutionServiceProvider) {
        List<Map<String, ?>> servers = (List<Map<String, ?>>) this.configYml.get(Constants.SERVERS);
        for (Map<String, ?> server : servers) {
            String serverUrl = (String) server.get(Constants.URL);
            logger.info("Running PromQL queries on server: " + serverUrl);
            List<Map<String, ?>> metrics = (List<Map<String, ?>>) server.get(Constants.METRICS);
            for (Map<String, ?> metric : metrics) {
                String metricName = (String) metric.get(Constants.NAME);
                logger.debug("Creating Task for Metric: " + metricName);
                PrometheusMonitorTask task = new PrometheusMonitorTask(tasksExecutionServiceProvider, this.configYml, server, metric);
                String taskName = metricName + "(" + serverUrl + ")";
                tasksExecutionServiceProvider.submit(taskName, task);
            }
        }
    }

  @Override
    protected List<Map<String, ?>> getServers () {
        return (List<Map<String, ?>>) getContextConfiguration().
                getConfigYml().get(Constants.SERVERS);
    }
}