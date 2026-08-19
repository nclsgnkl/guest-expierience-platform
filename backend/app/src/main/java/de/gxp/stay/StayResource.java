package de.gxp.stay;

import java.util.List;

import org.jboss.resteasy.reactive.ResponseStatus;

import de.gxp.api.ManagerStaysApi;
import de.gxp.api.model.StayDto;

/**
 * Boundary — JAX-RS adapter over the generated {@link ManagerStaysApi}.
 * Every method body is ONE delegating line (spec §4, binding).
 */
public class StayResource implements ManagerStaysApi {

    private final StayControl stayControl;

    StayResource(StayControl stayControl) {
        this.stayControl = stayControl;
    }

    @Override
    @ResponseStatus(201) // contract: created — HTTP-adapter concern, so it sits on the Boundary
    public StayDto createStay(StayDto stayDto) {
        return stayControl.create(stayDto);
    }

    @Override
    public void deleteStay(Long stayId) {
        stayControl.delete(stayId);
    }

    @Override
    public StayDto getStay(Long stayId) {
        return stayControl.get(stayId);
    }

    @Override
    public List<StayDto> listStays(Long roomId) {
        return stayControl.list(roomId);
    }

    @Override
    public StayDto updateStay(Long stayId, StayDto stayDto) {
        return stayControl.update(stayId, stayDto);
    }
}
