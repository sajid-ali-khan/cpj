/*
 * File: contest-arena.component.ts
 * Purpose: Student coding workspace. Manages code editor (Monaco) and UI layouts.
 */

import { Component, OnInit, AfterViewInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common'; import { FormsModule } from '@angular/forms';
import { ContestArenaStateService } from './contest-arena-state.service';
import { getSavedCode, saveCode, getMonacoLanguage } from './contest-arena.helper';

@Component({
  selector: 'app-student-contest-arena',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  providers: [ContestArenaStateService],
  templateUrl: './contest-arena.component.html',
  styleUrl: './contest-arena.component.css'
})
export class ContestArenaComponent implements OnInit, AfterViewInit {
  showSubmitModal = false;
  showLeaderboardModal = false;
  leftTab: 'description' | 'submissions' = 'description';
  isEditorExpanded = false;
  private editor: any;

  constructor(private route: ActivatedRoute, private router: Router, public state: ContestArenaStateService) {}

  ngOnInit(): void {
    this.state.contestId = Number(this.route.snapshot.paramMap.get('id'));
    this.state.activeQ = Number(this.route.snapshot.queryParamMap.get('problem')) || 0;
    this.state.selectedLang = this.route.snapshot.queryParamMap.get('lang') || 'java';
    this.state.loadProblems(this.state.contestId, () => this.loadSavedCode(), () => this.router.navigate(['/student/dashboard']));
  }

  ngAfterViewInit(): void {
    if ((window as any).monaco) return this.initMonaco();
    const s = document.createElement('script');
    s.src = 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs/loader.js';
    s.onload = () => {
      const w = window as any;
      w.require.config({ paths: { vs: 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs' } });
      w.require(['vs/editor/editor.main'], () => this.initMonaco());
    };
    document.body.appendChild(s);
  }

  initMonaco(): void {
    const prob = this.state.problems[this.state.activeQ];
    const code = getSavedCode(this.state.contestId, prob?.problemId, this.state.selectedLang, prob);
    this.editor = (window as any).monaco.editor.create(document.getElementById('editor-container'), {
      value: code,
      language: getMonacoLanguage(this.state.selectedLang),
      theme: 'vs-dark', automaticLayout: true, fontSize: 14, minimap: { enabled: false }
    });
    this.editor.onDidChangeModelContent(() => {
      if (this.editor && prob) saveCode(this.state.contestId, prob.problemId, this.state.selectedLang, this.editor.getValue());
    });
  }

  loadSavedCode(): void {
    const prob = this.state.problems[this.state.activeQ];
    if (!prob || !this.editor) return;
    const code = getSavedCode(this.state.contestId, prob.problemId, this.state.selectedLang, prob);
    this.editor.setValue(code);
    (window as any).monaco.editor.setModelLanguage(this.editor.getModel(), getMonacoLanguage(this.state.selectedLang));
  }

  onLangChange(lang: string): void {
    this.state.selectedLang = lang;
    this.loadSavedCode();
    this.updateRoute();
  }

  private triggerLayout(cb?: () => void): void {
    [50, 150, 300].forEach((t) => {
      setTimeout(() => {
        this.editor?.layout();
        if (t === 50 && cb) cb();
      }, t);
    });
  }

  toggleEditorExpand(): void { this.isEditorExpanded = !this.isEditorExpanded; this.triggerLayout(); }
  runAndCollapse(): void { this.isEditorExpanded = false; this.triggerLayout(() => this.runCode()); }
  submitAndCollapse(): void { this.isEditorExpanded = false; this.triggerLayout(() => this.submitCode()); }
  selectTab(tab: number | 'custom'): void { this.state.consoleTab = tab; this.state.updateConsoleOutput(); }

  updateRoute(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { problem: this.state.activeQ, lang: this.state.selectedLang },
      queryParamsHandling: 'merge'
    });
  }

  onProblemChange(idx: number): void {
    this.state.activeQ = idx;
    this.loadSavedCode();
    this.state.consoleTab = this.state.problems[idx]?.testCases?.length > 0 ? 0 : 'custom';
    this.state.tcOutputs = {}; this.state.tcVerdicts = {}; this.state.runResult = null; this.state.consoleOutput = '';
    this.updateRoute();
  }

  useCode(code: string, langId: number): void {
    const lang = { 62: 'java', 54: 'cpp', 71: 'python' }[langId] || 'java';
    this.state.selectedLang = lang;
    if (this.editor) {
      this.editor.setValue(code);
      (window as any).monaco.editor.setModelLanguage(this.editor.getModel(), getMonacoLanguage(lang));
    }
    this.updateRoute();
  }

  runCode(): void { this.state.runCode(this.state.contestId, this.editor?.getValue() || ''); }
  submitCode(): void { this.state.submitCode(this.state.contestId, this.editor?.getValue() || ''); }

  confirmSubmit(): void {
    this.state.confirmSubmit(this.state.contestId, () => {
      this.router.navigate(['/student/dashboard']);
    });
  }
}
