// src/test/java/com/appdynamics/monitors/prometheus/PrometheusMonitorTest.java
package com.appdynamics.extensions.prometheus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.prometheus.util.Constants;

public class PrometheusMonitorTest {

    @Spy
    private PrometheusMonitor monitor;

    @Mock
    private MonitorContextConfiguration monitorContextConfiguration;
    @Mock
    private TasksExecutionServiceProvider tasksExecutionServiceProvider;

    // KEEP THIS AS Map<String, Object> so you can populate it
    private Map<String, Object> mockConfigYml;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Prepare a mock configYml structure that mimics your config.yml
        mockConfigYml = new HashMap<>();
        mockConfigYml.put(Constants.METRIC_PREFIX, "Custom Metrics|Prometheus"); // This line will now be fine.

        // Define mock server configurations
        Map<String, Object> server1 = new HashMap<>();
        server1.put(Constants.URL, "http://server1:9090");
        List<Map<String, Object>> server1Metrics = Arrays.asList(
                createMetricConfig("pod_cpu_usage", "max by (pod) (rate(container_cpu_usage_seconds_total{namespace=\"system\", container!=\"POD\", pod!=\"\"}[5m]))", "Pod CPU Usage"),
                createMetricConfig("deployment_availability", "(kube_deployment_status_replicas_available{namespace=\"system\"} / kube_deployment_status_replicas{namespace=\"system\"}) * 100", "Deployment Availability")
        );
        server1.put(Constants.METRICS, server1Metrics);

        Map<String, Object> server2 = new HashMap<>();
        server2.put(Constants.URL, "http://server2:9090");
        List<Map<String, Object>> server2Metrics = Arrays.asList(
                createMetricConfig("another_metric", "some_other_promql", "Another Metric")
        );
        server2.put(Constants.METRICS, server2Metrics);

        mockConfigYml.put(Constants.SERVERS, Arrays.asList(server1, server2)); // This line will also be fine.

        // Mock getContextConfiguration() method of the spy `monitor` to return our mock
        doReturn(monitorContextConfiguration).when(monitor).getContextConfiguration();

        // THIS IS THE CRUCIAL CHANGE for the getConfigYml() mock
        // Use doReturn syntax here. mockConfigYml (Map<String, Object>) is assignable to Map<String, ?>
        doReturn(mockConfigYml).when(monitorContextConfiguration).getConfigYml();
    }

    // Helper method to create metric configuration maps for testing
    private Map<String, Object> createMetricConfig(String name, String promql, String alias) {
        Map<String, Object> metric = new HashMap<>();
        metric.put(Constants.NAME, name);
        metric.put(Constants.PROMQL, promql);
        metric.put(Constants.LABEL_FILTERS, new ArrayList<>());
        metric.put(Constants.ALIAS, alias);
        metric.put(Constants.AGGREGATION_TYPE, "AVERAGE");
        metric.put(Constants.TIME_ROLL_UP_TYPE, "AVERAGE");
        metric.put(Constants.CLUSTER_ROLL_UP_TYPE, "AVERAGE");
        return metric;
    }

    @Test
    public void testGetDefaultMetricPrefix() {
        assertEquals(Constants.DEFAULT_METRIC_PREFIX, monitor.getDefaultMetricPrefix());
    }

    @Test
    public void testGetMonitorName() {
        assertEquals(Constants.MONITOR_NAME, monitor.getMonitorName());
    }

    @Test
    public void testInitializeMoreStuff() {
        // Call initializeMoreStuff, which internally calls getContextConfiguration().getConfigYml()
        monitor.initializeMoreStuff(new HashMap<>());
        // Verify that the monitor's internal configYml field is set correctly
        assertEquals(mockConfigYml, monitor.configYml);
    }

    @Test
    public void testDoRunSubmitsCorrectTasks() {
        // First, simulate the initialization process that ABaseMonitor performs
        monitor.initializeMoreStuff(new HashMap<>());

        monitor.doRun(tasksExecutionServiceProvider);

        // Verify that tasksExecutionServiceProvider.submit was called for each metric.
        // There are 2 metrics for server1 and 1 for server2, totaling 3 tasks.
        verify(tasksExecutionServiceProvider, times(3)).submit(anyString(), any(PrometheusMonitorTask.class));

        // Capture the arguments passed to submit to verify task names and instances
        ArgumentCaptor<String> taskNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PrometheusMonitorTask> taskCaptor = ArgumentCaptor.forClass(PrometheusMonitorTask.class);

        // Capture all calls to submit
        verify(tasksExecutionServiceProvider, atLeastOnce()).submit(taskNameCaptor.capture(), taskCaptor.capture());

        List<String> capturedTaskNames = taskNameCaptor.getAllValues();
        List<PrometheusMonitorTask> capturedTasks = taskCaptor.getAllValues();

        // Assert the generated task names are correct
        assertEquals("pod_cpu_usage(http://server1:9090)", capturedTaskNames.get(0));
        assertEquals("deployment_availability(http://server1:9090)", capturedTaskNames.get(1));
        assertEquals("another_metric(http://server2:9090)", capturedTaskNames.get(2));

        // Assert that PrometheusMonitorTask instances were created and submitted
        assertNotNull(capturedTasks.get(0));
        assertNotNull(capturedTasks.get(1));
        assertNotNull(capturedTasks.get(2));
    }

    @Test
    public void testGetServers() {
        // Ensure configYml is set before calling getServers
        monitor.initializeMoreStuff(new HashMap<>());
        List<Map<String, ?>> servers = monitor.getServers();
        assertNotNull(servers);
        assertEquals(2, servers.size());
        assertEquals("http://server1:9090", servers.get(0).get(Constants.URL));
        assertEquals("http://server2:9090", servers.get(1).get(Constants.URL));
    }
}