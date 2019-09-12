package org.ostelco.simcards.profilevendors

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.ostelco.prime.getLogger
import org.ostelco.prime.simmanager.AdapterError
import org.ostelco.prime.simmanager.NotFoundError
import org.ostelco.prime.simmanager.NotUpdatedError
import org.ostelco.prime.simmanager.SimManagerError
import org.ostelco.sim.es2plus.ES2RequestHeader
import org.ostelco.sim.es2plus.Es2ConfirmOrder
import org.ostelco.sim.es2plus.Es2ConfirmOrderResponse
import org.ostelco.sim.es2plus.Es2DownloadOrderResponse
import org.ostelco.sim.es2plus.Es2PlusDownloadOrder
import org.ostelco.sim.es2plus.Es2ProfileStatusCommand
import org.ostelco.sim.es2plus.Es2ProfileStatusResponse
import org.ostelco.sim.es2plus.Es2Response
import org.ostelco.sim.es2plus.FunctionExecutionStatus
import org.ostelco.sim.es2plus.FunctionExecutionStatusType
import org.ostelco.sim.es2plus.IccidListEntry
import org.ostelco.sim.es2plus.ProfileStatus
import org.ostelco.simcards.admin.ProfileVendorConfig
import org.ostelco.simcards.inventory.SimEntry
import org.ostelco.simcards.inventory.SimInventoryDAO
import org.ostelco.simcards.inventory.SmDpPlusState
import javax.ws.rs.core.MediaType

/**
 * An profile vendors that can connect to SIM profile vendors and activate
 * the requested SIM profile.
 *
 * Will connect to the SM-DP+  and then activate the profile, so that when
 * user equpiment tries to download a profile, it will get a profile to
 * download.
 *
 * TODO:  Why on earth is the json property set to "metricName"? It makes no sense.
 *        Fix it, but understand what it means.
 */
