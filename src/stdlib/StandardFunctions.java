package stdlib;

import common.Report;
import compiler.parser.ast.expr.Call;
import compiler.seman.common.NodeDescription;
import compiler.seman.type.type.Type;

import java.util.Objects;

public class StandardFunctions {
    public static boolean exists(String name) {
        for (Functions function : Functions.values()) {
            if (function.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static void checkType(Call call, NodeDescription<Type> types) {
        Functions function = get(call.name);

        switch (Objects.requireNonNull(function)) {
            case print_int, seed -> {
                var arguments = call.arguments;

                if (arguments.size() != 1) Report.error(call.position, "SEM: Function '" + function.name() + "' takes one argument of Type 'INT'.");

                var argType = types.valueFor(call.arguments.get(0)).get();
                if (!argType.isInt()) {
                    Report.error(arguments.get(0).position, "SEM: Type mismatch. Expected '" + Type.Atom.Kind.INT +
                            "', got '" + argType.asAtom().get().kind + "'.");
                }
                types.store(new Type.Atom(Type.Atom.Kind.INT), call);
            }
            case print_str -> {
                var arguments = call.arguments;

                if (arguments.size() != 1) Report.error(call.position, "SEM: Function '" + function.name() + "' takes one argument of Type '" + Type.Atom.Kind.STR + "'.");

                var argType = types.valueFor(call.arguments.get(0)).get();
                if (!argType.isStr()) {
                    Report.error(arguments.get(0).position, "SEM: Type mismatch. Expected '" + Type.Atom.Kind.STR +
                            "', got '" + argType.asAtom().get().kind + "'.");
                }
                types.store(new Type.Atom(Type.Atom.Kind.STR), call);
            }
            case print_log -> {
                var arguments = call.arguments;

                if (arguments.size() != 1) Report.error(call.position, "SEM: Function '" + function.name() + "' takes one argument of Type '" + Type.Atom.Kind.LOG + "'.");

                var argType = types.valueFor(call.arguments.get(0)).get();
                if (!argType.isLog()) {
                    Report.error(arguments.get(0).position, "SEM: Type mismatch. Expected '" + Type.Atom.Kind.LOG +
                            "', got '" + argType.asAtom().get().kind + "'.");
                }
                types.store(new Type.Atom(Type.Atom.Kind.LOG), call);
            }
            case rand_int -> {
                var arguments = call.arguments;

                if (arguments.size() != 2) Report.error(call.position, "SEM: Function '" + function.name() + "' takes two arguments of Type '" + Type.Atom.Kind.INT + "'.");

                var arg1Type = types.valueFor(call.arguments.get(0)).get();
                if (!arg1Type.isInt()) {
                    Report.error(arguments.get(0).position, "SEM: Type mismatch. Expected '" + Type.Atom.Kind.INT +
                            "', got '" + arg1Type.asAtom().get().kind + "'.");
                }
                var arg2Type = types.valueFor(call.arguments.get(1)).get();
                if (!arg2Type.isInt()) {
                    Report.error(arguments.get(0).position, "SEM: Type mismatch. Expected '" + Type.Atom.Kind.INT +
                            "', got '" + arg2Type.asAtom().get().kind + "'.");
                }
                types.store(new Type.Atom(Type.Atom.Kind.INT), call);
            }
            default -> Report.error("StdLib Error: Function '" + function + "' not implemented.");
        }
    }

    public static Functions get(String name) {
        for (Functions function : Functions.values()) {
            if (function.name().equals(name)) {
                return function;
            }
        }
        return null;
    }

    private enum Functions {
        print_int, print_str, print_log, // izpisi
        rand_int, seed // random
    }
}
