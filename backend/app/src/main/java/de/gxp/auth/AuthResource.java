package de.gxp.auth;

import de.gxp.api.ManagerAuthApi;
import de.gxp.api.model.LoginRequest;
import de.gxp.api.model.LoginResponse;
import de.gxp.api.model.PasswordChangeRequest;

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
    public void changeManagerPassword(PasswordChangeRequest passwordChangeRequest) {
        authControl.changePassword(passwordChangeRequest);
    }

    @Override
    public LoginResponse managerLogin(LoginRequest loginRequest) {
        return authControl.login(loginRequest);
    }

    @Override
    public void managerLogout() {
        authControl.logout();
    }
}
