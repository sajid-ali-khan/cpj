/*
 * File: landing.component.ts
 * Purpose: Entry component displaying the landing portal for students and administrators.
 */

import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './landing.component.html',
  styles: [`
    .landing-box {
      max-width: 450px;
      margin: 100px auto;
      text-align: center;
      border: 1px solid var(--border-color);
      background-color: var(--bg-panel);
      padding: 40px 30px;
      border-radius: 4px;
    }
    .landing-title {
      margin-bottom: 30px;
      font-size: 26px;
    }
    .portal-buttons {
      display: flex;
      flex-direction: column;
      gap: 15px;
    }
    .portal-buttons .btn {
      padding: 12px;
      font-size: 16px;
    }
  `]
})
export class LandingComponent {}
