package com.github.lburgazzoli.camel.pulsar;

import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceHelper;
import org.apache.pulsar.functions.api.Context;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public final class CamelFunctionSupport {
    private CamelFunctionSupport() {
    }

    public static Resource loadSteps(Context context) throws Exception {
        String in = context.getUserConfigValue(CamelFunction.CONFIG_KEY_STEPS)
            .map(String.class::cast)
            .orElseThrow(() -> new IllegalArgumentException("Missing steps config"));

        YAMLMapper mapper = new YAMLMapper();

        ArrayNode steps = mapper.readValue(in, ArrayNode.class);
        ArrayNode root = mapper.createArrayNode();
        ObjectNode route = root.addObject();

        route.withObject("/route")
            .put("group", String.format("%s/%s", context.getTenant(), context.getNamespace()))
            .put("id", context.getFunctionId())
            .withObject("/from")
                .put("uri", "direct:" + context.getFunctionId())
                .set("steps", steps);

        return ResourceHelper.fromString(
            context.getFunctionId() + ".yaml",
            mapper.writeValueAsString(root));
    }
}
