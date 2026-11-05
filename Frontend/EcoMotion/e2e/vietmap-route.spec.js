import { expect, test } from '@playwright/test'

async function mountMap(page, routeResponse, status = 200) {
  const requests = []
  await page.route('**/api/location/route?*', route => {
    requests.push(new URL(route.request().url()).searchParams)
    return route.fulfill({ status, json: routeResponse })
  })
  await page.route('**/map-harness', route => route.fulfill({ contentType: 'text/html', body: `
    <div id="root"></div><script type="module">
      import React from '/node_modules/.vite/deps/react.js';
      import ReactDOMClient from '/node_modules/.vite/deps/react-dom_client.js';
      import RefreshRuntime from '/@react-refresh';
      RefreshRuntime.injectIntoGlobalHook(window);
      window.$RefreshReg$ = () => {}; window.$RefreshSig$ = () => type => type;
      window.__vite_plugin_react_preamble_installed__ = true;
      const { default: RoadRouteMap } = await import('/src/components/map/RoadRouteMap.jsx');
      ReactDOMClient.createRoot(document.getElementById('root')).render(React.createElement(RoadRouteMap, {
        from: { latitude: 10.8, longitude: 106.7 }, to: { latitude: 10.81, longitude: 106.72 }
      }));
    </script>` }))
  await page.goto('/map-harness')
  return requests
}

test('road map renders returned road geometry, distance and ETA without requesting old tiles', async ({ page }) => {
  const oldSources = []
  page.on('request', req => { if (/tile\.openstreetmap|google\.com\/maps/.test(req.url())) oldSources.push(req.url()) })
  const requests = await mountMap(page, { data: { distanceMeters: 4200, durationSeconds: 660,
    coordinates: [[106.7, 10.8], [106.705, 10.82], [106.72, 10.81]], source: 'VIETMAP_CAR' } })
  await expect(page.getByText(/Tuyến tham khảo VIETMAP/)).toContainText('4.20 km · khoảng 11 phút')
  await expect(page.locator('.leaflet-overlay-pane path[stroke="#047857"][fill="none"]')).toBeVisible()
  expect(requests[0].get('fromLatitude')).toBe('10.8')
  expect(requests[0].get('fromLongitude')).toBe('106.7')
  expect(oldSources).toEqual([])
})

test('route failures do not invent zero kilometers or draw a straight route', async ({ page }) => {
  await mountMap(page, { message: 'No car route' }, 503)
  await expect(page.getByText('Chưa có tuyến ô tô khả dụng. Không ước lượng bằng đường chim bay.')).toBeVisible()
  await expect(page.getByText(/Tuyến tham khảo VIETMAP/)).toHaveCount(0)
  await expect(page.locator('.leaflet-overlay-pane path[stroke="#047857"][fill="none"]')).toHaveCount(0)
})
