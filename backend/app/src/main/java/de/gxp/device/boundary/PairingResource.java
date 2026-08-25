package de.gxp.device;

import org.jboss.resteasy.reactive.ResponseStatus;

import de.gxp.api.PairingApi;
import de.gxp.api.model.PairingCompleted;
import de.gxp.api.model.PairingRequestCreated;

/**
 * Boundary — kiosk-facing pairing adapter over the generated
 * {@link PairingApi} (tag {@code pairing}, unauthenticated by design).
 * Second Boundary of this package beside {@link DeviceAdminResource}: one
 * consistency unit spanning two auth surfaces (approved 2026-08-19,
 * {@code backend/CLAUDE.md} amendment). Every method body is ONE delegating
 * line (spec §4, binding).
 */
public class PairingResource implements PairingApi {

    private final DeviceControl deviceControl;

    PairingResource(DeviceControl deviceControl) {
        this.deviceControl = deviceControl;
    }

    @Override
    @ResponseStatus(201)
    public PairingRequestCreated createPairingRequest() {
        return deviceControl.createPairingRequest();
    }

    @Override
    public String getPairingCa() {
        return deviceControl.caPem();
    }

    @Override
    public PairingCompleted pollPairingRequest(String code, String gxPPairingSecret) {
        return deviceControl.poll(code, gxPPairingSecret);
    }
}
