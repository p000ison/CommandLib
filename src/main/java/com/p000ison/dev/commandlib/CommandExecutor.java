package com.p000ison.dev.commandlib;

import org.apache.commons.lang.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents a CommandExecutor
 */
public abstract class CommandExecutor {

    private final List<Command> commands = new ArrayList<Command>(1);

    private int defaultElementsPerPage = 10;

    public void executeAll(CommandSender sender, String command) {

        String[] arguments = command.split(" ");
        if (arguments.length == 0) {
            return;
        }

        String identifier = arguments[0];

        if (arguments.length >= 1) {
            arguments = removeUntil(arguments, 1);
        }

        executeAll(sender, identifier, arguments);
    }

    public void executeAll(CommandSender sender, String identifier, String[] arguments) {
        if (executeAll(sender, identifier, arguments, commands) == CallResult.NOT_FOUND) {
            onCommandNotFound(sender);
        }
    }

    private CallResult executeAll(CommandSender sender, String identifier, String[] arguments, List<Command> currentCommands) {
        int argumentsNr = arguments.length;

        CallResult result = CallResult.NOT_FOUND;

        List<Command> helpCommands = new LinkedList<Command>(), permCommands = new LinkedList<Command>();

        for (Command command : currentCommands) {
            if (command.isIdentifier(identifier)) {

                CallResult subResult = null;
                if (argumentsNr > 0) {
                    subResult = executeAll(sender, arguments[0], removeUntil(arguments, 1), command.getSubCommands());
                }

                if (command.isInfinite() || argumentsNr < command.getMinArguments() || argumentsNr > command.getMaxArguments()) {
                    //TODO check if argument type is ok
                    if (subResult != CallResult.SUCCESS) {
                        helpCommands.add(command);
                        result = CallResult.DISPLAYED_COMMAND_HELP;
                        continue;
                    }
                }

                if (!command.hasPermission(sender)) {
                    permCommands.add(command);
                    result = CallResult.NO_PERMISSION;
                    continue;
                }

                CallInformation info = createCallInformation(command, sender, identifier, arguments);
                onPreCommand(info);
                command.execute(sender, info);
                onPostCommand(info);
                result = CallResult.SUCCESS;
            }
        }

        if (result != CallResult.SUCCESS) {
            for (Command cmd : helpCommands) {
                onDisplayCommandHelp(sender, cmd);
            }
            for (Command cmd : permCommands) {
                onPermissionFailed(sender, cmd);
            }
        }

        return result;
    }

    private static enum CallResult {
        SUCCESS,
        NOT_FOUND,
        DISPLAYED_COMMAND_HELP,
        NO_PERMISSION
    }


    protected CallInformation createCallInformation(Command command, CommandSender sender, String identifier, String... arguments) {
        return new CallInformation(this, command, sender, identifier, arguments);
    }


    //================================================================================
    // Listening methods
    //================================================================================


    public abstract void onPreCommand(CallInformation info);

    public abstract void onPostCommand(CallInformation info);

    public abstract void onDisplayCommandHelp(CommandSender sender, Command command);

    public abstract void onCommandNotFound(CommandSender sender);

    public abstract void onPermissionFailed(CommandSender sender, Command command);


    //================================================================================
    // Command registration
    //================================================================================


    public Command build() {
        return new Command();
    }

    public Command build(String name) {
        return new Command().setName(name);
    }

    public Command build(Object instance, String name) {
        return findCommand(instance, name, instance.getClass().getDeclaredMethods());
    }

    public Command build(Class clazz, String name) {
        return findCommand(null, name, clazz.getDeclaredMethods());
    }

    public Command register(Command command) {
        if (!commands.contains(command)) {
            command.check();
            commands.add(command);
        }

        return command;
    }


    public Command register(Object instance) {
        return register(instance, null, instance.getClass().getDeclaredMethods());
    }


    public Command register(Class clazz) {
        return register(null, null, clazz.getDeclaredMethods());
    }


    public Command register(Object instance, String name) {
        return register(instance, name, instance.getClass().getDeclaredMethods());
    }


    public Command register(Class clazz, String name) {
        return register(null, name, clazz.getDeclaredMethods());
    }

    private Command register(Object instance, String name, Method... methods) {
        Command cmd = findCommand(instance, name, methods);
        if (cmd == null) {
            throw new CommandException(cmd, "Command not found in the class %s!", instance.getClass().getName());
        }

        register(cmd);

        return cmd;
    }

    private Command findCommand(Object instance, String name, Method... methods) {
        for (Method method : methods) {
            CommandHandler annotation = getAnnotation(method);

            if (annotation != null) {
                if (name != null && !annotation.name().equals(name)) {
                    continue;
                }

                return createCommand(method, instance, annotation);
            }
        }

        return null;
    }

    public final boolean isRegistered(Command command) {
        return commands.contains(command);
    }


    public final List<Command> getCommands() {
        return commands;
    }


    public void setDefaultElementsPerPage(int defaultElementsPerPage) {
        this.defaultElementsPerPage = defaultElementsPerPage;
    }

    public int getDefaultElementsPerPage() {
        return defaultElementsPerPage;
    }

    //================================================================================
    // Internal helper methods
    //================================================================================


    private CommandHandler getAnnotation(Method method) {

        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 2 || parameterTypes[0] != CommandSender.class || parameterTypes[1] != CallInformation.class) {
            return null;
        }

        return method.getAnnotation(CommandHandler.class);
    }


    private Command createCommand(Method method, Object instance, CommandHandler annotation) {
        return new AnnotatedCommand(annotation.name(), annotation.usage(),
                annotation.identifiers(),
                createArguments(annotation.maxArguments(), annotation.minArguments(), annotation.arguments()),
                method, instance);
    }


    static List<Argument> createArguments(final int maxArguments, final int minArguments, final String[] names) {
        final List<Argument> arguments = new ArrayList<Argument>();

        for (int i = 0; i < maxArguments; i++) {
            arguments.add(new Argument((i < names.length ? names[i] : "param" + i), i >= minArguments, false, false, false));
        }

        return arguments;
    }

    public static int parseInt(final String s) {
        if (s == null)
            throw new NumberFormatException("Null string");

        // Check for a sign.
        int num = 0;
        int sign = -1;
        final int len = s.length();
        final char ch = s.charAt(0);
        if (ch == '-') {
            if (len == 1)
                throw new NumberFormatException("Missing digits:  " + s);
            sign = 1;
        } else {
            final int d = ch - '0';
            if (d < 0 || d > 9)
                throw new NumberFormatException("Malformed:  " + s);
            num = -d;
        }

        // Build the number.
        final int max = (sign == -1) ?
                -Integer.MAX_VALUE : Integer.MIN_VALUE;
        final int multmax = max / 10;
        int i = 1;
        while (i < len) {
            int d = s.charAt(i++) - '0';
            if (d < 0 || d > 9)
                throw new NumberFormatException("Malformed:  " + s);
            if (num < multmax)
                throw new NumberFormatException("Over/underflow:  " + s);
            num *= 10;
            if (num < (max + d))
                throw new NumberFormatException("Over/underflow:  " + s);
            num -= d;
        }

        return sign * num;
    }


    private static String[] removeUntil(String[] original, int until) {
        String[] newArray = new String[original.length - until];
        System.arraycopy(original, until,       // from array[removeEnd]
                newArray, 0,                    // to array[removeStart]
                newArray.length);       // this number of elements
        return newArray;
    }


    private static double fuzzyEqualsString(String a, String b) {
        return 1.0 - (double) StringUtils.getLevenshteinDistance(a, b) / (a.length() >= b.length() ? a.length() : b.length());
    }
}
