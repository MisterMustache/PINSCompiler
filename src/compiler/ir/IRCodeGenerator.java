/**
 * @ Author: turk
 * @ Description: Generator vmesne kode.
 */

package compiler.ir;

import static common.RequireNonNull.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import common.Constants;
import common.Report;
import common.VoidOperator;
import compiler.common.Visitor;
import compiler.frm.Access;
import compiler.frm.Frame;
import compiler.frm.Frame.Label;
import compiler.ir.chunk.Chunk;
import compiler.ir.code.IRNode;
import compiler.ir.code.expr.*;
import compiler.ir.code.stmt.*;
import compiler.parser.ast.def.*;
import compiler.parser.ast.def.FunDef.Parameter;
import compiler.parser.ast.expr.*;
import compiler.parser.ast.type.Array;
import compiler.parser.ast.type.Atom;
import compiler.parser.ast.type.TypeName;
import compiler.seman.common.NodeDescription;
import compiler.seman.type.type.Type;
import stdlib.StandardFunctions;

public class IRCodeGenerator implements Visitor {
    /**
     * Preslikava iz vozlišč AST v vmesno kodo.
     */
    private final NodeDescription<IRNode> imcCode;

    /**
     * Razrešeni klicni zapisi.
     */
    private final NodeDescription<Frame> frames;

    /**
     * Razrešeni dostopi.
     */
    private final NodeDescription<Access> accesses;

    /**
     * Razrešene definicije.
     */
    private final NodeDescription<Def> definitions;

    /**
     * Razrešeni tipi.
     */
    private final NodeDescription<Type> types;

    /**
     * **Rezultat generiranja vmesne kode** - seznam fragmentov.
     */
    public List<Chunk> chunks = new ArrayList<>();

    private int staticLevel;

    public IRCodeGenerator(
        NodeDescription<IRNode> imcCode,
        NodeDescription<Frame> frames, 
        NodeDescription<Access> accesses,
        NodeDescription<Def> definitions,
        NodeDescription<Type> types
    ) {
        requireNonNull(imcCode, frames, accesses, definitions, types);
        this.types = types;
        this.imcCode = imcCode;
        this.frames = frames;
        this.accesses = accesses;
        this.definitions = definitions;
        this.staticLevel = 0;
    }

    private void inNewScope(VoidOperator vo) {
        this.staticLevel++;
        vo.apply();
        this.staticLevel--;
    }

    @Override
    public void visit(Call call) {
        // --- Laying the field ---
        var argSeq = new ArrayList<IRExpr>();           // seznam argumentov
        Label functionLabel;                            // oznaka
        int SL;                                         // statični nivo
        Optional<MoveStmt> oldFPIR = Optional.empty();  // oldFP

        // --- Klic funkcije iz standardne knjižnice ---
        if (StandardFunctions.exists(call.name)) {
            functionLabel = Label.named(call.name);
            SL = 1;
        } else {
            // Okvir funkcije, ki se kliče
            var functionFrame = this.frames.valueFor(this.definitions.valueFor(call).get()).get();
            functionLabel = functionFrame.label;
            SL = functionFrame.staticLevel;

            oldFPIR = Optional.of(new MoveStmt(
                    new MemExpr(
                            new BinopExpr(
                                    NameExpr.SP(),
                                    new ConstantExpr(functionFrame.oldFPOffset()),
                                    BinopExpr.Operator.SUB
                            )
                    ),
                    NameExpr.FP()
            ));
        }

        // --- Generiranje argumentov ---
        // Prvi argument je statični nivo.
        if (SL <= 1)                                        // Če je funkcija na globalni ravni je statični nivo brezpredmeten
            argSeq.add(new ConstantExpr(0));
        else if (SL > this.staticLevel)                     // Če je funkcijo klicala starševska funkcija
            argSeq.add(NameExpr.FP());
        else if (SL == this.staticLevel)                    // Če je funkcija klicala sama sebe
            argSeq.add(new MemExpr(NameExpr.FP()));
        else {                                              // Če je funkcija klicala njeno starševsko funkcijo
            MemExpr SLJumps = new MemExpr(NameExpr.FP());
            for (int i = 0; i < this.staticLevel - SL; i++)
                SLJumps = new MemExpr(SLJumps);
            argSeq.add(SLJumps);
        }

        // Preostali argumenti
        call.arguments.forEach(arg -> {
            arg.accept(this);
            var callArgIRNode = this.imcCode.valueFor(arg);

            callArgIRNode.ifPresentOrElse(
                    irNode -> {
                        if (irNode instanceof IRExpr irExpr)
                            argSeq.add(irExpr);
                        else
                            Report.error(arg.position, "IMC: Compiler error. Call Argument is not an Expression.");
                    },
                    () -> Report.error(arg.position, "IMC: Compiler error. IR of Call Argument has failed to generate!")
            );
        });

        // --- Shrani kodo ---
        this.imcCode.store(
                new EseqExpr(
                        oldFPIR.isEmpty() ? SeqStmt.empty() : oldFPIR.get(),
                        new CallExpr(functionLabel, argSeq)
                ),
                call
        );

    }

