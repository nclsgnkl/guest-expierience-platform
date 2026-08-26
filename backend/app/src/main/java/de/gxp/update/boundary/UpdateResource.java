package de.gxp.update.boundary;

import org.jboss.resteasy.reactive.ResponseStatus;

import de.gxp.api.ManagerUpdatesApi;
import de.gxp.api.model.UpdateSettingsRequest;
import de.gxp.api.model.UpdateStatusDto;
import de.gxp.update.control.UpdateControl;
import jakarta.annotation.security.RolesAllowed;

/**
 * Boundary — the manager's update-channel surface (update-channel REQ-710…713,
 * tag {@code manager-updates}). Class-level {@code @RolesAllowed("admin")}:
 * the whole surface is admin, nothing here is ever anonymous. Four one-line
 * delegations; every rule lives in {@link UpdateControl}.
 */
@RolesAllowed("admin")
public class UpdateResource implements ManagerUpdatesApi {

    private final UpdateControl updateControl;

    UpdateResource(UpdateControl updateControl) {
        this.updateControl = updateControl;
    }

    @Override
    public UpdateStatusDto getUpdateStatus() {
        return updateControl.status();
    }

    @Override
    public UpdateStatusDto updateUpdateSettings(UpdateSettingsRequest request) {
        return updateControl.updateSettings(request);
    }

    @Override
    @ResponseStatus(202) // contract: accepted — the updater performs it later (an HTTP-adapter concern)
    public UpdateStatusDto applyUpdateNow() {
        return updateControl.applyNow();
    }

    @Override
    public UpdateStatusDto checkForUpdateNow() {
        return updateControl.checkNow();
    }
}
