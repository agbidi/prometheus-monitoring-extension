package com.appdynamics.extensions.prometheus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.appdynamics.extensions.prometheus.util.Constants;
import com.appdynamics.extensions.prometheus.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


public class PrometheusClient {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusClient.class); // Initialize logger

    private final String prometheusBaseUrl;
    private final String authToken; // New field for authentication token

    public PrometheusClient(String prometheusBaseUrl, String authToken) {
        this.prometheusBaseUrl = prometheusBaseUrl;
        this.authToken = authToken;
    }

    public PrometheusClient(String prometheusBaseUrl) {
        this(prometheusBaseUrl, null);
    }

    public String queryRaw(String promQL) throws Exception {
        String encodedQuery = URLEncoder.encode(promQL, StandardCharsets.UTF_8.toString());
        String urlStr = prometheusBaseUrl + Constants.PROMETHEUS_QUERY_PATH + encodedQuery;

        URL url = createUrl(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (authToken != null && !authToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            String errorResponse = "";
            try (BufferedReader errIn = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder errorBuilder = new StringBuilder();
                String inputLine;
                while ((inputLine = errIn.readLine()) != null) {
                    errorBuilder.append(inputLine);
                }
                errorResponse = errorBuilder.toString();
            } catch (Exception e) {
                // Ignore error reading error stream, it might be empty or unavailable
            }
            throw new IOException(String.format("Failed to query Prometheus: HTTP error code %d. Response: %s", status, errorResponse));
        }

        StringBuilder response;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        } finally {
            conn.disconnect();
        }

        return response.toString();
    }

    public List<PrometheusMetric> query(String promQL) throws Exception {
        String jsonResponse = queryRaw(promQL);
        return extractPrometheusMetrics(jsonResponse);
    }

    /**
     * Parses the JSON response from Prometheus and extracts a list of PrometheusMetric objects.
     * Handles both 'vector' (instant) and 'matrix' (range) result types.
     * For 'matrix' results, it extracts the latest value.
     */
    public List<PrometheusMetric> extractPrometheusMetrics(String jsonResponse) throws Exception {
        List<PrometheusMetric> metrics = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonResponse);

        if (!root.path("status").asText().equals("success")) {
            String errorType = root.path("errorType").asText("");
            String error = root.path("error").asText("");
            throw new IOException(String.format("Query failed or returned non-success status. Error Type: %s, Error: %s", errorType, error));
        }

        JsonNode dataNode = root.path("data");
        String resultType = dataNode.path("resultType").asText(); // Get the result type (vector or matrix)
        JsonNode results = dataNode.path("result"); // The array of results

        // Ensure 'results' node is not null or missing before iterating
        if (results.isMissingNode() || results.isNull() || !results.isArray()) {
            logger.warn("Prometheus response 'result' array is missing, null, or not an array. No metrics to process.");
            return metrics; // Return empty list as there are no metrics to process
        }

        for (JsonNode result : results) {
            // Safeguard 'result' node itself, though for-each loop should generally ensure it's not null
            if (result.isNull() || result.isMissingNode()) {
                logger.warn("Skipping null or missing result entry in Prometheus response: {}", result);
                continue;
            }

            JsonNode metricNode = result.path("metric"); // path() returns MissingNode if not found
            String metricName;
            Map<String, String> labels;

            // Handle cases where 'metric' field or '__name__' label might be missing (e.g., aggregation queries)
            if (metricNode.isMissingNode() || metricNode.isNull()) {
                metricName = ""; // No __name__ label, so use empty string
                labels = new HashMap<>(); // Initialize with an empty map
            } else {
                // asText() on MissingNode returns "", so this is safe for missing __name__
                metricName = metricNode.path("__name__").asText();

                // Convert metricNode to Map. Use TypeReference for better type safety with generics.
                // This can throw IllegalArgumentException if metricNode is not an object or is MissingNode.
                try {
                    labels = mapper.convertValue(metricNode, new TypeReference<Map<String, String>>() {});
                    // Ensure labels map is not null in case convertValue returns null (unlikely but safe)
                    if (labels == null) {
                        labels = new HashMap<>();
                    }
                } catch (IllegalArgumentException e) {
                    // Log and handle the malformed metricNode, treating labels as empty
                    logger.warn("Could not convert 'metric' node to Map for result: {}. Using empty labels. Error: {}", result.toString(), e.getMessage());
                    labels = new HashMap<>();
                }
                // Remove __name__ from the labels map if it was present
                labels.remove("__name__");
            }

            String value;
            if (null == resultType) {
                throw new IOException("Unsupported resultType: " + resultType);
            } else switch (resultType) {
                case "vector":
                    // Handle vector (instant) query result: value is [timestamp, "value_string"]
                    JsonNode valueNode = result.path("value");
                    if (valueNode.isMissingNode() || valueNode.isNull() || !valueNode.isArray() || valueNode.size() < 2) {
                        throw new IOException("Vector result 'value' is malformed or missing expected elements: " + valueNode.toString());
                    }   // valueNode.get(1) is safe here because we checked valueNode.size() < 2
                    value = StringUtils.strToLongStr(valueNode.get(1).asText());
                    break;
                case "matrix":
                    // Handle matrix (range) query result: values is [[timestamp1, "value1"], [timestamp2, "value2"], ...]
                    JsonNode valuesArray = result.path("values");
                    if (valuesArray.isMissingNode() || valuesArray.isNull() || !valuesArray.isArray() || valuesArray.size() == 0) {
                        throw new IOException("Matrix result 'values' array is malformed or empty: " + valuesArray.toString());
                    }
                    // For AppDynamics, we typically need a single current value. Take the latest one.
                    logger.warn("Unexepected resultType 'matrix': using the last value from result set. resultType 'vector' is recommended: {}", result);
                    JsonNode latestValueArray = valuesArray.get(valuesArray.size() - 1); // Get the last element of the 'values' array
                    if (latestValueArray.isMissingNode() || latestValueArray.isNull() || !latestValueArray.isArray() || latestValueArray.size() < 2) {
                        throw new IOException("Latest value array in matrix result is malformed: " + latestValueArray.toString());
                    }   value = StringUtils.strToLongStr(latestValueArray.get(1).asText());
                    break;
                default:
                    throw new IOException("Unsupported resultType: " + resultType);
            }

            PrometheusMetric metric = new PrometheusMetric(metricName, labels, value);
            metrics.add(metric);
        }
        return metrics;
    }

    protected URL createUrl(String urlStr) throws java.net.MalformedURLException {
        return new URL(urlStr);
    }
}