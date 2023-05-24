/**
 * @Description: Sintaksni analizator.
 */

package compiler.parser;

import static compiler.lexer.TokenType.*;
import static common.RequireNonNull.requireNonNull;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import common.Report;
import compiler.lexer.Position;
import compiler.lexer.Symbol;
import compiler.lexer.TokenType;
import compiler.parser.ast.Ast;
import compiler.parser.ast.def.*;
import compiler.parser.ast.expr.*;
import compiler.parser.ast.type.*;

public class Parser {
    /**
     * Seznam leksikalnih simbolov.
     */
    private final List<Symbol> symbols;

    private Symbol currentSymbol; // trenutni simbol
    private int currentSymbolIndex;              // indeks lokacije

    /**
     * Ciljni tok, kamor izpisujemo produkcije. Če produkcij ne želimo izpisovati,
     * vrednost opcijske spremenljivke nastavimo na Optional.empty().
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private final Optional<PrintStream> productionsOutputStream;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public Parser(List<Symbol> symbols, Optional<PrintStream> productionsOutputStream) {
        requireNonNull(symbols, productionsOutputStream);
        this.symbols = symbols;
        this.productionsOutputStream = productionsOutputStream;

        this.currentSymbol = symbols.get(0); // Pridobimo prvi simbol v izvorni kodi
        this.currentSymbolIndex = 0;
    }

    private boolean check(TokenType tokenType) {
        if (currentSymbol.tokenType == tokenType) {
            skip();
            return true;
        }
        return false;
    }

    private void skip() {
        currentSymbol = symbols.get(++currentSymbolIndex);
    }

    private void easyReport(String expected) {
        Report.error(currentSymbol.position, "SYN: Expected " + expected + ", got " + currentSymbol.tokenType +
                ":'" + currentSymbol.lexeme + "'.");
    }

    /**
     * Izvedi sintaksno analizo.
     */
    public Ast parse() {
        return parseSource();
    }

    private Ast parseSource() {
        dump("source -> definitions");
        var defs = parseDefinitions();

        currentSymbolIndex--;
        if (!check(TokenType.EOF)) {
            easyReport("EOF");
        }
        return defs;
    }

    private Defs parseDefinitions() {
        dump("definitions -> definition definitions2");
        var pos = currentSymbol.position;
        List<Def> defs = new ArrayList<>();
        defs.add(parseDefinition());
        defs = parseDefinitions2(defs);
        return new Defs(
                new Position(
                        pos.start,
                        defs.get(defs.size()-1).position.end
                ),
                defs
        );
    }

    private List<Def> parseDefinitions2(List<Def> defs) {
        if (check(TokenType.OP_SEMICOLON)) {
            dump("definitions2 -> ; definitions");
            defs.add(parseDefinition());
            return parseDefinitions2(defs);
        } else {
            dump("definitions2 -> e");
            return defs;
        }
    }

    private Def parseDefinition() {
        var symbol = currentSymbol;
        if (check(TokenType.KW_TYP)) {
            dump("definition -> type_definition");
            return parseTypeDefinition(symbol.position);
        } else if (check(TokenType.KW_FUN)) {
            dump("definition -> function_definition");
            return parseFunctionDefinition(symbol.position);
        } else if (check(TokenType.KW_VAR)) {
            dump("definition -> variable_definition");
            return parseVariableDefinition(symbol.position);
        } else {
            easyReport("'typ' or 'fun' or 'var'");
        }
        return null; // Nikoli se ne izvede. Ali se bo vrnila `Def` ali pa bo javljena napaka prevajalnika.
    }

    @SuppressWarnings("ConstantConditions")
    private TypeDef parseTypeDefinition(Position pos) {
        dump("type_definition -> typ identifier : type");
        var symbol = currentSymbol;
        if (check(TokenType.IDENTIFIER)) {
            if (check(TokenType.OP_COLON)) {
                var type = parseType();
                return new TypeDef(
                        new Position(
                                pos.start,
                                type.position.end
                        ),
                        symbol.lexeme,
                        type
                );
            } else {
                easyReport("';'");
            }
        } else {
            easyReport("IDENTIFIER");
        }
        return null; // Nikoli se ne izvede. Ali se bo vrnila `TypeDef` ali pa bo javljena napaka prevajalnika.
    }

