/**
 * @Description: Definicija spremenljivke.
 */

package compiler.parser.ast.def;

import static common.RequireNonNull.requireNonNull;

import compiler.common.Visitor;
import compiler.lexer.Position;
import compiler.parser.ast.type.Type;

public class VarDef extends Def {
    /**
     * Tip.
     */
    public final Type type;

    public VarDef(Position position, String name, Type type) {
        super(position, name);
        requireNonNull(type);
        this.type = type;
    }

	@Override public void accept(Visitor visitor) { visitor.visit(this); }
}
