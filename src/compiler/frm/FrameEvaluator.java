/**
 * @ Author: turk
 * @ Description: Analizator klicnih zapisov.
 */

package compiler.frm;

import static common.RequireNonNull.requireNonNull;

import common.Constants;
import compiler.common.Visitor;
import compiler.parser.ast.def.*;
import compiler.parser.ast.def.FunDef.Parameter;
import compiler.parser.ast.expr.*;
import compiler.parser.ast.type.Array;
import compiler.parser.ast.type.Atom;
import compiler.parser.ast.type.TypeName;
import compiler.seman.common.NodeDescription;
import compiler.seman.type.type.Type;

import java.util.Stack;

public class FrameEvaluator implements Visitor {
    /**
     * Opis definicij funkcij in njihovih klicnih zapisov.
     */
    private final NodeDescription<Frame> frames;

    /**
     * Opis definicij spremenljivk in njihovih dostopov.
     */
    private final NodeDescription<Access> accesses;

    /**
     * Opis vozlišč in njihovih definicij.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final NodeDescription<Def> definitions;

    /**
     * Opis vozlišč in njihovih podatkovnih tipov.
     */
    private final NodeDescription<Type> types;

    private final Stack<Frame.Builder> builders;

    public FrameEvaluator(
        NodeDescription<Frame> frames, 
        NodeDescription<Access> accesses,
        NodeDescription<Def> definitions,
        NodeDescription<Type> types
    ) {
        requireNonNull(frames, accesses, definitions, types);
        this.builders = new Stack<>();
        this.frames = frames;
        this.accesses = accesses;
        this.definitions = definitions;
        this.types = types;
    }

    @Override
    public void visit(Call call) {
        // Velikost argumenta je vedno WordSize, zato je vseh skupaj = WS * število argumentov (+ 1 WS za SL)
        this.builders.peek().addFunctionCall(call.arguments.size() * Constants.WordSize + Constants.WordSize);
    }


    @Override
    public void visit(Binary binary) {
        binary.left.accept(this);
        binary.right.accept(this);
    }


    @Override
    public void visit(Block block) {
        block.expressions.forEach(expr -> expr.accept(this));
    }


    @Override
    public void visit(For forLoop) {
        forLoop.body.accept(this);
    }


    @Override
    public void visit(Name name) {
    }


    @Override
    public void visit(IfThenElse ifThenElse) {
        ifThenElse.condition.accept(this);
        ifThenElse.thenExpression.accept(this);
        ifThenElse.elseExpression.ifPresent(expr -> expr.accept(this));
    }


    @Override
    public void visit(Literal literal) {
    }


    @Override
    public void visit(Unary unary) {
        unary.expr.accept(this);
    }


    @Override
    public void visit(While whileLoop) {
        whileLoop.body.accept(this);
    }


    @Override
    public void visit(Where where) {
        for (var def : where.defs.definitions)
            def.accept(this);
        where.expr.accept(this);
    }


    @Override
    public void visit(Defs defs) {
        for (var def : defs.definitions) {
            def.accept(this);
        }
    }


    @Override
    public void visit(FunDef funDef) {
        // Z vsako definicijo ustvarimo nov builder za okvir
        Frame.Builder builder;
        // Če smo na globalnem nivoju je okvir poimenovan
        if (this.builders.isEmpty()) {
            builder = new Frame.Builder(Frame.Label.named(funDef.name), 1);
        }
        // če pa smo v gnezdenem pa anonimen
        else {
            builder = new Frame.Builder(Frame.Label.nextAnonymous(), this.builders.size() + 1);
        }

        // Builderja dodamo na sklad, ker ni nujno, da ne bomo vmes morali ustvariti okvir za anonimno funkcijo
        this.builders.push(builder);
        builder.addParameter(Constants.WordSize); // SL
        funDef.parameters.forEach(parameter -> parameter.accept(this));
        funDef.body.accept(this);
        // Okvir shranimo
        this.frames.store(builder.build(), funDef);
        // in odstranimo builderja, ker smo z njim zaključili.
        this.builders.pop();
    }


    @Override
    public void visit(TypeDef typeDef) {
    }


    @Override
    public void visit(VarDef varDef) {
        // varDef je na globalnem nivoju
        if (this.builders.size() == 0) {
            this.accesses.store(
                    new Access.Global(
                            this.types.valueFor(varDef).get().sizeInBytes(),
                            Frame.Label.named(varDef.name)
                    ), varDef
            );
        } else {
            var builder = this.builders.peek();
            this.types.valueFor(varDef).ifPresent(type -> {
                int offset = builder.addLocalVariable(type.sizeInBytes());
                this.accesses.store(
                        new Access.Local(
                                type.sizeInBytes(),
                                offset,
                                builder.staticLevel
                        ), varDef
                );
            });
        }
    }


    @Override
    public void visit(Parameter parameter) {
        var builder = this.builders.peek();

        this.types.valueFor(parameter).ifPresent(type -> {
            int size = type.sizeInBytesAsParam();
            int offset = builder.addParameter(size);

            this.accesses.store(
                    new Access.Parameter(
                            size,
                            offset,
                            builder.staticLevel
                    ), parameter
            );
        });
    }


    @Override
    public void visit(Array array) {
    }


    @Override
    public void visit(Atom atom) {
    }


    @Override
    public void visit(TypeName name) {
    }
}
