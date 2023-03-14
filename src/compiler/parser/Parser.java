/**
 * @Author: turk
 * @Description: Sintaksni analizator.
 */

package compiler.parser;

import static common.RequireNonNull.requireNonNull;

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

import common.Report;
import compiler.lexer.Symbol;
import compiler.lexer.TokenType;

public class Parser {
    /**
     * Seznam leksikalnih simbolov.
     */
    private final List<Symbol> symbols;

    private Symbol currentSymbol; // trenutni simbol
    private int pos;              // indeks lokacije

    /**
     * Ciljni tok, kamor izpisujemo produkcije. Če produkcij ne želimo izpisovati,
     * vrednost opcijske spremenljivke nastavimo na Optional.empty().
     */
    private final Optional<PrintStream> productionsOutputStream;

    public Parser(List<Symbol> symbols, Optional<PrintStream> productionsOutputStream) {
        requireNonNull(symbols, productionsOutputStream);
        this.symbols = symbols;
        this.productionsOutputStream = productionsOutputStream;

        this.currentSymbol = symbols.get(0); // Pridobimo prvi simbol v izvorni kodi
        this.pos = 0;
    }

    private boolean check(TokenType tokenType) {
        if (currentSymbol.tokenType == tokenType) {
            skip();
            return true;
        }
        return false;
    }

    private void skip() {
        currentSymbol = symbols.get(++pos);
    }

    private void easyReport(String expected) {
        Report.error(currentSymbol.position, "SYN: Expected " + expected + " got " + currentSymbol.tokenType +
                ":'" + currentSymbol.lexeme + "'.");
    }

    /**
     * Izvedi sintaksno analizo.
     */
    public void parse() {
        parseSource();
    }

    private void parseSource() {
        dump("source -> definitions");
        parseDefinitions();
    }

    private void parseDefinitions() {
        dump("definitions -> definition definitions2");
        parseDefinition();
        parseDefinitions2();
    }

    private void parseDefinitions2() {
        if (check(TokenType.OP_SEMICOLON)) {
            dump("definitions2 -> ; definitions");
            parseDefinitions();
        } else {
            dump("definitions2 -> e");
        }
    }

    private void parseDefinition() {
        if (check(TokenType.KW_TYP)) {
            dump("definition -> type_definition");
            parseTypeDefinition();
        } else if (check(TokenType.KW_FUN)) {
            dump("definition -> function_definition");
            parseFunctionDefinition();
        } else if (check(TokenType.KW_VAR)) {
            dump("definition -> variable_definition");
            parseVariableDefinition();
        } else {
            easyReport("'typ' or 'fun' or 'var'");
        }
    }

    private void parseTypeDefinition() {
        dump("type_definition -> typ identifier : type");
        if (check(TokenType.IDENTIFIER)) {
            if (check(TokenType.OP_COLON)) {
                parseType();
            } else {
                easyReport("';'");
            }
        } else {
            easyReport("IDENTIFIER");
        }
    }

    private void parseType() {
        if (check(TokenType.IDENTIFIER)) {
            dump("type -> identifier");
        } else if (check(TokenType.AT_LOGICAL)) {
            dump("type -> logical");
        } else if (check(TokenType.AT_INTEGER)) {
            dump("type -> integer");
        } else if (check(TokenType.AT_STRING)) {
            dump("type -> string");
        } else if (check(TokenType.KW_ARR)) {
            dump("type -> arr [ int_const ] type");
            if (check(TokenType.OP_LBRACKET)) {
                if (check(TokenType.C_INTEGER)) {
                    if (check(TokenType.OP_RBRACKET)) {
                        parseType();
                    } else
                        easyReport("']'");
                } else
                    easyReport("CONSTANT_INTEGER");
            } else
                easyReport("'['");
        } else
            easyReport("IDENTIFIER or 'logical' or 'integer' or 'string' or 'arr'");

    }

    private void parseFunctionDefinition() {
        dump("function_definition -> fun identifier ( parameters ) : type = expression");
        if (check(TokenType.IDENTIFIER)) {
            if (check(TokenType.OP_LPARENT)) {
                parseParameters();
                if (check(TokenType.OP_RPARENT)) {
                    if (check(TokenType.OP_COLON)) {
                        parseType();
                        if (check(TokenType.OP_ASSIGN)) {
                            parseExpression();
                        } else
                            easyReport("'='");
                    } else
                        easyReport("':'");
                } else
                    easyReport("')'");
            } else
                easyReport("'('");
        } else {
            easyReport("IDENTIFIER");
        }
    }

    private void parseParameters() {
        dump("parameters -> parameter parameters2");
        parseParameter();
        parseParameters2();
    }