    @Override
    public void visit(Binary binary) {
        BinopExpr.Operator binopExprOperator = operatorMap(binary.operator);
        binary.left.accept(this);
        binary.right.accept(this);
        var binaryLeftIRNode = this.imcCode.valueFor(binary.left);
        var binaryRightIRNode = this.imcCode.valueFor(binary.right);

        if (binaryLeftIRNode.isPresent() && binaryRightIRNode.isPresent()) {
            if (binaryLeftIRNode.get() instanceof IRExpr binaryLeftIrExpr &&
                    binaryRightIRNode.get() instanceof IRExpr binaryRightIrExpr) {
                if (binary.operator == Binary.Operator.ARR) {
                    if (binaryLeftIrExpr instanceof MemExpr)
                        binaryLeftIrExpr = ((MemExpr) binaryLeftIrExpr).expr;

                    imcCode.store(
                            new MemExpr(
                                    new BinopExpr(
                                            binaryLeftIrExpr,
                                            new BinopExpr(
                                                    binaryRightIrExpr,
                                                    new ConstantExpr(this.types.valueFor(binary).get().sizeInBytes()),
                                                    BinopExpr.Operator.MUL
                                            ),
                                            BinopExpr.Operator.ADD
                                    )
                            ),
                            binary
                    );
                } else if (binary.operator == Binary.Operator.ASSIGN){
                    imcCode.store(new EseqExpr(new MoveStmt(binaryLeftIrExpr, binaryRightIrExpr), binaryLeftIrExpr), binary);
                } else if (binopExprOperator != null) {
                    var binopExpr = new BinopExpr(binaryLeftIrExpr, binaryRightIrExpr, binopExprOperator);
                    this.imcCode.store(binopExpr, binary);
                } else {
                    Report.error(binary.position, "IMC: Compiler error. Binary Operator assertion failed!");
                }
            } else {
                Report.error(binary.position, "IMC: Compiler error. Both sides of Binary are not Expressions.");
            }
        } else {
            Report.error(binary.position, "IMC: Compiler error. IR of Binary has failed to generate!");
        }
    }

    private BinopExpr.Operator operatorMap(Binary.Operator operator) {
        return switch (operator) {
            case ADD -> BinopExpr.Operator.ADD;
            case SUB -> BinopExpr.Operator.SUB;
            case MUL -> BinopExpr.Operator.MUL;
            case DIV -> BinopExpr.Operator.DIV;
            case MOD -> BinopExpr.Operator.MOD;
            case AND -> BinopExpr.Operator.AND;
            case OR -> BinopExpr.Operator.OR;
            case EQ -> BinopExpr.Operator.EQ;
            case NEQ -> BinopExpr.Operator.NEQ;
            case LT -> BinopExpr.Operator.LT;
            case GT -> BinopExpr.Operator.GT;
            case LEQ -> BinopExpr.Operator.LEQ;
            case GEQ -> BinopExpr.Operator.GEQ;
            default -> null;
        };
    }

