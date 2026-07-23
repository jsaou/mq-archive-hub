import { expect, test, type Page } from '@playwright/test';

const SAMPLE_PAGE = {
  content: [
    {
      id: 42,
      messageId: 'ID:e2e-msg',
      correlationId: 'CORR:e2e',
      contentType: 'application/json',
      status: 'RECEIVED',
      receivedAt: '2026-07-21T10:00:00Z',
    },
  ],
  page: {
    size: 20,
    number: 0,
    totalElements: 1,
    totalPages: 1,
  },
};

const SAMPLE_DETAIL = {
  ...SAMPLE_PAGE.content[0],
  payload: '{"amount":42,"currency":"EUR"}',
};

async function mockMessagesApi(page: Page): Promise<void> {
  await page.route(/\/api\/v1\/messages(?:\?.*)?$/, async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname !== '/api/v1/messages') {
      await route.fallback();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(SAMPLE_PAGE),
    });
  });

  await page.route(/\/api\/v1\/messages\/\d+$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(SAMPLE_DETAIL),
    });
  });
}

test.describe('Messages archive UI', () => {
  test('lists messages and opens detail with payload', async ({ page }) => {
    await mockMessagesApi(page);

    await page.goto('/messages');

    await expect(page).toHaveTitle(/MQ Archive Hub/);
    await expect(page.getByTestId('message-list-page')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Archived messages' })).toBeVisible();
    await expect(page.getByText('ID:e2e-msg')).toBeVisible();
    await expect(page.getByTestId('message-row-42').getByText('RECEIVED')).toBeVisible();

    await page.getByTestId('message-row-42').click();

    await expect(page).toHaveURL(/\/messages\/42$/);
    await expect(page.getByTestId('message-detail-page')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Message detail' })).toBeVisible();
    await expect(page.getByTestId('message-payload')).toContainText(
      '{"amount":42,"currency":"EUR"}',
    );

    await page.getByTestId('back-to-messages').click();
    await expect(page).toHaveURL(/\/messages$/);
    await expect(page.getByTestId('message-list-page')).toBeVisible();
  });

  test('shows empty state when API returns no rows', async ({ page }) => {
    await page.route(/\/api\/v1\/messages(?:\?.*)?$/, async (route) => {
      const url = new URL(route.request().url());
      if (url.pathname !== '/api/v1/messages') {
        await route.fallback();
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [],
          page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
        }),
      });
    });

    await page.goto('/messages');

    await expect(page.getByText('No messages match the current filters.')).toBeVisible();
  });
});
