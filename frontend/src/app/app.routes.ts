import { Routes } from '@angular/router';

import { MessageDetailPage } from './features/messages/message-detail-page';
import { MessageListPage } from './features/messages/message-list-page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'messages' },
  { path: 'messages', component: MessageListPage },
  { path: 'messages/:id', component: MessageDetailPage },
  { path: '**', redirectTo: 'messages' },
];