    @Override
    public void visit(Block block) {
        var seq = new ArrayList<IRStmt>();
        block.expressions.forEach(expr -> {
            expr.accept(this);
            this.imcCode.valueFor(expr).ifPresentOrElse(irNode -> {
                if (irNode instanceof IRExpr irExpr)
                    seq.add(new ExpStmt(irExpr));
                else if (irNode instanceof IRStmt irStmt)
                    seq.add(irStmt);
                else
                    Report.error(expr.position, "IMC: Compiler error. Block Item must be an Expression or Statement.");
            }, () -> Report.error(expr.position, "IMC: Compiler error. IR of Block Expression has failed to generate!"));
        });

        if (seq.remove(seq.size() - 1) instanceof ExpStmt expStmt)
            this.imcCode.store(new EseqExpr(new SeqStmt(seq), expStmt.expr), block);
        else
            Report.error(block.expressions.get(block.expressions.size() - 1).position, "IMC: Compiler error. " +
                    "Last Block Item is not an Expression.");
    }

    @Override
    public void visit(For forLoop) {
        // Izračun kode
        forLoop.counter.accept(this);
        forLoop.low.accept(this);
        forLoop.high.accept(this);
        forLoop.step.accept(this);
        forLoop.body.accept(this);
        var forLoopCounterIRExprOptional = this.imcCode.valueFor(forLoop.counter);
        var forLoopLowIRExprOptional = this.imcCode.valueFor(forLoop.low);
        var forLoopHighIRExprOptional = this.imcCode.valueFor(forLoop.high);
        var forLoopStepIRExprOptional = this.imcCode.valueFor(forLoop.step);
        var forLoopBodyIRExprOptional = this.imcCode.valueFor(forLoop.body);

        // Vsi deli for zanke so obvezni
        if (forLoopCounterIRExprOptional.isEmpty() || forLoopLowIRExprOptional.isEmpty() || forLoopHighIRExprOptional.isEmpty() ||
                forLoopStepIRExprOptional.isEmpty() || forLoopBodyIRExprOptional.isEmpty()) {
            Report.error(forLoop.position, "IMC: Compiler error. For loop is not present. Suspicious For loop?");
            return;
        }

        if (forLoopCounterIRExprOptional.get() instanceof IRExpr counterIrExpr &&
                forLoopLowIRExprOptional.get() instanceof IRExpr lowIrExpr &&
                forLoopHighIRExprOptional.get() instanceof IRExpr highIrExpr &&
                forLoopStepIRExprOptional.get() instanceof IRExpr stepIrExpr &&
                forLoopBodyIRExprOptional.get() instanceof IRExpr bodyIrExpr) {
            // --- Oznake za skakanje ---
            var seq = new ArrayList<IRStmt>();
            Label bodyBeginLabel = Label.nextAnonymous();
            Label bodyEndLabel = Label.nextAnonymous();
            Label conditionBeginLabel = Label.nextAnonymous();

            // --- Stavki pogoja ---
            seq.add(new MoveStmt(counterIrExpr, lowIrExpr)); // value(low) -> counter
            seq.add(new LabelStmt(conditionBeginLabel));     // _condition Label
            seq.add(new CJumpStmt(                           // _condition
                    new BinopExpr(                           // if counter < high
                            counterIrExpr,
                            highIrExpr,
                            BinopExpr.Operator.LT
                    ),
                    bodyBeginLabel,                          // then : execute(_body)
                    bodyEndLabel                             // else : __end
            ));
            seq.add(new LabelStmt(bodyBeginLabel));          // _body Label
            seq.add(new ExpStmt(bodyIrExpr));                // _body
            seq.add(new MoveStmt(                            // counter + value(step) -> counter
                    counterIrExpr,
                    new BinopExpr(
                            counterIrExpr,
                            stepIrExpr,
                            BinopExpr.Operator.ADD
                    )
            ));
            seq.add(new JumpStmt(conditionBeginLabel));      // execute(_condition)
            seq.add(new LabelStmt(bodyEndLabel));            // __end

            // --- Shrani kodo ---
            this.imcCode.store(new SeqStmt(seq), forLoop);
        } else {
            Report.error(forLoop.position, "IMC: Compiler error. All For loop building blocks are not expressions.");
        }
    }

