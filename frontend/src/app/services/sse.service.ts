import { Injectable, NgZone, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { LeaderboardEntry, VerdictEvent } from '../models/api.models';

export type SseConnectionState = 'disconnected' | 'connecting' | 'connected' | 'error';

export interface ContestEvent {
  contestId: number;
  status: 'UPCOMING' | 'ONGOING' | 'ENDED';
}

@Injectable({ providedIn: 'root' })
export class SseService {
  private readonly zone = inject(NgZone);

  readonly connectionState = signal<SseConnectionState>('disconnected');
  readonly verdict$ = new Subject<VerdictEvent>();
  readonly leaderboard$ = new Subject<LeaderboardEntry[]>();
  readonly contestEvent$ = new Subject<ContestEvent>();

  private source: EventSource | null = null;

  connect(rollNo: string): void {
    this.disconnect();
    this.connectionState.set('connecting');

    const url = `/api/events?rollNo=${encodeURIComponent(rollNo)}`;
    const source = new EventSource(url);
    this.source = source;

    source.onopen = () => {
      this.zone.run(() => this.connectionState.set('connected'));
    };

    source.onerror = () => {
      this.zone.run(() => {
        if (source.readyState === EventSource.CLOSED) {
          this.connectionState.set('disconnected');
        } else {
          this.connectionState.set('error');
        }
      });
    };

    source.addEventListener('verdict', (event) => {
      this.zone.run(() => {
        this.verdict$.next(JSON.parse((event as MessageEvent).data) as VerdictEvent);
      });
    });

    source.addEventListener('leaderboard', (event) => {
      this.zone.run(() => {
        this.leaderboard$.next(JSON.parse((event as MessageEvent).data) as LeaderboardEntry[]);
      });
    });

    source.addEventListener('contest', (event) => {
      this.zone.run(() => {
        this.contestEvent$.next(JSON.parse((event as MessageEvent).data) as ContestEvent);
      });
    });
  }

  disconnect(): void {
    this.source?.close();
    this.source = null;
    this.connectionState.set('disconnected');
  }
}
