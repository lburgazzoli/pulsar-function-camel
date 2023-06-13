package com.github.lburgazzoli.camel.pulsar;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.*;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.schema.GenericObject;
import org.apache.pulsar.functions.api.Context;
import org.apache.pulsar.functions.api.Record;

public class CamelFunction implements org.apache.pulsar.functions.api.Function<GenericObject, Record<GenericObject>> {
    public static final String CONFIG_KEY_STEPS = "steps";

    private final CamelContext camel;
    private final ProducerTemplate template;

    public CamelFunction() {
        this.camel = new DefaultCamelContext();
        this.template = this.camel.createProducerTemplate();
    }

    @Override
    public void initialize(Context context) throws Exception {
        Resource res = CamelFunctionSupport.loadSteps(context);
        PluginHelper.getRoutesLoader(camel).loadRoutes(res);

        camel.start();
    }

    @Override
    public void close() throws Exception {
        this.camel.close();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public Record<GenericObject> process(GenericObject input, Context context) throws Exception {
        Object nativeObject = input.getNativeObject();

        Record<?> record = context.getCurrentRecord();

        context.getLogger().info(
            "apply to {} {} ({})",
            input,
            nativeObject,
            nativeObject.getClass());
        context.getLogger().info(
            "record with schema {} version {} {}",
            record.getSchema(),
            record.getMessage().orElseThrow().getSchemaVersion(),
            record);

        Exchange result = template.request("direct:" + context.getFunctionId(), e -> {
            Message m = e.getMessage();
            m.setBody(input.getNativeObject());
            e.setProperty("pulsar.apache.org/function.id", context.getFunctionId());
            e.setProperty("pulsar.apache.org/function.output", context.getOutputTopic());
            e.setProperty("pulsar.apache.org/record.topic", record.getTopicName());
            e.setProperty("pulsar.apache.org/record.schema", record.getSchema());

            record.getKey().ifPresent(k -> {
                e.setProperty("pulsar.apache.org/record.key", k);
            });
            record.getPartitionId().ifPresent(k -> {
                e.setProperty("pulsar.apache.org/record.partition.id", k);
            });
            record.getPartitionIndex().ifPresent(k -> {
                e.setProperty("pulsar.apache.org/record.partition.index", k);
            });

            context.getCurrentRecord().getProperties().forEach(m::setHeader);
        });

        String outTopic = result.getProperty(
            "pulsar.apache.org/function.output",
            context.getOutputTopic(),
            String.class);
        Schema outSchema = result.getProperty(
            "pulsar.apache.org/record.schema",
            record.getSchema(),
            Schema.class);

        Map<String, String> outProperties = new HashMap<>();

        result.getMessage().getHeaders().keySet().forEach(k -> {
            try {
                outProperties.put(k, result.getMessage().getHeader(k, String.class));
            } catch (TypeConversionException e) {
                context.getLogger().warn("", e);
            }
        });

        return context
            .newOutputRecordBuilder(outSchema)
            .destinationTopic(outTopic)
            .value(result.getMessage().getBody())
            .properties(outProperties)
            .build();
    }
}