    private void parseParameters2() {
        if (check(TokenType.OP_COMMA)) {
            dump("parameters2 -> , parameters");
            parseParameters();
        } else {
            dump("parameters2 -> e");
        }
    }

    private void parseParameter() {
        dump("parameter -> identifier : type");
        if (check(TokenType.IDENTIFIER)) {
            if (check(TokenType.OP_COLON)) {
                parseType();
            } else
                easyReport("':'");
        } else {
            easyReport("IDENTIFIER");
        }
    }

    private void parseExpression() {
        dump("expression -> logical_ior_expression expression2");
        parseLogicalIorExpression();
        parseExpression2();
    }

    private void parseExpression2() {
        if(check(TokenType.OP_LBRACE)) {
            dump("expression2 -> { WHERE definitions }");
            if (check(TokenType.KW_WHERE)) {
                parseDefinitions();
                if (!check(TokenType.OP_RBRACE)) {
                    easyReport("'}'");
                }
            } else
                easyReport("'where'");
        } else {
            dump("expression2 -> e");
        }
    }

    private void parseLogicalIorExpression() {
        dump("logical_ior_expression -> logical_and_expression logical_ior_expression2");
        parseLogicalAndExpression();
        parseLogicalIorExpression2();
    }

    private void parseLogicalIorExpression2() {
        if (check(TokenType.OP_OR)) {
            dump("logical_ior_expression2 -> | logical_ior_expression");
            parseLogicalIorExpression();
        } else {
            dump("logical_ior_expression2 -> e");
        }
    }

    private void parseLogicalAndExpression() {
        dump("logical_and_expression -> compare_expression logical_and_expression2");
        parseCompareExpression();
        parseLogicalAndExpression2();
    }

    private void parseLogicalAndExpression2() {
        if (check(TokenType.OP_AND)) {
            dump("logical_and_expression2 -> & compare_expression");
            parseLogicalAndExpression();
        } else {
            dump("logical_and_expression2 -> e");
        }
    }

    private void parseCompareExpression() {
        dump("compare_expression -> additive_expression compare_expression2");
        parseAdditiveExpression();
        parseCompareExpression2();
    }

    private void parseCompareExpression2() {
        if (check(TokenType.OP_EQ)) {
            dump("compare_expression2 -> == additive_expression");
            parseAdditiveExpression();
        } else if (check(TokenType.OP_NEQ)) {
            dump("compare_expression2 -> != additive_expression");
            parseAdditiveExpression();
        } else if (check(TokenType.OP_LEQ)) {
            dump("compare_expression2 -> <= additive_expression");
            parseAdditiveExpression();
        } else if (check(TokenType.OP_GEQ)) {
            dump("compare_expression2 -> >= additive_expression");
            parseAdditiveExpression();
        } else if (check(TokenType.OP_LT)) {
            dump("compare_expression2 -> < additive_expression");
            parseAdditiveExpression();
        } else if (check(TokenType.OP_GT)) {
            dump("compare_expression2 -> > additive_expression");
            parseAdditiveExpression();
        } else {
            dump("compare_expression2 -> e");
        }
    }

    private void parseAdditiveExpression() {
        dump("additive_expression -> multiplicative_expression additive_expression2");
        parseMultiplicativeExpression();
        parseAdditiveExpression2();
    }

    private void parseAdditiveExpression2() {
        if (check(TokenType.OP_ADD)) {
            dump("additive_expression2 -> + additive_expression");
            parseAdditiveExpression();
        } else if (check(TokenType.OP_SUB)) {
            dump("additive_expression2 -> - additive_expression");
            parseAdditiveExpression();
        } else {
            dump("additive_expression2 -> e");
        }
    }

    private void parseMultiplicativeExpression() {
        dump("multiplicative_expression -> prefix_expression multiplicative_expression2");
        parsePrefixExpression();
        parseMultiplicativeExpression2();
    }

    private void parseMultiplicativeExpression2() {
        if (check(TokenType.OP_MUL)) {
            dump("multiplicative_expression2 -> * multiplicative_expression");
            parseMultiplicativeExpression();
        } else if (check(TokenType.OP_DIV)) {
            dump("multiplicative_expression2 -> / multiplicative_expression");
            parseMultiplicativeExpression();
        } else if (check(TokenType.OP_MOD)) {
            dump("multiplicative_expression2 -> % multiplicative_expression");
            parseMultiplicativeExpression();
        } else {
            dump("multiplicative_expression2 -> e");
        }
    }

    private void parsePrefixExpression() {
        if (check(TokenType.OP_ADD)) {
            dump("prefix_expression -> + prefix_expression");
            parsePrefixExpression();
        } else if (check(TokenType.OP_SUB)) {
            dump("prefix_expression -> - prefix_expression");
            parsePrefixExpression();
        } else if (check(TokenType.OP_NOT)) {
            dump("prefix_expression -> ! prefix_expression");
            parsePrefixExpression();
        } else {
            dump("prefix_expression -> postfix_expression");
            parsePostfixExpression();
        }
    }

