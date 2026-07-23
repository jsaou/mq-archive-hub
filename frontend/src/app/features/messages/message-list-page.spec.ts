import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { MessageListPage } from './message-list-page';
import { MessagePage } from './message.model';

const SAMPLE_PAGE: MessagePage = {
  content: [
    {
      id: 42,
      messageId: 'ID:abc',
      correlationId: 'CORR:1',
      contentType: 'text/plain',
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

describe('MessageListPage', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MessageListPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function createFixture(): ComponentFixture<MessageListPage> {
    const fixture = TestBed.createComponent(MessageListPage);
    fixture.detectChanges();
    return fixture;
  }

  function flushList(body: MessagePage | object, status = 200): void {
    const req = httpMock.expectOne(
      (request) => request.method === 'GET' && request.url === '/api/v1/messages',
    );
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('sort')).toBe('receivedAt,desc');

    if (status === 200) {
      req.flush(body);
    } else {
      req.flush(body, { status, statusText: 'Error' });
    }
  }

  it('should create', () => {
    const fixture = createFixture();
    flushList(SAMPLE_PAGE);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders status filter options including All', () => {
    const fixture = createFixture();
    flushList(SAMPLE_PAGE);
    fixture.detectChanges();

    const options = Array.from(
      fixture.nativeElement.querySelectorAll('select[matNativeControl] option'),
    ).map((option) => (option as HTMLOptionElement).textContent?.trim());

    expect(options).toEqual(['All', 'RECEIVED', 'ERROR', 'DLQ']);
  });

  it('loads messages into the table', async () => {
    const fixture = createFixture();
    flushList(SAMPLE_PAGE);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('ID:abc');
    expect(text).toContain('CORR:1');
    expect(text).toContain('RECEIVED');
  });

  it('shows an empty state when the API returns no rows', async () => {
    const fixture = createFixture();
    flushList({
      content: [],
      page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
    } satisfies MessagePage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'No messages match the current filters.',
    );
  });

  it('shows an error state when the API fails', async () => {
    const fixture = createFixture();
    flushList({ message: 'boom' }, 500);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Failed to load messages');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('shows a clear message when the API is unreachable', async () => {
    const fixture = createFixture();
    const req = httpMock.expectOne(
      (request) => request.method === 'GET' && request.url === '/api/v1/messages',
    );
    req.error(new ProgressEvent('error'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Cannot reach the API');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });
});