    @SuppressWarnings("ConstantConditions")
    private Type parseType() {
        var symbol = currentSymbol;
        if (check(TokenType.IDENTIFIER)) {
            dump("type -> identifier");
            return new TypeName(
                    symbol.position,
                    symbol.lexeme
            );
        } else if (check(TokenType.AT_LOGICAL)) {
            dump("type -> logical");
            return Atom.LOG(symbol.position);
        } else if (check(TokenType.AT_INTEGER)) {
            dump("type -> integer");
            return Atom.INT(symbol.position);
        } else if (check(TokenType.AT_STRING)) {
            dump("type -> string");
            return Atom.STR(symbol.position);
        } else if (check(TokenType.KW_ARR)) {
            dump("type -> arr [ int_const ] type");
            if (check(TokenType.OP_LBRACKET)) {
                var symbol2 = currentSymbol;
                if (check(TokenType.C_INTEGER)) {
                    if (check(TokenType.OP_RBRACKET)) {
                        var type = parseType();
                        return new Array(
                                new Position(
                                        symbol.position.start,
                                        type.position.end
                                ),
                                Integer.parseInt(symbol2.lexeme),
                                type
                        );
                    } else
                        easyReport("']'");
                } else
                    easyReport("CONSTANT_INTEGER");
            } else
                easyReport("'['");
        } else {
            easyReport("IDENTIFIER or 'logical' or 'integer' or 'string' or 'arr'");
        }
        return null; // Nikoli se ne izvede. Ali se bo vrnila `TypeDef` ali pa bo javljena napaka prevajalnika.
    }