    @Override
    public void visit(Name name) {
        // Dostop spremenljivke
        //noinspection OptionalGetWithoutIsPresent
        Access nameAccess = this.accesses.valueFor(this.definitions.valueFor(name).get()).get();

        // Če je spremenljivka globalna
        if (nameAccess instanceof Access.Global global) {
            this.imcCode.store(new MemExpr(
                    new NameExpr(global.label)
            ), name);
//            this.chunks.add(new Chunk.GlobalChunk(global));
        }
        // Če je spremenljivka lokalna ali parameter
        else if      (nameAccess instanceof Access.Stack stack) {
            // Pridobi ustrezno število MemExpr glede na statični nivo
            Optional<MemExpr> memExpr = Optional.empty();
            for (int i = 0; i < this.staticLevel - stack.staticLevel; i++) {
                if (memExpr.isEmpty())
                    memExpr = Optional.of(new MemExpr(NameExpr.FP()));
                else
                    memExpr = Optional.of(new MemExpr(memExpr.get()));
            }

            BinopExpr binopExpr = new BinopExpr(
                    memExpr.isEmpty() ? NameExpr.FP() : memExpr.get(),
                    new ConstantExpr(
                            stack.offset
                    ),
                    BinopExpr.Operator.ADD
            );
            this.imcCode.store(new MemExpr(binopExpr), name);
        }
        else {
            Report.error(name.position, "ICM: Compiler error. Name Access assertion failed!");
        }

    }

    @Override
    public void visit(IfThenElse ifThenElse) {
        // Izračun kode
        ifThenElse.condition.accept(this);
        ifThenElse.thenExpression.accept(this);
        ifThenElse.elseExpression.ifPresent(expr -> expr.accept(this));
        var ifConditionIRExpr = this.imcCode.valueFor(ifThenElse.condition);
        var ifThenIRExpr = this.imcCode.valueFor(ifThenElse.thenExpression);
        Optional<IRNode> ifElseIRExpr = Optional.empty();
        if (ifThenElse.elseExpression.isPresent())
            ifElseIRExpr = this.imcCode.valueFor(ifThenElse.elseExpression.get());

        // Pogoj in pozitiven stavek sta obvezna
        if (ifConditionIRExpr.isEmpty() || ifThenIRExpr.isEmpty())
            { Report.error(ifThenElse.position, "IMC: Compiler error. If statement is not present. " +
                    "Suspicious Condition Expression or Then body?"); return; }

        if (ifConditionIRExpr.get() instanceof IRExpr conditionIrExpr && ifThenIRExpr.get() instanceof IRExpr thenIrExpr) {
            // --- Oznake za skakanje ---
            var seq = new ArrayList<IRStmt>();
            Label thenBeginLabel = Label.nextAnonymous();
            Label elseBeginLabel = Label.nextAnonymous();
            Label ifEndLabel = Label.nextAnonymous();

            // --- Stavki pogoja ---
            // Če obstaja negativen stavek se bo le ta izvedel ob ne izpolnjenju pogoja, sicer se stavek zaključi.
            if (ifElseIRExpr.isPresent()) {
                if (ifElseIRExpr.get() instanceof IRExpr)
                    seq.add(new CJumpStmt(conditionIrExpr, thenBeginLabel, elseBeginLabel));
                else
                    Report.error(ifThenElse.elseExpression.get().position,
                            "IMC: Compiler error. If Else Body is not an expression.");
            } else
                seq.add(new CJumpStmt(conditionIrExpr, thenBeginLabel, ifEndLabel));

            // Then stavek
            seq.add(new LabelStmt(thenBeginLabel));
            seq.add(new ExpStmt(thenIrExpr));
            seq.add(new JumpStmt(ifEndLabel));

            // Else stavek (če le ta obstaja)
            if (ifElseIRExpr.isPresent() && ifElseIRExpr.get() instanceof IRExpr elseIrExpr) {
                seq.add(new LabelStmt(elseBeginLabel));
                seq.add(new ExpStmt(elseIrExpr));
                seq.add(new JumpStmt(ifEndLabel));
            }

            // Konec
            seq.add(new LabelStmt(ifEndLabel));

            // --- Shrani kodo ---
            this.imcCode.store(new SeqStmt(seq), ifThenElse);

        } else if (ifConditionIRExpr.get() instanceof IRExpr) {
            Report.error(ifThenElse.thenExpression.position, "IMC: Compiler error. If Then Body is not an expression.");
        } else {
            Report.error(ifThenElse.condition.position, "IMC: Compiler error. If Condition is not an expression.");
        }

    }

