package org.ostelco.at.pgw

import org.jdiameter.api.Avp
import org.jdiameter.api.Session
import org.junit.*
import org.ostelco.at.common.createCustomer
import org.ostelco.at.common.createSubscription
import org.ostelco.at.common.getLogger
import org.ostelco.at.common.randomInt
import org.ostelco.at.jersey.get
import org.ostelco.diameter.model.FinalUnitAction
import org.ostelco.diameter.model.RequestType
import org.ostelco.diameter.test.TestClient
import org.ostelco.diameter.test.TestHelper
import org.ostelco.prime.customer.model.Bundle
import java.lang.Thread.sleep
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Integration tests for the OcsApplication. This test uses the diameter-test lib to setup a test P-GW to
 * actually send Diameter traffic on the selected DataSource to the OcsApplication. The
 * DataSource used is the one in the configuration file for this resources.
 *
 */
class OcsTest {

    private val logger by getLogger()

    private fun simpleCreditControlRequestInit(session : Session,
                                               msisdn : String,
                                               requestedBucketSize : Long,
                                               expectedGrantedBucketSize : Long,
                                               ratingGroup : Int,
                                               serviceIdentifier : Int) {

        val request = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")

        TestHelper.createInitRequest(request.avps, msisdn, requestedBucketSize, ratingGroup, serviceIdentifier)

        testClient.sendNextRequest(request, session)

        waitForAnswer(session.sessionId)

        val result = testClient.getAnswer(session.sessionId)
        assertEquals(DIAMETER_SUCCESS, result?.resultCode)
        val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
        assertEquals(RequestType.INITIAL_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
        assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
        assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
        val resultMSCC = resultAvps.getAvp(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL)
        assertEquals(DIAMETER_SUCCESS, resultMSCC.grouped.getAvp(Avp.RESULT_CODE).integer32.toLong())
        if (serviceIdentifier > 0) {
            assertEquals(serviceIdentifier.toLong(), resultMSCC.grouped.getAvp(Avp.SERVICE_IDENTIFIER_CCA).unsigned32)
        }
        if (ratingGroup > 0) {
            assertEquals(ratingGroup.toLong(), resultMSCC.grouped.getAvp(Avp.RATING_GROUP).unsigned32)
        }
        val granted = resultMSCC.grouped.getAvp(Avp.GRANTED_SERVICE_UNIT)
        assertEquals(expectedGrantedBucketSize, granted.grouped.getAvp(Avp.CC_TOTAL_OCTETS).unsigned64)
    }

    private fun simpleCreditControlRequestUpdate(session: Session,
                                                 msisdn: String,
                                                 requestedBucketSize : Long,
                                                 usedBucketSize : Long,
                                                 expectedGrantedBucketSize : Long,
                                                 ratingGroup : Int,
                                                 serviceIdentifier : Int) {

        val request = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")

        TestHelper.createUpdateRequest(request.avps, msisdn, requestedBucketSize, usedBucketSize, ratingGroup, serviceIdentifier)

        testClient.sendNextRequest(request, session)

        waitForAnswer(session.sessionId)

        val result = testClient.getAnswer(session.sessionId)
        assertEquals(DIAMETER_SUCCESS, result?.resultCode)
        val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
        assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
        assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
        assertEquals(RequestType.UPDATE_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
        val resultMSCC = resultAvps.getAvp(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL)
        assertEquals(DIAMETER_SUCCESS, resultMSCC.grouped.getAvp(Avp.RESULT_CODE).integer32.toLong())
        if (serviceIdentifier > 0) {
            assertEquals(serviceIdentifier.toLong(), resultMSCC.grouped.getAvp(Avp.SERVICE_IDENTIFIER_CCA).unsigned32)
        }
        if (ratingGroup > 0) {
            assertEquals(ratingGroup.toLong(), resultMSCC.grouped.getAvp(Avp.RATING_GROUP).unsigned32)
        }
        val granted = resultMSCC.grouped.getAvp(Avp.GRANTED_SERVICE_UNIT)
        assertEquals(expectedGrantedBucketSize, granted.grouped.getAvp(Avp.CC_TOTAL_OCTETS).unsigned64)
    }

    private fun getBalance(email: String): Long {
        sleep(200) // wait for 200 ms for balance to be updated in db

        return get<List<Bundle>> {
            path = "/bundles"
            this.email = email
        }.first().balance
    }


    @Test
    fun multiRatingGroupsInit() {

        val email = "ocs-${randomInt()}@test.com"
        createCustomer(name = "Test OCS User", email = email)

        val msisdn = createSubscription(email = email)

        val session = testClient.createSession(object{}.javaClass.enclosingMethod.name) ?: fail("Failed to create session")
        val request = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")

        TestHelper.createInitRequestMultiRatingGroups(request.avps, msisdn, 5000L)

        testClient.sendNextRequest(request, session)

        waitForAnswer(session.sessionId)

        val result = testClient.getAnswer(session.sessionId)
        assertEquals(DIAMETER_SUCCESS, result?.resultCode)
        val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
        assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
        assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
        assertEquals(RequestType.INITIAL_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
        val resultMSCCs = resultAvps.getAvps(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL)
        assertEquals(3, resultMSCCs.size().toLong())
        for (i in 0 until resultMSCCs.size()) {
            val mscc = resultMSCCs.getAvpByIndex(i).grouped
            assertEquals(DIAMETER_SUCCESS, mscc.getAvp(Avp.RESULT_CODE).integer32.toLong())
            val granted = mscc.getAvp(Avp.GRANTED_SERVICE_UNIT)
            assertEquals(5000L, granted.grouped.getAvp(Avp.CC_TOTAL_OCTETS).unsigned64)
            when (mscc.getAvp(Avp.SERVICE_IDENTIFIER_CCA).unsigned32.toInt()) {
                1 -> assertEquals(10, mscc.getAvp(Avp.RATING_GROUP).unsigned32)
                2 -> assertEquals(12, mscc.getAvp(Avp.RATING_GROUP).unsigned32)
                4 -> assertEquals(14, mscc.getAvp(Avp.RATING_GROUP).unsigned32)
                else -> fail("Unexpected Service-Identifier")
            }
        }
    }

    //@Test
    // This is disabled until this is implemented in the store
    fun multiRatingGroupsInitUserUnknown() {

        val session = testClient.createSession(object{}.javaClass.enclosingMethod.name) ?: fail("Failed to create session")
        val request = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")

        TestHelper.createInitRequestMultiRatingGroups(request.avps, "4794763521", 5000L)

        testClient.sendNextRequest(request, session)

        waitForAnswer(session.sessionId)

        val result = testClient.getAnswer(session.sessionId)
        assertEquals(DIAMETER_USER_UNKNOWN, result?.resultCode)
        val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
        assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
        assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
        assertEquals(RequestType.INITIAL_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
        val resultMSCCs = resultAvps.getAvps(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL)
        assertEquals(3, resultMSCCs.size().toLong())
        for (i in 0 until resultMSCCs.size()) {
            val mscc = resultMSCCs.getAvpByIndex(i).grouped
            assertEquals(DIAMETER_USER_UNKNOWN, mscc.getAvp(Avp.RESULT_CODE).integer32.toLong())
            val granted = mscc.getAvp(Avp.GRANTED_SERVICE_UNIT)
            assertEquals(0L, granted.getGrouped().getAvp(Avp.CC_TOTAL_OCTETS).unsigned64)
            when (mscc.getAvp(Avp.SERVICE_IDENTIFIER_CCA).unsigned32.toInt()) {
                1 -> assertEquals(10, mscc.getAvp(Avp.RATING_GROUP).unsigned32)
                2 -> assertEquals(12, mscc.getAvp(Avp.RATING_GROUP).unsigned32)
                4 -> assertEquals(14, mscc.getAvp(Avp.RATING_GROUP).unsigned32)
                else -> fail("Unexpected Service-Identifier")
            }
        }
    }

    @Test
    fun simpleCreditControlRequestInitUpdateAndTerminate() {

        val email = "ocs-${randomInt()}@test.com"
        createCustomer(name = "Test OCS User", email = email)

        val msisdn = createSubscription(email = email)

        val ratingGroup = 10
        val serviceIdentifier = 1

        val session = testClient.createSession(object{}.javaClass.enclosingMethod.name) ?: fail("Failed to create session")
        simpleCreditControlRequestInit(session, msisdn, BUCKET_SIZE, BUCKET_SIZE, ratingGroup, serviceIdentifier)
        assertEquals(INITIAL_BALANCE - BUCKET_SIZE, getBalance(email = email), message = "Incorrect balance after init")

        simpleCreditControlRequestUpdate(session, msisdn, BUCKET_SIZE, BUCKET_SIZE, BUCKET_SIZE, ratingGroup, serviceIdentifier)
        assertEquals(INITIAL_BALANCE - 2 * BUCKET_SIZE, getBalance(email = email), message = "Incorrect balance after update")

        val request = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")

        TestHelper.createTerminateRequest(request.avps, msisdn, BUCKET_SIZE, ratingGroup, serviceIdentifier)

        testClient.sendNextRequest(request, session)

        waitForAnswer(session.sessionId)

        val result = testClient.getAnswer(session.sessionId)
        assertEquals(DIAMETER_SUCCESS, result?.resultCode)
        val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
        assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
        assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
        assertEquals(RequestType.TERMINATION_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())

        assertEquals(INITIAL_BALANCE - 2 * BUCKET_SIZE, getBalance(email = email), message = "Incorrect balance after terminate")
    }


    // @Test Final-Unit-Indication is not in use
    fun creditControlRequestInitTerminateNoCreditFUI() {

        val email = "ocs-${randomInt()}@test.com"
        createCustomer(name = "Test OCS User", email = email)

        val msisdn = createSubscription(email = email)

        val ratingGroup = 10
        val serviceIdentifier = 1

        var session = testClient.createSession(object{}.javaClass.enclosingMethod.name) ?: fail("Failed to create session")
        var request = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")


        // Requesting one more bucket then the balance for the user
        TestHelper.createInitRequest(request.avps, msisdn, INITIAL_BALANCE + BUCKET_SIZE, ratingGroup, serviceIdentifier)

        testClient.sendNextRequest(request, session)

        waitForAnswer(session.sessionId)

        // First request should reserve the full balance
        run {
            val result = testClient.getAnswer(session.sessionId)
            assertEquals(DIAMETER_SUCCESS, result?.resultCode)
            val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
            assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
            assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
            assertEquals(RequestType.INITIAL_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
            val resultMSCC = resultAvps.getAvp(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL)
            assertEquals(DIAMETER_SUCCESS, resultMSCC.grouped.getAvp(Avp.RESULT_CODE).integer32.toLong())
            assertEquals(serviceIdentifier.toLong(), resultMSCC.grouped.getAvp(Avp.SERVICE_IDENTIFIER_CCA).integer32.toLong())
            assertEquals(ratingGroup.toLong(), resultMSCC.grouped.getAvp(Avp.RATING_GROUP).integer32.toLong())
            val granted = resultMSCC.grouped.getAvp(Avp.GRANTED_SERVICE_UNIT)
            assertEquals(INITIAL_BALANCE, granted.grouped.getAvp(Avp.CC_TOTAL_OCTETS).unsigned64)
            val finalUnitIndication = resultMSCC.grouped.getAvp(Avp.FINAL_UNIT_INDICATION)
            assertEquals(FinalUnitAction.TERMINATE.ordinal, finalUnitIndication.grouped.getAvp(Avp.FINAL_UNIT_ACTION).integer32)
        }
        // There is 2 step in graceful shutdown. First OCS send terminate in Final-Unit-Indication, then P-GW report used units in a final update

        val updateRequest = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")

        TestHelper.createUpdateRequestFinal(updateRequest.avps, msisdn, INITIAL_BALANCE, ratingGroup, serviceIdentifier)

        testClient.sendNextRequest(updateRequest, session)

        waitForAnswer(session.sessionId)

        run {
            val result = testClient.getAnswer(session.sessionId)
            assertEquals(DIAMETER_SUCCESS, result?.resultCode)
            val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
            assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
            assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
            assertEquals(RequestType.UPDATE_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
        }

        // Last step is P-GW sending CCR-Terminate
        val terminateRequest = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")
        TestHelper.createTerminateRequest(terminateRequest.avps, msisdn)

        testClient.sendNextRequest(terminateRequest, session)

        waitForAnswer(session.sessionId)

        run {
            val result = testClient.getAnswer(session.sessionId)
            assertEquals(DIAMETER_SUCCESS, result?.resultCode)
            val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
            assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
            assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
            assertEquals(RequestType.TERMINATION_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
        }

        // If P-GW tries another CCR-I we should reply DIAMETER_CREDIT_LIMIT_REACHED

        session = testClient.createSession(object{}.javaClass.enclosingMethod.name + "-2") ?: fail("Failed to create session")
        request = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")


        // Requesting one more bucket, the balance should be zero now
        TestHelper.createInitRequest(request.avps, msisdn, BUCKET_SIZE, ratingGroup, serviceIdentifier)

        testClient.sendNextRequest(request, session)

        waitForAnswer(session.sessionId)

        // First request should reserve the full balance
        run {
            val result = testClient.getAnswer(session.sessionId)
            assertEquals(DIAMETER_SUCCESS, result?.resultCode)
            val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
            assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
            assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
            assertEquals(RequestType.INITIAL_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
            val resultMSCC = resultAvps.getAvp(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL)
            assertEquals(DIAMETER_CREDIT_LIMIT_REACHED, resultMSCC.grouped.getAvp(Avp.RESULT_CODE).integer32.toLong())
            assertEquals(serviceIdentifier.toLong(), resultMSCC.grouped.getAvp(Avp.SERVICE_IDENTIFIER_CCA).integer32.toLong())
            assertEquals(ratingGroup.toLong(), resultMSCC.grouped.getAvp(Avp.RATING_GROUP).integer32.toLong())
            val granted = resultMSCC.grouped.getAvp(Avp.GRANTED_SERVICE_UNIT)
            assertEquals(0L, granted.grouped.getAvp(Avp.CC_TOTAL_OCTETS).unsigned64)
        }
    }


    @Test
    fun creditControlRequestInitTerminateNoCredit() {

        val email = "ocs-${randomInt()}@test.com"
        createCustomer(name = "Test OCS User", email = email)

        val msisdn = createSubscription(email = email)

        val ratingGroup = 10
        val serviceIdentifier = 1

        var session = testClient.createSession(object{}.javaClass.enclosingMethod.name) ?: fail("Failed to create session")
        var request = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")


        // Requesting one more bucket then the balance for the user
        TestHelper.createInitRequest(request.avps, msisdn, INITIAL_BALANCE + BUCKET_SIZE, ratingGroup, serviceIdentifier)

        testClient.sendNextRequest(request, session)

        waitForAnswer(session.sessionId)

        // First request should reserve the full balance
        run {
            val result = testClient.getAnswer(session.sessionId)
            assertEquals(DIAMETER_SUCCESS, result?.resultCode)
            val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
            assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
            assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
            assertEquals(RequestType.INITIAL_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
            val resultMSCC = resultAvps.getAvp(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL)
            assertEquals(DIAMETER_SUCCESS, resultMSCC.grouped.getAvp(Avp.RESULT_CODE).integer32.toLong())
            assertEquals(serviceIdentifier.toLong(), resultMSCC.grouped.getAvp(Avp.SERVICE_IDENTIFIER_CCA).integer32.toLong())
            assertEquals(ratingGroup.toLong(), resultMSCC.grouped.getAvp(Avp.RATING_GROUP).integer32.toLong())
            val granted = resultMSCC.grouped.getAvp(Avp.GRANTED_SERVICE_UNIT)
            assertEquals(INITIAL_BALANCE, granted.grouped.getAvp(Avp.CC_TOTAL_OCTETS).unsigned64)
        }

        // Next request should deny request and grant no quota

        val updateRequest = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")

        TestHelper.createUpdateRequest(updateRequest.avps, msisdn, BUCKET_SIZE, INITIAL_BALANCE, ratingGroup, serviceIdentifier)

        testClient.sendNextRequest(updateRequest, session)

        waitForAnswer(session.sessionId)

        run {
            val result = testClient.getAnswer(session.sessionId)
            assertEquals(DIAMETER_SUCCESS, result?.resultCode)
            val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
            assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
            assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
            assertEquals(RequestType.UPDATE_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
            val resultMSCC = resultAvps.getAvp(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL)
            assertEquals(DIAMETER_CREDIT_LIMIT_REACHED, resultMSCC.grouped.getAvp(Avp.RESULT_CODE).integer32.toLong())
            assertEquals(serviceIdentifier.toLong(), resultMSCC.grouped.getAvp(Avp.SERVICE_IDENTIFIER_CCA).integer32.toLong())
            assertEquals(ratingGroup.toLong(), resultMSCC.grouped.getAvp(Avp.RATING_GROUP).integer32.toLong())
            val granted = resultMSCC.grouped.getAvp(Avp.GRANTED_SERVICE_UNIT)
            assertEquals(0L, granted.grouped.getAvp(Avp.CC_TOTAL_OCTETS).unsigned64)
        }

        // Simulate UE disconnect by P-GW sending CCR-Terminate
        val terminateRequest = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")
        TestHelper.createTerminateRequest(terminateRequest.avps, msisdn)

        testClient.sendNextRequest(terminateRequest, session)

        waitForAnswer(session.sessionId)

        run {
            val result = testClient.getAnswer(session.sessionId)
            assertEquals(DIAMETER_SUCCESS, result?.resultCode)
            val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
            assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
            assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
            assertEquals(RequestType.TERMINATION_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
        }

        // If UE attach again and P-GW tries another CCR-I we should get DIAMETER_CREDIT_LIMIT_REACHED

        session = testClient.createSession(object{}.javaClass.enclosingMethod.name + "-2") ?: fail("Failed to create session")
        request = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")


        // Requesting one more bucket, the balance should be zero now
        TestHelper.createInitRequest(request.avps, msisdn, BUCKET_SIZE, ratingGroup, serviceIdentifier)

        testClient.sendNextRequest(request, session)

        waitForAnswer(session.sessionId)

        // First request should reserve the full balance
        run {
            val result = testClient.getAnswer(session.sessionId)
            assertEquals(DIAMETER_SUCCESS, result?.resultCode)
            val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
            assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
            assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
            assertEquals(RequestType.INITIAL_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
            val resultMSCC = resultAvps.getAvp(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL)
            assertEquals(DIAMETER_CREDIT_LIMIT_REACHED, resultMSCC.grouped.getAvp(Avp.RESULT_CODE).integer32.toLong())
            assertEquals(serviceIdentifier.toLong(), resultMSCC.grouped.getAvp(Avp.SERVICE_IDENTIFIER_CCA).integer32.toLong())
            assertEquals(ratingGroup.toLong(), resultMSCC.grouped.getAvp(Avp.RATING_GROUP).integer32.toLong())
            val granted = resultMSCC.grouped.getAvp(Avp.GRANTED_SERVICE_UNIT)
            assertEquals(0L, granted.grouped.getAvp(Avp.CC_TOTAL_OCTETS).unsigned64)
        }

    }


    @Test
    fun creditControlRequestInitUnknownUser() {

        val session = testClient.createSession(object{}.javaClass.enclosingMethod.name) ?: fail("Failed to create session")
        val request = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")


        // Requesting bucket for msisdn not in our system
        TestHelper.createInitRequest(request.avps, "93682751", BUCKET_SIZE, 10, 1)

        testClient.sendNextRequest(request, session)

        waitForAnswer(session.sessionId)

        run {
            val result = testClient.getAnswer(session.sessionId)
            assertEquals(DIAMETER_USER_UNKNOWN, result?.resultCode)
            val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
            assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
            assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
            assertEquals(RequestType.INITIAL_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
        }
    }

    @Test
    fun creditControlRequestInitNoServiceId() {

        val email = "ocs-${randomInt()}@test.com"
        createCustomer(name = "Test OCS User", email = email)

        val msisdn = createSubscription(email = email)

        val session = testClient.createSession(object{}.javaClass.enclosingMethod.name) ?: fail("Failed to create session")
        val request = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        ) ?: fail("Failed to create request")

        val ratingGroup = 10
        val serviceIdentifier = -1

        TestHelper.createInitRequest(request.avps, msisdn, BUCKET_SIZE, ratingGroup, serviceIdentifier)

        testClient.sendNextRequest(request, session)

        waitForAnswer(session.sessionId)

        run {
            val result = testClient.getAnswer(session.sessionId)
            assertEquals(DIAMETER_SUCCESS, result?.resultCode)
            val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
            assertEquals(DEST_HOST, resultAvps.getAvp(Avp.ORIGIN_HOST).utF8String)
            assertEquals(DEST_REALM, resultAvps.getAvp(Avp.ORIGIN_REALM).utF8String)
            assertEquals(RequestType.INITIAL_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
            val resultMSCC = resultAvps.getAvp(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL)
            assertEquals(DIAMETER_SUCCESS, resultMSCC.grouped.getAvp(Avp.RESULT_CODE).integer32.toLong())
            assertEquals(ratingGroup.toLong(), resultMSCC.grouped.getAvp(Avp.RATING_GROUP).integer32.toLong())
            val granted = resultMSCC.grouped.getAvp(Avp.GRANTED_SERVICE_UNIT)
            assertEquals(BUCKET_SIZE, granted.grouped.getAvp(Avp.CC_TOTAL_OCTETS).unsigned64)
        }
    }

    @Test
    fun creditControlRequestInitUpdateAndTerminateNoRequestedServiceUnit() {

        val email = "ocs-${randomInt()}@test.com"
        createCustomer(name = "Test OCS User", email = email)

        val msisdn = createSubscription(email = email)

        val ratingGroup = 10
        val serviceIdentifier = -1

        val session = testClient.createSession(object{}.javaClass.enclosingMethod.name) ?: fail("Failed to create session")

        // This test assume that the default bucket size is set to 4000000L
        simpleCreditControlRequestInit(session, msisdn,0L, DEFAULT_REQUESTED_SERVICE_UNIT, ratingGroup, serviceIdentifier)
        simpleCreditControlRequestUpdate(session, msisdn, 0L, DEFAULT_REQUESTED_SERVICE_UNIT, DEFAULT_REQUESTED_SERVICE_UNIT, ratingGroup, serviceIdentifier)

        val request = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        )

        TestHelper.createTerminateRequest(request!!.avps, msisdn, DEFAULT_REQUESTED_SERVICE_UNIT, ratingGroup, serviceIdentifier)

        testClient.sendNextRequest(request, session)

        waitForAnswer(session.sessionId)

        val result = testClient.getAnswer(session.sessionId)
        assertEquals(DIAMETER_SUCCESS, result?.resultCode)
        val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
        assertEquals(RequestType.TERMINATION_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
    }



    @Test
    fun simpleCreditControlRequestInitUpdateNoRSU() {

        val email = "ocs-${randomInt()}@test.com"
        createCustomer(name = "Test OCS User", email = email)

        val msisdn = createSubscription(email = email)

        val ratingGroup = 10
        val serviceIdentifier = -1

        val session = testClient.createSession(object{}.javaClass.enclosingMethod.name) ?: fail("Failed to create session")

        // This test assume that the default bucket size is set to 4000000L
        simpleCreditControlRequestInit(session, msisdn,0L, DEFAULT_REQUESTED_SERVICE_UNIT, ratingGroup, serviceIdentifier)

        val request = testClient.createRequest(
                DEST_REALM,
                DEST_HOST,
                session
        )

        TestHelper.createUpdateRequest(request!!.avps, msisdn, -1L, DEFAULT_REQUESTED_SERVICE_UNIT, ratingGroup, serviceIdentifier)

        testClient.sendNextRequest(request, session)

        waitForAnswer(session.sessionId)

        val result = testClient.getAnswer(session.sessionId)
        assertEquals(DIAMETER_SUCCESS, result?.resultCode)
        val resultAvps = result?.resultAvps ?: fail("Missing AVPs")
        assertEquals(RequestType.UPDATE_REQUEST.toLong(), resultAvps.getAvp(Avp.CC_REQUEST_TYPE).integer32.toLong())
        assertEquals(86400L, resultAvps.getAvp(Avp.VALIDITY_TIME).integer32.toLong())
    }


    private fun waitForAnswer(sessionId: String) {

        var i = 0
        while (!testClient.isAnswerReceived(sessionId) && i < 10) {
            i++
            try {
                sleep(500)
            } catch (e: InterruptedException) {
                logger.error("Start Failed", e)
            }

        }
        assertEquals(true, testClient.isAnswerReceived(sessionId))
    }

    companion object {

        private const val DEST_REALM = "loltel"
        private const val DEST_HOST = "ocs"

        private const val INITIAL_BALANCE = 2_147_483_648L
        private const val BUCKET_SIZE = 500L
        private const val DEFAULT_REQUESTED_SERVICE_UNIT = 40_000_000L

        private const val DIAMETER_SUCCESS = 2001L
        private const val DIAMETER_CREDIT_LIMIT_REACHED = 4012L
        private const val DIAMETER_USER_UNKNOWN = 5030L

        // variables you initialize for the class later in the @BeforeClass method:
        lateinit var testClient: TestClient

        //configuration file
        private const val configFile = "client-jdiameter-config.xml"

        @BeforeClass @JvmStatic fun setup() {
            testClient = TestClient()
            testClient.initStack("/", configFile)
        }

        @AfterClass @JvmStatic fun teardown() {
            testClient.shutdown()
        }

    }
}
