package org.jruby.truffle.nodes.profiler;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jruby.truffle.nodes.RubyRootNode;
import org.jruby.truffle.nodes.call.DispatchHeadNode;
import org.jruby.truffle.nodes.call.NewCachedBoxedDispatchNode;
import org.jruby.truffle.nodes.call.NewCachedDispatchNode;
import org.jruby.truffle.nodes.call.NewCachedUnboxedDispatchNode;
import org.jruby.truffle.nodes.call.RubyCallNode;
import org.jruby.truffle.nodes.control.IfNode;
import org.jruby.truffle.nodes.debug.RubyWrapper;
import org.jruby.truffle.nodes.literal.NilLiteralNode;
import org.jruby.truffle.runtime.methods.RubyMethod;
import org.jruby.util.cli.Options;

import com.oracle.truffle.api.instrument.impl.InstrumentationNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.NodeVisitor;

public class ProfilerResultPrinter {

    private PrintStream out = System.err;
    private final ProfilerProber profilerProber;

    public ProfilerResultPrinter(ProfilerProber profilerProber) {
        this.profilerProber = profilerProber;
    }

    static long excludedTime = 0;

    public void printCallProfilerResults() {
        List<MethodBodyInstrument> methodBodyInstruments = profilerProber.getMethodBodyInstruments();
        List<TimeProfilerInstrument> callInstruments = profilerProber.getCallInstruments();

        if (methodBodyInstruments.size() > 0) {
            printBanner("Call Time Profiling Results", 116);
            /**
             * 50 is the length of the text by default padding left padding is added, so space is
             * added to the beginning of the string, minus sign adds padding to the right
             */

            out.format("%-40s", "Function Name");
            out.format("%-20s", "Counter");
            out.format("%-20s", "Excluded Time");
            out.format("%-20s", "Cumulative Time");
            out.format("%-9s", "Line");
            out.format("%-11s", "Column");
            out.println();
            out.println("===============                         ===============     ===============     ===============     ====     ======");

            excludedTime = 0;
            long totalCalls = 0;

            for (MethodBodyInstrument methodBodyInstrument : methodBodyInstruments) {
                Node methodBody = methodBodyInstrument.getMethodBodyNode();
                String methodName = ((RubyRootNode) methodBody.getRootNode()).getSharedMethodInfo().getName();
                long[] results = getCumulativeCounterTime(methodName, methodBodyInstrument, callInstruments);
                long totalCounter = results[0];
                long cumulativeTime = results[1];

                if (totalCounter > 0) {
                    getExcludedTime(methodBody, methodBodyInstrument, cumulativeTime);
                    out.format("%-40s", methodName);
                    out.format("%15s", totalCounter);
                    out.format("%20s", (excludedTime / 1000000000));
                    out.format("%20s", (cumulativeTime / 1000000000));
                    if (methodBody.getSourceSection().getSource().getShortName().equals("builtins.rb")) {
                        out.format("%9s", "-");
                        out.format("%11s", "-");
                    } else {
                        out.format("%9s", methodBody.getSourceSection().getStartLine());
                        out.format("%11s", methodBody.getSourceSection().getStartColumn());
                    }
                    out.println();
                    totalCalls = totalCalls + totalCounter;
                }
            }
            out.println("Total number of executed calls: " + totalCalls);
        }
    }

    private long[] getCumulativeCounterTime(String methodName, MethodBodyInstrument methodBodyInstrument,  List<TimeProfilerInstrument> callInstruments) {
        long totalCounter = 0;
        long cumulativeTime = 0;

        for (TimeProfilerInstrument callInstrument : callInstruments) {
            DispatchHeadNode callDispatchNode = ((RubyCallNode)callInstrument.getNode()).getDispatchHeadNode();
            if (callDispatchNode.getNewDispatch() instanceof NewCachedDispatchNode) {
                RubyMethod method = null;
                NewCachedDispatchNode newCachedDispatchNode = (NewCachedDispatchNode)callDispatchNode.getNewDispatch();
                if (newCachedDispatchNode instanceof NewCachedBoxedDispatchNode) {
                    NewCachedBoxedDispatchNode newCachedBoxedDispatchNode = (NewCachedBoxedDispatchNode)newCachedDispatchNode;
                    method = newCachedBoxedDispatchNode.getRubyMethod();
                } else if (newCachedDispatchNode instanceof NewCachedUnboxedDispatchNode) {
                    NewCachedUnboxedDispatchNode newCachedUnboxedDispatchNode = (NewCachedUnboxedDispatchNode)newCachedDispatchNode;
                    method = newCachedUnboxedDispatchNode.getRubyMethod();
                }

                if (method != null) {
                    if (methodBodyInstrument.getRubyMethod() != null) {
                        if (method.getSharedMethodInfo().equals(methodBodyInstrument.getRubyMethod().getSharedMethodInfo())) {
                            totalCounter = totalCounter + callInstrument.getCounter();
                            cumulativeTime = cumulativeTime + callInstrument.getTime();
                        }
                    }
                }
            }
        }

        long[] returnValues = new long[2];
        returnValues[0] = totalCounter;
        returnValues[1] = cumulativeTime;
        return returnValues;
    }

