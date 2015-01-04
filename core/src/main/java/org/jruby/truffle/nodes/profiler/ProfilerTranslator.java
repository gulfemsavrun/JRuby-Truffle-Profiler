// TODO: needs copyright message from Gulfem

package org.jruby.truffle.nodes.profiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jruby.truffle.nodes.RubyCallNode;
import org.jruby.truffle.nodes.RubyNode;
import org.jruby.truffle.nodes.RubyRootNode;
import org.jruby.truffle.nodes.control.BreakNode;
import org.jruby.truffle.nodes.control.IfNode;
import org.jruby.truffle.nodes.control.NextNode;
import org.jruby.truffle.nodes.control.SequenceNode;
import org.jruby.truffle.nodes.control.WhileNode;
import org.jruby.truffle.nodes.instrument.RubyWrapperNode;
import org.jruby.truffle.nodes.literal.NilLiteralNode;
import org.jruby.truffle.nodes.methods.ExceptionTranslatingNode;
import org.jruby.truffle.nodes.methods.locals.ReadLevelVariableNode;
import org.jruby.truffle.nodes.methods.locals.ReadLocalVariableNode;
import org.jruby.truffle.nodes.methods.locals.WriteLevelVariableNode;
import org.jruby.truffle.nodes.methods.locals.WriteLocalVariableNode;
import org.jruby.truffle.nodes.objects.ReadInstanceVariableNode;
import org.jruby.truffle.nodes.objects.WriteInstanceVariableNode;
import org.jruby.truffle.runtime.methods.RubyMethod;
import org.jruby.util.cli.Options;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;

public class ProfilerTranslator implements NodeVisitor {

    private final static ProfilerTranslator INSTANCE = new ProfilerTranslator();

    private RubyRootNode mainRootNode;
    private final List<RubyRootNode> methodRootNodes;
    private final ProfilerProber profilerProber;
    private final ProfilerResultPrinter resultPrinter;
    private final List<String> operators;
    private final List<String> collectionAccessOperators;
    private final List<String> usedDefinedMethods;
    private boolean isTranslatingBlock;
    private RubyMethod currentRubyMethod = null;

    public static ProfilerTranslator getInstance() {
        return INSTANCE;
    }

    private ProfilerTranslator() {
        this.methodRootNodes = new ArrayList<>();
        this.profilerProber = ProfilerProber.getInstance();
        this.resultPrinter = new ProfilerResultPrinter(this.profilerProber);
        this.usedDefinedMethods = new ArrayList<>();

        operators = Arrays.asList("+", "-", "*", "/", "%", "**",
                "==", "!=", ">", "<", ">=", "<=", "<=>", "===", ".eq?", "equal?",
                "&", "|", "^", "~", "<<", ">>", "and", "or", "&&", "||", "!", "not");

        /**
         * a = [10, 20, 30] 
         * x = a[0] ------------> RubyCallNode ("[]")  ----> Read from an array
         * a[0] = 40 -----------> RubyCallNode ("[]=") ----> Write into an array
         */
        collectionAccessOperators = Arrays.asList("[]", "[]=");
    }

    public void addUserDefinedMethod(String methodName) {
        this.usedDefinedMethods.add(methodName);
    }

    public void translate(RootNode rootNode, boolean isModule, boolean isBlock, RubyMethod rubyMethod) {
        /**
         * Main module invocations are not profiled, only function invocations are profiled.
         */

        isTranslatingBlock = isBlock;
        currentRubyMethod = rubyMethod;

        if (isModule) {
            mainRootNode = (RubyRootNode)rootNode;
        } else {
            methodRootNodes.add((RubyRootNode)rootNode);
        }

        rootNode.accept(this);
        isTranslatingBlock = false;
        currentRubyMethod = null;
    }

    @Override
    public boolean visit(Node node) {
        if (Options.TRUFFLE_PROFILE_CALLS.load() || Options.TRUFFLE_PROFILE_BUILTIN_CALLS.load()) {
            profileCalls(node);
        }

        if (Options.TRUFFLE_PROFILE_CONTROL_FLOW.load()) {
            profileControlFlow(node);
        }

        if (Options.TRUFFLE_PROFILE_VARIABLE_ACCESSES.load()) {
            profileVariableAccesses(node);
        }

        if (Options.TRUFFLE_PROFILE_OPERATIONS.load()) {
            profileOperations(node);
        }

        if (Options.TRUFFLE_PROFILE_COLLECTION_OPERATIONS.load()) {
            profileCollectionOperations(node);
        }

        return true;
    }

