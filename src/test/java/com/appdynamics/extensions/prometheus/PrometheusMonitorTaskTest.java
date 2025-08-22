// src/test/java/com/appdynamics/monitors/prometheus/PrometheusMonitorTaskTest.java
package com.appdynamics.extensions.prometheus;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorContext;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.metrics.MetricCharSequenceReplacer;
import com.appdynamics.extensions.prometheus.util.Constants;
import com.appdynamics.extensions.util.MetricPathUtils;


public class PrometheusMonitorTaskTest {

    @Mock
    private TasksExecutionServiceProvider serviceProvider;
    @Mock
    private MetricWriteHelper metricWriteHelper;
    @Mock
    private PrometheusClient prometheusClient; 
    @Mock
    private MonitorContext monitorContext;
    @Mock
    private MetricCharSequenceReplacer metricCharSequenceReplacer;
    
    private Map<String, Object> config;
    private Map<String, Object> metricConfig;
    private Map<String, Object> serverConfig;

    private PrometheusMonitorTask task;

    @Before
    public void setUp() throws Exception { // Added throws Exception for reflection
        MockitoAnnotations.initMocks(this);

        when(serviceProvider.getMetricWriteHelper()).thenReturn(metricWriteHelper);

        config = new HashMap<>();
        config.put(Constants.METRIC_PREFIX, "Custom Metrics|Prometheus");

        metricConfig = new HashMap<>();
        metricConfig.put(Constants.NAME, "node_cpu_usage");
        metricConfig.put(Constants.PROMQL, "100 * (sum by (instance) (rate(node_cpu_seconds_total{mode!='idle'}[60s]))/count by (instance) (node_cpu_seconds_total{mode='idle'}))");
        metricConfig.put(Constants.ALIAS, "Node CPU Usage");
        metricConfig.put(Constants.AGGREGATION_TYPE, "AVERAGE");
        metricConfig.put(Constants.TIME_ROLL_UP_TYPE, "AVERAGE");
        metricConfig.put(Constants.CLUSTER_ROLL_UP_TYPE, "INDIVIDUAL");
        metricConfig.put(Constants.LABEL_FILTERS, Arrays.asList(createFilterMap(Constants.NAME, "instance", Constants.VALUE, ".*")));

        serverConfig = new HashMap<>();
        serverConfig.put(Constants.URL, "http://testserver:9090");
        serverConfig.put(Constants.AUTH_TOKEN, "abcdef1234");

        // Instantiate the task and then spy on it.
        // Use doReturn().when(spy).method() for protected methods.
        task = Mockito.spy(new PrometheusMonitorTask(serviceProvider, config, serverConfig, metricConfig));
        doReturn(prometheusClient).when(task).getPrometheusClient();

        // --- MetricPathUtils Initialization for tests ---
        // Setup MonitorContext and MetricCharSequenceReplacer mocks
        when(monitorContext.getMetricCharSequenceReplacer()).thenReturn(metricCharSequenceReplacer);
        // For testing, we can make the replacer return the original string or a simplified replacement
        when(metricCharSequenceReplacer.getReplacementFromCache(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        // Use reflection to set the static monitorContext field in MetricPathUtils
        // This is necessary because MetricPathUtils is a utility class that typically gets initialized by the framework.
        try {
            java.lang.reflect.Field field = MetricPathUtils.class.getDeclaredField("monitorContext");
            field.setAccessible(true);
            field.set(null, monitorContext); // Set static field to our mock
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // This indicates a change in MetricPathUtils structure or field name
            throw new RuntimeException("Failed to set monitorContext in MetricPathUtils via reflection. " +
                    "Ensure 'monitorContext' field exists and is accessible.", e);
        }
        // --- End MetricPathUtils Initialization ---
    }

    @Test
    public void testRun_Success() throws Exception {
        // Mock PrometheusClient.query() response
        Map<String, String> labels = new HashMap<>();
        labels.put("instance", "node-1");
        PrometheusMetric mockMetric = new PrometheusMetric("node_cpu_seconds_total", labels, "50.0");
        when(prometheusClient.query(anyString())).thenReturn(Collections.singletonList(mockMetric));

        task.run();

        // Verify PromQL query was made
        verify(prometheusClient).query(
                "100 * (sum by (instance) (rate(node_cpu_seconds_total{mode!='idle'}[60s]))/count by (instance) (node_cpu_seconds_total{mode='idle'}))"
        );

        // Capture metrics passed to printMetric
        ArgumentCaptor<List<Metric>> metricsCaptor = ArgumentCaptor.forClass(List.class);
        verify(metricWriteHelper).printMetric(metricsCaptor.capture());

        List<Metric> capturedMetrics = metricsCaptor.getValue();
        assertNotNull(capturedMetrics);
        assertEquals(1, capturedMetrics.size());

        Metric capturedMetric = capturedMetrics.get(0);
        assertEquals("Node CPU Usage", capturedMetric.getMetricName());
        assertEquals("50.0", capturedMetric.getMetricValue());
        assertEquals("Custom Metrics|Prometheus|instance|node-1|Node CPU Usage", capturedMetric.getMetricPath());
        assertEquals("AVERAGE", capturedMetric.getAggregationType());
        assertEquals("AVERAGE", capturedMetric.getTimeRollUpType());
        assertEquals("INDIVIDUAL", capturedMetric.getClusterRollUpType());
    }

    @Test
    public void testRun_NoMetricsReturned() throws Exception {
        when(prometheusClient.query(anyString())).thenReturn(Collections.emptyList());

        task.run();

        verify(prometheusClient).query(anyString());
        verify(metricWriteHelper, never()).printMetric(anyList()); // No metrics should be printed
    }

    @Test
    public void testRun_PrometheusClientThrowsException() throws Exception {
        when(prometheusClient.query(anyString())).thenThrow(new RuntimeException("Prometheus connection failed"));

        task.run();
        verify(metricWriteHelper, never()).printMetric(anyList()); // No metrics should be printed
    }

    @Test
    public void testFilterReplace() {
        String promql = "kube_deployment_status_replicas_available{namespace=~'%namespace%'}[60s]";
        List<Map<String, String>> filters = Arrays.asList(
                createFilterMap(Constants.NAME, "namespace", Constants.VALUE, "spring-petclinic|kube-system")
        );

        // Directly call the protected method using a spy or by making it public for testing
        // For simplicity, we'll use a spy and call it directly.
        PrometheusMonitorTask spyTask = Mockito.spy(task);
        String result = spyTask.filterReplace(promql, filters);
        assertEquals("kube_deployment_status_replicas_available{namespace=~'spring-petclinic|kube-system'}[60s]", result);
    }

    @Test
    public void testBuildMetricPath_Success() {
        Map<String, String> labels = new HashMap<>();
        labels.put("instance", "node-1");
        labels.put("region", "us-east-1");
        PrometheusMetric metric = new PrometheusMetric("some_metric", labels, "10");

        List<Map<String, String>> filters = Arrays.asList(
                createFilterMap(Constants.NAME, "instance", Constants.VALUE, ".*"),
                createFilterMap(Constants.NAME, "region", Constants.VALUE, ".*")
        );

        PrometheusMonitorTask spyTask = Mockito.spy(task);
        String metricPrefix = "Custom Metrics|Prometheus";
        List<String> metricPathTokens = spyTask.getMetricPathTokens(metric, filters);
        String metricPath = MetricPathUtils.buildMetricPath(metricPrefix, metricPathTokens.toArray(new String[0]));
        assertEquals("Custom Metrics|Prometheus|instance|node-1|region|us-east-1", metricPath);
    }

    @Test
    public void testBuildMetricPath_MissingLabelInMetric() {
        Map<String, String> labels = new HashMap<>();
        labels.put("instance", "node-1"); // 'region' is missing
        PrometheusMetric metric = new PrometheusMetric("some_metric", labels, "10");

        List<Map<String, String>> filters = Arrays.asList(
                createFilterMap(Constants.NAME, "instance", Constants.VALUE, ".*"),
                createFilterMap(Constants.NAME, "region", Constants.VALUE, ".*")
        );

        PrometheusMonitorTask spyTask = Mockito.spy(task);
        String metricPrefix = "Custom Metrics|Prometheus";

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            spyTask.getMetricPathTokens(metric, filters);
        });
        assertTrue(thrown.getMessage().contains("Label filter region defined but not found in labels for metric some_metric"));
    }

    private static Map<String, String> createFilterMap(String k1, String v1, String k2, String v2) {
        Map<String, String> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}