    public void getExcludedTime(Node methodBody, final MethodBodyInstrument methodBodyInstrument, long cumulativeTime) {
      excludedTime = cumulativeTime;

      methodBody.accept(new NodeVisitor() {
          public boolean visit(Node node) {
              if (node instanceof RubyCallNode) {
                  if (node.getParent() instanceof RubyWrapper) {
                      RubyWrapper callWrapper = (RubyWrapper) node.getParent();
                      /**
                       * foo(bar())
                       * Do not exclude time spent in an argument to a call, i.e,  do not exclude bar() time since foo() time already includes the time spent in bar
                       */
                      if (!(callWrapper.getParent() instanceof RubyCallNode)) {
                          Node callProbe = (Node) callWrapper.getProbe();
                          InstrumentationNode instrumentationNode = (InstrumentationNode) (callProbe.getChildren().iterator().next());
                          TimeProfilerInstrument subCallInstrument = null;

                          while (instrumentationNode != null) {
                              if (instrumentationNode instanceof TimeProfilerInstrument) {
                                  subCallInstrument = (TimeProfilerInstrument) instrumentationNode;
                              }

                              instrumentationNode = (InstrumentationNode)(instrumentationNode.getChildren().iterator().next());
                          }

                          if (subCallInstrument != null) {
                              DispatchHeadNode callDispatchNode = ((RubyCallNode)node).getDispatchHeadNode();
                              if (callDispatchNode.getNewDispatch() instanceof NewCachedDispatchNode) {
                                  NewCachedDispatchNode newCachedDispatchNode = (NewCachedDispatchNode)callDispatchNode.getNewDispatch();
                                  if (newCachedDispatchNode instanceof NewCachedBoxedDispatchNode) {
                                      NewCachedBoxedDispatchNode newCachedBoxedDispatchNode = (NewCachedBoxedDispatchNode)newCachedDispatchNode;
                                      RubyMethod method = newCachedBoxedDispatchNode.getRubyMethod();
                                      if (methodBodyInstrument.getRubyMethod() != null) {
                                          if (!method.getSharedMethodInfo().equals(methodBodyInstrument.getRubyMethod().getSharedMethodInfo())){
                                              /**
                                               * Do not exclude recursive calls
                                               */
                                              excludedTime = excludedTime - subCallInstrument.getTime();
                                          }
                                      }
                                  }
                              }
                          }
                      }
                  }
              }
          return true;
          }
      });
    }

    public void printControlFlowProfilerResults() {
        long totalCount = 0;
        totalCount += printWhileProfilerResults();
        totalCount += printIteratorLoopProfilerResults();
        totalCount += printIfProfilerResults();
        totalCount += printBreakNextProfilerResults();
        out.println("Total number of executed control flow instruments: " + totalCount);
    }
    
    private long printWhileProfilerResults() {
        long totalCount = 0;
        List<ProfilerInstrument> whileInstruments = getInstruments(profilerProber.getWhileInstruments());   
        
        if (whileInstruments.size() > 0) {
            printCaption("While Loop Profiling Results");
            
            for (ProfilerInstrument instrument : whileInstruments) {
                if (instrument.getCounter() > 0) {
                    Node node = instrument.getNode();
                    Node whileNode = node.getParent().getParent();
                    String nodeName = getShortName(whileNode);
                    printProfilerResult(nodeName, whileNode, instrument.getCounter());
                    totalCount = totalCount + instrument.getCounter();
                }
            }

            out.println("Total number of executed instruments: " + totalCount);
        }

        return totalCount;
    }

