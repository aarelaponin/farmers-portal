parser grammar RulesScriptParser;

options { tokenVocab = RulesScriptLexer; }

// ============================================================
// Entry point
// ============================================================
script
    : rule* EOF
    ;

// ============================================================
// Rule definition
// ============================================================
rule
    : RULE STRING ruleBody
    ;

ruleBody
    : ruleClause*
    ;

ruleClause
    : TYPE COLON ruleType
    | CATEGORY COLON IDENTIFIER
    | MANDATORY COLON boolValue
    | ORDER COLON NUMBER
    | WHEN condition
    | SCORE COLON (PLUS | MINUS)? NUMBER
    | WEIGHT COLON NUMBER
    | PASS_MESSAGE COLON STRING
    | FAIL_MESSAGE COLON STRING
    ;

ruleType
    : INCLUSION
    | EXCLUSION
    | PRIORITY
    | BONUS
    ;

boolValue
    : YES
    | NO
    | TRUE
    | FALSE
    | NUMBER
    ;

// ============================================================
// Condition expressions (precedence: NOT > AND > OR)
// ============================================================
condition
    : orExpr
    ;

orExpr
    : andExpr (OR andExpr)*
    ;

andExpr
    : unaryExpr (AND unaryExpr)*
    ;

unaryExpr
    : NOT? primaryExpr
    ;

primaryExpr
    : LPAREN condition RPAREN                          # GroupedCondition
    | functionCall                                      # FunctionCondition
    | comparison                                        # ComparisonCondition
    ;

// ============================================================
// Comparisons
// ============================================================
comparison
    : fieldRef IS_EMPTY                                 # IsEmptyComparison
    | fieldRef IS_NOT_EMPTY                             # IsNotEmptyComparison
    | fieldRef BETWEEN value AND value                  # BetweenComparison
    | fieldRef IN LPAREN valueList RPAREN               # InComparison
    | fieldRef NOT_IN LPAREN valueList RPAREN           # NotInComparison
    | fieldRef comparisonOp value                       # SimpleComparison
    ;

comparisonOp
    : EQ
    | NEQ
    | GT
    | GTE
    | LT
    | LTE
    | CONTAINS
    | STARTS_WITH
    | ENDS_WITH
    ;

// ============================================================
// Function calls
// ============================================================
functionCall
    : aggregationFunc LPAREN fieldRef RPAREN (comparisonOp value)?   # AggregationCall
    | gridCheckFunc LPAREN fieldRef (COMMA valueList)? RPAREN        # GridCheckCall
    ;

aggregationFunc
    : COUNT
    | SUM
    | AVG
    | MIN
    | MAX
    ;

gridCheckFunc
    : HAS_ANY
    | HAS_ALL
    | HAS_NONE
    ;

// ============================================================
// Field reference (supports dot notation: field.subfield)
// ============================================================
fieldRef
    : IDENTIFIER (DOT IDENTIFIER)*
    ;

// ============================================================
// Values
// ============================================================
value
    : STRING
    | NUMBER
    | boolValue
    | IDENTIFIER
    ;

valueList
    : value (COMMA value)*
    ;
