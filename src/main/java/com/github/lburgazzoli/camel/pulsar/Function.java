package com.github.lburgazzoli.camel.pulsar;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.pulsar.client.api.schema.GenericObject;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.apache.pulsar.functions.api.Context;
import org.apache.pulsar.functions.api.Record;
import org.apache.pulsar.functions.api.utils.FunctionRecord;

public class Function implements org.apache.pulsar.functions.api.Function<byte[], Record<GenericObject>> {
    private static final String CONFIG_KEY_ROUTE = "route";

    private final CamelContext camel;
    private final ProducerTemplate template;

    public Function() {
        this.camel = new DefaultCamelContext();
        this.template = this.camel.createProducerTemplate();
    }

    @Override
    public void initialize(Context context) throws Exception {
        org.apache.pulsar.functions.api.Function.super.initialize(context);

        Resource res = context.getUserConfigValue(CONFIG_KEY_ROUTE)
            .map(String.class::cast)
            .map(in ->  ResourceHelper.fromString(context.getFunctionId() + ".yaml", in))
            .orElseThrow(() -> new IllegalArgumentException("Missing route config"));

        PluginHelper.getRoutesLoader(camel).loadRoutes(res);
    }

    @Override
    public void close() throws Exception {
        this.camel.close();
    }

    @Override
    public Record<GenericObject> process(byte[] input, Context context) throws Exception {
        Record<GenericRecord> record = (Record<GenericRecord>)context.getCurrentRecord();


        Exchange result = template.request("direct:in", exchange -> {
            Message m = exchange.getMessage();
            m.setBody(input);
            m.setHeader("pulsar.apache.org/function.id", context.getFunctionId());
            m.setHeader("pulsar.apache.org/function.topic.output", context.getOutputTopic());
            m.setHeader("pulsar.apache.org/record.topic", record.getTopicName());

            record.getKey().ifPresent(k -> {
                m.setHeader("pulsar.apache.org/record.key", k);
            });
            record.getPartitionId().ifPresent(k -> {
                m.setHeader("pulsar.apache.org/record.partition.id", k);
            });
            record.getPartitionIndex().ifPresent(k -> {
                m.setHeader("pulsar.apache.org/record.partition.index", k);
            });

            context.getCurrentRecord().getProperties().forEach(m::setHeader);
        });

        String outTopic = result.getMessage().getHeader(
            "pulsar.apache.org/function.topic.output",
            context.getOutputTopic(),
            String.class);

        return context
                .newOutputRecordBuilder(record.getSchema())
                .destinationTopic(outTopic)
                .value(result.getMessage().getBody())
                .properties(record.getProperties())
                .build();
    }
}
