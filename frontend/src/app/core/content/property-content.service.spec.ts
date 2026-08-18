import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { EXAMPLE_PROPERTY } from './example-property.data';
import { PropertyContentService } from './property-content.service';

describe('PropertyContentService', () => {
  function setup(): PropertyContentService {
    TestBed.configureTestingModule({});
    return TestBed.inject(PropertyContentService);
  }

  it('getProperty() returns the example property config shape', () => {
    const service = setup();
    const property = service.getProperty();

    expect(property.guestName).toBe('Lisa');
    expect(property.headline).toContain('{guestName}');
    expect(property.menuItems).toHaveLength(5);
    expect(property.settings.id).toBe('einstellungen');
    expect(property.cards).toHaveLength(4);
  });

  it('getSectionRoute() resolves every example card targetSectionId to a real route', () => {
    const service = setup();
    const property = service.getProperty();

    for (const card of property.cards) {
      const route = service.getSectionRoute(card.targetSectionId);
      const expected = [...property.menuItems, property.settings].find(
        (item) => item.id === card.targetSectionId,
      );
      expect(route).toBe(expected?.route);
    }
  });

  it('getSectionRoute() resolves both cards sharing targetSectionId "info" to the identical route', () => {
    const service = setup();
    const checkOut = EXAMPLE_PROPERTY.cards.find((c) => c.id === 'check-out')!;
    const wichtigeInfos = EXAMPLE_PROPERTY.cards.find((c) => c.id === 'wichtige-infos')!;

    expect(checkOut.targetSectionId).toBe('info');
    expect(wichtigeInfos.targetSectionId).toBe('info');
    expect(service.getSectionRoute(checkOut.targetSectionId)).toBe(
      service.getSectionRoute(wichtigeInfos.targetSectionId),
    );
  });

  it('getSectionRoute() throws for an unknown id', () => {
    const service = setup();
    expect(() => service.getSectionRoute('not-a-real-section')).toThrow();
  });
});
