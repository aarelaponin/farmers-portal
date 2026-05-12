package global.govstack.rules.grammar;

import global.govstack.rules.grammar.model.Script;
import org.antlr.v4.runtime.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for parsing Rules Script.
 * Provides a simple, fluent API for parsing rule scripts from various sources.
 *
 * <h2>Usage Examples:</h2>
 * <pre>{@code
 * // Parse from string
 * Script script = RulesScript.parse("""
 *     RULE "Age Check"
 *     TYPE: INCLUSION
 *     WHEN age >= 18
 *     """).getScriptOrThrow();
 *
 * // Parse from file
 * ParseResult result = RulesScript.parseFile(Path.of("rules.txt"));
 * if (result.isSuccess()) {
 *     Script script = result.script();
 *     // process rules...
 * } else {
 *     System.err.println(result.formatErrors());
 * }
 *
 * // Parse with custom error handling
 * RulesScript.parse(input)
 *     .getScript()
 *     .ifPresent(script -> {
 *         script.rules().forEach(rule -> System.out.println(rule.name()));
 *     });
 * }</pre>
 */
public final class RulesScript {

    private RulesScript() {
        // Utility class, no instantiation
    }

    /**
     * Parses Rules Script from a string.
     *
     * @param input the Rules Script source code
     * @return parse result containing the script or errors
     */
    public static ParseResult parse(String input) {
        return parseCharStream(CharStreams.fromString(input));
    }

    /**
     * Parses Rules Script from a file.
     *
     * @param path path to the Rules Script file
     * @return parse result containing the script or errors
     * @throws IOException if the file cannot be read
     */
    public static ParseResult parseFile(Path path) throws IOException {
        return parseCharStream(CharStreams.fromPath(path));
    }

    /**
     * Parses Rules Script from a file path string.
     *
     * @param path path to the Rules Script file
     * @return parse result containing the script or errors
     * @throws IOException if the file cannot be read
     */
    public static ParseResult parseFile(String path) throws IOException {
        return parseFile(Path.of(path));
    }

    /**
     * Parses Rules Script from an input stream.
     *
     * @param stream input stream containing Rules Script source
     * @return parse result containing the script or errors
     * @throws IOException if the stream cannot be read
     */
    public static ParseResult parseStream(InputStream stream) throws IOException {
        return parseCharStream(CharStreams.fromStream(stream));
    }

    /**
     * Parses Rules Script from a reader.
     *
     * @param reader reader containing Rules Script source
     * @return parse result containing the script or errors
     * @throws IOException if the reader cannot be read
     */
    public static ParseResult parseReader(Reader reader) throws IOException {
        return parseCharStream(CharStreams.fromReader(reader));
    }

    /**
     * Core parsing method that works with ANTLR CharStream.
     */
    private static ParseResult parseCharStream(CharStream charStream) {
        List<ParseError> errors = new ArrayList<>();

        // Create lexer
        RulesScriptLexer lexer = new RulesScriptLexer(charStream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(createErrorListener(errors));

        // Create parser
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        RulesScriptParser parser = new RulesScriptParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(createErrorListener(errors));

        // Parse
        RulesScriptParser.ScriptContext tree = parser.script();

        // If there were syntax errors, return failure or partial result
        if (!errors.isEmpty()) {
            // Try to build AST anyway for partial results
            try {
                RulesScriptAstBuilder builder = new RulesScriptAstBuilder();
                Script script = builder.visitScript(tree);
                return ParseResult.partial(script, errors);
            } catch (Exception e) {
                return ParseResult.failure(errors);
            }
        }

        // Build AST
        try {
            RulesScriptAstBuilder builder = new RulesScriptAstBuilder();
            Script script = builder.visitScript(tree);
            return ParseResult.success(script);
        } catch (Exception e) {
            errors.add(new ParseError(0, 0, "AST building failed: " + e.getMessage(), null));
            return ParseResult.failure(errors);
        }
    }

    /**
     * Creates an ANTLR error listener that collects errors.
     */
    private static BaseErrorListener createErrorListener(List<ParseError> errors) {
        return new BaseErrorListener() {
            @Override
            public void syntaxError(
                    Recognizer<?, ?> recognizer,
                    Object offendingSymbol,
                    int line,
                    int charPositionInLine,
                    String msg,
                    RecognitionException e
            ) {
                String symbol = offendingSymbol != null ? offendingSymbol.toString() : null;
                errors.add(new ParseError(line, charPositionInLine, msg, symbol));
            }
        };
    }

    /**
     * Validates Rules Script syntax without building the full AST.
     * This is faster for validation-only use cases.
     *
     * @param input the Rules Script source code
     * @return list of parse errors, empty if valid
     */
    public static List<ParseError> validate(String input) {
        List<ParseError> errors = new ArrayList<>();

        CharStream charStream = CharStreams.fromString(input);
        RulesScriptLexer lexer = new RulesScriptLexer(charStream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(createErrorListener(errors));

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        RulesScriptParser parser = new RulesScriptParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(createErrorListener(errors));

        // Parse without building AST
        parser.script();

        return errors;
    }

    /**
     * Checks if the input is valid Rules Script syntax.
     *
     * @param input the Rules Script source code
     * @return true if the input is syntactically valid
     */
    public static boolean isValid(String input) {
        return validate(input).isEmpty();
    }
}
