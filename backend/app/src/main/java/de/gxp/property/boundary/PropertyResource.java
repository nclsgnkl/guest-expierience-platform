package de.gxp.property;

import de.gxp.api.ManagerPropertyApi;
import de.gxp.api.model.PropertyDto;

/**
 * Boundary — JAX-RS adapter over the generated {@link ManagerPropertyApi}.
 * Every method body is ONE delegating line (spec §4, binding).
 */
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
