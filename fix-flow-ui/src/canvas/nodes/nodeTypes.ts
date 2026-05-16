import { NodeTypes } from '@xyflow/react';
import { StartNode } from './StartNode';
import { SendFIXNode } from './SendFIXNode';
import { ExpectFIXNode } from './ExpectFIXNode';
import { ValidateNode } from './ValidateNode';
import { DecisionNode } from './DecisionNode';
import { EndPassNode } from './EndPassNode';
import { EndFailNode } from './EndFailNode';
import { RetryNode } from './RetryNode';
import { WaitNode } from './WaitNode';

export const nodeTypes: NodeTypes = {
  START: StartNode,
  SEND_FIX: SendFIXNode,
  EXPECT_FIX: ExpectFIXNode,
  VALIDATE: ValidateNode,
  DECISION: DecisionNode,
  BRANCH: DecisionNode,
  END_PASS: EndPassNode,
  END_FAIL: EndFailNode,
  RETRY: RetryNode,
  LOOP: RetryNode,
  WAIT: WaitNode,
  DELAY: WaitNode,
  TIMEOUT: WaitNode,
};
