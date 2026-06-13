/*
 * File: students.component.ts
 * Purpose: Invigilator student directory panel. Provides search, single student additions, edits, deletions, and CSV bulk importing.
 */

import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'app-admin-students',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './students.component.html',
  styleUrl: './students.component.css'
})
export class StudentsComponent implements OnInit {
  students: any[] = [];
  filteredStudents: any[] = [];
  searchQuery = '';
  showAddModal = false;
  showImportModal = false;
  showEditModal = false;

  // Single User Forms
  newName = '';
  newRoll = '';
  newBranch = 'CSE';
  editId: number | null = null;
  editName = '';
  editRoll = '';
  editBranch = '';

  // Import Progress
  importing = false;
  importProgress = '';
  private importList: any[] = [];

  constructor(private apiService: ApiService) {}

  ngOnInit(): void { this.loadStudents(); }

  loadStudents(): void {
    this.apiService.getAdminUsers().subscribe({
      next: (data) => {
        const raw = data.content || data || [];
        const studentUsers = raw.filter((u: any) => u.role === 'STUDENT');
        this.students = studentUsers.map((u: any) => ({
          id: u.id,
          name: u.name,
          rollNumber: u.rollNo,
          branch: u.branch
        }));
        this.filter();
      }
    });
  }

  filter(): void {
    const q = this.searchQuery.toLowerCase().trim();
    this.filteredStudents = this.students.filter(s =>
      s.name.toLowerCase().includes(q) || s.rollNumber.toLowerCase().includes(q)
    );
  }

  addStudent(): void {
    if (!this.newName || !this.newRoll) return;
    this.apiService.createStudent({ name: this.newName, rollNo: this.newRoll.toUpperCase(), branch: this.newBranch.toUpperCase(), role: 'STUDENT' }).subscribe({
      next: () => { this.loadStudents(); this.showAddModal = false; this.newName = ''; this.newRoll = ''; },
      error: (err) => alert(err.error?.error || 'Failed to create student')
    });
  }

  openEditModal(student: any): void {
    this.editId = student.id;
    this.editName = student.name;
    this.editRoll = student.rollNumber;
    this.editBranch = student.branch;
    this.showEditModal = true;
  }

  saveEdit(): void {
    if (!this.editId || !this.editName || !this.editBranch) return;
    this.apiService.updateStudent(this.editId, { name: this.editName, rollNo: this.editRoll, branch: this.editBranch.toUpperCase(), role: 'STUDENT' }).subscribe({
      next: () => { this.loadStudents(); this.showEditModal = false; this.editId = null; },
      error: (err) => alert(err.error?.error || 'Failed to update student')
    });
  }

  deleteStudent(id: number): void {
    if (!confirm('Are you sure you want to delete this student?')) return;
    this.apiService.deleteStudent(id).subscribe({
      next: () => this.loadStudents(),
      error: (err) => alert(err.error?.error || 'Failed to delete student')
    });
  }

  onFileSelected(event: any): void {
    const file: File = event.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (e) => this.parseCSV(e.target?.result as string);
    reader.readAsText(file);
  }

  parseCSV(text: string): void {
    const lines = text.split('\n');
    this.importList = [];
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;
      const parts = line.split(',');
      if (parts.length >= 2) this.importList.push({ name: parts[0].trim(), rollNo: parts[1].trim().toUpperCase(), branch: (parts[2] || 'CSE').trim().toUpperCase(), role: 'STUDENT' });
    }
    this.importProgress = `Loaded ${this.importList.length} students from file. Ready to import.`;
  }

  runImport(): void {
    if (this.importList.length === 0) return;
    this.importing = true;
    this.importNext(0);
  }

  private importNext(idx: number): void {
    if (idx >= this.importList.length) {
      this.importProgress = `Import complete. ${this.importList.length} students loaded.`;
      this.importing = false; this.importList = []; this.loadStudents();
      return;
    }
    this.importProgress = `Importing (${idx + 1}/${this.importList.length}): ${this.importList[idx].name}`;
    this.apiService.createStudent(this.importList[idx]).subscribe({ next: () => this.importNext(idx + 1), error: () => this.importNext(idx + 1) });
  }
}
