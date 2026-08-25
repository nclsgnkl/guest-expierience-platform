package de.gxp.property.boundary;

import de.gxp.api.ManagerPropertyApi;
import de.gxp.api.model.PropertyDto;
import de.gxp.property.control.PropertyControl;
import jakarta.annotation.security.RolesAllowed;

/**
 * Boundary — JAX-RS adapter over the generated {@link ManagerPropertyApi}.
 * Every method body is ONE delegating line (spec §4, binding).
 */
@RolesAllowed("admin")
public class PropertyResource implements ManagerPropertyApi {

    private final PropertyControl propertyControl;

    PropertyResource(PropertyControl propertyControl) {
        this.propertyControl = propertyControl;
    }

    @Override
    public PropertyDto getProperty() {
        return propertyControl.get();
    }

    @Override
    public PropertyDto updateProperty(PropertyDto propertyDto) {
        return propertyControl.update(propertyDto);
    }
}
