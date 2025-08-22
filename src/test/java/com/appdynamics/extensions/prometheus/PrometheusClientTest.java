// src/test/java/com/appdynamics/monitors/prometheus/PrometheusClientTest.java
package com.appdynamics.extensions.prometheus;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

public class PrometheusClientTest {

    // WireMockRule starts a local HTTP server on port 8080 for testing
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8080);
    
    //region Mock Prometheus Responses
    private final String PROMETHEUS_SUCCESS_RESPONSE = "{\n" +
            "  \"status\": \"success\",\n" +
            "  \"data\": {\n" +
            "    \"resultType\": \"vector\",\n" +
            "    \"result\": [\n" +
            "      {\n" +
            "        \"metric\": {\n" +
            "          \"__name__\": \"node_cpu_seconds_total\",\n" +
            "          \"cpu\": \"0\",\n" +
            "          \"instance\": \"localhost:9100\",\n" +
            "          \"job\": \"node\"\n" +
            "        },\n" +
            "        \"value\": [\n" +
            "          1678886400,\n" +
            "          \"123.45\"\n" +
            "        ]\n" +
            "      },\n" +
            "      {\n" +
            "        \"metric\": {\n" +
            "          \"__name__\": \"node_cpu_seconds_total\",\n" +
            "          \"cpu\": \"1\",\n" +
            "          \"instance\": \"localhost:9100\",\n" +
            "          \"job\": \"node\"\n" +
            "        },\n" +
            "        \"value\": [\n" +
            "          1678886400,\n" +
            "          \"67.89\"\n" +
            "        ]\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}";

    private final String PROMETHEUS_EMPTY_RESULT_RESPONSE = "{\n" +
            "  \"status\": \"success\",\n" +
            "  \"data\": {\n" +
            "    \"resultType\": \"vector\",\n" +
            "    \"result\": []\n" +
            "  }\n" +
            "}";

    private final String PROMETHEUS_ERROR_STATUS_RESPONSE = "{\n" +
            "  \"status\": \"error\",\n" +
            "  \"errorType\": \"bad_data\",\n" +
            "  \"error\": \"invalid parameter 'query': 123\"\n" +
            "}";
    //endregion

    @Test
    public void testQueryRawSuccess() throws Exception {
        String promQL = "up";
        // Configure WireMock to respond to a GET request for /api/v1/query with a specific query parameter
        stubFor(get(urlPathEqualTo("/api/v1/query"))
                .withQueryParam("query", equalTo(promQL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(PROMETHEUS_SUCCESS_RESPONSE)));

        PrometheusClient client = new PrometheusClient("http://localhost:8080");
        String response = client.queryRaw(promQL);
        assertNotNull(response);
        assertTrue(response.contains("\"status\": \"success\""));
    }

    @Test(expected = IOException.class)
    public void testQueryRawHttpError() throws Exception {
        String promQL = "up";
        // Configure WireMock to simulate an HTTP 500 error
        stubFor(get(urlPathEqualTo("/api/v1/query"))
                .withQueryParam("query", equalTo(promQL))
                .willReturn(aResponse()
                        .withStatus(500) // Simulate HTTP 500 error
                        .withHeader("Content-Type", "application/json")
                        .withBody("Internal Server Error")));

        PrometheusClient client = new PrometheusClient("http://localhost:8080");
        client.queryRaw(promQL); // This call should throw a RuntimeException
    }

    @Test
    public void testExtractPrometheusMetricsSuccess() throws Exception {
        // Base URL doesn't matter for this method as it only parses a string
        PrometheusClient client = new PrometheusClient("http://localhost:8080");

        List<PrometheusMetric> metrics = client.extractPrometheusMetrics(PROMETHEUS_SUCCESS_RESPONSE);
        assertNotNull(metrics);
        assertEquals(2, metrics.size());

        PrometheusMetric metric1 = metrics.get(0);
        assertEquals("node_cpu_seconds_total", metric1.getName());
        assertEquals("123", metric1.getValue());
        Map<String, String> labels1 = metric1.getLabels();
        assertNotNull(labels1);
        assertEquals(3, labels1.size()); // Should contain cpu, instance, job
        assertEquals("0", labels1.get("cpu"));
        assertEquals("localhost:9100", labels1.get("instance"));
        assertEquals("node", labels1.get("job"));
        assertNull(labels1.get("__name__")); // __name__ should be removed from labels

        PrometheusMetric metric2 = metrics.get(1);
        assertEquals("node_cpu_seconds_total", metric2.getName());
        assertEquals("68", metric2.getValue());
        Map<String, String> labels2 = metric2.getLabels();
        assertNotNull(labels2);
        assertEquals(3, labels2.size());
        assertEquals("1", labels2.get("cpu"));
    }

    @Test
    public void testExtractPrometheusMetricsEmptyResult() throws Exception {
        PrometheusClient client = new PrometheusClient("http://localhost:8080");
        List<PrometheusMetric> metrics = client.extractPrometheusMetrics(PROMETHEUS_EMPTY_RESULT_RESPONSE);
        assertNotNull(metrics);
        assertTrue(metrics.isEmpty());
    }

    @Test(expected = Exception.class)
    public void testExtractPrometheusMetricsErrorStatus() throws Exception {
        PrometheusClient client = new PrometheusClient("http://localhost:8080");
        client.extractPrometheusMetrics(PROMETHEUS_ERROR_STATUS_RESPONSE); // Should throw due to "status": "error"
    }

    @Test(expected = Exception.class)
    public void testExtractPrometheusMetricsInvalidJson() throws Exception {
        PrometheusClient client = new PrometheusClient("http://localhost:8080");
        client.extractPrometheusMetrics("this is not json"); // Should throw due to invalid JSON
    }

    @Test
    public void testQueryIntegration() throws Exception {
        String promQL = "some_metric";
        // Configure WireMock for the integrated query method
        stubFor(get(urlPathEqualTo("/api/v1/query"))
                .withQueryParam("query", equalTo(promQL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(PROMETHEUS_SUCCESS_RESPONSE)));

        PrometheusClient client = new PrometheusClient("http://localhost:8080");
        List<PrometheusMetric> metrics = client.query(promQL); // Calls queryRaw and extractPrometheusMetrics
        assertNotNull(metrics);
        assertEquals(2, metrics.size());
        assertEquals("node_cpu_seconds_total", metrics.get(0).getName());
    }
}