/**
 * @ Author: turk
 * @ Description: Preverjanje tipov.
 */

package compiler.seman.type;

import static common.RequireNonNull.requireNonNull;

import common.Report;
import compiler.common.Visitor;
import compiler.parser.ast.def.*;
import compiler.parser.ast.def.FunDef.Parameter;
import compiler.parser.ast.expr.*;
import compiler.parser.ast.type.*;
import compiler.seman.common.NodeDescription;
import compiler.seman.type.type.Type;
import stdlib.StandardFunctions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class TypeChecker implements Visitor {
    /**
     * Opis vozlišč in njihovih definicij.
     */
    private final NodeDescription<Def> definitions;

    /**
     * Opis vozlišč, ki jim priredimo podatkovne tipe.
     */
    private final NodeDescription<Type> types;

    private final HashSet<TypeDef> typeDefDefined;

    public TypeChecker(NodeDescription<Def> definitions, NodeDescription<Type> types) {
        requireNonNull(definitions, types);
        this.definitions = definitions;
        this.types = types;
        this.typeDefDefined = new HashSet<>();
    }

    @Override
    public void visit(Call call) {
        if (this.definitions.valueFor(call).isEmpty()) {
            if (StandardFunctions.exists(call.name)) {
                call.arguments.forEach(expr -> expr.accept(this));
                StandardFunctions.checkType(call, this.types);
                return;
            }
            Report.error(call.position, "SEM: Function '" + call.name + "' does not exist.");
            return;
        }

        if (!(this.definitions.valueFor(call).get() instanceof FunDef funDef))
            { Report.error(call.position, "SEM: '" + call.name + "' is not a function."); return; }

//        if (this.types.valueFor(call).get().asFunction().isEmpty())
//            { Report.error("SEM: Unexpected error. 'fun' is not present."); return; }
//        var function = this.definitions.valueFor(call).get().asFunction().get();

        if (funDef.parameters.size() != call.arguments.size()) Report.error(call.position, "SEM: The number" +
                " of passed arguments does not match the number of required parameters of function '" + call.name + "'.");

//        var parameters = function.parameters;
        var arguments = call.arguments;
        for (int i = 0; i < funDef.parameters.size(); i++) {
            if (this.types.valueFor(funDef.parameters.get(i)).isEmpty()) funDef.parameters.get(i).accept(this);
            if (this.types.valueFor(funDef.parameters.get(i)).isEmpty())
                Report.error(arguments.get(i).position, "SEM: Type of argument is not present.");
//            this.types.store(this.types.valueFor(arguments.get(i)).get(), arguments.get(i));

            if (this.types.valueFor(call.arguments.get(i)).isEmpty()) call.arguments.get(i).accept(this);
            if (this.types.valueFor(call.arguments.get(i)).isEmpty())
                Report.error(arguments.get(i).position, "SEM: Type of argument is not present.");

            var parType = this.types.valueFor(funDef.parameters.get(i)).get();
            var argType = this.types.valueFor(call.arguments.get(i)).get();
            if (!parType.equals(argType))
                Report.error(arguments.get(i).position, "SEM: Type mismatch. Expected '" + parType.asAtom().get().kind +
                        "', got '" + argType.asAtom().get().kind + "'.");
        }

        if (this.types.valueFor(funDef.type).isEmpty()) funDef.type.accept(this);

        // Če tip Expression-a še zmeraj ne obstaja potem je to napaka
        if (this.types.valueFor(funDef.type).isEmpty())
        { Report.error(funDef.type.position, "SEM: Type of 'fun' definition is not present."); return; }

        this.types.store(this.types.valueFor(funDef.type).get(), call);
    }

    @Override
    public void visit(Binary binary) {
        // Expression-a potrebujeta tipa
        // Če še nista izračunana se izračunata
        if (this.types.valueFor(binary.left).isEmpty()) binary.left.accept(this);
        if (this.types.valueFor(binary.right).isEmpty()) binary.right.accept(this);

        // Če tipa Expression-a še zmeraj ne obstajata potem je to napaka
        if (this.types.valueFor(binary.left).isEmpty())
        { Report.error(binary.left.position, "SEM: Type of Expression definition is not present."); return; }
        if (this.types.valueFor(binary.right).isEmpty())
        { Report.error(binary.right.position, "SEM: Type of Expression definition is not present."); return; }

        var left = this.types.valueFor(binary.left).get();
        var right = this.types.valueFor(binary.right).get();
        if (left.isLog() && right.isLog()) {
            switch (binary.operator) {
                case AND, OR, EQ, NEQ, LEQ, GEQ, LT, GT, ASSIGN -> this.types.store(left, binary);
                default -> Report.error(binary.position, "SEM: Incompatible operator. Expected from set " +
                        "{ logical AND, logical OR, equality, relational }, got '" + binary.operator + "' instead.");
            }
        } else if (left.isInt() && right.isInt()) {
            switch (binary.operator) {
                case ADD, SUB, MUL, DIV, MOD, ASSIGN -> this.types.store(left, binary);
                case EQ, NEQ, LEQ, GEQ, LT, GT -> this.types.store(new Type.Atom(Type.Atom.Kind.LOG), binary);
                default -> Report.error(binary.position, "SEM: Incompatible operator. Expected from set " +
                        "{ additive, multiplicative, equality, relational }, got '" + binary.operator + "' instead.");
            }
        } else if (left.isStr() && right.isStr()) {
            //noinspection SwitchStatementWithTooFewBranches
            switch (binary.operator) {
                case ASSIGN -> this.types.store(left, binary);
                default -> Report.error(binary.position, "SEM: Incompatible operator. Expected '=', got '" +
                        binary.operator + "' instead.");
            }
        } else if (binary.operator.equals(Binary.Operator.ARR) && left.asArray().isPresent() && right.isInt()) {
            this.types.store(left.asArray().get().type, binary);
        } else {
            Report.error(binary.position, "SEM: Incompatible types '" + left.asAtom().get().kind + "' and '" +
                    right.asAtom().get().kind + "'.");
        }

    }

    @Override
    public void visit(Block block) {
        block.expressions.forEach(expr -> {
            if (this.types.valueFor(expr).isEmpty()) expr.accept(this);
            if (this.types.valueFor(expr).isEmpty())
                Report.error(expr.position, "SEM: Type of Expression is not present.");
        });
        // TODO: Bug: Return Type NE more biti VOID
        var returnExpr = block.expressions.get(block.expressions.size() - 1);
//        if (this.types.valueFor(returnExpr).get().equals(new Type.Atom(Type.Atom.Kind.VOID))) {
//            Report.error(returnExpr.position, "SEM: Return Type of Block cannot not be VOID.");
//        }

        if (this.types.valueFor(returnExpr).isPresent())
            this.types.store(this.types.valueFor(returnExpr).get(), block);
        else
            Report.error(returnExpr.position, "SEM: Type of Block return Expression is not present.");
    }

    @Override
    public void visit(For forLoop) {
        if (this.types.valueFor(forLoop.counter).isEmpty()) forLoop.counter.accept(this);
        if (this.types.valueFor(forLoop.counter).isEmpty()) { Report.error(forLoop.counter.position, "SEM: Type of " +
                "Step Expression of 'for' is not present."); return; }

        if (this.types.valueFor(forLoop.body).isEmpty()) forLoop.body.accept(this);
        if (this.types.valueFor(forLoop.body).isEmpty()) { Report.error(forLoop.body.position, "SEM: Type of " +
                "Body Expression of 'for' is not present."); return; }

        if (this.types.valueFor(forLoop.low).isEmpty()) forLoop.low.accept(this);
        if (this.types.valueFor(forLoop.low).isEmpty()) { Report.error(forLoop.low.position, "SEM: Type of " +
                "Low Expression of 'for' is not present."); return; }

        if (this.types.valueFor(forLoop.high).isEmpty()) forLoop.high.accept(this);
        if (this.types.valueFor(forLoop.high).isEmpty()) { Report.error(forLoop.high.position, "SEM: Type of " +
                "High Expression of 'for' is not present."); return; }

        if (this.types.valueFor(forLoop.step).isEmpty()) forLoop.step.accept(this);
        if (this.types.valueFor(forLoop.step).isEmpty()) { Report.error(forLoop.step.position, "SEM: Type of " +
                "Step Expression of 'for' is not present."); return; }

        if (this.types.valueFor(forLoop.counter).get().isInt() &&
                this.types.valueFor(forLoop.low).get().isInt() &&
                this.types.valueFor(forLoop.high).get().isInt() &&
                this.types.valueFor(forLoop.step).get().isInt())
            this.types.store(new Type.Atom(Type.Atom.Kind.VOID), forLoop);
        else
            Report.error(forLoop.position, "SEM: Type mismatch. All 'for' components must be of type '" +
                    new Type.Atom(Type.Atom.Kind.INT).kind + "'.");
    }

    @Override
    public void visit(Name name) {
        var def = definitions.valueFor(name);
        if (def.isEmpty()) { Report.error(name.position, "SEM: Unknown symbol '" + name.name + "'."); return; }

        if (this.types.valueFor(def.get()).isEmpty()) def.get().accept(this);
        if (this.types.valueFor(def.get()).isEmpty()) { Report.error(def.get().position, "SEM: Type of " +
                "symbol is not present."); return; }

        this.types.store(this.types.valueFor(def.get()).get(), name);
    }

    @Override
    public void visit(IfThenElse ifThenElse) {
        if (ifThenElse.elseExpression.isPresent()) {
            if (this.types.valueFor(ifThenElse.elseExpression.get()).isEmpty())
                ifThenElse.elseExpression.get().accept(this);
            if (this.types.valueFor(ifThenElse.elseExpression.get()).isEmpty())
            { Report.error(ifThenElse.elseExpression.get().position, "SEM: Type of 'else' of 'if' " +
                    "is not present."); return; }
        }
        if (this.types.valueFor(ifThenElse.thenExpression).isEmpty())
            ifThenElse.thenExpression.accept(this);
        if (this.types.valueFor(ifThenElse.thenExpression).isEmpty())
        { Report.error(ifThenElse.thenExpression.position, "SEM: Type of 'then' of 'if' is " +
                "not present."); return; }

        if (this.types.valueFor(ifThenElse.condition).isEmpty())
            ifThenElse.condition.accept(this);
        if (this.types.valueFor(ifThenElse.condition).isEmpty())
        { Report.error(ifThenElse.condition.position, "SEM: Type of condition of 'if' is " +
                "not present."); return; }

        if (this.types.valueFor(ifThenElse.condition).get().isLog())
            this.types.store(new Type.Atom(Type.Atom.Kind.VOID), ifThenElse);
        else
            Report.error(ifThenElse.position, "SEM: Type mismatch. 'if' condition must be of type '" +
                    new Type.Atom(Type.Atom.Kind.LOG) + "'.");
    }

    @SuppressWarnings("UnnecessaryDefault")
    @Override
    public void visit(Literal literal) {
        // log_const, int_const in str_const so tipa LOGICAL, INTEGER in STRING, zaporedoma.
        //noinspection DuplicatedCode
        Type.Atom.Kind kind = switch (literal.type) {
            case LOG -> Type.Atom.Kind.LOG;
            case INT -> Type.Atom.Kind.INT;
            case STR -> Type.Atom.Kind.STR;
            default -> Type.Atom.Kind.VOID; // Atom NE more biti VOID => pokazatelj neznanega tipa.
        };
        if (kind.equals(Type.Atom.Kind.VOID))
            Report.error(literal.position, "SEM: Unknown Type of Literal.");
        this.types.store(new Type.Atom(kind), literal);
    }

    @Override
    public void visit(Unary unary) {
        // Expression potrebuje tip
        // Če še ni izračunan ga izračuna
        if (this.types.valueFor(unary.expr).isEmpty()) unary.expr.accept(this);

        // Če tip Expression-a še zmeraj ne obstaja potem je to napaka
        if (this.types.valueFor(unary.expr).isEmpty())
         { Report.error(unary.expr.position, "SEM: Type of Expression definition is not present."); return; }

        var expr = this.types.valueFor(unary.expr).get();
        if (unary.operator.equals(Unary.Operator.NOT)) {
            if (expr.isLog())
                this.types.store(expr, unary);
            else
                Report.error(unary.expr.position, "SEM: Type mismatch. Expected '" + Type.Atom.Kind.LOG +
                        "', got '" + expr.asAtom().get().kind + "'.");
        } else if (unary.operator.equals(Unary.Operator.ADD) || unary.operator.equals(Unary.Operator.SUB)) {
            if (expr.isInt())
                this.types.store(expr, unary);
            else
                Report.error(unary.expr.position, "SEM: Type mismatch. Expected '" + Type.Atom.Kind.INT +
                        "', got '" + expr.asAtom().get().kind + "'.");
        } else
            Report.error(unary.position, "SEM: Unary operator mismatch. Expected '" +
                    Unary.Operator.NOT + "' or '" + Unary.Operator.ADD + "' or '" + Unary.Operator.SUB + "', got '" +
                    unary.operator + "' instead.");

    }

    @Override
    public void visit(While whileLoop) {
        if (this.types.valueFor(whileLoop.condition).isEmpty()) whileLoop.condition.accept(this);
        if (this.types.valueFor(whileLoop.condition).isEmpty())
        { Report.error(whileLoop.condition.position, "SEM: Type of 'while' condition is " +
                "not present."); return; }

        if (this.types.valueFor(whileLoop.body).isEmpty()) whileLoop.body.accept(this);
        if (this.types.valueFor(whileLoop.body).isEmpty())
        { Report.error(whileLoop.body.position, "SEM: Type of 'while' body is " +
                "not present."); return; }

        if (this.types.valueFor(whileLoop.condition).get().isLog())
            this.types.store(new Type.Atom(Type.Atom.Kind.VOID), whileLoop);
        else
            Report.error(whileLoop.condition.position, "SEM: Type mismatch. 'while' condition must be of type '" +
                    new Type.Atom(Type.Atom.Kind.LOG) + "'.");
    }

    @Override
    public void visit(Where where) {
        if (this.types.valueFor(where.expr).isEmpty()) where.expr.accept(this);
        if (this.types.valueFor(where.expr).isEmpty())
        { Report.error(where.expr.position, "SEM: Type of 'where' is not present."); return; }
        where.defs.accept(this);
        this.types.store(this.types.valueFor(where.expr).get(), where);
    }

    @Override
    public void visit(Defs defs) {
        defs.definitions.forEach((def) -> def.accept(this));
    }

    @Override
    public void visit(FunDef funDef) {
        List<Type> parameterTypes = new ArrayList<>();
        for (var parameter : funDef.parameters) {
            // Parameter potrebuje tip
            // Če še ni izračunan ga izračuna
            if (this.types.valueFor(parameter.type).isEmpty()) parameter.type.accept(this);

            // Če tip Parameter-ja še zmeraj ne obstaja potem je to napaka
            if (this.types.valueFor(parameter.type).isEmpty())
                Report.error(parameter.position, "SEM: Type of parameter is not present.");

            // Shrani tip Parameter-ja
            this.types.store(this.types.valueFor(parameter.type).get(), parameter);
            // temp
            parameterTypes.add(this.types.valueFor(parameter.type).get());
        }

        // Vračilni Type potrebuje tip
        // Če še ni izračunan ga izračuna
        if (this.types.valueFor(funDef.type).isEmpty()) funDef.type.accept(this);

        // Če tip vračilnega Type-a še zmeraj ne obstaja potem je to napaka
        if (this.types.valueFor(funDef.type).isEmpty())
            Report.error(funDef.type.position, "SEM: Type of return type is not present.");

        // temp
        Type returnType = this.types.valueFor(funDef.type).get();
        // Shrani tip vračilnega Type-a
        this.types.store(returnType, funDef.type);

        // Telo Expression potrebuje tip
        // Če še ni izračunan ga izračuna
        if (this.types.valueFor(funDef.body).isEmpty()) funDef.body.accept(this);

        // Če tip Expression-a še zmeraj ne obstaja potem je to napaka
        if (this.types.valueFor(funDef.body).isEmpty())
            Report.error(funDef.body.position, "SEM: Type of Expression is not present.");

        // temp
        Type expressionType = this.types.valueFor(funDef.body).get();
        // Shrani tip Expression-a
        this.types.store(expressionType, funDef.body);

        if (!returnType.equals(this.types.valueFor(funDef.body).get()))
            Report.error(funDef.position, "SEM: 'fun' type mismatch. Expected '" + returnType.asAtom().get().kind + "', got '"
                    + expressionType.asAtom().get().kind + "'.");

        this.types.store(new Type.Function(parameterTypes, returnType), funDef);
    }

    @Override
    public void visit(TypeDef typeDef) {

        if (this.typeDefDefined.contains(typeDef))
        { Report.error(typeDef.position, "SEM: Type of 'typ' must not reference back to self."); return; }
        this.typeDefDefined.add(typeDef);

        // TypeDef potrebuje tip
        // Če še ni izračunan ga izračuna
        if (this.types.valueFor(typeDef.type).isEmpty()) typeDef.type.accept(this);

        // Če tip TypeDef-a še zmeraj ne obstaja potem je to napaka
        if (this.types.valueFor(typeDef.type).isEmpty())
            Report.error(typeDef.type.position, "SEM: Type of 'typ' is not present.");

        // Shrani tip TypeDef-a pod definicijo TypeDef-a
        this.types.store(this.types.valueFor(typeDef.type).get(), typeDef);

        this.typeDefDefined.remove(typeDef);
    }

    @Override
    public void visit(VarDef varDef) {
        // VarDef potrebuje tip
        // Če še ni izračunan ga izračuna
        if (this.types.valueFor(varDef.type).isEmpty()) varDef.type.accept(this);

        // Če tip VarDef-a še zmeraj ne obstaja potem je to napaka
        if (this.types.valueFor(varDef.type).isEmpty())
            Report.error(varDef.type.position, "SEM: Type of 'var' is not present.");

        // Shrani tip VarDef-a
        this.types.store(this.types.valueFor(varDef.type).get(), varDef);
    }

    @Override
    public void visit(Parameter parameter) {
        // Parameter potrebuje tip
        // Če še ni izračunan ga izračuna
        if (this.types.valueFor(parameter.type).isEmpty()) parameter.type.accept(this);

        // Če tip Parameter-ja še zmeraj ne obstaja potem je to napaka
        if (this.types.valueFor(parameter.type).isEmpty())
            Report.error(parameter.type.position, "SEM: Type of parameter is not present.");

        // Shrani tip Parameter-ja
        this.types.store(this.types.valueFor(parameter.type).get(), parameter);
    }

    @Override
    public void visit(Array array) {
        // Array potrebuje tip
        // Če še ni izračunan ga izračuna
        if (this.types.valueFor(array.type).isEmpty()) array.type.accept(this);

        // Če tip Array-a še zmeraj ne obstaja potem je to napaka
        if (this.types.valueFor(array.type).isEmpty())
            Report.error(array.type.position, "SEM: Type of 'ARR' not present.");

        // Shrani tip Array-a
        this.types.store(new Type.Array(array.size, this.types.valueFor(array.type).get()), array);
    }

    @SuppressWarnings({"UnnecessaryDefault", "DuplicatedCode"})
    @Override
    public void visit(Atom atom) {
        // logical, integer in string opisujejo tipe LOGICAL, INTEGER in STRING, zaporedoma.
        Type.Atom.Kind kind = switch (atom.type) {
            case LOG -> Type.Atom.Kind.LOG;
            case INT -> Type.Atom.Kind.INT;
            case STR -> Type.Atom.Kind.STR;
            default -> Type.Atom.Kind.VOID; // Atom NE more biti VOID => pokazatelj neznanega tipa.
        };
        if (kind.equals(Type.Atom.Kind.VOID))
            Report.error(atom.position, "SEM: Unknown Type of Atom.");
        this.types.store(new Type.Atom(kind), atom);
    }

    @Override
    public void visit(TypeName name) {

        // Ali je ime sploh definirano?
        var def = this.definitions.valueFor(name);
        if (def.isEmpty())
            { Report.error(name.position, "SEM: Type '" + name.identifier + "' is not defined."); return; }

        // TypeName potrebuje tip
        // Če še ni izračunan, ga izračuna
        var type = this.types.valueFor(def.get());
        if (type.isEmpty()) def.get().accept(this);

        // Če tip TypeName-a še zmeraj ne obstaja potem je to napaka
        type = this.types.valueFor(def.get());
        if (type.isEmpty())
            { Report.error(name.position, "SEM: Type of 'typ' definition is not present."); return; }

        // Shrani tip TypeName-a
        this.types.store(type.get(), name);
    }
}
