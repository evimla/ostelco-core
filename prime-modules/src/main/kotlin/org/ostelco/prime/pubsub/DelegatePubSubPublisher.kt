package org.ostelco.prime.pubsub

import com.google.api.core.ApiFuture
import com.google.api.gax.core.NoCredentialsProvider
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.FixedTransportChannelProvider
import com.google.cloud.pubsub.v1.Publisher
import com.google.pubsub.v1.ProjectTopicName
import com.google.pubsub.v1.PubsubMessage
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit


class DelegatePubSubPublisher(
        private val topicId: String,
        private val projectId: String) : PubSubPublisher {

    private lateinit var publisher: Publisher

    override lateinit var singleThreadScheduledExecutor: ScheduledExecutorService

    override fun start() {
        singleThreadScheduledExecutor = Executors.newSingleThreadScheduledExecutor()

        val topicName = ProjectTopicName.of(projectId, topicId)
        val strSocketAddress = System.getenv("PUBSUB_EMULATOR_HOST")

        publisher = if (!strSocketAddress.isNullOrEmpty()) {
            val channel = ManagedChannelBuilder.forTarget(strSocketAddress)
                    .usePlaintext()
                    .build()
            /* Create a publishers instance with default settings bound
               to the topic. */
            val channelProvider = FixedTransportChannelProvider
                    .create(GrpcTransportChannel.create(channel))
            Publisher.newBuilder(topicName)
                    .setChannelProvider(channelProvider)
                    .setCredentialsProvider(NoCredentialsProvider())
                    .build()
        } else {
            Publisher.newBuilder(topicName).build()
        }
    }

    override fun stop() {
        publisher.shutdown()
        publisher.awaitTermination(1, TimeUnit.MINUTES)
        singleThreadScheduledExecutor.shutdown()
    }

    override fun publishPubSubMessage(pubsubMessage: PubsubMessage): ApiFuture<String> =
            publisher.publish(pubsubMessage)
}