package org.ostelco.prime.client.api.store

import io.vavr.control.Either
import io.vavr.control.Option
import org.ostelco.prime.client.api.model.Consent
import org.ostelco.prime.client.api.model.Person
import org.ostelco.prime.client.api.model.SubscriptionStatus
import org.ostelco.prime.core.ApiError
import org.ostelco.prime.model.ApplicationToken
import org.ostelco.prime.model.Product
import org.ostelco.prime.model.PurchaseRecord
import org.ostelco.prime.model.Subscriber
import org.ostelco.prime.model.Subscription
import org.ostelco.prime.paymentprocessor.core.ProfileInfo

/**
 *
 */
interface SubscriberDAO {

    fun getProfile(subscriberId: String): Either<ApiError, Subscriber>

    fun createProfile(subscriberId: String, profile: Subscriber, referredBy: String?): Either<ApiError, Subscriber>

    fun updateProfile(subscriberId: String, profile: Subscriber): Either<ApiError, Subscriber>

    @Deprecated("use getSubscriptions", ReplaceWith("getSubscriptions", "org.ostelco.prime.client.api.model.Subscription"))
    fun getSubscriptionStatus(subscriberId: String): Either<ApiError, SubscriptionStatus>

    fun getSubscriptions(subscriberId: String): Either<ApiError, Collection<Subscription>>

    fun getPurchaseHistory(subscriberId: String): Either<ApiError, Collection<PurchaseRecord>>

    fun getProduct(subscriptionId: String, sku: String): Either<ApiError, Product>

    fun getMsisdn(subscriberId: String): Either<ApiError, String>

    fun getProducts(subscriberId: String): Either<ApiError, Collection<Product>>

    fun purchaseProduct(subscriberId: String, sku: String): Option<ApiError>

    fun getConsents(subscriberId: String): Either<ApiError, Collection<Consent>>

    fun acceptConsent(subscriberId: String, consentId: String): Either<ApiError, Consent>

    fun rejectConsent(subscriberId: String, consentId: String): Either<ApiError, Consent>

    fun reportAnalytics(subscriberId: String, events: String): Option<ApiError>

    fun storeApplicationToken(msisdn: String, applicationToken: ApplicationToken): Either<ApiError, ApplicationToken>

    fun getPaymentProfile(name: String): Either<ApiError, ProfileInfo>

    fun setPaymentProfile(name: String, profileInfo: ProfileInfo): Option<ApiError>

    fun getReferrals(subscriberId: String): Either<ApiError, Collection<Person>>

    fun getReferredBy(subscriberId: String): Either<ApiError, Person>

    companion object {

        /**
         * Profile is only valid when name and email set.
         */
        fun isValidProfile(profile: Subscriber?): Boolean {
            return (profile != null
                    && !profile.name.isEmpty()
                    && !profile.email.isEmpty())
        }

        /**
         * The application token is only valid if token,
         * applicationID and token type is set.
         */
        fun isValidApplicationToken(appToken: ApplicationToken?): Boolean {
            return (appToken != null
                    && !appToken.token.isEmpty()
                    && !appToken.applicationID.isEmpty()
                    && !appToken.tokenType.isEmpty())
        }
    }
}
