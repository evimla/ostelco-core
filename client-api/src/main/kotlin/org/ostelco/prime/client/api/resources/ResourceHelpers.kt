package org.ostelco.prime.client.api.resources

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.ostelco.prime.getLogger

/**
 * Common 'helper' functions for resources.
 *
 */
val objectMapper = ObjectMapper()

fun <R : Any> R.asJson(`object`: Any): String {
    try {
        return objectMapper.writeValueAsString(`object`)
    } catch (e: JsonProcessingException) {
        val logger by getLogger()
        logger.error("Error in json response {}", e)
    }
    return ""
}