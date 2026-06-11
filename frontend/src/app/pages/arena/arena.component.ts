import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ContestProblemSummary, ContestSummary } from '../../models/admin.models';
import {
  LANGUAGES,
  LeaderboardEntry,
  SubmissionResponse,
  Verdict,
  VerdictEvent,
} from '../../models/api.models';
import { ApiService } from '../../services/api.service';
import { SessionService } from '../../services/session.service';
import { SseConnectionState, SseService } from '../../services/sse.service';

export interface Toast {
  id: number;
  verdict: Verdict;
  problemTitle: string;
}

@Component({
  selector: 'app-arena',
  imports: [CommonModule, FormsModule],
  templateUrl: './arena.component.html',
  styleUrl: './arena.component.css',
})
export class ArenaComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  readonly session = inject(SessionService);
  private readonly sse = inject(SseService);

  readonly languages = LANGUAGES;

  rollNoInput = '';
  contest = signal<ContestSummary | null>(null);
  problems = signal<ContestProblemSummary[]>([]);
  selectedProblemId: number | null = null;
  languageId = 71;
  code = '';

  readonly submissions = signal<SubmissionResponse[]>([]);
  readonly leaderboard = signal<LeaderboardEntry[]>([]);
  readonly submitting = signal(false);
  readonly joining = signal(false);
  readonly error = signal('');

  // Contest Archive properties
  readonly archiveContests = signal<ContestSummary[]>([]);
  readonly selectedArchiveContest = signal<ContestSummary | null>(null);
  readonly archiveLeaderboard = signal<LeaderboardEntry[]>([]);
  readonly showArchiveModal = signal(false);
  readonly loadingArchiveLeaderboard = signal(false);

  // Toast notifications
  readonly toasts = signal<Toast[]>([]);
  private toastCounter = 0;

  // Pending verdict badge: count of verdicts received while not on submissions tab
  readonly pendingVerdictCount = signal(0);

  activeTab = signal<'problem' | 'submissions' | 'leaderboard'>('problem');
  expandedSubmissionId = signal<number | null>(null);

  setTab(tab: 'problem' | 'submissions' | 'leaderboard'): void {
    this.activeTab.set(tab);
    if (tab === 'submissions') {
      this.pendingVerdictCount.set(0);
      this.refreshSubmissions();
    } else if (tab === 'leaderboard') {
      this.refreshLeaderboard();
    }
  }

  getProblemTitle(problemId: number): string {
    const p = this.problems().find((prob) => prob.problemId === problemId);
    return p ? p.title : `Problem ${problemId}`;
  }

  getLanguageLabel(languageId: number): string {
    const lang = this.languages.find((l) => l.id === languageId);
    return lang ? lang.label : `Lang ${languageId}`;
  }

  viewCode(row: SubmissionResponse): void {
    if (this.expandedSubmissionId() === row.id) {
      this.expandedSubmissionId.set(null);
    } else {
      this.expandedSubmissionId.set(row.id);
    }
  }

  loadIntoEditor(code: string, problemId: number, languageId: number): void {
    this.code = code;
    this.selectedProblemId = problemId;
    this.languageId = languageId;
    this.activeTab.set('problem');
  }
  readonly connectionState = this.sse.connectionState;
  readonly checkingContest = signal(true);
  readonly contestExists = signal<boolean | null>(null);

  private subs = new Subscription();
  private errorDismissTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.rollNoInput = this.session.rollNo();
    if (this.session.isConnected()) {
      this.loadContestAndSession();
    } else {
      // Check if a contest exists before showing login form
      this.checkContestAvailability();
    }

    // Load contests for the archive
    this.api.listContests().subscribe({
      next: (list) => this.archiveContests.set(list),
      error: (err) => console.error('Failed to load archive contests:', err),
    });

    this.subs.add(
      this.sse.verdict$.subscribe((event) => {
        this.applyVerdictInPlace(event);
        this.showVerdictToast(event);
        // Increment badge if the user is not on submissions tab
        if (this.activeTab() !== 'submissions') {
          this.pendingVerdictCount.update((n) => n + 1);
        }
        this.refreshLeaderboard();
      }),
    );
    this.subs.add(this.sse.leaderboard$.subscribe((entries) => this.leaderboard.set(entries)));
    this.subs.add(
      this.sse.contestEvent$.subscribe((event) => {
        if (event.status === 'ENDED') {
          this.showError('Contest has ended.');
        }
      }),
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.sse.disconnect();
    if (this.errorDismissTimer) clearTimeout(this.errorDismissTimer);
  }

  // ─── Error with auto-dismiss ────────────────────────────────────────────────

  showError(msg: string): void {
    this.error.set(msg);
    if (this.errorDismissTimer) clearTimeout(this.errorDismissTimer);
    this.errorDismissTimer = setTimeout(() => this.error.set(''), 4000);
  }

  dismissError(): void {
    this.error.set('');
    if (this.errorDismissTimer) clearTimeout(this.errorDismissTimer);
  }

  // ─── Toast ──────────────────────────────────────────────────────────────────

  private showVerdictToast(event: VerdictEvent): void {
    const id = ++this.toastCounter;
    const problemTitle = this.getProblemTitle(event.problemId);
    const toast: Toast = { id, verdict: event.verdict, problemTitle };
    this.toasts.update((t) => [...t, toast]);
    setTimeout(() => {
      this.toasts.update((t) => t.filter((x) => x.id !== id));
    }, 4000);
  }

  dismissToast(id: number): void {
    this.toasts.update((t) => t.filter((x) => x.id !== id));
  }

  // ─── In-place submission update from SSE ────────────────────────────────────

  private applyVerdictInPlace(event: VerdictEvent): void {
    this.submissions.update((rows) =>
      rows.map((row) =>
        row.id === event.submissionId
          ? { ...row, verdict: event.verdict, timeMs: event.timeMs, memoryKb: event.memoryKb }
          : row,
      ),
    );
  }

  // ─── Join form ──────────────────────────────────────────────────────────────

  connect(): void {
    this.dismissError();
    if (!this.rollNoInput.trim()) {
      this.showError('Roll number is required.');
      return;
    }
    if (this.joining()) return; // guard double-click
    this.loadContestAndSession(this.rollNoInput.trim());
  }

  private checkContestAvailability(): void {
    this.checkingContest.set(true);
    this.api.getCurrentContest().subscribe({
      next: () => {
        this.contestExists.set(true);
        this.checkingContest.set(false);
      },
      error: () => {
        this.contestExists.set(false);
        this.checkingContest.set(false);
        this.showError('No ongoing contest. Ask your instructor to start one.');
      },
    });
  }

  disconnect(): void {
    this.sse.disconnect();
    this.session.disconnect();
    this.contest.set(null);
    this.problems.set([]);
    this.submissions.set([]);
    this.leaderboard.set([]);
    this.rollNoInput = '';
    this.selectedProblemId = null;
    this.pendingVerdictCount.set(0);
    this.toasts.set([]);
  }

  submit(): void {
    const contestId = this.contest()?.id;
    if (!contestId || !this.selectedProblemId || !this.code.trim()) {
      return;
    }

    this.submitting.set(true);
    this.dismissError();

    this.api
      .submit({
        contestId,
        problemId: this.selectedProblemId,
        code: this.code,
        languageId: this.languageId,
      })
      .subscribe({
        next: (res) => {
          this.submitting.set(false);
          // Optimistically add a PENDING row so the user sees immediate feedback
          const optimistic: SubmissionResponse = {
            id: res.submissionId,
            problemId: this.selectedProblemId!,
            languageId: this.languageId,
            code: this.code,
            verdict: 'PENDING',
            timeMs: null,
            memoryKb: null,
            submittedAt: new Date().toISOString(),
          };
          this.submissions.update((rows) => [optimistic, ...rows]);
        },
        error: (err) => {
          this.submitting.set(false);
          this.showError(err?.error?.error ?? 'Submission failed.');
        },
      });
  }

  selectedProblem(): ContestProblemSummary | undefined {
    return this.problems().find((p) => p.problemId === this.selectedProblemId);
  }

  connectionLabel(state: SseConnectionState): string {
    switch (state) {
      case 'connected':
        return 'Live';
      case 'connecting':
        return 'Connecting…';
      case 'error':
        return 'Reconnecting…';
      default:
        return 'Offline';
    }
  }

  verdictClass(verdict: Verdict): string {
    switch (verdict) {
      case 'ACCEPTED':
        return 'verdict-accepted';
      case 'PENDING':
        return 'verdict-pending';
      case 'WRONG_ANSWER':
        return 'verdict-wa';
      default:
        return 'verdict-error';
    }
  }

  toastClass(verdict: Verdict): string {
    switch (verdict) {
      case 'ACCEPTED':
        return 'toast-accepted';
      case 'WRONG_ANSWER':
        return 'toast-wa';
      case 'PENDING':
        return 'toast-pending';
      default:
        return 'toast-error';
    }
  }

  toastIcon(verdict: Verdict): string {
    switch (verdict) {
      case 'ACCEPTED':
        return '✓';
      case 'WRONG_ANSWER':
        return '✗';
      case 'PENDING':
        return '…';
      default:
        return '!';
    }
  }

  formatTime(iso: string): string {
    return new Date(iso).toLocaleTimeString();
  }

  private loadContestAndSession(rollNo?: string): void {
    const resolvedRollNo = rollNo ?? this.session.rollNo();
    if (!resolvedRollNo) {
      this.checkingContest.set(false);
      return;
    }

    this.joining.set(true);

    this.api.getCurrentContest().subscribe({
      next: (contest) => {
        this.contest.set(contest);
        this.session.connect(resolvedRollNo, contest.id);
        this.sse.connect(resolvedRollNo);
        this.loadProblems(contest.id);
        this.refreshSubmissions();
        this.refreshLeaderboard();
        this.contestExists.set(true);
        this.checkingContest.set(false);
        this.joining.set(false);
      },
      error: (err) => {
        this.showError(err?.error?.error ?? 'No ongoing contest. Ask your instructor to start one.');
        this.sse.disconnect();
        this.contestExists.set(false);
        this.checkingContest.set(false);
        this.joining.set(false);
      },
    });
  }

  private loadProblems(contestId: number): void {
    this.api.getContestProblems(contestId).subscribe({
      next: (problems) => {
        this.problems.set(problems);
        if (problems.length > 0 && !this.selectedProblemId) {
          this.selectedProblemId = problems[0].problemId;
        }
      },
      error: (err) => this.showError(err?.error?.error ?? 'Failed to load problems.'),
    });
  }

  refreshSubmissions(): void {
    const contestId = this.contest()?.id;
    if (!contestId) {
      return;
    }
    this.api.getSubmissions(contestId).subscribe({
      next: (rows) => this.submissions.set(rows),
    });
  }

  refreshLeaderboard(): void {
    const contestId = this.contest()?.id;
    if (!contestId) {
      return;
    }
    this.api.getLeaderboard(contestId).subscribe({
      next: (rows) => this.leaderboard.set(rows),
    });
  }

  viewArchiveLeaderboard(contest: ContestSummary): void {
    this.selectedArchiveContest.set(contest);
    this.showArchiveModal.set(true);
    this.loadingArchiveLeaderboard.set(true);
    this.archiveLeaderboard.set([]);

    this.api.getLeaderboard(contest.id).subscribe({
      next: (entries) => {
        this.archiveLeaderboard.set(entries);
        this.loadingArchiveLeaderboard.set(false);
      },
      error: (err) => {
        console.error('Failed to load archive leaderboard:', err);
        this.loadingArchiveLeaderboard.set(false);
      },
    });
  }

  closeArchiveModal(): void {
    this.showArchiveModal.set(false);
    this.selectedArchiveContest.set(null);
    this.archiveLeaderboard.set([]);
  }

  formatDateTime(iso: string): string {
    if (!iso) return '—';
    const d = new Date(iso);
    return d.toLocaleString(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  }

  formatDuration(mins: number): string {
    if (mins < 60) return `${mins} mins`;
    const hrs = Math.floor(mins / 60);
    const remaining = mins % 60;
    return remaining > 0 ? `${hrs}h ${remaining}m` : `${hrs} hrs`;
  }
}
