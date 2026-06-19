/*
 * File: contest-arena.component.ts
 * Purpose: Student coding workspace. Manages code editor (Monaco) and UI layouts.
 */
import { Component, OnInit, AfterViewInit, HostListener } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common'; import { FormsModule } from '@angular/forms';
import { ContestArenaStateService } from './contest-arena-state.service';
import { ApiService } from '../../core/api.service';
import { getSavedCode, saveCode, getMonacoLanguage } from './contest-arena.helper';

@Component({
  selector: 'app-student-contest-arena', standalone: true,
  imports: [CommonModule, FormsModule, RouterLink], providers: [ContestArenaStateService],
  templateUrl: './contest-arena.component.html', styleUrl: './contest-arena.component.css'
})
export class ContestArenaComponent implements OnInit, AfterViewInit {
  showSubmitModal = false; showLeaderboardModal = false; leftTab: 'description' | 'submissions' = 'description';
  isEditorExpanded = false; private editor: any; hasStarted = false; violations = 0;
  isLocked = false; showWarningModal = false; warningReason = ''; monacoLoaded = false;

  constructor(private route: ActivatedRoute, private router: Router, public state: ContestArenaStateService, private apiService: ApiService) {}

  ngOnInit(): void {
    const s = this.route.snapshot;
    this.state.contestId = Number(s.paramMap.get('id'));
    this.state.activeQ = Number(s.queryParamMap.get('problem')) || 0;
    this.state.selectedLang = s.queryParamMap.get('lang') || 'java';
    this.state.loadProblems(this.state.contestId, () => {
      this.tryInitEditor(); this.loadSavedCode(); this.checkInitialViolations();
    }, () => this.router.navigate(['/student/dashboard']));
  }

  ngAfterViewInit(): void {
    if ((window as any).monaco) { this.monacoLoaded = true; this.tryInitEditor(); return; }
    const s = document.createElement('script');
    s.src = 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs/loader.js';
    s.onload = () => {
      const w = window as any;
      w.require.config({ paths: { vs: 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs' } });
      w.require(['vs/editor/editor.main'], () => { this.monacoLoaded = true; this.tryInitEditor(); });
    };
    document.body.appendChild(s);
  }

  tryInitEditor(): void {
    if (this.monacoLoaded && this.hasStarted && !this.editor && this.state.problems?.length > 0 && document.getElementById('editor-container')) this.initMonaco();
  }

  initMonaco(): void {
    const prob = this.state.problems[this.state.activeQ];
    const code = getSavedCode(this.state.contestId, prob?.problemId, this.state.selectedLang, prob);
    this.editor = (window as any).monaco.editor.create(document.getElementById('editor-container'), {
      value: code, language: getMonacoLanguage(this.state.selectedLang),
      theme: 'vs-dark', automaticLayout: true, fontSize: 14, minimap: { enabled: false }
    });
    this.editor.onDidChangeModelContent(() => {
      if (this.editor && prob) saveCode(this.state.contestId, prob.problemId, this.state.selectedLang, this.editor.getValue());
    });
  }

  loadSavedCode(): void {
    const prob = this.state.problems[this.state.activeQ];
    if (!prob || !this.editor) return;
    this.editor.setValue(getSavedCode(this.state.contestId, prob.problemId, this.state.selectedLang, prob));
    (window as any).monaco.editor.setModelLanguage(this.editor.getModel(), getMonacoLanguage(this.state.selectedLang));
  }

  onLangChange(lang: string): void { this.state.selectedLang = lang; this.loadSavedCode(); this.updateRoute(); }
  private triggerLayout(cb?: () => void): void {
    [50, 150, 300].forEach(t => setTimeout(() => { this.editor?.layout(); if (t === 50 && cb) cb(); }, t));
  }

  toggleEditorExpand(): void { this.isEditorExpanded = !this.isEditorExpanded; this.triggerLayout(); }
  runAndCollapse(): void { this.isEditorExpanded = false; this.triggerLayout(() => this.runCode()); }
  submitAndCollapse(): void { this.isEditorExpanded = false; this.triggerLayout(() => this.submitCode()); }
  selectTab(tab: number | 'custom'): void { this.state.consoleTab = tab; this.state.updateConsoleOutput(); }

  updateRoute(): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { problem: this.state.activeQ, lang: this.state.selectedLang }, queryParamsHandling: 'merge' });
  }

