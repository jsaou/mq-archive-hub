import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle, MatCardSubtitle } from '@angular/material/card';

@Component({
  selector: 'app-message-detail-page',
  imports: [
    RouterLink,
    MatButton,
    MatCard,
    MatCardHeader,
    MatCardTitle,
    MatCardSubtitle,
    MatCardContent,
  ],
  templateUrl: './message-detail-page.html',
  styleUrl: './message-detail-page.scss',
})
export class MessageDetailPage {
  // Bound from the `:id` route param.
  readonly id = input.required<string>();
}
