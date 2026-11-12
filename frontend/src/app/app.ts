import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToastModule } from 'primeng/toast';

/**
 * The sign-in page shows on its own; every other page renders inside the shell (see app.routes.ts).
 * The app's one toast and one confirm dialog live here, so they work on every page, sign-in included.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastModule, ConfirmDialogModule],
  template: `
    <router-outlet />
    <p-toast position="top-right" />
    <p-confirmdialog [style]="{ width: '440px', maxWidth: 'calc(100vw - 32px)' }" />
  `,
})
export class App {}
