import io.dropwizard.testing.junit.ResourceTestRule
import junit.framework.TestCase.assertEquals
import org.junit.AfterClass
import org.junit.ClassRule
import org.junit.Test
import org.ostelco.*
import org.ostelco.jsonValidation.JsonSchemaInputOutputValidationInterceptor
import javax.ws.rs.client.Entity
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response


class ES2PlusResourceTest {

    companion object {
        @JvmField
        @ClassRule
        val RULE: ResourceTestRule = ResourceTestRule
                .builder()
                .addResource(EsimInventoryResource())
                .addProvider(JsonSchemaInputOutputValidationInterceptor("resources"))
                .build()

        @JvmStatic
        @AfterClass
        fun afterClass() {
        }
    }

    @Test
    fun testFindByIccid() {
        val response = RULE.target("/ostelco/sim-inventory/Loltel/iccid/0123123123123")
                .request(MediaType.APPLICATION_JSON)
                .get()

        assertEquals(200, response.status)

        val simEntry = response.readEntity(SimEntry::class.java)
    }

    @Test
    fun testFindByImsi() {
        val response = RULE.target("/ostelco/sim-inventory/Loltel/imsi/44881122123123123")
                .request(MediaType.APPLICATION_JSON)
                .get()

        assertEquals(200, response.status)

        val simEntry = response.readEntity(SimEntry::class.java)
    }


    @Test
    fun testAllocateNextFree() {
        val response = RULE.target("/ostelco/sim-inventory/Loltel/msisdn/123123123/allocate-next-free")
                .request(MediaType.APPLICATION_JSON)
                .get() // XXX Post (or put?)x'

        assertEquals(200, response.status)

        val simEntry = response.readEntity(SimEntry::class.java)
    }


    @Test
    fun testActivate() {
        val response = RULE.target("/ostelco/sim-inventory/iccid/982389123498/activate")
                .request(MediaType.APPLICATION_JSON)
                .get()// XXX Post

        assertEquals(200, response.status)

        val simEntry = response.readEntity(SimEntry::class.java)
    }

    @Test
    fun testDeactivate() {
        val response = RULE.target("/ostelco/sim-inventory/iccid/8328238238328/deactivate")
                .request(MediaType.APPLICATION_JSON)
                .get() // XXX Post

        assertEquals(200, response.status)

        val simEntry = response.readEntity(SimEntry::class.java)
    }


    //  @Path("/import-batch/sim-profile-vendor/{profilevendor}")
    // Test importing a CSV file from the resources.

    @Test
    fun testImport() {
        val response = RULE.target("import-batch/sim-profile-vendor/Idemia")
                .request(MediaType.TEXT_PLAIN)
                .put()

        assertEquals(200, response.status)

        val simEntry = response.readEntity(SimEntry::class.java)
    }
}