    @Override
    public void visit(Literal literal) {
        // Tip konstante
        //noinspection OptionalGetWithoutIsPresent
        var type = this.types.valueFor(literal).get();

        // Rokovanje konstante glede na tip
        if (type.isInt()) {
            this.imcCode.store(
                    new ConstantExpr(Integer.parseInt(literal.value)),
                    literal
            );
        } else if (type.isLog()) {
            // true = 1
            // false = 0
            this.imcCode.store(
                    new ConstantExpr(literal.value.equals("true") ? 1 : 0),
                    literal
            );
        } else if (type.isStr()) {
            // Malo bolj posebno.
            // Dejanski string kot podatek je shranjen na kopici (anonimno), medtem ko je na mestu na skladu njegova
            // referenca velikosti kazalca v obliki oznake (Label)
            Label stringLabel = Label.nextAnonymous();
            var stringDataChunk = new Chunk.DataChunk(
                    new Access.Global(Constants.WordSize, stringLabel),
                    //      (kazalec) ~~~~~~~~~~~~~~~~~~  ~~~~~~~~~~~ (oznaka)
                    literal.value
                    //~~~~~~~~~~~ (besedilo, kot podatek)
            );
            this.chunks.add(stringDataChunk);
            this.imcCode.store(new NameExpr(stringLabel), literal);
        } else {
            // Če pride do tega potem pa tut res ne vem
            Report.error(literal.position, "IMC: Compiler Error. Literal assertion failed! Unimplemented literal type?");
        }
    }

    @Override
    public void visit(Unary unary) {
        // Izračun kode izraza
        unary.expr.accept(this);
        //noinspection OptionalGetWithoutIsPresent
        var unaryExprIRExpr = (IRExpr) this.imcCode.valueFor(unary.expr).get();

        // Če je spredaj minus, se shrani kot operacija, kjer se od 0 odšteje vrednost
        if (unary.operator == Unary.Operator.SUB) {
            this.imcCode.store(new BinopExpr(
                    new ConstantExpr(0),
                    unaryExprIRExpr,
                    BinopExpr.Operator.SUB
            ), unary);
        }
        // Če je spredaj plus, se lahko zanemari
        else if (unary.operator == Unary.Operator.ADD) {
            this.imcCode.store(unaryExprIRExpr, unary);
        }
        // Če je spredaj negacija, se najprej od pogoja odšteje 1 nato pa še pridobi absolutno vrednost z množenjem z -1.
        // novPogoj = (starPogoj - 1) * (-1)
        // iz 1 -> 0 in iz 0 -> 1
        else if (unary.operator == Unary.Operator.NOT) {
            this.imcCode.store(new BinopExpr(
                    new ConstantExpr(-1),
                    new BinopExpr(
                            unaryExprIRExpr,
                            new ConstantExpr(1),
                            BinopExpr.Operator.SUB
                    ),
                    BinopExpr.Operator.MUL
            ), unary);
        } else {
            Report.error(unary.position, "IMC: Unknown Unary operator " + unary.operator + ".");
        }
    }

