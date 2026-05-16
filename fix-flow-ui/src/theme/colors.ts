export const colors = {
  bgBase: '#0f1117', bgPanel: '#1a1d27', bgBorder: '#2a2d3a',
  accent: { blue: '#3b82f6', green: '#22c55e', red: '#ef4444', amber: '#f59e0b', yellow: '#eab308', purple: '#a855f7', orange: '#f97316', cyan: '#06b6d4', gray: '#6b7280' },
  node: { START: '#3b82f6', SEND_FIX: '#22c55e', EXPECT_FIX: '#eab308', VALIDATE: '#a855f7', DECISION: '#f97316', BRANCH: '#f97316', RETRY: '#06b6d4', LOOP: '#06b6d4', WAIT: '#6b7280', DELAY: '#6b7280', END_PASS: '#22c55e', END_FAIL: '#ef4444' },
} as const;
export type NodeColorKey = keyof typeof colors.node;
