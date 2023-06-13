# camel-pulsar-function

The Camel Pulsar Function is a regular [Pulsar Function](https://pulsar.apache.org/docs/functions-overview/) that aims to provide a low-code approach to apply [Enterprise Integration Patters](https://www.enterpriseintegrationpatterns.com/) to process and transform data by leveraging [Apache Camel](https://camel.apache.org/)'s integration capabilities.


## Configuration

The `CamelFunction` reads its configuration as `YAML` from the Function `userConfig` `steps` parameter, as example, a [function configuration file](https://pulsar.apache.org/docs/3.0.x/functions-cli/) could look like:

```yaml
tenant: "public"
namespace: "default"
name: "camel"
inputs:
  - "persistent://public/default/input-1"
output: "persistent://public/default/output-1"
jar: "build/libs/camel-pulsar-function-${version}-all.jar"
className: "com.github.lburgazzoli.camel.pulsar.CamelFunction"
logTopic: "persistent://public/default/logging-function-logs"
userConfig:
  steps: |
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
```

If you are familiar with the [Apache Camel YAML SQL](https://camel.apache.org/components/3.20.x/others/yaml-dsl.html), you have probably noticed that the route does not start with `from` or `route` as you would probably expect and this is because the Camel Pulsar Function automatically create all the boilerplate that are required to wire the Camel's routing engine to the Pulsar Function runtime. Beside this small difference, all the [Apache Camel EIPs](https://camel.apache.org/components/3.20.x/eips/enterprise-integration-patterns.html) are available.


### Access to contextual data

Some attributes of the function and record being processed are mapped to Camel's Exchange Properties:

| Pulsar                   | Exchnage Property Name                   |
|--------------------------|:-----------------------------------------|
| Function ID              | pulsar.apache.org/function.id            |
| Configured Output Topic  | pulsar.apache.org/function.output        |
| Record Topic             | pulsar.apache.org/record.topic           |
| Record Schema            | pulsar.apache.org/record.schema          |
| Record Key               | pulsar.apache.org/record.key             |
| Record Partition ID      | pulsar.apache.org/record.partition.id    |
| Record Partition Index   | pulsar.apache.org/record.partition.index |

### Routing

By default, the result of the processing pipeline is sent to the output topic defined in the function configuration, however it is possible to pick a different topic to apply a [Content-Based Routing pattern](https://www.enterpriseintegrationpatterns.com/patterns/messaging/ContentBasedRouter.html) as show in the example, by setting the exchange property `pulsar.apache.org/function.output`

## Deployment

See [the Pulsar docs](https://pulsar.apache.org/fr/docs/functions-deploy) for more details on how to deploy a Function.


