/**
 * JRE Mode for CodeMirror
 * Syntax highlighting for Rules Script (Joget Rule Editor)
 */
(function(mod) {
    if (typeof exports === "object" && typeof module === "object") // CommonJS
        mod(require("codemirror"));
    else if (typeof define === "function" && define.amd) // AMD
        define(["codemirror"], mod);
    else // Plain browser env
        mod(CodeMirror);
})(function(CodeMirror) {
    "use strict";

    CodeMirror.defineMode("jre", function() {

        // Keywords (blue, bold)
        var keywords = new RegExp("^(RULE|WHEN|AND|OR|NOT|BETWEEN|IN|CONTAINS|IS|EMPTY|STARTS|ENDS|WITH|DEPENDS|ON|STOP|FAIL|PASS|EFFECTIVE|FROM|TO)\\b", "i");

        // Clause keywords (purple)
        var clauses = new RegExp("^(TYPE|CATEGORY|MANDATORY|ORDER|SCORE|WEIGHT|MESSAGE)\\b", "i");

        // Types (brown)
        var types = new RegExp("^(INCLUSION|EXCLUSION|PRIORITY|BONUS|DEMOGRAPHIC|ECONOMIC|AGRICULTURAL|VULNERABILITY|HOUSEHOLD)\\b", "i");

        // Booleans (red)
        var booleans = new RegExp("^(YES|NO|true|false|Y|N)\\b", "i");

        // Operators
        var operators = /^(>=|<=|!=|<>|=|>|<|\+|-)/;

        return {
            startState: function() {
                return {
                    inString: false,
                    stringChar: null
                };
            },

            token: function(stream, state) {
                // Handle strings
                if (state.inString) {
                    while (!stream.eol()) {
                        var ch = stream.next();
                        if (ch === state.stringChar) {
                            state.inString = false;
                            state.stringChar = null;
                            return "string";
                        }
                        if (ch === '\\') {
                            stream.next(); // Skip escaped char
                        }
                    }
                    return "string";
                }

                // Skip whitespace
                if (stream.eatSpace()) {
                    return null;
                }

                // Comments (# to end of line)
                if (stream.match('#')) {
                    stream.skipToEnd();
                    return "comment";
                }

                // Start of string
                var ch = stream.peek();
                if (ch === '"' || ch === "'") {
                    stream.next();
                    state.inString = true;
                    state.stringChar = ch;
                    return "string";
                }

                // Numbers (including decimals and signed)
                if (stream.match(/^[+-]?\d+(\.\d+)?/)) {
                    return "number";
                }

                // Operators
                if (stream.match(operators)) {
                    return "operator";
                }

                // Colon (for clauses like TYPE:)
                if (stream.match(':')) {
                    return "punctuation";
                }

                // Parentheses, commas
                if (stream.match(/^[\(\),]/)) {
                    return "bracket";
                }

                // Words (identifiers, keywords)
                if (stream.match(/^[a-zA-Z_][a-zA-Z0-9_]*/)) {
                    var word = stream.current();

                    if (keywords.test(word)) {
                        return "keyword";
                    }
                    if (clauses.test(word)) {
                        return "def";  // Definition/clause keywords
                    }
                    if (types.test(word)) {
                        return "type";
                    }
                    if (booleans.test(word)) {
                        return "atom";  // Boolean atoms
                    }

                    // Must be a field name
                    return "variable";
                }

                // Unknown character
                stream.next();
                return null;
            }
        };
    });

    CodeMirror.defineMIME("text/x-jre", "jre");
});
