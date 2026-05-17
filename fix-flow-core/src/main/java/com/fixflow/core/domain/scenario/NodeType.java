package com.fixflow.core.domain.scenario;

public enum NodeType {
    START, SEND_FIX, EXPECT_FIX, VALIDATE, WAIT, TIMEOUT,
    DECISION, BRANCH, RETRY, LOOP, DELAY, END_PASS, END_FAIL,
    HTTP_REQUEST, ROUTE_FIX
}
