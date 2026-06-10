import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ContestProblemSummary, ContestSummary } from '../models/admin.models';
import {
  LeaderboardEntry,
  SubmissionRequest,
  SubmissionResponse,
  SubmitResponse,
} from '../models/api.models';
import { SessionService } from './session.service';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionService);

  submit(request: SubmissionRequest): Observable<SubmitResponse> {
    return this.http.post<SubmitResponse>('/api/submissions', request, {
      headers: this.authHeaders(),
    });
  }

  getSubmissions(contestId: number): Observable<SubmissionResponse[]> {
    return this.http.get<SubmissionResponse[]>('/api/submissions', {
      headers: this.authHeaders(),
      params: { contestId },
    });
  }

  getLeaderboard(contestId: number): Observable<LeaderboardEntry[]> {
    return this.http.get<LeaderboardEntry[]>('/api/leaderboard', {
      headers: this.authHeaders(),
      params: { contestId },
    });
  }

  getCurrentContest(): Observable<ContestSummary> {
    return this.http.get<ContestSummary>('/api/contests/current', {
      headers: this.authHeaders(),
    });
  }

  getContestProblems(contestId: number): Observable<ContestProblemSummary[]> {
    return this.http.get<ContestProblemSummary[]>(`/api/contests/${contestId}/problems`, {
      headers: this.authHeaders(),
    });
  }

  private authHeaders(): HttpHeaders {
    return new HttpHeaders({ 'X-Roll-No': this.session.rollNo() });
  }
}
