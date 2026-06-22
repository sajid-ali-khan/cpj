/*
 * File: api.service.ts
 * Purpose: Handles all REST API requests to the cpj backend.
 */
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private baseUrl: string;

  constructor(private http: HttpClient) {
    const host = typeof window !== 'undefined' ? window.location.hostname : '192.168.6.2';
    this.baseUrl = `http://${host}:8080/api`;
  }

  private getHeaders(isMultipart = false): HttpHeaders {
    const token = localStorage.getItem('cpj_token');
    let h = new HttpHeaders();
    if (!isMultipart) h = h.set('Content-Type', 'application/json');
    return token ? h.set('X-Roll-No', token) : h;
  }

  sendOtp(rollNumber: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/student/login/send-otp`, { rollNumber }, { headers: this.getHeaders() });
  }
  verifyOtp(rollNumber: string, otp: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/student/login/verify-otp`, { rollNumber, otp }, { headers: this.getHeaders() });
  }
  loginAdmin(username: string, password: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/login`, { username, password }, { headers: this.getHeaders() });
  }
  getContests(page = 0, size = 10): Observable<any> {
    return this.http.get(`${this.baseUrl}/contests?page=${page}&size=${size}`, { headers: this.getHeaders() });
  }
  getCurrentContest(): Observable<any> {
    return this.http.get(`${this.baseUrl}/contests/current`, { headers: this.getHeaders() });
  }
  getContestProblems(contestId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/contests/${contestId}/problems`, { headers: this.getHeaders() });
  }
  registerForContest(contestId: number): Observable<any> {
    return this.http.post(`${this.baseUrl}/contests/${contestId}/register`, {}, { headers: this.getHeaders() });
  }
  submitContest(contestId: number): Observable<any> {
    return this.http.post(`${this.baseUrl}/contests/${contestId}/submit`, {}, { headers: this.getHeaders() });
  }
  getStudentRegistrations(rollNumber: string): Observable<any> {
    return this.http.get(`${this.baseUrl}/students/${rollNumber}/registrations`, { headers: this.getHeaders() });
  }
  compileCode(body: any, custom = false): Observable<any> {
    return this.http.post(`${this.baseUrl}/compile?custom=${custom}`, body, { headers: this.getHeaders() });
  }
  submitCode(body: any): Observable<any> {
    return this.http.post(`${this.baseUrl}/submit`, body, { headers: this.getHeaders() });
  }
  getSubmissions(contestId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/submissions?contestId=${contestId}`, { headers: this.getHeaders() });
  }
  getLeaderboard(contestId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/contests/${contestId}/leaderboard`, { headers: this.getHeaders() });
  }
  getAdminContests(): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/contests`, { headers: this.getHeaders() });
  }
  createContest(body: any): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/contests`, body, { headers: this.getHeaders() });
  }
  updateContest(id: number, body: any): Observable<any> {
    return this.http.put(`${this.baseUrl}/admin/contests/${id}`, body, { headers: this.getHeaders() });
  }
  endContest(id: number): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/contests/${id}/end`, {}, { headers: this.getHeaders() });
  }
  deleteContest(id: number): Observable<any> {
    return this.http.delete(`${this.baseUrl}/admin/contests/${id}`, { headers: this.getHeaders() });
  }
  getAdminContestSubmissions(contestId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/contests/${contestId}/submissions`, { headers: this.getHeaders() });
  }
  registerStudentsBulk(contestId: number, rollNumbers: string[]): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/contests/${contestId}/registrations`, rollNumbers, { headers: this.getHeaders() });
  }
  getAdminContestRegistrations(contestId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/contests/${contestId}/registrations`, { headers: this.getHeaders() });
  }
  deleteStudentRegistration(contestId: number, userId: number): Observable<any> {
    return this.http.delete(`${this.baseUrl}/admin/contests/${contestId}/registrations/${userId}`, { headers: this.getHeaders() });
  }
  getAdminProblems(): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/problems`, { headers: this.getHeaders() });
  }
  createProblem(body: any): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/problems`, body, { headers: this.getHeaders() });
  }
  updateProblem(id: number, body: any): Observable<any> {
    return this.http.put(`${this.baseUrl}/admin/problems/${id}`, body, { headers: this.getHeaders() });
  }
  createTestCase(problemId: number, body: any): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/problems/${problemId}/test-cases`, body, { headers: this.getHeaders() });
  }

  getTestCases(problemId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/problems/${problemId}/test-cases`, { headers: this.getHeaders() });
  }
  deleteTestCase(id: number): Observable<any> {
    return this.http.delete(`${this.baseUrl}/admin/test-cases/${id}`, { headers: this.getHeaders() });
  }
  getAdminUsers(query?: string, page = 0, size = 10000): Observable<any> {
    let url = `${this.baseUrl}/admin/users?page=${page}&size=${size}`;
    if (query) url += `&query=${encodeURIComponent(query)}`;
    return this.http.get(url, { headers: this.getHeaders() });
  }
  createStudent(body: any): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/users`, body, { headers: this.getHeaders() });
  }
  updateStudent(id: number, body: any): Observable<any> {
    return this.http.put(`${this.baseUrl}/admin/users/${id}`, body, { headers: this.getHeaders() });
  }
  deleteStudent(id: number): Observable<any> {
    return this.http.delete(`${this.baseUrl}/admin/users/${id}`, { headers: this.getHeaders() });
  }

  // --- LAN Security & Violation features ---
  recordStudentViolation(contestId: number): Observable<any> {
    return this.http.put(`${this.baseUrl}/contests/${contestId}/violations`, {}, { headers: this.getHeaders() });
  }
  resetStudentViolations(rollNumber: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/students/${rollNumber}/reset-violations`, {}, { headers: this.getHeaders() });
  }
  getStudents(contestId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/contests/${contestId}/workstations`, { headers: this.getHeaders() });
  }

  // --- Newly integrated endpoints ---
  getContest(contestId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/contests/${contestId}`, { headers: this.getHeaders() });
  }
  getAdminContest(id: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/contests/${id}`, { headers: this.getHeaders() });
  }
  getAdminProblem(id: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/problems/${id}`, { headers: this.getHeaders() });
  }
  deleteProblem(id: number, force = false): Observable<any> {
    return this.http.delete(`${this.baseUrl}/admin/problems/${id}?force=${force}`, { headers: this.getHeaders() });
  }
  updateTestCase(id: number, body: any): Observable<any> {
    return this.http.put(`${this.baseUrl}/admin/test-cases/${id}`, body, { headers: this.getHeaders() });
  }

  uploadTestCaseZip(problemId: number, file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post(`${this.baseUrl}/admin/problems/${problemId}/test-cases/zip`, formData, {
      headers: this.getHeaders(true)
    });
  }

  calibrateLimits(problemId: number, body: { language: string; code: string }): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/problems/${problemId}/calibrate-limits`, body, {
      headers: this.getHeaders()
    });
  }
}

