package org.ostelco.prime.customer.endpoint.resources

import arrow.core.Either
import arrow.core.right
import com.nhaarman.mockito_kotlin.argumentCaptor
import io.dropwizard.auth.AuthDynamicFeature
import io.dropwizard.auth.AuthValueFactoryProvider
import io.dropwizard.auth.oauth.OAuthCredentialAuthFilter.Builder
import io.dropwizard.testing.junit.ResourceTestRule
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.ostelco.prime.apierror.ApiError
import org.ostelco.prime.auth.AccessTokenPrincipal
import org.ostelco.prime.auth.OAuthAuthenticator
import org.ostelco.prime.customer.endpoint.store.SubscriberDAO
import org.ostelco.prime.customer.endpoint.util.AccessToken
import org.ostelco.prime.jsonmapper.objectMapper
import org.ostelco.prime.model.Identity
import org.ostelco.prime.model.ScanInformation
import org.ostelco.prime.model.ScanStatus
import java.util.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

class KycResourcesTest {

    private val email = "mw@internet.org"

    @Test
    fun `test Jumio for Norway`() {

        val scanInfo = ScanInformation(
                scanId = "scan123",
                countryCode = "no",
                status = ScanStatus.PENDING,
                scanResult = null)

        val identityCaptor = argumentCaptor<Identity>()
        val scanIdCaptor = argumentCaptor<String>()

        `when`<Either<ApiError, ScanInformation>>(DAO.getScanInformation(identityCaptor.capture(), scanIdCaptor.capture()))
                .thenReturn(scanInfo.right())

        val resp = RULE.target("regions/no/kyc/jumio/scans/scan123")
                .request()
                .header("Authorization", "Bearer ${AccessToken.withEmail(email)}")
                .get(Response::class.java)

        Assertions.assertThat(resp.status).isEqualTo(Response.Status.OK.statusCode)
        Assertions.assertThat(resp.mediaType.toString()).isEqualTo(MediaType.APPLICATION_JSON)

        Assertions.assertThat(identityCaptor.firstValue).isEqualTo(Identity(email, "EMAIL", "email"))
        Assertions.assertThat(scanIdCaptor.firstValue).isEqualTo("scan123")

        Assertions.assertThat(resp.readEntity(ScanInformation::class.java))
                .isEqualTo(scanInfo)
    }

    @Test
    fun `test Jumio for Singapore`() {

        val scanInfo = ScanInformation(
                scanId = "scan123",
                countryCode = "sg",
                status = ScanStatus.PENDING,
                scanResult = null)

        val identityCaptor = argumentCaptor<Identity>()
        val scanIdCaptor = argumentCaptor<String>()

        `when`<Either<ApiError, ScanInformation>>(DAO.getScanInformation(identityCaptor.capture(), scanIdCaptor.capture()))
                .thenReturn(scanInfo.right())

        val resp = RULE.target("regions/sg/kyc/jumio/scans/scan123")
                .request()
                .header("Authorization", "Bearer ${AccessToken.withEmail(email)}")
                .get(Response::class.java)

        Assertions.assertThat(resp.status).isEqualTo(Response.Status.OK.statusCode)
        Assertions.assertThat(resp.mediaType.toString()).isEqualTo(MediaType.APPLICATION_JSON)

        Assertions.assertThat(identityCaptor.firstValue).isEqualTo(Identity(email, "EMAIL", "email"))
        Assertions.assertThat(scanIdCaptor.firstValue).isEqualTo("scan123")

        Assertions.assertThat(resp.readEntity(ScanInformation::class.java))
                .isEqualTo(scanInfo)
    }

    @Test
    fun `test myInfo for Singapore`() {

        val identityCaptor = argumentCaptor<Identity>()
        val authorisationCodeCaptor = argumentCaptor<String>()

        `when`<Either<ApiError, String>>(DAO.getCustomerMyInfoData(identityCaptor.capture(), authorisationCodeCaptor.capture()))
                .thenReturn("{}".right())

        val resp = RULE.target("regions/sg/kyc/myInfo/code123")
                .request()
                .header("Authorization", "Bearer ${AccessToken.withEmail(email)}")
                .get(Response::class.java)

        Assertions.assertThat(resp.status).isEqualTo(Response.Status.OK.statusCode)
        Assertions.assertThat(resp.mediaType.toString()).isEqualTo(MediaType.APPLICATION_JSON)

        Assertions.assertThat(identityCaptor.firstValue).isEqualTo(Identity(email, "EMAIL", "email"))
        Assertions.assertThat(authorisationCodeCaptor.firstValue).isEqualTo("code123")

        Assertions.assertThat(resp.readEntity(String::class.java))
                .isEqualTo("{}")
    }

    @Test
    fun `test myInfo for Norway`() {

        val identityCaptor = argumentCaptor<Identity>()
        val authorisationCodeCaptor = argumentCaptor<String>()

        `when`<Either<ApiError, String>>(DAO.getCustomerMyInfoData(identityCaptor.capture(), authorisationCodeCaptor.capture()))
                .thenReturn("{}".right())

        val resp = RULE.target("regions/no/kyc/myInfo/code123")
                .request()
                .header("Authorization", "Bearer ${AccessToken.withEmail(email)}")
                .get(Response::class.java)

        Assertions.assertThat(resp.status).isEqualTo(Response.Status.NOT_FOUND.statusCode)
        Assertions.assertThat(resp.mediaType.toString()).isEqualTo(MediaType.APPLICATION_JSON)
    }

    @Before
    fun setUp() {
        Mockito.`when`(AUTHENTICATOR.authenticate(ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(AccessTokenPrincipal(Identity(email, "EMAIL","email"))))
    }

    companion object {

        val DAO: SubscriberDAO = Mockito.mock(SubscriberDAO::class.java)
        val AUTHENTICATOR: OAuthAuthenticator = Mockito.mock(OAuthAuthenticator::class.java)

        @JvmField
        @ClassRule
        val RULE: ResourceTestRule = ResourceTestRule.builder()
                .setMapper(objectMapper)
                .addResource(AuthDynamicFeature(
                        Builder<AccessTokenPrincipal>()
                                .setAuthenticator(AUTHENTICATOR)
                                .setPrefix("Bearer")
                                .buildAuthFilter()))
                .addResource(AuthValueFactoryProvider.Binder(AccessTokenPrincipal::class.java))
                .addResource(RegionsResource(DAO))
                .build()
    }
}