/**
 * @Author: turk
 * @Description: Leksikalni analizator.
 */

package compiler.lexer;

import static common.RequireNonNull.requireNonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.Report;

public class Lexer {
    /**
     * Izvorna koda.
     */
    private final String source;

    /**
     * Preslikava iz ključnih besed v vrste simbolov.
     */
    private final static Map<String, TokenType> keywordMapping;

    static {
        keywordMapping = new HashMap<>();
        for (var token : TokenType.values()) {
            var str = token.toString();
            if (str.startsWith("KW_")) {
                keywordMapping.put(str.substring("KW_".length()).toLowerCase(), token);
            }
            if (str.startsWith("AT_")) {
                keywordMapping.put(str.substring("AT_".length()).toLowerCase(), token);
            }
        }
    }

    /**
     * Ustvari nov analizator.
     * 
     * @param source Izvorna koda programa.
     */
    public Lexer(String source) {
        requireNonNull(source);
        this.source = source;
    }

    /**
     * Izvedi leksikalno analizo.
     * 
     * @return seznam leksikalnih simbolov.
     */
    public List<Symbol> scan() {
        var symbols = new ArrayList<Symbol>();


        /*
         * FAZA 1
         * Predprocesiranje vhoda
         * Znebitev komentarjev ter filtriranje neveljavnih znakov.
         */
        var source_stage1 = new StringBuilder(); // nova izvorna koda
        var inComment = false; // v komentarju?
        var lineCntr = 1; // štetje vrstice
        var colCntr = 0; // šteje stolpca v vrstici
        for (int i = 0; i < source.length(); i++) { // za vsak znak v izvorni kodi
            final char currentChar = source.charAt(i); // znak na trenutnem mestu
            colCntr++;

            // FILTRIRANJE NEVELJAVNIH ZNAKOV
            if ((int) currentChar > 127) {
                Report.error(
                        new Position(new Position.Location(lineCntr, colCntr), new Position.Location(lineCntr, colCntr)),
                        "LEX: Character \"" + currentChar + "\" is not valid. Only ASCII characters are valid."
                );
            }

            if (currentChar == (char) 35) // char == "#"
            {
                inComment = true;
            }
            else if (isNewline(currentChar)) {
                inComment = false;
                lineCntr++;
                colCntr = 0;
            }

            if (!inComment) { // prepiši znak v novo izvorno kodo, če le ta ni v komentarju
                source_stage1.append(currentChar);
            }
        }


        /*
         * FAZA 2
         * Tokenizacija (Žetonizacija)
         * Procesiranje vhoda v zaporedje žetonov.
         */
        lineCntr = 1; // ponastavimo števec vrstic
        colCntr = 0; // ponastavimo števec stolpcev
        var wordLocBegin = new int[]{0, 0};

        var word = new StringBuilder(); // trenuten leksem
        var midWord = false; // sredi leksema?
        String wordTokenClass = "NONE"; // C_INTEGER || C_STRING || OPERAND || OTHER || NONE

        source_stage1.append(" ");
        for (int i = 0; i < source_stage1.length(); i++) { // za vsak znak predprocesirane izvorne kode
            final char currentChar = source_stage1.charAt(i); // trenuten znak v izvorni kodi
            colCntr++;

            if (!midWord) { // na začetku definiramo, kaj trenutna beseda sploh lahko je.
                if (isNumber(currentChar)) {
                    wordTokenClass = "C_INTEGER";
                    word.append(currentChar);
                } else if ((int) currentChar == 39) {
                    wordTokenClass = "C_STRING";
                } else if (isSpecial(currentChar)) {
                    wordTokenClass = "OPERAND";
                    word.append(currentChar);
                } else if (isLetter(currentChar) || currentChar == '_') { // Tukaj padejo vsi ostali tipi (ker so vsi na nek način posebni)
                    wordTokenClass = "OTHER";
                    word.append(currentChar);
                } else if (isWhitespace(currentChar)) {
                    if (isNewline(currentChar)) {
                        lineCntr++;
                        colCntr = 0;
                    } else if (isTabulator(currentChar)) {
                        colCntr+=3;
                    }
                    continue;
                } else {
                    Report.error(
                            new Position(new Position.Location(lineCntr, colCntr), new Position.Location(lineCntr, colCntr)),
                            "LEX: Illegal start of token."
                    );
                }
                midWord = true;
                wordLocBegin = new int[]{lineCntr, colCntr};
                continue;
            }

            switch (wordTokenClass) {
                /*
                 * SESTAVLJANJE STRING KONSTANTE
                 */
                case "C_STRING":
                    if (currentChar == '\'') {
                        if (source_stage1.charAt(i + 1) == '\'') {
                            word.append("'");
                            i++;
                            colCntr++;
                            continue;
                        }
                        symbols.add(
                                new Symbol(
                                        new Position.Location(wordLocBegin[0], wordLocBegin[1]),
                                        new Position.Location(lineCntr, colCntr),
                                        TokenType.C_STRING,
                                        word.toString()
                                )
                        );
                        midWord = false;
                        wordTokenClass = "NONE";
                        word.setLength(0);
                    } else
                        word.append(currentChar);
                    break;

                /*
                 * SESTAVLJANJE INTEGER KONSTANTE
                 */
                case "C_INTEGER":
                    if (!isNumber(currentChar)) {
                        symbols.add(
                                new Symbol(
                                        new Position.Location(wordLocBegin[0], wordLocBegin[1]),
                                        new Position.Location(lineCntr, colCntr - 1),
                                        TokenType.C_INTEGER,
                                        word.toString()
                                )
                        );
                        midWord = false;
                        wordTokenClass = "NONE";
                        i--;
                        colCntr--;
                        word.setLength(0);
                    } else
                        word.append(currentChar);
                    break;

                /*
                 * SESTAVLJANJE OPERANDA
                 */
                case "OPERAND":
                    boolean isLong; // dvo-mesten operand?

                    switch ("" + word + currentChar) {
                        case "==" -> {
                            symbols.add(
                                    new Symbol(
                                            new Position.Location(wordLocBegin[0], wordLocBegin[1]),
                                            new Position.Location(lineCntr, colCntr),
                                            TokenType.OP_EQ,
                                            "" + word + currentChar
                                    )
                            );
                            isLong = true;
                        }
                        case "!=" -> {
                            symbols.add(
                                    new Symbol(
                                            new Position.Location(wordLocBegin[0], wordLocBegin[1]),
                                            new Position.Location(lineCntr, colCntr),
                                            TokenType.OP_NEQ,
                                            "" + word + currentChar
                                    )
                            );
                            isLong = true;
                        }
                        case "<=" -> {
                            symbols.add(
                                    new Symbol(
                                            new Position.Location(wordLocBegin[0], wordLocBegin[1]),
                                            new Position.Location(lineCntr, colCntr),
                                            TokenType.OP_LEQ,
                                            "" + word + currentChar
                                    )
                            );
                            isLong = true;
                        }
                        case ">=" -> {
                            symbols.add(
                                    new Symbol(
                                            new Position.Location(wordLocBegin[0], wordLocBegin[1]),
                                            new Position.Location(lineCntr, colCntr),
                                            TokenType.OP_GEQ,
                                            "" + word + currentChar
                                    )
                            );
                            isLong = true;
                        }
                        default -> isLong = false;
                    }

                    // PRESLIKAVA OPERANDA V TokenType
                    if (!isLong) { // če je dvo-mesten operand, je že bil dodan med simbole, zato preskočimo preslikavo
                        symbols.add(
                                new Symbol(
                                        new Position.Location(wordLocBegin[0], wordLocBegin[1]),
                                        new Position.Location(lineCntr, colCntr - 1),
                                        mapTokenType(word.toString().charAt(0)),
                                        word.toString()
                                )
                        );
                        i--;
                        colCntr--;
                    }

                    midWord = false;
                    wordTokenClass = "NONE";
                    word.setLength(0);
                    break;

                /*
                 * SESTAVLJANJE OSTALEGA
                 */
                case "OTHER":
                    if (!isIdentifierName(currentChar)) {
                        if (word.toString().equals("true") || word.toString().equals("false")) {
                            symbols.add(
                                    new Symbol(
                                            new Position.Location(wordLocBegin[0], wordLocBegin[1]),
                                            new Position.Location(lineCntr, colCntr - 1),
                                            TokenType.C_LOGICAL,
                                            word.toString()
                                    )
                            );
                        } else {
                            symbols.add(
                                    new Symbol(
                                            new Position.Location(wordLocBegin[0], wordLocBegin[1]),
                                            new Position.Location(lineCntr, colCntr - 1),
                                            keywordMapping.get(word.toString()) != null ? keywordMapping.get(word.toString()) : TokenType.IDENTIFIER,
                                            word.toString()
                                    )
                            );
                        }
                        midWord = false;
                        wordTokenClass = "NONE";
                        word.setLength(0);
                        i--;
                        colCntr--;
                        continue;
                    }
                    word.append(currentChar);
                    break;
            }

        }

        if (!wordTokenClass.equals("NONE")) {
            Report.error("LEX: Illegal end of file.");
        }

        symbols.add(
                new Symbol(
                        new Position.Location(wordLocBegin[0], colCntr - 1),
                        new Position.Location(lineCntr, colCntr - 1),
                        TokenType.EOF,
                        "$"
                )
        );

        /*
         * QUICK FIX za teste LEXER-ja
         * Testi predpostavljajo, da se vsi simboli končajo en znak kasneje kot se dejansko.
         * Tukaj je "quick fix", da lexer na pade testov.
         * Načeloma čisto nepotreben in je lahko brez komplikacij odstranjen.
         * (Treba je biti morebitno edino previden v naslednjih fazah prevajalnika, če so kakršne
         * odvisnosti - vendar ne bi smele biti ...saj, je le pozicija, nikogar kot programerja ne
         * bi *smela* zanimati.)
         */
        symbols.replaceAll(symbol -> new Symbol(
                new Position.Location(
                        symbol.position.start.line,
                        symbol.position.start.column
                ),
                new Position.Location(
                        symbol.position.end.line,
                        symbol.position.end.column + 1
                ),
                symbol.tokenType,
                symbol.lexeme
        ));

        return symbols;
    }

