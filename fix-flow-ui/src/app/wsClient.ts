import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { ExecutionEvent, FIXMessage } from '../types';

export interface SessionStatusEvent {
  sessionId: string;
  status: 'UP' | 'DOWN' | 'LOGON' | 'LOGOUT';
  timestamp: string;
}

class WsClient {
  private client: Client | null = null;
  private connectPromise: Promise<void> | null = null;
  private subscriptions: Map<string, StompSubscription> = new Map();

  connect(): Promise<void> {
    if (this.connectPromise) return this.connectPromise;
    this.connectPromise = new Promise((resolve, reject) => {
      this.client = new Client({
        webSocketFactory: () => new SockJS('/ws'),
        reconnectDelay: 5_000,
        heartbeatIncoming: 10_000,
        heartbeatOutgoing: 10_000,
        debug: () => undefined,
        onConnect: () => resolve(),
        onStompError: (frame) => reject(new Error(frame.headers['message'] ?? 'STOMP error')),
        onWebSocketError: (err) => reject(err),
      });
      this.client.activate();
    });
    return this.connectPromise;
  }

  async subscribeExecution(
    executionId: string,
    onEvent: (event: ExecutionEvent) => void,
    onMessage: (msg: FIXMessage) => void,
  ): Promise<() => void> {
    await this.connect();
    if (!this.client) throw new Error('STOMP client not initialised');
    const eventsKey = `events:${executionId}`;
    const messagesKey = `messages:${executionId}`;
    const eventsSub = this.client.subscribe(
      `/topic/executions/${executionId}/events`,
      (frame: IMessage) => onEvent(JSON.parse(frame.body) as ExecutionEvent),
    );
    const messagesSub = this.client.subscribe(
      `/topic/executions/${executionId}/messages`,
      (frame: IMessage) => onMessage(JSON.parse(frame.body) as FIXMessage),
    );
    this.subscriptions.set(eventsKey, eventsSub);
    this.subscriptions.set(messagesKey, messagesSub);
    return () => {
      eventsSub.unsubscribe();
      messagesSub.unsubscribe();
      this.subscriptions.delete(eventsKey);
      this.subscriptions.delete(messagesKey);
    };
  }

  async subscribeSession(
    sessionId: string,
    onStatus: (status: SessionStatusEvent) => void,
  ): Promise<() => void> {
    await this.connect();
    if (!this.client) throw new Error('STOMP client not initialised');
    const key = `session:${sessionId}`;
    const sub = this.client.subscribe(
      `/topic/sessions/${sessionId}/status`,
      (frame: IMessage) => onStatus(JSON.parse(frame.body) as SessionStatusEvent),
    );
    this.subscriptions.set(key, sub);
    return () => {
      sub.unsubscribe();
      this.subscriptions.delete(key);
    };
  }

  disconnect(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
    this.subscriptions.clear();
    if (this.client) this.client.deactivate();
    this.client = null;
    this.connectPromise = null;
  }
}

export const wsClient = new WsClient();
