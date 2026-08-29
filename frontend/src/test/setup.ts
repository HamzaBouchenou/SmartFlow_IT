import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// `globals: false` (vite.config.ts) - Testing Library ne peut pas patcher un
// `afterEach` global qui n'existe pas ; il faut donc démonter chaque composant rendu
// explicitement entre deux tests, sinon un `screen.getByLabelText` d'un test suivant
// peut retrouver plusieurs éléments issus des rendus précédents.
afterEach(() => {
  cleanup();
});
