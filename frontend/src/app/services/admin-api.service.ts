import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AdminContest,
  AdminProblem,
  AdminTestCase,
  AdminUser,
  UserRole,
} from '../models/admin.models';
import { SessionService } from './session.service';

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionService);

  createUser(body: {
    name: string;
    rollNo: string;
    branch?: string;
    role?: UserRole;
  }): Observable<AdminUser> {
    return this.http.post<AdminUser>('/api/admin/users', body, { headers: this.authHeaders() });
  }

  listUsers(query?: string, page: number = 0, size: number = 20): Observable<any> {
    let url = `/api/admin/users?page=${page}&size=${size}`;
    if (query && query.trim()) {
      url += `&query=${encodeURIComponent(query.trim())}`;
    }
    return this.http.get<any>(url, { headers: this.authHeaders() });
  }

  createProblem(body: {
    title: string;
    description: string;
    constraints?: string;
    difficulty?: string;
    mediaLink?: string;
  }): Observable<AdminProblem> {
    return this.http.post<AdminProblem>('/api/admin/problems', body, { headers: this.authHeaders() });
  }

  listProblems(): Observable<AdminProblem[]> {
    return this.http.get<AdminProblem[]>('/api/admin/problems', { headers: this.authHeaders() });
  }

  createTestCase(
    problemId: number,
    body: { stdin?: string; expectedOutput: string; isSample: boolean },
  ): Observable<AdminTestCase> {
    return this.http.post<AdminTestCase>(`/api/admin/problems/${problemId}/test-cases`, body, {
      headers: this.authHeaders(),
    });
  }

  listTestCases(problemId: number): Observable<AdminTestCase[]> {
    return this.http.get<AdminTestCase[]>(`/api/admin/problems/${problemId}/test-cases`, {
      headers: this.authHeaders(),
    });
  }

  deleteTestCase(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/test-cases/${id}`, { headers: this.authHeaders() });
  }

  createContest(body: {
    title: string;
    startTime: string;
    durationMins: number;
    problems: { problemId: number; points: number; displayOrder: number }[];
  }): Observable<AdminContest> {
    return this.http.post<AdminContest>('/api/admin/contests', body, { headers: this.authHeaders() });
  }

  listContests(): Observable<AdminContest[]> {
    return this.http.get<AdminContest[]>('/api/admin/contests', { headers: this.authHeaders() });
  }

  startContest(id: number): Observable<AdminContest> {
    return this.http.post<AdminContest>(`/api/admin/contests/${id}/start`, null, {
      headers: this.authHeaders(),
    });
  }

  endContest(id: number): Observable<AdminContest> {
    return this.http.post<AdminContest>(`/api/admin/contests/${id}/end`, null, {
      headers: this.authHeaders(),
    });
  }

  private authHeaders(): HttpHeaders {
    return new HttpHeaders({ 'X-Roll-No': this.session.rollNo() });
  }
}
