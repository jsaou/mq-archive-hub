import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { MessageDetailPage } from './message-detail-page';
import { MqMessageDetail } from './message.model';

const SAMPLE_DETAIL: MqMessageDetail = {
  id: 42,
  messageId: 'ID:abc',
  correlationId: 'CORR:1',
  contentType: 'text/plain',
  status: 'RECEIVED',
  receivedAt: '2026-07-21T10:00:00Z',
  payload: '{"amount":100}',
};

describe('MessageDetailPage', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MessageDetailPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function createFixture(id = '42'): ComponentFixture<MessageDetailPage> {
    const fixture = TestBed.createComponent(MessageDetailPage);
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
    return fixture;
  }

  function flushDetail(body: object, status = 200): void {
    const req = httpMock.expectOne('/api/v1/messages/42');
    expect(req.request.method).toBe('GET');
    if (status === 200) {
      req.flush(body);
    } else {
      req.flush(body, { status, statusText: 'Error' });
    }
  }

  it('loads message metadata and payload', async () => {
    const fixture = createFixture();
    flushDetail(SAMPLE_DETAIL);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('ID:abc');
    expect(text).toContain('RECEIVED');
    expect(text).toContain('{"amount":100}');
  });

  it('shows not found for HTTP 404', async () => {
    const fixture = createFixture();
    flushDetail({ detail: 'Message not found: 42' }, 404);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Message not found.');
  });
});
