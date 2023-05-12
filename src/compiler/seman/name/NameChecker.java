/**
 * @ Author: turk
 * @ Description: Preverjanje in razreševanje imen.
 */

package compiler.seman.name;

import static common.RequireNonNull.requireNonNull;

import common.Report;
import compiler.common.Visitor;
import compiler.parser.ast.def.*;
import compiler.parser.ast.def.FunDef.Parameter;
import compiler.parser.ast.expr.*;
import compiler.parser.ast.type.*;
import compiler.seman.common.NodeDescription;
import compiler.seman.name.env.SymbolTable;
import compiler.seman.name.env.SymbolTable.DefinitionAlreadyExistsException;
import stdlib.StandardFunctions;

public class NameChecker implements Visitor {
    /**
     * Opis vozlišč, ki jih povežemo z njihovimi
     * definicijami.
     */
    private final NodeDescription<Def> definitions;

    /**
     * Simbolna tabela.
     */
    private final SymbolTable symbolTable;

    /**
     * Ustvari nov razreševalnik imen.
     */
    public NameChecker(
        NodeDescription<Def> definitions,
        SymbolTable symbolTable
    ) {
        requireNonNull(definitions, symbolTable);
        this.definitions = definitions;
        this.symbolTable = symbolTable;
    }

    @Override
    public void visit(Call call) {
        /*
         * Preverjanje ujemanja klica funkcije z njeno definicijo
         */

        // Pridobi definicijo funkcije
        var def = symbolTable.definitionFor(call.name);

        // Definicija obstaja
        if (def.isPresent()) {
            // Ali je up. def. tip dejansko uporabljen pri definiciji tipa?
            // Da (pravilno)
            if (def.get() instanceof FunDef) {
                this.definitions.store(def.get(), call);
                for (var arg : call.arguments)
                    arg.accept(this);
                // Ne (napaka)
            } else {
                /* npr.
                 * `typ t : integer;
                 * t(1)` */
                if (def.get() instanceof TypeDef) {
                    Report.error(call.position, "SEM: Expected function, got type '" + def.get().name + "'.");
                }
                /* npr.
                 * `var v : integer;
                 * v(1)` */
                else if (def.get() instanceof VarDef)
                    Report.error(call.position, "SEM: Expected function, got variable '" + def.get().name + "'.");
                    // "Future-proof" ujemanje neke neznane definicije.
                else
                    Report.error(call.position, "SEM: Expected function, got definition '" + def.get().name + "'.");
            }
        }
        // Preverjanje ujemanju funkcije iz standardne knjižnice
        else if (StandardFunctions.exists(call.name)) {
            //noinspection UnnecessaryReturnStatement
            return;
        }
        // Definicija NE obstaja
        else {
            Report.error(call.position, "SEM: Unknown function '" + call.name + "'.");
        }
    }

    @Override
    public void visit(Binary binary) {
        binary.left.accept(this);
        binary.right.accept(this);
    }

    @Override
    public void visit(Block block) {
        for (Expr expr : block.expressions) {
            expr.accept(this);
        }
    }

    @Override
    public void visit(For forLoop) {
        forLoop.counter.accept(this);
        forLoop.low.accept(this);
        forLoop.high.accept(this);
        forLoop.step.accept(this);
        forLoop.body.accept(this);
    }

    @Override
    public void visit(Name name) {
        /*
         * Preverjanje ujemanja imena spremenljivke z njegovo definicijo
         */

        // Pridobi definicijo imena
        var def = symbolTable.definitionFor(name.name);

        // Definicija obstaja
        if (def.isPresent()) {
            if (def.get() instanceof VarDef || def.get() instanceof Parameter) {
                definitions.store(def.get(), name);
            } else if (def.get() instanceof FunDef) {
                Report.error(name.position, "SEM: Expected variable, got function '" + def.get().name + "'.");
            } else if (def.get() instanceof TypeDef) {
                Report.error(name.position, "SEM: Expected variable, got type '" + def.get().name + "'.");
            }
        // Definicija NE obstaja
        } else {
            Report.error(name.position, "SEM: Unknown variable '" + name.name + "'.");
        }
    }

