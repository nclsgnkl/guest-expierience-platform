package de.gxp.device.boundary;

import org.jboss.resteasy.reactive.ResponseStatus;

import de.gxp.api.PairingApi;
import de.gxp.api.model.PairingCompleted;
import de.gxp.api.model.PairingRequestCreated;
import de.gxp.device.control.DeviceControl;
import jakarta.annotation.security.PermitAll;

/**
 * Boundary — kiosk-facing pairing adapter over the generated
 * {@link PairingApi} (tag {@code pairing}, unauthenticated by design).
 * Second Boundary of this package beside {@link DeviceAdminResource}: one
 * consistency unit spanning two auth surfaces (approved 2026-08-19,
 * {@code backend/CLAUDE.md} amendment). Every method body is ONE delegating
 * line (spec §4, binding).
 *
 * <p><b>{@code @PermitAll} sits on every method, never on the class</b>
 * (2026-08-24, after a review mutation): a class-level {@code @PermitAll} is
 * inherited by any method added later, so an unannotated {@code @GET} on this
 * class answers anonymously — and none of the three guards sees it
 * ({@code BoundaryAnnotationAuditTest} resolves it to 1 through the class,
 * the contract test cannot derive an operation that is not in the contract,
 * and {@code deny-unannotated-endpoints} is satisfied by the inherited
 * annotation). Method-level is the only form that makes the omission visible,
 * and this is the one anonymous surface on the LAN. The
 * {@code @RolesAllowed} Boundaries keep their class-level annotation — there
 * the inherited failure mode is "too restrictive", which is loud.
 */
public class PairingResource implements PairingApi {

    private final DeviceControl deviceControl;

    PairingResource(DeviceControl deviceControl) {
        this.deviceControl = deviceControl;
    }

    @Override
    @PermitAll
    @ResponseStatus(201)
    public PairingRequestCreated createPairingRequest() {
        return deviceControl.createPairingRequest();
    }

    @Override
    @PermitAll
    public String getPairingCa() {
        return deviceControl.caPem();
    }

    @Override
    @PermitAll
    public PairingCompleted pollPairingRequest(String code, String gxPPairingSecret) {
        return deviceControl.poll(code, gxPPairingSecret);
    }
}