  onProblemChange(idx: number): void {
    this.state.activeQ = idx; this.loadSavedCode();
    this.state.consoleTab = this.state.problems[idx]?.testCases?.length > 0 ? 0 : 'custom';
    this.state.tcOutputs = {}; this.state.tcVerdicts = {}; this.state.runResult = null; this.state.consoleOutput = '';
    this.updateRoute();
  }

  useCode(code: string, langId: number): void {
    const lang = { 62: 'java', 54: 'cpp', 71: 'python' }[langId] || 'java';
    this.state.selectedLang = lang;
    if (this.editor) { this.editor.setValue(code); (window as any).monaco.editor.setModelLanguage(this.editor.getModel(), getMonacoLanguage(lang)); }
    this.updateRoute();
  }

  runCode(): void { this.state.runCode(this.state.contestId, this.editor?.getValue() || ''); }
  submitCode(): void { this.state.submitCode(this.state.contestId, this.editor?.getValue() || ''); }
  confirmSubmit(): void { this.state.confirmSubmit(this.state.contestId, () => this.router.navigate(['/student/dashboard'])); }

  checkInitialViolations(): void {
    this.apiService.getLeaderboard(this.state.contestId).subscribe((rows: any) => {
      const me = rows.find((r: any) => r.rollNo === this.state.rollNo);
      if (me) { this.violations = me.violations || 0; this.isLocked = this.violations >= 3; }
    });
  }

  get isCurrentUserLocked(): boolean {
    const me = this.state.leaderboard.find(r => r.rollNo === this.state.rollNo);
    if (me && me.violations < 3 && this.isLocked) { this.isLocked = false; this.violations = me.violations; this.hasStarted = false; this.state.submittingContest = false; }
    return this.isLocked;
  }

  enterFullscreen(): void {
    const el = document.documentElement as any;
    const rfs = el.requestFullscreen || el.webkitRequestFullscreen || el.mozRequestFullScreen;
    if (rfs) rfs.call(el).then(() => { this.hasStarted = true; this.showWarningModal = false; setTimeout(() => this.tryInitEditor(), 50); }).catch(() => alert('Enable fullscreen.'));
  }

  triggerViolation(reason: string): void {
    if (this.isLocked || this.showWarningModal) return;
    this.violations++; this.warningReason = reason;
    this.apiService.recordStudentViolation(this.state.contestId, this.state.rollNo, this.violations).subscribe(() => {
      if (this.violations >= 3) {
        this.isLocked = true; this.showWarningModal = false;
        this.state.confirmSubmit(this.state.contestId, () => {});
      } else this.showWarningModal = true;
    });
  }

  private shouldViolate(): boolean { return this.hasStarted && !this.isLocked && !this.showWarningModal && !this.state.submittingContest; }

  @HostListener('document:visibilitychange')
  onVis(): void { if (this.shouldViolate() && document.hidden) this.triggerViolation('Tab switched or window minimized'); }

  @HostListener('window:blur')
  onBlur(): void { if (this.shouldViolate()) setTimeout(() => { if (!document.hasFocus()) this.triggerViolation('Window lost focus'); }, 300); }

  @HostListener('document:fullscreenchange')
  onFs(): void {
    if (this.shouldViolate() && !(document.fullscreenElement || (document as any).webkitFullscreenElement)) this.triggerViolation('Exited fullscreen mode');
  }

  getStructureLines(val: string): string[] {
    if (!val) return [];
    try {
      const parsed = JSON.parse(val);
      if (Array.isArray(parsed)) return parsed.map(item => item.trim()).filter(Boolean);
    } catch (e) {}
    return [val.trim()];
  }

  isSingleLine(val: string): boolean {
    return this.getStructureLines(val).length <= 1;
  }

  getSingleLine(val: string): string {
    const lines = this.getStructureLines(val);
    return lines.length > 0 ? lines[0] : '';
  }
}