    private long printIteratorLoopProfilerResults() {
        long totalCount = 0;
        List<ProfilerInstrument> iteratorLoopInstruments = getInstruments(profilerProber.getIteratorLoopInstruments()); 

        if (iteratorLoopInstruments.size() > 0) {
            printBanner("Iterator Loop Profiling Results", 116);

            out.format("%-50s", "Node");
            out.format("%-20s", "Counter");
            out.format("%-9s", "Line");
            out.format("%-11s", "Column");
            out.format("%-70s", "In Method");
            out.println();
            out.println("=============                                     ===============     ====     ======     ==========================");

            for (ProfilerInstrument instrument : iteratorLoopInstruments) {
                if (instrument.getCounter() > 0) {
                    Node node = instrument.getNode();
                    out.format("%-50s", ((RubyRootNode)node.getRootNode()).getSharedMethodInfo());
                    out.format("%15s", instrument.getCounter());
                    totalCount = totalCount + instrument.getCounter();
                    out.format("%9s", node.getSourceSection().getStartLine());
                    out.format("%11s", node.getSourceSection().getStartColumn());
                    out.format("%5s", "");
                    out.format("%-70s", node.getRootNode());
                    out.println();
                }
            }

            out.println("Total number of executed instruments: " + totalCount);
        }

        return totalCount;
    }
    
    public long printIfProfilerResults() {
        long totalCount = 0;
        Map<ProfilerInstrument, List<ProfilerInstrument>> ifInstruments;
        if (Options.TRUFFLE_PROFILE_SORT.load()) {
            ifInstruments = sortIfProfilerResults(profilerProber.getIfInstruments());
        } else {
            ifInstruments = profilerProber.getIfInstruments(); 
        }

        if (ifInstruments.size() > 0) {
            printBanner("If Node Profiling Results", 116);
            out.format("%-20s", "If Counter");
            out.format("%-18s", "Then Counter");
            out.format("%-18s", "Else Counter");
            out.format("%-9s", "Line");
            out.format("%-11s", "Column");
            out.format("%-70s", "In Method");
            out.println();
            out.println("===========         ============      =============     ====     ======     ========================================");

            Iterator<Map.Entry<ProfilerInstrument, List<ProfilerInstrument>>> it = ifInstruments.entrySet().iterator();
            while (it.hasNext()) {
                Entry<ProfilerInstrument, List<ProfilerInstrument>> entry = it.next();
                ProfilerInstrument ifInstrument = entry.getKey();
                if (ifInstrument.getCounter() > 0) {
                    List<ProfilerInstrument> instruments = entry.getValue();
                    out.format("%11s", ifInstrument.getCounter());

                    IfNode ifNode = (IfNode)ifInstrument.getNode();
                    totalCount = totalCount + ifInstrument.getCounter();

                    if (ifNode.getThen() instanceof NilLiteralNode) {
                        out.format("%21s", "-");
                    } else {
                        ProfilerInstrument thenInstrument = instruments.get(0);
                        out.format("%21s", thenInstrument.getCounter());
                        totalCount = totalCount + thenInstrument.getCounter();
                    }

                    if (ifNode.getElse() instanceof NilLiteralNode) {
                        out.format("%19s", "-");
                    } else {
                        if (instruments.size() == 1) {
                            ProfilerInstrument elseInstrument = instruments.get(0);
                            out.format("%19s", elseInstrument.getCounter());
                            totalCount = totalCount + elseInstrument.getCounter();
                        } else if (instruments.size() == 2) {
                            ProfilerInstrument elseInstrument = instruments.get(1);
                            out.format("%19s", elseInstrument.getCounter());
                            totalCount = totalCount + elseInstrument.getCounter();
                        }
                    }

                    out.format("%9s", ifNode.getSourceSection().getStartLine());
                    out.format("%11s", ifNode.getSourceSection().getStartColumn());
                    out.format("%5s", "");
                    out.format("%-70s", ifNode.getRootNode());
                    out.println();
                }
            }

            out.println("Total number of executed instruments: " + totalCount);
        }

        return totalCount;
    }

