// Fixture only: the violating import that src/featureBoundary.test.ts proves
// the boundary rule catches. Excluded from the routine `npm run lint` gate
// by eslint.config.js so this deliberate violation doesn't fail every build.
import { boundaryFixtureMarker } from "../events/__boundaryFixture";

export const checkinBoundaryFixtureMarker = boundaryFixtureMarker;
