import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup, configure } from '@testing-library/react';

// Testing Library's 1000 ms default is shorter than a hosted CI runner's slowest render; a local run never
// waits this long, so raising it only gives a slow runner room and hides no real hang.
configure({ asyncUtilTimeout: 5000 });

afterEach(() => {
  cleanup();
});
