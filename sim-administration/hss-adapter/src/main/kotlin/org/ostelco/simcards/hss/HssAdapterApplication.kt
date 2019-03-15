package org.ostelco.simcards.hss

import com.codahale.metrics.health.HealthCheck
import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.Application
import io.dropwizard.Configuration
import io.dropwizard.client.HttpClientBuilder
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import org.ostelco.dropwizardutils.OpenapiResourceAdder
import org.ostelco.simcards.admin.ConfigRegistry
import org.ostelco.simcards.admin.HssConfig
import org.ostelco.simcards.admin.ResourceRegistry
import javax.validation.Valid
import javax.validation.constraints.NotNull

fun main(args: Array<String>) = HssAdapterApplication().run(*args)

/**
 * The sim  manager will have to interface to many different Home Subscriber Module
 * instances.  Many of these will rely on proprietary libraries to interface to the
 * HSS.  We strongly believe that the majority of the Ostelco project's source code
 * should be open sourced, but it is impossible to open source something that isn't ours,
 * so we can't open source HSS libraries, and we won't.
 *
 * Instead we'll do the next best thing: We'll make it simple to create adapters
 * for these libraries and make them available to the ostelco core.
 *
 * Our strategy is to make a service, implemented by the HssAdapterApplication, that
 * will be available as an external executable, via rest  (or possibly gRPC,  not decided
 * at the time this documentation is  being written).  The "simmanager" module of the open
 * source Prime component will then connect to the hss adapter and make requests for
 * activation/suspension/deletion.
 *
 * This component is written in the open source project, and it contains a non-proprietary
 * implementation of a simple HSS interface.   We provide this as a template so that when
 * proprietary code is added to this application, it can be done in the same way as the
 * simple non-proprietary implementation was added.  You are however expected to do that,
 * and make your service, deploy it separately and tell the prime component where it is
 * (typically using kubernetes service lookup or something similar).
 */
class HssAdapterApplication : Application<HssAdapterApplicationConfiguration>() {

    override fun getName(): String {
        return "hss-adapter"
    }

    override fun initialize(bootstrap: Bootstrap<HssAdapterApplicationConfiguration>?) {
        // nothing to do yet
    }

    override fun run(configuration: HssAdapterApplicationConfiguration,
                     env: Environment) {

        val httpClient = HttpClientBuilder(env)
                .using(ConfigRegistry.config.httpClient)
                .build("SIM inventory")
        val jerseyEnv = env.jersey()

        OpenapiResourceAdder.addOpenapiResourceToJerseyEnv(jerseyEnv, ConfigRegistry.config.openApi)

        jerseyEnv.register(ResourceRegistry.simInventoryResource)


        val adapters = mutableSetOf<HssAdapter>()

        for (config in configuration.hssVendors) {
            // Only a simple adapter added here, ut this is the extension point where we will
            // add other, proprietary adapters eventually.
            adapters.add(SimpleHssAdapter(name = config.name, httpClient = httpClient, config = config))
        }

        val dispatcher = DirectHssDispatcher(adapters = adapters,
                healthCheckRegistrar = object : HealthCheckRegistrar {
                    override fun registerHealthCheck(name: String, healthCheck: HealthCheck) {
                        env.healthChecks().register(name, healthCheck)
                    }
                })

        // This dispatcdher is what we will use to handle the incoming
        // requests.  it will essentially do all the work.
    }
}


class HssAdapterApplicationConfiguration : Configuration() {
    @Valid
    @NotNull
    @JsonProperty("hlrs")
    lateinit var hssVendors: List<HssConfig>
}