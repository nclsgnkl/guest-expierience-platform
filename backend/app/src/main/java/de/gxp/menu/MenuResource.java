package de.gxp.menu;

import java.util.List;

import de.gxp.api.ManagerMenuApi;
import de.gxp.api.model.ActionCardDto;
import de.gxp.api.model.MenuItemConfigEntryDto;
import de.gxp.api.model.MenuItemDefinitionDto;

/**
 * Boundary — JAX-RS adapter over the generated {@link ManagerMenuApi}.
 * Every method body is ONE delegating line (spec §4, binding);
 * {@code null} is the Control's property-scope marker.
 */
public class MenuResource implements ManagerMenuApi {

    private final MenuControl menuControl;

    MenuResource(MenuControl menuControl) {
        this.menuControl = menuControl;
    }

    @Override
    public List<ActionCardDto> getActionCards() {
        return menuControl.getCards(null);
    }

    @Override
    public List<MenuItemDefinitionDto> getMenuCatalogue() {
        return menuControl.getCatalogue();
    }

    @Override
    public List<MenuItemConfigEntryDto> getMenuConfig() {
        return menuControl.getConfig(null);
    }

    @Override
    public List<ActionCardDto> getRoomActionCards(Long roomId) {
        return menuControl.getCards(roomId);
    }

    @Override
    public List<MenuItemConfigEntryDto> getRoomMenuConfig(Long roomId) {
        return menuControl.getConfig(roomId);
    }

    @Override
    public List<ActionCardDto> updateActionCards(List<ActionCardDto> actionCardDto) {
        return menuControl.putCards(null, actionCardDto);
    }

    @Override
    public List<MenuItemConfigEntryDto> updateMenuConfig(List<MenuItemConfigEntryDto> menuItemConfigEntryDto) {
        return menuControl.putConfig(null, menuItemConfigEntryDto);
    }

    @Override
    public List<ActionCardDto> updateRoomActionCards(Long roomId, List<ActionCardDto> actionCardDto) {
        return menuControl.putCards(roomId, actionCardDto);
    }

    @Override
    public List<MenuItemConfigEntryDto> updateRoomMenuConfig(Long roomId,
            List<MenuItemConfigEntryDto> menuItemConfigEntryDto) {
        return menuControl.putConfig(roomId, menuItemConfigEntryDto);
    }
}
