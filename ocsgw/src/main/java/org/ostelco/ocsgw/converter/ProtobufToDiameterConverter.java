package org.ostelco.ocsgw.converter;

import org.ostelco.diameter.CreditControlContext;
import org.ostelco.diameter.model.*;
import org.ostelco.ocs.api.CreditControlRequestInfo;
import org.ostelco.ocs.api.CreditControlRequestType;
import org.ostelco.ocs.api.ServiceInfo;
import org.ostelco.ocsgw.datasource.protobuf.GrpcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collections;

import static org.ostelco.diameter.model.RequestType.*;

public class ProtobufToDiameterConverter {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcDataSource.class);

    /**
     * Convert MultipleServiceCreditControl in gRPC format to diameter format
     */
    public static MultipleServiceCreditControl convertMSCC(org.ostelco.ocs.api.MultipleServiceCreditControl msccGRPC) {
        return new MultipleServiceCreditControl(
                msccGRPC.getRatingGroup(),
                (int) msccGRPC.getServiceIdentifier(),
                Collections.singletonList(new ServiceUnit()), new ServiceUnit(), new ServiceUnit(msccGRPC.getGranted().getTotalOctets(), 0, 0),
                msccGRPC.getValidityTime(),
                msccGRPC.getQuotaHoldingTime(),
                msccGRPC.getVolumeQuotaThreshold(),
                convertFinalUnitIndication(msccGRPC.getFinalUnitIndication()),
                convertResultCode(msccGRPC.getResultCode()));
    }

    /**
     * Convert Diameter request type to gRPC
     */
    public static CreditControlRequestType getRequestType(CreditControlContext context) {
        switch (context.getOriginalCreditControlRequest().getRequestTypeAVPValue()) {
            case INITIAL_REQUEST:
                return CreditControlRequestType.INITIAL_REQUEST;
            case UPDATE_REQUEST:
                return CreditControlRequestType.UPDATE_REQUEST;
            case TERMINATION_REQUEST:
                return CreditControlRequestType.TERMINATION_REQUEST;
            case EVENT_REQUEST:
                return CreditControlRequestType.EVENT_REQUEST;
            default:
                LOG.warn("Unknown request type");
                return CreditControlRequestType.NONE;
        }
    }

    private static FinalUnitIndication convertFinalUnitIndication(org.ostelco.ocs.api.FinalUnitIndication fuiGrpc) {
        if (!fuiGrpc.getIsSet()) {
            return null;
        }
        return new FinalUnitIndication(
                FinalUnitAction.values()[fuiGrpc.getFinalUnitAction().getNumber()],
                fuiGrpc.getRestrictionFilterRuleList(),
                fuiGrpc.getFilterIdList(),
                new RedirectServer(
                        RedirectAddressType.values()[fuiGrpc.getRedirectServer().getRedirectAddressType().getNumber()],
                        fuiGrpc.getRedirectServer().getRedirectServerAddress()
                )
        );
    }

    // We match the error codes on names in gRPC and internal model
    public static ResultCode convertResultCode(org.ostelco.ocs.api.ResultCode resultCode) {
        return ResultCode.valueOf(resultCode.name());
    }

    public static CreditControlRequestInfo convertRequestToProtobuf(final CreditControlContext context, @Nullable final String topicId) {

        try {
            CreditControlRequestInfo.Builder builder = CreditControlRequestInfo
                    .newBuilder()
                    .setType(getRequestType(context));

            if (topicId != null) {
                builder.setTopicId(topicId);
            }

            for (MultipleServiceCreditControl mscc : context.getCreditControlRequest().getMultipleServiceCreditControls()) {

                org.ostelco.ocs.api.MultipleServiceCreditControl.Builder protoMscc = org.ostelco.ocs.api.MultipleServiceCreditControl.newBuilder();

                if (!mscc.getRequested().isEmpty()) {

                    ServiceUnit requested = mscc.getRequested().get(0);

                    protoMscc.setRequested(org.ostelco.ocs.api.ServiceUnit.newBuilder()
                            .setTotalOctets(requested.getTotal()) // fails at 55904
                            .setInputOctets(0L)
                            .setOutputOctets(0L));
                }

                ServiceUnit used = mscc.getUsed();

                protoMscc.setUsed(org.ostelco.ocs.api.ServiceUnit.newBuilder()
                        .setInputOctets(used.getInput())
                        .setOutputOctets(used.getOutput())
                        .setTotalOctets(used.getTotal()));

                protoMscc.setRatingGroup(mscc.getRatingGroup());
                protoMscc.setServiceIdentifier(mscc.getServiceIdentifier());

                if (mscc.getReportingReason() != null) {
                    protoMscc.setReportingReasonValue(mscc.getReportingReason().ordinal());
                } else {
                    protoMscc.setReportingReasonValue(org.ostelco.ocs.api.ReportingReason.UNRECOGNIZED.ordinal());
                }
                builder.addMscc(protoMscc);
            }

            builder.setRequestId(context.getSessionId())
                    .setMsisdn(context.getCreditControlRequest().getMsisdn())
                    .setImsi(context.getCreditControlRequest().getImsi());

            if (!context.getCreditControlRequest().getServiceInformation().isEmpty()) {
                final PsInformation psInformation
                        = context.getCreditControlRequest().getServiceInformation().get(0).getPsInformation().get(0);

                if (psInformation != null
                        && psInformation.getCalledStationId() != null
                        && psInformation.getSgsnMccMnc() != null) {

                    builder.setServiceInformation(
                            ServiceInfo.newBuilder()
                                    .setPsInformation(org.ostelco.ocs.api.PsInformation.newBuilder()
                                            .setCalledStationId(psInformation.getCalledStationId())
                                            .setSgsnMccMnc(psInformation.getSgsnMccMnc())));
                }
            }
            return builder.build();

        } catch (Exception e) {
            LOG.error("Failed to create CreditControlRequestInfo [{}] [{}]", context.getCreditControlRequest().getMsisdn(), context.getSessionId(), e);
        }
        return null;
    }
}