data class ProfileVendorAdapter(
        @JsonProperty("id") val id: Long,
        @JsonProperty("metricName") val name: String) {


    private val logger by getLogger()

    //  This class is currently the target of an ongoing refactoring.
    //   * Figure out what the "metricName" thing above is all about, is it the
    //     metrics used to track how man profiles are left or something? Check
    //     it out and document clearly in class comment above.
    //   * Look into SimInventoryApi.kt, read TODO about design flaw, then figure  out
    //     how to proceed in that direction.
    //   * See if the code can be made much clearer still by injecting HTTP client
    //     etc. as class parameters.  Perhaps a two-way method is best?  First
    //     get the data object from the database, then make another object that is used
    //     to do the actual adaptations based on parameters both from the database, and from
    //     the application (http clients, DAOs, etc.).
    //   * Then  replace both with invocations to the possibly updated
    //     ES2+ client library (possibly by moving these methods into that library, or wrapping them
    //     around ES2+ client library invocations, we'll see what seems like the best choice when the
    //     refactoring has progressed a little more).
    //   * Ensure that the protocol is extensively unit tested.

    companion object {

        // For logging in the companion object
        private val logger by getLogger()

        // For logging serialization/deserialization of JSON serialized ES2+
        // payloads.
        private val mapper = jacksonObjectMapper()

        private fun <T> buildEs2plusRequest(endpoint: String, esplusOrderName: String, payload: T): HttpUriRequest {
            val payloadString = mapper.writeValueAsString(payload)
            return RequestBuilder.post()
                    .setUri("${endpoint}/gsma/rsp2/es2plus/${esplusOrderName}")
                    .setHeader("User-Agent", "gsma-rsp-lpad")
                    .setHeader("X-Admin-Protocol", "gsma/rsp/v2.0.0")
                    .setHeader("Content-Type", MediaType.APPLICATION_JSON)
                    .setEntity(StringEntity(payloadString))
                    .build()
        }

        private fun executionWasFailure(status: FunctionExecutionStatus) =
                status.status != FunctionExecutionStatusType.ExecutedSuccess

        fun <T : Es2Response> executeRequest(
                es2CommandName: String,
                httpClient: CloseableHttpClient,
                request: HttpUriRequest,
                remoteServiceName: String,
                functionCallIdentifier: String,
                valueType: Class<T>,
                iccids: String,
                treatAsPing: Boolean = false): Either<SimManagerError, T> {
            return try {
                return httpClient.execute(request).use { httpResponse ->
                    when (httpResponse.statusLine.statusCode) {
                        200 -> {
                            val response = mapper.readValue(httpResponse.entity.content, valueType)
                            if (executionWasFailure(status = response.myHeader.functionExecutionStatus)) {
                                var msg = "SM-DP+ '$es2CommandName' message to service $remoteServiceName for ICCID $iccids failed with execution status ${response.myHeader.functionExecutionStatus} (call-id: ${functionCallIdentifier})"
                                logger.error(msg)
                                NotUpdatedError(msg, pingOk = treatAsPing).left()
                            } else {
                                response.right()
                            }
                        }
                        else -> {
                            var msg = "SM-DP+ '$es2CommandName' message to service $remoteServiceName for ICCID $iccids failed with status code ${httpResponse.statusLine.statusCode} (call-id: ${functionCallIdentifier})"
                            logger.error(msg)
                            NotUpdatedError(msg, pingOk = treatAsPing).left()
                        }
                    }
                }
            } catch (e: Throwable) {  // TODO: Is this even necessary?
                val msg = "SM-DP+ 'order-download' message to service $remoteServiceName for ICCID $iccids."
                logger.error(msg, e)
                AdapterError("${msg} failed with error: $e")
                        .left()
            }
        }
    }


    /**
     * Initiate activation of a SIM profile with an external Profile Vendor
     * by sending a SM-DP+ 'download-order' message.
     * @param httpClient  HTTP client
     * @param config SIM vendor specific configuration
     * @param dao  DB interface
     * @param simEntry  SIM profile to activate
     * @return Updated SIM profile
     */
    private fun downloadOrder(httpClient: CloseableHttpClient,
                              config: ProfileVendorConfig,
                              dao: SimInventoryDAO,
                              simEntry: SimEntry): Either<SimManagerError, SimEntry> {


        if (simEntry.id == null) {
            return NotUpdatedError("simEntry without id.  simEntry=$simEntry").left()
        }

        val header = ES2RequestHeader(
                functionRequesterIdentifier = config.requesterIdentifier)
        val request =
                buildEs2plusRequest<Es2PlusDownloadOrder>(config.getEndpoint(), "downloadOrder",
                        Es2PlusDownloadOrder(
                                header = header,
                                iccid = simEntry.iccid
                        ))

        return executeRequest<Es2DownloadOrderResponse>("order-download", httpClient, request, config.name, header.functionCallIdentifier, Es2DownloadOrderResponse::class.java, simEntry.iccid)
                .flatMap {
                    dao.setSmDpPlusState(simEntry.id, SmDpPlusState.ALLOCATED)
                }
    }

    /**
     * Complete the activation of a SIM profile with an external Profile Vendor
     * by sending a SM-DP+ 'confirmation' message.
     * @param httpClient  HTTP client
     * @param config SIM vendor specific configuration
     * @param dao  DB interface
     * @param eid  ESIM id
     * @param simEntry  SIM profile to activate
     * @return Updated SIM profile
     */
    private fun confirmOrder(httpClient: CloseableHttpClient,
                             config: ProfileVendorConfig,
                             dao: SimInventoryDAO,
                             eid: String? = null,
                             simEntry: SimEntry): Either<SimManagerError, SimEntry> {

        if (simEntry.id == null) {
            return NotUpdatedError("simEntry without id.  simEntry=$simEntry").left()
        }

        // TODO: This header is a constant for this class, and could be precomputed, which
        //       would declutter the code even more!
        val header = ES2RequestHeader(functionRequesterIdentifier = config.requesterIdentifier)
        val request =
                buildEs2plusRequest<Es2ConfirmOrder>(config.getEndpoint(), "confirmOrder",
                        Es2ConfirmOrder(
                                header = header,
                                eid = eid,
                                iccid = simEntry.iccid
                        ))

        return executeRequest<Es2ConfirmOrderResponse>(
                "confirmOrder",
                httpClient,
                request,
                config.name,
                header.functionCallIdentifier,
                Es2ConfirmOrderResponse::class.java,
                simEntry.iccid)
                .flatMap { response ->

                    if (response.matchingId.isNullOrEmpty()) {
                        return AdapterError("simEntryId == null or empty").left()
                    }

                    // TODO: Check if we even care about eid at this point.
                    //  if (simEntry.eid != null && simEntry.eid != response.eid) {
                    //      return AdapterError("simEntry.eid = '${simEntry.eid}', response.eid = '${response.eid}'").left()
                    // }

                    dao.setSmDpPlusStateAndMatchingId(simEntry.id, SmDpPlusState.RELEASED, response.matchingId!!)
                    dao.getSimProfileById(simEntry.id) // TODO DO we really want to do this?
                }
    }

    /**
     * Downloads the SM-DP+ 'profile status' information for an ICCID from
     * a SM-DP+ service.
     * @param httpClient  HTTP client
     * @param config  SIM vendor specific configuration
     * @param iccid  ICCID
     * @return SM-DP+ 'profile status' for ICCID
     */
    fun getProfileStatus(httpClient: CloseableHttpClient,
                         config: ProfileVendorConfig,
                         iccid: String): Either<SimManagerError, ProfileStatus> =
            getProfileStatus(httpClient, config, listOf(iccid))
                    .flatMap {
                        it.first().right()
                    }

    /**
     * Downloads the SM-DP+ 'profile status' information for a list of ICCIDs
     * from a SM-DP+ service.
     * @param httpClient  HTTP client
     * @param config  SIM vendor specific configuration
     * @param iccidList  list with ICCID
     * @return  A list with SM-DP+ 'profile status' information
     */
    private fun getProfileStatus(httpClient: CloseableHttpClient,
                                 config: ProfileVendorConfig,
                                 iccidList: List<String>): Either<SimManagerError, List<ProfileStatus>> {
        if (iccidList.isNullOrEmpty()) {
            logger.error("One or more ICCID values required in SM-DP+ 'profile-status' message to service {}",
                    config.name)
            return NotFoundError("").left()
        }

        val header = ES2RequestHeader(
                functionRequesterIdentifier = config.requesterIdentifier)

        val request =
                buildEs2plusRequest<Es2ProfileStatusCommand>(config.getEndpoint(), "getProfileStatus",
                        Es2ProfileStatusCommand(
                                header = header,
                                iccidList = iccidList.map { IccidListEntry(iccid = it) }
                        ))

        /// Pretty print version of ICCID list to
        val iccids = iccidList.joinToString(prefix = "[", postfix = "]")
        val functionCallIdentifier = header.functionCallIdentifier

        return executeRequest<Es2ProfileStatusResponse>(
                "getProfileStatus",
                httpClient,
                request,
                config.name,
                header.functionCallIdentifier,
                Es2ProfileStatusResponse::class.java,
                iccids,
                treatAsPing = true)
                .flatMap { response ->

                    val profileStatusList = response.profileStatusList

                    if (!profileStatusList.isNullOrEmpty())
                        profileStatusList.right()
                    else
                        NotFoundError("No information found for ICCID $iccids in SM-DP+ 'profile-status' message to service ${config.name}",
                                pingOk = true)
                                .left()
                }
    }

    /**
     * Requests the an external Profile Vendor to activate the
     * SIM profile.
     * @param httpClient  HTTP client
     * @param config SIM vendor specific configuration
     * @param dao  DB interface
     * @param eid  ESIM id
     * @param simEntry  SIM profile to activate
     * @return Updated SIM profile
     */
    fun activate(httpClient: CloseableHttpClient,
                 config: ProfileVendorConfig,
                 dao: SimInventoryDAO,
                 eid: String? = null,
                 simEntry: SimEntry): Either<SimManagerError, SimEntry> =
            downloadOrder(httpClient, config, dao, simEntry)
                    .flatMap {
                        confirmOrder(httpClient, config, dao, eid, it)
                    }

    /**
     * A dummy ICCID. May or may notreturn a valid profile from any HSS or SM-DP+, but is
     * useful for checking of there is an SM-DP+ in the other end of the connection.
     */
    val invalidICCID = listOf("8901000000000000001")

    /**
     * Contact the ES2+  endpoint of the SM-DP+, and return true if the answer indicates
     * that it's up.
     */
    fun ping(httpClient: CloseableHttpClient,
             config: ProfileVendorConfig): Either<SimManagerError, List<ProfileStatus>> =
            getProfileStatus(
                    httpClient = httpClient,
                    config = config,
                    iccidList = invalidICCID)
}