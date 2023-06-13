package com.github.lburgazzoli.camel.pulsar;

import java.util.Collections;
import java.util.Map;

import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.common.functions.FunctionConfig;
import org.apache.pulsar.functions.LocalRunner;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.github.lburgazzoli.camel.pulsar.support.PulsarTestClient;
import com.github.lburgazzoli.camel.pulsar.support.PulsarTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class CamelFunctionTest extends PulsarTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(CamelFunctionTest.class);
    private static final String IMAGE_NAME = "apachepulsar/pulsar:3.0.0";
    private static final DockerImageName IMAGE = DockerImageName.parse(IMAGE_NAME);

    @Container
    private static final PulsarContainer PULSAR = new PulsarContainer(IMAGE)
        .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("pulsar.container")));

    private LocalRunner runner(String route) {
        FunctionConfig cfg = new FunctionConfig();
        cfg.setName("camel");
        cfg.setClassName(CamelFunction.class.getName());
        cfg.setRuntime(FunctionConfig.Runtime.JAVA);
        cfg.setInputs(Collections.singleton("sensors"));
        cfg.setOutput("output-1");
        cfg.setUserConfig(Map.of(
            CamelFunction.CONFIG_KEY_STEPS, route));

        return LocalRunner.builder()
            .functionConfig(cfg)
            .brokerServiceUrl(PULSAR.getPulsarBrokerUrl())
            .build();
    }

    @Test
    public void test() throws Exception {
        String route = """
            - setHeader:
                name: "source"
                jq: '.source'
            - choice:
                when:
                - jq: '.source == "sensor-1"'
                  steps:
                  - setProperty:
                      name: 'pulsar.apache.org/function.output'
                      constant: 'far'
                  - setBody:
                      jq:
                        expression: '.data'
                        resultType: 'java.lang.String'
                - jq: '.source == "sensor-2"'
                  steps:
                  - setProperty:
                      name: 'pulsar.apache.org/function.output'
                      constant: 'near'
                  - setBody:
                      jq:
                        expression: '.data'
                        resultType: 'java.lang.String'
            """;

        try (LocalRunner runner = runner(route)) {
            runner.start(false);

            try (var c = new PulsarTestClient<>(Schema.STRING, PULSAR.getPulsarBrokerUrl())) {
                c.send("sensors", "{ \"source\": \"sensor-1\", \"data\": \"s1\"}");

                Message<String> far = c.read("far");
                assertThat(far.getValue()).isEqualTo("s1");
                assertThat(far.getProperties()).containsEntry("source", "sensor-1");

                c.send("sensors", "{ \"source\": \"sensor-2\", \"data\": \"s2\"}");

                Message<String> near = c.read("near");
                assertThat(near.getValue()).isEqualTo("s2");
                assertThat(near.getProperties()).containsEntry("source", "sensor-2");

            }
        }
    }
}