    private FunDef parseFunctionDefinition(Position pos) {
        dump("function_definition -> fun identifier ( parameters ) : type = expression");
        var symbol = currentSymbol;
        if (check(TokenType.IDENTIFIER)) {
            if (check(TokenType.OP_LPARENT)) {
                var pars = parseParameters();
                if (check(TokenType.OP_RPARENT)) {
                    if (check(TokenType.OP_COLON)) {
                        var type = parseType();
                        if (check(TokenType.OP_ASSIGN)) {
                            var expr = parseExpression();
                            return new FunDef(
                                    new Position(
                                            pos.start,
                                            expr.position.end
                                    ),
                                    symbol.lexeme,
                                    pars,
                                    type,
                                    expr
                            );
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
        return null; // Nikoli se ne izvede. Ali se bo vrnila `FnDef` ali pa bo javljena napaka prevajalnika.
    }

    private List<FunDef.Parameter> parseParameters() {
        dump("parameters -> parameter parameters2");
        var pars = new ArrayList<FunDef.Parameter>();
        pars.add(parseParameter());
        return parseParameters2(pars);
    }

    private List<FunDef.Parameter> parseParameters2(List<FunDef.Parameter> pars) {
        if (check(TokenType.OP_COMMA)) {
            dump("parameters2 -> , parameters");
            pars.add(parseParameter());
            return parseParameters2(pars);
        } else {
            dump("parameters2 -> e");
            return pars;
        }
    }

    @SuppressWarnings("ConstantConditions")
    private FunDef.Parameter parseParameter() {
        dump("parameter -> identifier : type");
        var symbol = currentSymbol;
        if (check(TokenType.IDENTIFIER)) {
            if (check(TokenType.OP_COLON)) {
                var type = parseType();
                return new FunDef.Parameter(
                        new Position(
                                symbol.position.start,
                                type.position.end
                        ),
                        symbol.lexeme,
                        type
                );
            } else
                easyReport("':'");
        } else {
            easyReport("IDENTIFIER");
        }
        return null; // Nikoli se ne izvede. Ali se bo vrnil `Parameter` ali pa bo javljena napaka prevajalnika.
    }

    private Expr parseExpression() {
        dump("expression -> logical_ior_expression expression2");
        var expr = parseLogicalIorExpression();
        return parseExpression2(expr);
    }

    private Expr parseExpression2(Expr expr) {
        if(check(TokenType.OP_LBRACE)) {
            dump("expression2 -> { WHERE definitions }");
            if (check(TokenType.KW_WHERE)) {
                var defs = parseDefinitions();
                var symbol = currentSymbol;
                if (!check(TokenType.OP_RBRACE)) {
                    easyReport("'}'");
                }
                return new Where(
                        new Position(
                                expr.position.start,
                                symbol.position.end
                        ),
                        expr,
                        defs
                );
            } else
                easyReport("'where'");
        } else {
            dump("expression2 -> e");
            return expr;
        }
        return null; // Nikoli se ne izvede. Ali se bo vrnil `Expr` ali pa bo javljena napaka prevajalnika.
    }

    private Expr parseLogicalIorExpression() {
        dump("logical_ior_expression -> logical_and_expression logical_ior_expression2");
        var left = parseLogicalAndExpression();
        return parseLogicalIorExpression2(left);
    }

    private Expr parseLogicalIorExpression2(Expr left) {
        if (check(TokenType.OP_OR)) {
            dump("logical_ior_expression2 -> | logical_ior_expression");
            var right = parseLogicalAndExpression();
            var bin = new Binary(
                    new Position(
                        left.position.start, right.position.end
                    ),
                    left,
                    Binary.Operator.OR,
                    right
            );
            return parseLogicalIorExpression2(bin);
        } else {
            dump("logical_ior_expression2 -> e");
        }
        return left;
    }

    private Expr parseLogicalAndExpression() {
        dump("logical_and_expression -> compare_expression logical_and_expression2");
        var left = parseCompareExpression();
        return parseLogicalAndExpression2(left);
    }

    private Expr parseLogicalAndExpression2(Expr left) {
        if (check(TokenType.OP_AND)) {
            dump("logical_and_expression2 -> & compare_expression");
            var right = parseCompareExpression();
            var bin = new Binary(
                    new Position(
                            left.position.start, right.position.end
                    ),
                    left,
                    Binary.Operator.AND,
                    right
            );
            return parseLogicalAndExpression2(bin);
        } else {
            dump("logical_and_expression2 -> e");
        }
        return left;
    }

    private Expr parseCompareExpression() {
        dump("compare_expression -> additive_expression compare_expression2");
        var left = parseAdditiveExpression();
        return parseCompareExpression2(left);
    }

    private Expr parseCompareExpression2(Expr left) {
        if (check(TokenType.OP_EQ)) {
            dump("compare_expression2 -> == additive_expression");
            var right = parseAdditiveExpression();
            return new Binary(
                    new Position(
                            left.position.start,
                            right.position.end
                    ),
                    left,
                    Binary.Operator.EQ,
                    right
            );
        } else if (check(TokenType.OP_NEQ)) {
            dump("compare_expression2 -> != additive_expression");
            var right = parseAdditiveExpression();
            return new Binary(
                    new Position(
                            left.position.start,
                            right.position.end
                    ),
                    left,
                    Binary.Operator.NEQ,
                    right
            );
        } else if (check(TokenType.OP_LEQ)) {
            dump("compare_expression2 -> <= additive_expression");
            var right = parseAdditiveExpression();
            return new Binary(
                    new Position(
                            left.position.start,
                            right.position.end
                    ),
                    left,
                    Binary.Operator.LEQ,
                    right
            );
        } else if (check(TokenType.OP_GEQ)) {
            dump("compare_expression2 -> >= additive_expression");
            var right = parseAdditiveExpression();
            return new Binary(
                    new Position(
                            left.position.start,
                            right.position.end
                    ),
                    left,
                    Binary.Operator.GEQ,
                    right
            );
        } else if (check(TokenType.OP_LT)) {
            dump("compare_expression2 -> < additive_expression");
            var right = parseAdditiveExpression();
            return new Binary(
                    new Position(
                            left.position.start,
                            right.position.end
                    ),
                    left,
                    Binary.Operator.LT,
                    right
            );
        } else if (check(TokenType.OP_GT)) {
            dump("compare_expression2 -> > additive_expression");
            var right = parseAdditiveExpression();
            return new Binary(
                    new Position(
                            left.position.start,
                            right.position.end
                    ),
                    left,
                    Binary.Operator.GT,
                    right
            );
        } else {
            dump("compare_expression2 -> e");
        }
        return left;
    }

    private Expr parseAdditiveExpression() {
        dump("additive_expression -> multiplicative_expression additive_expression2");
        var left = parseMultiplicativeExpression();
        return parseAdditiveExpression2(left);
    }

    private Expr parseAdditiveExpression2(Expr left) {
        if (check(TokenType.OP_ADD)) {
            dump("additive_expression2 -> + additive_expression");
            var right = parseMultiplicativeExpression();
            var bin = new Binary(
                    new Position(
                            left.position.start,
                            right.position.end
                    ),
                    left,
                    Binary.Operator.ADD,
                    right
            );
            return parseAdditiveExpression2(bin);
        } else if (check(TokenType.OP_SUB)) {
            dump("additive_expression2 -> - additive_expression");
            var right = parseMultiplicativeExpression();
            var bin = new Binary(
                    new Position(
                            left.position.start,
                            right.position.end
                    ),
                    left,
                    Binary.Operator.SUB,
                    right
            );
            return parseAdditiveExpression2(bin);
        } else {
            dump("additive_expression2 -> e");
        }
        return left;
    }

    private Expr parseMultiplicativeExpression() {
        dump("multiplicative_expression -> prefix_expression multiplicative_expression2");
        var left = parsePrefixExpression();
        return parseMultiplicativeExpression2(left);
    }

    private Expr parseMultiplicativeExpression2(Expr left) {
        if (check(TokenType.OP_MUL)) {
            dump("multiplicative_expression2 -> * multiplicative_expression");
            var right = parsePrefixExpression();
            var bin = new Binary(
                    new Position(
                            left.position.start,
                            right.position.end
                    ),
                    left,
                    Binary.Operator.MUL,
                    right
            );
            return parseMultiplicativeExpression2(bin);
        } else if (check(TokenType.OP_DIV)) {
            dump("multiplicative_expression2 -> / multiplicative_expression");
            var right = parsePrefixExpression();
            var bin = new Binary(
                    new Position(
                            left.position.start,
                            right.position.end
                    ),
                    left,
                    Binary.Operator.DIV,
                    right
            );
            return parseMultiplicativeExpression2(bin);
        } else if (check(TokenType.OP_MOD)) {
            dump("multiplicative_expression2 -> % multiplicative_expression");
            var right = parsePrefixExpression();
            var bin = new Binary(
                    new Position(
                            left.position.start,
                            right.position.end
                    ),
                    left,
                    Binary.Operator.MOD,
                    right
            );
            return parseMultiplicativeExpression2(bin);
        } else {
            dump("multiplicative_expression2 -> e");
        }
        return left;
    }

    private Expr parsePrefixExpression() {
        var symbol = currentSymbol;
        if (check(TokenType.OP_ADD)) {
            dump("prefix_expression -> + prefix_expression");
            var expr = parsePrefixExpression();
            return new Unary(
                    new Position(
                            symbol.position.start,
                            expr.position.end
                    ),
                    expr,
                    Unary.Operator.ADD
            );
        } else if (check(TokenType.OP_SUB)) {
            dump("prefix_expression -> - prefix_expression");
            var expr = parsePrefixExpression();
            return new Unary(
                    new Position(
                            symbol.position.start,
                            expr.position.end
                    ),
                    expr,
                    Unary.Operator.SUB
            );
        } else if (check(TokenType.OP_NOT)) {
            dump("prefix_expression -> ! prefix_expression");
            var expr = parsePrefixExpression();
            return new Unary(
                    new Position(
                            symbol.position.start,
                            expr.position.end
                    ),
                    expr,
                    Unary.Operator.NOT
            );
        } else {
            dump("prefix_expression -> postfix_expression");
            return parsePostfixExpression();
        }
    }

    private Expr parsePostfixExpression() {
        dump("postfix_expression -> atom_expression postfix_expression2");
        var left = parseAtomExpression();
        return parsePostfixExpression2(left);
    }

    private Expr parsePostfixExpression2(Expr left) {
        if (check(TokenType.OP_LBRACKET)) {
            dump("postfix_expression2 -> [ expression ] postfix_expression2");
            var right = parseExpression();
            var symbol = currentSymbol;
            if (check(TokenType.OP_RBRACKET)) {
                var bin = new Binary(
                        new Position(
                                left.position.start,
                                symbol.position.end
                        ),
                        left,
                        Binary.Operator.ARR,
                        right
                );
                return parsePostfixExpression2(bin);
            } else
                easyReport("']'");
        }
        return left;
    }

    private Expr parseAtomExpression() {
        var symbol = currentSymbol;
        if (check(TokenType.C_LOGICAL)) {
            dump("atom_expression -> log_constant");
            return new Literal(
                    symbol.position,
                    symbol.lexeme,
                    Atom.Type.LOG
            );
        } else if (check(TokenType.C_INTEGER)) {
            dump("atom_expression -> int_constant");
            return new Literal(
                    symbol.position,
                    symbol.lexeme,
                    Atom.Type.INT
            );
        } else if (check(TokenType.C_STRING)) {
            dump("atom_expression -> str_constant");
            return new Literal(
                    symbol.position,
                    symbol.lexeme,
                    Atom.Type.STR
            );
        } else if (check(TokenType.OP_LPARENT)) {
            dump("atom_expression -> ( expressions )");
            var exprs = parseExpressions();
            var symbol2 = currentSymbol;
            if (!check(TokenType.OP_RPARENT)) {
                easyReport("')'");
            }
            return new Block(
                    new Position(
                            symbol.position.start,
                            symbol2.position.end
                    ),
                    exprs
            );
        } else if (check(TokenType.OP_LBRACE)) {
            dump("atom_expression -> { atom_expression3 }");
            var expr = parseAtomExpression3(symbol.position);
            if (!check(TokenType.OP_RBRACE)) {
                easyReport("'}'");
            }
            return expr;
        } else if (check(TokenType.IDENTIFIER)) {
            dump("atom_expression -> identifier atom_expression2");
            return parseAtomExpression2(
                    symbol.position,
                    new Name(
                            symbol.position,
                            symbol.lexeme
                    )
            );
        } else {
            easyReport("CONSTANT_LOGICAL or CONSTANT_INTEGER or CONSTANT_STRING or IDENTIFIER or '{' or '('");
            return null; // Nikoli se ne izvede. Ali se bo vrnil `Expr` ali pa bo javljena napaka prevajalnika.
        }
    }

    private Expr parseAtomExpression2(Position pos, Name name) {
        if (check(TokenType.OP_LPARENT)) {
            dump("atom_expression2 -> ( expressions )");
            var args = parseExpressions();
            var symbol = currentSymbol;
            if (!check(TokenType.OP_RPARENT))
                easyReport("')'");
            return new Call(
                    new Position(
                            pos.start,
                            symbol.position.end
                    ),
                    args,
                    name.name
            );
        } else {
            dump("atom_expression2 -> e");
            return name;
        }
    }

    private Expr parseAtomExpression3(Position pos) {
        if (check(TokenType.KW_IF)) {
            dump("atom_expression3 -> if expression then expression atom_expression4");
            var cond = parseExpression();
            if (check(TokenType.KW_THEN)) {
                var thenExpr = parseExpression();
                var ifThenElse = new IfThenElse(
                        new Position(
                                pos.start,
                                currentSymbol.position.end
                        ),
                        cond,
                        thenExpr
                );
                return parseAtomExpression4(pos, ifThenElse);
            } else
                easyReport("'then'");
        } else if (check(TokenType.KW_WHILE)) {
            dump("atom_expression3 -> while expression : expression");
            var cond = parseExpression();
            if (check(TokenType.OP_COLON)) {
                var body = parseExpression();
                return new While(
                        new Position(
                                pos.start,
                                currentSymbol.position.end
                        ),
                        cond,
                        body
                );
            } else
                easyReport("':'");
        } else if (check(TokenType.KW_FOR)) {
            dump("atom_expression3 -> for identifier = expression , expression , expression : expression");
            var name = new Name(
                    currentSymbol.position,
                    currentSymbol.lexeme
            );
            if (check(TokenType.IDENTIFIER)) {
                if (check(TokenType.OP_ASSIGN)) {
                    var low = parseExpression();
                    if (check(TokenType.OP_COMMA)) {
                        var high = parseExpression();
                        if (check(TokenType.OP_COMMA)) {
                            var step = parseExpression();
                            if (check(TokenType.OP_COLON)) {
                                var body = parseExpression();
                                return new For(
                                        new Position(
                                                pos.start,
                                                currentSymbol.position.end
                                        ),
                                        name,
                                        low,
                                        high,
                                        step,
                                        body
                                );
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
            var left = parseExpression();
            if (check(TokenType.OP_ASSIGN)) {
                var right = parseExpression();
                return new Binary(
                        new Position(
                                pos.start,
                                currentSymbol.position.end
                        ),
                        left,
                        Binary.Operator.ASSIGN,
                        right
                );
            } else {
                easyReport("'='");
            }
        }
        return null; // Nikoli se ne izvede. Ali se bo vrnil `Expr` ali pa bo javljena napaka prevajalnika.
    }

    private Expr parseAtomExpression4(Position pos, IfThenElse ifThenElse) {
        if (check(TokenType.KW_ELSE)) {
            dump("atom_expression4 -> else expression");
            var elseExpr = parseExpression();
            return new IfThenElse(
                    new Position(
                            pos.start,
                            currentSymbol.position.end
                    ),
                    ifThenElse.condition,
                    ifThenElse.thenExpression,
                    elseExpr
            );
        } else {
            dump("atom_expression4 -> e");
            return ifThenElse;
        }
    }

    private List<Expr> parseExpressions() {
        dump("expressions -> expression expressions2");
        var exprs = new ArrayList<Expr>();
        exprs.add(parseExpression());
        return parseExpressions2(exprs);
    }

    private List<Expr> parseExpressions2(List<Expr> exprs) {
        if (check(TokenType.OP_COMMA)) {
            dump("expressions2 -> , expression");
            exprs.add(parseExpression());
            return parseExpressions2(exprs);
        } else {
            dump("expressions2 -> e");
            return exprs;
        }
    }

    @SuppressWarnings("ConstantConditions")
    private VarDef parseVariableDefinition(Position pos) {
        var name = currentSymbol.lexeme;
        dump("variable_definition -> var identifier : type");
        if (check(TokenType.IDENTIFIER)) {
            if (check(TokenType.OP_COLON)) {
                var type = parseType();
                return new VarDef(
                        new Position(
                                pos.start,
                                type.position.end
                        ),
                        name,
                        type
                );
            } else
                easyReport("':'");
        } else {
            easyReport("IDENTIFIER");
        }
        return null; // Nikoli se ne izvede. Ali se bo vrnil `VarDef` ali pa bo javljena napaka prevajalnika.
    }

    /**
     * Izpiše produkcijo na izhodni tok.
     */
    private void dump(String production) {
        //noinspection OptionalIsPresent
        if (productionsOutputStream.isPresent()) {
            productionsOutputStream.get().println(production);
        }
    }
}
