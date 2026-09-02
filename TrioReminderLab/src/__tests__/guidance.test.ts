import { guidanceForManufacturer } from '../oem/guidance';

/**
 * The ported OEM table. Mirrors the native OemGuidanceTableTest so a divergence
 * between the two implementations shows up as a failing test rather than a
 * device that silently gets no guidance.
 */
describe('guidanceForManufacturer', () => {
  it('covers the manufacturers the report names', () => {
    for (const brand of ['samsung', 'Xiaomi', 'OnePlus', 'OPPO', 'realme', 'vivo', 'HUAWEI']) {
      expect(guidanceForManufacturer(brand)).not.toBeNull();
    }
  });

  it('matches case-insensitively', () => {
    expect(guidanceForManufacturer('SAMSUNG')).toEqual(guidanceForManufacturer('samsung'));
  });

  it('maps sub-brands to their parent guidance', () => {
    expect(guidanceForManufacturer('Redmi')?.brandLabel).toBe('Xiaomi');
    expect(guidanceForManufacturer('POCO')?.brandLabel).toBe('Xiaomi');
    expect(guidanceForManufacturer('honor')?.brandLabel).toBe('Huawei');
    expect(guidanceForManufacturer('iqoo')?.brandLabel).toBe('vivo');
  });

  it('keeps the realme label while sharing OPPO steps', () => {
    const realme = guidanceForManufacturer('realme');
    const oppo = guidanceForManufacturer('oppo');
    expect(realme?.brandLabel).toBe('realme');
    expect(oppo?.brandLabel).toBe('OPPO');
    expect(realme?.steps).toEqual(oppo?.steps);
  });

  it('gives usable, non-empty steps for every known brand', () => {
    for (const brand of ['samsung', 'xiaomi', 'oneplus', 'oppo', 'vivo', 'huawei']) {
      const g = guidanceForManufacturer(brand);
      expect(g).not.toBeNull();
      expect(g!.steps.length).toBeGreaterThan(0);
      expect(g!.steps.every((s) => s.trim().length > 0)).toBe(true);
    }
  });

  it('returns null for unknown or empty manufacturers', () => {
    expect(guidanceForManufacturer('Google')).toBeNull();
    expect(guidanceForManufacturer('')).toBeNull();
    expect(guidanceForManufacturer(null)).toBeNull();
  });
});
