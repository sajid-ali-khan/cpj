import { Routes } from '@angular/router';
import { AdminComponent } from './pages/admin/admin.component';
import { ArenaComponent } from './pages/arena/arena.component';
import { HomeComponent } from './pages/home/home.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'arena', component: ArenaComponent },
  { path: 'admin', component: AdminComponent },
  { path: '**', redirectTo: '' },
];
