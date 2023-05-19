package com.github.lburgazzoli.camel.pulsar;

import java.util.Collections;
import java.util.Map;

import org.apache.pulsar.common.functions.FunctionConfig;
import org.apache.pulsar.functions.LocalRunner;

public class Main {
    public static void main(String[] args) throws Exception {

        String route =
            " - from:\n" +
            "     uri: 'direct:in'\n" +
            "     steps:\n" +
            "     - setBody:\n" +
            "         constant: 'foo'";

        FunctionConfig cfg = new FunctionConfig();
        cfg.setName("camel");
        cfg.setClassName(CamelFunction.class.getName());
        cfg.setRuntime(FunctionConfig.Runtime.JAVA);
        cfg.setInputs(Collections.singleton("persistent://public/default/input-1"));
        cfg.setOutput("persistent://public/default/output-1");
        //cfg.setLogTopic("persistent://public/default/logging-function-logs");
        cfg.setUserConfig(Map.of("route", route));


        try (LocalRunner localRunner = LocalRunner.builder().functionConfig(cfg).build()) {
            localRunner.start(false);
        }
    }
}
