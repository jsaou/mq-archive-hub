export type MessageStatus = 'RECEIVED' | 'ERROR' | 'DLQ';

/** List item — no payload (loaded only on detail). */
export interface MqMessageSummary {
  id: number;
  messageId: string;
  correlationId: string | null;
  contentType: string | null;
  status: MessageStatus;
  receivedAt: string;
}

/** Detail view — includes payload. */
export interface MqMessageDetail extends MqMessageSummary {
  payload: string;
}

export interface PageMeta {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

export interface MessagePage {
  content: MqMessageSummary[];
  page: PageMeta;
}

export interface MessageSearchFilters {
  queueName: string;
  status: MessageStatus | '';
  messageId: string;
  correlationId: string;
}

export const MESSAGE_STATUSES: readonly MessageStatus[] = ['RECEIVED', 'ERROR', 'DLQ'] as const;

export const EMPTY_MESSAGE_FILTERS: MessageSearchFilters = {
  queueName: '',
  status: '',
  messageId: '',
  correlationId: '',
};

export function statusChipClass(status: MessageStatus): string {
  return `status status--${status.toLowerCase()}`;
}
