import { isValidElement } from 'react';
import { describe, expect, it } from 'vitest';
import { router } from './router';

function getChildRoute(path: string) {
  const rootRoute = router.routes.find((route) => route.path === '/');
  return rootRoute?.children?.find((route) => route.path === path);
}

describe('router', () => {
  it('wraps page routes in suspense for lazy loading', () => {
    const routePaths = ['workbench', 'knowledge', 'agent-config', 'tool-audit', 'tasks/:taskId', 'collaboration/:sessionId'];

    for (const path of routePaths) {
      const route = getChildRoute(path);
      expect(route).toBeDefined();
      const element = route?.element;
      expect(isValidElement(element)).toBe(true);
      if (!isValidElement(element)) {
        throw new Error(`${path} route element is not a React element`);
      }
      expect(String(element.type)).toBe(Symbol.for('react.suspense').toString());
    }
  });
});
