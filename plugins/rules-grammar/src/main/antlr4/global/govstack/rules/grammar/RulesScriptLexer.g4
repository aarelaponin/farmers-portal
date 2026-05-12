lexer grammar RulesScriptLexer;

// ============================================================
// Case-insensitive letter fragments
// ============================================================
fragment A: [aA];
fragment B: [bB];
fragment C: [cC];
fragment D: [dD];
fragment E: [eE];
fragment F: [fF];
fragment G: [gG];
fragment H: [hH];
fragment I: [iI];
fragment J: [jJ];
fragment K: [kK];
fragment L: [lL];
fragment M: [mM];
fragment N: [nN];
fragment O: [oO];
fragment P: [pP];
fragment Q: [qQ];
fragment R: [rR];
fragment S: [sS];
fragment T: [tT];
fragment U: [uU];
fragment V: [vV];
fragment W: [wW];
fragment X: [xX];
fragment Y: [yY];
fragment Z: [zZ];

fragment DIGIT: [0-9];
fragment LETTER: [a-zA-Z];

// ============================================================
// Multi-word keywords (must come before single-word to match longest)
// ============================================================
IS_NOT_EMPTY  : I S WS_INLINE+ N O T WS_INLINE+ E M P T Y;
IS_EMPTY      : I S WS_INLINE+ E M P T Y;
NOT_IN        : N O T WS_INLINE+ I N;
STARTS_WITH   : S T A R T S WS_INLINE+ W I T H;
ENDS_WITH     : E N D S WS_INLINE+ W I T H;
PASS_MESSAGE  : P A S S WS_INLINE+ M E S S A G E;
FAIL_MESSAGE  : F A I L WS_INLINE+ M E S S A G E;

// Inline whitespace fragment (for multi-word keywords)
fragment WS_INLINE: [ \t];

// ============================================================
// Rule structure keywords
// ============================================================
RULE      : R U L E;
TYPE      : T Y P E;
CATEGORY  : C A T E G O R Y;
MANDATORY : M A N D A T O R Y;
ORDER     : O R D E R;
WHEN      : W H E N;
SCORE     : S C O R E;
WEIGHT    : W E I G H T;

// ============================================================
// Rule types
// ============================================================
INCLUSION : I N C L U S I O N;
EXCLUSION : E X C L U S I O N;
PRIORITY  : P R I O R I T Y;
BONUS     : B O N U S;

// ============================================================
// Boolean values
// ============================================================
YES   : Y E S;
NO    : N O;
TRUE  : T R U E;
FALSE : F A L S E;

// ============================================================
// Logical operators
// ============================================================
AND : A N D;
OR  : O R;
NOT : N O T;

// ============================================================
// Comparison keywords
// ============================================================
BETWEEN  : B E T W E E N;
IN       : I N;
CONTAINS : C O N T A I N S;

// ============================================================
// Aggregation functions
// ============================================================
COUNT : C O U N T;
SUM   : S U M;
AVG   : A V G;
MIN   : M I N;
MAX   : M A X;

// ============================================================
// Grid check functions
// ============================================================
HAS_ANY  : H A S '_' A N Y;
HAS_ALL  : H A S '_' A L L;
HAS_NONE : H A S '_' N O N E;

// ============================================================
// Operators
// ============================================================
EQ   : '=';
NEQ  : '!=';
GTE  : '>=';
GT   : '>';
LTE  : '<=';
LT   : '<';

// ============================================================
// Punctuation
// ============================================================
LPAREN : '(';
RPAREN : ')';
COMMA  : ',';
COLON  : ':';
DOT    : '.';
PLUS   : '+';
MINUS  : '-';

// ============================================================
// Literals
// ============================================================
STRING
    : '"' (~["\r\n\\] | '\\' .)* '"'
    | '\'' (~['\r\n\\] | '\\' .)* '\''
    ;

NUMBER
    : '-'? DIGIT+ ('.' DIGIT+)?
    ;

// Identifier must come after keywords to give keywords priority
IDENTIFIER
    : LETTER (LETTER | DIGIT | '_')*
    ;

// ============================================================
// Whitespace and comments
// ============================================================
COMMENT : '#' ~[\r\n]* -> skip;
WS      : [ \t]+ -> skip;
NEWLINE : [\r\n]+ -> channel(HIDDEN);