    private void profileCalls(Node node) {
        if (node instanceof ExceptionTranslatingNode) {
            createMethodBodyWrapper((RubyNode)node);
        } else if (node instanceof RubyCallNode) {
            RubyCallNode callNode = (RubyCallNode) node;
            String name = callNode.getName();
            if (Options.TRUFFLE_PROFILE_BUILTIN_CALLS.load()) {
                createCallWrapper((RubyNode)node);
            } else {
                if (isTranslatingBlock) {
                    if(!operators.contains(name) && !collectionAccessOperators.contains(name)) {
                        createCallWrapper((RubyNode)node);
                    }
                } else if (usedDefinedMethods.contains(name)) {
                    createCallWrapper((RubyNode)node);
                }
            }
        }
    }

    private void profileControlFlow(Node node) {
        profileLoops(node);
        profileIfs(node);
        profileBreakNextNodes(node);
    }

    private void profileLoops(Node node) {
        if (node instanceof WhileNode) {
            WhileNode loopNodeNode = (WhileNode) node;
            RubyNode loopBodyNode = loopNodeNode.getBody();
            createLoopBodyWrapper(loopBodyNode);
        }
    }

    private void profileIfs(Node node) {
        if (node instanceof IfNode) {
            IfNode ifNode = (IfNode) node;
            /**
             * Iterators create extra if node, so don't wrap this if node in iterators
             * (1..10).each do |i|
             *     x = 10
             * end
             * AST of an iterator loop
             * body = SequenceNode
             * body[0] = IfNode
             * condition = BooleanCastBooleanNode
             *    child = BehaveAsBlockNode
             * thenBody = NilLiteralNode
             * elseBody = CheckArityNode
             * body[1] = WriteLocalVariableIntNode
             *   rhs = ReadPreArgumentNode
             * body[2] = RubyWrapper
             * child = WriteLocalVariableIntNode
             *  rhs = IntegerFixnumLiteralNode
             * body[3] = RubyWrapper
             * child = RubyCallNode
             * receiver = SelfNode
             **/

            if (!(isTranslatingBlock && ifNode.getParent() instanceof SequenceNode)) {
                RubyNode thenNode = ifNode.getThen();
                RubyNode elseNode = ifNode.getElse();

                /**
                 * Unless statement is translated into an if node
                 * thenNode of such an if node is null
                 * Only create a wrapper node if then part exists.
                 */
                if (thenNode instanceof NilLiteralNode) {
                    createIfWithoutThenWrappers(ifNode, elseNode);
                    return;
                }

                /**
                 * Only create a wrapper node if an else part exists.
                 */
                if (elseNode instanceof NilLiteralNode) {
                    createIfWithoutElseWrappers(ifNode, thenNode);
                } else {
                    createIfWrappers(ifNode, thenNode, elseNode);
                }
            }
        }
    }

    private void profileBreakNextNodes(Node node) {
        if (node instanceof BreakNode) {
            createBreakNextWrapper((RubyNode) node);
        } else if (node instanceof NextNode) {
            createBreakNextWrapper((RubyNode) node);
        }
    }

    private void profileVariableAccesses(Node node) {
        if (node instanceof ReadLocalVariableNode) {
            createReadWriteWrapper((RubyNode) node);
        } else if (node instanceof ReadLevelVariableNode) {
            createReadWriteWrapper((RubyNode) node);
        } else if (node instanceof ReadInstanceVariableNode) {
            createReadWriteWrapper((RubyNode) node);
        } else if (node instanceof WriteLocalVariableNode) {
            createReadWriteWrapper((RubyNode) node);
        } else if (node instanceof WriteLevelVariableNode) {
            createReadWriteWrapper((RubyNode) node);
        } else if (node instanceof WriteInstanceVariableNode) {
            createReadWriteWrapper((RubyNode) node);
        }
    }

    private void profileOperations(Node node) {
        if (node instanceof RubyCallNode) {
            RubyCallNode callNode = (RubyCallNode) node;
            String name = callNode.getName();
            if (operators.contains(name)) {
                createOperationWrapper(callNode);
            }
        }
    }

