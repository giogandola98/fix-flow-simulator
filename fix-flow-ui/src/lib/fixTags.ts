// FIX tag number -> field name dictionary (common FIX 4.2 / 4.4 / FIXT tags).
// Used by the node config editors to show human-readable field names next to tags.
export const FIX_TAGS: Record<number, string> = {
  1: 'Account',
  6: 'AvgPx',
  8: 'BeginString',
  9: 'BodyLength',
  10: 'CheckSum',
  11: 'ClOrdID',
  14: 'CumQty',
  15: 'Currency',
  17: 'ExecID',
  18: 'ExecInst',
  19: 'ExecRefID',
  20: 'ExecTransType',
  21: 'HandlInst',
  22: 'SecurityIDSource',
  30: 'LastMkt',
  31: 'LastPx',
  32: 'LastQty',
  34: 'MsgSeqNum',
  35: 'MsgType',
  37: 'OrderID',
  38: 'OrderQty',
  39: 'OrdStatus',
  40: 'OrdType',
  41: 'OrigClOrdID',
  44: 'Price',
  48: 'SecurityID',
  49: 'SenderCompID',
  50: 'SenderSubID',
  52: 'SendingTime',
  54: 'Side',
  55: 'Symbol',
  56: 'TargetCompID',
  57: 'TargetSubID',
  58: 'Text',
  59: 'TimeInForce',
  60: 'TransactTime',
  63: 'SettlType',
  64: 'SettlDate',
  75: 'TradeDate',
  76: 'ExecBroker',
  99: 'StopPx',
  100: 'ExDestination',
  103: 'OrdRejReason',
  107: 'SecurityDesc',
  108: 'HeartBtInt',
  110: 'MinQty',
  114: 'LocateReqd',
  115: 'OnBehalfOfCompID',
  120: 'SettlCurrency',
  126: 'ExpireTime',
  128: 'DeliverToCompID',
  141: 'ResetSeqNumFlag',
  150: 'ExecType',
  151: 'LeavesQty',
  167: 'SecurityType',
  200: 'MaturityMonthYear',
  201: 'PutOrCall',
  202: 'StrikePrice',
  205: 'MaturityDay',
  207: 'SecurityExchange',
  262: 'MDReqID',
  263: 'SubscriptionRequestType',
  264: 'MarketDepth',
  265: 'MDUpdateType',
  267: 'NoMDEntryTypes',
  268: 'NoMDEntries',
  269: 'MDEntryType',
  270: 'MDEntryPx',
  271: 'MDEntrySize',
  336: 'TradingSessionID',
  354: 'EncodedTextLen',
  355: 'EncodedText',
  372: 'RefMsgType',
  373: 'SessionRejectReason',
  379: 'BusinessRejectRefID',
  380: 'BusinessRejectReason',
  423: 'PriceType',
  432: 'ExpireDate',
  447: 'PartyIDSource',
  448: 'PartyID',
  452: 'PartyRole',
  453: 'NoPartyIDs',
  461: 'CFICode',
  528: 'OrderCapacity',
  529: 'OrderRestrictions',
  541: 'MaturityDate',
  636: 'WorkingIndicator',
  1128: 'ApplVerID',

  // Order lifecycle
  102: 'CxlRejReason', 378: 'ExecRestatementReason', 434: 'CxlRejResponseTo',
  442: 'MultiLegReportingType', 584: 'MassStatusReqID',

  // Instrument reference block
  460: 'Product', 762: 'SecuritySubType',

  // FX settlement
  119: 'SettlCurrAmt', 155: 'SettlCurrFxRate', 156: 'SettlCurrFxRateCalc',
  193: 'SettlDate2',

  // Options
  231: 'ContractMultiplier', 947: 'StrikeCurrency', 1193: 'SettlMethod',
  1194: 'ExerciseStyle', 1482: 'OptPayoutType',

  // Legs (NoLegs group)
  555: 'NoLegs', 566: 'LegPrice', 587: 'LegSettlType', 588: 'LegSettlDate',
  600: 'LegSymbol', 608: 'LegCFICode', 609: 'LegSecurityType',
  623: 'LegRatioQty', 624: 'LegSide', 637: 'LegLastPx', 654: 'LegRefID',
  675: 'LegSettlCurrency', 687: 'LegQty', 1418: 'LegLastQty',

  // Events (NoEvents group)
  864: 'NoEvents', 865: 'EventType', 866: 'EventDate',

  // Underlyings (NoUnderlyings group)
  311: 'UnderlyingSymbol', 711: 'NoUnderlyings',

  // Position maintenance
  581: 'AccountType', 702: 'NoPositions', 703: 'PosType', 704: 'LongQty',
  705: 'ShortQty', 709: 'PosTransType', 710: 'PosReqID', 712: 'PosMaintAction',
  715: 'ClearingBusinessDate', 721: 'PosMaintRptID', 722: 'PosMaintStatus',
  723: 'PosMaintResult',

  // Trade capture
  487: 'TradeReportTransType', 571: 'TradeReportID', 828: 'TrdType',
  856: 'TradeReportType', 1003: 'TradeID', 1123: 'TradeReportStatus',

  // Allocations
  78: 'NoAllocs', 79: 'AllocAccount',
};

/**
 * Repeating group counter tags offered in the SEND_FIX group editor, and used by
 * parseFIXMessage to rebuild group structure from a pasted raw message.
 * Every key must also exist in FIX_TAGS.
 */
export const GROUP_COUNTER_TAGS: Record<number, string> = {
  78: 'NoAllocs',
  453: 'NoPartyIDs',
  555: 'NoLegs',
  702: 'NoPositions',
  711: 'NoUnderlyings',
  864: 'NoEvents',
};

export function fixTagName(tag: number): string | undefined {
  return FIX_TAGS[tag];
}
