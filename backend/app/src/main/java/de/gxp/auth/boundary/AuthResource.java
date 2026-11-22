package de.gxp.auth.boundary;

import de.gxp.api.ManagerAuthApi;
import de.gxp.api.model.LoginRequest;
import de.gxp.api.model.LoginResponse;
import de.gxp.api.model.PasswordChangeRequest;
import de.gxp.auth.control.AuthControl;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

/**
 * Boundary — JAX-RS adapter over the generated {@link ManagerAuthApi}.
 * Every method body is ONE delegating line (spec §4, binding).
 */
public class AuthResource implements ManagerAuthApi {

    private final AuthControl authControl;

    AuthResource(AuthControl authControl) {
        this.authControl = authControl;
    }

    @Override
    @RolesAllowed("admin")
    public void changeManagerPassword(PasswordChangeRequest passwordChangeRequest) {
        authControl.changePassword(passwordChangeRequest);
    }

    @Override
    @PermitAll
    public LoginResponse managerLogin(LoginRequest loginRequest) {
        return authControl.login(loginRequest);
    }

    @Override
    @RolesAllowed("admin")
    public void managerLogout() {
        authControl.logout();
    }
}
