# AppDynamics Prometheus Monitoring Extension

This AppDynamics Machine Agent extension collects metrics from Prometheus servers via PromQL and sends them to the AppDynamics Controller, allowing you to monitor your Prometheus-exposed metrics within AppDynamics.

## Contents

*   [Overview](#overview)
*   [Prerequisites](#prerequisites)
*   [Installation](#installation)
*   [Configuration](#configuration)
    *   [Metric Prefix](#metric-prefix)
    *   [Servers](#servers)
    *   [Metric Path Replacements](#metric-path-replacements)
    *   [Performance Tuning](#performance-tuning)
    *   [Proxy Settings](#proxy-settings)
    *   [Controller Information](#controller-information)
    *   [Encryption Key](#encryption-key)
    *   [Events Service Parameters (Optional)](#events-service-parameters-optional)
*   [Metrics Captured](#metrics-captured)
*   [Troubleshooting](#troubleshooting)
*   [Support](#support)

## Overview

The Prometheus Monitoring Extension leverages the AppDynamics Machine Agent to query specified Prometheus endpoints using PromQL (Prometheus Query Language) and translate the results into custom metrics within the AppDynamics Controller. This provides centralized visibility and monitoring for your Kubernetes clusters, applications, or any other systems exposing metrics via Prometheus.

## Prerequisites ##
- Before the extension is installed, the prerequisites mentioned [here](https://community.appdynamics.com/t5/Knowledge-Base/Extensions-Prerequisites-Guide/ta-p/35213) need to be met. Please do not proceed with the extension installation if the specified prerequisites are not met.
- Download and install [Apache Maven](https://maven.apache.org/) which is configured with `Java 8` to build the extension artifact from source. You can check the java version used in maven using command `mvn -v` or `mvn --version`. If your maven is using some other java version then please download java 8 for your platform and set JAVA_HOME parameter before starting maven.
- The extension also needs a [Prometheus] server installed.
- The extension needs to be able to connect to Prometheus in order to collect and send metrics. 
  To do this, you will have to either establish a remote connection in between the extension and the product, 
  or have an agent on the same machine running the product in order for the extension to collect and send the metrics.
  
## Installation ##
- Clone the "prometheus-monitoring-extension" repo using `git clone <repoUrl>` command.
- Run 'mvn clean install' from "prometheus-monitoring-extension". This will produce a PrometheusMonitor-VERSION.zip in the target directory.
- Unzip the file PrometheusMonitor-\[version\].zip into <b><MACHINE_AGENT_HOME>/monitors/</b>
- In the newly created directory <b>"PrometheusMonitor"</b>, edit the config.yml to configure the parameters (See Configuration section below)
- Restart the Machine Agent

    ```bash
    # Example command (Linux)
    <MACHINE_AGENT_HOME>/bin/machineagent restart
    ```

## Configuration

The main configuration file for the extension is `config.yml` located in `<MACHINE_AGENT_HOME>/monitors/PrometheusMonitor/config.yml`.

### Metric Prefix

The `metricPrefix` defines the root path under which all metrics collected by this extension will appear in the AppDynamics Controller.

```yaml
metricPrefix: "Custom Metrics|Prometheus"
```

*   **`metricPrefix`**: (String) The base path for all metrics. It is highly recommended to use a specific tier ID for better organization, as suggested in the commented out example in `config.yml`.

    ```yaml
    # Example with tier ID (HIGHLY RECOMMENDED for production)
    # metricPrefix: "Server|Component:YourTierID|Custom Metrics|Prometheus"
    ```

### Servers

The `servers` section is a list of Prometheus endpoints from which metrics will be collected.

Each server entry has the following properties:

*   **`url`**: (String, Required) The URL of the Prometheus HTTP API endpoint (e.g., `http://localhost:9090`).
*   **`authToken`**: (String, Optional) A bearer token for authentication if the Prometheus endpoint requires it (e.g., for OpenShift ServiceAccount tokens). Uncomment and provide the token if needed.
*   **`metrics`**: (List of Objects, Required) A list of metric definitions to be collected from this Prometheus server. Each metric definition has:
    *   **`name`**: (String, Required) A unique internal name for the metric.
    *   **`promql`**: (String, Required) The PromQL query to execute.
        *   **Placeholders**: You can use `%labelName%` placeholders in your PromQL queries, which will be replaced by values from `labelFilters`. For example, `namespace=~'%namespace%'`.
    *   **`labelFilters`**: (List of Objects, Optional) A list of label filters to apply to the metric.
        *   **`name`**: (String, Required) The name of the label (e.g., `instance`, `namespace`).
        *   **`value`**: (String, Required) A regular expression to match the label's value. If multiple values are matched, the metric will be reported for each matched combination.
    *   **`alias`**: (String, Required) The display name for the metric in AppDynamics. This will be used in the metric path.
    *   **`aggregationType`**: (String, Optional) How the metric values are aggregated over time. Default is `AVERAGE`. Other options include `SUM`, `OBSERVATION`.
    *   **`timeRollUpType`**: (String, Optional) How the metric values are rolled up over time. Default is `AVERAGE`. Other options include `SUM`, `CURRENT`.
    *   **`clusterRollUpType`**: (String, Optional) How the metric values are rolled up across multiple instances (if `labelFilters` result in multiple metrics). Default is `COLLECTIVE`. Other options include `INDIVIDUAL`.

### Metric Path Replacements

This section allows you to define character replacements in the generated AppDynamics metric paths to ensure they are valid.

```yaml
metricPathReplacements:
- replace: ":"
  replaceWith: "-"
- replace: "|"
  replaceWith: "="
- replace: ","
  replaceWith: "#"
```

*   **`replace`**: (String) A regular expression for the characters to be replaced.
*   **`replaceWith`**: (String) The string to replace the matched characters with.

### Performance Tuning

```yaml
numberOfThreads: 10
threadTimeOut: 60
```

*   **`numberOfThreads`**: (Integer) The number of concurrent threads used to collect metrics. Adjust based on the number of Prometheus metrics.
*   **`threadTimeOut`**: (Integer) The timeout in seconds for each metric collection thread.

### Proxy Settings

If your Machine Agent requires a proxy to reach the Prometheus endpoints, configure it here.

```yaml
proxy:
  uri: ""
  username: ""
```

*   **`uri`**: (String) The URI of the proxy server (e.g., `http://localhost:8080`).
*   **`username`**: (String, Optional) Username for proxy authentication. Leave empty if not required.

### Controller Information

These fields configure how the extension connects to the AppDynamics Controller. If left empty, the extension will attempt to retrieve these values from the Machine Agent's system properties or configuration file.

```yaml
controllerInfo:
  controllerHost: "" # -Dappdynamics.controller.hostName
  controllerPort: 8090 # -Dappdynamics.controller.port
  controllerSslEnabled: false # -Dappdynamics.controller.ssl.enabled
  enableOrchestration: false # N/A
  uniqueHostId: "" # -Dappdynamics.agent.uniqueHostId
  username: "" # -Dappdynamics.agent.monitors.controller.username
  password: "" # -Dappdynamics.agent.monitors.controller.password
  encryptedPassword: "" # -Dappdynamics.agent.monitors.controller.encryptedPassword
  accountAccessKey: "" # -Dappdynamics.agent.accountAccessKey
  account: "" # -Dappdynamics.agent.accountName
  machinePath: "" # -Dappdynamics.machine.agent.hierarchyPath
  simEnabled: false # -Dappdynamics.sim.enabled
  applicationName: "" # -Dappdynamics.agent.applicationName
  tierName: "" # -Dappdynamics.agent.tierName
  nodeName: "" # -Dappdynamics.agent.nodeName
```

It is generally recommended to configure these properties via the Machine Agent's `controller-info.xml` or system properties (`-D` flags) rather than directly in `config.yml` to maintain consistency across extensions.

## Troubleshooting

*   **Check Logs**: The extension logs are located in `<MACHINE_AGENT_HOME>/monitors/PrometheusMonitor/logs`. Review these logs for any errors or warnings related to connection issues, PromQL query failures, or metric processing.
*   **Prometheus Access**: Ensure the Machine Agent can reach the configured Prometheus `url` and that any required `authToken` is correct.
*   **PromQL Queries**: Verify that your `promql` queries are valid and return data when executed directly against your Prometheus instance.
*   **Metric Path Issues**: If metrics are not appearing, check the `metricPathReplacements` configuration to ensure no invalid characters are being created in the metric paths.

## Support

For any issues or questions, please refer to the AppDynamics documentation or contact AppDynamics Support.

---  
 
