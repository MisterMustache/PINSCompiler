/**
 * @ Author: turk
 * @ Description: Visitor, ki izpiše AST.
 */

package compiler.common;

import static common.StringUtil.*;
import static common.RequireNonNull.requireNonNull;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import common.VoidOperator;
import compiler.parser.ast.*;
import compiler.parser.ast.def.*;
import compiler.parser.ast.expr.*;
import compiler.parser.ast.type.Array;
import compiler.parser.ast.type.Atom;
import compiler.parser.ast.type.TypeName;

@SuppressWarnings("unused")
public class PrettyPrintVisitor1 implements Visitor {
    /**
     * Trenutna indentacija.
     */
    private int indent = 0;

    /**
     * Za koliko naj se indentacija poveča pri gnezdenju.
     */
    private final int increaseIndentBy;

    /**
     * Izhodni tok, na katerega se izpiše drevo.
     */
    @SuppressWarnings("FieldMayBeFinal")
    private PrintStream stream;

    /**
     * Ustvari novo instanco.
     * 
     * @param increaseIndentBy za koliko naj se poveča indentacija pri gnezdenju.
     * @param stream izhodni tok.
     */
    public PrettyPrintVisitor1(int increaseIndentBy, PrintStream stream) {
        requireNonNull(stream);
        this.increaseIndentBy = increaseIndentBy;
        this.stream = stream;
    }

    /**
     * Ustvari novo instanco.
     * Privzeta vrednost `increaseIndentBy` je 4.
     * 
     * @param stream izhodni tok.
     */
    @SuppressWarnings("UnusedConstructor")
    public PrettyPrintVisitor1(PrintStream stream) {
        requireNonNull(stream);
        this.increaseIndentBy = 4;
        this.stream = stream;

        //noinspection MismatchedQueryAndUpdateOfCollection
        var xs = new ArrayList<Integer>();
        //noinspection RedundantOperationOnEmptyContainer,ResultOfMethodCallIgnored
        xs.stream()
            .map(t -> t * 2)
            .filter(t -> t % 2 != 0)
            .collect(Collectors.toList());
    }

    /**
     * Implementacija ``Visitor`` vmesnika:
     */

    @Override
    public void visit(Call call) {
        println("Call", call, call.name);
        inNewScope(() -> {
            call.arguments.forEach((arg) -> arg.accept(this));
        });
    }

    @Override
    public void visit(Binary binary) {
        println("Binary", binary, binary.operator.toString());
        inNewScope(() -> {
            binary.left.accept(this);
            binary.right.accept(this);
        });
    }

    @Override
    public void visit(Block block) {
        println("Block", block);
        inNewScope(() -> {
            block.expressions.forEach((expr) -> expr.accept(this));
        });
    }

    @Override
    public void visit(For forLoop) {
        println("For", forLoop);
        inNewScope(() -> {
            forLoop.counter.accept(this);
            forLoop.low.accept(this);
            forLoop.high.accept(this);
            forLoop.step.accept(this);
            forLoop.body.accept(this);
        });
    }

    @Override
    public void visit(Name name) {
        println("Name", name, name.name);
    }

    @Override
    public void visit(IfThenElse ifThenElse) {
        println("IfThenElse", ifThenElse);
        inNewScope(() -> {
            ifThenElse.condition.accept(this);
            ifThenElse.thenExpression.accept(this);
            ifThenElse.elseExpression.ifPresent(expr -> expr.accept(this));
        });
    }

    @Override
    public void visit(Literal literal) {
        println("Literal", literal, literal.type.toString(), "(", literal.value, ")");
    }

    @Override
    public void visit(Unary unary) {
        println("Unary", unary, unary.operator.toString());
        inNewScope(() -> {
            unary.expr.accept(this);
        });
    }

    @Override
    public void visit(While whileLoop) {
        println("While", whileLoop);
        inNewScope(() -> {
            whileLoop.condition.accept(this);
            whileLoop.body.accept(this);
        });
    }

    @Override
    public void visit(Where where) {
        println("Where", where);
        inNewScope(() -> {
            where.defs.accept(this);
            where.expr.accept(this);
        });
    }

    // Definicije:

    @Override
    public void visit(Defs defs) {
        println("Defs", defs);
        inNewScope(() -> {
            defs.definitions.forEach((def) -> def.accept(this));
        });
    }

    @Override
    public void visit(FunDef funDef) {
        println("FunDef", funDef, funDef.name);
        inNewScope(() -> {
            visit(funDef.parameters);
            funDef.type.accept(this);
            funDef.body.accept(this);
        });
    }

    @Override
    public void visit(TypeDef typeDef) {
        println("TypeDef", typeDef, typeDef.name);
        inNewScope(() -> {
            typeDef.type.accept(this);
        });
    }

    @Override
    public void visit(VarDef varDef) {
        println("VarDef", varDef, varDef.name);
        inNewScope(() -> {
            varDef.type.accept(this);
        });
    }

    @Override
    public void visit(FunDef.Parameter parameter) {
        println("Parameter", parameter, parameter.name);
        inNewScope(() -> {
            parameter.type.accept(this);
        });
    }

    // Tipi:

    @Override
    public void visit(Array array) {
        println("Array", array);
        inNewScope(() -> {
            print("[", Integer.toString(array.size), "]\n");
            array.type.accept(this);
        });
    }

    @Override
    public void visit(Atom atom) {
        println("Atom", atom, atom.type.toString());
    }

    @Override
    public void visit(TypeName name) {
        println("TypeName", name, name.identifier);
    }

    // ----------------------------------

    public <T extends Ast> void visit(List<T> nodes) {
        nodes.forEach((node) -> {
            node.accept(this);
        });
    }
    
    // ----------------------------------

    /**
     * Znotraj gnezdenega bloka izvede operator `op`.
     * Gnezdenje bloka poveča trenutno indentacijo,
     * izvede operator `op`, nato pa indentacijo
     * nastavi na prejšnjo vrednost.
     */
    private void inNewScope(VoidOperator op) {
        requireNonNull(op);
        indent += increaseIndentBy;
        op.apply();
        indent -= increaseIndentBy;
    }

    private void print(String... args) {
        stream.print(indented("", indent));
        for (var arg : args) {
            stream.print(arg);
        }
    }

    private void println(String line, Ast node, String... s) {
        print(line, " ", node.position.toString());
        if (s.length > 0) {
            stream.print(": ");
        }
        for (var a : s) {
            stream.print(a);
        }
        stream.println();
    }
}
