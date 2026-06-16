/*
 * File: contest-arena-state.service.ts
 * Purpose: Manage state and business logic for the Student Coding Arena.
 */

import { Injectable, OnDestroy } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { getVerdictLabel } from './contest-arena.helper';

@Injectable()
export class ContestArenaStateService implements OnDestroy {
  contestId = 0;
  problems: any[] = [];
  pendingSubmissionId: number | null = null;
  private sseSource: EventSource | null = null;
  activeQ = 0;
  selectedLang = 'java';
  consoleTab: number | 'custom' = 0;
  customInput = '';
  consoleOutput = '';
  loading = false;
  submitting = false;
  submittingContest = false;
  submitError = '';
  tcOutputs: Record<string, string> = {};
  tcVerdicts: Record<string, string> = {};
  submissions: any[] = [];
  leaderboard: any[] = [];
  runResult: any = null;

  constructor(private apiService: ApiService, private authService: AuthService) {}

  get rollNo(): string { return this.authService.rollNumber() || ''; }

  loadProblems(contestId: number, success: () => void, error: () => void): void {
    this.contestId = contestId;
    this.connectSse();
    this.apiService.getContestProblems(contestId).subscribe({
      next: (d) => {
        this.problems = d;
        this.consoleTab = d[this.activeQ]?.testCases?.length > 0 ? 0 : 'custom';
        this.loadSubmissions(contestId);
        this.loadLeaderboard(contestId);
        success();
      },
      error: () => error()
    });
  }

  loadSubmissions(contestId: number): void {
    this.apiService.getSubmissions(contestId).subscribe({
      next: (d) => this.submissions = d
    });
  }

  loadLeaderboard(contestId: number): void {
    this.apiService.getLeaderboard(contestId).subscribe({ next: (d) => this.leaderboard = d });
  }

  get problemSubmissions(): any[] { return this.submissions.filter((s: any) => s.problemId === this.problems[this.activeQ]?.problemId); }

  isSolved(problemId: number): boolean { return this.submissions.some((s: any) => s.problemId === problemId && s.verdict === 'ACCEPTED'); }

  updateConsoleOutput(): void {
    if (!this.runResult) return;
    if (this.runResult.status === 'Compilation Error') {
      this.consoleOutput = `Compilation Error:\n\n${this.runResult.output || ''}`;
      return;
    }
    if (this.consoleTab === 'custom') {
      this.consoleOutput = this.runResult.testCaseResults?.[this.runResult.testCaseResults.length - 1]?.stderr || '';
    } else {
      const tc = this.runResult.testCaseResults?.[this.consoleTab];
      if (!tc) {
        this.consoleOutput = 'No verdict.';
        return;
      }
      const v = this.tcVerdicts[this.consoleTab];
      this.consoleOutput = `Verdict: ${v}` + (v === 'Accepted' ? '' : (tc.stderr ? `\n\nError:\n${tc.stderr}` : `\n\nInput:\n${tc.stdin}\nExpected:\n${tc.expectedOutput}\nActual:\n${tc.actualOutput}`));
    }
  }

  runCode(contestId: number, code: string): void {
    if (this.loading || !this.problems[this.activeQ]) return;
    this.loading = true;
    this.consoleOutput = 'Executing code on server...';
    this.apiService.compileCode({
      contestId,
      questionId: this.problems[this.activeQ].problemId,
      language: this.selectedLang.toUpperCase(),
      code,
      customInput: this.customInput ? this.customInput.trim() : ''
    }).subscribe({
      next: (res) => {
        this.loading = false;
        this.runResult = res;
        if (res.status === 'Compilation Error') {
          this.consoleOutput = `Compilation Error:\n\n${res.output || ''}`;
          return;
        }
        res.testCaseResults?.forEach((tc: any, idx: number) => {
          const isCust = idx === this.problems[this.activeQ].testCases?.length;
          const k = isCust ? 'custom' : String(idx);
          this.tcOutputs[k] = tc.actualOutput || '';
          this.tcVerdicts[k] = isCust ? '' : getVerdictLabel(tc.verdict);
        });
        this.updateConsoleOutput();
      },
      error: (e) => {
        this.loading = false;
        this.consoleOutput = e.error?.error || 'Run failed';
      }
    });
  }

  submitCode(contestId: number, code: string): void {
    if (this.submitting || !this.problems[this.activeQ]) return;
    this.submitting = true;
    this.consoleOutput = 'Evaluating...';
    this.apiService.submitCode({
      contestId,
      questionId: this.problems[this.activeQ].problemId,
      rollNumber: this.authService.rollNumber() || '',
      language: this.selectedLang.toUpperCase(),
      code
    }).subscribe({
      next: (r) => {
        this.pendingSubmissionId = r.submissionId;
        console.log('Submission created, waiting for SSE verdict. ID:', r.submissionId);
      },
      error: (e) => {
        this.submitting = false;
        this.consoleOutput = e.error?.error || 'Failed';
      }
    });
  }

  connectSse(): void {
    if (this.sseSource) {
      this.sseSource.close();
      this.sseSource = null;
    }

    const token = localStorage.getItem('cpj_token');
    if (!token) return;

    const host = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
    this.sseSource = new EventSource(`http://${host}:8080/api/events?rollNo=${encodeURIComponent(token)}`);

    this.sseSource.addEventListener('verdict', (event: any) => {
      try {
        const data = JSON.parse(event.data);
        if (data.submissionId === this.pendingSubmissionId) {
          this.submitting = false;
          this.pendingSubmissionId = null;
          const vLabel = getVerdictLabel(data.verdict);
          this.consoleOutput = `Verdict: ${vLabel}\nPassed: ${data.passedCount != null ? data.passedCount : '—'}/${data.totalCount != null ? data.totalCount : '—'}\nTime: ${data.timeMs != null ? data.timeMs + 'ms' : '—'}\nMemory: ${data.memoryKb != null ? data.memoryKb + ' KB' : '—'}`;
          this.loadSubmissions(this.contestId);
          this.loadLeaderboard(this.contestId);
        }
      } catch (err) {
        console.error('Error parsing verdict SSE:', err);
      }
    });

    this.sseSource.addEventListener('leaderboard', (event: any) => {
      try {
        const data = JSON.parse(event.data);
        this.leaderboard = data;
      } catch (err) {
        console.error('Error parsing leaderboard SSE:', err);
      }
    });

    this.sseSource.onerror = (err) => {
      console.error('SSE connection error, closing...', err);
      this.sseSource?.close();
      this.sseSource = null;
      setTimeout(() => {
        if (this.contestId) {
          this.connectSse();
        }
      }, 5000);
    };
  }

  ngOnDestroy(): void {
    if (this.sseSource) {
      this.sseSource.close();
      this.sseSource = null;
    }
  }

  confirmSubmit(contestId: number, success: () => void): void {
    this.submitError = '';
    this.submittingContest = true;
    this.apiService.submitContest(contestId).subscribe({
      next: () => {
        this.submittingContest = false;
        success();
      },
      error: (e) => {
        this.submittingContest = false;
        this.submitError = e.error?.error || 'Failed';
      }
    });
  }
}
