package de.gxp.device.boundary;

import java.util.List;

import de.gxp.api.ManagerDevicesApi;
import de.gxp.api.model.ConfirmPairingRequest;
import de.gxp.api.model.DeviceDto;
import de.gxp.api.model.DeviceUpdateRequest;
import de.gxp.device.control.DeviceControl;
import jakarta.annotation.security.RolesAllowed;

/**
 * Boundary — admin-facing device adapter over the generated
 * {@link ManagerDevicesApi} (tag {@code manager-devices}). Every method body
 * is ONE delegating line (spec §4, binding); both Boundaries of this package
 * adapt the single public {@link DeviceControl}.
 */
@RolesAllowed("admin")
public class DeviceAdminResource implements ManagerDevicesApi {

    private final DeviceControl deviceControl;

    DeviceAdminResource(DeviceControl deviceControl) {
        this.deviceControl = deviceControl;
    }

    @Override
    public void confirmPairing(String code, ConfirmPairingRequest confirmPairingRequest) {
        deviceControl.confirmPairing(code, confirmPairingRequest);
    }

    @Override
    public List<DeviceDto> listDevices() {
        return deviceControl.listDevices();
    }

    @Override
    public DeviceDto renameDevice(Long deviceId, DeviceUpdateRequest deviceUpdateRequest) {
        return deviceControl.renameDevice(deviceId, deviceUpdateRequest);
    }

    @Override
    public void revokeDevice(Long deviceId) {
        deviceControl.revokeDevice(deviceId);
    }
}
