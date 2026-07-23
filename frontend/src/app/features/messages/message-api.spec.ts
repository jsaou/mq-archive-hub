import { buildMessageDetailRequest, buildMessagesListRequest } from './message-api';
import { EMPTY_MESSAGE_FILTERS } from './message.model';

describe('buildMessagesListRequest', () => {
  it('builds the list URL with paging and default sort', () => {
    const request = buildMessagesListRequest(EMPTY_MESSAGE_FILTERS, 2, 50);

    expect(request.url).toBe('/api/v1/messages');
    expect(request.params.get('page')).toBe('2');
    expect(request.params.get('size')).toBe('50');
    expect(request.params.get('sort')).toBe('receivedAt,desc');
  });

  it('omits blank filter values', () => {
    const request = buildMessagesListRequest(
      {
        queueName: '  ',
        status: '',
        messageId: '',
        correlationId: '   ',
      },
      0,
      20,
    );

    expect(request.params.get('queueName')).toBeNull();
    expect(request.params.get('status')).toBeNull();
    expect(request.params.get('messageId')).toBeNull();
    expect(request.params.get('correlationId')).toBeNull();
  });

  it('includes trimmed filters when set', () => {
    const request = buildMessagesListRequest(
      {
        queueName: '  PAYMENTS.IN  ',
        status: 'ERROR',
        messageId: ' ID:1 ',
        correlationId: ' CORR:9 ',
      },
      0,
      20,
    );

    expect(request.params.get('queueName')).toBe('PAYMENTS.IN');
    expect(request.params.get('status')).toBe('ERROR');
    expect(request.params.get('messageId')).toBe('ID:1');
    expect(request.params.get('correlationId')).toBe('CORR:9');
  });
});

describe('buildMessageDetailRequest', () => {
  it('builds the detail URL for a numeric id', () => {
    expect(buildMessageDetailRequest(42)).toEqual({ url: '/api/v1/messages/42' });
    expect(buildMessageDetailRequest('7')).toEqual({ url: '/api/v1/messages/7' });
  });

  it('skips invalid ids', () => {
    expect(buildMessageDetailRequest('')).toBeUndefined();
    expect(buildMessageDetailRequest('abc')).toBeUndefined();
  });
});
