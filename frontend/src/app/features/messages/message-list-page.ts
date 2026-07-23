import { DatePipe } from '@angular/common';
import { Component, computed, linkedSignal, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { form, FormField, debounce } from '@angular/forms/signals';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatChip } from '@angular/material/chips';

import { buildMessagesListRequest } from './message-api';
import {
  EMPTY_MESSAGE_FILTERS,
  MessagePage,
  MESSAGE_STATUSES,
  MessageStatus,
} from './message.model';

@Component({
  selector: 'app-message-list-page',
  imports: [
    DatePipe,
    RouterLink,
    FormField,
    MatButton,
    MatFormField,
    MatLabel,
    MatInput,
    MatTableModule,
    MatPaginator,
    MatProgressBar,
    MatChip,
  ],
  templateUrl: './message-list-page.html',
  styleUrl: './message-list-page.scss',
})
export class MessageListPage {
  protected readonly statuses = MESSAGE_STATUSES;
  protected readonly displayedColumns = [
    'id',
    'messageId',
    'correlationId',
    'contentType',
    'status',
    'receivedAt',
  ] as const;

  protected readonly filtersModel = signal({ ...EMPTY_MESSAGE_FILTERS });

  protected readonly filtersForm = form(this.filtersModel, (filters) => {
    debounce(filters.queueName, 300);
    debounce(filters.messageId, 300);
    debounce(filters.correlationId, 300);
  });

  // Resets to page 0 whenever filters change; still writable for the paginator.
  protected readonly pageIndex = linkedSignal({
    source: this.filtersModel,
    computation: () => 0,
  });
  protected readonly pageSize = signal(20);

  protected readonly messages = httpResource<MessagePage>(() =>
    buildMessagesListRequest(this.filtersModel(), this.pageIndex(), this.pageSize()),
  );

  protected readonly rows = computed(() =>
    this.messages.hasValue() ? this.messages.value().content : [],
  );
  protected readonly totalElements = computed(() =>
    this.messages.hasValue() ? this.messages.value().page.totalElements : 0,
  );
  protected readonly showEmpty = computed(
    () => this.messages.hasValue() && this.rows().length === 0 && !this.messages.isLoading(),
  );

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  protected resetFilters(): void {
    this.filtersModel.set({ ...EMPTY_MESSAGE_FILTERS });
  }

  protected reload(): void {
    this.messages.reload();
  }

  protected statusClass(status: MessageStatus): string {
    return `status status--${status.toLowerCase()}`;
  }
}
