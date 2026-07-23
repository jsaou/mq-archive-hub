import { HttpParams } from '@angular/common/http';

import { API_BASE_PATH } from '../../core/api/api.config';
import { MessageSearchFilters } from './message.model';

export function buildMessagesListRequest(
  filters: MessageSearchFilters,
  page: number,
  size: number,
): { url: string; params: HttpParams } {
  let params = new HttpParams()
    .set('page', page)
    .set('size', size)
    .set('sort', 'id,asc');

  const queueName = filters.queueName.trim();
  const messageId = filters.messageId.trim();
  const correlationId = filters.correlationId.trim();

  if (queueName) {
    params = params.set('queueName', queueName);
  }
  if (filters.status) {
    params = params.set('status', filters.status);
  }
  if (messageId) {
    params = params.set('messageId', messageId);
  }
  if (correlationId) {
    params = params.set('correlationId', correlationId);
  }

  return {
    url: `${API_BASE_PATH}/messages`,
    params,
  };
}
