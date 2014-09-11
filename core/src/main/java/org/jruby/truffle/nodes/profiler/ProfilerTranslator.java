package org.jruby.truffle.nodes.profiler;

import java.util.Arrays;
import java.util.List;

import org.jruby.truffle.nodes.RubyNode;
import org.jruby.truffle.nodes.call.RubyCallNode;
import org.jruby.truffle.nodes.control.BreakNode;
import org.jruby.truffle.nodes.control.IfNode;
import org.jruby.truffle.nodes.control.NextNode;
import org.jruby.truffle.nodes.control.WhileNode;
import org.jruby.truffle.nodes.debug.RubyWrapper;
import org.jruby.truffle.nodes.literal.NilLiteralNode;
import org.jruby.truffle.nodes.methods.CatchRetryAsErrorNode;
import org.jruby.truffle.nodes.methods.locals.ReadLevelVariableNode;
import org.jruby.truffle.nodes.methods.locals.ReadLocalVariableNode;
import org.jruby.truffle.nodes.methods.locals.WriteLevelVariableNode;
import org.jruby.truffle.nodes.methods.locals.WriteLocalVariableNode;
import org.jruby.truffle.nodes.objects.ReadInstanceVariableNode;
import org.jruby.truffle.nodes.objects.WriteInstanceVariableNode;
import org.jruby.util.cli.Options;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;

public class ProfilerTranslator implements NodeVisitor {
	
	private final static ProfilerTranslator INSTANCE = new ProfilerTranslator();
	
    private final ProfilerProber profilerProber;
    private final ProfilerResultPrinter resultPrinter;    
    private final List<String> operators;
    private final List<String> collectionAccessOperators;
    
    private static boolean translatingModule = false;
    
    public static ProfilerTranslator getInstance() {
    	return INSTANCE;
    }
    
    private ProfilerTranslator() {
    	this.profilerProber = new ProfilerProber();
        this.resultPrinter = new ProfilerResultPrinter(this.profilerProber);
        
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

    public void translate(RootNode rootNode, boolean isModule) {
    	/**
    	 * Main module invocations are not profiled, only function invocations are profiled.
    	 */
    	
    	translatingModule = isModule;
    	rootNode.accept(this);
    	translatingModule = false;
    }

    @Override
    public boolean visit(Node node) {    	
        if (!translatingModule && Options.TRUFFLE_PROFILE_CALLS.load()) {
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
        if (node instanceof CatchRetryAsErrorNode) {
        	createCallWrapper((RubyNode)node);
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
            RubyNode thenNode = ifNode.getThen();
            RubyNode elseNode = ifNode.getElse();

            /**
             * TODO
             * When if modifier is used as "code if condition", then the source section of the following three points to code
             * - if nodes
             * - then body 
             * - else body
             * Therefore, this special case is not currently profiled. 
             */
            
            if (ifNode.getSourceSection().equals(thenNode.getSourceSection())) {
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
    
    private RubyWrapper createCallWrapper(RubyNode node) {
    	RubyWrapper wrapperNode = profilerProber.probeAsCall(node);
        replaceNodeWithWrapper(node, wrapperNode);
        return wrapperNode;
    }
   
    private RubyWrapper createLoopBodyWrapper(RubyNode node) {
    	RubyWrapper wrapperNode = profilerProber.probeAsWhile(node);
        replaceNodeWithWrapper(node, wrapperNode);
        return wrapperNode;
    }
    
    private void createIfWrappers(IfNode ifNode, RubyNode thenNode, RubyNode elseNode) {
        List<RubyWrapper> wrappers = profilerProber.probeAsIf(ifNode, thenNode, elseNode);
        replaceNodeWithWrapper(ifNode, wrappers.get(0));
        replaceNodeWithWrapper(thenNode, wrappers.get(1));
        replaceNodeWithWrapper(elseNode, wrappers.get(2));
    }

    private void createIfWithoutElseWrappers(RubyNode ifNode, RubyNode thenNode) {
        List<RubyWrapper> wrappers = profilerProber.probeAsIfWithoutElse(ifNode, thenNode);
        replaceNodeWithWrapper(ifNode, wrappers.get(0));
        replaceNodeWithWrapper(thenNode, wrappers.get(1));
    }

    private RubyWrapper createBreakNextWrapper(RubyNode node) {
        RubyWrapper wrapperNode = profilerProber.probeAsBreakNext(node);
        replaceNodeWithWrapper(node, wrapperNode);
        return wrapperNode;
    }
    
    private RubyWrapper createReadWriteWrapper(RubyNode node) {
        RubyWrapper wrapperNode = profilerProber.probeAsVariableAccess(node);
        replaceNodeWithWrapper(node, wrapperNode);
        return wrapperNode;
    }
    
    private RubyWrapper createOperationWrapper(RubyNode node) {
        RubyWrapper wrapperNode = profilerProber.probeAsOperation(node);
        replaceNodeWithWrapper(node, wrapperNode);
        return wrapperNode;
    }
    
    private RubyWrapper createCollectionOperationWrapper(RubyNode node) {
        RubyWrapper wrapperNode = profilerProber.probeAsCollectionOperation(node);
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

}