package org.ostelco.prime.analytics.publishers

import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.api.gax.rpc.ApiException
import com.google.protobuf.util.Timestamps
import com.google.pubsub.v1.PubsubMessage
import org.ostelco.analytics.api.DataTrafficInfo
import org.ostelco.prime.analytics.ConfigRegistry
import org.ostelco.prime.getLogger
import java.time.Instant

/**
 * This class publishes the data consumption information events to the Google Cloud Pub/Sub.
 */
object DataConsumptionInfoPublisher :
        PubSubPublisher by DelegatePubSubPublisher(topicId = ConfigRegistry.config.dataTrafficTopicId) {

    private val logger by getLogger()

    fun publish(msisdnAnalyticsId: String, usedBucketBytes: Long, bundleBytes: Long, apn: String?, mccMnc: String?) {

        if (usedBucketBytes == 0L) {
            return
        }

        val now = Instant.now().toEpochMilli()

        val data = DataTrafficInfo.newBuilder()
                .setMsisdn(msisdnAnalyticsId)
                .setBucketBytes(usedBucketBytes)
                .setBundleBytes(bundleBytes)
                .setTimestamp(Timestamps.fromMillis(now))
                .setApn(apn)
                .setMccMnc(mccMnc)
                .build()
                .toByteString()

        val pubsubMessage = PubsubMessage.newBuilder()
                .setData(data)
                .build()

        //schedule a message to be published, messages are automatically batched
        val future = publishPubSubMessage(pubsubMessage)

        // add an asynchronous callback to handle success / failure
        ApiFutures.addCallback(future, object : ApiFutureCallback<String> {

            override fun onFailure(throwable: Throwable) {
                if (throwable is ApiException) {
                    // details on the API exception
                    logger.warn("Status code: {}", throwable.statusCode.code)
                    logger.warn("Retrying: {}", throwable.isRetryable)
                }
                logger.warn("Error publishing message for msisdnAnalyticsId: {}", msisdnAnalyticsId)
            }

            override fun onSuccess(messageId: String) {
                // Once published, returns server-assigned message ids (unique within the topic)
                logger.debug("Published message $messageId")
            }
        }, singleThreadScheduledExecutor)
    }
}
