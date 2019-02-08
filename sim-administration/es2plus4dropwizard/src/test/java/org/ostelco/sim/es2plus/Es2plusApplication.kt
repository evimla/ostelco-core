package org.ostelco.sim.es2plus

import io.dropwizard.Application
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource
import io.swagger.v3.oas.integration.SwaggerConfiguration
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import org.ostelco.sim.es2plus.ES2PlusIncomingHeadersFilter.Companion.addEs2PlusDefaultFiltersAndInterceptors
import java.util.stream.Collectors
import java.util.stream.Stream

class Es2plusApplication : Application<Es2plusConfiguration>() {

    override fun getName(): String {
        return "es2+ application"
    }

    override fun initialize(bootstrap: Bootstrap<Es2plusConfiguration>) {
        // TODO: application initialization
    }

    override fun run(configuration: Es2plusConfiguration,
                     environment: Environment) {

        // XXX Add these parameters to configuration file.
        val oas = OpenAPI()
        val info = Info()
                .title(name)
                .description("Restful membership management.")
                .termsOfService("http://example.com/terms")
                .contact(Contact().email("la3lma@gmail.com"))

        oas.info(info)
        val oasConfig = SwaggerConfiguration()
                .openAPI(oas)
                .prettyPrint(true)
                .resourcePackages(Stream.of("no .rmz.membershipmgt")
                        .collect(Collectors.toSet<String>()))
        val env = environment.jersey()
        env.register(OpenApiResource()
                .openApiConfiguration(oasConfig))


        addEs2PlusDefaultFiltersAndInterceptors(env)
    }

    companion object {
        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            Es2plusApplication().run(*args)
        }
    }

    // We're basing this implementation on
    // https://www.gsma.com/newsroom/wp-content/uploads/SGP.22-v2.0.pdf
}


class PlaceholderSmDpPlusService : SmDpPlusService {
    @Throws(SmDpPlusException::class)
    override fun downloadOrder(eid: String?, iccid: String?, profileType: String?): String {
        return "01234567890123456789"
    }

    override fun confirmOrder(eid: String?, iccid: String?, smdsAddress: String?, machingId: String?, confirmationCode: String?, releaseFlag: Boolean): Es2ConfirmOrderResponse {
        return Es2ConfirmOrderResponse(eS2SuccessResponseHeader(), eid="1234567890123456789012", matchingId = "foo", smdsAddress = "localhost")
    }

    @Throws(SmDpPlusException::class)
    override fun cancelOrder(iccid: String?, matchingId: String?, eid: String?, finalProfileStatusIndicator: String?) {
    }

    @Throws(SmDpPlusException::class)
    override fun releaseProfile(iccid: String) {
    }
}


class PlaceholderSmDpPlusCallbackService : SmDpPlusCallbackService {
    override fun handleDownloadProgressInfo(eid: String?, iccid: String, notificationPointId: Int, profileType: String?, resultData: String?, timestamp: String) {

    }
}