    @Override
    public void visit(IfThenElse ifThenElse) {
        ifThenElse.condition.accept(this);
        ifThenElse.thenExpression.accept(this);
        ifThenElse.elseExpression.ifPresent(expr -> expr.accept(this));
    }

    @Override
    public void visit(Literal literal) {
        //noinspection UnnecessaryReturnStatement
        return;
    }

    @Override
    public void visit(Unary unary) {
        unary.expr.accept(this);
    }

    @Override
    public void visit(While whileLoop) {
        whileLoop.condition.accept(this);
        whileLoop.body.accept(this);
    }

    @Override
    public void visit(Where where) {
        symbolTable.inNewScope(() -> {
            where.defs.accept(this);
            where.expr.accept(this);
        });
    }

    @Override
    public void visit(Defs defs) {
        /*
         * Prvi sprehod
         * Shranjevanje vseh deklaracij definicij v simbolno tabelo.
         */
        for (Def def : defs.definitions) {
            try {
                symbolTable.insert(def);
            } catch (DefinitionAlreadyExistsException _e) {
                Report.error(def.position, "SEM: Definition '" + def.name + "' is already defined in scope.");
            }
        }

        /*
         * Drugi sprehod
         * Razreševanje (povezovanje) vseh imen.
         */
        for (Def def : defs.definitions) {
            def.accept(this);
        }
    }

    @Override
    public void visit(FunDef funDef) {
        symbolTable.inNewScope(() -> {
            funDef.type.accept(this);
            for (Parameter par : funDef.parameters) {
                par.type.accept(this);
            }
            for (var par : funDef.parameters)
                par.accept(this);
            funDef.body.accept(this);
        });
    }

    @Override
    public void visit(TypeDef typeDef) {
        typeDef.type.accept(this);
    }

    @Override
    public void visit(VarDef varDef) {
        varDef.type.accept(this);
    }

    @Override
    public void visit(Parameter parameter) {
        // Prvo pregledamo tip parametra in ga šele nato dodamo v tabelo
        //parameter.type.accept(this);
        try {
            symbolTable.insert(parameter);
        } catch (DefinitionAlreadyExistsException _e) {
            Report.error(parameter.position, "SEM: Parameter '" + parameter.name + "' is already defined in scope.");
        }
    }

    @Override
    public void visit(Array array) {
        array.type.accept(this);
    }

    @Override
    public void visit(Atom atom) {
        // Atomarnih tipov ni mogoče razrešiti
        //noinspection UnnecessaryReturnStatement
        return;
    }

    @Override
    public void visit(TypeName name) {
        /*
         * Preverjanje ujemanja uporabe (uporabniško definiranega) tipa (z njegovo deklaracijo)
         */

        // Pridobi definicijo uporabniško definiranega tipa
        var def = symbolTable.definitionFor(name.identifier);

        // Definicija obstaja
        if (def.isPresent()) {
            // Ali je up. def. tip dejansko uporabljen pri definiciji tipa?
            // Da (pravilno)
            if (def.get() instanceof TypeDef) {
                // Povezava definicije z deklaracijo
                definitions.store(def.get(), name);
            // Ne (napaka)
            } else {
                /* npr.
                 * `fun f (p: integer) : integer = p;
                 * typ t : f` */
                if (def.get() instanceof FunDef)
                    Report.error(name.position, "SEM: Expected type, got function '" + def.get().name + "'.");
                /* npr.
                 * `var v : integer;
                 * typ t : v` */
                else if (def.get() instanceof VarDef)
                    Report.error(name.position, "SEM: Expected type, got variable '" + def.get().name + "'.");
                // "Future-proof" ujemanje neke neznane definicije.
                else
                    Report.error(name.position, "SEM: Expected type, got definition '" + def.get().name + "'.");
            }
        // Definicija NE obstaja
        } else {
            Report.error(name.position, "SEM: Unknown type '" + name.identifier + "'.");
        }
    }
}
