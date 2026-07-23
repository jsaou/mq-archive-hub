import { DatePipe } from '@angular/common';
import { HttpErrorResponse, httpResource } from '@angular/common/http';
import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatChip } from '@angular/material/chips';
import { MatProgressBar } from '@angular/material/progress-bar';

import { buildMessageDetailRequest } from './message-api';
import { describeHttpLoadError } from './http-load-error';
import { MqMessageDetail, statusChipClass } from './message.model';

@Component({
  selector: 'app-message-detail-page',
  imports: [DatePipe, RouterLink, MatButton, MatChip, MatProgressBar],
  templateUrl: './message-detail-page.html',
  styleUrl: './message-detail-page.scss',
})
export class MessageDetailPage {
  readonly id = input.required<string>();

  protected readonly message = httpResource<MqMessageDetail>(() =>
    buildMessageDetailRequest(this.id()),
  );

  protected readonly detail = computed(() =>
    this.message.hasValue() ? this.message.value() : null,
  );

  protected readonly notFound = computed(() => {
    const error = this.message.error();
    return error instanceof HttpErrorResponse && error.status === 404;
  });

  protected reload(): void {
    this.message.reload();
  }

  protected loadErrorMessage(error: unknown): string {
    return describeHttpLoadError(error, 'Please try again.');
  }

  protected statusClass = statusChipClass;
}
