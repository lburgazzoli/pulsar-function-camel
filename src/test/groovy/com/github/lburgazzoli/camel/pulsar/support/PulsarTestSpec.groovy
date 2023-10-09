package com.github.lburgazzoli.camel.pulsar.support

import com.github.lburgazzoli.camel.pulsar.CamelFunction
import org.apache.pulsar.common.functions.FunctionConfig
import org.apache.pulsar.functions.LocalRunner
import spock.lang.Specification

class PulsarTestSpec extends Specification {

    static LocalRunner runner(String url, String route, Collection<String> inputs, String output) {
        FunctionConfig cfg = new FunctionConfig();
        cfg.setName('camel')
        cfg.setClassName(CamelFunction.class.getName())
        cfg.setRuntime(FunctionConfig.Runtime.JAVA)
        cfg.setInputs(inputs)
        cfg.setOutput(output)
        cfg.setUserConfig(Map.of(CamelFunction.CONFIG_KEY_STEPS, route))

        return LocalRunner.builder()
            .functionConfig(cfg)
            .brokerServiceUrl(url)
            .build()
    }

    static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final IOException ignored) {
            }
        }
    }

}
