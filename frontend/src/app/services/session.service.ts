import { Injectable, signal } from '@angular/core';

const ROLL_NO_KEY = 'cpj.rollNo';
const CONTEST_ID_KEY = 'cpj.contestId';

@Injectable({ providedIn: 'root' })
export class SessionService {
  readonly rollNo = signal(this.read(ROLL_NO_KEY));
  readonly contestId = signal(this.readNumber(CONTEST_ID_KEY));

  isConnected(): boolean {
    return !!this.rollNo() && this.contestId() !== null;
  }

  connect(rollNo: string, contestId: number): void {
    const trimmed = rollNo.trim();
    sessionStorage.setItem(ROLL_NO_KEY, trimmed);
    sessionStorage.setItem(CONTEST_ID_KEY, String(contestId));
    this.rollNo.set(trimmed);
    this.contestId.set(contestId);
  }

  disconnect(): void {
    sessionStorage.removeItem(ROLL_NO_KEY);
    sessionStorage.removeItem(CONTEST_ID_KEY);
    this.rollNo.set('');
    this.contestId.set(null);
  }

  private read(key: string): string {
    return sessionStorage.getItem(key) ?? '';
  }

  private readNumber(key: string): number | null {
    const value = sessionStorage.getItem(key);
    if (!value) {
      return null;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
}