    private void profileCollectionOperations(Node node) {
        if (node instanceof RubyCallNode) {
            RubyCallNode callNode = (RubyCallNode) node;
            String name = callNode.getName();
            if (collectionAccessOperators.contains(name)) {
                createCollectionOperationWrapper(callNode);
            }
        }
    }

    private RubyWrapperNode createMethodBodyWrapper(RubyNode node) {
        RubyWrapperNode wrapperNode = profilerProber.probeAsMethodBody(node, currentRubyMethod);
        replaceNodeWithWrapper(node, wrapperNode);
        return wrapperNode;
    }

    public RubyWrapperNode createCallWrapper(RubyNode node) {
        RubyWrapperNode wrapperNode = profilerProber.probeAsCall(node);
        replaceNodeWithWrapper(node, wrapperNode);
        return wrapperNode;
    }

    private RubyWrapperNode createLoopBodyWrapper(RubyNode node) {
        RubyWrapperNode wrapperNode = profilerProber.probeAsWhile(node);
        replaceNodeWithWrapper(node, wrapperNode);
        return wrapperNode;
    }

    private void createIfWrappers(IfNode ifNode, RubyNode thenNode, RubyNode elseNode) {
        List<RubyWrapperNode> wrappers = profilerProber.probeAsIf(ifNode, thenNode, elseNode);
        replaceNodeWithWrapper(ifNode, wrappers.get(0));
        replaceNodeWithWrapper(thenNode, wrappers.get(1));
        replaceNodeWithWrapper(elseNode, wrappers.get(2));
    }

    private void createIfWithoutThenWrappers(RubyNode ifNode, RubyNode elseNode) {
        List<RubyWrapperNode> wrappers = profilerProber.probeAsIfWithoutThen(ifNode, elseNode);
        replaceNodeWithWrapper(ifNode, wrappers.get(0));
        replaceNodeWithWrapper(elseNode, wrappers.get(1));
    }

    private void createIfWithoutElseWrappers(RubyNode ifNode, RubyNode thenNode) {
        List<RubyWrapperNode> wrappers = profilerProber.probeAsIfWithoutElse(ifNode, thenNode);
        replaceNodeWithWrapper(ifNode, wrappers.get(0));
        replaceNodeWithWrapper(thenNode, wrappers.get(1));
    }

    private RubyWrapperNode createBreakNextWrapper(RubyNode node) {
        RubyWrapperNode wrapperNode = profilerProber.probeAsBreakNext(node);
        replaceNodeWithWrapper(node, wrapperNode);
        return wrapperNode;
    }

    private RubyWrapperNode createReadWriteWrapper(RubyNode node) {
        RubyWrapperNode wrapperNode = profilerProber.probeAsVariableAccess(node);
        replaceNodeWithWrapper(node, wrapperNode);
        return wrapperNode;
    }

    private RubyWrapperNode createOperationWrapper(RubyNode node) {
        RubyWrapperNode wrapperNode = profilerProber.probeAsOperation(node);
        replaceNodeWithWrapper(node, wrapperNode);
        return wrapperNode;
    }

    private RubyWrapperNode createCollectionOperationWrapper(RubyNode node) {
        RubyWrapperNode wrapperNode = profilerProber.probeAsCollectionOperation(node);
        replaceNodeWithWrapper(node, wrapperNode);
        return wrapperNode;
    }

    private static void replaceNodeWithWrapper(RubyNode node, RubyNode wrapperNode) {
        /**
         * If a node is already wrapped, then another wrapper node is not created, and existing
         * wrapper node is used. If a wrapper node is not created, do not replace the node,
         * otherwise replace the node with the new created wrapper node
         */
        if (!wrapperNode.equals(node.getParent())) {
            node.replace(wrapperNode);
            wrapperNode.adoptChildren();
        }
    }

    public ProfilerProber getProfilerProber() {
        return profilerProber;
    }

    public ProfilerResultPrinter getProfilerResultPrinter() {
        return resultPrinter;
    }

    public RubyRootNode getMainRootNode() {
        return mainRootNode;
    }

    public List<RubyRootNode> getMethodRootNodes() {
        return methodRootNodes;
    }

}