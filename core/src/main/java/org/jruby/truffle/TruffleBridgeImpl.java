/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;

import org.jruby.TruffleBridge;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.truffle.nodes.core.CoreMethodNodeManager;
import org.jruby.truffle.nodes.methods.MethodDefinitionNode;
import org.jruby.truffle.nodes.profiler.ProfilerTranslator;
import org.jruby.truffle.runtime.NilPlaceholder;
import org.jruby.truffle.runtime.RubyArguments;
import org.jruby.truffle.runtime.RubyContext;
import org.jruby.truffle.runtime.RubyParserResult;
import org.jruby.truffle.runtime.backtrace.Backtrace;
import org.jruby.truffle.runtime.control.RaiseException;
import org.jruby.truffle.runtime.core.RubyArray;
import org.jruby.truffle.runtime.core.RubyException;
import org.jruby.truffle.translator.TranslatorDriver;
import org.jruby.util.cli.Options;

import java.io.IOException;

public class TruffleBridgeImpl implements TruffleBridge {

    private final org.jruby.Ruby runtime;
    private final RubyContext truffleContext;

    public TruffleBridgeImpl(org.jruby.Ruby runtime) {
        assert runtime != null;

        this.runtime = runtime;

        // Set up a context

        truffleContext = new RubyContext(runtime);
    }

    @Override
    public void init() {
        if (RubyContext.PRINT_RUNTIME) {
            runtime.getInstanceConfig().getError().println("jruby: using " + Truffle.getRuntime().getName());
        }

        // Bring in core method nodes

        CoreMethodNodeManager.addStandardMethods(truffleContext.getCoreLibrary().getObjectClass());

        // Give the core library manager a chance to tweak some of those methods

        truffleContext.getCoreLibrary().initializeAfterMethodsAdded();

        // Set program arguments

        for (IRubyObject arg : ((org.jruby.RubyArray) runtime.getObject().getConstant("ARGV")).toJavaArray()) {
            assert arg != null;

            truffleContext.getCoreLibrary().getArgv().slowPush(truffleContext.makeString(arg.toString()));
        }

        // Set the load path

        final RubyArray loadPath = (RubyArray) truffleContext.getCoreLibrary().getGlobalVariablesObject().getInstanceVariable("$:");

        for (IRubyObject path : ((org.jruby.RubyArray) runtime.getLoadService().getLoadPath()).toJavaArray()) {
            loadPath.slowPush(truffleContext.makeString(path.toString()));
        }

        // Hook

        if (truffleContext.getHooks() != null) {
            truffleContext.getHooks().afterInit(truffleContext);
        }
    }

    @Override
    public TruffleMethod truffelize(DynamicMethod originalMethod, org.jruby.ast.ArgsNode argsNode, org.jruby.ast.Node bodyNode) {
        final MethodDefinitionNode methodDefinitionNode = truffleContext.getTranslator().parse(truffleContext, null, argsNode, bodyNode, null);
        return new TruffleMethod(originalMethod, Truffle.getRuntime().createCallTarget(methodDefinitionNode.getMethodRootNode()));
    }

    @Override
    public Object execute(TranslatorDriver.ParserContext parserContext, Object self, MaterializedFrame parentFrame, org.jruby.ast.RootNode rootNode) {
        try {
            final String inputFile = rootNode.getPosition().getFile();

            final Source source;

            if (inputFile.equals("-e")) {
                // TODO(CS): what if a file is legitimately called -e?
                source = Source.asPseudoFile(runtime.getInstanceConfig().getInlineScript().toString(), "-e");
            } else {
                source = Source.fromFileName(inputFile);
            }

            final RubyParserResult parseResult = truffleContext.getTranslator().parse(truffleContext, source, parserContext, parentFrame, null);
            final CallTarget callTarget = Truffle.getRuntime().createCallTarget(parseResult.getRootNode());
            //return callTarget.call(RubyArguments.pack(null, parentFrame, self, null));
            /**
             * When a profiler related option is enabled, {@link ProfilerTranslator} traverses the module to create {@link RubyWrapper} wrapper nodes.
             */
            ProfilerTranslator profilerTranslator = null;

            if (Options.TRUFFLE_PROFILE_CALLS.load() || Options.TRUFFLE_PROFILE_CONTROL_FLOW.load()
                || Options.TRUFFLE_PROFILE_VARIABLE_ACCESSES.load() || Options.TRUFFLE_PROFILE_OPERATIONS.load() 
                || Options.TRUFFLE_PROFILE_ATTRIBUTES_ELEMENTS.load()) {
                profilerTranslator = ProfilerTranslator.getInstance();
                profilerTranslator.translate(parseResult.getRootNode(), true);
            }

            Object value = callTarget.call(RubyArguments.pack(null, parentFrame, self, null));

            if (Options.TRUFFLE_PROFILE_CALLS.load()) {
                profilerTranslator.getProfilerResultPrinter().printCallProfilerResults();
            }

            if (Options.TRUFFLE_PROFILE_CONTROL_FLOW.load()) {
                profilerTranslator.getProfilerResultPrinter().printControlFlowProfilerResults();
            }

            if (Options.TRUFFLE_PROFILE_VARIABLE_ACCESSES.load()) {
                profilerTranslator.getProfilerResultPrinter().printVariableAccessProfilerResults();
            }

            if (Options.TRUFFLE_PROFILE_OPERATIONS.load()) {
                profilerTranslator.getProfilerResultPrinter().printOperationProfilerResults();
            }

            if (Options.TRUFFLE_PROFILE_ATTRIBUTES_ELEMENTS.load()) {
                profilerTranslator.getProfilerResultPrinter().printAttributeElementProfilerResults();
            }

            return value;
        } catch (RaiseException e) {
            // TODO(CS): what's this cast about?
            final RubyException rubyException = (RubyException) e.getRubyException();

            for (String line : Backtrace.DISPLAY_FORMATTER.format(truffleContext, rubyException, rubyException.getBacktrace())) {
                System.err.println(line);
            }

            return NilPlaceholder.INSTANCE;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public IRubyObject toJRuby(Object object) {
        return truffleContext.toJRuby(object);
    }

    @Override
    public Object toTruffle(IRubyObject object) {
        return truffleContext.toTruffle(object);
    }

    @Override
    public void shutdown() {
        truffleContext.shutdown();
    }

}
