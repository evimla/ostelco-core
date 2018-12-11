package org.ostelco.diameter.ha.client

import org.jdiameter.api.AvpDataException
import org.jdiameter.api.Request
import org.jdiameter.client.api.IContainer
import org.jdiameter.client.api.IMessage
import org.jdiameter.client.api.parser.IMessageParser
import org.jdiameter.client.api.parser.ParseException
import org.jdiameter.client.impl.app.cca.IClientCCASessionData
import org.jdiameter.common.api.app.cca.ClientCCASessionState
import org.ostelco.diameter.ha.common.AppSessionDataRedisReplicatedImpl
import org.ostelco.diameter.ha.common.RedisStorage
import org.ostelco.diameter.ha.logger
import java.io.Serializable
import java.io.IOException
import java.lang.IllegalStateException


class ClientCCASessionDataRedisReplicatedImpl( id: String, redisStorage: RedisStorage, container: IContainer) : AppSessionDataRedisReplicatedImpl(id, redisStorage), IClientCCASessionData {

    private val logger by logger()

    private val EVENT_BASED = "EVENT_BASED"
    private val REQUEST_TYPE = "REQUEST_TYPE"
    private val STATE = "STATE"
    private val TXTIMER_ID = "TXTIMER_ID"
    private val TXTIMER_REQUEST = "TXTIMER_REQUEST"
    private val BUFFER = "BUFFER"
    private val GRA = "GRA"
    private val GDDFH = "GDDFH"
    private val GCCFH = "GCCFH"

    private val messageParser: IMessageParser

    init {
        messageParser = container.assemblerFacility.getComponentInstance(IMessageParser::class.java);
    }


    override fun isEventBased(): Boolean {
        return  toPrimitive(this.redisStorage.getValue(id, EVENT_BASED), true)
    }

    override fun setEventBased(b: Boolean) {
        storeValue(EVENT_BASED, b.toString())
    }

    override fun isRequestTypeSet(): Boolean {
        return toPrimitive(this.redisStorage.getValue(id, REQUEST_TYPE), false)
    }

    override fun setRequestTypeSet(b: Boolean) {
        storeValue(REQUEST_TYPE, b.toString())
    }

    override fun getClientCCASessionState(): ClientCCASessionState {
        val value = this.redisStorage.getValue(id, STATE)
        if (value != null) {
            return ClientCCASessionState.valueOf(value)
        } else {
            throw IllegalStateException()
        }
        //return this.redisStorage.getValue(id, STATE) as ClientCCASessionState
    }

    override fun setClientCCASessionState(state: ClientCCASessionState?) {
        storeValue(STATE, state.toString())
    }

    override fun getTxTimerId(): Serializable {
        val value =  redisStorage.getValue(id, TXTIMER_ID)
        if (value != null) {
            return value
        } else {
            throw IllegalStateException()
        }
    }

    override fun setTxTimerId(txTimerId: Serializable?) {
        storeValue(TXTIMER_ID, txTimerId.toString())
    }

    override fun getTxTimerRequest(): Request? {
        val b64String = this.redisStorage.getValue(id, TXTIMER_REQUEST)
        if (b64String != null) {
            try {
                return this.messageParser.createMessage(ByteArrayfromBase64String(b64String))
            } catch (e : IOException) {
                logger.error("Failed to decode Tx Timer Request", e)
            } catch (e : ClassNotFoundException) {
                logger.error("Failed to decode Tx Timer Request", e)
            } catch (e: AvpDataException) {
                logger.error("Failed to decode Tx Timer Request", e)
            }
            return null
        } else {
            return null
        }
    }

    override fun setTxTimerRequest(txTimerRequest: Request?) {
        if (txTimerRequest != null) {
            try {
                val data = this.messageParser.encodeMessage(txTimerRequest as IMessage)
                storeValue(ByteBuffertoBase64String(data), TXTIMER_REQUEST)
            } catch (e: IOException) {
                logger.error("Unable to encode Tx Timer Request to buffer.", e)
            }
        } else {
            this.redisStorage.removeValue(id, TXTIMER_REQUEST)
        }
    }

    override fun getBuffer(): Request? {
        val b64String = this.redisStorage.getValue(id, BUFFER)
        if (b64String != null) {
            try {
                return this.messageParser.createMessage(ByteArrayfromBase64String(b64String))
            } catch (e : IOException) {
                logger.error("Unable to recreate message from buffer.", e)
            } catch (e : ClassNotFoundException) {
                logger.error("Unable to recreate message from buffer.", e)
            } catch (e: AvpDataException) {
                logger.error("Unable to recreate message from buffer.", e)
            }
            return null
        } else {
            return null
        }
    }

    override fun setBuffer(buffer: Request?) {
        if (buffer != null) {
            try {
                val data = this.messageParser.encodeMessage(buffer as IMessage)
                storeValue(ByteBuffertoBase64String(data), BUFFER)
            } catch (e: ParseException) {
                logger.error("Unable to encode message to buffer.", e)
            }

        } else {
            this.redisStorage.removeValue(id, BUFFER)
        }
    }

    override fun getGatheredRequestedAction(): Int {
        val value = redisStorage.getValue(id, GRA)
        if (value != null) {
            return value.toInt()
        } else {
            throw java.lang.IllegalStateException()
        }
    }

    override fun setGatheredRequestedAction(gatheredRequestedAction: Int) {
        storeValue(GRA, gatheredRequestedAction.toString())
    }

    override fun getGatheredCCFH(): Int {
        val value =  redisStorage.getValue(id, GCCFH)
        if (value != null) {
            return value.toInt()
        } else {
            throw IllegalStateException()
        }
    }

    override fun setGatheredCCFH(gatheredCCFH: Int) {
        storeValue(GCCFH, gatheredCCFH.toString())
    }

    override fun getGatheredDDFH(): Int {
        val value = redisStorage.getValue(id, GDDFH)
        if (value != null) {
            return value.toInt()
        } else {
            throw IllegalStateException()
        }
    }

    override fun setGatheredDDFH(gatheredDDFH: Int) {
        storeValue(GDDFH, gatheredDDFH.toString())
    }

}