    private void parsePostfixExpression() {
        dump("postfix_expression -> atom_expression postfix_expression2");
        parseAtomExpression();
        parsePostfixExpression2();
    }

    private void parsePostfixExpression2() {
        if (check(TokenType.OP_LBRACKET)) {
            dump("postfix_expression2 -> [ expression ] postfix_expression2");
            parseExpression();
            if (check(TokenType.OP_RBRACKET)) {
                parsePostfixExpression2();
            } else
                easyReport("']'");
        }
    }

    private void parseAtomExpression() {
        if (check(TokenType.C_LOGICAL)) {
            dump("atom_expression -> log_constant");
        } else if (check(TokenType.C_INTEGER)) {
            dump("atom_expression -> int_constant");
        } else if (check(TokenType.C_STRING)) {
            dump("atom_expression -> str_constant");
        } else if (check(TokenType.IDENTIFIER)) {
            dump("atom_expression -> identifier atom_expression2");
            parseAtomExpression2();
        } else if (check(TokenType.OP_LBRACE)) {
            dump("atom_expression -> { atom_expression3 }");
            parseAtomExpression3();
            if (!check(TokenType.OP_RBRACE)) {
                easyReport("'}'");
            }
        } else if (check(TokenType.OP_LPARENT)) {
            dump("atom_expression -> ( expressions )");
            parseExpressions();
            if (!check(TokenType.OP_RPARENT)) {
                easyReport("')'");
            }
        } else {
            easyReport("CONSTANT_LOGICAL or CONSTANT_INTEGER or CONSTANT_STRING or IDENTIFIER or '{' or '('");
        }
    }

    private void parseAtomExpression2() {
        if (check(TokenType.OP_LPARENT)) {
            dump("atom_expression2 -> ( expressions )");
            parseExpressions();
            if (!check(TokenType.OP_RPARENT))
                easyReport("')'");
        } else {
            dump("atom_expression2 -> e");
        }
    }

    private void parseAtomExpression3() {
        if (check(TokenType.KW_IF)) {
            dump("atom_expression3 -> if expression then expression atom_expression4");
            parseExpression();
            if (check(TokenType.KW_THEN)) {
                parseExpression();
                parseAtomExpression4();
            } else
                easyReport("'then'");
        } else if (check(TokenType.KW_WHILE)) {
            dump("atom_expression3 -> while expression : expression");
            parseExpression();
            if (check(TokenType.OP_COLON)) {
                parseExpression();
            } else 
                easyReport("':'");
        } else if (check(TokenType.KW_FOR)) {
            dump("atom_expression3 -> for identifier = expression , expression , expression : expression");
            if (check(TokenType.IDENTIFIER)) {
                if (check(TokenType.OP_ASSIGN)) {
                    parseExpression();
                    if (check(TokenType.OP_COMMA)) {
                        parseExpression();
                        if (check(TokenType.OP_COMMA)) {
                            parseExpression();
                            if (check(TokenType.OP_COLON)) {
                                parseExpression();
                            } else
                                easyReport("':'");
                        } else
                            easyReport("','");
                    } else
                        easyReport("','");
                } else
                    easyReport("'='");
            } else
                easyReport("IDENTIFIER");
        } else {
            dump("atom_expression3 -> expression = expression");
            parseExpression();
            if (check(TokenType.OP_ASSIGN)) {
                parseExpression();
            } else {
                easyReport("'='");
            }
        }
    }

    private void parseAtomExpression4() {
        if (check(TokenType.KW_ELSE)) {
            dump("atom_expression4 -> else expression");
            parseExpression();
        } else {
            dump("atom_expression4 -> e");
        }
    }

    private void parseExpressions() {
        dump("expressions -> expression expressions2");
        parseExpression();
        parseExpressions2();
    }

    private void parseExpressions2() {
        if (check(TokenType.OP_COMMA)) {
            dump("expressions2 -> , expression");
            parseExpression();
        } else {
            dump("expressions2 -> e");
        }
    }

    private void parseVariableDefinition() {
        dump("variable_definition -> var identifier : type");
        if (check(TokenType.IDENTIFIER)) {
            if (check(TokenType.OP_COLON)) {
                parseType();
            } else
                easyReport("':'");
        } else {
            easyReport("IDENTIFIER");
        }
    }

    /**
     * Izpiše produkcijo na izhodni tok.
     */
    private void dump(String production) {
        if (productionsOutputStream.isPresent()) {
            productionsOutputStream.get().println(production);
        }
    }
}