    public long printBreakNextProfilerResults() {
	    long totalCount = 0;
	    List<ProfilerInstrument> breakNextInstruments = getInstruments(profilerProber.getBreakNextInstruments());   

	    if (breakNextInstruments.size() > 0) {
	        printCaption("Break/Next Profiling Results");

	        for (ProfilerInstrument instrument : breakNextInstruments) {
	            if (instrument.getCounter() > 0) {
	                Node node = instrument.getNode();
	                String nodeName = getShortName(node);
	                printProfilerResult(nodeName, node, instrument.getCounter());
	                totalCount = totalCount + instrument.getCounter();
	            }
	        }
	
	        out.println("Total number of executed instruments: " + totalCount);
	    }

        return totalCount;
    }
    
    public void printVariableAccessProfilerResults() {
        if (Options.TRUFFLE_PROFILE_TYPE_DISTRIBUTION.load()) {
            long totalCount = 0;
            List<TypeDistributionProfilerInstrument> variableAccessInstruments = profilerProber.getVariableAccessTypeDistributionInstruments();           
            
            printBanner("Variable Access Profiling Type Distribution Results", 140);

            if (variableAccessInstruments.size() > 0) {
                out.format("%-50s", "Node");
                out.format("%-20s", "Counter");
                out.format("%-9s", "Line");
                out.format("%-11s", "Column");
                out.format("%-70s", "In Method");
                out.println();
                out.println("=============                                     ===============     ====     ======     ==================================================");
  
                for (TypeDistributionProfilerInstrument profilerInstrument : variableAccessInstruments) {
                    Map<Class<? extends Node>, Counter> types = profilerInstrument.getTypes();
                    if (types.isEmpty()) {
                        Node initialNode = profilerInstrument.getInitialNode();
                        Node onlyNode = profilerInstrument.getOnlyNode();
                        long counter = profilerInstrument.getOnlyCounter();
                        Class<? extends Node> nodeClass = onlyNode.getClass();
                        totalCount = totalCount + counter;
                        out.format("%-50s", nodeClass.getSimpleName());
                        out.format("%15s", counter);
                        out.format("%9s", initialNode.getSourceSection().getStartLine());
                        out.format("%11s", initialNode.getSourceSection().getStartColumn());
                        out.format("%5s", "");
                        out.format("%-70s", initialNode.getRootNode());
                        out.println();
                    } else {
                        Iterator<Map.Entry<Class<? extends Node>, Counter>> it = types.entrySet().iterator();
                        out.println();

                        while (it.hasNext()) {
                            Entry<Class<? extends Node>, Counter> entry = it.next();
                            Node initialNode = profilerInstrument.getInitialNode();
                            Class<? extends Node> nodeClass = entry.getKey();
                            long counter = entry.getValue().getCounter();
                            totalCount = totalCount + counter;
                            out.format("%-50s", nodeClass.getSimpleName());
                            out.format("%15s", counter);
                            out.format("%9s", initialNode.getSourceSection().getStartLine());
                            out.format("%11s", initialNode.getSourceSection().getStartColumn());
                            out.format("%5s", "");
                            out.format("%-70s", initialNode.getRootNode());
                            out.println();
                        }

                        out.println();
                    }
                }
            }      
        } else { 
            long totalCount = 0;
            List<ProfilerInstrument> variableAccessInstruments = getInstruments(profilerProber.getVariableAccessInstruments()); 
            
            if (variableAccessInstruments.size() > 0) {
                printCaption("Variable Access Profiling Results");
                
                for (ProfilerInstrument instrument : variableAccessInstruments) {
                    if (instrument.getCounter() > 0) {
                        Node node = instrument.getNode();
                        String nodeName = getShortName(node);                   
                        printProfilerResult(nodeName, node, instrument.getCounter());
                        totalCount = totalCount + instrument.getCounter();
                    }
                }
                
                out.println("Total number of executed instruments: " + totalCount);
            }    
        }
    }

    public void printOperationProfilerResults() {
        long totalCount = 0;
        List<ProfilerInstrument> operationInstruments = getInstruments(profilerProber.getOperationInstruments()); 
        
        if (operationInstruments.size() > 0) {
            printCaption("Operation Profiling Results");
            
            for (ProfilerInstrument instrument : operationInstruments) {
                if (instrument.getCounter() > 0) {
                    Node node = instrument.getNode();
                    String nodeName =  ((RubyCallNode) node).getName();                    
                    printProfilerResult(nodeName, node, instrument.getCounter());
                    totalCount = totalCount + instrument.getCounter();
                }
            }
            
            out.println("Total number of executed instruments: " + totalCount);
        }
    }
    
