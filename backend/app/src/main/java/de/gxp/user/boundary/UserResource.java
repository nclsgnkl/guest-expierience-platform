package de.gxp.user.boundary;

import java.util.List;

import org.jboss.resteasy.reactive.ResponseStatus;

import de.gxp.api.ManagerUsersApi;
import de.gxp.api.model.PasswordConfirmationRequest;
import de.gxp.api.model.PasswordResetRequest;
import de.gxp.api.model.RecoveryCodesDto;
import de.gxp.api.model.SecondFactorCodeRequest;
import de.gxp.api.model.SecondFactorEnrollmentDto;
import de.gxp.api.model.SecondFactorStatusDto;
import de.gxp.api.model.UserCreateRequest;
import de.gxp.api.model.UserDto;
import de.gxp.user.control.UserControl;
import jakarta.annotation.security.RolesAllowed;

/**
 * Boundary — manager account administration over the generated
 * {@link ManagerUsersApi} (tag {@code manager-users}). Whole surface admin,
 * hence the class-level {@code @RolesAllowed} (allowed for that annotation,
 * {@code backend/CLAUDE.md}). Every method body is ONE delegating line
 * (spec §4, binding).
 */
@RolesAllowed("admin")
public class UserResource implements ManagerUsersApi {

    private final UserControl userControl;

    UserResource(UserControl userControl) {
        this.userControl = userControl;
    }

    @Override
    @ResponseStatus(201) // contract: created — HTTP-adapter concern, so it sits on the Boundary
    public UserDto createUser(UserCreateRequest userCreateRequest) {
        return userControl.create(userCreateRequest);
    }

    @Override
    public void deactivateUser(Long userId) {
        userControl.deactivate(userId);
    }

    @Override
    public List<UserDto> listUsers() {
        return userControl.list();
    }

    @Override
    public void reactivateUser(Long userId) {
        userControl.reactivate(userId);
    }

    @Override
    public void resetUserPassword(Long userId, PasswordResetRequest passwordResetRequest) {
        userControl.resetPassword(userId, passwordResetRequest);
    }

    @Override
    public void resetUserSecondFactor(Long userId) {
        userControl.resetSecondFactor(userId);
    }

    @Override
    public SecondFactorStatusDto getOwnSecondFactor() {
        return userControl.secondFactorStatus();
    }

    @Override
    public SecondFactorEnrollmentDto enrollOwnSecondFactor() {
        return userControl.enrollSecondFactor();
    }

    @Override
    public RecoveryCodesDto confirmOwnSecondFactor(SecondFactorCodeRequest secondFactorCodeRequest) {
        return userControl.confirmSecondFactor(secondFactorCodeRequest);
    }

    @Override
    public void disableOwnSecondFactor(PasswordConfirmationRequest passwordConfirmationRequest) {
        userControl.disableSecondFactor(passwordConfirmationRequest);
    }

    @Override
    public RecoveryCodesDto regenerateOwnRecoveryCodes(PasswordConfirmationRequest passwordConfirmationRequest) {
        return userControl.regenerateRecoveryCodes(passwordConfirmationRequest);
    }
}
