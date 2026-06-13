/*
 * File: contests.component.ts
 * Purpose: Invigilator contest manager. Handles contest scheduling and maps questions with custom weights and display orders.
 */

import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'app-admin-contests',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './contests.component.html'
})
export class ContestsComponent implements OnInit {
  contests: any[] = [];
  problems: any[] = [];
  showAddModal = false;

  // New Contest Form
  title = '';
  description = '';
  startTime = '';
  durationMins = 120;
  selectedProblemIds: number[] = [];
  problemPoints: Record<number, number> = {};
  problemOrder: Record<number, number> = {};

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.apiService.getAdminContests().subscribe({
      next: (data) => this.contests = data
    });

    this.apiService.getAdminProblems().subscribe({
      next: (data) => this.problems = data
    });
  }

  toggleProblem(problemId: number): void {
    const idx = this.selectedProblemIds.indexOf(problemId);
    if (idx > -1) {
      this.selectedProblemIds.splice(idx, 1);
      delete this.problemPoints[problemId];
      delete this.problemOrder[problemId];
    } else {
      this.selectedProblemIds.push(problemId);
      this.problemPoints[problemId] = 100;
      this.problemOrder[problemId] = this.selectedProblemIds.length;
    }
  }

  getProblemTitle(id: number): string {
    const p = this.problems.find(item => item.id === id);
    return p ? p.title : '';
  }

  saveContest(): void {
    if (!this.title || !this.startTime || this.selectedProblemIds.length === 0) {
      alert('Please fill all required fields and select at least one problem.');
      return;
    }

    const contestProblems = this.selectedProblemIds.map((id) => ({
      problemId: id,
      points: Number(this.problemPoints[id] || 100),
      displayOrder: Number(this.problemOrder[id] || 1)
    }));

    const body = {
      title: this.title.trim(),
      description: this.description.trim(),
      startTime: new Date(this.startTime).toISOString(),
      durationMins: this.durationMins,
      problems: contestProblems
    };

    this.apiService.createContest(body).subscribe({
      next: () => {
        this.loadData();
        this.showAddModal = false;
        this.resetForm();
        alert('Contest created successfully.');
      },
      error: (err) => alert(err.error?.error || 'Failed to create contest')
    });
  }

  private resetForm(): void {
    this.title = '';
    this.description = '';
    this.startTime = '';
    this.durationMins = 120;
    this.selectedProblemIds = [];
    this.problemPoints = {};
    this.problemOrder = {};
  }
}
