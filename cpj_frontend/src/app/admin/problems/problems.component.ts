/*
 * File: problems.component.ts
 * Purpose: Invigilator problem repository manager. Supports problem creation and testcase (sample/hidden) uploads.
 */

import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'app-admin-problems',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './problems.component.html'
})
export class ProblemsComponent implements OnInit {
  problems: any[] = [];
  testCases: any[] = [];
  selectedProblemId: number | null = null;
  selectedProblemTitle = '';
  
  // Modals
  showAddProblem = false;
  showTestCases = false;

  // New Problem Form
  title = '';
  description = '';
  constraints = '';
  difficulty = 'EASY';
  inputStructure = '';
  outputStructure = '';

  // New TestCase Form
  tcInput = '';
  tcOutput = '';
  tcIsSample = false;

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.loadProblems();
  }

  loadProblems(): void {
    this.apiService.getAdminProblems().subscribe({
      next: (data) => this.problems = data
    });
  }

  saveProblem(): void {
    if (!this.title || !this.description) return;
    this.apiService.createProblem({
      title: this.title,
      description: this.description,
      constraints: this.constraints,
      difficulty: this.difficulty,
      inputStructure: this.inputStructure,
      outputStructure: this.outputStructure
    }).subscribe({
      next: () => {
        this.loadProblems();
        this.showAddProblem = false;
        this.resetProblemForm();
      },
      error: (err) => alert(err.error?.error || 'Failed to create problem')
    });
  }

  viewTestCases(problemId: number, title: string): void {
    this.selectedProblemId = problemId;
    this.selectedProblemTitle = title;
    this.apiService.getTestCases(problemId).subscribe({
      next: (data) => {
        this.testCases = data;
        this.showTestCases = true;
      },
      error: () => alert('Failed to retrieve test cases')
    });
  }

  addTestCase(): void {
    if (this.selectedProblemId === null) return;
    this.apiService.createTestCase(this.selectedProblemId, {
      stdin: this.tcInput,
      expectedOutput: this.tcOutput,
      isSample: this.tcIsSample
    }).subscribe({
      next: () => {
        this.viewTestCases(this.selectedProblemId!, this.selectedProblemTitle);
        this.tcInput = '';
        this.tcOutput = '';
        this.tcIsSample = false;
      },
      error: (err) => alert(err.error?.error || 'Failed to add testcase')
    });
  }

  deleteTestCase(id: number): void {
    if (confirm('Delete this testcase?')) {
      this.apiService.deleteTestCase(id).subscribe({
        next: () => this.viewTestCases(this.selectedProblemId!, this.selectedProblemTitle),
        error: (err) => alert(err.error?.error || 'Failed to delete testcase')
      });
    }
  }

  private resetProblemForm(): void {
    this.title = '';
    this.description = '';
    this.constraints = '';
    this.difficulty = 'EASY';
    this.inputStructure = '';
    this.outputStructure = '';
  }
}
