package org.ostelco.prime.storage.graph.model

import org.ostelco.prime.model.CustomerRegionStatus
import org.ostelco.prime.model.HasId
import org.ostelco.prime.model.KycStatus
import org.ostelco.prime.model.KycType

data class Identity(
        override val id: String,
        val type: String) : HasId {

    companion object
}

data class Identifies(val provider: String)

data class SubscriptionToBundle(val reservedBytes: Long = 0)

data class PlanSubscription(
        val subscriptionId: String,
        val created: Long,
        val trialEnd: Long)

data class CustomerRegion(
        val status: CustomerRegionStatus,
        val kycStatusMap: Map<KycType, KycStatus> = emptyMap())

data class SimProfile(
        override val id: String,
        val iccId: String,
        val alias: String = "") : HasId

data class Segment(override val id: String) : HasId

data class Offer(override val id: String) : HasId