    @Override
    public void visit(While whileLoop) {
        // Izračun kode pogoja in telesa zanke
        whileLoop.condition.accept(this);
        whileLoop.body.accept(this);
        var whileConditionIMC = this.imcCode.valueFor(whileLoop.condition).get();
        var whileBodyIMC = this.imcCode.valueFor(whileLoop.body).get();

        // Telo in pogoj morata biti izraza
        if (whileConditionIMC instanceof IRExpr conditionIrExpr && whileBodyIMC instanceof IRExpr bodyIrExpr) {
            // Oznake za skakanje
            var conditionLabel = Label.nextAnonymous();
            var bodyBeginLabel = Label.nextAnonymous();
            var bodyEndLabel = Label.nextAnonymous();

            // Stavki zanke
            var seq = new ArrayList<IRStmt>();
            seq.add(new LabelStmt(conditionLabel));
            seq.add(new CJumpStmt(conditionIrExpr, bodyBeginLabel, bodyEndLabel));
            seq.add(new LabelStmt(bodyBeginLabel));
            seq.add(new ExpStmt(bodyIrExpr));
            seq.add(new JumpStmt(conditionLabel));
            seq.add(new LabelStmt(bodyEndLabel));

            this.imcCode.store(new SeqStmt(seq), whileLoop);
        } else if (!(whileConditionIMC instanceof IRExpr)) {
            Report.error(whileLoop.condition.position, "IMC: Compiler error. While Condition is not an Expression.");
        } else {
            Report.error(whileLoop.condition.position, "IMC: Compiler error. While Body is not an Expression.");
        }
    }

    @Override
    public void visit(Where where) {
        where.defs.accept(this);
        where.expr.accept(this);

        var whereExprIRNode = this.imcCode.valueFor(where.expr);
        if (whereExprIRNode.isPresent()) {
            if (whereExprIRNode.get() instanceof IRExpr whereExprIrExpr) {
                this.imcCode.store(whereExprIrExpr, where);
            } else {
                Report.error(where.expr.position, "IMC: Compiler error. Where Expression is not an Expression.");
            }
        } else {
            Report.error(where.expr.position, "IMC: Compiler error. Where Expression is not present.");
        }
    }

    @Override
    public void visit(Defs defs) {
        defs.definitions.forEach(def -> def.accept(this));
    }

    @Override
    public void visit(FunDef funDef) {
        // Izračun IR telesa funkcije
        inNewScope(() -> funDef.body.accept(this));

        var funBodyIMCOptional = this.imcCode.valueFor(funDef.body);

        if (funBodyIMCOptional.isPresent()) {
            // Telo funkcije mora biti izraz
            if (funBodyIMCOptional.get() instanceof IRExpr funBodyIrExpr) {
                // Ustvarjanje novega kodnega fragmenta
                // Klicni zapis funkcije
                this.frames.valueFor(funDef).ifPresentOrElse(frame -> {
                    var codeChunk = new Chunk.CodeChunk(
                            frame,
                            new MoveStmt(new MemExpr(NameExpr.FP()), funBodyIrExpr)
                    );
                    this.chunks.add(codeChunk);
                }, () -> Report.error(funDef.position, "IMC: Compiler error. No frame for function '" +
                        funDef.name + "' is present."));
            } else {
                Report.error(funDef.body.position, "IMC: Compiler error. Function Body assertion failed. " +
                        "Function Body must be an Expression.");
            }
        } else {
            Report.error(funDef.body.position, "IMC: Compiler error. IR of Function Body has failed to generate!");
        }
    }

    @Override
    public void visit(TypeDef typeDef) {
        //noinspection UnnecessaryReturnStatement
        return;
    }

    @Override
    public void visit(VarDef varDef) {
        // Če je spremenljivka globalna jo dodaj kot global chunk
        var varDefAccess = this.accesses.valueFor(varDef);
        if (varDefAccess.isPresent()) {
            if (varDefAccess.get() instanceof Access.Global global) {
                this.chunks.add(new Chunk.GlobalChunk(global));
            }
        } else
            Report.error(varDef.position, "IMC: Compiler error. Variable Definition is not present.");
    }

    @Override
    public void visit(Parameter parameter) {
        //noinspection UnnecessaryReturnStatement
        return;
    }

    @Override
    public void visit(Array array) {
        //noinspection UnnecessaryReturnStatement
        return;
    }

    @Override
    public void visit(Atom atom) {
        //noinspection UnnecessaryReturnStatement
        return;
    }

    @Override
    public void visit(TypeName name) {
        //noinspection UnnecessaryReturnStatement
        return;
    }
}
