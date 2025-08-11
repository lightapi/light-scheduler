package com.networknt.scheduler;

import com.networknt.config.Config;
import com.networknt.kafka.common.config.KafkaStreamsConfig;
import com.networknt.service.SingletonServiceFactory;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.*;
import com.networknt.scheduler.transformer.TaskSchedulingTransformerSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskSchedulingStreamTopology {
    private final static Logger logger = LoggerFactory.getLogger(TaskSchedulingStreamTopology.class);
    static final KafkaStreamsConfig streamsConfig = KafkaStreamsConfig.load();
    static final SchedulerConfig schedulerConfig = (SchedulerConfig) Config.getInstance().getJsonObjectConfig(SchedulerConfig.CONFIG_NAME, SchedulerConfig.class);

    public Topology buildTaskStreamingTopology() {
        // create a Stream Builder instance
        final StreamsBuilder streamsBuilder = new StreamsBuilder();

        // add necessary stores to the stream builder
        createVariousTaskDefinitionStateStore(streamsBuilder);

        // Created Serde to consume the tasks from topics
        final Consumed<TaskDefinitionKey, TaskDefinition> consumed = Consumed.with(getTaskDefinitionKeySpecificAvroSerde(),
                getTaskDefinitionSpecificAvroSerde());

        // Create the stream to consume the tasks/tasks definitions from the tasks topics
        final KStream<TaskDefinitionKey, TaskDefinition> keyTaskDefinitionKStream = streamsBuilder
                .stream(schedulerConfig.getInputTopic(), consumed);

        // add processors using DSL and PAPI
        keyTaskDefinitionKStream
                .peek((taskDefinitionKey, taskDefinition) -> logger.info("Received Task Key {}, Definition {}", taskDefinitionKey, taskDefinition))
                .transform(new TaskSchedulingTransformerSupplier(), SchedulerConstants.TASK_STORES)
                .to(new TargetTopicNameExtractor(), Produced.with(getTaskDefinitionKeySpecificAvroSerde(), getTaskDefinitionSpecificAvroSerde()));
        ;

        // build and return the Topology
        return streamsBuilder.build();
    }

    private static SpecificAvroSerde<TaskDefinitionKey> getTaskDefinitionKeySpecificAvroSerde() {
        final SpecificAvroSerde<TaskDefinitionKey> changeEventSpecificAvroSerde = new SpecificAvroSerde<>(SingletonServiceFactory.getBean(SchemaRegistryClient.class));
        changeEventSpecificAvroSerde.configure(streamsConfig.getProperties().getMergedProperties(), true);
        return changeEventSpecificAvroSerde;
    }

    private static SpecificAvroSerde<TaskDefinition> getTaskDefinitionSpecificAvroSerde() {
        final SpecificAvroSerde<TaskDefinition> changeEventSpecificAvroSerde = new SpecificAvroSerde<>(SingletonServiceFactory.getBean(SchemaRegistryClient.class));
        changeEventSpecificAvroSerde.configure(streamsConfig.getProperties().getMergedProperties(), false);
        return changeEventSpecificAvroSerde;
    }

    /**
     * Helper method creates various Task Stores and add them to the Stream Builder object.
     *
     * @param streamsBuilder
     */
    private void createVariousTaskDefinitionStateStore(final StreamsBuilder streamsBuilder) {
        for (String storeName : SchedulerConstants.TASK_STORES) {
            // Create persistence store
            final KeyValueBytesStoreSupplier keyValueBytesStoreSupplier = Stores.persistentKeyValueStore(storeName);

            // create the store builder
            final StoreBuilder<KeyValueStore<TaskDefinitionKey, TaskDefinition>> storeBuilder = Stores.keyValueStoreBuilder(
                    keyValueBytesStoreSupplier, getTaskDefinitionKeySpecificAvroSerde(), getTaskDefinitionSpecificAvroSerde());

            // add the store to the stream builder
            streamsBuilder.addStateStore(storeBuilder);

            if(logger.isInfoEnabled()) logger.info("Created KeyValue Store with name {}", storeName);
        }
    }
}
