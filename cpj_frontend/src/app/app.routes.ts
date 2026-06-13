/*
 * File: app.routes.ts
 * Purpose: Defines global client-side routing maps for the student arena and admin console.
 */

import { Routes } from '@angular/router';
import { LandingComponent } from './landing/landing.component';
import { StudentLoginComponent } from './auth/student-login.component';
import { AdminLoginComponent } from './auth/admin-login.component';
import { DashboardComponent } from './student/dashboard/dashboard.component';
import { ContestArenaComponent } from './student/contest-arena/contest-arena.component';
import { DashboardLayoutComponent } from './admin/dashboard-layout/dashboard-layout.component';
import { OverviewComponent } from './admin/overview/overview.component';
import { ContestsComponent } from './admin/contests/contests.component';
import { StudentsComponent } from './admin/students/students.component';
import { ProblemsComponent } from './admin/problems/problems.component';

export const routes: Routes = [
  { path: '', component: LandingComponent },
  { path: 'student-login', component: StudentLoginComponent },
  { path: 'admin-login', component: AdminLoginComponent },
  { path: 'student/dashboard', component: DashboardComponent },
  { path: 'contest/:id', component: ContestArenaComponent },
  {
    path: 'admin',
    component: DashboardLayoutComponent,
    children: [
      { path: '', redirectTo: 'overview', pathMatch: 'full' },
      { path: 'overview', component: OverviewComponent },
      { path: 'contests', component: ContestsComponent },
      { path: 'problems', component: ProblemsComponent },
      { path: 'students', component: StudentsComponent }
    ]
  },
  { path: '**', redirectTo: '' }
];
