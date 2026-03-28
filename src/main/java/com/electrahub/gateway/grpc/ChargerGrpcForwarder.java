package com.electrahub.gateway.grpc;

import com.electrahub.proto.charger.v1.ChargerServiceGrpc;
import com.electrahub.proto.charger.v1.ChargerServiceOuterClass;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * gRPC forwarder for Charger Service.
 * Handles charger endpoints: search chargers, get charger details, charger availability, etc.
 */
@RestController
@RequestMapping("/charger")
@ConditionalOnProperty(name = "app.grpc.forwarders.charger.enabled", havingValue = "true")
public class ChargerGrpcForwarder extends AbstractGrpcForwarder {

    private static final Logger log = LoggerFactory.getLogger(ChargerGrpcForwarder.class);

    @GrpcClient("charger-service")
    private ChargerServiceGrpc.ChargerServiceBlockingStub chargerServiceStub;

    /**
     * Initialize ChargerGrpcForwarder.
     *
     * @param statusMapper for mapping gRPC status to HTTP
     * @param metadataHelper for creating gRPC metadata
     */
    public ChargerGrpcForwarder(GrpcStatusToHttpMapper statusMapper, GrpcMetadataHelper metadataHelper) {
        super(statusMapper, metadataHelper);
    }

    /**
     * GET /charger/api/v1/chargers - Search chargers with filters and pagination.
     *
     * @param request the HTTP request
     * @param latitude latitude for search
     * @param longitude longitude for search
     * @param radius search radius in kilometers
     * @param page page number (default 0)
     * @param size page size (default 10)
     * @return list of chargers
     */
    @GetMapping("/api/v1/chargers")
    public ResponseEntity<String> searchChargers(HttpServletRequest request,
                                                @RequestParam(value = "latitude") double latitude,
                                                @RequestParam(value = "longitude") double longitude,
                                                @RequestParam(value = "radius", defaultValue = "10") double radius,
                                                @RequestParam(value = "page", defaultValue = "0") int page,
                                                @RequestParam(value = "size", defaultValue = "10") int size) {
        try {
            ChargerServiceOuterClass.SearchChargersRequest grpcRequest =
                    ChargerServiceOuterClass.SearchChargersRequest.newBuilder()
                    .setLatitude(latitude)
                    .setLongitude(longitude)
                    .setRadius(radius)
                    .setPage(page)
                    .setSize(size)
                    .build();

            ChargerServiceOuterClass.SearchChargersResponse grpcResponse = chargerServiceStub.searchChargers(grpcRequest);
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "searchChargers");
        } catch (Exception ex) {
            log.error("Error in searchChargers: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * GET /charger/api/v1/chargers/{chargerId} - Get charger details.
     *
     * @param request the HTTP request
     * @param chargerId the charger ID
     * @return charger details
     */
    @GetMapping("/api/v1/chargers/{chargerId}")
    public ResponseEntity<String> getCharger(HttpServletRequest request,
                                            @PathVariable String chargerId) {
        try {
            ChargerServiceOuterClass.GetChargerRequest grpcRequest =
                    ChargerServiceOuterClass.GetChargerRequest.newBuilder()
                    .setChargerId(chargerId)
                    .build();

            ChargerServiceOuterClass.ChargerResponse grpcResponse = chargerServiceStub.getCharger(grpcRequest);
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "getCharger");
        } catch (Exception ex) {
            log.error("Error in getCharger: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * GET /charger/api/v1/chargers/{chargerId}/availability - Get charger availability.
     *
     * @param request the HTTP request
     * @param chargerId the charger ID
     * @return charger availability
     */
    @GetMapping("/api/v1/chargers/{chargerId}/availability")
    public ResponseEntity<String> getAvailability(HttpServletRequest request,
                                                 @PathVariable String chargerId) {
        try {
            ChargerServiceOuterClass.GetAvailabilityRequest grpcRequest =
                    ChargerServiceOuterClass.GetAvailabilityRequest.newBuilder()
                    .setChargerId(chargerId)
                    .build();

            ChargerServiceOuterClass.AvailabilityResponse grpcResponse = chargerServiceStub.getAvailability(grpcRequest);
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "getAvailability");
        } catch (Exception ex) {
            log.error("Error in getAvailability: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * POST /charger/api/v1/chargers/{chargerId}/reserve - Reserve a charger.
     *
     * @param request the HTTP request
     * @param chargerId the charger ID
     * @param body the reservation request body
     * @return reservation response
     */
    @PostMapping("/api/v1/chargers/{chargerId}/reserve")
    public ResponseEntity<String> reserveCharger(HttpServletRequest request,
                                                @PathVariable String chargerId,
                                                @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            ChargerServiceOuterClass.ReserveChargerRequest.Builder requestBuilder =
                    ChargerServiceOuterClass.ReserveChargerRequest.newBuilder()
                    .setChargerId(chargerId);
            parseJsonToProto(json, requestBuilder);

            ChargerServiceOuterClass.ReservationResponse grpcResponse = chargerServiceStub.reserveCharger(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "reserveCharger");
        } catch (Exception ex) {
            log.error("Error in reserveCharger: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * POST /charger/api/v1/reservations/{reservationId}/start - Start charging.
     *
     * @param request the HTTP request
     * @param reservationId the reservation ID
     * @param body the start charging request body
     * @return charging response
     */
    @PostMapping("/api/v1/reservations/{reservationId}/start")
    public ResponseEntity<String> startCharging(HttpServletRequest request,
                                               @PathVariable String reservationId,
                                               @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            ChargerServiceOuterClass.StartChargingRequest.Builder requestBuilder =
                    ChargerServiceOuterClass.StartChargingRequest.newBuilder()
                    .setReservationId(reservationId);
            parseJsonToProto(json, requestBuilder);

            ChargerServiceOuterClass.ChargingResponse grpcResponse = chargerServiceStub.startCharging(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "startCharging");
        } catch (Exception ex) {
            log.error("Error in startCharging: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * POST /charger/api/v1/reservations/{reservationId}/stop - Stop charging.
     *
     * @param request the HTTP request
     * @param reservationId the reservation ID
     * @param body the stop charging request body
     * @return charging response
     */
    @PostMapping("/api/v1/reservations/{reservationId}/stop")
    public ResponseEntity<String> stopCharging(HttpServletRequest request,
                                              @PathVariable String reservationId,
                                              @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            ChargerServiceOuterClass.StopChargingRequest.Builder requestBuilder =
                    ChargerServiceOuterClass.StopChargingRequest.newBuilder()
                    .setReservationId(reservationId);
            parseJsonToProto(json, requestBuilder);

            ChargerServiceOuterClass.ChargingResponse grpcResponse = chargerServiceStub.stopCharging(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "stopCharging");
        } catch (Exception ex) {
            log.error("Error in stopCharging: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }
}
