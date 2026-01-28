package com.networknt.scheduler;

import com.networknt.kafka.common.config.KafkaStreamsConfig;
import com.networknt.kafka.streams.LightStreams;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class SchedulerStreams implements LightStreams {
    static private final Logger logger = LoggerFactory.getLogger(SchedulerStreams.class);
    static final KafkaStreamsConfig config = KafkaStreamsConfig.load();

    private KafkaStreams schedulerStreams;

    private void startSchedulerStreams(String ip, int port) {
        TaskSchedulingStreamTopology topology = new TaskSchedulingStreamTopology();
        Properties streamsProps = new Properties();
        streamsProps.putAll(config.getProperties().getMergedProperties());
        streamsProps.put(StreamsConfig.APPLICATION_SERVER_CONFIG, ip + ":" + port);
        schedulerStreams = new KafkaStreams(topology.buildTaskStreamingTopology(), streamsProps);
        schedulerStreams.setUncaughtExceptionHandler(ex -> {
            logger.error("Kafka-Streams uncaught exception occurred. Stream will be replaced with new thread", ex);
            return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
        });

        if(config.getCleanUp()) {
            schedulerStreams.cleanUp();
        }
        schedulerStreams.start();
    }

    @Override
    public void start(String ip, int port) {
        if(logger.isDebugEnabled()) logger.info("ServiceStreams is starting...");
        startSchedulerStreams(ip, port);
    }

    @Override
    public void close() {
        if(logger.isDebugEnabled()) logger.info("ServiceStreams is closing...");
        schedulerStreams.close();
    }

    public KafkaStreams getKafkaStreams() {
        return schedulerStreams;
    }
}
