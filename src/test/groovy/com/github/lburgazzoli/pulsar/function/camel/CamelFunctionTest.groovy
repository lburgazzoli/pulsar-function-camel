package com.github.lburgazzoli.pulsar.function.camel

import com.github.lburgazzoli.pulsar.function.camel.support.PulsarTestClient
import com.github.lburgazzoli.pulsar.function.camel.support.PulsarTestSpec
import groovy.util.logging.Slf4j
import org.apache.pulsar.client.api.Schema
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PulsarContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.spock.Testcontainers
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared

@Slf4j
@Testcontainers
class CamelFunctionTest extends PulsarTestSpec {
    private static final String IMAGE_NAME = "apachepulsar/pulsar:3.1.0"
    private static final DockerImageName IMAGE = DockerImageName.parse(IMAGE_NAME)

    @Shared
    PulsarContainer PULSAR = new PulsarContainer(IMAGE)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("pulsar.container")))


    def 'simple test'(String topic, String source, String data) {
        given:
            String route = '''
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
                '''.stripIndent()

            def r = runner(PULSAR.pulsarBrokerUrl, route, [ "sensors" ], "output-1")
            r.start(false)

            def c = new PulsarTestClient<>(Schema.STRING, PULSAR.pulsarBrokerUrl)

        when:
            c.send("sensors", """{ "source": "${source}", "data": "${data}" }""" as String)
        then:
            def msg = c.read(topic)
            msg.value == data
            msg.properties["source"] == source

        cleanup:
            closeQuietly(c)
            closeQuietly(r)
        where:
            topic  | source     | data
            "far"  | "sensor-1" | "s1"
            "near" | "sensor-2" | "s2"
    }
}