    public void printCollectionOperationProfilerResults() {
        long totalCount = 0;
        List<ProfilerInstrument> collectionOperationInstruments = getInstruments(profilerProber.getCollectionOperationInstruments());

        if (collectionOperationInstruments.size() > 0) {
            printCaption("Collection Operator Profiling Results");

            for (ProfilerInstrument instrument : collectionOperationInstruments) {
                if (instrument.getCounter() > 0) {
                    Node node = instrument.getNode();
                    String nodeName =  ((RubyCallNode) node).getName();
                    printProfilerResult(nodeName, node, instrument.getCounter());
                    totalCount = totalCount + instrument.getCounter();
                }
            }

            out.println("Total number of executed instruments: " + totalCount);
        }
    }
    
    private void printCaption(String caption) {
        printBanner(caption, 116);
        out.format("%-25s", "Node");
        out.format("%-20s", "Counter");
        out.format("%-9s", "Line");
        out.format("%-11s", "Column");
        out.format("%-70s", "In Method");
        out.println();
        out.println("=============            ===============     ====     ======     ===================================================");
    }

    private void printProfilerResult(String nodeName, Node node, long counter) {
        out.format("%-25s", nodeName);
        out.format("%15s", counter);
        out.format("%9s", node.getSourceSection().getStartLine());
        out.format("%11s", node.getSourceSection().getStartColumn());
        out.format("%11s", node.getSourceSection().getCharLength());
        out.format("%5s", "");
        out.format("%-70s", node.getRootNode());
        out.println();
    }
    
    private static String getShortName(Node node) {
        NodeInfo nodeInfo = node.getClass().getAnnotation(NodeInfo.class);

        if (nodeInfo == null) {
            nodeInfo = node.getClass().getSuperclass().getAnnotation(NodeInfo.class);
        } else if (nodeInfo.shortName().equals("")) {
            nodeInfo = node.getClass().getSuperclass().getAnnotation(NodeInfo.class);
        }

        if (nodeInfo != null) {
            return nodeInfo.shortName();
        } else {
            throw new RuntimeException("Short name is missing in " + node);
        }
    }
	
    private static List<ProfilerInstrument> getInstruments(List<ProfilerInstrument> instruments) {
        if (Options.TRUFFLE_PROFILE_SORT.load()) {
            List<ProfilerInstrument> sortedInstruments = sortProfilerResult(instruments);
            return sortedInstruments;
        }

        return instruments;
    }
	
    private static List<ProfilerInstrument> sortProfilerResult(List<ProfilerInstrument> list) {
        Collections.sort(list, new Comparator<ProfilerInstrument>() {
            @Override
            public int compare(final ProfilerInstrument profiler1, final ProfilerInstrument profiler2) {
                return Long.compare(profiler2.getCounter(), profiler1.getCounter());
            }
        });

        return list;
    }
    
    private static Map<ProfilerInstrument, List<ProfilerInstrument>> sortIfProfilerResults(Map<ProfilerInstrument, List<ProfilerInstrument>> map) {
        List<Map.Entry<ProfilerInstrument, List<ProfilerInstrument>>> list = new LinkedList<>(map.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<ProfilerInstrument, List<ProfilerInstrument>>>() {

            public int compare(Map.Entry<ProfilerInstrument, List<ProfilerInstrument>> if1, Map.Entry<ProfilerInstrument, List<ProfilerInstrument>> if2) {
                return Long.compare(if2.getKey().getCounter(), if1.getKey().getCounter());
            }
        });

        Map<ProfilerInstrument, List<ProfilerInstrument>> result = new LinkedHashMap<>();
        for (Map.Entry<ProfilerInstrument, List<ProfilerInstrument>> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;

    }
    
    private void printBanner(String caption, int size) {
        // CheckStyle: stop system..print check
        int bannerSize = size - caption.length() - 2;
        for (int i = 0; i < bannerSize / 2; i++) {
            out.print("=");
        }

        out.print(" " + caption + " ");

        for (int i = 0; i < (bannerSize - (bannerSize / 2)); i++) {
            out.print("=");
        }

        out.println();
        // CheckStyle: resume system..print check
    }
}
