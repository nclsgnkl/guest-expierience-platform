package de.gxp.user.entity;

import java.time.ZoneOffset;

import de.gxp.api.model.SecondFactorState;
import de.gxp.api.model.UserDto;

/**
 * Hand-written mapper (spec §4) — static, no mapping framework. The DTO NEVER
 * carries password material (REQ-613): {@code passwordHash} is deliberately
 * not mapped, not even redacted; neither are the second-factor secret and
 * the replay step (REQ-619). Public only because {@code de.gxp.user.control} invokes it; it is
 * still a {@code de.gxp.user} internal.
 */
public final class ManagerUserMapper {

    private ManagerUserMapper() {
    }

    public static UserDto toDto(ManagerUser user) {
        return new UserDto()
                .id(user.id)
                .username(user.username)
                .active(user.active)
                .secondFactorState(secondFactorState(user))
                .createdAt(user.createdAt.atOffset(ZoneOffset.UTC));
    }

    /** Derived from the two nullable columns: no secret = none; secret without confirmation = pending. */
    public static SecondFactorState secondFactorState(ManagerUser user) {
        if (user.totpSecret == null) {
            return SecondFactorState.NONE;
        }
        return user.totpConfirmedAt == null ? SecondFactorState.PENDING : SecondFactorState.CONFIRMED;
    }
}
