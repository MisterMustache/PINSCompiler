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
    public List<Symbol> scan() throws IllegalCharacterException {
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
                throw new IllegalCharacterException("Znak \"" + currentChar + "\", ki se nahaja na " + lineCntr + ":" +
                        colCntr + " ni veljaven znak. Dovoljeni so le znaki ASCII tabele.");
            }

            if (currentChar == (char) 35) // char == "#"
            {
                inComment = true;
            }
            else if (currentChar == (char) 10 || currentChar == (char) 13) { // char == *new line*
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
        var tokens = new ArrayList<String>(); // seznam žetonov
        var word = new StringBuilder(); // trenuten leksem
        var midWord = true; // sredi leksema?
        for (int i = 0; i < source_stage1.length(); i++) { // za vsak znak predprocesirane izvorne kode
            final char currentChar = source_stage1.charAt(i); // trenuten znak v izvorni kodi

            if (isWhitespace(currentChar))
                midWord = false;
            else {
                midWord = true;
                word.append(currentChar);
            }

            if (!midWord && !word.isEmpty()) {
                tokens.add(word.toString());
                word.setLength(0);
            }

            // TODO
        }


        return symbols;
    }

    private TokenType classifyToken(String token) {
        // TODO
        return TokenType.EOF;
    }

    private boolean isLetter(char givenChar) {
        if ((int)givenChar >= 65 && (int)givenChar <= 90) // A to Z
            return true;

        if ((int)givenChar >= 97 && (int)givenChar <= 122) // a to z
            return true;

        return false;
    }

    private boolean isWhitespace(char givenChar) {
        return switch ((int) givenChar) {
            case 32, 9, 10, 13 -> true;
            default -> false;
        };
    }

    private boolean isNumber(char givenChar) {
        return (int) givenChar >= 48 && (int) givenChar <= 57;
    }
}

