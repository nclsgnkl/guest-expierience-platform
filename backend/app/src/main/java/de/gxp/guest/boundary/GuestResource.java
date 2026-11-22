package de.gxp.guest.boundary;

import java.io.File;

import de.gxp.api.GuestApi;
import de.gxp.api.model.GuestContent;
import de.gxp.api.model.GuestPreferencesUpdate;
import de.gxp.guest.control.GuestControl;
import jakarta.annotation.security.RolesAllowed;

/**
 * Boundary — JAX-RS adapter over the generated {@link GuestApi} (tag
 * {@code guest}, device bearer token). Every method body is ONE delegating
 * line (spec §4, binding). No method carries a room/stay/device parameter —
 * checkable on the contract itself (REQ-411).
 */
@RolesAllowed("device")
public class GuestResource implements GuestApi {

    private final GuestControl guestControl;

    GuestResource(GuestControl guestControl) {
        this.guestControl = guestControl;
    }

    @Override
    public File getGuestAsset(Long assetId, String ifNoneMatch) {
        return guestControl.asset(assetId, ifNoneMatch);
    }

    @Override
    public GuestContent getGuestContent(String ifNoneMatch) {
        return guestControl.content(ifNoneMatch);
    }

    @Override
    public void putGuestPreferences(GuestPreferencesUpdate guestPreferencesUpdate) {
        guestControl.writePreferences(guestPreferencesUpdate);
    }
}