    /**
     * Dobi znak, ki predstavlja nek operand in vrne ustrezen <code>TokenType</code>.
     * @param givenChar Podan znak. Npr.: '+'
     * @return <code>TokenType</code> tip.
     */
    private TokenType mapTokenType(char givenChar) {
        return switch (givenChar) {
            case '+' -> TokenType.OP_ADD;
            case '-' -> TokenType.OP_SUB;
            case '*' -> TokenType.OP_MUL;
            case '/' -> TokenType.OP_DIV;
            case '%' -> TokenType.OP_MOD;
            case '&' -> TokenType.OP_AND;
            case '|' -> TokenType.OP_OR;
            case '!' -> TokenType.OP_NOT;
            case '<' -> TokenType.OP_LT;
            case '>' -> TokenType.OP_GT;
            case '(' -> TokenType.OP_LPARENT;
            case ')' -> TokenType.OP_RPARENT;
            case '[' -> TokenType.OP_LBRACKET;
            case ']' -> TokenType.OP_RBRACKET;
            case '{' -> TokenType.OP_LBRACE;
            case '}' -> TokenType.OP_RBRACE;
            case ':' -> TokenType.OP_COLON;
            case ';' -> TokenType.OP_SEMICOLON;
            case '.' -> TokenType.OP_DOT;
            case ',' -> TokenType.OP_COMMA;
            case '=' -> TokenType.OP_ASSIGN;
            default -> null;
        };
    }

