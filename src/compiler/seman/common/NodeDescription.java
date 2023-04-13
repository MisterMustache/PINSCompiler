/**
 * @ Author: turk
 * @ Description: Preslikava iz vozlišč abstraktnega 
 * sintaksnega drevesa v vrednosti poljubnega tipa.
 */

package compiler.seman.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import compiler.parser.ast.Ast;

public class NodeDescription<T> {
    private final Map<Ast, T> storage = new HashMap<>();

    /**
     * Vrne vrednost za podano vozlišče, če je le-ta
     * prisotna.
     */
    public Optional<T> valueFor(Ast node) {
        return Optional.ofNullable(storage.get(node));
    }

    /**
     * Shrani vrednost za vozlišče.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean store(T value, Ast forNode) {
        return storage.put(forNode, value) == null;
    }
}
