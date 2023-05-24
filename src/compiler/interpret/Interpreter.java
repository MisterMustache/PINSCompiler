/**
 * @ Description: Navidezni stroj (intepreter).
 */

package compiler.interpret;

import static common.RequireNonNull.requireNonNull;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import common.Constants;
import common.Report;
import compiler.frm.Frame;
import compiler.gen.Memory;
import compiler.ir.chunk.Chunk.CodeChunk;
import compiler.ir.code.IRNode;
import compiler.ir.code.expr.*;
import compiler.ir.code.stmt.*;
import compiler.ir.IRPrettyPrint;

public class Interpreter {
    /**
     * Pomnilnik navideznega stroja.
     */
    private final Memory memory;

    /**
     * Izhodni tok, kamor izpisujemo rezultate izvajanja programa.
     * V primeru, da rezultatov ne želimo izpisovati, nastavimo na `Optional.empty()`.
     */
    private final Optional<PrintStream> outputStream;

    /**
     * Generator naključnih števil.
     */
    private Random random;

    /**
     * Skladovni kazalec (kaže na dno sklada).
     */
    private int stackPointer;

    /**
     * Klicni kazalec (kaže na vrh aktivnega klicnega zapisa).
     */
    private int framePointer;

    public Interpreter(Memory memory, Optional<PrintStream> outputStream) {
        requireNonNull(memory, outputStream);
        this.memory = memory;
        this.outputStream = outputStream;
        this.stackPointer = memory.size - Constants.WordSize;
        this.framePointer = memory.size - Constants.WordSize;
    }

    // --------- izvajanje navideznega stroja ----------

    public void interpret(CodeChunk chunk) {
        memory.stM(framePointer + Constants.WordSize, 999); // argument v funkcijo main
        memory.stM(framePointer - chunk.frame.oldFPOffset(), framePointer); // oldFP
        internalInterpret(chunk, new HashMap<>());
    }

    private void internalInterpret(CodeChunk chunk, Map<Frame.Temp, Object> temps) {
        // Najprej FP prestavimo na mesto SP, nato pa SP nastavimo na naslov, ki je oddaljen za velikost trenutnega okvirja.
        memory.stM(this.stackPointer - chunk.frame.oldFPOffset(), this.framePointer);
        this.framePointer = this.stackPointer;
        this.stackPointer -= chunk.frame.size();
        memory.registerLabel(NameExpr.FP().label, this.framePointer);
        memory.registerLabel(NameExpr.SP().label, this.stackPointer);

        Object result;
        if (chunk.code instanceof SeqStmt seq) {
            for (int pc = 0; pc < seq.statements.size(); pc++) {
                var stmt = seq.statements.get(pc);
                result = execute(stmt, temps);
                if (result instanceof Frame.Label label) {
                    for (int q = 0; q < seq.statements.size(); q++) {
                        if (seq.statements.get(q) instanceof LabelStmt labelStmt && labelStmt.label.equals(label)) {
                            pc = q;
                            break;
                        }
                    }
                }
            }
        } else {
            throw new RuntimeException("Linearize IR!");
        }

        // Ponastavi FP in SP na stare vrednosti
        this.stackPointer = this.framePointer;
        this.framePointer = toInt(
                memory.ldM(
                        this.framePointer - chunk.frame.oldFPOffset()
                )
        );
        memory.registerLabel(NameExpr.FP().label, this.framePointer);
        memory.registerLabel(NameExpr.SP().label, this.stackPointer);
    }

    private Object execute(IRStmt stmt, Map<Frame.Temp, Object> temps) {
        if (stmt instanceof CJumpStmt cjump) {
            return execute(cjump, temps);
        } else if (stmt instanceof ExpStmt exp) {
            return execute(exp, temps);
        } else if (stmt instanceof JumpStmt jump) {
            return execute(jump, temps);
        } else if (stmt instanceof LabelStmt label) {
            return null;
        } else if (stmt instanceof MoveStmt move) {
            return execute(move, temps);
        } else {
            throw new RuntimeException("Cannot execute this statement!");
        }
    }

    private Object execute(CJumpStmt cjump, Map<Frame.Temp, Object> temps) {
        return toBool(execute(cjump.condition, temps)) ? cjump.thenLabel : cjump.elseLabel;
    }

    private Object execute(ExpStmt exp, Map<Frame.Temp, Object> temps) {
        return execute(exp.expr, temps);
    }

    private Object execute(JumpStmt jump, Map<Frame.Temp, Object> temps) {
        return jump.label;
    }

    private Object execute(MoveStmt move, Map<Frame.Temp, Object> temps) {
        Object moveDestination;
        if (move.dst instanceof MemExpr memExpr) {
            moveDestination = execute(memExpr.expr, temps);
            if (moveDestination instanceof Frame.Label label) {
                memory.stM(label, execute(move.src, temps));
                return memory.ldM(label);
            } else {
                memory.stM(toInt(moveDestination), execute(move.src, temps));
                return memory.ldM(toInt(moveDestination));
            }
        } else if (move.dst instanceof TempExpr tempExpr) {
            temps.put(tempExpr.temp, execute(move.src, temps));
            return temps.get(tempExpr.temp);
        } else {
            Report.error("INT: Interpreter error. MoveStmt must be succeeded by MemExpr or TempExpr.");
            return null;
        }
    }

    private Object execute(IRExpr expr, Map<Frame.Temp, Object> temps) {
        if (expr instanceof BinopExpr binopExpr) {
            return execute(binopExpr, temps);
        } else if (expr instanceof CallExpr callExpr) {
            return execute(callExpr, temps);
        } else if (expr instanceof ConstantExpr constantExpr) {
            return execute(constantExpr);
        } else if (expr instanceof EseqExpr eseqExpr) {
            throw new RuntimeException("Cannot execute ESEQ; linearize IRCode!");
        } else if (expr instanceof MemExpr memExpr) {
            return execute(memExpr, temps);
        } else if (expr instanceof NameExpr nameExpr) {
            return execute(nameExpr);
        } else if (expr instanceof TempExpr tempExpr) {
            return execute(tempExpr, temps);
        } else {
            throw new IllegalArgumentException("Unknown expr type");
        }
    }