    /**
     * Dobi znak in vrne <code>true</code>, če je znak od a-z ali A-Z in <code>false</code>, če ni.
     * @param givenChar Podan znak.
     * @return <code>boolean</code>
     */

    @SuppressWarnings("All")
    private boolean isLetter(char givenChar) {
        if ((int)givenChar >= 65 && (int)givenChar <= 90) // A to Z
            return true;

        if ((int)givenChar >= 97 && (int)givenChar <= 122) // a to z
            return true;

        return false;
    }

    /**
     * Dobi znak in vrne <code>true</code>, če je znak presledek, nova vrstica ali tabulator in <code>false</code>, če ni.
     * @param givenChar Podan znak.
     * @return <code>boolean</code>
     */
    private boolean isWhitespace(char givenChar) {
        return switch ((int) givenChar) {
            case 32, 9, 10, 13 -> true;
            default -> false;
        };
    }

    /**
     * Dobi znak in vrne <code>true</code>, če je znak cifra in <code>false</code>, če ni.
     * @param givenChar Podan znak.
     * @return <code>boolean</code>
     */
    private boolean isNumber(char givenChar) {
        return (int) givenChar >= 48 && (int) givenChar <= 57;
    }

    /**
     * Dobi znak in vrne <code>true</code>, če je znak eden izmed: + - * / % & | ! == != < > <= >= ( ) [ ] { } : ; . , = in <code>false</code>, če ni.
     * @param givenChar Podan znak.
     * @return <code>boolean</code>
     */
    private boolean isSpecial(char givenChar) {
        return switch ((int) givenChar) {
            case 43, 45, 42, 47, 37, 38, 124, 33, 61, 60, 62, 40, 41,
                    91, 93, 123, 125, 58, 59, 46, 44 -> true; // + - * / % & | ! == != < > <= >= ( ) [ ] { } : ; . , =
            default -> false;
        };
    }

    /**
     * Dobi znak in vrne <code>true</code>, če je znak za novo vrstico in <code>false</code>, če ni.
     * Za novo vrstico se šteje le '\n' oz. LF (line-feed), '\r' oz. CR (carriage-return) pa se ne.
     * @param givenChar Podan znak.
     * @return <code>boolean</code>
     */
    private boolean isNewline(char givenChar) {
        return (int) givenChar == 10;
    }

    /**
     * Dobi znak in vrne <code>true</code>, če je znak tabulator in <code>false</code>, če ni.
     * @param givenChar Podan znak.
     * @return <code>boolean</code>
     */
    private boolean isTabulator(char givenChar) {
        return (int) givenChar == 9;
    }

    /**
     * Dobi znak in vrne <code>true</code>, če je znak ali a-z ali A-Z ali _ in <code>false</code>, če ni.
     * @param givenChar Podan znak.
     * @return <code>boolean</code>
     */
    private boolean isIdentifierName(char givenChar) {
        return isNumber(givenChar) || isLetter(givenChar) || givenChar == '_';
    }
}