    private Object execute(BinopExpr binop, Map<Frame.Temp, Object> temps) {
        Object lhs = execute(binop.lhs, temps);
        Object rhs = execute(binop.rhs, temps);
        int left_stmt, right_stmt;

        if (lhs instanceof Frame.Temp temp)
            left_stmt = toInt(temps.get(temp));
        else if (lhs instanceof Frame.Label label)
            left_stmt = memory.address(label);
        else
            left_stmt = toInt(lhs);

        if (rhs instanceof Frame.Temp temp)
            right_stmt = toInt(temps.get(temp));
        else if (rhs instanceof Frame.Label label)
            right_stmt = memory.address(label);
        else
            right_stmt = toInt(rhs);

        return switch (binop.op) {
            case ADD -> left_stmt + right_stmt;
            case SUB -> left_stmt - right_stmt;
            case MUL -> left_stmt * right_stmt;
            case DIV -> left_stmt / right_stmt;
            case MOD -> left_stmt % right_stmt;
            case AND -> toInt(toBool(left_stmt) && toBool(right_stmt));
            case OR  -> toInt(toBool(left_stmt) || toBool(right_stmt));
            case EQ  -> toInt(left_stmt == right_stmt);
            case NEQ -> toInt(left_stmt != right_stmt);
            case LT  -> toInt(left_stmt < right_stmt);
            case GT  -> toInt(left_stmt > right_stmt);
            case LEQ -> toInt(left_stmt <= right_stmt);
            case GEQ -> toInt(left_stmt >= right_stmt);
            default -> {
                Report.error("INT: Interpreter error. Unknown binary operator '" + binop.op + "'.");
                yield null;
            }
        };
    }

    private Object execute(CallExpr call, Map<Frame.Temp, Object> temps) {
        if (call.label.name.equals(Constants.printIntLabel)) {
            if (call.args.size() != 2) { throw new RuntimeException("Invalid argument count!"); }
            var arg = execute(call.args.get(1), temps);
            outputStream.ifPresent(stream -> stream.println(arg));
            return null;
        } else if (call.label.name.equals(Constants.printStringLabel)) {
            if (call.args.size() != 2) { throw new RuntimeException("Invalid argument count!"); }
            var address = execute(call.args.get(1), temps);
            var res = memory.ldM(toInt(address));
            outputStream.ifPresent(stream -> stream.println("\""+res+"\""));
            return null;
        } else if (call.label.name.equals(Constants.printLogLabel)) {
            if (call.args.size() != 2) { throw new RuntimeException("Invalid argument count!"); }
            var arg = execute(call.args.get(1), temps);
            outputStream.ifPresent(stream -> stream.println(toBool(arg)));
            return null;
        } else if (call.label.name.equals(Constants.randIntLabel)) {
            if (call.args.size() != 3) { throw new RuntimeException("Invalid argument count!"); }
            var min = toInt(execute(call.args.get(1), temps));
            var max = toInt(execute(call.args.get(2), temps));
            return random.nextInt(min, max);
        } else if (call.label.name.equals(Constants.seedLabel)) {
            if (call.args.size() != 2) { throw new RuntimeException("Invalid argument count!"); }
            var seed = toInt(execute(call.args.get(1), temps));
            random = new Random(seed);
            return null;
        } else if (memory.ldM(call.label) instanceof CodeChunk chunk) {
            // Argumente shrani nato izvede funkcijo
            int argCount = 0;
            for (var arg : call.args) {
                memory.stM(this.stackPointer + argCount, execute(arg, temps));
                argCount += Constants.WordSize;
            }
            internalInterpret(chunk, new HashMap<>());
            return memory.ldM(this.stackPointer);
        } else {
            throw new RuntimeException("Only functions can be called!");
        }
    }

    private Object execute(ConstantExpr constant) {
        return constant.constant;
    }

    private Object execute(MemExpr mem, Map<Frame.Temp, Object> temps) {
        Object expr = execute(mem.expr, temps);
        if (expr instanceof Frame.Label label)
            return memory.ldM(label);
        else if (expr instanceof Frame.Temp temp)
            return temps.get(temp);
        return memory.ldM(toInt(expr));
    }

    private Object execute(NameExpr name) {
        return memory.address(name.label);
    }

    private Object execute(TempExpr temp, Map<Frame.Temp, Object> temps) {
        return temps.get(temp.temp);
    }

    // ----------- pomožne funkcije -----------

    private int toInt(Object obj) {
        if (obj instanceof Integer integer) {
            return integer;
        }
        throw new IllegalArgumentException("Could not convert obj to integer!");
    }

    private boolean toBool(Object obj) {
        return toInt(obj) == 0 ? false : true;
    }

    private int toInt(boolean bool) {
        return bool ? 1 : 0;
    }

    private String prettyDescription(IRNode ir, int indent) {
        var os = new ByteArrayOutputStream();
        var ps = new PrintStream(os);
        new IRPrettyPrint(ps, indent).print(ir);
        return os.toString(Charset.defaultCharset());
    }

    private String prettyDescription(IRNode ir) {
        return prettyDescription(ir, 2);
    }

    private void prettyPrint(IRNode ir, int indent) {
        System.out.println(prettyDescription(ir, indent));
    }

    private void prettyPrint(IRNode ir) {
        System.out.println(prettyDescription(ir));
    }
}
